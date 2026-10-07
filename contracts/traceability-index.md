# 追踪索引

当前阶段：implementation-and-test / 测试通过。

当前状态：CR-DG-001 已复核通过，数据治理需求、测试、原型和技术设计基线 V1.1 已确认。I0 工程骨架已通过 GitHub Actions 验证，I1 身份、权限和企业隔离已复核通过；I2 回收批次与电池登记已复核通过并关闭；I3 验收闭环已实现，Implementation CI run 37638275006 验证通过，并暂停待人工复核。

## I3 实现追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| IMPL-I3-001 | I3 启动与接口契约对齐 | `project-state.yaml`、`contracts/api/openapi-first-slice.yaml` | 已完成 |
| IMPL-I3-002 | 待处理验收任务查询 | `GET /api/v1/acceptances/pending` | 已完成 |
| IMPL-I3-003 | 验收登记三结果闭环 | `POST /api/v1/batteries/{id}/acceptances`、`acceptance_record`、`battery`、`lifecycle_event` | 已完成 |
| IMPL-I3-004 | 资料补充与重新提交 | `POST /api/v1/batteries/{id}/acceptance-supplements`、`acceptance_supplement`、`business_attachment` | 已完成 |
| IMPL-I3-005 | 批次验收进度联动 | `recycle_batch.batch_status`、批次详情成员状态 | 已完成 |
| IMPL-I3-006 | 附件临时上传、绑定和下载保护 | `POST /api/v1/attachments`、`GET /api/v1/attachments/{id}/download` | 已完成 |
| IMPL-I3-007 | 生效验收记录删除保护 | `DELETE /api/v1/acceptance-records/{id}`、`audit_log` | 已完成 |
| IMPL-I3-008 | I3 前端业务闭环 | 待处理验收页面、批次详情验收进度、追溯页面 | 已完成 |
| IMPL-I3-009 | I3 自动化测试和验证记录 | `I3-TC-001..030`、I1/I2 回归 | 已完成 |

## I3 测试追踪摘要

| 测试范围 | 覆盖 |
| --- | --- |
| I3-TC-001..006 | 待验收列表、验收通过、待补充、不通过、生命周期事件和批次进度 |
| I3-TC-007..012 | 必填、条件说明、字段长度、非法状态、失败状态保持和 OpenAPI 契约 |
| I3-TC-013..018 | 仅说明补充、仅附件补充、附件绑定、已绑定/无效附件拒绝、多轮补充再验收和历史保留 |
| I3-TC-019..024 | 权限矩阵、企业隔离、同键幂等、同键异请求冲突、同电池并发验收和同批次并发完成 |
| I3-TC-025..030 | 删除保护审计、前端待处理验收页面、附件上传、补充提交、批次进度展示和构建回归 |

## I2 实现追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| IMPL-I2-001 | I2 启动与接口契约预检 | `project-state.yaml`、`contracts/api/openapi-first-slice.yaml`、`docs/api/first-slice-api-design.md` | 已完成 |
| IMPL-I2-002 | 回收批次创建、查询和草稿修改 | `POST/GET/PUT /api/v1/recycle-batches` | 已完成 |
| IMPL-I2-003 | 电池登记和系统追溯编码 | `POST /api/v1/batteries` | 已完成 |
| IMPL-I2-004 | 重复原始编码检查和人工核实 | `POST /api/v1/batteries/duplicate-check`、`POST /api/v1/battery-registration-candidates/{id}/duplicate-resolution` | 已完成 |
| IMPL-I2-005 | 电池加入批次和批次提交 | `POST /api/v1/recycle-batches/{id}/batteries`、`POST /api/v1/recycle-batches/{id}/submit` | 已完成 |
| IMPL-I2-006 | I2 前端业务闭环 | 批次、登记、核实、追溯页面 | 已完成 |
| IMPL-I2-007 | I2 自动化测试和验证记录 | `I2-TC-001..046`、I1 回归 | 已完成 |

## I2 测试追踪摘要

| 测试范围 | 覆盖 |
| --- | --- |
| I2-TC-001..008 | 批次创建、必填校验、并发编号、草稿修改、非草稿修改拒绝、企业隔离和拒绝审计 |
| I2-TC-009..020 | 电池登记、追溯编码、重复检查、候选创建、同一/不同电池核实、候选重复处理拒绝、跨企业候选保护 |
| I2-TC-021..029 | 电池加入批次、重复加入、跨有效批次占用、空批次提交、状态保持、提交原子状态转换和生命周期事件 |
| I2-TC-030..034 | 幂等重复、幂等冲突、权限矩阵、企业隔离、成功/拒绝审计、前端流程和错误提示 |
| I2-TC-035..046 | V6 有效批次关系真实并发约束、同原始编码并发登记、候选并发核实、字段长度边界、前端草稿编辑、前端超时重试幂等、OpenAPI YAML 契约断言、未核实重复阻断、多电池提交回滚、批次并发提交、原始编码锁名上限、退出登录清理待重试幂等 Key |

