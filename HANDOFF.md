# HANDOFF

本文件用于新会话无历史聊天记录时继续项目。请先阅读本文件，再读取 `project-state.yaml`、`contracts/traceability-index.md` 和对应阶段文档。

## 1. 项目目标

项目名称：面向动力电池回收利用企业的流转协同与追溯管理系统。

目标是按软件工程全过程逐阶段完成一个 PC Web 业务系统，围绕动力电池回收利用企业的业务，逐步实现：

- 回收批次建立；
- 电池包登记；
- 原始编码重复检查与人工核实；
- 批次提交待验收；
- 后续验收、资料补充、入库、库存、追溯；
- 数据治理 V1.1 能力；
- 权限、企业隔离、审计和幂等保护；
- 最终形成可运行、可演示、可追踪的前后端系统。

当前正式实现阶段遵循增量：

| 增量 | 范围 | 状态 |
| --- | --- | --- |
| I0 | 工程骨架、Flyway、CI | 已完成 |
| I1 | 登录、JWT、RBAC、企业隔离、审计、幂等基础 | 已完成 |
| I2 | 回收批次、电池登记、重复编码核实、加入批次、提交待验收 | 已完成，复核通过 |
| I3 | 验收、资料补充、验收不通过、入库前置 | 已完成，复核通过 |
| I4 | 待入库、仓库库位、单块入库、当前库存、入库追溯 | 已实现，暂停待复核 |

重要边界：I2、I3 均已关闭；I4 已按用户确认范围启动并完成本地实现，当前停在 `paused-for-review / 修改后复核`。不要关闭 I4，不要启动后续增量，直到 GitHub Actions MySQL 8.4 验证和人工复核通过。

## 2. 当前仓库与分支

