# 第一切片数据字典 V1.0

文档名称：第一切片数据字典
系统名称：面向动力电池回收利用企业的流转协同与追溯管理系统
当前阶段：system-design
当前检查点：设计评审
文档状态：已确认
对应 SQL：`contracts/database/first-slice-schema-design.sql`

## 1. 通用说明

- 主键策略：所有 `id BIGINT PRIMARY KEY` 使用 MyBatis-Plus `ASSIGN_ID` 雪花算法生成。
- 企业数据范围：含 `enterprise_id` 的表必须在查询和更新时同时带当前登录企业条件。
- 乐观锁：含 `version` 的表由服务层在状态更新时校验和递增。
- 时间字段：`created_at`、`updated_at`、业务发生时间均由后端服务端生成。
- 操作人字段来自登录上下文。
- `updated_by` 目前不统一设置数据库外键，避免历史用户停用或跨模块更新导致约束阻塞；由应用层按登录用户维护。
- `business_attachment.object_type/object_id` 是应用层维护的多态关联，不设置数据库外键；临时上传阶段允许为空，绑定到批次或补充资料后必须填写。

## 2. 字段字典

| 表 | 字段 | 类型 | 空 | 默认值 | 约束 | 含义 | 来源 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `enterprise` | `id` | BIGINT | 否 | 无 | PK | 企业主键 | 系统 ASSIGN_ID |
| `enterprise` | `name` | VARCHAR(100) | 否 | 无 | UNIQUE | 企业名称 | 初始化或管理录入 |
| `enterprise` | `unified_social_credit_code` | VARCHAR(40) | 是 | NULL | 无 | 统一社会信用代码 | 管理录入 |
| `enterprise` | `enabled_status` | VARCHAR(20) | 否 | `ENABLED` | CHECK | 启用状态 | 管理维护 |
| `enterprise` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `enterprise` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `enterprise` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `sys_user` | `id` | BIGINT | 否 | 无 | PK | 用户主键 | 系统 ASSIGN_ID |
| `sys_user` | `enterprise_id` | BIGINT | 否 | 无 | FK | 所属企业 | 管理录入 |
| `sys_user` | `username` | VARCHAR(64) | 否 | 无 | UNIQUE | 登录名 | 管理录入 |
| `sys_user` | `password_hash` | VARCHAR(255) | 否 | 无 | 无 | BCrypt 密码摘要 | 系统生成 |
| `sys_user` | `display_name` | VARCHAR(64) | 否 | 无 | 无 | 显示名称 | 管理录入 |
| `sys_user` | `enabled_status` | VARCHAR(20) | 否 | `ENABLED` | CHECK | 启用状态 | 管理维护 |
| `sys_user` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `sys_user` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `sys_user` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `sys_role` | `id` | BIGINT | 否 | 无 | PK | 角色主键 | 系统 ASSIGN_ID |
| `sys_role` | `role_code` | VARCHAR(64) | 否 | 无 | UNIQUE | 角色编码 | 初始化或管理录入 |
| `sys_role` | `role_name` | VARCHAR(64) | 否 | 无 | 无 | 角色名称 | 初始化或管理录入 |
| `sys_role` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `sys_role` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `sys_role` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `sys_permission` | `id` | BIGINT | 否 | 无 | PK | 权限主键 | 系统 ASSIGN_ID |
| `sys_permission` | `permission_code` | VARCHAR(100) | 否 | 无 | UNIQUE | 权限编码 | 初始化 |
| `sys_permission` | `permission_name` | VARCHAR(100) | 否 | 无 | 无 | 权限名称 | 初始化 |
| `sys_permission` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `sys_permission` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `sys_permission` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `sys_user_role` | `id` | BIGINT | 否 | 无 | PK | 用户角色关系主键 | 系统 ASSIGN_ID |
| `sys_user_role` | `user_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 用户 | 管理维护 |
| `sys_user_role` | `role_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 角色 | 管理维护 |
| `sys_user_role` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `sys_role_permission` | `id` | BIGINT | 否 | 无 | PK | 角色权限关系主键 | 系统 ASSIGN_ID |
| `sys_role_permission` | `role_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 角色 | 管理维护 |
| `sys_role_permission` | `permission_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 权限 | 管理维护 |
| `sys_role_permission` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `recycle_batch` | `id` | BIGINT | 否 | 无 | PK | 批次主键 | 系统 ASSIGN_ID |
| `recycle_batch` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `recycle_batch` | `batch_no` | VARCHAR(40) | 否 | 无 | UNIQUE | 批次编号 | 系统生成 |
| `recycle_batch` | `source_type` | VARCHAR(30) | 否 | 无 | 无 | 来源类型 | 用户录入 |
| `recycle_batch` | `source_subject_name` | VARCHAR(100) | 否 | 无 | 无 | 来源主体名称 | 用户录入 |
| `recycle_batch` | `handover_date` | DATE | 否 | 无 | 无 | 交接日期 | 用户录入 |
| `recycle_batch` | `handover_location` | VARCHAR(200) | 是 | NULL | 无 | 交接地点 | 用户录入 |
| `recycle_batch` | `related_document_no` | VARCHAR(80) | 是 | NULL | 无 | 关联单据号 | 用户录入 |
| `recycle_batch` | `handover_person` | VARCHAR(64) | 是 | NULL | 无 | 交接人员 | 用户录入 |
| `recycle_batch` | `remark` | VARCHAR(500) | 是 | NULL | 无 | 备注 | 用户录入 |
| `recycle_batch` | `batch_status` | VARCHAR(40) | 否 | `DRAFT` | CHECK | 批次状态 | 系统维护 |
| `recycle_batch` | `submitted_at` | DATETIME(3) | 是 | NULL | 无 | 提交验收时间 | 系统生成 |
| `recycle_batch` | `created_by` | BIGINT | 否 | 无 | FK | 创建人 | 登录上下文 |
| `recycle_batch` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `recycle_batch` | `updated_by` | BIGINT | 是 | NULL | 应用层用户引用 | 修改人 | 登录上下文 |
| `recycle_batch` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `recycle_batch` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `battery` | `id` | BIGINT | 否 | 无 | PK | 电池档案主键 | 系统 ASSIGN_ID |
| `battery` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `battery` | `system_trace_code` | VARCHAR(64) | 否 | 无 | UNIQUE | 系统追溯编码 | 系统生成 |
| `battery` | `original_code` | VARCHAR(100) | 是 | NULL | 普通索引 | 原始编码，可重复 | 用户录入 |
| `battery` | `battery_type` | VARCHAR(20) | 否 | `PACK` | CHECK | 电池类型 | 固定值 |
| `battery` | `battery_model` | VARCHAR(100) | 是 | NULL | 无 | 电池型号 | 用户录入 |
| `battery` | `manufacturer` | VARCHAR(100) | 是 | NULL | 无 | 生产企业 | 用户录入 |
| `battery` | `battery_chemistry` | VARCHAR(40) | 否 | 无 | 无 | 电池体系 | 用户录入 |
| `battery` | `nominal_capacity` | DECIMAL(10,2) | 是 | NULL | 无 | 标称容量 | 用户录入 |
| `battery` | `production_date` | DATE | 是 | NULL | 无 | 生产日期 | 用户录入 |
| `battery` | `current_responsible_enterprise_id` | BIGINT | 否 | 无 | FK | 当前责任企业 | 系统维护 |
| `battery` | `lifecycle_status` | VARCHAR(40) | 否 | `REGISTERED` | CHECK | 生命周期状态 | 系统维护 |
| `battery` | `duplicate_status` | VARCHAR(40) | 否 | `NORMAL` | CHECK | 重复核实状态 | 系统维护 |
| `battery` | `created_by` | BIGINT | 否 | 无 | FK | 创建人 | 登录上下文 |
| `battery` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `battery` | `updated_by` | BIGINT | 是 | NULL | 应用层用户引用 | 修改人 | 登录上下文 |
| `battery` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `battery` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `battery_registration_candidate` | `id` | BIGINT | 否 | 无 | PK | 候选登记主键 | 系统 ASSIGN_ID |
| `battery_registration_candidate` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `battery_registration_candidate` | `original_code` | VARCHAR(100) | 否 | 无 | 索引 | 疑似重复原始编码 | 用户录入 |
| `battery_registration_candidate` | `battery_type` | VARCHAR(20) | 否 | `PACK` | CHECK | 电池类型 | 固定值 |
| `battery_registration_candidate` | `battery_model` | VARCHAR(100) | 是 | NULL | 无 | 电池型号 | 用户录入 |
| `battery_registration_candidate` | `manufacturer` | VARCHAR(100) | 是 | NULL | 无 | 生产企业 | 用户录入 |
| `battery_registration_candidate` | `battery_chemistry` | VARCHAR(40) | 否 | 无 | 无 | 电池体系 | 用户录入 |
| `battery_registration_candidate` | `nominal_capacity` | DECIMAL(10,2) | 是 | NULL | 无 | 标称容量 | 用户录入 |
| `battery_registration_candidate` | `production_date` | DATE | 是 | NULL | 无 | 生产日期 | 用户录入 |
| `battery_registration_candidate` | `candidate_status` | VARCHAR(40) | 否 | `PENDING_REVIEW` | CHECK | 候选状态 | 系统维护 |
| `battery_registration_candidate` | `submitted_by` | BIGINT | 否 | 无 | FK | 提交人 | 登录上下文 |
| `battery_registration_candidate` | `submitted_at` | DATETIME(3) | 否 | 无 | 无 | 提交时间 | 系统生成 |
| `battery_registration_candidate` | `closed_at` | DATETIME(3) | 是 | NULL | 无 | 关闭时间 | 系统生成 |
| `battery_registration_candidate` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `battery_registration_candidate` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `battery_registration_candidate` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `recycle_batch_battery` | `id` | BIGINT | 否 | 无 | PK | 关系主键 | 系统 ASSIGN_ID |
| `recycle_batch_battery` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `recycle_batch_battery` | `batch_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 批次 | 用户操作 |
| `recycle_batch_battery` | `battery_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 电池 | 用户操作 |
| `recycle_batch_battery` | `relation_status` | VARCHAR(20) | 否 | `ACTIVE` | CHECK | 关系状态 | 系统维护 |
| `recycle_batch_battery` | `created_by` | BIGINT | 否 | 无 | FK | 创建人 | 登录上下文 |
| `recycle_batch_battery` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `duplicate_code_review` | `id` | BIGINT | 否 | 无 | PK | 核实记录主键 | 系统 ASSIGN_ID |
| `duplicate_code_review` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `duplicate_code_review` | `candidate_id` | BIGINT | 否 | 无 | FK | 候选登记 | 系统维护 |
| `duplicate_code_review` | `original_code` | VARCHAR(100) | 否 | 无 | 索引 | 重复原始编码 | 候选带入 |
| `duplicate_code_review` | `existing_battery_id` | BIGINT | 是 | NULL | FK | 同一电池时原档案 | 人工核实 |
| `duplicate_code_review` | `created_battery_id` | BIGINT | 是 | NULL | FK | 不同电池时新档案 | 系统生成 |
| `duplicate_code_review` | `review_result` | VARCHAR(40) | 否 | 无 | CHECK | 核实结果 | 人工核实 |
| `duplicate_code_review` | `duplicate_reason` | VARCHAR(255) | 是 | NULL | 条件必填 | 重复原因 | 人工核实 |
| `duplicate_code_review` | `reviewer_id` | BIGINT | 否 | 无 | FK | 处理人 | 登录上下文 |
| `duplicate_code_review` | `reviewed_at` | DATETIME(3) | 否 | 无 | 无 | 处理时间 | 系统生成 |
| `duplicate_code_review` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `duplicate_code_review` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `acceptance_record` | `id` | BIGINT | 否 | 无 | PK | 验收记录主键 | 系统 ASSIGN_ID |
| `acceptance_record` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `acceptance_record` | `battery_id` | BIGINT | 否 | 无 | FK | 电池 | 系统维护 |
| `acceptance_record` | `batch_id` | BIGINT | 否 | 无 | FK | 批次 | 系统维护 |
| `acceptance_record` | `acceptance_result` | VARCHAR(30) | 否 | 无 | CHECK | 验收结果 | 用户录入 |
| `acceptance_record` | `identity_check_result` | VARCHAR(40) | 否 | 无 | 无 | 编码及身份核对情况 | 用户录入 |
| `acceptance_record` | `appearance_check_result` | VARCHAR(40) | 否 | 无 | 无 | 外观情况 | 用户录入 |
| `acceptance_record` | `document_check_result` | VARCHAR(40) | 否 | 无 | 无 | 资料完整情况 | 用户录入 |
| `acceptance_record` | `acceptance_note` | VARCHAR(500) | 是 | NULL | 条件必填 | 验收说明 | 用户录入 |
| `acceptance_record` | `accepted_by` | BIGINT | 否 | 无 | FK | 验收人员 | 登录上下文 |
| `acceptance_record` | `accepted_at` | DATETIME(3) | 否 | 无 | 无 | 验收时间 | 系统生成 |
| `acceptance_record` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `acceptance_record` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `acceptance_supplement` | `id` | BIGINT | 否 | 无 | PK | 补充资料主键 | 系统 ASSIGN_ID |
| `acceptance_supplement` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `acceptance_supplement` | `battery_id` | BIGINT | 否 | 无 | FK | 电池 | 系统维护 |
| `acceptance_supplement` | `acceptance_record_id` | BIGINT | 是 | NULL | FK | 关联验收记录 | 系统维护 |
| `acceptance_supplement` | `supplement_note` | VARCHAR(500) | 否 | 无 | 无 | 补充说明 | 用户录入 |
| `acceptance_supplement` | `supplemented_by` | BIGINT | 否 | 无 | FK | 补充人 | 登录上下文 |
| `acceptance_supplement` | `supplemented_at` | DATETIME(3) | 否 | 无 | 无 | 补充时间 | 系统生成 |
| `acceptance_supplement` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `acceptance_supplement` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `warehouse` | `id` | BIGINT | 否 | 无 | PK | 仓库主键 | 系统 ASSIGN_ID |
| `warehouse` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 管理维护 |
| `warehouse` | `warehouse_code` | VARCHAR(20) | 否 | 无 | UNIQUE, CHECK | 仓库编码 | 管理维护 |
| `warehouse` | `warehouse_name` | VARCHAR(100) | 否 | 无 | 无 | 仓库名称 | 管理维护 |
| `warehouse` | `enabled_status` | VARCHAR(20) | 否 | `ENABLED` | CHECK | 启用状态 | 管理维护 |
| `warehouse` | `created_by` | BIGINT | 否 | 无 | FK | 创建人 | 登录上下文 |
| `warehouse` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `warehouse` | `updated_by` | BIGINT | 是 | NULL | 应用层用户引用 | 修改人 | 登录上下文 |
| `warehouse` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `warehouse` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `warehouse_location` | `id` | BIGINT | 否 | 无 | PK | 库位主键 | 系统 ASSIGN_ID |
| `warehouse_location` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 管理维护 |
| `warehouse_location` | `warehouse_id` | BIGINT | 否 | 无 | FK | 所属仓库 | 管理维护 |
| `warehouse_location` | `location_code` | VARCHAR(40) | 否 | 无 | UNIQUE, CHECK | 库位编码 | 管理维护 |
| `warehouse_location` | `enabled_status` | VARCHAR(20) | 否 | `ENABLED` | CHECK | 启用状态 | 管理维护 |
| `warehouse_location` | `created_by` | BIGINT | 否 | 无 | FK | 创建人 | 登录上下文 |
| `warehouse_location` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `warehouse_location` | `updated_by` | BIGINT | 是 | NULL | 应用层用户引用 | 修改人 | 登录上下文 |
| `warehouse_location` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `warehouse_location` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `inbound_record` | `id` | BIGINT | 否 | 无 | PK | 入库记录主键 | 系统 ASSIGN_ID |
| `inbound_record` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `inbound_record` | `inbound_no` | VARCHAR(40) | 否 | 无 | UNIQUE | 入库单号 | 系统生成 |
| `inbound_record` | `battery_id` | BIGINT | 否 | 无 | FK | 电池 | 系统维护 |
| `inbound_record` | `warehouse_id` | BIGINT | 否 | 无 | FK | 仓库 | 用户选择 |
| `inbound_record` | `location_id` | BIGINT | 否 | 无 | FK | 库位 | 用户选择 |
| `inbound_record` | `inbound_status` | VARCHAR(20) | 否 | `EFFECTIVE` | CHECK | 入库记录状态 | 系统维护 |
| `inbound_record` | `inbound_by` | BIGINT | 否 | 无 | FK | 入库人员 | 登录上下文 |
| `inbound_record` | `inbound_at` | DATETIME(3) | 否 | 无 | 无 | 入库时间 | 系统生成 |
| `inbound_record` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `inbound_record` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `inventory` | `id` | BIGINT | 否 | 无 | PK | 库存主键 | 系统 ASSIGN_ID |
| `inventory` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `inventory` | `battery_id` | BIGINT | 否 | 无 | FK | 电池 | 系统维护 |
| `inventory` | `warehouse_id` | BIGINT | 否 | 无 | FK | 当前仓库 | 入库生成 |
| `inventory` | `location_id` | BIGINT | 否 | 无 | FK | 当前库位 | 入库生成 |
| `inventory` | `inbound_record_id` | BIGINT | 否 | 无 | FK | 来源入库记录 | 入库生成 |
| `inventory` | `is_current` | TINYINT | 否 | 1 | CHECK | 是否当前库存 | 系统维护 |
| `inventory` | `current_battery_id` | BIGINT | 是 | 生成列 | UNIQUE | 当前库存唯一约束列 | 数据库生成 |
| `inventory` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `inventory` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `inventory` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `lifecycle_event` | `id` | BIGINT | 否 | 无 | PK | 事件主键 | 系统 ASSIGN_ID |
| `lifecycle_event` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `lifecycle_event` | `battery_id` | BIGINT | 否 | 无 | FK | 电池 | 系统维护 |
| `lifecycle_event` | `batch_id` | BIGINT | 是 | NULL | FK | 关联批次 | 系统维护 |
| `lifecycle_event` | `event_type` | VARCHAR(50) | 否 | 无 | 无 | 事件类型 | 系统生成 |
| `lifecycle_event` | `event_name` | VARCHAR(100) | 否 | 无 | 无 | 事件名称 | 系统生成 |
| `lifecycle_event` | `from_status` | VARCHAR(40) | 是 | NULL | 无 | 原状态 | 系统生成 |
| `lifecycle_event` | `to_status` | VARCHAR(40) | 是 | NULL | 无 | 新状态 | 系统生成 |
| `lifecycle_event` | `event_result` | VARCHAR(20) | 否 | `SUCCESS` | CHECK | 事件结果 | 系统生成 |
| `lifecycle_event` | `operator_user_id` | BIGINT | 否 | 无 | FK | 操作人 | 登录上下文 |
| `lifecycle_event` | `occurred_at` | DATETIME(3) | 否 | 无 | 无 | 发生时间 | 系统生成 |
| `lifecycle_event` | `remark` | VARCHAR(500) | 是 | NULL | 无 | 备注 | 系统生成 |
| `lifecycle_event` | `trace_id` | VARCHAR(64) | 是 | NULL | 无 | 请求追踪号 | 系统生成 |
| `audit_log` | `id` | BIGINT | 否 | 无 | PK | 审计日志主键 | 系统 ASSIGN_ID |
| `audit_log` | `enterprise_id` | BIGINT | 是 | NULL | FK | 企业 | 登录上下文 |
| `audit_log` | `operator_user_id` | BIGINT | 是 | NULL | FK | 操作人 | 登录上下文 |
| `audit_log` | `action_code` | VARCHAR(80) | 否 | 无 | 无 | 审计动作编码 | 系统生成 |
| `audit_log` | `object_type` | VARCHAR(40) | 是 | NULL | 无 | 对象类型 | 系统生成 |
| `audit_log` | `object_id` | BIGINT | 是 | NULL | 无 | 对象 ID | 系统生成 |
| `audit_log` | `result` | VARCHAR(20) | 否 | 无 | CHECK | 操作结果 | 系统生成 |
| `audit_log` | `reject_reason` | VARCHAR(255) | 是 | NULL | 无 | 拒绝或失败原因 | 系统生成 |
| `audit_log` | `operated_at` | DATETIME(3) | 否 | 无 | 无 | 操作时间 | 系统生成 |
| `audit_log` | `ip_address` | VARCHAR(64) | 是 | NULL | 无 | IP 地址 | 请求上下文 |
| `audit_log` | `user_agent` | VARCHAR(255) | 是 | NULL | 无 | 客户端信息 | 请求上下文 |
| `audit_log` | `trace_id` | VARCHAR(64) | 是 | NULL | 无 | 请求追踪号 | 系统生成 |
| `idempotency_record` | `id` | BIGINT | 否 | 无 | PK | 幂等记录主键 | 系统 ASSIGN_ID |
| `idempotency_record` | `enterprise_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 企业 | 登录上下文 |
| `idempotency_record` | `operator_user_id` | BIGINT | 否 | 无 | FK, UNIQUE 组合 | 操作人 | 登录上下文 |
| `idempotency_record` | `operation_code` | VARCHAR(80) | 否 | 无 | UNIQUE 组合 | 操作编码 | 接口定义 |
| `idempotency_record` | `idempotency_key` | VARCHAR(80) | 否 | 无 | UNIQUE 组合 | 幂等键 | 请求头 |
| `idempotency_record` | `request_hash` | VARCHAR(128) | 否 | 无 | 无 | 请求体摘要 | 系统生成 |
| `idempotency_record` | `process_status` | VARCHAR(20) | 否 | 无 | CHECK | 处理状态 | 系统维护 |
| `idempotency_record` | `response_code` | VARCHAR(80) | 是 | NULL | 无 | 首次响应码 | 系统维护 |
| `idempotency_record` | `response_body` | JSON | 是 | NULL | 无 | 首次响应体 | 系统维护 |
| `idempotency_record` | `created_at` | DATETIME(3) | 否 | 无 | 无 | 创建时间 | 系统生成 |
| `idempotency_record` | `updated_at` | DATETIME(3) | 否 | 无 | 无 | 修改时间 | 系统生成 |
| `idempotency_record` | `expires_at` | DATETIME(3) | 否 | 无 | 索引 | 过期时间 | 系统生成 |
| `idempotency_record` | `version` | INT | 否 | 0 | 乐观锁 | 版本号 | 系统维护 |
| `business_attachment` | `id` | BIGINT | 否 | 无 | PK | 附件主键 | 系统 ASSIGN_ID |
| `business_attachment` | `enterprise_id` | BIGINT | 否 | 无 | FK | 当前企业 | 登录上下文 |
| `business_attachment` | `object_type` | VARCHAR(40) | 是 | NULL | 多态关联 | 所属对象类型 | 绑定后填写 |
| `business_attachment` | `object_id` | BIGINT | 是 | NULL | 多态关联 | 所属对象 ID | 绑定后填写 |
| `business_attachment` | `binding_status` | VARCHAR(20) | 否 | 无 | CHECK | 绑定状态，`TEMP` 或 `BOUND` | 系统维护 |
| `business_attachment` | `file_name` | VARCHAR(255) | 否 | 无 | 无 | 原始文件名 | 上传文件 |
| `business_attachment` | `file_ext` | VARCHAR(20) | 否 | 无 | 无 | 文件扩展名 | 系统解析 |
| `business_attachment` | `file_size_bytes` | BIGINT | 否 | 无 | 无 | 文件大小 | 系统解析 |
| `business_attachment` | `storage_path` | VARCHAR(500) | 否 | 无 | 无 | 服务端存储路径 | 系统生成 |
| `business_attachment` | `uploaded_by` | BIGINT | 否 | 无 | FK | 上传人 | 登录上下文 |
| `business_attachment` | `uploaded_at` | DATETIME(3) | 否 | 无 | 无 | 上传时间 | 系统生成 |
| `business_attachment` | `expires_at` | DATETIME(3) | 是 | NULL | 索引 | 临时附件过期时间 | TEMP 状态必填 |

## 3. 权限编码字典

| 权限编码 | 说明 |
| --- | --- |
| `batch:create` | 创建或修改回收批次。 |
| `batch:read` | 查询回收批次。 |
| `batch:submit` | 提交批次验收。 |
| `battery:create` | 登记电池档案或候选登记。 |
| `battery:duplicate:resolve` | 处理重复原始编码核实。 |
| `acceptance:create` | 登记录入验收结果。 |
| `acceptance:supplement` | 补充验收资料。 |
| `inbound:create` | 办理入库。 |
| `inventory:read` | 查询库存。 |
| `trace:read` | 查看生命周期追溯。 |
| `audit:read` | 查看审计日志。 |
| `permission:manage` | 管理用户、角色和权限。 |
| `warehouse:read` | 查询启用仓库和库位。 |
| `attachment:upload` | 上传业务附件。 |
| `attachment:read` | 下载业务附件。 |

## 4. 事件和审计字典

| 类型 | 编码 | 说明 |
| --- | --- | --- |
| 生命周期事件 | `BATTERY_REGISTERED` | 电池完成有效档案登记。 |
| 生命周期事件 | `DUPLICATE_RESOLVED_SAME` | 核实为同一电池并使用原档案。 |
| 生命周期事件 | `DUPLICATE_RESOLVED_DIFFERENT` | 核实为不同电池并创建新档案。 |
| 生命周期事件 | `BATCH_SUBMITTED` | 批次提交验收。 |
| 生命周期事件 | `ACCEPTANCE_PASSED` | 验收通过。 |
| 生命周期事件 | `ACCEPTANCE_NEED_SUPPLEMENT` | 待补充资料。 |
| 生命周期事件 | `ACCEPTANCE_SUPPLEMENTED` | 补充资料后重新提交。 |
| 生命周期事件 | `ACCEPTANCE_REJECTED` | 验收不通过。 |
| 生命周期事件 | `INBOUND_COMPLETED` | 入库完成。 |
| 审计动作 | `AUTH_LOGIN_SUCCESS` | 登录成功。 |
| 审计动作 | `AUTH_LOGIN_FAILED` | 登录失败。 |
| 审计动作 | `FORBIDDEN_OPERATION` | 越权操作。 |
| 审计动作 | `INBOUND_FAILED` | 入库失败。 |
| 审计动作 | `DELETE_EFFECTIVE_RECORD_DENIED` | 删除已生效记录被拒绝。 |
| 审计动作 | `BATTERY_CANDIDATE_CREATED` | 疑似重复候选创建，候选阶段无 `battery_id`，只写审计。 |
| 审计动作 | `ATTACHMENT_BOUND` | 临时附件绑定到业务对象。 |
| 审计动作 | `PERMISSION_CHANGED` | 权限配置变更。 |
| 审计动作 | `IDEMPOTENCY_KEY_REUSED` | 幂等键被不同请求体复用。 |
| 审计动作 | `CROSS_ENTERPRISE_ACCESS_DENIED` | 跨企业访问被拒绝。 |
