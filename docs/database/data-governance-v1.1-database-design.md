# 数据治理 V1.1 数据库增量设计

文档状态：已确认
关联变更：CR-DG-001
数据库：MySQL 8.4

## 1. 设计原则

- 只新增数据治理表，不修改 V1.0 已确认表结构。
- 所有数据治理业务表必须包含 `enterprise_id`。
- 企业隔离由应用层上下文和数据库字段共同保证。
- 已生效业务数据不得由数据治理表直接覆盖。
- 写操作继续复用 `idempotency_record`。
- 数据治理写操作和拒绝操作使用 `dq_operation_audit` 保存结构化治理审计；V1.0 `audit_log` 仍保留为通用审计表。

## 2. 新增表

| 表 | 说明 |
| --- | --- |
| `dq_rule_definition` | 固定数据质量规则定义。 |
| `dq_enterprise_rule_config` | 企业维度规则启停状态。 |
| `dq_check_run` | 一次手工质量检查任务。 |
| `dq_check_result` | 检查任务下每条规则的执行结果。 |
| `dq_issue` | 数据质量问题。 |
| `dq_remediation` | 问题处理说明、证据和更正引用。 |
| `dq_recheck` | 问题重新检查记录。 |
| `dq_operation_audit` | 数据治理结构化操作审计。 |

## 3. 关键约束

| 约束 | 设计 |
| --- | --- |
| 规则编码唯一 | `dq_rule_definition.rule_code` 唯一。 |
| 企业规则配置唯一 | `dq_enterprise_rule_config(enterprise_id, rule_code)` 唯一。 |
| 检查任务编号唯一 | `dq_check_run.run_no` 唯一。 |
| 检查结果唯一 | `dq_check_result(run_id, rule_code)` 唯一。 |
| 未关闭问题去重 | `dq_issue(enterprise_id, open_issue_key)` 唯一；`open_issue_key` 使用规范化 `object_identity`，关闭问题生成列为 `NULL`，允许历史重复。 |
| 问题状态保护 | 数据库保存状态，应用层以条件更新和乐观锁控制转换。 |
| 整改证据强制存在 | `dq_remediation` 要求附件证据或更正对象至少存在一种；更正对象类型和 ID 必须成对出现。 |
| 复核结果机器生成 | `dq_recheck` 必须关联 `linked_check_run_id` 和 `linked_check_result_id`，客户端不得直接提交通过结果。 |
| 复核完成态一致性 | `dq_recheck` 使用 `ck_dq_recheck_completion` 约束 `RUNNING`、`PASSED`、`FAILED` 与 `result`、`completed_by`、`completed_at`、`result_reason` 的合法组合。 |
| 结构化审计 | `dq_operation_audit` 保存操作对象、前后状态、结果、原因、操作者、时间、`trace_id` 和幂等键摘要。 |
| 企业规则默认启用 | V1.1 迁移时只为缺失企业规则配置补齐 DQ-001..DQ-007 默认启用配置；已存在配置不得被重复迁移覆盖。新增企业创建时同步初始化 7 条配置。 |
| 权限与角色初始化 | V1.1 迁移幂等初始化 10 个 `dq:*` 权限编码和 4 类课程角色映射，不重复插入；`dq:rule:toggle` 在 V1.1 不分配给任何角色。 |

## 4. 开放问题唯一键

`dq_issue.open_issue_key` 是生成列。它不使用可展示、可变化的 `object_key`，而使用稳定、规范化的 `object_identity`：

```sql
CASE
  WHEN issue_status <> 'CLOSED'
  THEN CONCAT(rule_code, '#', object_type, '#', LOWER(TRIM(object_identity)))
  ELSE NULL
END
```

MySQL 唯一索引允许多个 `NULL`，因此已关闭问题不参与未关闭问题去重；同一对象再次违规可创建新问题。大小写和前后空格差异不能绕过去重。

对象标识口径：

| 规则 | `object_identity` |
| --- | --- |
| DQ-001 | 电池技术 ID 或规范化冲突标识。 |
| DQ-002 | `battery.id`。 |
| DQ-003 | 重复候选记录 ID。 |
| DQ-004 | `inbound_record.id`。 |
| DQ-005 | `inbound_record.id`。 |
| DQ-006 | `battery.id`。 |
| DQ-007 | 库存记录 ID 或 `battery.id`。 |

## 5. V1.0 表复用

| 复用表 | 用途 |
| --- | --- |
| `enterprise` | 企业隔离外键。 |
| `sys_user` | 发起人、责任人、处理人、复核人。 |
| `business_attachment` | 处理证据附件。 |
| `audit_log` | V1.0 通用审计。 |
| `idempotency_record` | 写接口幂等。 |
| `battery` | DQ-001、DQ-002、DQ-004、DQ-007。 |
| `battery_registration_candidate` | DQ-003。 |
| `duplicate_code_review` | DQ-003。 |
| `acceptance_record` | DQ-004。 |
| `inbound_record` | DQ-004、DQ-005。 |
| `warehouse_location` | DQ-005。 |
| `inventory` | DQ-007。 |
| `lifecycle_event` | DQ-004、DQ-006。 |

## 6. 删除规则

数据治理表不做物理删除。后续如需停用，使用状态字段：

- 规则配置使用 `enabled_status`。
- 检查任务使用 `run_status`。
- 问题使用 `issue_status`。
- 处理和复核保留历史记录。

## 7. 迁移幂等策略

V1.1 增量迁移可以重复执行，但不得覆盖企业已经做出的业务配置：

- `dq_rule_definition` 可重复初始化固定规则元数据。
- `dq_enterprise_rule_config` 只补缺失企业与规则组合；如果企业已将 DQ-002 停用，再次执行迁移后仍保持 `DISABLED`。
- `sys_permission`、`sys_role` 和 `sys_role_permission` 使用唯一键幂等初始化，重复执行不得产生重复权限或重复授权。
- `dq:rule:toggle` 作为权限编码保留，但 V1.1 不授予任何角色。

## 8. 检查失败事务

质量检查采用全有或全无策略：

1. 创建检查任务并进入 `RUNNING`。
2. 执行当前企业已启用的全部规则。
3. 暂存检查结果和候选问题。
4. 所有规则成功后，统一写入 `dq_check_result` 和 `dq_issue`。
5. 任一规则发生致命失败时，回滚本轮产生的检查结果和质量问题。
6. 使用独立事务将 `dq_check_run` 标记为 `FAILED`，记录失败原因和 `dq_operation_audit`。

不得出现“运行状态为失败，但前几条规则已经生成质量问题”的中间状态。

## 9. 看板统计 SQL 口径

默认最近 30 天，按当前企业过滤：

```sql
SELECT COUNT(*)
FROM dq_check_run
WHERE enterprise_id = ?
  AND run_status = 'COMPLETED'
  AND completed_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY;
```

问题数量：

```sql
SELECT COUNT(*)
FROM dq_issue
WHERE enterprise_id = ?
  AND first_detected_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY;
```

未关闭问题：

```sql
SELECT COUNT(*)
FROM dq_issue
WHERE enterprise_id = ?
  AND issue_status <> 'CLOSED';
```

关闭率：

```sql
SELECT
  SUM(CASE WHEN issue_status = 'CLOSED' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0)
FROM dq_issue
WHERE enterprise_id = ?
  AND first_detected_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY;
```

## 10. 当前结论

本数据库增量设计已确认。正式数据库迁移仍需在后续实现和发布关口按审批执行。
