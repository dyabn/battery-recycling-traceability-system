-- Dev-only demo data. This location is loaded only by the dev profile.
-- Demo password for all enabled accounts: password

INSERT INTO enterprise (id, name, unified_social_credit_code, enabled_status, created_at, updated_at, version)
VALUES
(1, '演示回收利用企业A', '91310000DEMO000001', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(2, '演示协作企业B', '91310000DEMO000002', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO sys_user (id, enterprise_id, username, password_hash, display_name, enabled_status, created_at, updated_at, version)
VALUES
(1, 1, 'admin', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '系统管理员', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(2, 1, 'supervisor', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '业务主管', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(3, 1, 'recycle_operator', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '回收操作员', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(4, 1, 'warehouse_admin', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '仓库管理员', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(5, 1, 'disabled_user', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '禁用用户', 'DISABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
(6, 2, 'enterprise_b_user', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', '企业B业务主管', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO sys_user_role (id, user_id, role_id, created_at)
VALUES
(10201, 1, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), CURRENT_TIMESTAMP(3)),
(10202, 2, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), CURRENT_TIMESTAMP(3)),
(10203, 3, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), CURRENT_TIMESTAMP(3)),
(10204, 4, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), CURRENT_TIMESTAMP(3)),
(10205, 5, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), CURRENT_TIMESTAMP(3)),
(10206, 6, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO dq_enterprise_rule_config (
  enterprise_id, rule_code, enabled_status, toggled_reason, toggled_by, toggled_at,
  created_at, updated_at, version
)
SELECT e.id, r.rule_code, 'ENABLED', NULL, NULL, NULL,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM enterprise e
CROSS JOIN dq_rule_definition r
WHERE e.id IN (1, 2)
  AND r.rule_code IN ('DQ-001','DQ-002','DQ-003','DQ-004','DQ-005','DQ-006','DQ-007')
  AND NOT EXISTS (
    SELECT 1
    FROM dq_enterprise_rule_config c
    WHERE c.enterprise_id = e.id
      AND c.rule_code = r.rule_code
  );
