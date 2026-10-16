-- Per-service table grants for the SWE5001 service assemblies (backend ADR 0008, #455 step A4).
--
-- A Flyway afterMigrate callback, not a repeatable migration: infra/swe5001/db-init grants DML on
-- every table the owner creates to the service's runtime role (default privileges), so each table
-- a later migration adds must be re-checked on every start, not only when this file changes.
-- Loaded only from classpath:db/swe5001, which only the service profiles list; the file is not on
-- the monolith's classpath. Runs as the database owner (SPRING_FLYWAY_USER).
--
-- Ownership (refactoring plan section 2.2): identity owns the user, role and permission tables;
-- notification owns its notification and consumed-message tables (created later by #215 and the
-- ADR 0007 work); commerce owns every other table and sequence. Placeholders come from
-- application-<service>.yml: ${service} and ${approle} (the runtime role, DB_USERNAME).
DO $grants$
DECLARE
    svc CONSTANT text := '${service}';
    app CONSTANT text := '${approle}';
    identity_tables CONSTANT text[] := ARRAY[
        'sys_user', 'sys_user_oauth', 'sys_role', 'sys_permission', 'rel_user_role', 'rel_role_permission'];
    notification_tables CONSTANT text[] := ARRAY['biz_notification', 'sys_consumed_message'];
    rel record;
    owned boolean;
BEGIN
    IF svc NOT IN ('identity', 'commerce', 'notification') THEN
        RAISE EXCEPTION 'swe5001 grants: unknown service "%"', svc;
    END IF;
    -- Local runs and tests that connect as the owner have no separate runtime role to restrict.
    IF app = '' OR app = current_user OR NOT EXISTS (SELECT FROM pg_roles WHERE rolname = app) THEN
        RAISE NOTICE 'swe5001 grants skipped: no separate runtime role "%"', app;
        RETURN;
    END IF;
    -- Two tasks of one service may migrate at once, and concurrent GRANTs on one table can fail.
    PERFORM pg_advisory_xact_lock(hashtext('blocbuy-swe5001-grants'));

    FOR rel IN
        SELECT c.relname, c.relkind
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'domain' AND c.relkind IN ('r', 'p', 'S')
    LOOP
        owned := CASE
            WHEN rel.relname = 'flyway_schema_history' THEN false
            WHEN svc = 'identity' THEN rel.relname = ANY (identity_tables)
            WHEN svc = 'notification' THEN rel.relname = ANY (notification_tables)
            ELSE NOT (rel.relname = ANY (identity_tables || notification_tables))
        END;
        IF rel.relkind = 'S' THEN
            IF owned THEN
                EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE domain.%I TO %I', rel.relname, app);
            ELSE
                EXECUTE format('REVOKE ALL ON SEQUENCE domain.%I FROM %I', rel.relname, app);
            END IF;
        ELSIF owned THEN
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE domain.%I TO %I', rel.relname, app);
        ELSE
            EXECUTE format('REVOKE ALL ON TABLE domain.%I FROM %I', rel.relname, app);
        END IF;
    END LOOP;
END
$grants$;