## I1 实现追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| IMPL-I1-001 | JWT 登录和当前用户 | `backend/src/main/java/.../auth`、`frontend/src/stores/auth.ts` | 已完成 |
| IMPL-I1-002 | DTO Envelope 契约 | `contracts/api/openapi-first-slice.yaml`、`common/api/ApiResponse.java` | 已完成 |
| IMPL-I1-003 | 企业隔离和 RBAC | `user`、`role`、`permission` 模块、`V5__correct_core_role_permission_matrix.sql` | 已完成 |
| IMPL-I1-004 | 幂等与失败记录 | `idempotency` 模块、`V4__expand_idempotency_key_length.sql` | 已完成 |
| IMPL-I1-005 | 成功/拒绝审计事务边界 | `audit/AuditService.java` | 已完成 |
| IMPL-I1-006 | 前端认证恢复和权限路由 | `frontend/src/router/authGuard.ts` | 已完成 |
| IMPL-I1-007 | I1 验证记录 | `docs/implementation/i1-auth-tenant-rbac-verification.md` | 已完成 |

## I1 测试追踪摘要

| 测试范围 | 覆盖 |
| --- | --- |
| I1-TC-001..006 | 登录、错误密码、禁用用户、无 Token、过期 Token、当前用户 |
| I1-TC-007..011 | 无权限、跨企业、系统管理员治理权限基线、`dq:rule:toggle` 未分配 |
| I1-TC-012..016 | 幂等重复、幂等冲突、V3/V4/V5 迁移、失败写入状态保持和 `FAILED` 记录 |
| I1-TC-017..023 | 拒绝审计、跨企业审计、RBAC 权限提升保护、请求校验、Token 即时失效 |
| I1-TC-024..028 | 幂等并发保护、接口响应契约、前端认证恢复、生产构建密码检查、ID 唯一性 |
| I1-TC-029..034 | 业务角色权限提升保护、V1.0/V1.1 权限矩阵、V5 纠偏、成功审计回滚和拒绝审计持久化 |

## 当前来源

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| MAT-001 | 课堂模板 | 业务分析与系统需求设计流程模板.docx | 已索引 |
| MAT-002 | 用户请求 | 当前对话 | 已索引 |
| BIZ-C1-BASELINE | C1 基线 | `project-state.yaml` 中 `baselines.C1` | 已确认 |
| BIZ-C2-1 | C2-1 业务事实盘点 | `docs/business/c2-1-business-facts-baseline.md` | 已吸收到 C2 基线 |
| BIZ-C2-BASELINE | C2 全局业务基线 | `docs/business/c2-global-business-baseline.md` | 已确认 |

## C2 已确认追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| BF-001..012 | 业务事实 | C2 全局业务基线 | 已确认 |
| BO-001..020 | 业务对象 | C2 全局业务基线 | 已确认 |
| BAT-01..013 | 电池包生命周期状态 | C2 全局业务基线 | 已确认 |
| BR-001..050 | 业务规则 | C2 全局业务基线 | 已确认 |
| EX-001..015 | 异常类型 | C2 全局业务基线 | 已确认 |
| SLICE-CANDIDATE-001 | 纵向业务切片候选 | 回收接收 -> 电池登记 -> 验收 -> 入库 -> 库存可见 | 已由 C3 确认 |

## C3 当前追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| BIZ-C3-SLICE-001 | 第一条业务切片 | `docs/business/c3-first-business-slice.md` | 已确认 |
| UC-C3-001..011 | 用例 | C3 第一条业务切片定义 | 已确认 |
| AC-001..014 | 验收标准 | C3 第一条业务切片定义 | 已确认 |
| BSR-C3-001..010 | 业务切片需求 | C3 追踪矩阵 | 已确认 |
| Q-C3-001..003 | C4 后移问题 | C3 评审问题处理结论 | resolved |
| Q-C3-004..005 | C3 已解决问题 | C3 评审问题处理结论 | resolved |

## C3 追踪关系摘要

| 业务切片需求 | C2 规则 | 验收标准 | 用例 |
| --- | --- | --- | --- |
| BSR-C3-001 创建回收批次并登记来源信息 | BR-007、BR-008 | AC-001、AC-004 | UC-C3-001 |
| BSR-C3-002 为新电池建立唯一追溯档案 | BR-001、BR-003、BR-004、BR-006、BR-046 | AC-002、AC-012 | UC-C3-002 |
| BSR-C3-003 阻止重复编码建立有效档案 | BR-002、BR-009、EX-001 | AC-003 | UC-C3-002 |
| BSR-C3-004 将电池加入回收批次并提交验收 | BR-008、BR-010 | AC-004、AC-012 | UC-C3-003、UC-C3-004 |
| BSR-C3-005 登记验收通过 | BR-011、BR-014、BR-046 | AC-005、AC-012 | UC-C3-005 |
| BSR-C3-006 处理资料不足并支持重新验收 | BR-013、EX-004、BR-046 | AC-006、AC-007、AC-012 | UC-C3-006 |
| BSR-C3-007 阻止验收不通过电池入库 | BR-012、EX-003 | AC-008 | UC-C3-007 |
| BSR-C3-008 对已验收待入库电池办理入库 | BR-015、BR-016、BR-017、BR-046 | AC-009、AC-010、AC-011、AC-012 | UC-C3-008、UC-C3-009 |
| BSR-C3-009 权限控制和越权审计 | BR-047、BR-048、BR-049、EX-012 | AC-013 | UC-C3-010 |
| BSR-C3-010 阻止直接删除已生效记录 | BR-005、BR-014、BR-044、BR-045、EX-013 | AC-014 | UC-C3-011 |

## 后续追踪链