- 仓库目录：`D:\ruanjiankaifajishu\battery-recycling-traceability-system`
- 当前分支：`feature/first-slice-implementation`
- 远程仓库：`https://github.com/dyabn/battery-recycling-traceability-system.git`
- 当前最新本地/远程实现提交：以 `git rev-parse HEAD` 和 `git rev-parse origin/feature/first-slice-implementation` 为准
- I2 代码验证提交：`0571da07eb8e22427a2376603d0605d606cf4a39`
- I3 代码验证提交：`6fee83009925d4fdec505c7f2dfbaeb5b7755425`
- I3 最新通过的 GitHub Actions：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37734497499`
- I3 当前状态：`completed / 复核通过`。
- I4 当前状态：`paused-for-review / 修改后复核`；GitHub Actions MySQL 8.4 验证待取得。
- 测试阶段缺陷修复：后端 Long/long 响应统一序列化为字符串，前端 ID 类型同步为字符串，避免 JavaScript `number` 精度丢失导致详情、编辑保存或加入电池找错对象。

## 3. 已完成的前置工作

已完成并确认：

- Skill V0.1 和项目工程治理骨架；
- C1 业务定位基线；
- C2 全局业务基线；
- C3 第一条业务切片；
- C4 第一切片需求、测试设计和低保真原型；
- 第一切片技术设计 V1.0；
- 数据治理 CR-DG-001 需求基线 V1.1；
- 数据治理 V1.1 技术设计；
- I0 工程骨架；
- I1 身份认证、企业隔离和 RBAC。

关键状态文件：

- `project-state.yaml`
- `contracts/traceability-index.md`
- `contracts/change-log.md`

关键业务/需求/设计文档：

- `docs/business/c2-global-business-baseline.md`
- `docs/business/c3-first-business-slice.md`
- `docs/requirements/c4-first-slice-system-requirements.md`
- `docs/testing/c4-first-slice-test-design.md`
- `docs/design/first-slice-architecture-design.md`
- `docs/database/first-slice-database-design.md`
- `docs/database/first-slice-data-dictionary.md`
- `docs/api/first-slice-api-design.md`
- `docs/security/first-slice-security-design.md`
- `docs/implementation/i1-auth-tenant-rbac-verification.md`
- `docs/implementation/i2-batch-battery-registration-verification.md`
- `docs/implementation/i3-acceptance-verification.md`
- `docs/implementation/i4-inbound-inventory-verification.md`

契约与迁移：

- `contracts/api/openapi-first-slice.yaml`
- `contracts/database/first-slice-schema-design.sql`
- `backend/src/main/resources/db/migration/V1__first_slice_baseline.sql`
- `backend/src/main/resources/db/migration/V2__data_governance_v1_1.sql`
- `backend/src/main/resources/db/migration/V3__initialize_core_roles_and_permissions.sql`
- `backend/src/main/resources/db/migration/V4__expand_idempotency_key_length.sql`
- `backend/src/main/resources/db/migration/V5__correct_core_role_permission_matrix.sql`
- `backend/src/main/resources/db/migration/V6__add_i2_active_batch_battery_guard.sql`

不要修改已经执行过的 V1 到 V6。后续数据库变化必须新增 `V7__xxx.sql` 或更高版本。

## 4. I2 完成情况

I2 最终复核已通过。最新实现提交 `0571da07eb8e22427a2376603d0605d606cf4a39` 已由 Implementation CI run `37629609561` 验证通过；I2 已关闭。I3 最终复核已通过，验证提交 `6fee83009925d4fdec505c7f2dfbaeb5b7755425` 已由 Implementation CI run `37734497499` 验证通过；I3 已关闭。I4 已按用户确认范围启动并完成本地实现，当前等待 CI 和人工复核。

### 4.1 后端修复

修改文件：

- `backend/src/main/java/com/batteryrecycling/traceability/battery/BatteryDtos.java`
- `backend/src/main/java/com/batteryrecycling/traceability/battery/BatteryService.java`
- `backend/src/main/java/com/batteryrecycling/traceability/batch/RecycleBatchDtos.java`
- `backend/src/main/java/com/batteryrecycling/traceability/batch/RecycleBatchService.java`
- `backend/src/main/java/com/batteryrecycling/traceability/idempotency/IdempotencyService.java`
- `backend/src/test/java/com/batteryrecycling/traceability/i2/I2BatchBatteryIntegrationTest.java`

已修复：

- DTO 增加 `@Size`、`@Digits` 等字段长度/精度约束，避免超长请求直接打到数据库变成 500。
- 原始编码并发锁改为 `bat:` + SHA-256 前 60 位，锁名固定 64 字符，避免 MySQL 用户锁名称超过限制。
- 原始编码锁改为事务同步 `afterCompletion` 释放，确保提交或回滚后释放。
- 核实为不同电池时新档案写入 `duplicate_status=RESOLVED_DIFFERENT`。
- 加入批次和提交批次前检查 `duplicate_status` 与未关闭候选，未核实重复对象拒绝进入待验收。
- 修复幂等并发竞态：同一 `Idempotency-Key` 已存在时不再穿透执行业务；只能返回缓存、处理中、失败或冲突。
- 新增/扩展 I2 MySQL 集成测试到 17 个测试方法，覆盖 `I2-TC-001..046` 所需关键场景。

重要测试覆盖包括：

- 批次编号并发唯一；
- 无原始编码登记；
- 必填字段缺失；
- 原始编码 100/101 长度边界；
- 同原始编码并发登记只生成一条有效电池和一条候选；
- 同候选并发核实只成功一次；
- 未关闭重复候选阻止加入批次或提交；
- 非草稿批次/非登记状态电池不能加入；
- 多电池提交失败整体回滚；
- 同幂等键并发提交只执行一次；
- 不同幂等键并发提交同一批次只成功一次；
- OpenAPI 错误状态码契约。

## 5. I3 当前实现情况

I3 已按用户确认方案实现，并已复核关闭。

新增/修改的主要能力：

- `GET /api/v1/acceptances/pending` 查询待处理验收任务，包含待验收和待补充资料电池；
- `POST /api/v1/batteries/{id}/acceptances` 登记 `PASS / NEED_SUPPLEMENT / REJECT` 三种验收结果；
- `POST /api/v1/batteries/{id}/acceptance-supplements` 保存补充说明或附件并重新提交；
- `POST /api/v1/attachments` 创建临时附件，`GET /api/v1/attachments/{id}/download` 做权限内下载保护；
- `DELETE /api/v1/acceptance-records/{id}` 拒绝删除已生效验收记录并写审计；
- 批次状态根据成员验收进度推进到 `ACCEPTANCE_PROCESSING` 或 `COMPLETED`；
- 生命周期追溯增加验收通过、待补充、已补充、不通过事件；
- 追溯兼容字段返回验收三项、验收说明、补充说明和附件下载入口，前端追溯页已展示并通过带 JWT 的请求下载附件；
- 附件上传保存真实文件到 `app.attachment.storage-dir` 配置目录，默认 `../data/attachments`，下载返回原始字节，上传幂等摘要纳入文件内容 SHA-256；
- 附件存储文件名由服务端生成，事务回滚后清理已写文件，测试覆盖重建服务实例后仍可下载；
- 缺少说明、无效附件和 DTO 校验失败等拒绝路径写独立事务拒绝审计；
- 前端新增待处理验收页面，批次详情新增验收进度摘要。

新增测试：

- `backend/src/test/java/com/batteryrecycling/traceability/i3/I3AcceptanceIntegrationTest.java`
- `frontend/src/views/i3-pages.test.ts`

本地已验证：

- 后端 `mvn -B test` 通过；本地未设置 `RUN_MYSQL_TESTS=true`，I2/I3 MySQL 集成测试按环境变量跳过；
- 前端 Vitest 17 个测试通过；
- 前端生产构建通过；
- Redocly OpenAPI lint 退出码为 0；删除保护接口保留无 2xx 响应的语义警告。

最新通过的 GitHub Actions 已验证：

- Workflow：Implementation CI
- Run：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37734497499`
- 验证提交：`6fee83009925d4fdec505c7f2dfbaeb5b7755425`
- 结果：通过
- `RUN_MYSQL_TESTS=true` 下实际执行 I1/I2/I3 集成测试，`I3AcceptanceIntegrationTest` 6 个测试通过，覆盖真实附件下载、附件内容幂等、无效附件组合、追溯历史详情、重建服务实例后下载、回滚文件清理和拒绝审计。

