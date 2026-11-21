CREATE TABLE IF NOT EXISTS cloud_files (
  id BIGSERIAL PRIMARY KEY,
  original_name TEXT NOT NULL,
  mime_type TEXT NOT NULL DEFAULT 'application/octet-stream',
  byte_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  provider TEXT NOT NULL DEFAULT 'telegram',
  telegram_chat_id TEXT NOT NULL,
  telegram_message_id BIGINT NOT NULL,
  telegram_file_id TEXT NOT NULL,
  telegram_file_unique_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cloud_files_created ON cloud_files(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cloud_files_hash ON cloud_files(sha256);
ALTER TABLE cloud_files ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_cloud_files_deleted ON cloud_files(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS activity_logs (
  id BIGSERIAL PRIMARY KEY, action TEXT NOT NULL, file_id BIGINT, file_name TEXT,
  details JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_activity_logs_created ON activity_logs(created_at DESC);


-- Autentikasi perangkat dan pairing QR (ditambahkan pada Nexora 3.3).
CREATE TABLE IF NOT EXISTS nexora_devices (
  id BIGSERIAL PRIMARY KEY,
  device_name TEXT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_nexora_devices_active ON nexora_devices(created_at DESC) WHERE revoked_at IS NULL;
CREATE TABLE IF NOT EXISTS nexora_pairings (
  id UUID PRIMARY KEY,
  secret_hash CHAR(64) NOT NULL,
  device_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  approved_at TIMESTAMPTZ,
  consumed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_nexora_pairings_expiry ON nexora_pairings(expires_at);
