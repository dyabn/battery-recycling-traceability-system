# 第一切片数据字典 V0.1

文档名称：第一切片数据字典  
系统名称：面向动力电池回收利用企业的流转协同与追溯管理系统  
当前阶段：system-design  
当前检查点：设计评审  
文档状态：待评审  

## 1. 公共字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键。 |
| `enterprise_id` | BIGINT | 企业数据范围，引用 `enterprise.id`。 |
| `created_by` | BIGINT | 创建人，引用 `sys_user.id`。 |
| `created_at` | DATETIME(3) | 创建时间。 |
| `updated_by` | BIGINT | 修改人。 |
| `updated_at` | DATETIME(3) | 修改时间。 |
| `version` | INT | 乐观锁版本。 |

## 2. 主要业务字段

| 表 | 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `enterprise` | `name` | VARCHAR(100) | NOT NULL | 企业名称。 |
| `enterprise` | `enabled_status` | VARCHAR(20) | NOT NULL | `ENABLED`、`DISABLED`。 |
| `sys_user` | `username` | VARCHAR(64) | UNIQUE, NOT NULL | 登录名。 |
| `sys_user` | `password_hash` | VARCHAR(255) | NOT NULL | BCrypt 密码摘要。 |
| `sys_user` | `display_name` | VARCHAR(64) | NOT NULL | 显示名称。 |
| `sys_role` | `role_code` | VARCHAR(64) | UNIQUE, NOT NULL | 角色编码。 |
| `sys_permission` | `permission_code` | VARCHAR(100) | UNIQUE, NOT NULL | 权限编码。 |
| `recycle_batch` | `batch_no` | VARCHAR(40) | UNIQUE, NOT NULL | 系统生成批次编号。 |
| `recycle_batch` | `source_type` | VARCHAR(30) | NOT NULL | 来源类型。 |
| `recycle_batch` | `source_subject_name` | VARCHAR(100) | NOT NULL | 来源主体名称。 |
| `recycle_batch` | `handover_date` | DATE | NOT NULL | 交接日期。 |
| `recycle_batch` | `batch_status` | VARCHAR(40) | NOT NULL | 批次状态。 |
| `battery` | `system_trace_code` | VARCHAR(64) | UNIQUE, NOT NULL | 系统追溯编码，全局唯一。 |
| `battery` | `original_code` | VARCHAR(100) | NULL | 原始编码，可为空、可重复。 |
| `battery` | `battery_type` | VARCHAR(20) | NOT NULL | 固定为 `PACK`。 |
| `battery` | `battery_chemistry` | VARCHAR(40) | NOT NULL | 电池体系，允许 `UNKNOWN`。 |
| `battery` | `lifecycle_status` | VARCHAR(40) | NOT NULL | 电池生命周期状态。 |
| `battery` | `duplicate_status` | VARCHAR(40) | NOT NULL | 原始编码重复核实状态。 |
| `duplicate_code_review` | `review_result` | VARCHAR(40) | NOT NULL | `SAME_BATTERY`、`DIFFERENT_BATTERY`。 |
| `duplicate_code_review` | `duplicate_reason` | VARCHAR(255) | NULL | 不同电池时必填。 |
| `acceptance_record` | `acceptance_result` | VARCHAR(30) | NOT NULL | `PASS`、`NEED_SUPPLEMENT`、`REJECT`。 |
| `acceptance_record` | `identity_check_result` | VARCHAR(40) | NOT NULL | 编码及身份核对情况。 |
| `acceptance_record` | `appearance_check_result` | VARCHAR(40) | NOT NULL | 外观情况。 |
| `acceptance_record` | `document_check_result` | VARCHAR(40) | NOT NULL | 资料完整情况。 |
| `acceptance_record` | `acceptance_note` | VARCHAR(500) | NULL | 验收说明。 |
| `acceptance_supplement` | `supplement_note` | VARCHAR(500) | NOT NULL | 补充资料说明。 |
| `warehouse` | `warehouse_code` | VARCHAR(20) | UNIQUE, NOT NULL | 格式 `WH-{3位数字}`。 |
| `warehouse_location` | `location_code` | VARCHAR(40) | UNIQUE, NOT NULL | 格式 `{仓库编码}-A{2位数字}-R{2位数字}-L{2位数字}`。 |
| `inbound_record` | `inbound_no` | VARCHAR(40) | UNIQUE, NOT NULL | 入库单号。 |
| `inventory` | `is_current` | TINYINT | NOT NULL | 当前有效库存标记。 |
| `lifecycle_event` | `event_type` | VARCHAR(50) | NOT NULL | 生命周期事件类型。 |
| `audit_log` | `action_code` | VARCHAR(80) | NOT NULL | 审计动作编码。 |
| `business_attachment` | `object_type` | VARCHAR(40) | NOT NULL | 附件所属对象类型。 |

## 3. 权限编码字典

| 权限编码 | 说明 |
| --- | --- |
| `batch:create` | 创建回收批次。 |
| `batch:submit` | 提交批次验收。 |
| `battery:create` | 登记电池档案。 |
| `battery:duplicate:resolve` | 处理重复原始编码核实。 |
| `acceptance:create` | 登记录入验收结果。 |
| `acceptance:supplement` | 补充验收资料。 |
| `inbound:create` | 办理入库。 |
| `inventory:read` | 查询库存。 |
| `trace:read` | 查看追溯。 |
| `audit:read` | 查看审计日志。 |
| `permission:manage` | 管理角色和权限。 |

## 4. 事件类型字典

| 事件类型 | 说明 |
| --- | --- |
| `BATTERY_REGISTERED` | 电池完成登记。 |
| `BATCH_SUBMITTED` | 批次提交验收。 |
| `ACCEPTANCE_PASSED` | 验收通过。 |
| `ACCEPTANCE_NEED_SUPPLEMENT` | 待补充资料。 |
| `ACCEPTANCE_SUPPLEMENTED` | 补充资料后重新提交。 |
| `ACCEPTANCE_REJECTED` | 验收不通过。 |
| `INBOUND_COMPLETED` | 入库完成。 |

## 5. 审计动作字典

| 动作编码 | 说明 |
| --- | --- |
| `AUTH_LOGIN_SUCCESS` | 登录成功。 |
| `AUTH_LOGIN_FAILED` | 登录失败。 |
| `FORBIDDEN_OPERATION` | 越权操作。 |
| `INBOUND_FAILED` | 入库失败。 |
| `DELETE_EFFECTIVE_RECORD_DENIED` | 删除已生效记录被拒绝。 |
| `PERMISSION_CHANGED` | 权限配置变更。 |

