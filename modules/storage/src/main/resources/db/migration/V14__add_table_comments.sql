/*
 * VerschraenktCI Database Schema - Table Comments
 * 
 * Adds documentation comments to tables and important columns.
 * These show up in DB tools, which is nice.
 */

COMMENT ON TABLE pipelines IS 'CI/CD pipeline definitions with version history';
COMMENT ON TABLE executions IS 'Pipeline execution instances';
COMMENT ON TABLE executors IS 'Registered execution agents/runners';
COMMENT ON TABLE secrets IS 'Secret references stored in external vault (encrypted paths)';
COMMENT ON TABLE cache_entries IS 'Build cache entries with scope-based isolation';
COMMENT ON TABLE audit_log IS 'Immutable audit trail for compliance';

COMMENT ON COLUMN secrets.vault_path_encrypted IS 'AES-256 encrypted vault path, decrypted at runtime';
COMMENT ON COLUMN secrets.vault_path_key_id IS 'KMS key ID for rotation tracking';
COMMENT ON COLUMN executors.token_hash IS 'Argon2/bcrypt hashed authentication token';
