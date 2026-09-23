# 数据治理 V1.1 数据库增量设计

文档状态：待评审
关联变更：CR-DG-001
数据库：MySQL 8.4

## 1. 设计原则

- 只新增数据治理表，不修改 V1.0 已确认表结构。
- 所有数据治理业务表必须包含 `enterprise_id`。
- 企业隔离由应用层上下文和数据库字段共同保证。
- 已生效业务数据不得由数据治理表直接覆盖。
- 写操作继续复用 `idempotency_record`。
- 审计继续复用 `audit_log`。

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

## 3. 关键约束

| 约束 | 设计 |
| --- | --- |
| 规则编码唯一 | `dq_rule_definition.rule_code` 唯一。 |
| 企业规则配置唯一 | `dq_enterprise_rule_config(enterprise_id, rule_code)` 唯一。 |
| 检查任务编号唯一 | `dq_check_run.run_no` 唯一。 |
| 检查结果唯一 | `dq_check_result(run_id, rule_code)` 唯一。 |
| 未关闭问题去重 | `dq_issue(enterprise_id, open_issue_key)` 唯一；关闭问题生成列为 `NULL`，允许历史重复。 |
| 问题状态保护 | 数据库保存状态，应用层以条件更新和乐观锁控制转换。 |
| 更正不覆盖原始业务表 | `dq_remediation` 仅保存证据和更正引用。 |

## 4. 开放问题唯一键

`dq_issue.open_issue_key` 是生成列：

```sql
CASE
  WHEN issue_status <> 'CLOSED'
  THEN CONCAT(rule_code, '#', object_type, '#', object_key)
  ELSE NULL
END
```

MySQL 唯一索引允许多个 `NULL`，因此已关闭问题不参与未关闭问题去重；同一对象再次违规可创建新问题。

## 5. V1.0 表复用

| 复用表 | 用途 |
| --- | --- |
| `enterprise` | 企业隔离外键。 |
| `sys_user` | 发起人、责任人、处理人、复核人。 |
| `business_attachment` | 处理证据附件。 |
| `audit_log` | 操作、拒绝和跨企业访问审计。 |
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

## 7. 看板统计 SQL 口径

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

## 8. 当前结论

本数据库增量设计为待评审稿。评审通过前不得执行正式数据库迁移。
