-- 제안 DDL; 실제 Room 생성 schema와 검토 후 동기화.
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS world_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  total_game_minutes INTEGER NOT NULL CHECK(total_game_minutes>=0),
  sub_minute_ms INTEGER NOT NULL CHECK(sub_minute_ms BETWEEN 0 AND 59999),
  world_seed TEXT NOT NULL,
  session_epoch TEXT NOT NULL,
  branch_id TEXT NOT NULL,
  content_version TEXT NOT NULL,
  balance_version TEXT NOT NULL,
  rng_version TEXT NOT NULL,
  engine_order_version INTEGER NOT NULL,
  player_id TEXT,
  state_hash TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_world_state_1 ON world_state(session_epoch);

CREATE TABLE IF NOT EXISTS command_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  command_id TEXT NOT NULL,
  epoch TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  result_code TEXT NOT NULL,
  result_json TEXT NOT NULL,
  state_version INTEGER NOT NULL,
  game_minute INTEGER NOT NULL,
  UNIQUE(epoch,command_id)
);
CREATE INDEX IF NOT EXISTS ix_command_receipt_1 ON command_receipt(state_version);

CREATE TABLE IF NOT EXISTS world_event (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_id TEXT,
  source_event_id TEXT,
  source_epoch TEXT NOT NULL CHECK(length(source_epoch)>0),
  source_command_id TEXT NOT NULL CHECK(length(source_command_id)>0),
  source_version INTEGER NOT NULL CHECK(source_version>=0),
  event_type TEXT NOT NULL,
  event_sequence INTEGER NOT NULL CHECK(event_sequence>=0),
  game_minute INTEGER NOT NULL CHECK(game_minute>=0),
  sub_ms INTEGER NOT NULL CHECK(sub_ms BETWEEN 0 AND 59999),
  visibility TEXT NOT NULL,
  importance INTEGER NOT NULL,
  payload_json TEXT NOT NULL,
  consumed_mask INTEGER NOT NULL DEFAULT 0,
  UNIQUE(source_epoch,source_command_id,event_sequence),
  FOREIGN KEY(source_epoch,source_command_id) REFERENCES command_receipt(epoch,command_id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS ix_world_event_1 ON world_event(game_minute,id);
CREATE INDEX IF NOT EXISTS ix_world_event_2 ON world_event(event_type,game_minute);
CREATE INDEX IF NOT EXISTS ix_world_event_3 ON world_event(source_epoch,source_command_id,event_sequence);

CREATE TABLE IF NOT EXISTS rng_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  stream_key TEXT NOT NULL,
  algorithm_version TEXT NOT NULL,
  state_hex TEXT NOT NULL,
  increment_hex TEXT NOT NULL,
  draw_counter INTEGER NOT NULL CHECK(draw_counter>=0),
  UNIQUE(stream_key)
);

CREATE TABLE IF NOT EXISTS scheduled_action (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  actor_id TEXT,
  action_kind TEXT NOT NULL,
  start_minute INTEGER NOT NULL,
  due_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  reservation_group_id TEXT,
  payload_json TEXT NOT NULL,
  completion_event_id TEXT,
  CHECK(due_minute>=start_minute),
  UNIQUE(completion_event_id)
);
CREATE INDEX IF NOT EXISTS ix_scheduled_action_1 ON scheduled_action(status,due_minute,id);
CREATE INDEX IF NOT EXISTS ix_scheduled_action_2 ON scheduled_action(actor_id,start_minute);

CREATE TABLE IF NOT EXISTS occupancy (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  resource_key TEXT NOT NULL,
  action_id TEXT NOT NULL REFERENCES scheduled_action(id) ON DELETE RESTRICT,
  start_minute INTEGER NOT NULL,
  end_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  CHECK(end_minute>start_minute),
  UNIQUE(resource_key,action_id)
);
CREATE INDEX IF NOT EXISTS ix_occupancy_1 ON occupancy(resource_key,status,start_minute,end_minute);

CREATE TABLE IF NOT EXISTS resource_reservation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  resource_kind TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  action_id TEXT NOT NULL REFERENCES scheduled_action(id) ON DELETE RESTRICT,
  quantity INTEGER NOT NULL CHECK(quantity>0),
  status TEXT NOT NULL,
  UNIQUE(resource_kind,resource_id,action_id)
);
CREATE INDEX IF NOT EXISTS ix_resource_reservation_1 ON resource_reservation(resource_id,status);
CREATE INDEX IF NOT EXISTS ix_resource_reservation_2 ON resource_reservation(action_id);

CREATE TABLE IF NOT EXISTS time_advance_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  request_id TEXT NOT NULL,
  target_minute INTEGER NOT NULL,
  last_boundary_key TEXT,
  next_boundary_minute INTEGER,
  interrupt_policy_json TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(request_id)
);

CREATE TABLE IF NOT EXISTS save_generation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  parent_id TEXT REFERENCES save_generation(id) ON DELETE RESTRICT,
  branch_id TEXT NOT NULL,
  generation_no INTEGER NOT NULL,
  game_minute INTEGER NOT NULL,
  schema_version INTEGER NOT NULL,
  content_version TEXT NOT NULL,
  balance_version TEXT NOT NULL,
  manifest_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('WRITING','COMMITTED','ABORTED')),
  UNIQUE(branch_id,generation_no)
);
CREATE INDEX IF NOT EXISTS ix_save_generation_1 ON save_generation(status,generation_no);

CREATE TABLE IF NOT EXISTS checkpoint_chunk (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  sha256 TEXT NOT NULL,
  codec_version INTEGER NOT NULL,
  encoding TEXT NOT NULL,
  uncompressed_bytes INTEGER NOT NULL CHECK(uncompressed_bytes>=0),
  payload BLOB NOT NULL,
  UNIQUE(sha256)
);

CREATE TABLE IF NOT EXISTS generation_chunk (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  generation_id TEXT NOT NULL REFERENCES save_generation(id) ON DELETE RESTRICT,
  domain_key TEXT NOT NULL,
  shard_no INTEGER NOT NULL,
  chunk_id TEXT NOT NULL REFERENCES checkpoint_chunk(id) ON DELETE RESTRICT,
  UNIQUE(generation_id,domain_key,shard_no)
);
CREATE INDEX IF NOT EXISTS ix_generation_chunk_1 ON generation_chunk(chunk_id);

CREATE TABLE IF NOT EXISTS save_slot (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  slot_kind TEXT NOT NULL,
  ordinal INTEGER NOT NULL,
  display_name TEXT NOT NULL,
  generation_id TEXT NOT NULL REFERENCES save_generation(id) ON DELETE RESTRICT,
  updated_at_real_ms INTEGER NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(slot_kind,ordinal)
);
CREATE INDEX IF NOT EXISTS ix_save_slot_1 ON save_slot(generation_id);

