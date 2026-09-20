# 第一切片技术测试设计 V1.0

文档名称：第一切片技术测试设计
系统名称：面向动力电池回收利用企业的流转协同与追溯管理系统
当前阶段：system-design
当前检查点：设计评审
文档状态：已确认
输入：C4 第一切片测试设计 V1.0、第一切片 API 设计 V1.0、数据库设计 V1.0

## 1. 测试目标

本测试设计用于验证第一切片技术设计是否能够支撑 C4 已确认的 17 条测试用例。测试覆盖 API、数据库约束、事务、并发、权限、安全、追溯、审计和部署基线，不编写正式自动化测试代码。

## 2. 测试范围

纳入：

- API 契约与请求响应结构。
- 数据库表、约束、索引和状态字段。
- 权限编码和角色矩阵。
- 事务原子性、失败状态保持、并发和幂等。
- 生命周期事件和审计日志边界。
- OpenAPI 和 SQL 设计检查。
- 部署配置和健康检查设计。

排除：

- 正式 JUnit、Vitest 和端到端测试代码。
- 性能压测。
- 第一切片以外业务流程。

## 3. C4 测试到技术支撑矩阵

| C4 测试 | 技术验证 | API | DB | 模块 |
| --- | --- | --- | --- | --- |
| TC-C4-001 | TD-TC-001 | 创建批次、登记电池、提交验收、保存验收、入库、库存、追溯 | `recycle_batch`、`battery`、`acceptance_record`、`inbound_record`、`inventory`、`lifecycle_event` | MOD-BATCH、MOD-BATTERY、MOD-ACCEPTANCE、MOD-INBOUND、MOD-INVENTORY、MOD-TRACE |
| TC-C4-002 | TD-TC-002 | `POST /recycle-batches/{id}/submit` | `recycle_batch`、`audit_log` | MOD-BATCH |
| TC-C4-003 | TD-TC-003 | `POST /recycle-batches/{id}/submit` | `recycle_batch_battery` | MOD-BATCH |
| TC-C4-004 | TD-TC-004 | `POST /batteries`、`POST /batteries/duplicate-check` | `battery`、`battery_registration_candidate` | MOD-BATTERY、MOD-DUPLICATE |
| TC-C4-005 | TD-TC-005 | `POST /battery-registration-candidates/{id}/duplicate-resolution` | `battery_registration_candidate`、`duplicate_code_review`、`battery` | MOD-DUPLICATE |
| TC-C4-006 | TD-TC-006 | `POST /recycle-batches/{id}/submit` | `recycle_batch`、`battery`、`audit_log` | MOD-BATCH |
| TC-C4-007 | TD-TC-007、024、025 | 验收、补充资料和附件绑定接口 | `acceptance_record`、`acceptance_supplement`、`business_attachment`、`lifecycle_event` | MOD-ACCEPTANCE、MOD-ATTACHMENT、MOD-TRACE |
| TC-C4-008 | TD-TC-008 | 验收和入库接口 | `acceptance_record`、`battery`、`audit_log` | MOD-ACCEPTANCE、MOD-INBOUND |
| TC-C4-009 | TD-TC-009 | `POST /batteries/{id}/inbounds` | `warehouse`、`warehouse_location`、`audit_log` | MOD-INBOUND |
| TC-C4-010 | TD-TC-010 | `POST /batteries/{id}/inbounds` | `warehouse`、`warehouse_location`、`audit_log` | MOD-INBOUND |
| TC-C4-011 | TD-TC-011 | `POST /batteries/{id}/inbounds` | `battery`、`audit_log` | MOD-INBOUND |
| TC-C4-012 | TD-TC-012 | 入库接口 | 权限表、`audit_log` | MOD-AUTH、MOD-AUDIT |
| TC-C4-013 | TD-TC-013 | 删除保护统一拦截 | `audit_log` | MOD-AUDIT |
| TC-C4-014 | TD-TC-014 | 追溯接口、审计接口 | `lifecycle_event`、`audit_log` | MOD-TRACE、MOD-AUDIT |
| TC-C4-015 | TD-TC-015、024、025 | 创建、修改批次和附件绑定接口 | `recycle_batch`、`business_attachment` | MOD-BATCH、MOD-ATTACHMENT |
| TC-C4-016 | TD-TC-016 | 验收接口 | `acceptance_record`、`battery`、`recycle_batch` | MOD-ACCEPTANCE |
| TC-C4-017 | TD-TC-017 | 权限和审计接口 | 权限表、`audit_log` | MOD-AUTH、MOD-AUDIT |

## 4. 技术测试用例