进入 C4 之后，每条正式功能需求至少建立：

`BIZ/BR/EX -> UR/FR -> AC -> TC`

后续进入系统设计和实现后，继续扩展为：

`BIZ/BR/EX -> UR/FR -> AC -> TC -> UI/API/DB -> CODE`

## C4 需求追踪项

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| C4-REQ-001 | 系统需求规格 | `docs/requirements/c4-first-slice-system-requirements.md` | 已确认 |
| UR-C4-001..010 | 用户需求 | C4 第一切片系统需求规格 | 已确认 |
| FR-C4-001..022 | 功能需求 | C4 第一切片系统需求规格 | 已确认 |
| NFR-C4-001..005 | 非功能需求 | C4 第一切片系统需求规格 | 已确认 |
| Q-C3-001..003 | C4 后移问题处理 | C4 第一切片系统需求规格第 10 节 | resolved |
| C4-TEST-001 | 测试设计 | `docs/testing/c4-first-slice-test-design.md` | 已确认 |
| TC-C4-001..017 | 测试用例 | C4 第一切片测试设计 | 已确认 |
| C4-PROTO-001 | 低保真原型规格 | `docs/prototype/c4-first-slice-prototype-spec.md` | 已确认 |
| UI-C4-001..014 | 原型页面 | `prototype/first-slice/` | 已确认 |
| C4-REVIEW-001 | 原型评审记录 | `docs/prototype/c4-first-slice-prototype-review-record.md` | 已确认 |

## C4 BSR -> UR -> FR -> AC 追踪摘要

| BSR | UR | FR | AC |
| --- | --- | --- | --- |
| BSR-C3-001 | UR-C4-001 | FR-C4-001、FR-C4-002、FR-C4-003 | AC-001、AC-004 |
| BSR-C3-002 | UR-C4-002 | FR-C4-004、FR-C4-005 | AC-002、AC-012 |
| BSR-C3-003 | UR-C4-003 | FR-C4-006、FR-C4-007、FR-C4-008 | AC-003 |
| BSR-C3-004 | UR-C4-004 | FR-C4-008、FR-C4-009、FR-C4-010 | AC-004、AC-012 |
| BSR-C3-005 | UR-C4-005 | FR-C4-011、FR-C4-019 | AC-005、AC-012 |
| BSR-C3-006 | UR-C4-005、UR-C4-006 | FR-C4-012、FR-C4-013、FR-C4-019 | AC-006、AC-007、AC-012 |
| BSR-C3-007 | UR-C4-005、UR-C4-007 | FR-C4-014、FR-C4-015 | AC-008 |
| BSR-C3-008 | UR-C4-007、UR-C4-008 | FR-C4-015、FR-C4-016、FR-C4-017、FR-C4-018、FR-C4-019 | AC-009、AC-010、AC-011、AC-012 |
| BSR-C3-009 | UR-C4-009 | FR-C4-020、FR-C4-021 | AC-013 |
| BSR-C3-010 | UR-C4-010 | FR-C4-022 | AC-014 |

## C4 BSR -> UR -> FR -> AC -> TC 追踪摘要

| BSR | UR | FR | AC | TC |
| --- | --- | --- | --- | --- |
| BSR-C3-001 | UR-C4-001 | FR-C4-001、FR-C4-002、FR-C4-003 | AC-001、AC-004 | TC-C4-001、TC-C4-002、TC-C4-015 |
| BSR-C3-002 | UR-C4-002 | FR-C4-004、FR-C4-005 | AC-002、AC-012 | TC-C4-001、TC-C4-014 |
| BSR-C3-003 | UR-C4-003 | FR-C4-006、FR-C4-007、FR-C4-008 | AC-003 | TC-C4-004、TC-C4-005 |
| BSR-C3-004 | UR-C4-004 | FR-C4-008、FR-C4-009、FR-C4-010 | AC-004、AC-012 | TC-C4-003、TC-C4-006、TC-C4-014 |
| BSR-C3-005 | UR-C4-005 | FR-C4-011、FR-C4-019 | AC-005、AC-012 | TC-C4-001、TC-C4-014、TC-C4-016 |
| BSR-C3-006 | UR-C4-005、UR-C4-006 | FR-C4-012、FR-C4-013、FR-C4-019 | AC-006、AC-007、AC-012 | TC-C4-007、TC-C4-014、TC-C4-016 |
| BSR-C3-007 | UR-C4-005、UR-C4-007 | FR-C4-014、FR-C4-015 | AC-008 | TC-C4-008、TC-C4-011、TC-C4-016 |
| BSR-C3-008 | UR-C4-007、UR-C4-008 | FR-C4-015、FR-C4-016、FR-C4-017、FR-C4-018、FR-C4-019 | AC-009、AC-010、AC-011、AC-012 | TC-C4-001、TC-C4-009、TC-C4-010、TC-C4-011、TC-C4-014 |
| BSR-C3-009 | UR-C4-009 | FR-C4-020、FR-C4-021 | AC-013 | TC-C4-012、TC-C4-014、TC-C4-017 |
| BSR-C3-010 | UR-C4-010 | FR-C4-022 | AC-014 | TC-C4-013 |

## C4 BSR -> UR -> FR -> AC -> TC -> UI 完整追踪摘要

