# I3 验收闭环验证记录

文档状态：待复核

## 验证对象

- 增量：I3-acceptance-supplement
- 分支：feature/first-slice-implementation
- 验证提交：`6fee83009925d4fdec505c7f2dfbaeb5b7755425`
- GitHub Actions：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37734497499`
- 结论：I3 修改后复核阻断项已完成代码、本地验证和 MySQL 8.4 GitHub Actions 验证，状态保持 `paused-for-review / 修改后复核`，等待人工复核；不进入 I4。

## I3 范围

本增量实现“待验收 -> 验收通过 / 待补充资料 / 验收不通过；待补充资料 -> 补充并重新提交 -> 再次验收”的闭环。

不包含真实入库记录、有效库存、仓库库位办理、数据治理规则执行、企业间流转、出库、综合利用和报送。

## 实现接口

| 接口 | 结果 |
| --- | --- |
| `GET /api/v1/acceptances/pending` | 已实现 |
| `POST /api/v1/batteries/{id}/acceptances` | 已实现 |
| `POST /api/v1/batteries/{id}/acceptance-supplements` | 已实现 |
| `POST /api/v1/attachments` | 已实现 |
| `GET /api/v1/attachments/{id}/download` | 已实现 |
| `GET /api/v1/batteries/{id}/trace` | 已增强显示验收和补充生命周期事件、验收三项详情、补充说明和附件下载入口 |
| `GET /api/v1/recycle-batches/{id}` | 已复用成员状态支撑批次验收进度展示 |
| `DELETE /api/v1/acceptance-records/{id}` | 已实现删除保护，拒绝删除并写审计 |

所有写接口继续使用 I1/I2 确认的 `Idempotency-Key`、统一 Envelope、JWT、RBAC、企业隔离和审计机制。

## 状态转换

| 当前状态 | 操作 | 下一状态 |
| --- | --- | --- |
| `PENDING_ACCEPTANCE` | 验收通过 | `ACCEPTED_PENDING_INBOUND` |
| `PENDING_ACCEPTANCE` | 待补充资料 | `PENDING_SUPPLEMENT` |
| `PENDING_SUPPLEMENT` | 补充并重新提交 | `PENDING_ACCEPTANCE` |
| `PENDING_ACCEPTANCE` | 验收不通过 | `ACCEPTANCE_REJECTED` |

批次状态根据成员验收进度从 `PENDING_ACCEPTANCE` 推进到 `ACCEPTANCE_PROCESSING` 或 `COMPLETED`；`COMPLETED` 表示验收完成，不表示已入库。

## 本轮复核阻断项处理

| 阻断项 | 处理结果 | 证据 |
| --- | --- | --- |
| 附件没有实际保存 | 上传写入可配置的 `app.attachment.storage-dir`，默认 `../data/attachments`；下载读取原始字节，不再返回占位文本 | `AttachmentService`、`supplementWithAttachmentsIsAtomicAndRejectsInvalidAttachments` |
| 附件幂等摘要不包含文件内容 | 后端幂等 canonical 纳入文件 SHA-256；前端上传 fingerprint 纳入文件内容 fingerprint | `AttachmentService.upload`、`frontend/src/api/i2.ts`、前后端测试 |
| 验收历史无法完整查看 | 后端追溯事件新增兼容 `details` 字段；前端追溯页展示验收三项、验收说明、补充说明和携带 JWT 的附件下载按钮 | `BatteryService.trace`、`BatteryTraceView.vue`、I3 追溯断言、前端组件测试 |
| 部分拒绝路径没有审计 | 业务校验、无效附件和 DTO 校验失败均写拒绝审计，拒绝审计使用独立事务 | `AcceptanceService`、`GlobalExceptionHandler`、I3 审计断言 |
| 附件保存到构建目录 | 不再写入 `target/attachments`；服务端生成存储文件名，事务回滚后清理已写文件，重建服务实例后仍可按数据库路径下载 | `AttachmentService.storagePath`、`deleteOnRollback`、I3 附件断言 |
| 验证记录超出测试覆盖 | 补齐 `I3-TC-010/011/013/015/016/020/028` 的真实断言并更新映射 | 本文件“测试证据映射” |

## 实现页面

| 页面 | 结果 |
| --- | --- |
| 待处理验收 | 已实现 |
| 验收登记弹窗 | 已实现 |
| 补充资料弹窗和附件上传 | 已实现 |
| 批次详情验收进度展示 | 已实现 |
| 生命周期追溯 | 已复用并展示新增事件、验收/补充详情和附件下载入口 |

## 数据库与迁移

- 未修改 Flyway V1-V6。
- 未新增 V7；I3 所需 `acceptance_record`、`acceptance_supplement`、`business_attachment`、状态枚举和权限编码已在既有迁移中存在。

## 测试结果

| 编号 | 场景 | 结果 |
| --- | --- | --- |
| I3-TC-001 | 待处理验收任务查询 | MySQL 8.4 CI 通过 |
| I3-TC-002 | 验收通过进入 `ACCEPTED_PENDING_INBOUND` | MySQL 8.4 CI 通过 |
| I3-TC-003 | 待补充资料进入 `PENDING_SUPPLEMENT` | MySQL 8.4 CI 通过 |
| I3-TC-004 | 补充资料后回到 `PENDING_ACCEPTANCE` | MySQL 8.4 CI 通过 |
| I3-TC-005 | 验收不通过进入 `ACCEPTANCE_REJECTED` | MySQL 8.4 CI 通过 |
| I3-TC-006 | 验收和补充生命周期事件进入追溯 | MySQL 8.4 CI 通过 |
| I3-TC-007 | 验收必填缺失拒绝且状态保持 | MySQL 8.4 CI 通过 |
| I3-TC-008 | 待补充和不通过说明必填 | MySQL 8.4 CI 通过 |
| I3-TC-009 | 三项检查 40/41 和说明 500/501 长度边界 | MySQL 8.4 CI 通过 |
| I3-TC-010 | 非待验收状态不能登记验收 | MySQL 8.4 CI 通过 |
| I3-TC-011 | 非待补充状态不能提交补充 | MySQL 8.4 CI 通过 |
| I3-TC-012 | 失败时电池、批次、历史和附件状态保持 | MySQL 8.4 CI 通过 |
| I3-TC-013 | 仅说明补充 | MySQL 8.4 CI 通过 |
| I3-TC-014 | 仅附件补充，说明持久化为空字符串 | MySQL 8.4 CI 通过 |
| I3-TC-015 | 说明加附件补充 | MySQL 8.4 CI 通过 |
| I3-TC-016 | 补充内容全部为空拒绝 | MySQL 8.4 CI 通过 |
| I3-TC-017 | 多轮补充再验收保留历史 | MySQL 8.4 CI 通过 |
| I3-TC-018 | 其他企业、其他用户、过期、已绑定或不存在附件被拒绝 | MySQL 8.4 CI 通过 |
| I3-TC-019 | 四类角色权限矩阵和未登录/无效 Token 保护 | MySQL 8.4 CI 通过 |
| I3-TC-020 | 企业隔离覆盖列表、验收、补充、追溯和附件 | MySQL 8.4 CI 通过 |
| I3-TC-021 | 同幂等键同请求返回首个结果 | MySQL 8.4 CI 通过 |
| I3-TC-022 | 同幂等键不同请求返回冲突 | MySQL 8.4 CI 通过 |
| I3-TC-023 | 不同幂等键并发验收同一电池只成功一次 | MySQL 8.4 CI 通过 |
| I3-TC-024 | 同批次不同电池并发完成能正确汇总 `COMPLETED` | MySQL 8.4 CI 通过 |
| I3-TC-025 | 删除生效验收记录返回保护错误并审计 | MySQL 8.4 CI 通过 |
| I3-TC-026 | 前端待处理验收页面加载和登记验收 | 通过 |
| I3-TC-027 | 前端补充资料和附件上传 | 通过 |
| I3-TC-028 | 批次详情验收进度展示 | 通过 |
| I3-TC-029 | OpenAPI 与运行时字段、错误码和路径一致 | 通过 |
| I3-TC-030 | I1/I2 回归、前端测试和构建 | 通过 |

## 测试证据映射

| 测试方法 / 文件 | 覆盖编号 | 关键断言 |
| --- | --- | --- |
| `I3AcceptanceIntegrationTest.acceptanceResultsSupplementLoopAndBatchProgressAreClosed` | I3-TC-001..006、013、017 | 待验收列表、通过/待补充/仅说明补充/拒绝状态流转、批次完成、追溯详情包含三项检查、验收说明和补充说明 |
| `I3AcceptanceIntegrationTest.validationFailuresKeepBatteryBatchAndHistoryUnchanged` | I3-TC-007..012、016 | 三项检查分别缺失、条件说明缺失、40/41 和 500/501 边界、非待验收状态登记拒绝、非待补充状态补充拒绝、空补充请求拒绝、失败后状态和历史不变、拒绝审计存在 |
| `I3AcceptanceIntegrationTest.supplementWithAttachmentsIsAtomicAndRejectsInvalidAttachments` | I3-TC-014..018、020、022 | 仅附件补充、说明加附件补充、空补充拒绝、多轮补充再验收、真实附件字节下载、内容 SHA-256、同 Key 不同内容冲突、附件绑定、跨企业下载/补充拒绝、其他用户/企业/过期/已绑定/不存在附件拒绝、重建服务实例后下载、回滚后文件清理 |
| `I3AcceptanceIntegrationTest.permissionsAndEnterpriseIsolationAreEnforced` | I3-TC-019、020 | 回收操作员以外角色拒绝、跨企业列表隔离、跨企业验收拒绝、跨企业追溯拒绝、未登录和无效 Token 拒绝 |
| `I3AcceptanceIntegrationTest.idempotencyAndConcurrentAcceptanceAllowOnlyOneStateTransition` | I3-TC-021..023 | 同键同请求复用结果、同键异请求冲突、不同键并发同电池只成功一次 |
| `I3AcceptanceIntegrationTest.concurrentFinalMembersCompleteBatchAndDeleteEffectiveAcceptanceIsProtected` | I3-TC-024、025 | 同批次不同电池并发完成汇总 `COMPLETED`，删除生效验收记录返回保护错误并审计 |
| `frontend/src/api/i2.test.ts` | I3-TC-022、027 | 附件上传内容不同会生成不同幂等 Key，会话清理后不复用旧 Key |
| `frontend/src/views/i3-pages.test.ts` | I3-TC-026..028 | 待处理验收页面、验收登记、补充资料、追溯历史详情、JWT 附件下载入口和批次进度展示 |
| `FirstSliceOpenApiContractTest` | I3-TC-029 | 验收/补充字段长度、补充接口响应码、附件 `contentSha256` 和追溯 `details` 契约 |

## 本地验证命令

```powershell
$env:JAVA_HOME='D:\Java'
$env:Path='D:\Java\bin;' + $env:Path
cd backend
mvn -B test

