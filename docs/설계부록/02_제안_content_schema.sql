-- content.db 권위 DDL; JVM builder와 Android read-only adapter가 공유한다.
PRAGMA foreign_keys=ON;
PRAGMA journal_mode=DELETE;

CREATE TABLE content_manifest (
  id TEXT PRIMARY KEY NOT NULL CHECK(id='CONTENT-MANIFEST'),
  content_version TEXT NOT NULL,
  balance_version TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  source_hash TEXT NOT NULL,
  bundle_hash TEXT NOT NULL,
  profile TEXT NOT NULL CHECK(profile IN ('PROTOTYPE','ALPHA','FULL'))
);

CREATE TABLE content_template (
  id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('ACC','ARM','BOS','CHAIN','CTR','DNG-EVT','EPRE','ESUF','EVT','ITM','LEG','MON','MPRE','MSUF','REL','SET','SKL','SPRE','SSUF','WPN')),
  source_display_name TEXT NOT NULL,
  display_name_override TEXT,
  display_name TEXT NOT NULL,
  grade TEXT,
  min_level INTEGER,
  tags_json TEXT NOT NULL,
  definition_json TEXT NOT NULL,
  definition_version INTEGER NOT NULL,
  CHECK(display_name=COALESCE(display_name_override,source_display_name))
);
CREATE INDEX ix_content_template_kind_id ON content_template(kind,id);

CREATE TABLE content_alias (
  id TEXT PRIMARY KEY NOT NULL,
  old_id TEXT NOT NULL,
  new_id TEXT REFERENCES content_template(id) ON DELETE RESTRICT,
  policy TEXT NOT NULL CHECK(policy IN ('REMAP','TOMBSTONE')),
  reason TEXT NOT NULL,
  CHECK(old_id<>COALESCE(new_id,'')),
  CHECK((policy='REMAP' AND new_id IS NOT NULL) OR (policy='TOMBSTONE' AND new_id IS NULL)),
  UNIQUE(old_id)
);

CREATE TABLE asset_image (
  id TEXT PRIMARY KEY NOT NULL,
  relative_path TEXT NOT NULL,
  category TEXT NOT NULL CHECK(category IN ('PORTRAIT','BACKGROUND','ICON','EMBLEM','EVENT_ART','KEY_ART')),
  width INTEGER NOT NULL CHECK(width>0),
  height INTEGER NOT NULL CHECK(height>0),
  byte_size INTEGER NOT NULL CHECK(byte_size>0),
  sha256 TEXT NOT NULL CHECK(length(sha256)=64 AND sha256 NOT GLOB '*[^0-9a-f]*'),
  pool_version TEXT,
  focal_x_ppm INTEGER CHECK(focal_x_ppm BETWEEN 0 AND 1000000),
  focal_y_ppm INTEGER CHECK(focal_y_ppm BETWEEN 0 AND 1000000),
  CHECK((focal_x_ppm IS NULL) = (focal_y_ppm IS NULL)),
  UNIQUE(relative_path)
);

CREATE TABLE asset_binding (
  id TEXT PRIMARY KEY NOT NULL,
  template_id TEXT NOT NULL REFERENCES content_template(id) ON DELETE RESTRICT,
  usage_type TEXT NOT NULL CHECK(usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB','ROOM_BACKGROUND','KEY_ART','ICON','EMBLEM','EVENT_ART')),
  asset_id TEXT NOT NULL REFERENCES asset_image(id) ON DELETE RESTRICT,
  priority INTEGER NOT NULL CHECK(priority>=0),
  UNIQUE(template_id,usage_type,priority)
);

CREATE TABLE asset_fallback (
  id TEXT PRIMARY KEY NOT NULL,
  usage_type TEXT NOT NULL CHECK(usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB','ROOM_BACKGROUND','KEY_ART','ICON','EMBLEM','EVENT_ART')),
  matcher_type TEXT NOT NULL CHECK(matcher_type IN ('SEX','ARCHETYPE','CLASS','MONSTER_FAMILY','REGION','ROOM_THEME','ITEM_TYPE','FACILITY_CATEGORY','CATEGORY_DEFAULT','GLOBAL_DEFAULT')),
  matcher_value TEXT,
  asset_id TEXT NOT NULL REFERENCES asset_image(id) ON DELETE RESTRICT,
  priority INTEGER NOT NULL CHECK(priority>=0),
  CHECK(
    (matcher_type IN ('CATEGORY_DEFAULT','GLOBAL_DEFAULT') AND matcher_value IS NULL)
    OR
    (matcher_type NOT IN ('CATEGORY_DEFAULT','GLOBAL_DEFAULT') AND matcher_value IS NOT NULL AND length(trim(matcher_value))>0)
  )
);
CREATE UNIQUE INDEX ux_asset_fallback_order ON asset_fallback(usage_type,matcher_type,COALESCE(matcher_value,''),priority);