| BSR | UR | FR | AC | TC | UI |
| --- | --- | --- | --- | --- | --- |
| BSR-C3-001 | UR-C4-001 | FR-C4-001、FR-C4-002、FR-C4-003 | AC-001、AC-004 | TC-C4-001、TC-C4-002、TC-C4-015 | UI-C4-003、UI-C4-004、UI-C4-005 |
| BSR-C3-002 | UR-C4-002 | FR-C4-004、FR-C4-005 | AC-002、AC-012 | TC-C4-001、TC-C4-014 | UI-C4-006、UI-C4-013 |
| BSR-C3-003 | UR-C4-003 | FR-C4-006、FR-C4-007、FR-C4-008 | AC-003 | TC-C4-004、TC-C4-005 | UI-C4-006、UI-C4-014 |
| BSR-C3-004 | UR-C4-004 | FR-C4-008、FR-C4-009、FR-C4-010 | AC-004、AC-012 | TC-C4-003、TC-C4-006、TC-C4-014 | UI-C4-005、UI-C4-007、UI-C4-013 |
| BSR-C3-005 | UR-C4-005 | FR-C4-011、FR-C4-019 | AC-005、AC-012 | TC-C4-001、TC-C4-014、TC-C4-016 | UI-C4-007、UI-C4-008、UI-C4-013 |
| BSR-C3-006 | UR-C4-005、UR-C4-006 | FR-C4-012、FR-C4-013、FR-C4-019 | AC-006、AC-007、AC-012 | TC-C4-007、TC-C4-014、TC-C4-016 | UI-C4-008、UI-C4-009、UI-C4-013 |
| BSR-C3-007 | UR-C4-005、UR-C4-007 | FR-C4-014、FR-C4-015 | AC-008 | TC-C4-008、TC-C4-011、TC-C4-016 | UI-C4-008、UI-C4-011、UI-C4-014 |
| BSR-C3-008 | UR-C4-007、UR-C4-008 | FR-C4-015、FR-C4-016、FR-C4-017、FR-C4-018、FR-C4-019 | AC-009、AC-010、AC-011、AC-012 | TC-C4-001、TC-C4-009、TC-C4-010、TC-C4-011、TC-C4-014 | UI-C4-010、UI-C4-011、UI-C4-012、UI-C4-013 |
| BSR-C3-009 | UR-C4-009 | FR-C4-020、FR-C4-021 | AC-013 | TC-C4-012、TC-C4-014、TC-C4-017 | UI-C4-001、UI-C4-002、UI-C4-011、UI-C4-014 |
| BSR-C3-010 | UR-C4-010 | FR-C4-022 | AC-014 | TC-C4-013 | UI-C4-014 |

## C4 NFR -> TC/REVIEW 验证摘要

| NFR | 验证方式 |
| --- | --- |
| NFR-C4-001 | TC-C4-001、TC-C4-007、TC-C4-014 |
| NFR-C4-002 | TC-C4-002、TC-C4-006、TC-C4-009、TC-C4-010、TC-C4-011、TC-C4-016 |
| NFR-C4-003 | TC-C4-012、TC-C4-017 |
| NFR-C4-004 | TC-C4-013 |
| NFR-C4-005 | C4 原型评审记录 REVIEW-C4-001..016 |

## C4 原型页面覆盖摘要

| 原型页面 | 覆盖需求 | 覆盖测试 |
| --- | --- | --- |
| UI-C4-001 登录 | UR-C4-009 | TC-C4-012 |
| UI-C4-002 首页或工作台 | UR-C4-004、UR-C4-007 | TC-C4-001、TC-C4-004..014 |
| UI-C4-003 回收批次列表 | UR-C4-001 | TC-C4-001 |
| UI-C4-004 新建回收批次 | UR-C4-001 | TC-C4-001、TC-C4-002、TC-C4-015 |
| UI-C4-005 回收批次详情 | UR-C4-004 | TC-C4-003、TC-C4-006 |
| UI-C4-006 电池登记 | UR-C4-002、UR-C4-003 | TC-C4-001、TC-C4-004、TC-C4-005 |
| UI-C4-007 待验收列表 | UR-C4-005 | TC-C4-001、TC-C4-007、TC-C4-008 |
| UI-C4-008 验收登记 | UR-C4-005 | TC-C4-007、TC-C4-008、TC-C4-016 |
| UI-C4-009 补充资料 | UR-C4-006 | TC-C4-007 |
| UI-C4-010 待入库列表 | UR-C4-007 | TC-C4-001 |
| UI-C4-011 入库办理 | UR-C4-007、UR-C4-009 | TC-C4-009、TC-C4-010、TC-C4-011、TC-C4-012 |
| UI-C4-012 库存列表 | UR-C4-008 | TC-C4-001 |
| UI-C4-013 电池追溯详情 | UR-C4-008 | TC-C4-001、TC-C4-014 |
| UI-C4-014 异常提示 | UR-C4-003、UR-C4-009、UR-C4-010 | TC-C4-004、TC-C4-012、TC-C4-013 |

## 系统设计技术追踪项