| 编号 | 验证目标 | 前置条件 | 操作 | 预期结果 | 类型 |
| --- | --- | --- | --- | --- | --- |
| TD-TC-001 | 正常入库闭环技术支撑 | 有效用户、仓库和库位。 | 依次调用创建批次、登记电池、加入批次、提交验收、验收通过、入库、库存查询、追溯查询。 | API 响应成功；相关表生成记录；电池状态最终 `IN_STOCK`；库存当前记录唯一；追溯事件完整。 | API、DB、事务 |
| TD-TC-002 | 来源必填缺失状态不变 | 草稿批次缺少来源必填字段。 | 提交批次。 | 返回 `BATCH_REQUIRED_FIELD_MISSING`；批次和电池状态不变；记录失败审计。 | 校验、状态 |
| TD-TC-003 | 空批次提交失败 | 草稿批次无有效电池。 | 提交批次。 | 返回 `BATCH_EMPTY`；批次保持 `DRAFT`。 | 边界 |
| TD-TC-004 | 原始编码重复识别 | 已存在相同 `original_code` 电池。 | 先调用 `POST /batteries/duplicate-check`，再调用 `POST /batteries`。 | 重复检查只返回疑似重复和匹配电池 ID，不创建候选；登记接口返回 `DUPLICATE_REVIEW_REQUIRED`、`candidateId` 和 `matchedBatteryIds`；不创建第二条有效 `battery`；候选创建只写审计。 | 约束、异常 |
| TD-TC-005 | 重复编码人工核实 | 存在疑似重复候选记录。 | 分别提交同一电池和不同电池核实结果。 | 同一电池关闭候选并返回原档案；不同电池记录原因、关闭候选并创建新 `battery`；写 `duplicate_code_review`。 | 业务规则 |
| TD-TC-006 | 批次整体校验原子性 | 批次含有效电池和未核实疑似重复电池。 | 提交批次。 | 整批失败；所有电池状态不变；事务回滚。 | 事务 |
| TD-TC-007 | 待补充资料与重新提交 | 电池待验收。 | 验收为待补充资料，再补充资料。 | 资料不足说明必填；状态先到 `PENDING_SUPPLEMENT` 再回到 `PENDING_ACCEPTANCE`；事件完整。 | 状态、追溯 |
| TD-TC-008 | 验收不通过不可入库 | 电池待验收。 | 保存不通过，再尝试入库。 | 不通过原因必填；电池 `ACCEPTANCE_REJECTED`；入库返回 `INVALID_BATTERY_STATE`。 | 状态 |
| TD-TC-009 | 入库必填校验 | 电池已验收待入库。 | 仓库或库位为空时入库。 | 返回校验错误；不生成入库和库存；状态不变。 | 校验 |
| TD-TC-010 | 仓库库位有效性 | 电池已验收待入库。 | 使用停用仓库、停用库位或错配库位入库。 | 返回对应错误码；写入库失败审计；状态不变。 | DB、审计 |
| TD-TC-011 | 非法状态入库 | 电池处于非已验收待入库状态。 | 办理入库。 | 返回 `INVALID_BATTERY_STATE`；状态不变。 | 状态 |
| TD-TC-012 | 单点越权拒绝 | 无入库权限用户登录。 | 调用入库接口。 | 返回 403 和 `FORBIDDEN_OPERATION`；写审计；业务状态不变。 | 安全 |
| TD-TC-013 | 已生效记录删除保护 | 已存在验收或入库记录。 | 尝试 `DELETE /api/v1/acceptance-records/{id}`、`DELETE /api/v1/inbound-records/{id}` 或 `DELETE /api/v1/lifecycle-events/{id}`。 | 统一过滤器或拒绝控制器返回 HTTP 409 和 `EFFECTIVE_RECORD_DELETE_FORBIDDEN`；原记录保留；写审计。 | 安全、审计 |
| TD-TC-014 | 追溯和审计查询 | 电池经历正常或异常流程。 | 查询追溯和审计。 | 生命周期事件包含状态变化；审计包含失败和越权原因。 | 追溯 |
| TD-TC-015 | 选填字段为空 | 批次只填写必填来源字段。 | 创建批次并提交前校验。 | 批次创建成功；关联单据等选填字段为空不阻断。 | 字段 |
| TD-TC-016 | 验收必填失败状态保持 | 电池待验收，批次待验收。 | 验收结果或三项检查字段为空；或资料不足/不通过说明为空。 | 返回 `ACCEPTANCE_REQUIRED_FIELD_MISSING`；不写最终验收记录；批次和电池均保持待验收。 | 校验、事务 |
| TD-TC-017 | 权限矩阵表驱动验证 | 初始化五类用户。 | 按角色权限矩阵逐项调用接口。 | 允许项成功；拒绝项返回 403 并审计；系统管理员只能管理权限和查看审计。 | 权限 |
| TD-TC-018 | 并发提交同一批次 | 两个用户或两个请求同时提交同一草稿批次。 | 并发调用提交接口。 | 只有一个成功；另一个返回 `DUPLICATE_SUBMISSION` 或版本冲突；无重复事件。 | 并发 |
| TD-TC-019 | 重复点击入库 | 同一电池快速重复调用入库。 | 两次调用入库接口。 | 只有一条有效当前库存；第二次通过幂等记录、状态条件或当前库存唯一约束返回首次结果或冲突。 | 幂等、唯一约束 |
| TD-TC-020 | OpenAPI 契约检查 | OpenAPI 文件存在。 | 使用 Redocly 解析工具检查。 | `npx.cmd --yes @redocly/cli@latest lint .\contracts\api\openapi-first-slice.yaml` 返回 valid；路径、请求、响应和安全方案完整。 | 契约 |
| TD-TC-021 | SQL 设计检查 | SQL 设计文件存在。 | 在 MySQL 8 兼容环境中解析建表语句。 | 21 张表、外键、唯一约束和索引无明显冲突。 | DB |
| TD-TC-022 | 部署配置检查 | 部署设计已完成。 | 检查环境变量、端口、健康检查、日志、备份和初始化说明。 | 配置项完整，不含密钥明文。 | 部署 |
| TD-TC-023 | 企业数据隔离 | 企业 A 和企业 B 均有批次、电池、库存、仓库和库位。 | 企业 A 用户查询或操作企业 B 的批次、电池、库存、追溯，或将企业 B 电池加入企业 A 批次。 | 返回 403 或业务拒绝；记录跨企业拒绝审计；Repository 更新必须同时带主键和 `enterprise_id` 条件。 | 安全、数据隔离 |
| TD-TC-024 | 附件跨企业绑定拒绝 | 企业 A 和企业 B 均有临时附件。 | 企业 A 用户在创建批次或补充资料时传入企业 B 的 `attachmentId`。 | 返回 403 或业务拒绝；附件仍保持原状态；写入跨企业附件绑定拒绝审计。 | 安全、附件 |
| TD-TC-025 | 临时附件过期拒绝 | 当前用户存在已过期 `TEMP` 附件。 | 创建批次或补充资料时传入过期 `attachmentId`。 | 返回附件过期错误；业务主事务不提交；附件不绑定；业务状态保持不变。 | 附件、事务 |

