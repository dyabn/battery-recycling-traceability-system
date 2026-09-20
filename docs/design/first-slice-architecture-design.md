# 第一切片架构设计 V0.1

文档名称：第一切片架构设计  
系统名称：面向动力电池回收利用企业的流转协同与追溯管理系统  
当前阶段：system-design  
当前检查点：设计评审  
文档状态：待评审  
架构风格：前后端分离的模块化单体应用

## 1. 架构目标

第一切片架构需要支持已确认的回收接收、登记、验收、入库和库存可见闭环，同时保持课程项目可实现、可测试、可部署。架构不采用微服务，避免增加部署、网络调用和分布式事务复杂度。

## 2. 逻辑架构

```text
Browser
  |
  | HTTPS / JSON
  v
Vue 3 Frontend
  - Element Plus views
  - Pinia stores
  - Axios API client
  |
  | REST API / JWT
  v
Spring Boot Modular Monolith
  - auth
  - batch
  - battery
  - duplicate
  - idempotency
  - attachment
  - acceptance
  - inbound
  - inventory
  - trace
  - audit
  |
  | MyBatis-Plus mapper
  v
MySQL 8
  - business tables
  - lifecycle events
  - audit logs
  - attachment metadata
```

## 3. 分层结构

| 层 | 后续实现职责 |
| --- | --- |
| Controller | 接收请求、参数绑定、调用应用服务、返回统一响应。 |
| Application Service | 编排用例、事务控制、幂等校验、跨模块协作。 |
| Domain Service | 状态转换、业务规则判断、重复编码核实、批次完成判定。 |
| Repository/Mapper | 持久化访问、条件更新、唯一约束冲突识别。 |
| Infrastructure | JWT、权限、审计、附件、配置、异常映射和 traceId。 |

## 4. 前端架构

| 前端区域 | 页面 | 调用接口 |
| --- | --- | --- |
| 登录与工作台 | UI-C4-001、UI-C4-002 | `/auth/login`、`/auth/current-user` |
| 回收批次 | UI-C4-003..005 | `/recycle-batches` |
| 电池登记 | UI-C4-006 | `/batteries`、`/batteries/duplicate-check`、`/battery-registration-candidates/{id}/duplicate-resolution` |
| 验收处理 | UI-C4-007..009 | `/acceptances/pending`、`/acceptances`、`/acceptance-supplements` |
| 入库与库存 | UI-C4-010..012 | `/inbounds/pending`、`/inbounds`、`/inventory` |
| 追溯与异常 | UI-C4-013..014 | `/batteries/{id}/trace`、统一错误响应 |
| 系统管理 | 权限和审计入口 | `/roles`、`/permissions`、`/audit-logs` |

前端只做用户体验层校验。后端返回的错误码和消息是最终业务结果。

## 5. 后端模块

| 模块 | 包名建议 | 核心服务 | 数据表 |
| --- | --- | --- | --- |
| 身份与权限 | `auth` | `AuthService`、`PermissionService` | `sys_user`、`sys_role`、`sys_permission` |
| 回收批次 | `batch` | `RecycleBatchService` | `recycle_batch`、`recycle_batch_battery` |
| 电池档案 | `battery` | `BatteryService`、`TraceCodeService` | `battery` |
| 重复编码核实 | `duplicate` | `DuplicateCodeReviewService`、`BatteryRegistrationCandidateService` | `battery_registration_candidate`、`duplicate_code_review` |
| 验收 | `acceptance` | `AcceptanceService`、`SupplementService` | `acceptance_record`、`acceptance_supplement` |
| 入库 | `inbound` | `InboundService` | `warehouse`、`warehouse_location`、`inbound_record`、`inventory` |
| 库存查询 | `inventory` | `InventoryQueryService` | `inventory`、`battery` |
| 生命周期追溯 | `trace` | `LifecycleEventService` | `lifecycle_event` |
| 审计日志 | `audit` | `AuditLogService` | `audit_log` |
| 附件 | `attachment` | `AttachmentService` | `business_attachment` |
| 幂等 | `idempotency` | `IdempotencyService` | `idempotency_record` |