当前技术设计状态：已确认。
追踪链扩展为：`BSR -> UR -> FR -> AC -> TC -> UI -> API -> DB -> MODULE`。

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| TD-ARCH-001 | 总体设计 | `docs/design/first-slice-overall-design.md` | 已确认 |
| TD-ARCH-002 | 架构设计 | `docs/design/first-slice-architecture-design.md` | 已确认 |
| TD-DB-001 | 数据库设计 | `docs/database/first-slice-database-design.md` | 已确认 |
| TD-DB-002 | 数据字典 | `docs/database/first-slice-data-dictionary.md` | 已确认 |
| TD-DB-003 | SQL 设计稿 | `contracts/database/first-slice-schema-design.sql` | 已确认 |
| TD-API-001 | API 设计 | `docs/api/first-slice-api-design.md` | 已确认 |
| TD-API-002 | OpenAPI 契约 | `contracts/api/openapi-first-slice.yaml` | 已确认 |
| TD-SEC-001 | 安全设计 | `docs/security/first-slice-security-design.md` | 已确认 |
| TD-DEP-001 | 部署设计 | `docs/deployment/first-slice-deployment-design.md` | 已确认 |
| TD-TEST-001 | 技术测试设计 | `docs/testing/first-slice-technical-test-design.md` | 已确认 |
| TD-REVIEW-001 | 技术评审记录 | `docs/design/first-slice-technical-review-record.md` | 已确认 |

## 系统设计 FR -> API -> DB -> MODULE 追踪摘要

| FR | UI | API | DB | MODULE |
| --- | --- | --- | --- | --- |
| FR-C4-001 | UI-C4-004 | `POST /api/v1/recycle-batches` | `recycle_batch` | MOD-BATCH |
| FR-C4-002 | UI-C4-004 | `POST /api/v1/recycle-batches`、`PUT /api/v1/recycle-batches/{id}` | `recycle_batch` | MOD-BATCH |
| FR-C4-003 | UI-C4-004 | `POST /api/v1/recycle-batches`、`PUT /api/v1/recycle-batches/{id}`、`POST /api/v1/attachments`、`GET /api/v1/attachments/{id}/download` | `recycle_batch`、`business_attachment`、`idempotency_record` | MOD-BATCH、MOD-ATTACHMENT、MOD-IDEMPOTENCY |
| FR-C4-004 | UI-C4-006 | `POST /api/v1/batteries` | `battery`、`battery_registration_candidate`、`lifecycle_event`、`audit_log`、`idempotency_record` | MOD-BATTERY、MOD-DUPLICATE、MOD-TRACE、MOD-AUDIT、MOD-IDEMPOTENCY |
| FR-C4-005 | UI-C4-006 | `POST /api/v1/batteries` | `battery` | MOD-BATTERY |
| FR-C4-006 | UI-C4-006、UI-C4-014 | `POST /api/v1/batteries`、`POST /api/v1/batteries/duplicate-check` | `battery`、`battery_registration_candidate` | MOD-BATTERY、MOD-DUPLICATE |
| FR-C4-007 | UI-C4-006 | `POST /api/v1/battery-registration-candidates/{id}/duplicate-resolution` | `battery_registration_candidate`、`duplicate_code_review`、`battery` | MOD-DUPLICATE |
| FR-C4-008 | UI-C4-005、UI-C4-006 | `POST /api/v1/recycle-batches/{id}/batteries` | `recycle_batch_battery`、`battery`；V6 `active_battery_id` + `uk_rbb_active_battery`；I2-TC-035、042 | MOD-BATCH、MOD-BATTERY |
| FR-C4-009 | UI-C4-005 | `POST /api/v1/recycle-batches/{id}/submit` | `recycle_batch`、`recycle_batch_battery`、`battery`、`audit_log` | MOD-BATCH、MOD-AUDIT |
| FR-C4-010 | UI-C4-005、UI-C4-007 | `POST /api/v1/recycle-batches/{id}/submit` | `recycle_batch`、`battery`、`lifecycle_event` | MOD-BATCH、MOD-TRACE |
| FR-C4-011 | UI-C4-008 | `POST /api/v1/batteries/{id}/acceptances` | `acceptance_record`、`battery`、`recycle_batch`、`lifecycle_event` | MOD-ACCEPTANCE、MOD-TRACE |
| FR-C4-012 | UI-C4-008 | `POST /api/v1/batteries/{id}/acceptances` | `acceptance_record`、`battery`、`lifecycle_event` | MOD-ACCEPTANCE、MOD-TRACE |
| FR-C4-013 | UI-C4-009 | `POST /api/v1/batteries/{id}/acceptance-supplements`、`POST /api/v1/attachments`、`GET /api/v1/attachments/{id}/download` | `acceptance_supplement`、`battery`、`business_attachment`、`lifecycle_event`、`idempotency_record` | MOD-ACCEPTANCE、MOD-ATTACHMENT、MOD-TRACE、MOD-IDEMPOTENCY |
| FR-C4-014 | UI-C4-008、UI-C4-014 | `POST /api/v1/batteries/{id}/acceptances` | `acceptance_record`、`battery`、`lifecycle_event` | MOD-ACCEPTANCE、MOD-TRACE |
| FR-C4-015 | UI-C4-010、UI-C4-011、UI-C4-014 | `GET /api/v1/inbounds/pending`、`POST /api/v1/batteries/{id}/inbounds` | `battery`、`audit_log` | MOD-INBOUND、MOD-AUDIT |
| FR-C4-016 | UI-C4-011 | `GET /api/v1/warehouses`、`GET /api/v1/warehouses/{id}/locations`、`POST /api/v1/batteries/{id}/inbounds` | `warehouse`、`warehouse_location`、`audit_log` | MOD-INBOUND、MOD-AUDIT |
| FR-C4-017 | UI-C4-011、UI-C4-012 | `POST /api/v1/batteries/{id}/inbounds` | `inbound_record`、`inventory`、`battery`、`lifecycle_event` | MOD-INBOUND、MOD-INVENTORY、MOD-TRACE |
| FR-C4-018 | UI-C4-012 | `GET /api/v1/inventory` | `inventory`、`battery`、`warehouse`、`warehouse_location` | MOD-INVENTORY |
| FR-C4-019 | UI-C4-013 | `GET /api/v1/batteries/{id}/trace` | `lifecycle_event` | MOD-TRACE |
| FR-C4-020 | UI-C4-014 | `GET /api/v1/audit-logs` | `audit_log` | MOD-AUDIT |
| FR-C4-021 | UI-C4-001、UI-C4-002 | `POST /api/v1/auth/login`、`GET /api/v1/auth/current-user`、`GET /api/v1/users`、`GET /api/v1/roles`、`GET /api/v1/permissions`、`PUT /api/v1/users/{id}/roles`、`PUT /api/v1/roles/{id}/permissions` | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`audit_log`、`idempotency_record` | MOD-AUTH、MOD-AUDIT、MOD-IDEMPOTENCY |
| FR-C4-022 | UI-C4-014 | 删除保护统一拦截：`DELETE /api/v1/acceptance-records/{id}`、`DELETE /api/v1/inbound-records/{id}`、`DELETE /api/v1/lifecycle-events/{id}` | `acceptance_record`、`inbound_record`、`inventory`、`lifecycle_event`、`audit_log` | MOD-AUDIT |