CREATE TABLE IF NOT EXISTS recovery_checkpoint (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  checkpoint_kind TEXT NOT NULL,
  generation_id TEXT NOT NULL REFERENCES save_generation(id) ON DELETE RESTRICT,
  payload_codec TEXT NOT NULL,
  domain_payload BLOB NOT NULL,
  checksum TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_recovery_checkpoint_1 ON recovery_checkpoint(generation_id);

CREATE TABLE IF NOT EXISTS migration_history (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  from_version INTEGER NOT NULL,
  to_version INTEGER NOT NULL,
  migration_id TEXT NOT NULL,
  source_hash TEXT NOT NULL,
  result_hash TEXT,
  status TEXT NOT NULL,
  error_code TEXT,
  UNIQUE(source_hash,migration_id)
);

CREATE TABLE IF NOT EXISTS recovery_journal (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  operation_id TEXT NOT NULL,
  action_kind TEXT NOT NULL,
  source_generation_id TEXT,
  result_generation_id TEXT,
  error_code TEXT,
  detail_json TEXT NOT NULL,
  UNIQUE(operation_id)
);
CREATE INDEX IF NOT EXISTS ix_recovery_journal_1 ON recovery_journal(source_generation_id);

CREATE TABLE IF NOT EXISTS content_binding (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  content_version TEXT NOT NULL,
  balance_version TEXT NOT NULL,
  source_bundle_hash TEXT NOT NULL,
  compatibility_json TEXT NOT NULL,
  UNIQUE(content_version,balance_version)
);

CREATE TABLE IF NOT EXISTS mercenary (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  sex_code TEXT NOT NULL CHECK(sex_code IN ('M','W')),
  given_name TEXT NOT NULL,
  family_name TEXT NOT NULL,
  display_name TEXT NOT NULL,
  name_generator_version TEXT NOT NULL,
  birth_game_day INTEGER NOT NULL,
  culture_id TEXT NOT NULL,
  portrait_image_key TEXT NOT NULL,
  portrait_pool_version TEXT NOT NULL,
  class_id TEXT NOT NULL,
  level INTEGER NOT NULL CHECK(level>=1),
  experience INTEGER NOT NULL CHECK(experience>=0),
  lifecycle_status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_mercenary_1 ON mercenary(lifecycle_status,level);
CREATE INDEX IF NOT EXISTS ix_mercenary_2 ON mercenary(display_name);
CREATE INDEX IF NOT EXISTS ix_mercenary_3 ON mercenary(portrait_image_key);

CREATE TABLE IF NOT EXISTS character_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  hp INTEGER NOT NULL CHECK(hp>=0),
  mp INTEGER NOT NULL CHECK(mp>=0),
  stamina INTEGER NOT NULL CHECK(stamina>=0),
  fatigue INTEGER NOT NULL,
  location_id TEXT,
  activity_status TEXT NOT NULL,
  free_stat_points INTEGER NOT NULL CHECK(free_stat_points>=0),
  UNIQUE(mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_character_state_1 ON character_state(location_id,activity_status);

CREATE TABLE IF NOT EXISTS character_stat (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  stat_key TEXT NOT NULL,
  initial_value INTEGER NOT NULL,
  class_growth INTEGER NOT NULL,
  allocated_value INTEGER NOT NULL,
  permanent_value INTEGER NOT NULL,
  base_potential INTEGER NOT NULL,
  potential_modifier INTEGER NOT NULL,
  UNIQUE(mercenary_id,stat_key)
);

CREATE TABLE IF NOT EXISTS growth_ledger (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  source_event_id TEXT NOT NULL,
  growth_kind TEXT NOT NULL,
  level_at_event INTEGER NOT NULL,
  stat_key TEXT NOT NULL DEFAULT 'NONE',
  delta_value INTEGER NOT NULL,
  reversible INTEGER NOT NULL CHECK(reversible IN(0,1)),
  UNIQUE(mercenary_id,source_event_id,growth_kind,stat_key)
);
CREATE INDEX IF NOT EXISTS ix_growth_ledger_1 ON growth_ledger(mercenary_id,level_at_event);

CREATE TABLE IF NOT EXISTS potential_event (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  source_event_id TEXT NOT NULL,
  stat_key TEXT NOT NULL,
  modifier_value INTEGER NOT NULL,
  replaces_event_id TEXT,
  status TEXT NOT NULL,
  UNIQUE(mercenary_id,source_event_id,stat_key)
);

CREATE TABLE IF NOT EXISTS mastery (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  domain_key TEXT NOT NULL,
  experience INTEGER NOT NULL CHECK(experience>=0),
  mastery_level INTEGER NOT NULL CHECK(mastery_level>=0),
  UNIQUE(mercenary_id,domain_key)
);

CREATE TABLE IF NOT EXISTS name_registry (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  normalized_full_name TEXT NOT NULL,
  active INTEGER NOT NULL CHECK(active IN(0,1)),
  allocation_attempts INTEGER NOT NULL,
  collision_mode TEXT NOT NULL,
  UNIQUE(mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_name_registry_1 ON name_registry(active,normalized_full_name);

CREATE TABLE IF NOT EXISTS portrait_reservation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  portrait_key TEXT NOT NULL,
  status TEXT NOT NULL,
  reservation_mode TEXT NOT NULL,
  reusable_after_minute INTEGER,
  pool_version TEXT NOT NULL,
  UNIQUE(mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_portrait_reservation_1 ON portrait_reservation(portrait_key,status);
CREATE INDEX IF NOT EXISTS ix_portrait_reservation_2 ON portrait_reservation(status,reusable_after_minute);

CREATE TABLE IF NOT EXISTS knowledge_record (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  observer_id TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  field_key TEXT NOT NULL,
  disclosure_level TEXT NOT NULL,
  known_value_json TEXT,
  observed_minute INTEGER NOT NULL,
  UNIQUE(observer_id,subject_id,field_key)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_record_1 ON knowledge_record(observer_id,subject_id);

CREATE TABLE IF NOT EXISTS money_account (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  owner_kind TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  purpose TEXT NOT NULL,
  balance INTEGER NOT NULL CHECK(balance>=0),
  reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0 AND reserved<=balance),
  UNIQUE(owner_kind,owner_id,purpose)
);
CREATE INDEX IF NOT EXISTS ix_money_account_1 ON money_account(owner_id);

CREATE TABLE IF NOT EXISTS storage_location (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  owner_kind TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  location_kind TEXT NOT NULL,
  parent_location_id TEXT,
  capacity INTEGER NOT NULL,
  weight_limit INTEGER NOT NULL,
  access_policy_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_storage_location_1 ON storage_location(owner_kind,owner_id);
CREATE INDEX IF NOT EXISTS ix_storage_location_2 ON storage_location(parent_location_id);

CREATE TABLE IF NOT EXISTS item_instance (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  template_id TEXT NOT NULL,
  owner_kind TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  custodian_id TEXT,
  storage_id TEXT NOT NULL REFERENCES storage_location(id) ON DELETE RESTRICT,
  durability INTEGER NOT NULL CHECK(durability BETWEEN 0 AND 100),
  grade TEXT NOT NULL,
  prefix_id TEXT,
  suffix_id TEXT,
  protection_flags INTEGER NOT NULL,
  lifecycle_status TEXT NOT NULL,
  source_event_id TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_item_instance_1 ON item_instance(owner_id);
CREATE INDEX IF NOT EXISTS ix_item_instance_2 ON item_instance(storage_id);
CREATE INDEX IF NOT EXISTS ix_item_instance_3 ON item_instance(template_id);
CREATE INDEX IF NOT EXISTS ix_item_instance_4 ON item_instance(source_event_id);

CREATE TABLE IF NOT EXISTS inventory_stack (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  storage_id TEXT NOT NULL REFERENCES storage_location(id) ON DELETE RESTRICT,
  template_id TEXT NOT NULL,
  stack_signature TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity>=0),
  reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0 AND reserved<=quantity),
  UNIQUE(storage_id,template_id,stack_signature)
);
CREATE INDEX IF NOT EXISTS ix_inventory_stack_1 ON inventory_stack(template_id);

CREATE TABLE IF NOT EXISTS equipment_slot (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  slot_key TEXT NOT NULL,
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  UNIQUE(mercenary_id,slot_key),
  UNIQUE(item_id)
);
CREATE INDEX IF NOT EXISTS ix_equipment_slot_1 ON equipment_slot(mercenary_id);

CREATE TABLE IF NOT EXISTS skill_instance (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  template_id TEXT NOT NULL,
  prefix_id TEXT,
  suffix_id TEXT,
  source_event_id TEXT NOT NULL,
  mastery_exp INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_skill_instance_1 ON skill_instance(mercenary_id,template_id);
CREATE INDEX IF NOT EXISTS ix_skill_instance_2 ON skill_instance(source_event_id);

CREATE TABLE IF NOT EXISTS skill_affinity (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  skill_template_id TEXT NOT NULL,
  base_affinity INTEGER NOT NULL,
  modifier INTEGER NOT NULL,
  disclosed_level TEXT NOT NULL,
  UNIQUE(mercenary_id,skill_template_id)
);

CREATE TABLE IF NOT EXISTS loadout (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  name TEXT NOT NULL,
  status TEXT NOT NULL,
  definition_json TEXT NOT NULL,
  validation_version TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_loadout_1 ON loadout(mercenary_id,status);

CREATE TABLE IF NOT EXISTS tactic_rule (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  loadout_id TEXT NOT NULL REFERENCES loadout(id) ON DELETE RESTRICT,
  priority INTEGER NOT NULL,
  condition_ast_json TEXT NOT NULL,
  action_json TEXT NOT NULL,
  enabled INTEGER NOT NULL CHECK(enabled IN(0,1)),
  UNIQUE(loadout_id,priority)
);

CREATE TABLE IF NOT EXISTS loot_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_event_id TEXT NOT NULL,
  claimant_id TEXT NOT NULL,
  reward_json TEXT NOT NULL,
  loot_version TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(source_event_id,claimant_id)
);
CREATE INDEX IF NOT EXISTS ix_loot_receipt_1 ON loot_receipt(claimant_id);

CREATE TABLE IF NOT EXISTS combat_checkpoint (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  run_id TEXT NOT NULL,
  combat_version TEXT NOT NULL,
  content_version TEXT NOT NULL,
  combat_ms INTEGER NOT NULL CHECK(combat_ms>=0),
  combat_payload BLOB NOT NULL,
  rng_payload BLOB NOT NULL,
  checksum TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_combat_checkpoint_1 ON combat_checkpoint(run_id,combat_ms);

CREATE TABLE IF NOT EXISTS combat_result (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  run_id TEXT NOT NULL,
  encounter_id TEXT NOT NULL,
  result TEXT NOT NULL,
  duration_ms INTEGER NOT NULL,
  outcome_json TEXT NOT NULL,
  trace_hash TEXT NOT NULL,
  settlement_status TEXT NOT NULL,
  UNIQUE(encounter_id)
);
CREATE INDEX IF NOT EXISTS ix_combat_result_1 ON combat_result(run_id);

CREATE TABLE IF NOT EXISTS status_effect (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_id TEXT NOT NULL,
  definition_id TEXT NOT NULL,
  source_id TEXT NOT NULL,
  time_domain TEXT NOT NULL CHECK(time_domain IN('COMBAT_MS','WORLD_MINUTE')),
  applied_at INTEGER NOT NULL,
  expires_at INTEGER,
  stack_count INTEGER NOT NULL,
  payload_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_status_effect_1 ON status_effect(subject_id,definition_id);

CREATE TABLE IF NOT EXISTS monster_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL,
  template_id TEXT NOT NULL,
  level INTEGER NOT NULL,
  grade TEXT NOT NULL,
  room_id TEXT,
  group_id TEXT,
  state_payload BLOB NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_monster_state_1 ON monster_state(dungeon_id,room_id);
CREATE INDEX IF NOT EXISTS ix_monster_state_2 ON monster_state(group_id);

CREATE TABLE IF NOT EXISTS patrol_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL,
  group_id TEXT NOT NULL,
  current_room_id TEXT NOT NULL,
  next_due_minute INTEGER,
  route_json TEXT NOT NULL,
  blackboard_json TEXT NOT NULL,
  UNIQUE(dungeon_id,group_id)
);
CREATE INDEX IF NOT EXISTS ix_patrol_state_1 ON patrol_state(next_due_minute);

CREATE TABLE IF NOT EXISTS boss_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL,
  monster_id TEXT NOT NULL,
  phase_key TEXT NOT NULL,
  phase_sequence INTEGER NOT NULL,
  break_gauge INTEGER NOT NULL,
  flags_json TEXT NOT NULL,
  reward_claimed INTEGER NOT NULL CHECK(reward_claimed IN(0,1)),
  UNIQUE(monster_id)
);
CREATE INDEX IF NOT EXISTS ix_boss_state_1 ON boss_state(dungeon_id);

CREATE TABLE IF NOT EXISTS dungeon_instance (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  seed_hex TEXT NOT NULL,
  generator_version TEXT NOT NULL,
  grade TEXT NOT NULL,
  size_key TEXT NOT NULL,
  theme_id TEXT NOT NULL,
  lifecycle_status TEXT NOT NULL,
  spawn_minute INTEGER NOT NULL,
  deadline_minute INTEGER,
  conquest_status TEXT NOT NULL,
  graph_hash TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_dungeon_instance_1 ON dungeon_instance(lifecycle_status,deadline_minute);
CREATE INDEX IF NOT EXISTS ix_dungeon_instance_2 ON dungeon_instance(grade,spawn_minute);

CREATE TABLE IF NOT EXISTS dungeon_room (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL REFERENCES dungeon_instance(id) ON DELETE RESTRICT,
  zone_key TEXT NOT NULL,
  room_type TEXT NOT NULL,
  terrain_json TEXT NOT NULL,
  weight INTEGER NOT NULL,
  hidden INTEGER NOT NULL CHECK(hidden IN(0,1)),
  room_payload_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_dungeon_room_1 ON dungeon_room(dungeon_id,zone_key);

CREATE TABLE IF NOT EXISTS dungeon_connection (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL REFERENCES dungeon_instance(id) ON DELETE RESTRICT,
  from_room_id TEXT NOT NULL REFERENCES dungeon_room(id) ON DELETE RESTRICT,
  to_room_id TEXT NOT NULL REFERENCES dungeon_room(id) ON DELETE RESTRICT,
  travel_minutes INTEGER NOT NULL CHECK(travel_minutes>=0),
  requirement_ast_json TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(dungeon_id,from_room_id,to_room_id)
);
CREATE INDEX IF NOT EXISTS ix_dungeon_connection_1 ON dungeon_connection(from_room_id);
CREATE INDEX IF NOT EXISTS ix_dungeon_connection_2 ON dungeon_connection(to_room_id);

CREATE TABLE IF NOT EXISTS dungeon_population (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL REFERENCES dungeon_instance(id) ON DELETE RESTRICT,
  group_key TEXT NOT NULL,
  template_id TEXT NOT NULL,
  initial_count INTEGER NOT NULL,
  remaining_count INTEGER NOT NULL CHECK(remaining_count>=0),
  reserved_budget INTEGER NOT NULL,
  UNIQUE(dungeon_id,group_key,template_id)
);

CREATE TABLE IF NOT EXISTS dungeon_treasure (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL REFERENCES dungeon_instance(id) ON DELETE RESTRICT,
  room_id TEXT NOT NULL REFERENCES dungeon_room(id) ON DELETE RESTRICT,
  loot_profile_id TEXT NOT NULL,
  lock_difficulty INTEGER NOT NULL,
  mimic_flag INTEGER NOT NULL,
  status TEXT NOT NULL,
  claim_event_id TEXT,
  UNIQUE(claim_event_id)
);
CREATE INDEX IF NOT EXISTS ix_dungeon_treasure_1 ON dungeon_treasure(room_id);

CREATE TABLE IF NOT EXISTS dungeon_trap (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  room_id TEXT NOT NULL REFERENCES dungeon_room(id) ON DELETE RESTRICT,
  definition_id TEXT NOT NULL,
  hidden INTEGER NOT NULL,
  status TEXT NOT NULL,
  trigger_event_id TEXT,
  UNIQUE(trigger_event_id)
);
CREATE INDEX IF NOT EXISTS ix_dungeon_trap_1 ON dungeon_trap(room_id);

CREATE TABLE IF NOT EXISTS run_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL REFERENCES dungeon_instance(id) ON DELETE RESTRICT,
  party_id TEXT,
  current_room_id TEXT,
  started_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  supply_payload_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_run_state_1 ON run_state(dungeon_id,status);

CREATE TABLE IF NOT EXISTS exploration_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  run_id TEXT NOT NULL REFERENCES run_state(id) ON DELETE RESTRICT,
  room_id TEXT NOT NULL REFERENCES dungeon_room(id) ON DELETE RESTRICT,
  discovery_state TEXT NOT NULL,
  progress_bp INTEGER NOT NULL CHECK(progress_bp BETWEEN 0 AND 10000),
  info_bp INTEGER NOT NULL CHECK(info_bp BETWEEN 0 AND 10000),
  UNIQUE(run_id,room_id)
);

CREATE TABLE IF NOT EXISTS knowledge_fact (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_kind TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  predicate TEXT NOT NULL,
  value_json TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  valid_until INTEGER,
  UNIQUE(subject_kind,subject_id,predicate)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_fact_1 ON knowledge_fact(source_event_id);

CREATE TABLE IF NOT EXISTS map_annotation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  dungeon_id TEXT NOT NULL,
  room_id TEXT,
  observer_id TEXT NOT NULL,
  annotation_kind TEXT NOT NULL,
  text TEXT NOT NULL,
  created_minute INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_map_annotation_1 ON map_annotation(dungeon_id,observer_id);

CREATE TABLE IF NOT EXISTS camp_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  run_id TEXT NOT NULL REFERENCES run_state(id) ON DELETE RESTRICT,
  scheduled_action_id TEXT NOT NULL REFERENCES scheduled_action(id) ON DELETE RESTRICT,
  guards_json TEXT NOT NULL,
  recovery_profile TEXT NOT NULL,
  elapsed_minutes INTEGER NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(scheduled_action_id)
);

CREATE TABLE IF NOT EXISTS recovery_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  defeat_id TEXT NOT NULL,
  destination_id TEXT NOT NULL,
  gold_lost INTEGER NOT NULL,
  elapsed_minutes INTEGER NOT NULL,
  durability_delta_json TEXT NOT NULL,
  injury_result_json TEXT NOT NULL,
  recovery_kind TEXT NOT NULL,
  UNIQUE(defeat_id)
);

CREATE TABLE IF NOT EXISTS city_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  template_id TEXT NOT NULL,
  safety_index INTEGER NOT NULL,
  market_profile_id TEXT NOT NULL,
  flags_json TEXT NOT NULL,
  UNIQUE(template_id)
);

CREATE TABLE IF NOT EXISTS facility_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  city_id TEXT NOT NULL REFERENCES city_state(id) ON DELETE RESTRICT,
  template_id TEXT NOT NULL,
  open_schedule_json TEXT NOT NULL,
  capacity INTEGER NOT NULL CHECK(capacity>=0),
  service_level INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_facility_state_1 ON facility_state(city_id,template_id);

CREATE TABLE IF NOT EXISTS injury (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  definition_id TEXT NOT NULL,
  body_part TEXT NOT NULL,
  severity TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  state TEXT NOT NULL,
  treatment_due_minute INTEGER,
  UNIQUE(mercenary_id,source_event_id,body_part)
);
CREATE INDEX IF NOT EXISTS ix_injury_1 ON injury(mercenary_id,state);

CREATE TABLE IF NOT EXISTS disease (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  definition_id TEXT NOT NULL,
  exposure_event_id TEXT NOT NULL,
  onset_minute INTEGER,
  stage TEXT NOT NULL,
  severity INTEGER NOT NULL,
  UNIQUE(mercenary_id,exposure_event_id,definition_id)
);
CREATE INDEX IF NOT EXISTS ix_disease_1 ON disease(onset_minute,stage);

CREATE TABLE IF NOT EXISTS treatment_order (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  facility_id TEXT NOT NULL REFERENCES facility_state(id) ON DELETE RESTRICT,
  action_id TEXT NOT NULL REFERENCES scheduled_action(id) ON DELETE RESTRICT,
  treatment_json TEXT NOT NULL,
  cost INTEGER NOT NULL,
  refund_policy TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(action_id)
);

CREATE TABLE IF NOT EXISTS residence (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  facility_id TEXT,
  owner_kind TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  tenure TEXT NOT NULL,
  occupants_json TEXT NOT NULL,
  monthly_cost INTEGER NOT NULL,
  last_billing_month INTEGER,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_residence_1 ON residence(owner_id);

CREATE TABLE IF NOT EXISTS maintenance_order (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  batch_id TEXT NOT NULL,
  ordinal INTEGER NOT NULL,
  command_id TEXT NOT NULL,
  budget_limit INTEGER NOT NULL,
  actual_cost INTEGER NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(batch_id,ordinal),
  UNIQUE(command_id)
);

CREATE TABLE IF NOT EXISTS contract (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  template_id TEXT NOT NULL,
  issuer_id TEXT NOT NULL,
  assignee_id TEXT,
  deadline_minute INTEGER NOT NULL,
  accepted_minute INTEGER,
  status TEXT NOT NULL,
  reward_json TEXT NOT NULL,
  conditions_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_contract_1 ON contract(assignee_id,status);
CREATE INDEX IF NOT EXISTS ix_contract_2 ON contract(status,deadline_minute);

CREATE TABLE IF NOT EXISTS contract_objective (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  contract_id TEXT NOT NULL REFERENCES contract(id) ON DELETE RESTRICT,
  objective_key TEXT NOT NULL,
  target INTEGER NOT NULL,
  progress INTEGER NOT NULL CHECK(progress>=0),
  matched_event_ids_json TEXT NOT NULL,
  UNIQUE(contract_id,objective_key)
);

CREATE TABLE IF NOT EXISTS contract_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  contract_id TEXT NOT NULL REFERENCES contract(id) ON DELETE RESTRICT,
  settlement_no INTEGER NOT NULL,
  reward_json TEXT NOT NULL,
  game_minute INTEGER NOT NULL,
  UNIQUE(contract_id,settlement_no)
);

CREATE TABLE IF NOT EXISTS recruitment_post (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  owner_kind TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  criteria_public_json TEXT NOT NULL,
  conditions_json TEXT NOT NULL,
  deadline_minute INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_recruitment_post_1 ON recruitment_post(status,deadline_minute);

CREATE TABLE IF NOT EXISTS employment_contract (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  employer_id TEXT NOT NULL,
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  terms_version INTEGER NOT NULL,
  terms_json TEXT NOT NULL,
  start_minute INTEGER NOT NULL,
  end_minute INTEGER,
  status TEXT NOT NULL,
  outstanding_wages INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_employment_contract_1 ON employment_contract(mercenary_id,status);
CREATE INDEX IF NOT EXISTS ix_employment_contract_2 ON employment_contract(end_minute);

CREATE TABLE IF NOT EXISTS dialogue_session (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  speaker_id TEXT NOT NULL,
  topic_key TEXT NOT NULL,
  node_key TEXT NOT NULL,
  context_version INTEGER NOT NULL,
  last_choice_receipt_id TEXT,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_dialogue_session_1 ON dialogue_session(speaker_id,status);

CREATE TABLE IF NOT EXISTS dialogue_memory (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  speaker_id TEXT NOT NULL,
  target_id TEXT NOT NULL,
  topic_key TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  last_game_minute INTEGER NOT NULL,
  repeat_count INTEGER NOT NULL,
  importance INTEGER NOT NULL,
  UNIQUE(speaker_id,target_id,source_event_id)
);
CREATE INDEX IF NOT EXISTS ix_dialogue_memory_1 ON dialogue_memory(speaker_id,target_id,topic_key);

CREATE TABLE IF NOT EXISTS dialogue_template_binding (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  topic_key TEXT NOT NULL,
  voice_profile TEXT NOT NULL,
  template_id TEXT NOT NULL,
  content_version TEXT NOT NULL,
  UNIQUE(topic_key,voice_profile,template_id)
);

CREATE TABLE IF NOT EXISTS enhancement_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  level INTEGER NOT NULL CHECK(level BETWEEN 0 AND 20),
  attempt_count INTEGER NOT NULL CHECK(attempt_count>=0),
  target_fail_stacks_json TEXT NOT NULL,
  stability_bp INTEGER NOT NULL CHECK(stability_bp BETWEEN 0 AND 500),
  highest_level INTEGER NOT NULL CHECK(highest_level BETWEEN 0 AND 20),
  UNIQUE(item_id)
);

CREATE TABLE IF NOT EXISTS enhancement_attempt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  attempt_no INTEGER NOT NULL,
  from_level INTEGER NOT NULL,
  target_level INTEGER NOT NULL,
  final_probability_ppm INTEGER NOT NULL CHECK(final_probability_ppm BETWEEN 0 AND 1000000),
  result_json TEXT NOT NULL,
  cost_json TEXT NOT NULL,
  seed_hex TEXT NOT NULL,
  source_command_id TEXT NOT NULL,
  UNIQUE(item_id,attempt_no),
  UNIQUE(source_command_id)
);

CREATE TABLE IF NOT EXISTS enhancement_growth (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  reached_level INTEGER NOT NULL,
  base_growth_bp INTEGER NOT NULL,
  random_growth_json TEXT NOT NULL,
  great_success INTEGER NOT NULL,
  active INTEGER NOT NULL,
  UNIQUE(item_id,reached_level)
);

CREATE TABLE IF NOT EXISTS item_inscription (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  ordinal INTEGER NOT NULL,
  skill_template_id TEXT NOT NULL,
  grade TEXT NOT NULL,
  value_json TEXT NOT NULL,
  UNIQUE(item_id,ordinal)
);

CREATE TABLE IF NOT EXISTS craft_order (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  recipe_id TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  facility_id TEXT,
  action_id TEXT NOT NULL REFERENCES scheduled_action(id) ON DELETE RESTRICT,
  recipe_version TEXT NOT NULL,
  quality_seed TEXT NOT NULL,
  result_json TEXT,
  status TEXT NOT NULL,
  UNIQUE(action_id)
);
CREATE INDEX IF NOT EXISTS ix_craft_order_1 ON craft_order(owner_id,status);

CREATE TABLE IF NOT EXISTS craft_material_reservation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  order_id TEXT NOT NULL REFERENCES craft_order(id) ON DELETE RESTRICT,
  reservation_id TEXT NOT NULL REFERENCES resource_reservation(id) ON DELETE RESTRICT,
  template_id TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity>0),
  UNIQUE(order_id,reservation_id)
);

CREATE TABLE IF NOT EXISTS party (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  name TEXT NOT NULL,
  leader_id TEXT,
  charter_version INTEGER NOT NULL,
  status TEXT NOT NULL,
  cohesion INTEGER NOT NULL,
  morale INTEGER NOT NULL,
  tactical_trust INTEGER NOT NULL,
  founded_minute INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_party_1 ON party(status);

CREATE TABLE IF NOT EXISTS party_member (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL REFERENCES party(id) ON DELETE RESTRICT,
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  member_type TEXT NOT NULL,
  status TEXT NOT NULL,
  joined_minute INTEGER NOT NULL,
  satisfaction INTEGER NOT NULL,
  deployed_minutes INTEGER NOT NULL,
  UNIQUE(party_id,mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_party_member_1 ON party_member(mercenary_id,status);

CREATE TABLE IF NOT EXISTS deployment (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL REFERENCES party(id) ON DELETE RESTRICT,
  run_id TEXT NOT NULL,
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  formation_slot INTEGER NOT NULL CHECK(formation_slot BETWEEN 0 AND 5),
  status TEXT NOT NULL,
  UNIQUE(run_id,mercenary_id),
  UNIQUE(run_id,party_id,formation_slot)
);

CREATE TABLE IF NOT EXISTS party_charter (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL REFERENCES party(id) ON DELETE RESTRICT,
  charter_version INTEGER NOT NULL,
  rules_json TEXT NOT NULL,
  effective_minute INTEGER NOT NULL,
  UNIQUE(party_id,charter_version)
);

CREATE TABLE IF NOT EXISTS party_proposal (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL REFERENCES party(id) ON DELETE RESTRICT,
  proposal_type TEXT NOT NULL,
  electorate_json TEXT NOT NULL,
  closes_minute INTEGER NOT NULL,
  policy_json TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_party_proposal_1 ON party_proposal(party_id,status,closes_minute);

CREATE TABLE IF NOT EXISTS party_vote (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  proposal_id TEXT NOT NULL REFERENCES party_proposal(id) ON DELETE RESTRICT,
  voter_id TEXT NOT NULL,
  vote TEXT NOT NULL,
  voted_minute INTEGER NOT NULL,
  UNIQUE(proposal_id,voter_id)
);

CREATE TABLE IF NOT EXISTS party_distribution (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL REFERENCES party(id) ON DELETE RESTRICT,
  source_event_id TEXT NOT NULL,
  charter_version INTEGER NOT NULL,
  shares_json TEXT NOT NULL,
  remainder_account_id TEXT,
  total_value INTEGER NOT NULL,
  UNIQUE(party_id,source_event_id)
);

CREATE TABLE IF NOT EXISTS party_history (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  party_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  predecessor_party_ids_json TEXT NOT NULL,
  history_type TEXT NOT NULL,
  data_json TEXT NOT NULL,
  UNIQUE(party_id,source_event_id)
);
CREATE INDEX IF NOT EXISTS ix_party_history_1 ON party_history(party_id);

CREATE TABLE IF NOT EXISTS ranking_snapshot (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  ranking_type TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  game_day INTEGER NOT NULL,
  total_score INTEGER NOT NULL CHECK(total_score BETWEEN 0 AND 10000),
  rank_no INTEGER NOT NULL,
  formula_version TEXT NOT NULL,
  source_generation TEXT NOT NULL,
  UNIQUE(ranking_type,subject_id,game_day)
);
CREATE INDEX IF NOT EXISTS ix_ranking_snapshot_1 ON ranking_snapshot(ranking_type,game_day,rank_no);

CREATE TABLE IF NOT EXISTS ranking_streak (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  ranking_type TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  last_closed_day INTEGER NOT NULL,
  consecutive_days INTEGER NOT NULL CHECK(consecutive_days>=0),
  contribution_eligible INTEGER NOT NULL,
  UNIQUE(ranking_type,subject_id)
);

CREATE TABLE IF NOT EXISTS ranking_component (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  snapshot_id TEXT NOT NULL REFERENCES ranking_snapshot(id) ON DELETE RESTRICT,
  component_key TEXT NOT NULL,
  raw_score INTEGER NOT NULL,
  capped_score INTEGER NOT NULL,
  cap INTEGER NOT NULL,
  evidence_json TEXT NOT NULL,
  UNIQUE(snapshot_id,component_key)
);

CREATE TABLE IF NOT EXISTS market_index (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  city_id TEXT NOT NULL,
  category_key TEXT NOT NULL,
  demand_index INTEGER NOT NULL,
  supply_index INTEGER NOT NULL,
  price_index INTEGER NOT NULL,
  last_closed_day INTEGER NOT NULL,
  UNIQUE(city_id,category_key)
);

CREATE TABLE IF NOT EXISTS market_stock (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  city_id TEXT NOT NULL,
  vendor_id TEXT NOT NULL,
  template_id TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity>=0),
  buy_price INTEGER NOT NULL CHECK(buy_price>=0),
  sell_price INTEGER NOT NULL CHECK(sell_price>=0),
  quoted_minute INTEGER NOT NULL,
  UNIQUE(vendor_id,template_id)
);
CREATE INDEX IF NOT EXISTS ix_market_stock_1 ON market_stock(city_id,template_id);

CREATE TABLE IF NOT EXISTS economic_ledger (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_command_id TEXT NOT NULL,
  leg_no INTEGER NOT NULL,
  account_id TEXT NOT NULL REFERENCES money_account(id) ON DELETE RESTRICT,
  delta INTEGER NOT NULL,
  transfer_group TEXT NOT NULL,
  reason TEXT NOT NULL,
  UNIQUE(source_command_id,leg_no)
);
CREATE INDEX IF NOT EXISTS ix_economic_ledger_1 ON economic_ledger(account_id);
CREATE INDEX IF NOT EXISTS ix_economic_ledger_2 ON economic_ledger(transfer_group);

CREATE TABLE IF NOT EXISTS trade_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_command_id TEXT NOT NULL,
  quote_version INTEGER NOT NULL,
  buyer_id TEXT NOT NULL,
  seller_id TEXT NOT NULL,
  items_json TEXT NOT NULL,
  paid INTEGER NOT NULL,
  fee INTEGER NOT NULL,
  UNIQUE(source_command_id)
);

CREATE TABLE IF NOT EXISTS auction (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  seller_id TEXT NOT NULL,
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  opens_minute INTEGER NOT NULL,
  closes_minute INTEGER NOT NULL,
  minimum_bid INTEGER NOT NULL,
  winner_id TEXT,
  settled_event_id TEXT,
  status TEXT NOT NULL,
  UNIQUE(settled_event_id)
);
CREATE INDEX IF NOT EXISTS ix_auction_1 ON auction(status,closes_minute);

CREATE TABLE IF NOT EXISTS auction_bid (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  auction_id TEXT NOT NULL REFERENCES auction(id) ON DELETE RESTRICT,
  bidder_id TEXT NOT NULL,
  bid_sequence INTEGER NOT NULL,
  amount INTEGER NOT NULL CHECK(amount>0),
  reservation_account_id TEXT NOT NULL REFERENCES money_account(id) ON DELETE RESTRICT,
  status TEXT NOT NULL,
  UNIQUE(auction_id,bid_sequence)
);
CREATE INDEX IF NOT EXISTS ix_auction_bid_1 ON auction_bid(auction_id,amount,bid_sequence);

CREATE TABLE IF NOT EXISTS loan_contract (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  item_id TEXT NOT NULL REFERENCES item_instance(id) ON DELETE RESTRICT,
  owner_id TEXT NOT NULL,
  borrower_id TEXT NOT NULL,
  terms_json TEXT NOT NULL,
  deadline_minute INTEGER,
  deposit INTEGER NOT NULL CHECK(deposit>=0),
  status TEXT NOT NULL,
  return_event_id TEXT,
  UNIQUE(return_event_id)
);
CREATE INDEX IF NOT EXISTS ix_loan_contract_1 ON loan_contract(item_id,status);
CREATE INDEX IF NOT EXISTS ix_loan_contract_2 ON loan_contract(borrower_id,status);

CREATE TABLE IF NOT EXISTS shipment (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  sender_id TEXT NOT NULL,
  source_storage_id TEXT NOT NULL REFERENCES storage_location(id) ON DELETE RESTRICT,
  destination_storage_id TEXT NOT NULL REFERENCES storage_location(id) ON DELETE RESTRICT,
  transit_storage_id TEXT NOT NULL REFERENCES storage_location(id) ON DELETE RESTRICT,
  due_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  delivery_event_id TEXT,
  UNIQUE(delivery_event_id)
);
CREATE INDEX IF NOT EXISTS ix_shipment_1 ON shipment(status,due_minute);

CREATE TABLE IF NOT EXISTS shipment_item (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  shipment_id TEXT NOT NULL REFERENCES shipment(id) ON DELETE RESTRICT,
  item_id TEXT REFERENCES item_instance(id) ON DELETE RESTRICT,
  template_id TEXT,
  quantity INTEGER NOT NULL CHECK(quantity>0),
  CHECK((item_id IS NOT NULL) != (template_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_shipment_item_1 ON shipment_item(shipment_id);
CREATE INDEX IF NOT EXISTS ix_shipment_item_2 ON shipment_item(item_id);

CREATE TABLE IF NOT EXISTS observation (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  observer_id TEXT NOT NULL,
  fact_id TEXT NOT NULL REFERENCES knowledge_fact(id) ON DELETE RESTRICT,
  source_event_id TEXT NOT NULL,
  confidence_bp INTEGER NOT NULL CHECK(confidence_bp BETWEEN 0 AND 10000),
  observed_minute INTEGER NOT NULL,
  disclosed_value_json TEXT NOT NULL,
  UNIQUE(observer_id,fact_id,source_event_id)
);

CREATE TABLE IF NOT EXISTS rumor (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_id TEXT NOT NULL,
  claim_json TEXT NOT NULL,
  source_npc_id TEXT,
  spread_region_id TEXT NOT NULL,
  created_minute INTEGER NOT NULL,
  credibility_bp INTEGER NOT NULL,
  expires_minute INTEGER
);
CREATE INDEX IF NOT EXISTS ix_rumor_1 ON rumor(subject_id);
CREATE INDEX IF NOT EXISTS ix_rumor_2 ON rumor(spread_region_id);

CREATE TABLE IF NOT EXISTS knowledge_projection (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  observer_id TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  projection_version INTEGER NOT NULL,
  public_json TEXT NOT NULL,
  source_generation TEXT NOT NULL,
  UNIQUE(observer_id,subject_id)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_projection_1 ON knowledge_projection(observer_id);

CREATE TABLE IF NOT EXISTS reputation_score (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_id TEXT NOT NULL,
  scope_kind TEXT NOT NULL,
  scope_id TEXT NOT NULL,
  dimension TEXT NOT NULL,
  score INTEGER NOT NULL,
  UNIQUE(subject_id,scope_kind,scope_id,dimension)
);

CREATE TABLE IF NOT EXISTS reputation_event (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  dimension TEXT NOT NULL,
  delta INTEGER NOT NULL,
  evidence_json TEXT NOT NULL,
  UNIQUE(subject_id,source_event_id,dimension)
);

CREATE TABLE IF NOT EXISTS legal_case (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  jurisdiction_id TEXT NOT NULL,
  accused_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  judgment_json TEXT,
  status TEXT NOT NULL,
  UNIQUE(jurisdiction_id,accused_id,source_event_id)
);

CREATE TABLE IF NOT EXISTS relationship (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  from_npc_id TEXT NOT NULL,
  to_npc_id TEXT NOT NULL,
  affection INTEGER NOT NULL,
  trust INTEGER NOT NULL,
  respect INTEGER NOT NULL,
  intimacy INTEGER NOT NULL,
  conflict INTEGER NOT NULL,
  UNIQUE(from_npc_id,to_npc_id)
);
CREATE INDEX IF NOT EXISTS ix_relationship_1 ON relationship(to_npc_id);

CREATE TABLE IF NOT EXISTS relationship_memory (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  from_npc_id TEXT NOT NULL,
  to_npc_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  effect_json TEXT NOT NULL,
  importance INTEGER NOT NULL,
  occurred_minute INTEGER NOT NULL,
  decay_profile TEXT NOT NULL,
  UNIQUE(from_npc_id,to_npc_id,source_event_id)
);
CREATE INDEX IF NOT EXISTS ix_relationship_memory_1 ON relationship_memory(from_npc_id,to_npc_id,occurred_minute);

CREATE TABLE IF NOT EXISTS relationship_stage (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  relationship_id TEXT NOT NULL REFERENCES relationship(id) ON DELETE RESTRICT,
  stage_tag TEXT NOT NULL,
  established_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(relationship_id,stage_tag)
);

CREATE TABLE IF NOT EXISTS personality_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  axes_json TEXT NOT NULL,
  charisma INTEGER NOT NULL,
  voice_profile TEXT NOT NULL,
  source_version TEXT NOT NULL,
  UNIQUE(mercenary_id)
);

CREATE TABLE IF NOT EXISTS trait_binding (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  trait_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  active INTEGER NOT NULL,
  UNIQUE(mercenary_id,trait_id)
);

CREATE TABLE IF NOT EXISTS personal_goal (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  goal_type TEXT NOT NULL,
  target_id TEXT,
  priority INTEGER NOT NULL,
  status TEXT NOT NULL,
  progress_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_personal_goal_1 ON personal_goal(mercenary_id,status);

CREATE TABLE IF NOT EXISTS guild (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  name TEXT NOT NULL,
  leader_id TEXT,
  charter_json TEXT NOT NULL,
  founding_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  legitimacy INTEGER NOT NULL,
  profile_id TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guild_1 ON guild(status);

CREATE TABLE IF NOT EXISTS guild_member (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  joined_minute INTEGER NOT NULL,
  status TEXT NOT NULL,
  loyalty INTEGER NOT NULL,
  contribution INTEGER NOT NULL,
  UNIQUE(guild_id,mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_guild_member_1 ON guild_member(mercenary_id,status);

CREATE TABLE IF NOT EXISTS guild_office (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  office_key TEXT NOT NULL,
  holder_id TEXT,
  term_end_minute INTEGER,
  authority_json TEXT NOT NULL,
  UNIQUE(guild_id,office_key)
);

CREATE TABLE IF NOT EXISTS guild_history (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  history_type TEXT NOT NULL,
  predecessor_ids_json TEXT NOT NULL,
  data_json TEXT NOT NULL,
  UNIQUE(guild_id,source_event_id)
);

CREATE TABLE IF NOT EXISTS guild_budget (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  fiscal_month INTEGER NOT NULL,
  purpose TEXT NOT NULL,
  limit_amount INTEGER NOT NULL,
  used_amount INTEGER NOT NULL,
  reserved_amount INTEGER NOT NULL,
  UNIQUE(guild_id,fiscal_month,purpose)
);

CREATE TABLE IF NOT EXISTS guild_facility (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  facility_template_id TEXT NOT NULL,
  level INTEGER NOT NULL,
  status TEXT NOT NULL,
  completion_action_id TEXT,
  UNIQUE(guild_id,facility_template_id)
);

CREATE TABLE IF NOT EXISTS guild_assignment (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  officer_id TEXT,
  assignment_kind TEXT NOT NULL,
  budget_id TEXT REFERENCES guild_budget(id) ON DELETE RESTRICT,
  params_json TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guild_assignment_1 ON guild_assignment(guild_id,status);

CREATE TABLE IF NOT EXISTS guild_faction (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  name TEXT NOT NULL,
  preference_json TEXT NOT NULL,
  members_json TEXT NOT NULL,
  influence INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guild_faction_1 ON guild_faction(guild_id);

CREATE TABLE IF NOT EXISTS guild_proposal (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL REFERENCES guild(id) ON DELETE RESTRICT,
  proposal_type TEXT NOT NULL,
  electorate_json TEXT NOT NULL,
  closes_minute INTEGER NOT NULL,
  policy_json TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_guild_proposal_1 ON guild_proposal(guild_id,status,closes_minute);

CREATE TABLE IF NOT EXISTS guild_vote (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  proposal_id TEXT NOT NULL REFERENCES guild_proposal(id) ON DELETE RESTRICT,
  voter_id TEXT NOT NULL,
  vote TEXT NOT NULL,
  vote_minute INTEGER NOT NULL,
  UNIQUE(proposal_id,voter_id)
);

CREATE TABLE IF NOT EXISTS raid_assignment (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  guild_id TEXT NOT NULL,
  run_id TEXT NOT NULL,
  party_id TEXT NOT NULL,
  assigned_members_json TEXT NOT NULL,
  role_key TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(run_id,party_id)
);

CREATE TABLE IF NOT EXISTS contribution_ledger (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subject_id TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  scope_kind TEXT NOT NULL,
  scope_id TEXT NOT NULL,
  points INTEGER NOT NULL,
  evidence_json TEXT NOT NULL,
  UNIQUE(subject_id,source_event_id,scope_kind,scope_id)
);

CREATE TABLE IF NOT EXISTS return_proof (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL,
  proof_type TEXT NOT NULL,
  awarded_game_minute INTEGER NOT NULL,
  source_event_id TEXT NOT NULL,
  contributing_generations_json TEXT NOT NULL,
  evidence_hash TEXT NOT NULL,
  UNIQUE(lineage_id,proof_type)
);
CREATE INDEX IF NOT EXISTS ix_return_proof_1 ON return_proof(source_event_id);

CREATE TABLE IF NOT EXISTS npc_activity (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  activity_type TEXT NOT NULL,
  due_minute INTEGER NOT NULL,
  command_id TEXT NOT NULL,
  detail_level TEXT NOT NULL,
  status TEXT NOT NULL,
  plan_json TEXT NOT NULL,
  UNIQUE(command_id)
);
CREATE INDEX IF NOT EXISTS ix_npc_activity_1 ON npc_activity(mercenary_id,status);
CREATE INDEX IF NOT EXISTS ix_npc_activity_2 ON npc_activity(due_minute,status);

CREATE TABLE IF NOT EXISTS npc_summary (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  detail_level TEXT NOT NULL,
  last_resolved_boundary TEXT NOT NULL,
  summary_json TEXT NOT NULL,
  protected_references_json TEXT NOT NULL,
  UNIQUE(mercenary_id)
);

CREATE TABLE IF NOT EXISTS simulation_cursor (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  subsystem TEXT NOT NULL,
  shard_key TEXT NOT NULL,
  last_boundary_key TEXT NOT NULL,
  game_minute INTEGER NOT NULL,
  scheduler_version TEXT NOT NULL,
  UNIQUE(subsystem,shard_key)
);

CREATE TABLE IF NOT EXISTS population_cohort (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  region_id TEXT NOT NULL,
  birth_year INTEGER NOT NULL,
  sex_code TEXT NOT NULL,
  occupation TEXT NOT NULL,
  population_count INTEGER NOT NULL CHECK(population_count>=0),
  last_closed_year INTEGER NOT NULL,
  UNIQUE(region_id,birth_year,sex_code,occupation)
);

CREATE TABLE IF NOT EXISTS mercenary_registry (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  mercenary_id TEXT NOT NULL REFERENCES mercenary(id) ON DELETE RESTRICT,
  registration_minute INTEGER NOT NULL,
  current_status TEXT NOT NULL,
  last_status_event_id TEXT NOT NULL,
  exit_reason TEXT,
  UNIQUE(mercenary_id)
);
CREATE INDEX IF NOT EXISTS ix_mercenary_registry_1 ON mercenary_registry(current_status);

CREATE TABLE IF NOT EXISTS lineage (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  name TEXT NOT NULL,
  generation_no INTEGER NOT NULL CHECK(generation_no>=1),
  current_player_id TEXT NOT NULL,
  predecessor_player_id TEXT,
  reputation INTEGER NOT NULL,
  state TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS family_member (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL REFERENCES lineage(id) ON DELETE RESTRICT,
  mercenary_id TEXT,
  cohort_ref TEXT,
  relation_type TEXT NOT NULL,
  parent_ids_json TEXT NOT NULL,
  education_stage TEXT NOT NULL,
  profession TEXT,
  adult_consent INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_family_member_1 ON family_member(lineage_id);
CREATE INDEX IF NOT EXISTS ix_family_member_2 ON family_member(mercenary_id);

CREATE TABLE IF NOT EXISTS education_plan (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  family_member_id TEXT NOT NULL REFERENCES family_member(id) ON DELETE RESTRICT,
  plan_type TEXT NOT NULL,
  start_year INTEGER NOT NULL,
  end_year INTEGER NOT NULL,
  paid_cost INTEGER NOT NULL,
  result_json TEXT,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_education_plan_1 ON education_plan(family_member_id,status);

CREATE TABLE IF NOT EXISTS succession_plan (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL REFERENCES lineage(id) ON DELETE RESTRICT,
  successor_id TEXT NOT NULL,
  eligibility_json TEXT NOT NULL,
  consent_minute INTEGER,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_succession_plan_1 ON succession_plan(lineage_id,status);

CREATE TABLE IF NOT EXISTS succession_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL REFERENCES lineage(id) ON DELETE RESTRICT,
  source_command_id TEXT NOT NULL,
  old_player_id TEXT NOT NULL,
  new_player_id TEXT NOT NULL,
  old_generation INTEGER NOT NULL,
  new_generation INTEGER NOT NULL,
  transferred_assets_hash TEXT NOT NULL,
  UNIQUE(source_command_id),
  UNIQUE(lineage_id,new_generation)
);

CREATE TABLE IF NOT EXISTS lineage_contribution (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL REFERENCES lineage(id) ON DELETE RESTRICT,
  generation_no INTEGER NOT NULL,
  source_event_id TEXT NOT NULL,
  contribution_type TEXT NOT NULL,
  amount INTEGER NOT NULL,
  UNIQUE(lineage_id,generation_no,source_event_id,contribution_type)
);

CREATE TABLE IF NOT EXISTS event_instance (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  template_id TEXT NOT NULL,
  template_version TEXT NOT NULL,
  occurred_minute INTEGER NOT NULL,
  observed_minute INTEGER,
  choice_state TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_event_instance_1 ON event_instance(status,occurred_minute);

CREATE TABLE IF NOT EXISTS event_participant (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  event_id TEXT NOT NULL REFERENCES event_instance(id) ON DELETE RESTRICT,
  role_key TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  perception_json TEXT NOT NULL,
  UNIQUE(event_id,role_key)
);

CREATE TABLE IF NOT EXISTS event_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  event_id TEXT NOT NULL REFERENCES event_instance(id) ON DELETE RESTRICT,
  choice_key TEXT NOT NULL,
  source_command_id TEXT NOT NULL,
  effect_json TEXT NOT NULL,
  UNIQUE(event_id),
  UNIQUE(source_command_id)
);

CREATE TABLE IF NOT EXISTS event_chain (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  template_id TEXT NOT NULL,
  current_step TEXT NOT NULL,
  status TEXT NOT NULL,
  cooldown_until INTEGER,
  variables_json TEXT NOT NULL,
  source_event_id TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_event_chain_1 ON event_chain(template_id,status);

CREATE TABLE IF NOT EXISTS event_chain_step (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  chain_id TEXT NOT NULL REFERENCES event_chain(id) ON DELETE RESTRICT,
  step_key TEXT NOT NULL,
  event_id TEXT,
  result_json TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(chain_id,step_key)
);

CREATE TABLE IF NOT EXISTS director_state (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  director_version TEXT NOT NULL,
  last_checked_minute INTEGER NOT NULL,
  pacing_json TEXT NOT NULL,
  cooldowns_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS director_budget (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  budget_type TEXT NOT NULL,
  period_key TEXT NOT NULL,
  allowed_amount INTEGER NOT NULL,
  consumed_amount INTEGER NOT NULL,
  CHECK(consumed_amount<=allowed_amount),
  UNIQUE(budget_type,period_key)
);

CREATE TABLE IF NOT EXISTS legendary_record (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  entity_kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  title TEXT NOT NULL,
  source_event_ids_json TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(entity_kind,entity_id)
);

CREATE TABLE IF NOT EXISTS strategy_plan (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  owner_id TEXT NOT NULL,
  input_generation TEXT NOT NULL,
  plan_json TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_strategy_plan_1 ON strategy_plan(owner_id);

CREATE TABLE IF NOT EXISTS automation_rule (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  owner_id TEXT NOT NULL,
  condition_ast_json TEXT NOT NULL,
  action_json TEXT NOT NULL,
  budget_limit INTEGER NOT NULL,
  stop_policy_json TEXT NOT NULL,
  enabled INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_automation_rule_1 ON automation_rule(owner_id,enabled);

CREATE TABLE IF NOT EXISTS return_campaign (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  lineage_id TEXT NOT NULL,
  stage TEXT NOT NULL,
  natural_spawn_enabled INTEGER NOT NULL,
  sealing_quiet_days INTEGER NOT NULL,
  war_quiet_days INTEGER NOT NULL,
  last_closed_day INTEGER NOT NULL,
  source_gate_status TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(lineage_id)
);

CREATE TABLE IF NOT EXISTS rift_core (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  campaign_id TEXT NOT NULL REFERENCES return_campaign(id) ON DELETE RESTRICT,
  core_type TEXT NOT NULL,
  dungeon_id TEXT,
  discovered_minute INTEGER,
  sealed_minute INTEGER,
  status TEXT NOT NULL,
  UNIQUE(campaign_id,core_type)
);

CREATE TABLE IF NOT EXISTS demon_faction (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  name TEXT NOT NULL,
  command_profile TEXT NOT NULL,
  strength INTEGER NOT NULL,
  status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS demon_base (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  faction_id TEXT NOT NULL REFERENCES demon_faction(id) ON DELETE RESTRICT,
  region_id TEXT NOT NULL,
  created_minute INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_demon_base_1 ON demon_base(status);

CREATE TABLE IF NOT EXISTS demon_gate (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  faction_id TEXT NOT NULL REFERENCES demon_faction(id) ON DELETE RESTRICT,
  region_id TEXT NOT NULL,
  created_minute INTEGER NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_demon_gate_1 ON demon_gate(status);

CREATE TABLE IF NOT EXISTS demon_presence (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  faction_id TEXT NOT NULL REFERENCES demon_faction(id) ON DELETE RESTRICT,
  presence_type TEXT NOT NULL,
  world_entity_id TEXT NOT NULL,
  status TEXT NOT NULL,
  UNIQUE(world_entity_id)
);
CREATE INDEX IF NOT EXISTS ix_demon_presence_1 ON demon_presence(presence_type,status);

CREATE TABLE IF NOT EXISTS ending_record (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  campaign_id TEXT NOT NULL REFERENCES return_campaign(id) ON DELETE RESTRICT,
  choice TEXT NOT NULL,
  source_command_id TEXT NOT NULL,
  game_minute INTEGER NOT NULL,
  ending_snapshot_json TEXT NOT NULL,
  UNIQUE(source_command_id)
);

CREATE TABLE IF NOT EXISTS chronicle_event (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  occurred_minute INTEGER NOT NULL,
  observed_minute INTEGER,
  importance INTEGER NOT NULL,
  visibility TEXT NOT NULL,
  text_template_id TEXT NOT NULL,
  args_json TEXT NOT NULL,
  preserve_forever INTEGER NOT NULL,
  UNIQUE(source_event_id)
);
CREATE INDEX IF NOT EXISTS ix_chronicle_event_1 ON chronicle_event(occurred_minute,id);
CREATE INDEX IF NOT EXISTS ix_chronicle_event_2 ON chronicle_event(importance);

CREATE TABLE IF NOT EXISTS chronicle_link (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  chronicle_event_id TEXT NOT NULL REFERENCES chronicle_event(id) ON DELETE RESTRICT,
  entity_kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  link_role TEXT NOT NULL,
  UNIQUE(chronicle_event_id,entity_kind,entity_id,link_role)
);
CREATE INDEX IF NOT EXISTS ix_chronicle_link_1 ON chronicle_link(entity_kind,entity_id);

CREATE TABLE IF NOT EXISTS chronicle_snapshot (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  entity_kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  year_no INTEGER NOT NULL,
  summary_json TEXT NOT NULL,
  source_hash TEXT NOT NULL,
  summary_version TEXT NOT NULL,
  UNIQUE(entity_kind,entity_id,year_no)
);

CREATE TABLE IF NOT EXISTS statistics_aggregate (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  projection_version TEXT NOT NULL,
  subject_kind TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  period_kind TEXT NOT NULL,
  period_key TEXT NOT NULL,
  metric_key TEXT NOT NULL,
  value INTEGER NOT NULL,
  UNIQUE(projection_version,subject_kind,subject_id,period_kind,period_key,metric_key)
);

CREATE TABLE IF NOT EXISTS aggregate_receipt (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  projector_key TEXT NOT NULL,
  projection_version TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  UNIQUE(projector_key,projection_version,source_event_id)
);

CREATE TABLE IF NOT EXISTS search_document (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  observer_id TEXT NOT NULL,
  entity_kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  title TEXT NOT NULL,
  public_text TEXT NOT NULL,
  sort_key TEXT NOT NULL,
  visibility_version INTEGER NOT NULL,
  UNIQUE(observer_id,entity_kind,entity_id)
);
CREATE INDEX IF NOT EXISTS ix_search_document_1 ON search_document(observer_id,sort_key,entity_id);

CREATE TABLE IF NOT EXISTS bookmark (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  observer_id TEXT NOT NULL,
  entity_kind TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  created_minute INTEGER NOT NULL,
  UNIQUE(observer_id,entity_kind,entity_id)
);

CREATE TABLE IF NOT EXISTS notification (
  id TEXT PRIMARY KEY NOT NULL,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK(row_version>=0),
  source_event_id TEXT NOT NULL,
  observer_id TEXT NOT NULL,
  priority TEXT NOT NULL,
  read INTEGER NOT NULL,
  batch_key TEXT,
  public_text TEXT NOT NULL,
  UNIQUE(observer_id,source_event_id)
);
CREATE INDEX IF NOT EXISTS ix_notification_1 ON notification(observer_id,read,priority);