## 5. NFR 技术验证

| NFR | 技术验证 |
| --- | --- |
| NFR-C4-001 | TD-TC-001、007、014 验证生命周期事件。 |
| NFR-C4-002 | TD-TC-002、006、009、010、011、016、025 验证失败状态保持。 |
| NFR-C4-003 | TD-TC-012、017、023、024 验证权限默认拒绝、企业隔离和审计。 |
| NFR-C4-004 | TD-TC-013 验证已生效记录删除保护。 |
| NFR-C4-005 | 技术设计评审确认原型不作为正式代码。 |

## 6. 并发和幂等验证

| 场景 | 验证点 |
| --- | --- |
| 两人同时提交同一批次 | 批次 `version` 和状态条件更新生效。 |
| 重复点击验收 | 验收幂等键和电池状态条件生效。 |
| 重复点击入库 | 幂等记录、电池状态条件更新和 `inventory.current_battery_id` 当前库存唯一约束生效。 |
| 两人同时登记相同原始编码 | 原始编码不唯一；进入重复核实而不是数据库异常。 |
| 已验收电池再次验收 | 返回状态错误，不新增最终验收。 |
| 已入库电池再次入库 | 返回冲突，不新增库存。 |
| 幂等键复用不同请求体 | 返回 `IDEMPOTENCY_KEY_REUSED`，不执行业务操作。 |

## 7. 技术评审检查项

| 检查项 | 设计支撑 |
| --- | --- |
| C4 基线未被擅自修改 | 本阶段只新增技术设计材料。 |
| 技术栈已确认 | 总体设计第 3 节。 |
| 模块边界清晰 | 总体设计和架构设计模块表。 |
| ER 图和数据字典完整 | 数据库设计和数据字典。 |
| 状态转换有数据库实现方案 | 数据库状态字段、事务设计和 API 事务表。 |
| 17 个测试用例都有 API 和 DB 支撑 | 本文第 3 节。 |
| 权限矩阵映射到权限编码 | 安全设计。 |
| 事务、并发、幂等完整 | 总体设计、架构设计和本文第 6 节。 |
| 生命周期事件与审计边界清晰 | 数据库设计、API 设计、安全设计。 |
| OpenAPI 文件可解析 | TD-TC-020 已使用 Redocly 验证通过。 |
| SQL 设计无明显语法和约束冲突 | 待 TD-TC-021 使用 MySQL 8 验证。 |
| 追踪链完整 | `contracts/traceability-index.md`。 |
| 未编写正式业务代码 | 本阶段只新增文档和契约。 |
| 未扩展到第一切片以外 | 所有设计均限定第一切片。 |
