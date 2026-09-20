# 追踪索引

当前阶段：C4 原型与需求验证。

C4 已围绕第一条纵向业务切片建立需求、测试和低保真原型追踪链。当前状态为已确认，C4 已关闭。后续进入 `system-design`，在技术设计评审前仍不得编写生产代码。

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
| FR-C4-008 | UI-C4-005、UI-C4-006 | `POST /api/v1/recycle-batches/{id}/batteries` | `recycle_batch_battery`、`battery` | MOD-BATCH、MOD-BATTERY |
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
