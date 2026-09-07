-- identity.sql — identity domain DDL (schema: domain, PostgreSQL 18).
-- Source of truth for RBAC loaded by Sa-Token StpInterface (decision D7).
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); instants timestamptz (UTC).

-- Registered platform users (buyers and admins).
CREATE TABLE sys_user (
    id              uuid         NOT NULL DEFAULT uuidv7(),
    username        varchar(64)  NOT NULL,
    email           varchar(512),
    phone           varchar(32),
    password_hash   varchar(128) NOT NULL,
    nickname        varchar(64),
    avatar_url      varchar(1024),
    status          varchar(16)  NOT NULL,
    last_login_time timestamptz,
    create_time     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       uuid,
    update_time     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       uuid,
    deleted         boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_user PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user IS 'Platform user accounts; login identity and profile.';
COMMENT ON COLUMN sys_user.username IS 'Login name; unique among active rows.';
COMMENT ON COLUMN sys_user.email IS 'Login/contact email; unique among active rows when present.';
COMMENT ON COLUMN sys_user.password_hash IS 'Slow-hash (bcrypt/argon2) output; never returned to clients or logged.';
COMMENT ON COLUMN sys_user.status IS 'Account status (e.g. active, disabled); allowed values enforced by the application.';
COMMENT ON COLUMN sys_user.last_login_time IS 'Instant of last successful login; UTC.';

CREATE UNIQUE INDEX uq_sys_user_username
    ON sys_user (username)
    WHERE deleted = false;

CREATE UNIQUE INDEX uq_sys_user_email
    ON sys_user (email)
    WHERE deleted = false AND email IS NOT NULL;

-- Role definitions for RBAC.
CREATE TABLE sys_role (
    id          uuid         NOT NULL DEFAULT uuidv7(),
    role_code   varchar(64)  NOT NULL,
    role_name   varchar(64)  NOT NULL,
    description varchar(256),
    create_time timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   uuid,
    update_time timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   uuid,
    deleted     boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_role PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_role IS 'RBAC roles; codes loaded into Sa-Token via StpInterface.';
COMMENT ON COLUMN sys_role.role_code IS 'Stable machine code, e.g. admin, buyer; unique among active rows.';

CREATE UNIQUE INDEX uq_sys_role_role_code
    ON sys_role (role_code)
    WHERE deleted = false;

-- Permission point definitions for RBAC.
CREATE TABLE sys_permission (
    id          uuid         NOT NULL DEFAULT uuidv7(),
    perm_code   varchar(128) NOT NULL,
    perm_name   varchar(64)  NOT NULL,
    description varchar(256),
    create_time timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   uuid,
    update_time timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   uuid,
    deleted     boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_permission PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_permission IS 'RBAC permission points, e.g. group-deal:create.';
COMMENT ON COLUMN sys_permission.perm_code IS 'Stable machine code; unique among active rows.';

CREATE UNIQUE INDEX uq_sys_permission_perm_code
    ON sys_permission (perm_code)
    WHERE deleted = false;

-- User-to-role assignment; pure relationship table, reduced column set.
CREATE TABLE rel_user_role (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    user_id     uuid        NOT NULL,
    role_id     uuid        NOT NULL,
    create_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   uuid,
    deleted     boolean     NOT NULL DEFAULT false,
    CONSTRAINT pk_rel_user_role PRIMARY KEY (id)
);

COMMENT ON TABLE rel_user_role IS 'User-role assignments; physical rows kept, soft delete on revoke.';

CREATE UNIQUE INDEX uq_rel_user_role_user_role
    ON rel_user_role (user_id, role_id)
    WHERE deleted = false;

CREATE INDEX idx_rel_user_role_role_id
    ON rel_user_role (role_id)
    WHERE deleted = false;

-- Role-to-permission assignment; pure relationship table, reduced column set.
CREATE TABLE rel_role_permission (
    id            uuid        NOT NULL DEFAULT uuidv7(),
    role_id       uuid        NOT NULL,
    permission_id uuid        NOT NULL,
    create_time   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     uuid,
    deleted       boolean     NOT NULL DEFAULT false,
    CONSTRAINT pk_rel_role_permission PRIMARY KEY (id)
);

COMMENT ON TABLE rel_role_permission IS 'Role-permission grants; physical rows kept, soft delete on revoke.';

CREATE UNIQUE INDEX uq_rel_role_permission_role_permission
    ON rel_role_permission (role_id, permission_id)
    WHERE deleted = false;

CREATE INDEX idx_rel_role_permission_permission_id
    ON rel_role_permission (permission_id)
    WHERE deleted = false;

-- Application obligations:
-- * Password hashing, status value set, and role/permission value sets validated in application.
-- * rel_user_role / rel_role_permission: on insert verify user/role/permission exist, active,
--   and not deleted; revoke = soft delete. Parent-deletion race handled by locking the parent
--   row (SELECT ... FOR UPDATE) in the same transaction.
-- * username/email uniqueness re-checked by the unique partial indexes; map violation to 409.
-- * Scheduled orphan audit: rel rows whose user/role/permission is deleted or missing.
