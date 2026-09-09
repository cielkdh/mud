-- 제안 DDL; 실제 Room 생성 schema와 검토 후 동기화.
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS content_manifest (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  content_version TEXT NOT NULL,
  balance_version TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  source_hash TEXT NOT NULL,
  bundle_hash TEXT NOT NULL,
  profile TEXT NOT NULL,
  UNIQUE(content_version)
);
CREATE INDEX IF NOT EXISTS ix_content_manifest_1 ON content_manifest(profile);

CREATE TABLE IF NOT EXISTS content_template (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  kind TEXT NOT NULL,
  source_id TEXT NOT NULL,
  display_name TEXT NOT NULL,
  grade TEXT,
  min_level INTEGER,
  tags_json TEXT NOT NULL,
  definition_json TEXT NOT NULL,
  definition_version INTEGER NOT NULL,
  UNIQUE(source_id)
);
CREATE INDEX IF NOT EXISTS ix_content_template_1 ON content_template(kind,grade);
CREATE INDEX IF NOT EXISTS ix_content_template_2 ON content_template(display_name);

CREATE TABLE IF NOT EXISTS content_alias (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  old_id TEXT NOT NULL,
  new_id TEXT,
  policy TEXT NOT NULL,
  reason TEXT NOT NULL,
  UNIQUE(old_id)
);

CREATE TABLE IF NOT EXISTS asset_image (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  relative_path TEXT NOT NULL,
  category TEXT NOT NULL,
  width INTEGER NOT NULL CHECK(width>0),
  height INTEGER NOT NULL CHECK(height>0),
  byte_size INTEGER NOT NULL CHECK(byte_size>=0),
  sha256 TEXT NOT NULL,
  pool_version TEXT,
  UNIQUE(relative_path)
);
CREATE INDEX IF NOT EXISTS ix_asset_image_1 ON asset_image(category);

CREATE TABLE IF NOT EXISTS asset_binding (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  entity_kind TEXT NOT NULL,
  template_id TEXT NOT NULL,
  usage_type TEXT NOT NULL,
  asset_id TEXT NOT NULL REFERENCES asset_image(id) ON DELETE RESTRICT,
  crop_profile TEXT NOT NULL,
  priority INTEGER NOT NULL,
  UNIQUE(entity_kind,template_id,usage_type,priority)
);
CREATE INDEX IF NOT EXISTS ix_asset_binding_1 ON asset_binding(asset_id);

CREATE TABLE IF NOT EXISTS asset_fallback (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  category TEXT NOT NULL,
  matcher TEXT NOT NULL,
  asset_id TEXT NOT NULL REFERENCES asset_image(id) ON DELETE RESTRICT,
  priority INTEGER NOT NULL,
  UNIQUE(category,matcher,priority)
);
CREATE INDEX IF NOT EXISTS ix_asset_fallback_1 ON asset_fallback(asset_id);
