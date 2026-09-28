-- I1 身份、权限和企业隔离基础权限初始化。
-- 只补充缺失的角色、权限和角色权限映射，不覆盖已经调整过的授权状态。

INSERT INTO sys_role (id, role_code, role_name, created_at, updated_at)
VALUES
(9101, 'SYSTEM_ADMIN', '系统管理员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9102, 'BUSINESS_SUPERVISOR', '业务主管', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9103, 'RECYCLE_OPERATOR', '回收操作员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9104, 'WAREHOUSE_ADMIN', '仓库管理员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO sys_permission (id, permission_code, permission_name, created_at, updated_at)
VALUES
(10001, 'authenticated', '已认证访问', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10002, 'batch:create', '创建回收批次', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10003, 'batch:read', '查看回收批次', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10004, 'batch:submit', '提交批次验收', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10005, 'battery:create', '登记电池档案', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10006, 'battery:duplicate:resolve', '处理重复编码核实', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10007, 'acceptance:create', '登记验收结果', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10008, 'acceptance:supplement', '补充验收资料', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10009, 'inbound:create', '办理入库', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10010, 'inventory:read', '查看库存', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10011, 'trace:read', '查看生命周期追溯', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10012, 'audit:read', '查看通用审计日志', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10013, 'permission:manage', '管理用户角色权限', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10014, 'warehouse:read', '查看仓库和库位', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10015, 'attachment:upload', '上传业务附件', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(10016, 'attachment:read', '查看业务附件', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
VALUES
(10101, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'authenticated'), CURRENT_TIMESTAMP(3)),
(10102, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'permission:manage'), CURRENT_TIMESTAMP(3)),
(10103, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'audit:read'), CURRENT_TIMESTAMP(3)),
(10111, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'authenticated'), CURRENT_TIMESTAMP(3)),
(10112, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'batch:read'), CURRENT_TIMESTAMP(3)),
(10113, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'battery:duplicate:resolve'), CURRENT_TIMESTAMP(3)),
(10114, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'inventory:read'), CURRENT_TIMESTAMP(3)),
(10115, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'trace:read'), CURRENT_TIMESTAMP(3)),
(10116, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'audit:read'), CURRENT_TIMESTAMP(3)),
(10117, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'warehouse:read'), CURRENT_TIMESTAMP(3)),
(10121, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'authenticated'), CURRENT_TIMESTAMP(3)),
(10122, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'batch:create'), CURRENT_TIMESTAMP(3)),
(10123, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'batch:read'), CURRENT_TIMESTAMP(3)),
(10124, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'batch:submit'), CURRENT_TIMESTAMP(3)),
(10125, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'battery:create'), CURRENT_TIMESTAMP(3)),
(10126, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'acceptance:supplement'), CURRENT_TIMESTAMP(3)),
(10127, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'attachment:upload'), CURRENT_TIMESTAMP(3)),
(10128, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'attachment:read'), CURRENT_TIMESTAMP(3)),
(10129, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'trace:read'), CURRENT_TIMESTAMP(3)),
(10131, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'authenticated'), CURRENT_TIMESTAMP(3)),
(10132, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'acceptance:create'), CURRENT_TIMESTAMP(3)),
(10133, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'acceptance:supplement'), CURRENT_TIMESTAMP(3)),
(10134, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'inbound:create'), CURRENT_TIMESTAMP(3)),
(10135, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'inventory:read'), CURRENT_TIMESTAMP(3)),
(10136, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'warehouse:read'), CURRENT_TIMESTAMP(3)),
(10137, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'attachment:upload'), CURRENT_TIMESTAMP(3)),
(10138, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'attachment:read'), CURRENT_TIMESTAMP(3)),
(10139, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'trace:read'), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  id = id;