## 系统设计完整链路摘要

| BSR | UR | FR | AC | TC | UI | API | DB | MODULE |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BSR-C3-001 | UR-C4-001 | FR-C4-001..003 | AC-001、AC-004 | TC-C4-001、002、015、024、025 | UI-C4-003..005 | 批次创建、修改、查询、提交、附件接口 | `recycle_batch`、`business_attachment`、`idempotency_record` | MOD-BATCH、MOD-ATTACHMENT、MOD-IDEMPOTENCY |
| BSR-C3-002 | UR-C4-002 | FR-C4-004、005 | AC-002、AC-012 | TC-C4-001、014 | UI-C4-006、013 | 电池登记、追溯接口 | `battery`、`battery_registration_candidate`、`lifecycle_event`、`audit_log`、`idempotency_record` | MOD-BATTERY、MOD-DUPLICATE、MOD-TRACE、MOD-AUDIT、MOD-IDEMPOTENCY |
| BSR-C3-003 | UR-C4-003 | FR-C4-006..008 | AC-003 | TC-C4-004、005 | UI-C4-006、014 | 重复检查、候选核实、加入批次接口 | `battery`、`battery_registration_candidate`、`duplicate_code_review`、`recycle_batch_battery` | MOD-DUPLICATE、MOD-BATTERY、MOD-BATCH |
| BSR-C3-004 | UR-C4-004 | FR-C4-008..010 | AC-004、AC-012 | TC-C4-003、006、014 | UI-C4-005、007、013 | 加入批次、提交批次、追溯接口 | `recycle_batch`、`battery`、`recycle_batch_battery`、`lifecycle_event` | MOD-BATCH、MOD-TRACE |
| BSR-C3-005 | UR-C4-005 | FR-C4-011、019 | AC-005、AC-012 | TC-C4-001、014、016 | UI-C4-007、008、013 | 验收接口、追溯接口 | `acceptance_record`、`battery`、`lifecycle_event` | MOD-ACCEPTANCE、MOD-TRACE |
| BSR-C3-006 | UR-C4-005、006 | FR-C4-012、013、019 | AC-006、AC-007、AC-012 | TC-C4-007、014、016、024、025 | UI-C4-008、009、013 | 验收接口、补充资料接口、附件接口、追溯接口 | `acceptance_record`、`acceptance_supplement`、`business_attachment`、`battery`、`lifecycle_event`、`idempotency_record` | MOD-ACCEPTANCE、MOD-ATTACHMENT、MOD-TRACE、MOD-IDEMPOTENCY |
| BSR-C3-007 | UR-C4-005、007 | FR-C4-014、015 | AC-008 | TC-C4-008、011、016 | UI-C4-008、011、014 | 验收接口、入库接口 | `acceptance_record`、`battery`、`audit_log` | MOD-ACCEPTANCE、MOD-INBOUND、MOD-AUDIT |
| BSR-C3-008 | UR-C4-007、008 | FR-C4-015..019 | AC-009..012 | TC-C4-001、009、010、011、014 | UI-C4-010..013 | 待入库、仓库库位、入库、库存、追溯接口 | `warehouse`、`warehouse_location`、`inbound_record`、`inventory`、`battery`、`lifecycle_event`、`idempotency_record` | MOD-INBOUND、MOD-INVENTORY、MOD-TRACE、MOD-IDEMPOTENCY |
| BSR-C3-009 | UR-C4-009 | FR-C4-020、021 | AC-013 | TC-C4-012、014、017 | UI-C4-001、002、011、014 | 登录、当前用户、用户、角色、权限、审计接口 | 权限表、`audit_log`、`idempotency_record` | MOD-AUTH、MOD-AUDIT、MOD-IDEMPOTENCY |
| BSR-C3-010 | UR-C4-010 | FR-C4-022 | AC-014 | TC-C4-013 | UI-C4-014 | 删除保护统一拦截路径 | 生效业务表、`audit_log` | MOD-AUDIT |