## 6. 模块依赖规则

- `auth` 可被所有业务模块依赖。
- `trace` 和 `audit` 为基础能力，可被业务模块调用。
- `idempotency` 为写接口基础能力，由应用服务在业务事务前后调用。
- `attachment` 只保存附件元数据和文件存储路径，不直接参与业务状态裁决。
- `batch` 可依赖 `battery` 和 `duplicate` 做提交校验。
- `acceptance` 可依赖 `batch` 查询批次状态并回写批次完成状态。
- `inbound` 可依赖 `battery`、`inventory` 和仓库基础资料。
- `inventory` 只提供查询，不反向修改入库记录。
- 禁止跨模块直接修改对方数据表，应通过应用服务或领域服务完成。

## 7. 状态机设计

### 7.1 电池状态

```text
已登记
  -> 待验收
  -> 已验收待入库
  -> 在库

待验收
  -> 待补充资料
  -> 待验收

待验收
  -> 验收不通过
```

非法转换必须返回 `INVALID_BATTERY_STATE`，并保持状态不变。

### 7.2 批次状态

```text
草稿 -> 待验收 -> 验收处理中 -> 已完成
```

批次提交成功后进入待验收。任一电池开始验收后批次进入验收处理中。所有电池均取得通过或不通过的最终结果后，批次进入已完成。存在待验收或待补充资料电池时保持验收处理中。

## 8. 事务和一致性

| 用例 | 事务边界 | 并发控制 |
| --- | --- | --- |
| 提交批次 | 校验批次、更新批次状态、批量更新电池状态、写事件和审计。 | 批次 `version` 乐观锁、状态条件更新、幂等键。 |
| 保存验收 | 写验收记录、更新电池状态、更新批次状态、写事件和审计。 | 电池 `version` 乐观锁、状态条件更新和幂等键；不依赖 `acceptance_record.battery_id` 永久唯一约束。 |
| 补充资料 | 写补充记录、恢复电池待验收、写事件和审计。 | 电池状态条件更新、补充记录幂等键。 |
| 办理入库 | 写入库记录、写库存、更新电池状态、写事件和审计。 | 电池状态条件更新、幂等键和当前库存唯一约束；不依赖 `inbound_record.battery_id` 永久唯一约束。 |

## 9. 幂等和重复提交

业务写接口必须携带 `Idempotency-Key` 请求头。相同用户、相同接口、相同业务对象和相同幂等键的重复请求，应返回首次成功结果或明确的重复提交错误，不得产生重复业务记录。

幂等记录保存到 `idempotency_record`。相同键和相同请求体返回首次结果；相同键但请求体不同返回 `IDEMPOTENCY_KEY_REUSED`。成功时业务数据与 `SUCCEEDED` 在同一事务提交；业务失败时主事务回滚，再用独立事务记录 `FAILED`；系统异常或超时留下 `PROCESSING` 时，由过期策略允许重试。缺少幂等键的业务写接口返回 400。

## 10. 错误响应

所有 API 使用统一响应结构：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "traceId": "..."
}
```

失败响应同样使用该结构，HTTP 状态码与业务错误码共同表达失败类型。权限失败返回 403，业务校验失败返回 400，状态冲突或幂等冲突返回 409。

## 11. 日志和追踪

- 请求入口生成 `traceId`。
- 应用日志记录 `traceId`、用户 ID、企业 ID、接口、结果和耗时。
- 生命周期事件记录业务状态变化。
- 审计日志记录安全事件、越权操作、失败业务操作和删除保护。
- 日志不得输出密码、JWT、完整密钥或敏感配置。

## 12. 后续实现约束

本架构设计通过前，不创建正式工程骨架。本设计通过后，后续 `feature/first-slice-implementation` 才能初始化 Vue 和 Spring Boot 工程。
