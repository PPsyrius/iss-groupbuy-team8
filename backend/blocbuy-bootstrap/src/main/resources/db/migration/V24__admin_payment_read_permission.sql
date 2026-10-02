-- Additive RBAC capability for the read-only payment attention query.
INSERT INTO sys_permission (perm_code, perm_name, description)
VALUES ('payment:read', 'Read payment attention', 'Read outstanding participation compensation payments');

INSERT INTO rel_role_permission (role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role CROSS JOIN sys_permission permission
WHERE role.role_code = 'admin' AND NOT role.deleted
    AND permission.perm_code = 'payment:read' AND NOT permission.deleted;

-- Application obligation: require both the admin role and this capability, plus an active account.
