-- identity.sql — identity domain DDL (schema: domain, PostgreSQL 18).
-- Source of truth for RBAC loaded by Sa-Token StpInterface (decision D7).
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); instants timestamptz (UTC).

-- Registered platform users (buyers and admins).
-- Local account record; OAuth identities (Google first, others later) bind in sys_user_oauth.
-- Sa-Token never sees provider tokens: after the app verifies the OAuth identity it resolves a
-- local user id and opens the Sa-Token session with it.
CREATE TABLE sys_user (
    id              uuid         NOT NULL DEFAULT uuidv7(),
    username        varchar(64),
    email           varchar(512),
    phone           varchar(32),
    password_hash   varchar(128),
    nickname        varchar(64),
    avatar_url      varchar(1024),
    register_source varchar(16)  NOT NULL,
    status          varchar(16)  NOT NULL,
    last_login_time timestamptz,
    create_time     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       uuid,
    update_time     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       uuid,
    deleted         boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_user PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user IS 'Platform user accounts; login identity and profile. OAuth login is bound via sys_user_oauth.';
COMMENT ON COLUMN sys_user.username IS 'Login name; unique among active rows when present. Null for pure-OAuth users until they pick one.';
COMMENT ON COLUMN sys_user.email IS 'Login/contact email; unique among active rows when present. Primary identifier for OAuth account resolution.';
COMMENT ON COLUMN sys_user.password_hash IS 'Slow-hash (bcrypt/argon2) output; null for OAuth-only users. Never returned to clients or logged.';
COMMENT ON COLUMN sys_user.register_source IS 'Registration channel (e.g. local, google); allowed values enforced by the application.';
COMMENT ON COLUMN sys_user.status IS 'Account status (e.g. active, disabled); allowed values enforced by the application.';
COMMENT ON COLUMN sys_user.last_login_time IS 'Instant of last successful login (any channel); UTC.';

CREATE UNIQUE INDEX uq_sys_user_username
    ON sys_user (username)
    WHERE deleted = false;

CREATE UNIQUE INDEX uq_sys_user_email
    ON sys_user (email)
    WHERE deleted = false AND email IS NOT NULL;

-- External OAuth identity bindings; one row per (provider, provider user) per local account.
CREATE TABLE sys_user_oauth (
    id               uuid          NOT NULL DEFAULT uuidv7(),
    user_id          uuid          NOT NULL,
    provider         varchar(32)   NOT NULL,
    provider_user_id varchar(256)  NOT NULL,
    email            varchar(512),
    display_name     varchar(128),
    avatar_url       varchar(1024),
    raw_profile      jsonb,
    last_login_time  timestamptz,
    create_time      timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        uuid,
    update_time      timestamptz   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        uuid,
    deleted          boolean       NOT NULL DEFAULT false,
    CONSTRAINT pk_sys_user_oauth PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user_oauth IS 'OAuth identity bindings (Google first; one row per provider account linked to a local user).';
COMMENT ON COLUMN sys_user_oauth.user_id IS 'Owning local account; validity enforced by the application.';
COMMENT ON COLUMN sys_user_oauth.provider IS 'Provider code (e.g. google); allowed values enforced by the application. Provider-neutral on purpose for future providers.';
COMMENT ON COLUMN sys_user_oauth.provider_user_id IS 'Provider-side stable subject (Google `sub`, up to 255 ASCII chars); unique per provider among active rows.';
COMMENT ON COLUMN sys_user_oauth.email IS 'Email reported by the provider at last login; snapshot only, sys_user.email stays the canonical contact.';
COMMENT ON COLUMN sys_user_oauth.raw_profile IS 'Last verified provider profile payload for audit/repair; never returned to clients. PII — access restricted.';
COMMENT ON COLUMN sys_user_oauth.last_login_time IS 'Instant this identity last authenticated; UTC.';

CREATE UNIQUE INDEX uq_sys_user_oauth_provider_subject
    ON sys_user_oauth (provider, provider_user_id)
    WHERE deleted = false;

CREATE INDEX idx_sys_user_oauth_user_id
    ON sys_user_oauth (user_id)
    WHERE deleted = false;

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
-- * Password hashing, status value set, register_source set, and role/permission value sets
--   validated in application. OAuth-only users have null password_hash; password login must
--   reject them cleanly instead of comparing hashes.
-- * OAuth login flow (sa-token-oauth2 / OIDC client): verify provider id_token signature +
--   iss/aud/exp, resolve by (provider, provider_user_id); unknown subject -> create sys_user +
--   sys_user_oauth in one local transaction. uq_sys_user_oauth_provider_subject is the backstop
--   against double-register races; map violation to retry-then-login.
-- * Email-based account merge (binding a Google login to an existing email account) is an
--   explicit verified action, never automatic on email match alone.
-- * Provider access/refresh tokens are NOT stored here by default; if refresh is ever needed,
--   add encrypted columns — never plaintext, never logged. raw_profile contains PII: never
--   return to clients, never log in full.
-- * Sa-Token session is opened with the local user id only after the steps above; RBAC via
--   StpInterface is unchanged and OAuth-agnostic.
-- * rel_user_role / rel_role_permission: on insert verify user/role/permission exist, active,
--   and not deleted; revoke = soft delete. Parent-deletion race handled by locking the parent
--   row (SELECT ... FOR UPDATE) in the same transaction.
-- * username/email uniqueness re-checked by the unique partial indexes; map violation to 409.
-- * Scheduled orphan audit: rel rows whose user/role/permission is deleted or missing, and
--   sys_user_oauth rows whose user is deleted or missing.
