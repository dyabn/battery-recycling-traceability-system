# 数据治理 V1.1 技术测试设计

文档状态：待评审
关联变更：CR-DG-001

## 1. 测试范围

覆盖数据库、API、权限、企业隔离、状态机、幂等、审计、规则执行、看板 SQL 和追踪链。

## 2. 技术测试用例

| 编号 | 对应需求 | 技术对象 | 测试点 | 预期结果 |
| --- | --- | --- | --- | --- |
| TD-DG-TC-001 | DQ-001 | `TraceCodeUniqueRule` | 全系统追溯编码唯一检查。 | 发现重复；只返回当前企业可见对象。 |
| TD-DG-TC-002 | DQ-002 | `BatteryRequiredFieldRule` | 核心字段为空。 | 生成 DQ-002 问题；“未知”不算空。 |
| TD-DG-TC-003 | DQ-003 | `DuplicateReviewRule` | 候选或重复核实未终结。 | 生成 DQ-003 问题。 |
| TD-DG-TC-004 | DQ-004 | `AcceptanceBeforeInboundRule` | 入库前无通过验收事实。 | 生成 DQ-004 问题。 |
| TD-DG-TC-005 | DQ-005 | `WarehouseLocationRule` | 入库仓库和库位归属不一致。 | 生成 DQ-005 问题。 |
| TD-DG-TC-006 | DQ-006 | `LifecycleSequenceRule` | `lifecycle_event.occurred_at` 倒序。 | 生成 DQ-006 问题。 |
| TD-DG-TC-007 | DQ-007 | `InventoryEnterpriseRule` | `inventory.is_current=1` 且企业不一致。 | 生成 DQ-007 问题。 |
| TD-DG-TC-008 | FR-DG-001 | API/DB | 查询规则列表。 | 返回规则元数据和当前企业启停状态。 |
| TD-DG-TC-009 | FR-DG-002 | API/DB | 未被授权的用户启停规则。 | 返回 403，企业配置保持不变，写治理审计。 |
| TD-DG-TC-010 | FR-DG-003 | API | 尝试编辑算法或脚本。 | 无接口可用或返回 403/404，规则定义不变。 |
| TD-DG-TC-011 | FR-DG-004..006 | API/DB | 发起质量检查。 | 创建 `dq_check_run` 和 `dq_check_result`。 |
| TD-DG-TC-012 | FR-DG-006 | API/DB | 检查执行失败。 | 任务 `FAILED`，不生成问题。 |
| TD-DG-TC-013 | FR-DG-008 | DB | 重复检查同一未关闭问题。 | 唯一约束阻止重复开放问题。 |
| TD-DG-TC-014 | FR-DG-009 | API/DB | 分配问题。 | `OPEN -> ASSIGNED`，写审计。 |
| TD-DG-TC-015 | FR-DG-010..012 | API/DB | 开始处理并提交处理。 | `ASSIGNED -> PROCESSING -> SUBMITTED`。 |
| TD-DG-TC-016 | FR-DG-013 | API | 非责任人提交处理。 | 403，状态不变，写拒绝审计。 |
| TD-DG-TC-017 | FR-DG-014..016 | API/DB | 复核通过。 | `SUBMITTED -> RECHECKING -> CLOSED`。 |
| TD-DG-TC-018 | FR-DG-015 | API/DB | 复核失败。 | `SUBMITTED -> RECHECKING -> REJECTED`。 |
| TD-DG-TC-019 | FR-DG-016 | API | 编辑关闭问题。 | 409，状态保持 `CLOSED`。 |
| TD-DG-TC-020 | FR-DG-017 | SQL | 默认最近 30 天看板。 | 按企业统计完成检查、问题、未关闭问题和关闭率。 |
| TD-DG-TC-021 | NFR-DG-003 | API/DB | 跨企业按 ID 访问详情。 | 403，列表、详情、结果、审计、看板均隔离。 |
| TD-DG-TC-022 | NFR-DG-005 | API/DB | 失败操作状态保持。 | 业务状态和版本号不变。 |
| TD-DG-TC-023 | 幂等 | API/DB | 同键同请求重复提交。 | 返回首次结果，不重复写入。 |
| TD-DG-TC-024 | 幂等 | API/DB | 同键不同请求。 | 409 `IDEMPOTENCY_KEY_REUSED`。 |
| TD-DG-TC-025 | 审计 | `dq_operation_audit` | 查询拒绝审计结构。 | 包含对象、前后状态、结果、原因、操作者、时间、`trace_id` 和幂等键摘要。 |
| TD-DG-TC-026 | SQL | MySQL 8.4 | 执行 V1.0 schema + V1.1 增量 SQL。 | 新增 8 张表成功，规则种子和企业规则配置初始化成功。 |
| TD-DG-TC-027 | OpenAPI | Redocly | 严格解析 OpenAPI V1.1。 | 无断链，operationId 唯一。 |
| TD-DG-TC-028 | 追踪 | 文档 | `UR->FR->AC->TC->UI->API->DB->MODULE`。 | 全链路完整。 |
| TD-DG-TC-029 | FR-DG-012 | API/DB | 没有整改证据时提交处理。 | 数据库 CHECK 和 API 均拒绝，问题状态保持不变。 |
| TD-DG-TC-030 | FR-DG-014..016 | API | 客户端尝试自行指定复核通过。 | OpenAPI 无 `passed` 字段和提交结果接口，服务端拒绝客户端判定。 |
| TD-DG-TC-031 | FR-DG-014..016 | API/DB | 同规则、同对象机器复核通过才关闭。 | `dq_recheck` 关联 `dq_check_run` 和 `dq_check_result`；只有结果通过才关闭。 |
| TD-DG-TC-032 | NFR-DG-003 | 权限 | 系统管理员只读看板和问题。 | 系统管理员可查看看板和问题，不能启停规则、处理整改或执行复核。 |
| TD-DG-TC-033 | FR-DG-008 | DB | 未关闭问题并发生成。 | `enterprise_id + rule_code + object_type + object_identity` 规范化唯一约束只允许一条未关闭问题。 |
| TD-DG-TC-034 | FR-DG-008、016 | DB | 问题关闭后同一对象再次违规。 | 已关闭问题不参与未关闭唯一键，可创建新问题。 |
| TD-DG-TC-035 | FR-DG-006 | 事务 | 检查中途失败。 | 回滚本轮检查结果和质量问题，仅保留失败运行和审计。 |
| TD-DG-TC-036 | FR-DG-001 | DB | 新企业默认规则配置。 | 企业获得 DQ-001..DQ-007 共 7 条启用配置，缺失配置视为初始化异常。 |

## 3. NFR 技术验证

| NFR | 技术验证 |
| --- | --- |
| NFR-DG-001 | TD-DG-TC-015、TD-DG-TC-025、TD-DG-TC-029 |
| NFR-DG-002 | TD-DG-TC-014..019、TD-DG-TC-025、TD-DG-TC-031 |
| NFR-DG-003 | TD-DG-TC-009、TD-DG-TC-016、TD-DG-TC-021、TD-DG-TC-032 |
| NFR-DG-004 | TD-DG-TC-010 |
| NFR-DG-005 | TD-DG-TC-011、TD-DG-TC-012、TD-DG-TC-020、TD-DG-TC-022、TD-DG-TC-033、TD-DG-TC-034、TD-DG-TC-035、TD-DG-TC-036 |

## 4. 自动检查要求

- OpenAPI 使用 Redocly 严格解析。
- SQL 使用 MySQL 8.4 实际执行。
- YAML 使用 PyYAML 严格解析。
- Git diff 使用 `git diff --check`。

## 5. 当前结论

技术测试设计待评审。评审通过前不得创建实现分支。
