/*
 * VerschraenktCI Database Schema - Row Level Security
 * 
 * Enables row-level security policies on sensitive tables.
 * Let's lock this down a bit.
 */

-- Enable RLS on sensitive tables
ALTER TABLE pipelines ENABLE ROW LEVEL SECURITY;
ALTER TABLE executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE secret_access_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Pipelines: users see their own stuff, admins see everything
CREATE POLICY pipeline_owner_policy ON pipelines
  FOR ALL
  USING (
    created_by = current_setting('app.current_user', true) OR
    current_setting('app.is_admin', true)::boolean = true
  );

-- Executions: same deal, you run it, you see it
CREATE POLICY execution_owner_policy ON executions
  FOR ALL
  USING (
    trigger_by = current_setting('app.current_user', true) OR
    current_setting('app.is_admin', true)::boolean = true
  );

-- Secrets: highly sensitive, strict need-to-know basis
CREATE POLICY secret_access_policy ON secrets
  FOR ALL
  USING (
    current_setting('app.is_admin', true)::boolean = true OR
    (scope = 'global' AND current_setting('app.can_read_global_secrets', true)::boolean = true) OR
    (scope != 'global' AND created_by = current_setting('app.current_user', true))
  );

-- Secret access log: admin eyes only
CREATE POLICY secret_log_admin_policy ON secret_access_log
  FOR SELECT
  USING (current_setting('app.is_admin', true)::boolean = true);

-- Audit log: admin eyes only
CREATE POLICY audit_admin_policy ON audit_log
  FOR SELECT
  USING (current_setting('app.is_admin', true)::boolean = true);