## 系统设计覆盖检查

| 检查项 | 结果 |
| --- | --- |
| 没有需求来源的表 | 未发现；21 张表均服务第一切片模块。 |
| 没有需求来源的接口 | 未发现；19 个 OpenAPI 路径均映射 FR/AC/TC/UI。 |
| 有需求但没有接口 | 未发现；FR-C4-001..022 均有 API 或统一拦截设计。 |
| 有接口但没有权限定义 | 未发现；OpenAPI `x-traceability.permission` 已定义。 |
| 有状态变化但没有事件记录 | 未发现；批次提交、验收、补充资料、入库均写生命周期事件。 |
| 有测试但无法追踪到需求 | 未发现；TC-C4-001..017 均已映射技术验证。 |

## 数据治理 V1.1 变更追踪草案

当前状态：已确认。
追踪链：`CR-DG -> UR-DG -> FR-DG -> AC-DG -> TC-DG -> UI-DG`。

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| CR-DG-001 | 数据治理能力变更请求 V1.1 | `docs/change/CR-DG-001-data-governance-change-request.md` | 已确认 |
| DG-BIZ-001 | 数据治理第一切片 V1.1 | `docs/business/data-governance-first-slice.md` | 已确认 |
| DG-REQ-001 | 数据治理需求规格 V1.1 | `docs/requirements/data-governance-requirements.md` | 已确认 |
| DG-TEST-001 | 数据治理测试设计 V1.1 | `docs/testing/data-governance-test-design.md` | 已确认 |
| DG-PROTO-001 | 数据治理原型规格 V1.1 | `docs/prototype/data-governance-prototype-spec.md` | 已确认 |
| DG-PROTO-002 | 数据治理低保真原型 V1.1 | `prototype/data-governance/` | 已确认 |
| DG-PROTO-003 | 数据治理原型实际评审记录 V1.1 | `docs/prototype/data-governance-prototype-review-record.md` | 已确认 |

## 数据治理 UR -> FR -> AC -> TC -> UI 草案

| UR | FR | AC | TC | UI |
| --- | --- | --- | --- | --- |
| UR-DG-001 | FR-DG-001、002、003、018 | AC-DG-001、002、003 | TC-DG-001、002、003、023、024、027、029、032 | UI-DG-001 |
| UR-DG-002 | FR-DG-004、005、006、018 | AC-DG-004、005、006 | TC-DG-004、005、006、022、023、024、027、032 | UI-DG-002、003 |
| UR-DG-003 | FR-DG-007、008、009、010、018 | AC-DG-007、008、009、016 | TC-DG-007、008、009、023、025、028、031、032 | UI-DG-003、004、005、006 |
| UR-DG-004 | FR-DG-010、011、012、013、018 | AC-DG-010、011、016 | TC-DG-010、011、012、013、014、017、023、024、026、030、031、032 | UI-DG-005、007 |
| UR-DG-005 | FR-DG-010、014、015、016、018 | AC-DG-012、013、014、016 | TC-DG-015、016、017、018、019、020、023、024、030、031、032 | UI-DG-005、007 |
| UR-DG-006 | FR-DG-017 | AC-DG-015 | TC-DG-021、028 | UI-DG-008 |

## 数据治理 NFR 验证矩阵

| NFR | 验证方式 |
| --- | --- |
| NFR-DG-001 | TC-DG-026 |
| NFR-DG-002 | TC-DG-023、TC-DG-032 |
| NFR-DG-003 | TC-DG-024、TC-DG-025、TC-DG-028 |
| NFR-DG-004 | TC-DG-029 |
| NFR-DG-005 | TC-DG-003、TC-DG-012、TC-DG-013、TC-DG-014、TC-DG-018、TC-DG-019、TC-DG-022、TC-DG-024、TC-DG-025、TC-DG-030、TC-DG-031 |

## 数据治理 V1.1 技术设计追踪项

当前技术设计状态：已确认。
追踪链扩展为：`CR-DG -> UR-DG -> FR-DG -> AC-DG -> TC-DG -> UI-DG -> API-DG -> DB-DG -> MODULE`。

| 编号 | 类型 | 来源 | 状态 |
| --- | --- | --- | --- |
| DG-TD-001 | 影响分析 | `docs/design/data-governance-v1.1-impact-analysis.md` | 已确认 |
| DG-TD-002 | 总体设计 | `docs/design/data-governance-v1.1-overall-design.md` | 已确认 |
| DG-TD-003 | 架构设计 | `docs/design/data-governance-v1.1-architecture-design.md` | 已确认 |
| DG-TD-004 | 数据库设计 | `docs/database/data-governance-v1.1-database-design.md` | 已确认 |
| DG-TD-005 | 数据字典 | `docs/database/data-governance-v1.1-data-dictionary.md` | 已确认 |
| DG-TD-006 | 增量 SQL | `contracts/database/data-governance-v1.1-migration.sql` | 已确认 |
| DG-TD-007 | API 设计 | `docs/api/data-governance-v1.1-api-design.md` | 已确认 |
| DG-TD-008 | OpenAPI 契约 | `contracts/api/openapi-data-governance-v1.1.yaml` | 已确认 |
| DG-TD-009 | 安全设计 | `docs/security/data-governance-v1.1-security-design.md` | 已确认 |
| DG-TD-010 | 部署设计 | `docs/deployment/data-governance-v1.1-deployment-design.md` | 已确认 |
| DG-TD-011 | 技术测试设计 | `docs/testing/data-governance-v1.1-technical-test-design.md` | 已确认 |
| DG-TD-012 | 技术评审记录 | `docs/design/data-governance-v1.1-technical-review-record.md` | 已确认 |

