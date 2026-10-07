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
| I2 | 回收批次、电池登记、重复编码核实、加入批次、提交待验收 | 修改后复核，等待用户最终确认 |
| I3 | 验收、资料补充、验收不通过、入库前置 | 未启动 |

重要边界：当前不能关闭 I2，不能进入 I3，不能创建实现 PR，不能合并 main，除非用户明确给出 I2 最终复核通过结论。

## 2. 当前仓库与分支

- 仓库目录：`D:\ruanjiankaifajishu\battery-recycling-traceability-system`
- 当前分支：`feature/first-slice-implementation`
- 远程仓库：`https://github.com/dyabn/battery-recycling-traceability-system.git`
- 当前最新本地/远程提交：`023f28c docs(implementation): update i2 review evidence [skip ci]`
- I2 代码验证提交：`02a210070585e66839fb1641319fcbd15669407f`
- 最新通过的 GitHub Actions：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37596117520`

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

## 4. 本次会话完成的 I2 整改

用户给出的 I2 正式评审结论是“修改后复核，暂不通过，不关闭 I2，不进入 I3”。本次会话已完成整改并通过 CI。

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
- 原始编码并发锁改为 `battery-original:{enterpriseId}:{sha256前32位}`，避免锁名过长。
- 原始编码锁改为事务同步 `afterCompletion` 释放，确保提交或回滚后释放。
- 核实为不同电池时新档案写入 `duplicate_status=RESOLVED_DIFFERENT`。
- 加入批次和提交批次前检查 `duplicate_status` 与未关闭候选，未核实重复对象拒绝进入待验收。
- 修复幂等并发竞态：同一 `Idempotency-Key` 已存在时不再穿透执行业务；只能返回缓存、处理中、失败或冲突。
- 新增/扩展 I2 MySQL 集成测试到 17 个测试方法，覆盖 `I2-TC-001..044` 所需关键场景。

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

### 4.2 前端修复

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

### 4.3 契约与文档修复

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
- I2 验证记录扩展到 `I2-TC-001..044`。
- `project-state.yaml` 保持 I2 为 `paused-for-review / 修改后复核`，未关闭 I2。
- `contracts/change-log.md` 增加 `CHG-038`。

## 5. 验证结果

本地已执行并通过：

```powershell
$env:JAVA_HOME='D:\Java'; $env:Path='D:\Java\bin;' + $env:Path; mvn -B test
npm run test
npm run build
npx --yes @redocly/cli@2.54.3 lint contracts/api/openapi-first-slice.yaml
python -c "import yaml; yaml.safe_load(open('project-state.yaml', encoding='utf-8')); print('yaml ok')"
git diff --check
```

本地注意：

- 本机没有可用 MySQL 8.4 测试环境，后端 MySQL 集成测试会按 `RUN_MYSQL_TESTS` 条件跳过。
- GitHub Actions 会设置 `RUN_MYSQL_TESTS=true` 并启动 MySQL 8.4，因此 I1/I2 集成测试在 CI 中真实执行。

GitHub Actions：

- Workflow：Implementation CI
- Run：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37596117520`
- 结果：通过
- 验证提交：`02a210070585e66839fb1641319fcbd15669407f`

CI 已验证：

- 后端 `mvn test`；
- MySQL 8.4 启动；
- Flyway V1..V6 迁移；
- 29 张业务表；
- V6 有效批次电池关系唯一约束；
- I1 认证、RBAC、企业隔离、幂等和审计回归；
- I2 批次、电池登记、重复核实、加入批次、提交待验收和追溯集成测试；
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

## 6. 评审包

已在仓库根目录生成新版 I2 评审包，不提交到 Git：

```text
D:\ruanjiankaifajishu\battery-recycling-traceability-system\i2-batch-battery-registration-review.zip
```

SHA-256：

```text
D698401513FECDE6B003BAAB0486F67F7236FE988AA4046E09861ABB0B704266
```

该 ZIP 来自最新 HEAD：

```text
023f28c docs(implementation): update i2 review evidence [skip ci]
```

其中代码验证提交为：

```text
02a210070585e66839fb1641319fcbd15669407f
```

## 7. 当前停在什么位置

当前停在：I2 修改后复核整改完成，等待用户最终复核。

当前状态应保持：

```yaml
implementation_progress:
  current_increment: I2-batch-battery-registration
  increments:
    I2:
      status: paused-for-review
      review_result: 修改后复核
      I2_started: true
```

不要把 I2 改为 completed，除非用户明确回复“I2 复核通过”或等价确认。

不要启动 I3，除非 I2 已确认完成。

## 8. 下一步应该做什么

新会话开始后建议按顺序执行：

1. 读取本文件。
2. 检查当前分支与状态：

```powershell
cd D:\ruanjiankaifajishu\battery-recycling-traceability-system
git status --short
git log --oneline -5
```

3. 如用户要求上传/复核材料，提供：

```text
i2-batch-battery-registration-review.zip
SHA-256: D698401513FECDE6B003BAAB0486F67F7236FE988AA4046E09861ABB0B704266
CI: https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37596117520
```

4. 等待用户的 I2 最终复核结论。

如果用户确认 I2 通过，才做：

- 更新 `project-state.yaml`：I2 -> `completed / 复核通过`；
- 更新 `docs/implementation/i2-batch-battery-registration-verification.md` 文档状态为已确认；
- 更新 `contracts/traceability-index.md` 中 I2 状态为已完成；
- 更新 `contracts/change-log.md` 增加 I2 最终复核通过记录；
- 提交类似：

```text
docs(implementation): confirm i2 batch battery increment
```

然后再按用户指令启动 I3。

I3 建议范围仍然只做：

- 验收登记；
- 资料补充；
- 验收不通过；
- 批次验收处理中/已完成状态；
- 不做入库、库存、数据治理闭环，除非用户扩大范围。

## 9. 避免重复和接口对不上的提醒

- 不要重新设计 C1-C4、技术设计 V1.0 或数据治理 V1.1；这些都已经确认。
- 不要修改 V1 到 V6 历史迁移。
- 不要把原型里的假登录或角色切换器搬回正式前端。
- 后端权限是最终裁决点，前端权限隐藏只是体验优化。
- 所有写接口必须保留 `Idempotency-Key`。
- 企业 ID 必须来自 JWT/当前用户上下文，不能由请求体或查询参数决定。
- 当前 I2 的目标止于“批次提交待验收”，不要提前实现验收、入库或数据治理处理器。
- 如果要继续开发，优先读取：
  - `project-state.yaml`
  - `docs/implementation/i2-batch-battery-registration-verification.md`
  - `contracts/api/openapi-first-slice.yaml`
  - `docs/api/first-slice-api-design.md`
  - `docs/database/first-slice-database-design.md`
  - `docs/database/first-slice-data-dictionary.md`
  - `contracts/traceability-index.md`
