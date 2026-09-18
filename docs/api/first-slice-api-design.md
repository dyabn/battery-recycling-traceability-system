# 第一切片 API 设计 V0.1

文档名称：第一切片 API 设计  
系统名称：面向动力电池回收利用企业的流转协同与追溯管理系统  
当前阶段：system-design  
当前检查点：设计评审  
文档状态：待评审  
接口风格：REST + JSON + JWT  
OpenAPI 文件：`contracts/api/openapi-first-slice.yaml`

## 1. 统一约定

### 1.1 基础路径

`/api/v1`

### 1.2 统一响应

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "traceId": "..."
}
```

### 1.3 认证和权限

- 除登录接口外，所有接口必须携带 `Authorization: Bearer <jwt>`。
- 写操作必须进行方法级权限校验。
- 越权操作返回 `FORBIDDEN_OPERATION` 并写入审计日志。
- 写操作建议携带 `Idempotency-Key`，防止重复提交。

### 1.4 重点错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `BATCH_REQUIRED_FIELD_MISSING` | 400 | 批次来源必填信息缺失。 |
| `BATCH_EMPTY` | 400 | 空批次不能提交验收。 |
| `BATTERY_ORIGINAL_CODE_DUPLICATE` | 409 | 原始编码疑似重复。 |
| `BATTERY_DUPLICATE_UNRESOLVED` | 409 | 疑似重复未核实。 |
| `INVALID_BATTERY_STATE` | 409 | 电池当前状态不允许操作。 |
| `ACCEPTANCE_REQUIRED_FIELD_MISSING` | 400 | 验收必填字段缺失。 |
| `WAREHOUSE_DISABLED` | 409 | 仓库未启用。 |
| `LOCATION_DISABLED` | 409 | 库位未启用。 |
| `LOCATION_WAREHOUSE_MISMATCH` | 409 | 库位不属于所选仓库。 |
| `FORBIDDEN_OPERATION` | 403 | 无权限操作。 |
| `EFFECTIVE_RECORD_DELETE_FORBIDDEN` | 409 | 已生效记录禁止删除。 |
| `DUPLICATE_SUBMISSION` | 409 | 重复提交或重复入库。 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 同一幂等键被不同请求体复用。 |

## 2. 接口清单

| 接口 | 权限 | FR | AC | TC | UI | 模块 | 主要表 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `POST /auth/login` | 匿名 | FR-C4-020、021 | AC-013 | TC-C4-012、017 | UI-C4-001 | MOD-AUTH | `sys_user`、`audit_log` |
| `GET /auth/current-user` | 登录 | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_user`、权限表 |
| `POST /recycle-batches` | `batch:create` | FR-C4-001..003 | AC-001 | TC-C4-001、015 | UI-C4-004 | MOD-BATCH | `recycle_batch` |
| `GET /recycle-batches` | `batch:read` | FR-C4-001 | AC-001 | TC-C4-001 | UI-C4-003 | MOD-BATCH | `recycle_batch` |
| `GET /recycle-batches/{id}` | `batch:read` | FR-C4-008..010 | AC-004 | TC-C4-003、006 | UI-C4-005 | MOD-BATCH | `recycle_batch`、`battery` |
| `PUT /recycle-batches/{id}` | `batch:create` | FR-C4-002、003 | AC-001 | TC-C4-002、015 | UI-C4-004 | MOD-BATCH | `recycle_batch` |
| `POST /recycle-batches/{id}/submit` | `batch:submit` | FR-C4-009、010、019 | AC-004、012 | TC-C4-002、003、006、014 | UI-C4-005 | MOD-BATCH | `recycle_batch`、`battery`、`lifecycle_event` |
| `POST /batteries` | `battery:create` | FR-C4-004..006、019 | AC-002、003、012 | TC-C4-001、004、014 | UI-C4-006 | MOD-BATTERY | `battery`、`lifecycle_event` |
| `POST /batteries/duplicate-check` | `battery:create` | FR-C4-006 | AC-003 | TC-C4-004 | UI-C4-006 | MOD-DUPLICATE | `battery` |
| `POST /battery-registration-candidates/{id}/duplicate-resolution` | `battery:duplicate:resolve` | FR-C4-007 | AC-003 | TC-C4-005 | UI-C4-006 | MOD-DUPLICATE | `battery_registration_candidate`、`duplicate_code_review`、`battery` |
| `POST /recycle-batches/{id}/batteries` | `battery:create` | FR-C4-008 | AC-003、004 | TC-C4-001、006 | UI-C4-006 | MOD-BATCH | `recycle_batch_battery` |
| `GET /acceptances/pending` | `acceptance:create` | FR-C4-011..014 | AC-005..008 | TC-C4-001、007、008 | UI-C4-007 | MOD-ACCEPTANCE | `battery` |
| `POST /batteries/{id}/acceptances` | `acceptance:create` | FR-C4-011、012、014、019 | AC-005、006、008、012 | TC-C4-001、007、008、016 | UI-C4-008 | MOD-ACCEPTANCE | `acceptance_record`、`battery`、`recycle_batch` |
| `POST /batteries/{id}/acceptance-supplements` | `acceptance:supplement` | FR-C4-013、019 | AC-007、012 | TC-C4-007、014 | UI-C4-009 | MOD-ACCEPTANCE | `acceptance_supplement`、`battery` |
| `GET /inbounds/pending` | `inbound:create` | FR-C4-015 | AC-008、009 | TC-C4-008、011 | UI-C4-010 | MOD-INBOUND | `battery` |
| `POST /batteries/{id}/inbounds` | `inbound:create` | FR-C4-015..017、019、020 | AC-009..012 | TC-C4-001、009、010、011、014 | UI-C4-011 | MOD-INBOUND | `inbound_record`、`inventory`、`battery` |
| `GET /inventory` | `inventory:read` | FR-C4-018 | AC-011 | TC-C4-001 | UI-C4-012 | MOD-INVENTORY | `inventory`、`battery` |
| `GET /batteries/{id}/trace` | `trace:read` | FR-C4-019 | AC-012 | TC-C4-001、014 | UI-C4-013 | MOD-TRACE | `lifecycle_event` |
| `GET /audit-logs` | `audit:read` | FR-C4-020、021 | AC-013 | TC-C4-012、017 | UI-C4-014 | MOD-AUDIT | `audit_log` |
| `GET /roles` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_role` |
| `GET /permissions` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_permission` |
| `GET /warehouses` | `warehouse:read` | FR-C4-016 | AC-009 | TC-C4-009、010 | UI-C4-011 | MOD-INBOUND | `warehouse` |
| `GET /warehouses/{id}/locations` | `warehouse:read` | FR-C4-016 | AC-009 | TC-C4-009、010 | UI-C4-011 | MOD-INBOUND | `warehouse_location` |
| `POST /attachments` | `attachment:upload` | FR-C4-003、013 | AC-001、007 | TC-C4-007、015 | UI-C4-004、009 | MOD-ATTACHMENT | `business_attachment`、`idempotency_record` |
| `GET /attachments/{id}/download` | `attachment:read` | FR-C4-003、013 | AC-001、007 | TC-C4-007、015 | UI-C4-004、009 | MOD-ATTACHMENT | `business_attachment` |
| `GET /users` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_user` |
| `PUT /users/{id}/roles` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_user_role`、`audit_log`、`idempotency_record` |
| `PUT /roles/{id}/permissions` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_role_permission`、`audit_log`、`idempotency_record` |

## 3. 写接口事务和状态变化

| 接口 | 前置状态 | 成功后状态 | 事务范围 | 生命周期事件 | 审计事件 |
| --- | --- | --- | --- | --- | --- |
| `POST /recycle-batches` | 无 | 批次 `DRAFT` | 写批次。 | 无 | 可记录创建操作。 |
| `POST /batteries` | 原始编码未重复或为空；重复时进入候选 | 不重复时电池 `REGISTERED`；重复时创建候选 `PENDING_REVIEW` | 写电池或候选记录、写登记事件。 | `BATTERY_REGISTERED` 或 `BATTERY_CANDIDATE_CREATED` | 重复时记录核实待处理审计。 |
| `POST /battery-registration-candidates/{id}/duplicate-resolution` | 候选 `PENDING_REVIEW` | 同一电池关闭候选并返回原档案；不同电池关闭候选并创建新档案 | 写核实记录、更新候选、必要时创建电池。 | `DUPLICATE_RESOLVED_SAME` 或 `DUPLICATE_RESOLVED_DIFFERENT` | 记录核实处理。 |
| `POST /recycle-batches/{id}/batteries` | 批次 `DRAFT`，电池有效 | 关系生效 | 写批次电池关系。 | 无 | 失败时审计。 |
| `POST /recycle-batches/{id}/submit` | 批次 `DRAFT` | 批次 `PENDING_ACCEPTANCE`，电池 `PENDING_ACCEPTANCE` | 批次、电池、事件、审计。 | `BATCH_SUBMITTED` | 提交失败审计。 |
| `POST /batteries/{id}/acceptances` | 电池 `PENDING_ACCEPTANCE` | 通过则 `ACCEPTED_PENDING_INBOUND`，资料不足则 `PENDING_SUPPLEMENT`，不通过则 `ACCEPTANCE_REJECTED` | 验收、电池、批次、事件、审计。 | `ACCEPTANCE_PASSED`、`ACCEPTANCE_NEED_SUPPLEMENT`、`ACCEPTANCE_REJECTED` | 验收失败审计。 |
| `POST /acceptance-supplements` | 电池 `PENDING_SUPPLEMENT` | 电池 `PENDING_ACCEPTANCE` | 补充记录、电池、事件、审计。 | `ACCEPTANCE_SUPPLEMENTED` | 失败审计。 |
| `POST /batteries/{id}/inbounds` | 电池 `ACCEPTED_PENDING_INBOUND` | 电池 `IN_STOCK`，当前库存有效 | 入库、库存、电池、事件、审计。 | `INBOUND_COMPLETED` | 入库失败审计。 |
| `POST /attachments` | 已登录且有附件权限 | 附件元数据有效 | 写附件元数据，保存文件。 | 无 | 上传失败审计。 |
| `PUT /users/{id}/roles` | 系统管理员 | 用户角色关系更新 | 写用户角色关系、审计和幂等记录。 | 无 | `PERMISSION_CHANGED`。 |
| `PUT /roles/{id}/permissions` | 系统管理员 | 角色权限关系更新 | 写角色权限关系、审计和幂等记录。 | 无 | `PERMISSION_CHANGED`。 |

## 4. 关键接口字段

### 4.1 创建回收批次

- 必填：`sourceType`、`sourceSubjectName`、`handoverDate`。
- 选填：`handoverLocation`、`relatedDocumentNo`、`handoverPerson`、`remark`、`attachmentIds`。
- 自动：`batchNo`、`enterpriseId`、`createdBy`、`createdAt`、`batchStatus`。

### 4.2 电池登记与重复核实

- 自动：`systemTraceCode`、`currentResponsibleEnterpriseId`、`lifecycleStatus`。
- 必填：`batteryType=PACK`、`batteryChemistry`，其中电池体系允许 `UNKNOWN`。
- 选填：`originalCode`、`batteryModel`、`manufacturer`、`nominalCapacity`、`productionDate`。
- 原始编码为空或不重复时，`POST /batteries` 直接创建有效 `battery`。
- 原始编码疑似重复时，`POST /batteries` 不创建有效 `battery`，而是创建 `battery_registration_candidate` 并返回候选 ID。
- `POST /battery-registration-candidates/{id}/duplicate-resolution` 核实为同一电池时返回原档案；核实为不同电池时必须填写 `duplicateReason` 并创建新 `battery`。

### 4.3 验收登记

- 必填：`acceptanceResult`、`identityCheckResult`、`appearanceCheckResult`、`documentCheckResult`。
- `NEED_SUPPLEMENT` 必须填写 `acceptanceNote`。
- `REJECT` 必须填写 `acceptanceNote`。
- 系统只记录人工结论，不自动判断行业阈值。

### 4.4 补充资料

- `supplementNote` 和 `attachmentIds` 至少填写一项。
- 附件先通过 `POST /attachments` 上传，再将 `attachmentIds` 关联到补充资料。

### 4.5 入库办理

- 必填：`warehouseId`、`locationId`。
- 仓库和库位必须启用。
- 库位必须属于所选仓库。
- 电池必须为 `ACCEPTED_PENDING_INBOUND`。

## 5. 幂等要求

| 接口 | 幂等方式 |
| --- | --- |
| 创建批次 | 客户端传 `Idempotency-Key`，服务端按用户、接口和键去重。 |
| 创建电池 | `system_trace_code` 由服务端生成；重复原始编码进入候选核实，不用原始编码做幂等键。 |
| 提交批次 | 幂等键 + 批次状态条件，重复提交返回既有状态或 `DUPLICATE_SUBMISSION`。 |
| 保存验收 | 幂等键 + 电池状态条件，防止重复点击产生多条最终验收。 |
| 办理入库 | 幂等键 + 电池状态条件 + 当前库存唯一约束。 |
| 上传附件 | 幂等键 + 文件摘要，避免重复元数据。 |
| 权限配置 | 幂等键 + 请求体摘要，避免重复写入权限关系。 |

幂等记录持久化到 `idempotency_record`。相同键、相同请求体返回首次处理结果；相同键、不同请求体返回 `IDEMPOTENCY_KEY_REUSED`。写接口必须先登记幂等处理状态，再在业务事务中写业务记录和首次响应。

## 6. 删除保护接口约定

第一切片不提供删除已生效业务记录的成功接口。若前端或用户尝试删除批次、验收记录、入库记录、库存记录或生命周期事件，后端统一返回 `EFFECTIVE_RECORD_DELETE_FORBIDDEN`，并写入 `audit_log`。

## 7. OpenAPI 说明

`contracts/api/openapi-first-slice.yaml` 用于接口评审和 Swagger 展示。后续正式开发时可拆分为模块级 API 文件，但接口语义和错误码不得偏离本设计。