本轮第二轮整改提交 `6fee83009925d4fdec505c7f2dfbaeb5b7755425` 已通过最新 GitHub Actions 验证并通过人工复核；I3 已关闭。用户已明确确认启动 I4，当前 I4 已完成本地实现，等待 CI 和人工复核。

## 6. I4 当前实现情况

I4 已按用户确认范围实现入库库存闭环，不包含数据治理闭环、出库、企业间流转和综合利用。

新增/修改的主要能力：

- `GET /api/v1/inbounds/pending` 查询本企业 `ACCEPTED_PENDING_INBOUND` 待入库电池；
- `GET /api/v1/warehouses` 查询本企业启用仓库；
- `GET /api/v1/warehouses/{id}/locations` 查询启用库位；
- `POST /api/v1/batteries/{id}/inbounds` 办理单块电池入库；
- `GET /api/v1/inventory?systemTraceCode=` 查询当前库存并支持追溯编码筛选；
- `DELETE /api/v1/inbound-records/{id}` 拒绝删除生效入库记录并审计；
- 入库在同一事务中生成 `inbound_record`、`inventory`、电池 `IN_STOCK` 状态、`INBOUND_COMPLETED` 生命周期事件、成功审计和幂等结果；
- 仓库停用、库位停用、仓库库位错配、跨企业访问、非待入库状态和重复提交均拒绝并保持业务数据不变；
- `BatteryService.trace` 的兼容 `details.inbound` 返回入库单、仓库、库位、库存记录和入库人；
- dev profile 新增 `R__dev_demo_warehouses.sql`，提供演示仓库和库位数据，不修改生产迁移；
- 前端新增待入库和当前库存页面，仓库切换会清空旧库位，所有 ID 继续按字符串处理；
- 默认首页对仓库管理员优先跳转到 `/inbounds/pending`。

新增测试：

- `backend/src/test/java/com/batteryrecycling/traceability/i4/I4InboundInventoryIntegrationTest.java`
- `frontend/src/views/i4-pages.test.ts`

本地已验证：

- 后端 `mvn -B test` 通过；本地未设置 `RUN_MYSQL_TESTS=true`，I2/I3/I4 MySQL 集成测试按环境变量跳过；
- 前端 Vitest 22 个测试通过；
- 前端生产构建通过；
- OpenAPI 契约测试通过，I4 入库响应码、删除保护路径和 LongId 字符串字段已断言。

待验证：

- Implementation CI 在 MySQL 8.4、`RUN_MYSQL_TESTS=true` 下实际执行 `I4InboundInventoryIntegrationTest`；
- Redocly、后端测试、前端测试、前端构建和 YAML 检查在 CI 中通过；
- CI 通过后更新验证提交、Actions run、评审包 SHA-256，再由用户复核决定是否关闭 I4。

## 7. I2 前端修复补充

修改文件：

- `frontend/src/api/i2.ts`
- `frontend/src/api/i2.test.ts`
- `frontend/src/views/batch/BatchListView.vue`
- `frontend/src/views/batch/BatchListView.test.ts`
- `frontend/src/views/battery/BatteryRegisterView.vue`
- `frontend/src/views/battery/DuplicateResolutionView.vue`
- `frontend/vite.config.ts`
- `frontend/package.json`
- `frontend/package-lock.json`

已修复：

