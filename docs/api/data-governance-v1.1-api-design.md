# 数据治理 V1.1 API 设计

文档状态：已确认
关联变更：CR-DG-001
契约文件：`contracts/api/openapi-data-governance-v1.1.yaml`

## 1. 通用约束

- API 前缀：`/api/v1/data-quality`。
- 认证：所有接口均需登录。
- 企业隔离：`enterprise_id` 仅来自登录上下文，请求体不得提交企业 ID。
- 写接口必须携带 `Idempotency-Key`。
- 所有受保护接口必须声明 401 和 403。
- 失败操作必须保持业务状态不变，并写拒绝审计。
- 不提供规则算法、字段、判断表达式或脚本编辑接口。

## 2. 权限编码

| 权限 | 说明 |
| --- | --- |
| `dq:rule:read` | 查看规则。 |
| `dq:rule:toggle` | 启用或停用企业规则；V1.1 不授予系统管理员。 |
| `dq:check:execute` | 发起质量检查。 |
| `dq:check:read` | 查看检查任务和结果。 |
| `dq:issue:read` | 查看质量问题。 |
| `dq:issue:assign` | 分配质量问题。 |
| `dq:issue:process` | 开始处理和提交处理。 |
| `dq:issue:recheck` | 发起后端机器重新检查并关闭或退回问题。 |
| `dq:dashboard:read` | 查看质量看板。 |
| `dq:audit:read` | 查看治理审计。 |

## 3. API 清单

| API | 方法 | 权限 | 幂等 | 说明 |
| --- | --- | --- | --- | --- |
| `/rules` | GET | `dq:rule:read` | 否 | 查询固定规则和当前企业启停状态。 |
| `/rules/{ruleCode}/status` | PATCH | `dq:rule:toggle` | 是 | 启用或停用规则。 |
| `/check-runs` | POST | `dq:check:execute` | 是 | 发起质量检查。 |
| `/check-runs/{runId}` | GET | `dq:check:read` | 否 | 查询检查任务详情。 |
| `/check-runs/{runId}/results` | GET | `dq:check:read` | 否 | 查询检查结果。 |
| `/issues` | GET | `dq:issue:read` | 否 | 查询问题列表。 |
| `/issues/{issueId}` | GET | `dq:issue:read` | 否 | 查询问题详情。 |
| `/issues/{issueId}/assign` | POST | `dq:issue:assign` | 是 | 分配责任人。 |
| `/issues/{issueId}/start-processing` | POST | `dq:issue:process` | 是 | 责任人开始处理。 |
| `/issues/{issueId}/remediations` | POST | `dq:issue:process` | 是 | 提交处理说明，且必须提供附件证据或更正对象引用。 |
| `/issues/{issueId}/rechecks` | POST | `dq:issue:recheck` | 是 | 后端使用原规则、原对象执行机器复核，并关闭或退回问题。 |
| `/dashboard/summary` | GET | `dq:dashboard:read` | 否 | 查询最近 30 天看板汇总。 |
| `/audit-logs` | GET | `dq:audit:read` | 否 | 查询当前企业治理审计。 |

## 4. 状态与错误

| 场景 | HTTP | 错误码 | 状态影响 |
| --- | --- | --- | --- |
| 未登录 | 401 | `UNAUTHENTICATED` | 不变 |
| 无权限 | 403 | `FORBIDDEN` | 不变，写审计 |
| 跨企业访问 | 403 | `CROSS_ENTERPRISE_ACCESS_DENIED` | 不变，写审计 |
| 状态非法 | 409 | `INVALID_ISSUE_STATUS` | 不变，写审计 |
| 幂等键复用且请求不同 | 409 | `IDEMPOTENCY_KEY_REUSED` | 不变 |
| 缺少原因或证据 | 400 | `VALIDATION_FAILED` | 不变 |
| 开放问题重复 | 200 | 返回既有问题 | 不重复创建 |
| 客户端提交复核通过结果 | 400 | `CLIENT_RECHECK_RESULT_NOT_ALLOWED` | 不变 |