cd ..\frontend
npm test -- --run
npm run build

cd ..
npx @redocly/cli lint contracts/api/openapi-first-slice.yaml
```

本地结果：

- 后端 `mvn -B test` 通过；`I3AcceptanceIntegrationTest` 在未设置 `RUN_MYSQL_TESTS=true` 时跳过。
- 前端 Vitest 17 个测试通过。
- 前端生产构建通过。
- Redocly OpenAPI lint 退出码为 0；保留删除保护接口无 2xx 响应的语义警告。

## GitHub Actions 验证

- Workflow：Implementation CI
- Run：`https://github.com/dyabn/battery-recycling-traceability-system/actions/runs/37734497499`
- 验证提交：`6fee83009925d4fdec505c7f2dfbaeb5b7755425`
- 结果：通过

CI 已验证：

- 后端 `mvn test` 通过。
- MySQL 8.4 启动成功。
- Flyway V1..V6 迁移通过。
- `RUN_MYSQL_TESTS=true` 下实际执行 I1、I2 和 I3 集成测试。
- `I3AcceptanceIntegrationTest` 6 个测试通过，覆盖验收结果、补充闭环、真实附件下载、附件内容幂等、无效附件组合、权限隔离、拒绝审计、追溯历史详情、真实并发、批次完成汇总、重建服务实例后下载、回滚文件清理和删除保护。
- Redocly OpenAPI lint、前端 Vitest 和前端生产构建通过。

## 待复核事项

- 等待用户对 I3 进行人工复核。
- 人工复核通过前，I3 继续保持 `paused-for-review / 修改后复核`，不关闭 I3，不进入 I4。
