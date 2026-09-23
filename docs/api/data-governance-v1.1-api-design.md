# 数据治理 V1.1 API 设计

文档状态：待评审
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
| `dq:rule:toggle` | 启用或停用企业规则。 |
| `dq:check:execute` | 发起质量检查。 |
| `dq:check:read` | 查看检查任务和结果。 |
| `dq:issue:read` | 查看质量问题。 |
| `dq:issue:assign` | 分配质量问题。 |
| `dq:issue:process` | 开始处理和提交处理。 |
| `dq:issue:recheck` | 发起重新检查和提交复核结果。 |
| `dq:dashboard:read` | 查看质量看板。 |

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
| `/issues/{issueId}/remediations` | POST | `dq:issue:process` | 是 | 提交处理说明、证据和更正引用。 |
| `/issues/{issueId}/rechecks` | POST | `dq:issue:recheck` | 是 | 发起重新检查。 |
| `/issues/{issueId}/rechecks/{recheckId}/result` | PUT | `dq:issue:recheck` | 是 | 提交复核结果，关闭或退回问题。 |
| `/dashboard/summary` | GET | `dq:dashboard:read` | 否 | 查询最近 30 天看板汇总。 |

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

## 5. 写接口事务

写接口统一执行：

1. 校验 `Idempotency-Key`。
2. 校验认证、权限和企业。
3. 校验输入和状态。
4. 执行业务写入和审计。
5. 更新幂等记录。
6. 提交事务。

失败时不更新业务状态；拒绝类失败写审计；业务失败用独立事务记录 `FAILED` 幂等结果。

## 6. 技术追踪

| FR | API |
| --- | --- |
| FR-DG-001 | GET `/rules` |
| FR-DG-002、003 | PATCH `/rules/{ruleCode}/status` |
| FR-DG-004、005、006 | POST `/check-runs`，GET `/check-runs/{runId}` |
| FR-DG-007、008 | GET `/check-runs/{runId}/results`，GET `/issues` |
| FR-DG-009 | POST `/issues/{issueId}/assign` |
| FR-DG-010、011 | POST `/issues/{issueId}/start-processing` |
| FR-DG-012、013 | POST `/issues/{issueId}/remediations` |
| FR-DG-014、015、016 | POST `/issues/{issueId}/rechecks`，PUT `/issues/{issueId}/rechecks/{recheckId}/result` |
| FR-DG-017 | GET `/dashboard/summary` |
| FR-DG-018 | 全部写接口和拒绝场景 |

## 7. 当前结论

API 设计待评审。评审通过前不生成正式后端代码。