- 批次列表中草稿批次显示“编辑”按钮；非草稿批次不显示编辑入口。
- 点击编辑会回填全部字段，保存调用 `PUT /recycle-batches/{id}`。
- 前端写接口不再每次点击都随机生成幂等键；改为按“操作类型 + 请求内容指纹”缓存。
- 请求成功后清除幂等键；请求失败或超时保持 key 以支持重试。
- 请求内容变化时生成新的幂等 key。
- 创建批次、修改批次、电池登记、重复核实、加入批次、批次提交都走该策略。
- 表单输入增加 `maxlength`，与数据库字段长度和 OpenAPI 对齐。
- 增加 Vitest + happy-dom 组件测试，覆盖草稿编辑入口与字段回填。
- 增加前端 API 测试，覆盖超时后重试复用同一幂等键，以及内容变化生成新键。

## 8. I2 契约与文档修复补充

修改文件：

- `contracts/api/openapi-first-slice.yaml`
- `contracts/traceability-index.md`
- `contracts/change-log.md`
- `docs/api/first-slice-api-design.md`
- `docs/database/first-slice-database-design.md`
- `docs/database/first-slice-data-dictionary.md`
- `docs/implementation/i2-batch-battery-registration-verification.md`
- `project-state.yaml`

已修复：

- OpenAPI 补充字段 `maxLength`。
- OpenAPI 补充运行时可能返回的 `400` 和 `404`：
  - `POST /recycle-batches/{id}/submit` -> `400`
  - `POST /batteries/duplicate-check` -> `400`
  - `POST /battery-registration-candidates/{id}/duplicate-resolution` -> `400`
  - `POST /recycle-batches/{id}/batteries` -> `400`
  - `GET /batteries/{id}/trace` -> `404`
- 数据库设计补充 V6 `active_battery_id` 生成列和 `uk_rbb_active_battery` 唯一约束说明。
- 数据字典补充 `recycle_batch_battery.active_battery_id`。
- 追踪索引补充 `FR-C4-008 -> V6 -> I2-TC-035/042` 关系。
- I2 验证记录扩展到 `I2-TC-001..046`。
- `project-state.yaml` 中 I2 和 I3 均已更新为 `completed / 复核通过`；当前 I4 已更新为 `paused-for-review / 修改后复核`。
- `contracts/change-log.md` 增加 `CHG-038`、`CHG-039` 和 I2 最终复核通过记录。

## 9. 验证结果

本地已执行并通过：

```powershell
$env:JAVA_HOME='D:\Java'; $env:Path='D:\Java\bin;' + $env:Path
cd backend
mvn -B test
cd ..\frontend
npm test -- --run
npm run build
cd ..
npx @redocly/cli lint contracts/api/openapi-first-slice.yaml
python -c "import yaml; yaml.safe_load(open('project-state.yaml', encoding='utf-8')); print('yaml ok')"
git diff --check
```

本地注意：

- 本机没有可用 MySQL 8.4 测试环境，后端 MySQL 集成测试会按 `RUN_MYSQL_TESTS` 条件跳过。
- GitHub Actions 会设置 `RUN_MYSQL_TESTS=true` 并启动 MySQL 8.4，因此 I1/I2/I3 集成测试需要在 CI 中真实执行。
- I4 新增集成测试也必须在 CI 中真实执行；本地跳过不能作为关闭 I4 的证据。

I2 GitHub Actions：

- Workflow：Implementation CI
- Run：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37629609561`
- 结果：通过
- 验证提交：`0571da07eb8e22427a2376603d0605d606cf4a39`

CI 已验证：

- 后端 `mvn test`；
- MySQL 8.4 启动；
- Flyway V1..V6 迁移；
- `RUN_MYSQL_TESTS=true`；
- 29 张业务表；
- V6 有效批次电池关系唯一约束；
- I1 认证、RBAC、企业隔离、幂等和审计回归；
- I2 批次、电池登记、重复核实、加入批次、提交待验收和追溯集成测试，`I2BatchBatteryIntegrationTest` 为 Tests run 17、Skipped 0、Failures 0、Errors 0；
- 双线程并发加入两个批次测试；
- 原始编码锁名 64 字符上限和 OpenAPI YAML 契约断言测试；
- 同原始编码并发登记；
- 候选并发核实；
- 同幂等键并发提交；
- 不同幂等键并发提交；
- 未核实重复对象阻断；
- 字段长度边界；
- OpenAPI 错误状态码契约；
- Redocly OpenAPI 校验；
- 前端 `npm ci`；
- 前端 Vitest；
- 前端生产构建。

I3 最新通过的 GitHub Actions：

- Workflow：Implementation CI
- Run：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37734497499`
- 结果：通过
- 验证提交：`6fee83009925d4fdec505c7f2dfbaeb5b7755425`
- 已验证 MySQL 8.4、Flyway V1..V6、RUN_MYSQL_TESTS=true、I1/I2/I3 集成测试、Redocly、前端测试和构建。

