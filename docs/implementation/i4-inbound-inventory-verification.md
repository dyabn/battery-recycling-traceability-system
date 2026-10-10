# I4 入库库存验证记录

文档状态：待 GitHub Actions MySQL 8.4 验证

## 验证对象

- 增量：I4-inbound-inventory
- 分支：feature/first-slice-implementation
- 实现提交：`df9b2de9afabea1032747506335e25b24f210969`
- 验证提交：待 GitHub Actions MySQL 8.4 验证
- GitHub Actions：待执行
- 结论：I4 已完成本地实现并停在 `paused-for-review / 修改后复核`；等待 MySQL 8.4 CI 证据和人工复核后才能关闭。

## I4 范围

本增量实现完整第一业务切片的入库与库存段：

`回收批次 -> 电池登记 -> 提交验收 -> 验收通过 -> 待入库 -> 办理入库 -> 形成库存 -> 追溯可见`

包含待入库列表、启用仓库和库位查询、仓库库位级联选择、单块电池入库、当前库存查询、入库生命周期事件、成功/拒绝审计、幂等、并发、事务、企业隔离、删除保护、开发环境演示仓库库位数据和前端待入库/库存页面。

不包含数据治理闭环、出库、企业间流转、综合利用、仓库库位 CRUD、批量入库、移库、盘点或库存调整。

## 实现接口

| 接口 | 结果 |
| --- | --- |
| `GET /api/v1/inbounds/pending` | 已实现 |
| `GET /api/v1/warehouses` | 已实现 |
| `GET /api/v1/warehouses/{id}/locations` | 已实现 |
| `POST /api/v1/batteries/{id}/inbounds` | 已实现 |
| `GET /api/v1/inventory?systemTraceCode=` | 已实现，返回当前责任企业、仓库、库位和电池状态 |
| `GET /api/v1/batteries/{id}/trace` | 已增强显示入库单、仓库、库位和库存记录 |
| `DELETE /api/v1/inbound-records/{id}` | 已实现删除保护，拒绝删除并写审计 |

所有数据库 Long ID 在 JSON 和前端类型中继续按数字字符串处理。`InventoryItem` 返回 `currentResponsibleEnterpriseId`、`currentResponsibleEnterpriseName` 和 `lifecycleStatus`，满足 FR-C4-018 / AC-011 对当前责任企业和状态展示的要求。

## 状态转换

| 当前状态 | 操作 | 下一状态 |
| --- | --- | --- |
| `ACCEPTED_PENDING_INBOUND` | 办理入库 | `IN_STOCK` |

成功入库在同一事务中写入 `inbound_record`、`inventory`、电池状态、`INBOUND_COMPLETED` 生命周期事件、成功审计和幂等结果。失败、越权和跨企业路径写拒绝审计，业务状态保持不变。

## 数据库与迁移

- 未修改 Flyway V1-V6。
- 未新增结构迁移；I4 使用既有 `warehouse`、`warehouse_location`、`inbound_record` 和 `inventory` 表。
- 新增 dev profile 可重复执行演示数据脚本：`backend/src/main/resources/db/devdata/R__dev_demo_warehouses.sql`。

## 测试结果

| 编号 | 场景 | 当前结果 |
| --- | --- | --- |
| I4-TC-001 | 正常入库生成一条入库记录和一条当前库存 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-002 | 电池变为 `IN_STOCK`，追溯包含入库详情 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-003 | 待入库列表只显示本企业 `ACCEPTED_PENDING_INBOUND` 电池 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-004 | 非待入库状态不出现在列表并拒绝入库 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-005 | 仓库、库位为空返回 400，状态不变 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-006 | 停用仓库、停用库位和仓库库位错配均被拒绝 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-007 | 非待入库状态返回 `INVALID_BATTERY_STATE` | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-008 | 跨企业电池、仓库、库位访问被拒绝 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-009 | 同一幂等键同请求返回首次结果，不重复写入 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-010 | 同一幂等键配不同库位返回 `IDEMPOTENCY_KEY_REUSED` | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-011 | 两个请求并发入库只有一个成功 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-012 | 当前库存唯一约束保证一块电池只有一条有效库存 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-013 | 失败时入库、库存、电池状态和事件整体回滚 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-014 | 失败、越权和跨企业操作均有审计 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-015 | 删除有效入库记录返回 409 且原记录保留 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-016 | 仓库管理员权限成功，其他角色写操作返回 403 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-017 | 业务主管可以查询库存但不能办理入库 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-018 | 库存只返回当前记录，并支持系统追溯编码查询 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-019 | 入库时间晚于或等于验收时间 | 本地编译通过，待 MySQL 8.4 CI 实执行 |
| I4-TC-020 | 前端仓库切换时清空旧库位，禁止错配提交 | 通过 |
| I4-TC-021 | 前端 ID 全程保持字符串，不发生 JavaScript 精度丢失 | 通过 |
| I4-TC-022 | 完整 I1-I4 主流程和 I1-I3 回归 | 本地回归通过，待 MySQL 8.4 CI 实执行 |

