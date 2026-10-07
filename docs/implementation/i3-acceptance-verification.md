# I3 验收闭环验证记录

文档状态：待复核

## 验证对象

- 增量：I3-acceptance-supplement
- 分支：feature/first-slice-implementation
- 验证提交：待提交
- GitHub Actions：待运行
- 结论：I3 已完成本地实现与本地验证，状态保持 `paused-for-review / 修改后复核`，等待 MySQL 8.4 GitHub Actions 和人工复核；不进入 I4。

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
| `GET /api/v1/batteries/{id}/trace` | 已增强显示验收和补充生命周期事件 |
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

## 实现页面

| 页面 | 结果 |
| --- | --- |
| 待处理验收 | 已实现 |
| 验收登记弹窗 | 已实现 |
| 补充资料弹窗和附件上传 | 已实现 |
| 批次详情验收进度展示 | 已实现 |
| 生命周期追溯 | 已复用并展示新增事件 |

## 数据库与迁移

- 未修改 Flyway V1-V6。
- 未新增 V7；I3 所需 `acceptance_record`、`acceptance_supplement`、`business_attachment`、状态枚举和权限编码已在既有迁移中存在。

## 测试结果

| 编号 | 场景 | 结果 |
| --- | --- | --- |
| I3-TC-001 | 待处理验收任务查询 | 本地通过；MySQL CI 待验证 |
| I3-TC-002 | 验收通过进入 `ACCEPTED_PENDING_INBOUND` | 本地通过；MySQL CI 待验证 |
| I3-TC-003 | 待补充资料进入 `PENDING_SUPPLEMENT` | 本地通过；MySQL CI 待验证 |
| I3-TC-004 | 补充资料后回到 `PENDING_ACCEPTANCE` | 本地通过；MySQL CI 待验证 |
| I3-TC-005 | 验收不通过进入 `ACCEPTANCE_REJECTED` | 本地通过；MySQL CI 待验证 |
| I3-TC-006 | 验收和补充生命周期事件进入追溯 | 本地通过；MySQL CI 待验证 |
| I3-TC-007 | 验收必填缺失拒绝且状态保持 | 本地通过；MySQL CI 待验证 |
| I3-TC-008 | 待补充和不通过说明必填 | 本地通过；MySQL CI 待验证 |
| I3-TC-009 | 三项检查 40/41 和说明 500/501 长度边界 | 本地通过；MySQL CI 待验证 |
| I3-TC-010 | 非待验收状态不能登记验收 | 本地通过；MySQL CI 待验证 |
| I3-TC-011 | 非待补充状态不能提交补充 | 本地通过；MySQL CI 待验证 |
| I3-TC-012 | 失败时电池、批次、历史和附件状态保持 | 本地通过；MySQL CI 待验证 |
| I3-TC-013 | 仅说明补充 | 本地通过；MySQL CI 待验证 |
| I3-TC-014 | 仅附件补充，说明持久化为空字符串 | 本地通过；MySQL CI 待验证 |
| I3-TC-015 | 说明加附件补充 | 本地通过；MySQL CI 待验证 |
| I3-TC-016 | 补充内容全部为空拒绝 | 本地通过；MySQL CI 待验证 |
| I3-TC-017 | 多轮补充再验收保留历史 | 本地通过；MySQL CI 待验证 |
| I3-TC-018 | 其他企业、其他用户、过期、已绑定或不存在附件被拒绝 | 本地通过；MySQL CI 待验证 |
| I3-TC-019 | 四类角色权限矩阵和未登录/过期 Token 保护 | 本地通过；MySQL CI 待验证 |
| I3-TC-020 | 企业隔离覆盖列表、验收、补充、追溯和附件 | 本地通过；MySQL CI 待验证 |
| I3-TC-021 | 同幂等键同请求返回首个结果 | 本地通过；MySQL CI 待验证 |
| I3-TC-022 | 同幂等键不同请求返回冲突 | 本地通过；MySQL CI 待验证 |
| I3-TC-023 | 不同幂等键并发验收同一电池只成功一次 | 本地通过；MySQL CI 待验证 |
| I3-TC-024 | 同批次不同电池并发完成能正确汇总 `COMPLETED` | 本地通过；MySQL CI 待验证 |
| I3-TC-025 | 删除生效验收记录返回保护错误并审计 | 本地通过；MySQL CI 待验证 |
| I3-TC-026 | 前端待处理验收页面加载和登记验收 | 通过 |
| I3-TC-027 | 前端补充资料和附件上传 | 通过 |
| I3-TC-028 | 批次详情验收进度展示 | 通过 |
| I3-TC-029 | OpenAPI 与运行时字段、错误码和路径一致 | 通过 |
| I3-TC-030 | I1/I2 回归、前端测试和构建 | 通过 |

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
- 前端 Vitest 14 个测试通过。
- 前端生产构建通过。
- Redocly OpenAPI lint 退出码为 0；保留删除保护接口无 2xx 响应的语义警告。

## 待复核事项

- 推送后等待 Implementation CI 在 MySQL 8.4 下以 `RUN_MYSQL_TESTS=true` 实际执行 `I3AcceptanceIntegrationTest`。
- CI 通过后再更新本文件的验证提交、Actions 链接和最终复核结论。
