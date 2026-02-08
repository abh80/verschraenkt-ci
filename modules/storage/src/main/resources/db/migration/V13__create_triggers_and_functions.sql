/*
 * VerschraenktCI Database Schema - Triggers and Functions
 * 
 * Creates helper functions and triggers for automated table maintenance.
 */

-- -----------------------------------------------------------------------------
-- Helper Functions
-- -----------------------------------------------------------------------------

-- Trigger function to keep our 'updated_at' columns fresh
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Wiring up the triggers
CREATE TRIGGER update_pipelines_updated_at
  BEFORE UPDATE ON pipelines
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_secrets_updated_at
  BEFORE UPDATE ON secrets
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cache_entries_updated_at
  BEFORE UPDATE ON cache_entries
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Auto-create those monthly partitions so we don't have to wake up at midnight on the 1st
CREATE OR REPLACE FUNCTION create_monthly_partition(
  parent_table TEXT,
  partition_date DATE
) RETURNS VOID AS $$
DECLARE
  partition_name TEXT;
  start_date DATE;
  end_date DATE;
BEGIN
  start_date := date_trunc('month', partition_date);
  end_date := start_date + INTERVAL '1 month';
  partition_name := parent_table || '_' || to_char(start_date, 'YYYY_MM');
  
  EXECUTE format(
    'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
    partition_name, parent_table, start_date, end_date
  );
END;
$$ LANGUAGE plpgsql;
