# 数据治理 V1.1 影响分析

文档状态：已确认
关联变更：CR-DG-001
目标基线：V1.1
当前关口：V1.1技术设计评审

## 1. 影响范围

本次技术设计只在已确认的 V1.0 第一切片技术基线上增加数据治理能力，不重写 V1.0 的回收、登记、验收、入库、库存、追溯和审计设计。

纳入范围：

- 固定数据质量规则 DQ-001..DQ-007。
- 企业级规则启用和停用配置。
- 手工数据质量检查任务。
- 检查结果和质量问题。
- 问题分配、处理、复核和关闭。
- 数据质量看板。
- 数据治理权限、企业隔离、审计、幂等和状态机。

排除范围：

- 自定义脚本规则引擎。
- 自动修复业务数据。
- 数据湖、血缘、主数据平台。
- 正式生产代码实现。
- 正式数据库迁移执行。

## 2. 对 V1.0 技术基线的影响

| V1.0 模块 | 影响方式 | 处理原则 |
| --- | --- | --- |
| MOD-BATTERY | 只读校验电池字段、追溯编码、责任企业和生命周期状态。 | 不覆盖已生效电池档案。 |
| MOD-DUPLICATE | 读取候选登记和重复核实记录。 | 使用现有 `battery_registration_candidate`、`duplicate_code_review` 字段。 |
| MOD-ACCEPTANCE | 读取验收结论和资料状态。 | 数据治理不重新判定验收，只做事后一致性检查。 |
| MOD-INBOUND | 读取入库记录、入库时间和入库状态。 | 不恢复或删除入库记录。 |
| MOD-INVENTORY | 读取当前库存责任企业和仓库库位。 | 不直接调整库存，仅要求关联更正或处理证据。 |
| MOD-TRACE | 读取生命周期事件顺序。 | 不直接改写历史事件。 |
| MOD-AUDIT | 复用审计能力记录数据治理操作和拒绝。 | 审计必须包含对象、前后状态、结果和原因。 |
| MOD-IDEMPOTENCY | 复用幂等记录。 | 所有写接口强制 `Idempotency-Key`。 |

## 3. 数据规则与 V1.0 字段对齐

| 规则 | V1.0 字段和约束 | V1.1 技术口径 |
| --- | --- | --- |
| DQ-001 | `battery.system_trace_code`，唯一约束 `uk_battery_trace_code`。 | 全系统唯一检查；结果只向当前企业返回其有权查看的对象。 |
| DQ-002 | `battery.current_responsible_enterprise_id`、`battery.battery_type`、`battery.battery_chemistry`、`battery.lifecycle_status`。 | 核心字段必须完整；允许值“未知”视为已按数据标准填写，不允许说明豁免。 |
| DQ-003 | `battery_registration_candidate.candidate_status`、`duplicate_code_review.review_result`。 | 原始编码重复必须完成候选核实或重复核实。 |
| DQ-004 | `acceptance_record.acceptance_result`、`battery.lifecycle_status`、`inbound_record.inbound_status`、`inbound_record.inbound_at`。 | 入库前必须存在通过验收事实；必要时结合生命周期事件证明顺序。 |
| DQ-005 | `inbound_record.warehouse_id` 与 `warehouse_location.warehouse_id`。 | 入库仓库和库位必须归属一致且均有效。 |
| DQ-006 | `lifecycle_event.occurred_at`。 | 同一电池关键事件时间不得倒序。 |
| DQ-007 | `battery.current_responsible_enterprise_id`、`inventory.enterprise_id`、`inventory.is_current=1`。 | 当前库存企业必须与电池当前责任企业一致。 |

## 4. 技术风险和控制

| 风险 | 控制措施 |
| --- | --- |
| 规则检查泄露跨企业数据。 | 所有查询以登录上下文 `enterprise_id` 为强制条件；DQ-001 全系统唯一只返回当前企业可见对象。 |
| 重复检查生成重复问题。 | `dq_issue` 使用开放问题唯一约束，同一企业、规则、对象只允许一个未关闭问题。 |
| 失败操作污染状态。 | 状态机统一在事务前校验；校验失败只写拒绝审计，不更新业务状态。 |
| 处理过程覆盖原始业务数据。 | 数据治理只记录问题、证据和更正引用，不直接覆盖 V1.0 已生效业务表。 |
| 幂等记录和业务事务不一致。 | 复用 V1.0 幂等策略；成功同事务提交，业务失败独立事务记录失败。 |

## 5. 评审结论

本影响分析用于技术设计评审。当前仅完成设计稿，不批准实施，不执行正式数据库迁移。