## 数据治理 V1.1 FR -> API -> DB -> MODULE 追踪摘要

| FR | UI | API | DB | MODULE |
| --- | --- | --- | --- | --- |
| FR-DG-001 | UI-DG-001 | `GET /api/v1/data-quality/rules` | `dq_rule_definition`、`dq_enterprise_rule_config` | MOD-DQ-RULE |
| FR-DG-002、FR-DG-003 | UI-DG-001 | `PATCH /api/v1/data-quality/rules/{ruleCode}/status` | `dq_enterprise_rule_config`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-RULE、MOD-DQ-AUDIT、MOD-IDEMPOTENCY |
| FR-DG-004..006 | UI-DG-002、UI-DG-003 | `POST /api/v1/data-quality/check-runs`、`GET /api/v1/data-quality/check-runs/{runId}` | `dq_check_run`、`dq_check_result`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-CHECK |
| FR-DG-007、FR-DG-008 | UI-DG-003、UI-DG-004 | `GET /api/v1/data-quality/check-runs/{runId}/results`、`GET /api/v1/data-quality/issues` | `dq_check_result`、`dq_issue` | MOD-DQ-CHECK、MOD-DQ-ISSUE |
| FR-DG-009 | UI-DG-005 | `POST /api/v1/data-quality/issues/{issueId}/assign` | `dq_issue`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-ISSUE |
| FR-DG-010、FR-DG-011 | UI-DG-005 | `POST /api/v1/data-quality/issues/{issueId}/start-processing` | `dq_issue`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-ISSUE |
| FR-DG-012、FR-DG-013 | UI-DG-007 | `POST /api/v1/data-quality/issues/{issueId}/remediations` | `dq_remediation`、`business_attachment`、`dq_issue`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-REMEDIATION |
| FR-DG-014..016 | UI-DG-005、UI-DG-007 | `POST /api/v1/data-quality/issues/{issueId}/rechecks` | `dq_recheck`、`dq_check_run`、`dq_check_result`、`dq_issue`、`dq_operation_audit`、`idempotency_record` | MOD-DQ-RECHECK |
| FR-DG-017 | UI-DG-008 | `GET /api/v1/data-quality/dashboard/summary` | `dq_enterprise_rule_config`、`dq_check_run`、`dq_issue` | MOD-DQ-DASHBOARD |
| FR-DG-018 | UI-DG-001..008 | `GET /api/v1/data-quality/audit-logs`、全部写接口和拒绝路径 | `dq_operation_audit` | MOD-DQ-AUDIT |

## 数据治理 V1.1 技术验证追踪

| 技术验证 | 覆盖 |
| --- | --- |
| TD-DG-TC-001..007 | DQ-001..DQ-007 固定规则处理器 |
| TD-DG-TC-008..012 | 规则、检查任务和失败处理 |
| TD-DG-TC-013 | 未关闭问题唯一约束 |
| TD-DG-TC-014..019 | 问题状态机 |
| TD-DG-TC-020 | 最近 30 天看板 SQL |
| TD-DG-TC-021 | 企业隔离 |
| TD-DG-TC-022 | 失败状态不变 |
| TD-DG-TC-023..024 | 幂等 |
| TD-DG-TC-025 | 结构化审计 |
| TD-DG-TC-026 | MySQL 8.4 增量 SQL 验证和 8 张治理表断言 |
| TD-DG-TC-027 | OpenAPI 严格解析 |
| TD-DG-TC-028 | 完整追踪矩阵 |
| TD-DG-TC-029..036 | 整改证据、机器复核、只读系统管理员、稳定对象去重、失败回滚和企业默认规则配置 |
| TD-DG-TC-037..042 | 治理审计查询、规则元数据完整性、复核状态约束、迁移幂等和权限角色初始化 |

## 数据治理开放问题追踪

| 问题 | 影响范围 | 当前处理 |
| --- | --- | --- |
| DG-Q-001 | 质量规则配置权限 | 已解决：V1.1 固定规则默认启用；系统管理员只读查看规则、问题、看板和审计，不执行规则配置、整改或复核。 |
| DG-Q-002 | 质量问题责任人 | 已解决：业务主管分配和复核，回收操作员和仓库管理员处理各自问题。 |
| DG-Q-003 | 检查触发方式 | 已解决：V1.1 只支持手工质量检查。 |
| DG-Q-004 | 第一版规则范围 | 已解决：DQ-001..DQ-007 纳入，DQ-008 延期。 |
| DG-Q-005 | 错误数据处理方式 | 已解决：不得直接覆盖已生效数据，需关联更正记录或处理证据。 |
| DG-Q-006 | 问题关闭条件 | 已解决：处理证据、同规则复查通过、主管复核和完整审计齐备后关闭。 |
