# I5 数据质量检查与问题发现验证记录

文档状态：待复核  
增量：I5-data-quality-discovery  
当前结论：修改后复核  
实现提交：`1457bb70bf567cddc167ef0f77146e3d7de43a67`
验证提交：待 GitHub Actions 验证  
GitHub Actions：待生成  
评审包：`i5-data-quality-discovery-review.zip`，SHA-256 以交付消息或本地 `Get-FileHash` 输出为准

## 范围

I5 实现“规则查看 -> 手工检查 -> 检查结果 -> OPEN 质量问题”的发现闭环。包含 DQ-001..DQ-007 固定规则、企业规则配置补齐、手工同步检查、检查结果、问题列表/详情、去重、治理审计、权限、企业隔离、幂等和前端页面。

不包含问题分配、整改、复核、关闭、看板、治理审计查询、定时检查、出库、企业间流转和综合利用。

## 实现摘要

| 范围 | 状态 |
| --- | --- |
| 固定规则查询 `GET /api/v1/data-quality/rules` | 已实现 |
| 保护性规则启停入口 `PATCH /api/v1/data-quality/rules/{ruleCode}/status` | 已实现，内置角色均拒绝 |
| 手工检查 `POST /api/v1/data-quality/check-runs` | 已实现，同步执行 |
| 检查任务与逐规则结果查询 | 已实现 |
| 质量问题列表与详情 | 已实现 |
| DQ-001..DQ-007 规则处理器 | 已实现 |
| OPEN 问题生成与未关闭问题去重 | 已实现 |
| `dq_operation_audit` 治理审计 | 已实现 |
| V7 企业规则配置回填 | 已实现，不覆盖已有配置 |
| 前端规则、检查、结果、问题页面 | 已实现 |

## 本地验证

```powershell
$env:JAVA_HOME='D:\Java'
$env:PATH='D:\Java\bin;' + $env:PATH
cd backend
mvn -q test
cd ..\frontend
npm run test
npm run build
cd ..
npx --yes @redocly/cli@2.54.3 lint contracts/api/openapi-first-slice.yaml contracts/api/openapi-data-governance-v1.1.yaml
```

结果：

- 后端常规测试通过。本地未设置 `RUN_MYSQL_TESTS=true`，MySQL 条件集成测试跳过。
- 前端 Vitest 28 个测试通过。
- 前端生产构建通过。
- Redocly 两份 OpenAPI 均通过，保留保护性删除/规则启停接口无 2xx 的语义警告。

## 自动化测试映射

| 测试 | 自动化证据 |
| --- | --- |
| I5-TC-001 七条规则元数据完整返回 | `I5DataQualityDiscoveryIntegrationTest.rulesAndV7BackfillAreVisibleAndDoNotOverrideDisabledConfig` |
| I5-TC-002 企业七条配置默认启用 | 同上 |
| I5-TC-003 V7 不覆盖既有 DISABLED 配置 | 同上 |
| I5-TC-004 内置角色启停规则拒绝并审计 | 同上 |
| I5-TC-005 不存在算法/脚本编辑接口 | `FirstSliceOpenApiContractTest.i5DataQualityRuntimeContractIsDeclaredInOpenApi` |
| I5-TC-006 范围映射 | `I5DataQualityDiscoveryIntegrationTest.manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` |
| I5-TC-007 指定规则子集和范围不匹配拒绝 | `validationMissingConfigAndPermissionsAreAudited` |
| I5-TC-008 DQ-001..DQ-007 处理器 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency`；DQ-001 重复识别受生产唯一约束保护，集成测试验证正常数据路径 |
| I5-TC-009 DQ-001 不泄露其他企业明细 | 由处理器企业过滤和契约范围约束覆盖，待 CI 后复核 |
| I5-TC-010 无违规检查完成且不生成问题 | `crossEnterpriseRunsAndIssuesAreRejectedAndAudited` 的企业 B BATTERY 检查路径 |
| I5-TC-011 违规检查生成结果和 OPEN 问题 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` |
| I5-TC-012 重复检查不重复创建未关闭问题 | 同上 |
| I5-TC-013 并发开放问题去重 | 数据库 `uk_dq_issue_open` 约束兜底，待 CI 后复核 |
| I5-TC-014 规则执行异常任务 FAILED 且结果回滚 | `validationMissingConfigAndPermissionsAreAudited` 缺配置 FAILED 路径 |
| I5-TC-015 同幂等键同请求不重复执行 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` |
| I5-TC-016 同幂等键不同请求 409 | 同上 |
| I5-TC-017 缺少或非法幂等键 400 | `validationMissingConfigAndPermissionsAreAudited` |
| I5-TC-018 未授权角色不能发起或读取检查 | 同上 |
| I5-TC-019 系统管理员只能读取规则和问题 | 同上、I1 权限矩阵测试 |
| I5-TC-020 回收/仓库角色仅能看到本人相关问题 | `validationMissingConfigAndPermissionsAreAudited` |
| I5-TC-021 跨企业任务和问题拒绝并审计 | `crossEnterpriseRunsAndIssuesAreRejectedAndAudited` |
| I5-TC-022 不存在资源 404 | 服务实现路径，待 CI 后复核 |
| I5-TC-023 Long ID 为字符串 | 后端 Jackson 全局配置、OpenAPI 契约测试、前端类型 |
| I5-TC-024 前端菜单、路由、表单、结果和问题页 | `frontend/src/views/i5-pages.test.ts` |
| I5-TC-025 I1..I4 回归 | 待 GitHub Actions MySQL 8.4 验证 |

## CI 通过标准

I5 复核前必须取得 GitHub Actions 证据：

- Flyway V1..V7 在 MySQL 8.4 执行成功；
- V1..V6 文件内容保持不变；
- `RUN_MYSQL_TESTS=true`；
- `I5DataQualityDiscoveryIntegrationTest` 实际执行，`skipped=0`；
- I1、I2、I3、I4 回归通过；
- 后端测试、前端测试、前端构建、Redocly 全部通过。

## 结论

当前 I5 已完成本地实现与本地验证，状态保持：

```yaml
I5:
  status: paused-for-review
  review_result: 修改后复核
```

不得关闭 I5，不启动 I6。
