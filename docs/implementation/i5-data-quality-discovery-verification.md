# I5 数据质量检查与问题发现验证记录

文档状态：待复核  
增量：I5-data-quality-discovery  
当前结论：修改后复核  
实现提交：`1457bb70bf567cddc167ef0f77146e3d7de43a67`
上一轮验证提交：`fa54798c1f0d01065e4d7ce095e145c9b8f36fe8`
本轮验证提交：待 GitHub Actions 验证当前 HEAD
GitHub Actions：待取得当前 HEAD 的 MySQL 8.4 运行结果
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
npx --yes @redocly/cli@2.54.3 lint contracts/api/openapi-first-slice.yaml contracts/api/openapi-data-governance-v1.1.yaml contracts/api/openapi-data-governance-i5-runtime.yaml
```

结果：

- 后端常规测试通过。本地未设置 `RUN_MYSQL_TESTS=true`，MySQL 条件集成测试跳过。
- 前端 Vitest 29 个测试通过。
- 前端生产构建通过。
- Redocly 三份 OpenAPI 均通过，当前 4 个 warning 分别为两个删除保护接口无 2xx、I5 规则启停保护接口无 2xx、完整 V1.1 设计基线保留未使用的 `RecheckId` 组件。

## CI 验证

上一轮 GitHub Actions run `38203806351` 已不再作为本轮整改的最终证据。本轮修改调整了 DQ-004/DQ-005/DQ-007 规则口径，新增 DQ-001、DQ-003、跨企业异常引用和前端菜单权限测试，必须重新取得当前 HEAD 的 MySQL 8.4 CI 结果。

待确认项：

- MySQL 8.4 启动成功；
- Flyway V1..V7 执行成功，V1..V6 未修改；
- `RUN_MYSQL_TESTS=true`；
- `I5DataQualityDiscoveryIntegrationTest` 实际执行，Tests run 不少于 7、Failures 0、Errors 0、Skipped 0；
- I2、I3、I4 MySQL 集成回归分别为 17、6、4 个测试通过且 Skipped 0；
- 后端测试、前端测试、前端构建和三份 OpenAPI lint 全部通过。

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
| I5-TC-008 DQ-001..DQ-007 处理器 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` 验证 DQ-002..DQ-007 命中与 DQ-004 合规反例；`ruleProcessorsCoverDuplicateClosedCandidateAndEnterpriseIsolation` 临时移除并恢复追溯编码唯一索引以验证 DQ-001 重复识别，验证 DQ-003 已关闭但缺核实记录、DQ-004 无 `INBOUND_COMPLETED` 事件仍通过、DQ-005/DQ-007 跨企业异常引用 |
| I5-TC-009 DQ-001 不泄露其他企业明细 | DQ-001 处理器只返回当前企业对象；`ruleProcessorsCoverDuplicateClosedCandidateAndEnterpriseIsolation` 验证 DQ-007 跨企业异常引用不泄露另一企业追溯编码 |
| I5-TC-010 无违规检查完成且不生成问题 | `crossEnterpriseRunsAndIssuesAreRejectedAndAudited` 断言企业 B BATTERY 检查 `issueCount=0`、结果全部 PASS、未生成问题 |
| I5-TC-011 违规检查生成结果和 OPEN 问题 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` |
| I5-TC-012 重复检查不重复创建未关闭问题 | 同上 |
| I5-TC-013 并发开放问题去重 | `concurrentChecksDeduplicateOpenIssues` 使用双线程不同幂等键真实并发检查 |
| I5-TC-014 规则执行异常任务 FAILED 且结果回滚 | `partialWriteFailureRollsBackResultsIssuesAndSuccessAudit` 在写入结果后诱发异常并断言结果、问题和成功审计回滚 |
| I5-TC-015 同幂等键同请求不重复执行 | `manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency` |
| I5-TC-016 同幂等键不同请求 409 | 同上 |
| I5-TC-017 缺少或非法幂等键 400 | `validationMissingConfigAndPermissionsAreAudited` |
| I5-TC-018 未授权角色不能发起或读取检查 | 同上 |
| I5-TC-019 系统管理员只能读取规则和问题 | 同上、I1 权限矩阵测试 |
| I5-TC-020 回收/仓库角色仅能看到本人相关问题 | `validationMissingConfigAndPermissionsAreAudited` 断言回收操作员和仓库管理员均只能看到本人分配问题，访问他人分配问题详情返回 403 |
| I5-TC-021 跨企业任务和问题拒绝并审计 | `crossEnterpriseRunsAndIssuesAreRejectedAndAudited` |
| I5-TC-022 不存在资源 404 | `validationMissingConfigAndPermissionsAreAudited` |
| I5-TC-023 Long ID 为字符串 | `FirstSliceOpenApiContractTest.i5DataQualityRuntimeContractIsDeclaredInOpenApi`、后端 Jackson 全局配置、前端类型 |
| I5-TC-024 前端菜单、路由、表单、结果、完整规则元数据和问题页 | `frontend/src/views/i5-pages.test.ts` 断言路由权限、不同权限下 Shell 菜单显示/隐藏、检查表单、结果页、问题详情和规则完整元数据 |
| I5-TC-025 I1..I4 回归 | 待当前 HEAD GitHub Actions 验证 |

## CI 通过标准

I5 复核前必须取得 GitHub Actions 证据：

- Flyway V1..V7 在 MySQL 8.4 执行成功；
- V1..V6 文件内容保持不变；
- `RUN_MYSQL_TESTS=true`；
- `I5DataQualityDiscoveryIntegrationTest` 实际执行，Tests run 不少于 7 且 `skipped=0`；
- I1、I2、I3、I4 回归通过；
- 后端测试、前端测试、前端构建、Redocly 全部通过。

## 结论

当前 I5 已完成本轮整改和本地验证，仍需取得当前 HEAD 的 GitHub Actions MySQL 8.4 证据，状态保持：

```yaml
I5:
  status: paused-for-review
  review_result: 修改后复核
```

不得关闭 I5，不启动 I6。
