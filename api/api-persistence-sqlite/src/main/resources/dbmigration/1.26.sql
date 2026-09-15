-- apply changes
-- The fill runs before the rebuild copies it, as `1.24.sql` does, so the copy's `select` names
-- `last_activity_at` on both sides and carries no expression. A row that never received a chunk
-- takes its request instant, which is what the sweep's `coalesce` used to compute at read time.
alter table user_data_imports add column last_activity_at timestamp;
update user_data_imports set last_activity_at = coalesce(last_upload_activity_at, requested_at);

-- apply alter tables
-- SQLite neither drops a column nor adds a not-null constraint in place. The generator renders
-- none of this either: it refuses a non-null column with no default, and stops before writing a
-- script at all, so the model file beside this one is all it produced.
create table user_data_imports_tmp_rebuild (
  id                            uuid not null,
  user_id                       uuid not null,
  state                         text not null,
  requested_at                  timestamp not null,
  last_activity_at              timestamp not null,
  task_id                       uuid,
  run_token                     uuid,
  uploaded_bytes                integer not null,
  archive_completed_at          timestamp,
  started_at                    timestamp,
  completed_at                  timestamp,
  storage_key                   text,
  byte_size                     integer,
  format_version                integer,
  announced_pins                integer,
  processed_pins                integer not null,
  created_pins                  integer not null,
  skipped_pins                  integer not null,
  created_boards                integer not null,
  skipped_boards                integer not null,
  created_tags                  integer not null,
  skipped_tags                  integer not null,
  issue_count                   integer not null,
  issue_detail_truncated        int default 0 not null,
  failure_code                  text,
  constraint pk_user_data_imports primary key (id),
  foreign key (user_id) references users (id) on delete restrict on update restrict
);
insert into user_data_imports_tmp_rebuild (id, user_id, state, requested_at, last_activity_at, task_id, run_token, uploaded_bytes, archive_completed_at, started_at, completed_at, storage_key, byte_size, format_version, announced_pins, processed_pins, created_pins, skipped_pins, created_boards, skipped_boards, created_tags, skipped_tags, issue_count, issue_detail_truncated, failure_code)
  select id, user_id, state, requested_at, last_activity_at, task_id, run_token, uploaded_bytes, archive_completed_at, started_at, completed_at, storage_key, byte_size, format_version, announced_pins, processed_pins, created_pins, skipped_pins, created_boards, skipped_boards, created_tags, skipped_tags, issue_count, issue_detail_truncated, failure_code
  from user_data_imports;
drop table user_data_imports;
alter table user_data_imports_tmp_rebuild rename to user_data_imports;

-- foreign keys and indices
-- Dropping the table dropped both of its indexes (`1.21.sql:46,47`); `MigratedSchemaIndexesTest`
-- is what sees a rebuild that forgets to put them back.
create unique index uq_user_data_imports_active on user_data_imports (user_id) where state in ('AWAITING_ARCHIVE','PENDING','RUNNING');
create index ix_user_data_imports_user_state on user_data_imports (user_id,state);