## 5. 写接口事务

写接口统一执行：

1. 校验 `Idempotency-Key`。
2. 校验认证、权限和企业。
3. 校验输入和状态。
4. 执行业务写入和审计。
5. 更新幂等记录。
6. 提交事务。

失败时不更新业务状态；拒绝类失败写审计；业务失败用独立事务记录 `FAILED` 幂等结果。

## 6. 复核执行约束

`POST /issues/{issueId}/rechecks` 不接收 `passed`、`result` 等客户端判定字段。服务端必须：

1. 校验问题属于当前企业且状态为 `SUBMITTED`。
2. 校验问题已有整改说明，且存在绑定附件证据或修正对象引用。
3. 使用原问题的 `rule_code`、`object_type` 和 `object_identity` 执行同一规则检查。
4. 生成 `dq_check_run` 和 `dq_check_result`。
5. 机器检查通过时写入 `dq_recheck.recheck_status=PASSED`、`dq_recheck.result=PASSED` 并关闭问题。
6. 机器检查未通过时写入 `dq_recheck.recheck_status=FAILED`、`dq_recheck.result=FAILED`、`result_reason` 并将问题退回 `REJECTED`。
7. 问题状态、复核记录、检查结果和 `dq_operation_audit` 在同一事务中保存。

同步接口返回的 `RecheckResponse.result` 必填，`recheckStatus` 只能返回 `PASSED` 或 `FAILED`；`RUNNING` 仅作为数据库执行过程中的中间状态，不暴露为成功响应。

## 7. 整改证据约束

`POST /issues/{issueId}/remediations` 必须满足以下任一条件：

- 提供 `evidenceAttachmentId`，且附件已经绑定、属于当前企业、与问题对象相关。
- 同时提供 `correctionObjectType` 和 `correctionObjectId`，且修正对象存在、属于当前企业、与问题对象相关。

只填写说明、只填更正对象类型或只填更正对象 ID 均返回 400，问题状态保持不变并记录治理审计。

## 8. 规则元数据返回

`GET /rules` 和规则状态更新后的 `RuleResponse` 必须返回完整规则元数据，至少包括：

- `dataStandardMetadata`
- `involvedFields`
- `checkCondition`
- `remediationGuidance`

上述字段由 `dq_rule_definition` 提供，只读展示，不提供在线编辑接口。

## 9. 治理审计查询

`GET /audit-logs` 查询 `dq_operation_audit`，企业 ID 只能来自登录上下文，不接受请求参数传入。接口支持分页，并支持按 `actionCode`、`objectType`、`result`、`operatedFrom` 和 `operatedTo` 过滤。

权限要求：

- 系统管理员和业务主管允许查询本企业治理审计。
- 回收操作员、仓库管理员和无权限用户返回 403。
- 跨企业按 ID 或条件访问返回 403，并记录拒绝审计。

## 10. 技术追踪

| FR | API |
| --- | --- |
| FR-DG-001 | GET `/rules` |
| FR-DG-002、003 | PATCH `/rules/{ruleCode}/status` |
| FR-DG-004、005、006 | POST `/check-runs`，GET `/check-runs/{runId}` |
| FR-DG-007、008 | GET `/check-runs/{runId}/results`，GET `/issues` |
| FR-DG-009 | POST `/issues/{issueId}/assign` |
| FR-DG-010、011 | POST `/issues/{issueId}/start-processing` |
| FR-DG-012、013 | POST `/issues/{issueId}/remediations` |
| FR-DG-014、015、016 | POST `/issues/{issueId}/rechecks` |
| FR-DG-017 | GET `/dashboard/summary` |
| FR-DG-018 | GET `/audit-logs`，全部写接口和拒绝场景 |

## 11. 当前结论

API 设计已确认，可作为数据治理 V1.1 实现输入；正式编码仍需在后续实现分支按本契约执行。