## 测试证据映射

| 测试方法 / 文件 | 覆盖编号 | 关键断言 |
| --- | --- | --- |
| `I4InboundInventoryIntegrationTest.inboundCreatesEffectiveRecordCurrentInventoryTraceAndInventoryView` | I4-TC-001、002、003、012、015、017、018、019 | 待入库列表、启用仓库库位、入库成功、状态变更、批次保持完成、当前库存唯一、追溯入库详情、库存返回责任企业和状态、`is_current=0` 历史库存不返回、不匹配追溯编码返回空结果、删除保护 |
| `I4InboundInventoryIntegrationTest.validationWarehouseLocationAndInvalidStatesAreRejectedAtomically` | I4-TC-004、005、006、007、013、014 | 同企业非待入库状态不出现在待入库列表，非待入库状态拒绝入库，缺仓库/缺库位/缺幂等头返回 400，停用仓库、停用库位、仓库库位错配、仓库不存在、库位不存在均拒绝并审计，通过预置当前库存触发库存唯一约束，证明入库记录写入后异常仍整体回滚 |
| `I4InboundInventoryIntegrationTest.idempotencyAndConcurrentInboundAllowOnlyOneEffectiveInventory` | I4-TC-009、010、011、012 | 同键同请求复用结果、同键异请求冲突、不同键并发同电池仅一笔成功、当前库存唯一 |
| `I4InboundInventoryIntegrationTest.enterpriseIsolationAndRolePermissionsAreEnforced` | I4-TC-008、014、016、017 | 跨企业电池/仓库/库位拒绝、列表隔离、仓库管理员成功边界、主管/回收/系统管理员写拒绝、跨企业和角色越权拒绝审计 |
| `frontend/src/views/i4-pages.test.ts` | I4-TC-020、021 | 仓库切换清空旧库位，快速切换时忽略旧仓库库位响应，入库提交使用字符串 ID，库存按追溯编码查询并展示责任企业和状态，追溯页展示入库详情 |
| `FirstSliceOpenApiContractTest` | I4 契约 | 入库响应码、入库记录删除保护路径、`InboundCreateRequest` 和 `InventoryItem` LongId 字符串契约，库存责任企业和状态字段契约 |

## 本地验证命令

```powershell
$env:JAVA_HOME='D:\Java'
$env:Path='D:\Java\bin;' + $env:Path
cd backend
mvn -B test

cd ..\frontend
npm test -- --run
npm run build
```

本地结果：

- 后端 `mvn -B test` 通过；`I4InboundInventoryIntegrationTest` 在未设置 `RUN_MYSQL_TESTS=true` 时跳过。
- 前端 Vitest 23 个测试通过。
- 前端生产构建通过。
- Redocly OpenAPI 校验通过，保留删除保护接口无 2xx 的既有警告。

## GitHub Actions 验证

实现提交 `df9b2de9afabea1032747506335e25b24f210969` 待推送并由 Implementation CI 取得 MySQL 8.4 证据。必须确认：

- MySQL 8.4 启动成功。
- Flyway V1..V6 完整通过且未修改历史迁移。
- `RUN_MYSQL_TESTS=true`。
- `I4InboundInventoryIntegrationTest` 实际执行，不能 skipped。
- CI 新增 Surefire XML 检查，要求 `I4InboundInventoryIntegrationTest` 报告存在、测试数至少 4 且 `skipped=0`、`failures=0`、`errors=0`。
- I1/I2/I3 回归、Redocly、前端测试和构建全部通过。

## 复核结论

- I4 已完成本轮修改后复核阻断项整改和本地测试。
- I4 继续保持 `paused-for-review / 修改后复核`。
- I4 暂不能关闭，I5 不启动；需等待 GitHub Actions MySQL 8.4 验证证据和人工复核。
