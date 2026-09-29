# I2 回收批次与电池登记验证记录

文档状态：待评审

## 验证对象

- 增量：I2-batch-battery-registration
- 分支：feature/first-slice-implementation
- 验证提交：8c0707c
- GitHub Actions：https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/36561591132
- 结论：I2 已实现并通过自动化验证，等待正式评审。

## I2 范围

本增量实现“回收批次创建 -> 电池登记 -> 重复编码检查与人工核实 -> 电池加入批次 -> 批次提交待验收”。

不包含验收登记、补充资料、验收不通过、入库、库存、数据质量规则执行、治理问题整改与复核、正式附件上传、合规报送和删除已生效记录。

## 实现接口

| 接口 | 结果 |
| --- | --- |
| `POST /api/v1/recycle-batches` | 已实现 |
| `GET /api/v1/recycle-batches` | 已实现 |
| `GET /api/v1/recycle-batches/{id}` | 已实现 |
| `PUT /api/v1/recycle-batches/{id}` | 已实现 |
| `POST /api/v1/recycle-batches/{id}/submit` | 已实现 |
| `POST /api/v1/batteries` | 已实现 |
| `POST /api/v1/batteries/duplicate-check` | 已实现 |
| `POST /api/v1/battery-registration-candidates/{id}/duplicate-resolution` | 已实现 |
| `POST /api/v1/recycle-batches/{id}/batteries` | 已实现 |
| `GET /api/v1/batteries/{id}/trace` | 已实现 |

所有写接口继续使用 I1 确认的 `Idempotency-Key`、统一 Envelope、JWT、RBAC、企业隔离和审计机制。

## 实现页面

| 页面 | 结果 |
| --- | --- |
| 回收批次列表 | 已实现 |
| 新建/编辑草稿批次 | 已实现 |
| 回收批次详情 | 已实现 |
| 电池登记 | 已实现 |
| 重复编码人工核实 | 已实现 |
| 电池生命周期时间线 | 已实现 |

## 数据库与迁移

- 继续使用 V1、V2、V3、V4、V5，未修改已执行迁移。
- 新增 `V6__add_i2_active_batch_battery_guard.sql`。
- V6 为 `recycle_batch_battery` 增加 `active_battery_id` 生成列和 `uk_rbb_active_battery` 唯一索引，用于兜底防止同一电池同时存在于两个有效批次。
- CI 已验证业务表总数仍为 29，V6 约束存在。

## 测试结果

| 编号 | 场景 | 结果 |
| --- | --- | --- |
| I2-TC-001 | 使用完整必填信息创建草稿批次 | 通过 |
| I2-TC-002 | 可选字段为空仍能创建批次 | 通过 |
| I2-TC-003 | 缺少任一必填字段时拒绝创建且数据库无记录 | 通过 |
| I2-TC-004 | 批次编号自动生成且不重复 | 通过 |
| I2-TC-005 | 草稿批次允许修改 | 通过 |
| I2-TC-006 | 非草稿批次禁止修改 | 通过 |
| I2-TC-007 | 批次列表、详情只返回当前企业数据 | 通过 |
| I2-TC-008 | 跨企业访问批次被拒绝并写拒绝审计 | 通过 |
| I2-TC-009 | 无原始编码的电池可以登记 | 通过 |
| I2-TC-010 | 新原始编码登记成功并产生追溯事件 | 通过 |
| I2-TC-011 | 电池类型或化学体系缺失时拒绝登记 | 通过 |
| I2-TC-012 | 系统追溯编码由服务端生成并由数据库唯一约束兜底 | 通过 |
| I2-TC-013 | 重复检查接口不创建电池、候选或生命周期事件 | 通过 |
| I2-TC-014 | 重复编码登记只创建候选，不创建有效电池 | 通过 |
| I2-TC-015 | `SAME_BATTERY` 关闭候选并返回已有电池 | 通过 |
| I2-TC-016 | `DIFFERENT_BATTERY` 缺少原因时拒绝处理 | 通过 |
| I2-TC-017 | `DIFFERENT_BATTERY` 创建新电池、核实记录和生命周期事件 | 通过 |
| I2-TC-018 | 已关闭候选不能再次核实 | 通过 |
| I2-TC-019 | 候选状态条件更新防止重复核实 | 通过 |
| I2-TC-020 | 跨企业候选、匹配电池或核实请求被拒绝 | 通过 |
| I2-TC-021 | 有效电池可以加入草稿批次 | 通过 |
| I2-TC-022 | 同一电池重复加入同一批次不会产生重复关系 | 通过 |
| I2-TC-023 | 电池不能同时加入另一个有效批次 | 通过 |
| I2-TC-024 | 非草稿批次或非登记状态电池不能建立关系 | 通过 |
| I2-TC-025 | 必填来源信息不完整时提交失败且状态不变 | 通过 |
| I2-TC-026 | 空批次提交失败且状态不变 | 通过 |
| I2-TC-027 | 存在无效电池时提交事务回滚 | 通过 |
| I2-TC-028 | 提交成功后批次和全部电池原子进入待验收 | 通过 |
| I2-TC-029 | 提交只产生一组生命周期事件 | 通过 |
| I2-TC-030 | 相同幂等键重复调用返回第一次结果 | 通过 |
| I2-TC-031 | 相同幂等键配合不同请求摘要返回冲突 | 通过 |
| I2-TC-032 | 数据库唯一约束兜底防止重复有效批次关系 | 通过 |
| I2-TC-033 | 权限矩阵、企业隔离、成功审计和拒绝审计均正确 | 通过 |
| I2-TC-034 | 前端完整流程、路由守卫、Envelope 解析和错误提示正确 | 通过 |

## 验证命令

本地已执行：

```powershell
python -c "import yaml, pathlib; [yaml.safe_load(pathlib.Path(p).read_text(encoding='utf-8')) for p in ['project-state.yaml','contracts/api/openapi-first-slice.yaml']]; print('yaml ok')"
npx --yes @redocly/cli@2.54.3 lint contracts/api/openapi-first-slice.yaml
$env:JAVA_HOME='D:\Java'; $env:Path='D:\Java\bin;' + $env:Path; mvn -B test
npm run test
npm run build
git diff --check
```

GitHub Actions 已执行：

- 后端 `mvn test`，包含 I1 回归和 I2 MySQL 集成测试；
- MySQL 8.4 启动；
- Flyway V1..V6 迁移；
- 29 张业务表和 V6 唯一索引断言；
- Spring Boot 健康检查；
- Redocly OpenAPI lint；
- 前端 `npm ci`、Vitest 和生产构建。

## 已知限制

- I2 停在批次提交进入 `PENDING_ACCEPTANCE`。
- 验收、补充资料、验收不通过、入库、库存和数据治理闭环未进入本增量。
- 正式附件上传接口未在 I2 页面中开放；批次接口对非空 `attachmentIds` 做有效性校验。
- I2 完成后仍需正式评审，评审通过前不得进入 I3。
