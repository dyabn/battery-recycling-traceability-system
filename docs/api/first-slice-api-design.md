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
| `POST /batteries/{id}/duplicate-resolution` | `battery:duplicate:resolve` | FR-C4-007 | AC-003 | TC-C4-005 | UI-C4-006 | MOD-DUPLICATE | `duplicate_code_review`、`battery` |
| `POST /recycle-batches/{id}/batteries` | `battery:create` | FR-C4-008 | AC-003、004 | TC-C4-001、006 | UI-C4-006 | MOD-BATCH | `recycle_batch_battery` |
| `GET /acceptances/pending` | `acceptance:create` | FR-C4-011..014 | AC-005..008 | TC-C4-001、007、008 | UI-C4-007 | MOD-ACCEPTANCE | `battery` |
| `POST /batteries/{id}/acceptances` | `acceptance:create` | FR-C4-011、012、014、019 | AC-005、006、008、012 | TC-C4-001、007、008、016 | UI-C4-008 | MOD-ACCEPTANCE | `acceptance_record`、`battery`、`recycle_batch` |
| `POST /batteries/{id}/acceptance-supplements` | `acceptance:supplement` | FR-C4-013、019 | AC-007、012 | TC-C4-007、014 | UI-C4-009 | MOD-ACCEPTANCE | `acceptance_supplement`、`battery` |
| `GET /inbounds/pending` | `inbound:create` | FR-C4-015 | AC-008、009 | TC-C4-008、011 | UI-C4-010 | MOD-INBOUND | `battery` |
| `POST /batteries/{id}/inbounds` | `inbound:create` | FR-C4-015..017、019、020 | AC-009..012 | TC-C4-001、009、010、011、014 | UI-C4-011 | MOD-INBOUND | `inbound_record`、`inventory`、`battery` |
| `GET /inventory` | `inventory:read` | FR-C4-018 | AC-011 | TC-C4-001 | UI-C4-012 | MOD-INVENTORY | `inventory`、`battery` |
| `GET /batteries/{id}/trace` | `trace:read` | FR-C4-019、020 | AC-012 | TC-C4-001、014 | UI-C4-013 | MOD-TRACE | `lifecycle_event`、`audit_log` |
| `GET /audit-logs` | `audit:read` | FR-C4-020、021 | AC-013 | TC-C4-012、017 | UI-C4-014 | MOD-AUDIT | `audit_log` |
| `GET /roles` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_role` |
| `GET /permissions` | `permission:manage` | FR-C4-021 | AC-013 | TC-C4-017 | UI-C4-002 | MOD-AUTH | `sys_permission` |

## 3. 写接口事务和状态变化

| 接口 | 前置状态 | 成功后状态 | 事务范围 | 生命周期事件 | 审计事件 |
| --- | --- | --- | --- | --- | --- |
| `POST /recycle-batches` | 无 | 批次 `DRAFT` | 写批次。 | 无 | 可记录创建操作。 |
| `POST /batteries` | 原始编码未重复或为空 | 电池 `REGISTERED` | 写电池、写登记事件。 | `BATTERY_REGISTERED` | 重复时记录失败审计。 |
| `POST /duplicate-resolution` | 电池 `SUSPECTED_DUPLICATE` | `RESOLVED_SAME` 或 `RESOLVED_DIFFERENT` | 写核实记录、更新电池。 | 可记录核实事件。 | 记录核实处理。 |
| `POST /recycle-batches/{id}/batteries` | 批次 `DRAFT`，电池有效 | 关系生效 | 写批次电池关系。 | 无 | 失败时审计。 |
| `POST /recycle-batches/{id}/submit` | 批次 `DRAFT` | 批次 `PENDING_ACCEPTANCE`，电池 `PENDING_ACCEPTANCE` | 批次、电池、事件、审计。 | `BATCH_SUBMITTED` | 提交失败审计。 |
| `POST /batteries/{id}/acceptances` | 电池 `PENDING_ACCEPTANCE` | 通过则 `ACCEPTED_PENDING_INBOUND`，资料不足则 `PENDING_SUPPLEMENT`，不通过则 `ACCEPTANCE_REJECTED` | 验收、电池、批次、事件、审计。 | `ACCEPTANCE_PASSED`、`ACCEPTANCE_NEED_SUPPLEMENT`、`ACCEPTANCE_REJECTED` | 验收失败审计。 |
| `POST /acceptance-supplements` | 电池 `PENDING_SUPPLEMENT` | 电池 `PENDING_ACCEPTANCE` | 补充记录、电池、事件、审计。 | `ACCEPTANCE_SUPPLEMENTED` | 失败审计。 |
| `POST /batteries/{id}/inbounds` | 电池 `ACCEPTED_PENDING_INBOUND` | 电池 `IN_STOCK`，当前库存有效 | 入库、库存、电池、事件、审计。 | `INBOUND_COMPLETED` | 入库失败审计。 |

## 4. 关键接口字段

### 4.1 创建回收批次

- 必填：`sourceType`、`sourceSubjectName`、`handoverDate`。
- 选填：`handoverLocation`、`relatedDocumentNo`、`handoverPerson`、`remark`、`attachments`。
- 自动：`batchNo`、`enterpriseId`、`createdBy`、`createdAt`、`batchStatus`。

### 4.2 电池登记

- 自动：`systemTraceCode`、`currentResponsibleEnterpriseId`、`lifecycleStatus`。
- 必填：`batteryType=PACK`、`batteryChemistry`，其中电池体系允许 `UNKNOWN`。
- 选填：`originalCode`、`batteryModel`、`manufacturer`、`nominalCapacity`、`productionDate`。

### 4.3 验收登记

- 必填：`acceptanceResult`、`identityCheckResult`、`appearanceCheckResult`、`documentCheckResult`。
- `NEED_SUPPLEMENT` 必须填写 `acceptanceNote`。
- `REJECT` 必须填写 `acceptanceNote`。
- 系统只记录人工结论，不自动判断行业阈值。

### 4.4 入库办理

- 必填：`warehouseId`、`locationId`。
- 仓库和库位必须启用。
- 库位必须属于所选仓库。
- 电池必须为 `ACCEPTED_PENDING_INBOUND`。

## 5. 幂等要求

| 接口 | 幂等方式 |
| --- | --- |
| 创建批次 | 客户端传 `Idempotency-Key`，服务端按用户、接口和键去重。 |
| 创建电池 | `system_trace_code` 由服务端生成；重复原始编码进入核实，不用原始编码做幂等键。 |
| 提交批次 | 幂等键 + 批次状态条件，重复提交返回既有状态或 `DUPLICATE_SUBMISSION`。 |
| 保存验收 | 幂等键 + 电池状态条件，防止重复点击产生多条最终验收。 |
| 办理入库 | 幂等键 + `inbound_record.battery_id` 唯一约束 + 电池状态条件。 |

## 6. 删除保护接口约定

第一切片不提供删除已生效业务记录的成功接口。若前端或用户尝试删除批次、验收记录、入库记录、库存记录或生命周期事件，后端统一返回 `EFFECTIVE_RECORD_DELETE_FORBIDDEN`，并写入 `audit_log`。

## 7. OpenAPI 说明

`contracts/api/openapi-first-slice.yaml` 用于接口评审和 Swagger 展示。后续正式开发时可拆分为模块级 API 文件，但接口语义和错误码不得偏离本设计。

