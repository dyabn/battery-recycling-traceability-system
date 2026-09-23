# 数据治理 V1.1 架构设计

文档状态：待评审
关联变更：CR-DG-001

## 1. 模块划分

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| MOD-DQ-RULE | 固定规则定义、企业规则配置和只读查询；启停接口保留但 V1.1 不授予系统管理员。 | MOD-AUTH、MOD-DQ-AUDIT |
| MOD-DQ-CHECK | 创建检查任务、执行启用规则、生成检查结果。 | MOD-DQ-RULE、V1.0 业务表 |
| MOD-DQ-ISSUE | 质量问题生成、稳定对象去重、分配和状态机。 | MOD-DQ-CHECK、MOD-DQ-AUDIT |
| MOD-DQ-REMEDIATION | 处理说明、附件、业务更正引用和证据校验。 | MOD-ATTACHMENT、MOD-DQ-AUDIT |
| MOD-DQ-RECHECK | 重新检查、关闭或退回问题。 | MOD-DQ-CHECK、MOD-DQ-ISSUE |
| MOD-DQ-DASHBOARD | 最近 30 天质量状态汇总。 | MOD-DQ-CHECK、MOD-DQ-ISSUE |
| MOD-DQ-AUDIT | 数据治理结构化审计。 | MOD-AUTH |

## 2. 固定规则执行器

| 处理器 | 输入 | 输出 | 关键约束 |
| --- | --- | --- | --- |
| `TraceCodeUniqueRule` | 当前企业可见电池、全系统追溯编码聚合结果。 | DQ-001 违规对象。 | 全系统唯一，跨企业冲突不泄露对方明细。 |
| `BatteryRequiredFieldRule` | `battery` 当前企业记录。 | DQ-002 违规对象。 | `battery_chemistry='未知'` 合法，空值不合法。 |
| `DuplicateReviewRule` | `battery_registration_candidate`、`duplicate_code_review`。 | DQ-003 违规对象。 | 候选或重复核实未终结即违规。 |
| `AcceptanceBeforeInboundRule` | `acceptance_record`、`inbound_record`、`battery`、`lifecycle_event`。 | DQ-004 违规对象。 | 入库前必须存在通过验收事实。 |
| `WarehouseLocationRule` | `inbound_record`、`warehouse_location`。 | DQ-005 违规对象。 | 比较 `inbound_record.warehouse_id` 与 `warehouse_location.warehouse_id`。 |
| `LifecycleSequenceRule` | `lifecycle_event.occurred_at`。 | DQ-006 违规对象。 | 按关键事件顺序检查时间不倒序。 |
| `InventoryEnterpriseRule` | `battery`、`inventory`。 | DQ-007 违规对象。 | `inventory.is_current=1` 时企业必须一致。 |

## 3. 检查流程

1. 业务主管提交检查范围。
2. 系统读取当前企业已启用规则。
3. 系统创建 `dq_check_run`，状态为 `RUNNING`。
4. 执行全部启用规则，并在内存或临时上下文暂存检查结果和候选问题。
5. 所有规则成功后，统一写入 `dq_check_result`。
6. 违规对象生成或命中 `dq_issue`。
7. 检查完成后更新任务摘要。
8. 写入 `dq_operation_audit`。

检查失败时：

- 回滚本轮产生的检查结果和质量问题。
- 使用独立事务将 `dq_check_run.run_status` 更新为 `FAILED`。
- 记录失败原因和 `dq_operation_audit`。
- 不计入看板的完成检查次数。

## 4. 问题生成和去重

同一企业、同一规则、同一业务对象只能存在一个未关闭问题。技术上由 `dq_issue.open_issue_key` 生成列和唯一约束控制。

对象标识规则：

- 有业务主键时：`object_type + object_identity`，其中 `object_identity` 使用稳定技术 ID。
- 追溯编码类问题需要隐藏跨企业对象时：使用规范化冲突标识作为 `object_identity`，展示用 `object_key` 不参与唯一判重。
- 唯一键使用 `LOWER(TRIM(object_identity))`，大小写和前后空格不能绕过去重。
- 关闭后再次违规可以创建新问题。
- 并发检查由数据库唯一约束兜底，只允许一个未关闭问题。

## 5. 审计闭环

每个写操作和拒绝操作必须写审计：

- 企业。
- 操作人。
- 业务对象。
- 动作。
- 变更前状态。
- 变更后状态。
- 操作结果。
- 原因或拒绝原因。

数据治理使用 `dq_operation_audit` 保存结构化审计，字段包括 `action_code`、`object_type`、`object_id`、`before_state`、`after_state`、`result`、`reason`、`operated_by`、`operated_at`、`trace_id` 和 `idempotency_key_hash`；不得省略拒绝审计。

## 6. 机器复核闭环

`POST /issues/{issueId}/rechecks` 由后端执行：

1. 读取 `SUBMITTED` 状态问题。
2. 校验整改说明和证据存在。
3. 使用原 `rule_code`、`object_type` 和 `object_identity` 执行同一规则。
4. 生成 `dq_check_run`、`dq_check_result` 和 `dq_recheck`。
5. 机器检查通过时关闭问题；未通过时退回 `REJECTED`。
6. 问题状态、复核记录、检查结果和审计在同一事务中保存。

## 7. API 分层

| 层 | 设计 |
| --- | --- |
| Controller | 只接收请求、提取登录上下文、校验幂等头和权限。 |
| Application Service | 编排事务、状态机、审计和幂等。 |
| Domain Service | 规则执行、问题去重、状态转换。 |
| Repository | 按企业隔离访问数据。 |

## 8. 并发控制

- `dq_issue`、`dq_enterprise_rule_config`、`dq_check_run` 使用 `version` 字段做乐观锁。
- 问题状态更新采用条件更新：`WHERE id=? AND enterprise_id=? AND issue_status=? AND version=?`。
- 开放问题去重由数据库唯一约束兜底。
- 幂等键防止重复提交。

## 9. 当前结论

架构设计待评审。评审通过前不得进入生产代码实现。
