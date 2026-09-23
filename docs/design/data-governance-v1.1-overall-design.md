# 数据治理 V1.1 总体设计

文档状态：待评审
关联变更：CR-DG-001
目标基线：V1.1

## 1. 设计目标

在 V1.0 第一切片基础上增加数据治理闭环，使系统能够对已形成的回收接收、登记、验收、入库、库存和追溯数据执行固定质量检查，并形成可分配、可处理、可复核、可审计的问题闭环。

核心目标：

- 固定规则可查看、可按企业启停，但不可在线修改算法或脚本。
- 质量检查由业务主管手工发起。
- 检查结果按企业隔离，问题按规则和对象去重。
- 问题处理不得直接覆盖原始业务数据。
- 重新检查通过后才允许关闭问题。
- 看板统计默认最近 30 天，全部按企业隔离。

## 2. 总体架构

V1.1 采用增量模块方式新增 `MOD-DATA-QUALITY`，复用 V1.0 的认证、权限、审计、幂等、附件和业务基础数据。

```text
PC Web 原型/未来前端
  -> REST API
    -> DataQualityApplicationService
      -> RuleDefinitionService
      -> CheckRunService
      -> IssueWorkflowService
      -> DashboardQueryService
      -> AuditService / IdempotencyService
    -> MySQL 8.4
```

固定规则执行器：

| 规则 | 处理器 |
| --- | --- |
| DQ-001 | `TraceCodeUniqueRule` |
| DQ-002 | `BatteryRequiredFieldRule` |
| DQ-003 | `DuplicateReviewRule` |
| DQ-004 | `AcceptanceBeforeInboundRule` |
| DQ-005 | `WarehouseLocationRule` |
| DQ-006 | `LifecycleSequenceRule` |
| DQ-007 | `InventoryEnterpriseRule` |

## 3. 数据模型增量

新增 7 张表：

- `dq_rule_definition`
- `dq_enterprise_rule_config`
- `dq_check_run`
- `dq_check_result`
- `dq_issue`
- `dq_remediation`
- `dq_recheck`

复用 V1.0 表：

- `enterprise`
- `sys_user`
- `sys_role`
- `sys_permission`
- `business_attachment`
- `audit_log`
- `idempotency_record`
- `battery`
- `battery_registration_candidate`
- `duplicate_code_review`
- `acceptance_record`
- `inbound_record`
- `warehouse`
- `warehouse_location`
- `inventory`
- `lifecycle_event`

## 4. 状态机

质量问题状态：

```text
OPEN -> ASSIGNED -> PROCESSING -> SUBMITTED -> RECHECKING -> CLOSED
                                  \-> RECHECKING -> REJECTED -> PROCESSING
```

状态要求：

- `OPEN` 只能由业务主管分配为 `ASSIGNED`。
- `ASSIGNED` 只能由责任人开始处理为 `PROCESSING`。
- `PROCESSING` 必须提交说明和证据后进入 `SUBMITTED`。
- `SUBMITTED` 只能由业务主管发起重新检查进入 `RECHECKING`。
- `RECHECKING` 通过后进入 `CLOSED`，失败后进入 `REJECTED`。
- `CLOSED` 不允许编辑、分配、处理或复核。
- 所有非法转换必须拒绝并保持原状态不变。

## 5. 事务和幂等

所有写接口必须携带 `Idempotency-Key`。

成功流程：

- 校验权限、企业、状态、必填字段和幂等键。
- 执行业务写入。
- 写入审计。
- 将幂等记录置为 `SUCCEEDED`。
- 同一事务提交。

失败流程：

- 业务校验失败时主事务不更新业务状态。
- 使用独立事务记录 `FAILED` 幂等结果和拒绝审计。
- 系统异常或超时留下 `PROCESSING` 时，由过期策略允许重试。
- 同键同请求返回首次成功结果。
- 同键不同请求返回 `IDEMPOTENCY_KEY_REUSED`。

## 6. 企业隔离

设计约束：

- `enterprise_id` 只能来自登录上下文。
- API 请求体不得提交或覆盖当前企业。
- 所有列表、详情、结果、审计和看板查询必须带企业条件。
- 跨企业按 ID 访问返回 403，并写结构化审计。
- DQ-001 使用全系统唯一检查，但违规详情只返回当前企业可见对象；其他企业对象以“存在不可见冲突对象”表达。

## 7. 数据更正原则

数据治理模块不直接修改 V1.0 已生效业务记录。处理质量问题时只允许：

- 提交处理说明。
- 上传或关联处理附件。
- 关联已有业务更正记录或后续业务更正接口产生的记录。
- 发起同规则重新检查。

如当前 V1.0 尚无专门业务更正接口，`dq_remediation` 记录更正引用和证据；不得通过数据治理接口直接覆盖 `battery`、`acceptance_record`、`inbound_record`、`inventory` 或 `lifecycle_event`。

## 8. 当前结论

本总体设计为待评审稿。评审通过前不得创建实现分支，不得编写生产代码，不得执行正式数据库迁移。
