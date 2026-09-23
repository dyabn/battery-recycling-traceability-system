# 数据治理 V1.1 架构设计

文档状态：待评审
关联变更：CR-DG-001

## 1. 模块划分

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| MOD-DQ-RULE | 固定规则定义、企业规则配置、启停审计。 | MOD-AUTH、MOD-AUDIT |
| MOD-DQ-CHECK | 创建检查任务、执行启用规则、生成检查结果。 | MOD-DQ-RULE、V1.0 业务表 |
| MOD-DQ-ISSUE | 质量问题生成、去重、分配和状态机。 | MOD-DQ-CHECK、MOD-AUDIT |
| MOD-DQ-REMEDIATION | 处理说明、附件、业务更正引用。 | MOD-ATTACHMENT、MOD-AUDIT |
| MOD-DQ-RECHECK | 重新检查、关闭或退回问题。 | MOD-DQ-CHECK、MOD-DQ-ISSUE |
| MOD-DQ-DASHBOARD | 最近 30 天质量状态汇总。 | MOD-DQ-CHECK、MOD-DQ-ISSUE |

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
4. 每条规则生成一条 `dq_check_result`。
5. 违规对象生成或命中 `dq_issue`。
6. 检查完成后更新任务摘要。
7. 写入检查完成审计。

检查失败时：

- `dq_check_run.run_status=FAILED`。
- 记录失败原因。
- 不生成质量问题。
- 不计入看板的完成检查次数。

## 4. 问题生成和去重

同一企业、同一规则、同一业务对象只能存在一个未关闭问题。技术上由 `dq_issue.open_issue_key` 生成列和唯一约束控制。

对象标识规则：

- 有业务主键时：`object_type + object_id`。
- 追溯编码类问题需要隐藏跨企业对象时：`object_type + object_key`。
- 关闭后再次违规可以创建新问题。

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

复用 `audit_log`。若 V1.0 物理字段不足，V1.1 通过结构化 `action`、`object_type`、`object_id`、`before_status`、`after_status`、`reject_reason`、`request_id` 的约定补足；不得省略拒绝审计。

## 6. API 分层

| 层 | 设计 |
| --- | --- |
| Controller | 只接收请求、提取登录上下文、校验幂等头和权限。 |
| Application Service | 编排事务、状态机、审计和幂等。 |
| Domain Service | 规则执行、问题去重、状态转换。 |
| Repository | 按企业隔离访问数据。 |

## 7. 并发控制

- `dq_issue`、`dq_enterprise_rule_config`、`dq_check_run` 使用 `version` 字段做乐观锁。
- 问题状态更新采用条件更新：`WHERE id=? AND enterprise_id=? AND issue_status=? AND version=?`。
- 开放问题去重由数据库唯一约束兜底。
- 幂等键防止重复提交。

## 8. 当前结论

架构设计待评审。评审通过前不得进入生产代码实现。
