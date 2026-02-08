/*
 * VerschraenktCI Database Schema - UUID Functions
 * 
 * Custom UUID v7 generator for time-ordered UUIDs.
 * Time-ordered UUIDs play much nicer with B-tree indexes than random v4 UUIDs.
 */

CREATE OR REPLACE FUNCTION uuid_generate_v7() RETURNS uuid AS $$
DECLARE
  unix_ts_ms BIGINT;
  uuid_bytes BYTEA;
BEGIN
  unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
  uuid_bytes := decode(lpad(to_hex(unix_ts_ms), 12, '0'), 'hex') || gen_random_bytes(10);
  -- Set version (7) and variant bits
  uuid_bytes := set_byte(uuid_bytes, 6, (get_byte(uuid_bytes, 6) & 15) | 112);
  uuid_bytes := set_byte(uuid_bytes, 8, (get_byte(uuid_bytes, 8) & 63) | 128);
  RETURN encode(uuid_bytes, 'hex')::uuid;
END;
$$ LANGUAGE plpgsql VOLATILE;