## 10. 评审包

本轮 I3 评审包已生成在仓库根目录，文件名为：

```text
D:\ruanjiankaifajishu\battery-recycling-traceability-system\i3-acceptance-supplement-review.zip
```

该包由当前 HEAD 生成，已排除所有旧版 ZIP，避免评审材料混淆。旧 I2 评审包如仍存在，仅代表 I2 收口材料，不应用作 I3 复核材料。

I2 旧包路径：

```text
D:\ruanjiankaifajishu\battery-recycling-traceability-system\i2-batch-battery-registration-review.zip
```

SHA-256 以交付时 `Get-FileHash` 输出为准；不要在本文件中硬编码评审包自身哈希，避免评审包内容与哈希形成循环依赖。

```text
见交付消息或重新执行：
Get-FileHash .\i2-batch-battery-registration-review.zip -Algorithm SHA256
```

该 ZIP 来自当前文档收口 HEAD；以本地命令输出为准：

```text
git rev-parse HEAD
```

其中 I2 代码验证提交为：

```text
0571da07eb8e22427a2376603d0605d606cf4a39
```

其中 I3 最新通过 CI 的代码验证提交为：

```text
6fee83009925d4fdec505c7f2dfbaeb5b7755425
```

## 11. 当前停在什么位置

当前停在：I3 已完成最终复核并关闭，I4 已完成本地实现并暂停待复核。

当前状态应保持：

```yaml
implementation_progress:
  current_increment: I4-inbound-inventory
  increments:
    I2:
      status: completed
      review_result: 复核通过
      confirmed_date: 2026-10-07
      I2_started: true
    I3:
      status: completed
      review_result: 复核通过
      confirmed_date: 2026-10-08
      I3_started: true
    I4:
      status: paused-for-review
      review_result: 修改后复核
      I4_started: true
```

I4 尚未关闭。不要启动后续增量，不要合并到 `main`，不要修改 Flyway V1-V6。

## 12. 下一步应该做什么

新会话开始后建议按顺序执行：

1. 读取本文件。
2. 检查当前分支与状态：

```powershell
cd D:\ruanjiankaifajishu\battery-recycling-traceability-system
git status --short
git log --oneline -5
```

3. 核对 I3 验证证据：

```text
Implementation CI run 37734497499 已确认提交 6fee83009925d4fdec505c7f2dfbaeb5b7755425 通过，覆盖 MySQL 8.4、Flyway V1..V6、RUN_MYSQL_TESTS=true、I3AcceptanceIntegrationTest 6 个测试、Redocly、前端测试和构建。
```

4. 核对 I4 本地实现和验证记录：

```text
docs/implementation/i4-inbound-inventory-verification.md
I4 状态应为 paused-for-review / 修改后复核。
```

5. 推送后等待 Implementation CI，在 MySQL 8.4、`RUN_MYSQL_TESTS=true` 下确认 `I4InboundInventoryIntegrationTest` 实际执行且 skipped 为 0。CI 通过并人工复核前不得关闭 I4。

## 13. 避免重复和接口对不上的提醒

- 不要重新设计 C1-C4、技术设计 V1.0 或数据治理 V1.1；这些都已经确认。
- 不要修改 V1 到 V6 历史迁移。
- 不要把原型里的假登录或角色切换器搬回正式前端。
- 后端权限是最终裁决点，前端权限隐藏只是体验优化。
- 所有写接口必须保留 `Idempotency-Key`。
- 企业 ID 必须来自 JWT/当前用户上下文，不能由请求体或查询参数决定。
- 当前 I4 只实现真实入库与当前库存，不实现数据治理闭环、出库、企业间流转或综合利用。
- 前后端交互中的后端 Long ID 必须按字符串处理，不要在前端使用 `Number(route.params.id)` 或 `number` 类型接收业务 ID。
- 如果要继续开发，优先读取：
  - `project-state.yaml`
  - `docs/implementation/i3-acceptance-verification.md`
  - `docs/implementation/i4-inbound-inventory-verification.md`
  - `docs/implementation/i2-batch-battery-registration-verification.md`
  - `contracts/api/openapi-first-slice.yaml`
  - `docs/api/first-slice-api-design.md`
  - `docs/database/first-slice-database-design.md`
  - `docs/database/first-slice-data-dictionary.md`
  - `contracts/traceability-index.md`
