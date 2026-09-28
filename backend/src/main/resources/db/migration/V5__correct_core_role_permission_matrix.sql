-- I1 第二轮复核整改：纠正 V1.0 第一切片与 V1.1 数据治理角色权限矩阵。
-- 本迁移只调整内置角色的确认基线权限，不修改用户角色关系。

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE (r.role_code = 'BUSINESS_SUPERVISOR' AND p.permission_code IN ('battery:duplicate:resolve', 'audit:read', 'warehouse:read'))
   OR (r.role_code = 'WAREHOUSE_ADMIN' AND p.permission_code IN ('acceptance:create', 'acceptance:supplement'));

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
SELECT 12001, r.id, p.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'attachment:read'
WHERE r.role_code = 'BUSINESS_SUPERVISOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
SELECT 12002, r.id, p.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'battery:duplicate:resolve'
WHERE r.role_code = 'RECYCLE_OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
SELECT 12003, r.id, p.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'acceptance:create'
WHERE r.role_code = 'RECYCLE_OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
SELECT 12004, r.id, p.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'batch:read'
WHERE r.role_code = 'WAREHOUSE_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
