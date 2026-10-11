-- apply changes
create table boards (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamp not null,
  name                          text not null,
  description                   text not null,
  updated_at                    timestamp not null,
  soft_deleted_at               timestamp,
  constraint pk_boards primary key (id),
  foreign key (author_id) references users (id) on delete cascade on update restrict
);

create table media_downloads (
  id                            uuid not null,
  pin_id                        uuid not null,
  source_url                    text not null,
  status                        text not null,
  reason_code                   text,
  task_id                       uuid not null,
  requested_at                  timestamp not null,
  updated_at                    timestamp not null,
  constraint pk_media_downloads primary key (id),
  foreign key (pin_id) references pins (id) on delete cascade on update restrict
);

create table media_frames (
  id                            uuid not null,
  media_id                      uuid not null,
  hash_0                        integer not null,
  hash_1                        integer not null,
  hash_2                        integer not null,
  hash_3                        integer not null,
  constraint pk_media_frames primary key (id),
  foreign key (media_id) references media (id) on delete cascade on update restrict
);

create table media (
  id                            uuid not null,
  pin_id                        uuid not null,
  mime_type                     text not null,
  width                         integer not null,
  height                        integer not null,
  byte_size                     integer not null,
  content_hash                  text not null,
  storage_key                   text not null,
  created_at                    timestamp not null,
  frames                        integer default 1 not null,
  duration_millis               integer,
  video_bit_rate                integer,
  audio_channels                integer,
  audio_bit_rate                integer,
  fingerprint_version           integer,
  constraint uq_media_pin_id unique (pin_id),
  constraint pk_media primary key (id),
  foreign key (pin_id) references pins (id) on delete cascade on update restrict
);

create table persons (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamp not null,
  name                          text not null,
  urls                          text not null,
  constraint pk_persons primary key (id),
  foreign key (author_id) references users (id) on delete cascade on update restrict
);

create table pin_boards (
  pin_id                        uuid not null,
  board_id                      uuid not null,
  foreign key (pin_id) references pins (id) on delete cascade on update restrict,
  foreign key (board_id) references boards (id) on delete cascade on update restrict
);

create table pin_creators (
  pin_id                        uuid not null,
  person_id                     uuid not null,
  foreign key (pin_id) references pins (id) on delete cascade on update restrict,
  foreign key (person_id) references persons (id) on delete cascade on update restrict
);

create table pin_duplicates (
  id                            uuid not null,
  first_pin_id                  uuid not null,
  second_pin_id                 uuid not null,
  rejected_at                   timestamp,
  constraint pk_pin_duplicates primary key (id),
  foreign key (first_pin_id) references pins (id) on delete cascade on update restrict,
  foreign key (second_pin_id) references pins (id) on delete cascade on update restrict
);

create table pins (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamp not null,
  source_context_url            text,
  source_media_url              text,
  description                   text not null,
  updated_at                    timestamp not null,
  soft_deleted_at               timestamp,
  publisher_id                  uuid,
  published_at                  timestamp,
  constraint pk_pins primary key (id),
  foreign key (author_id) references users (id) on delete cascade on update restrict,
  foreign key (publisher_id) references persons (id) on delete set null on update restrict
);

create table pin_tags (
  pin_id                        uuid not null,
  tag_id                        uuid not null,
  foreign key (pin_id) references pins (id) on delete cascade on update restrict,
  foreign key (tag_id) references tags (id) on delete cascade on update restrict
);

create table remote_collections (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamp not null,
  url                           text not null,
  name                          text not null,
  board_id                      uuid not null,
  constraint pk_remote_collections primary key (id),
  foreign key (author_id) references users (id) on delete cascade on update restrict,
  foreign key (board_id) references boards (id) on delete cascade on update restrict
);

create table session_tokens (
  id                            uuid not null,
  user_id                       uuid not null,
  token_hash                    text not null,
  expires_at                    timestamp not null,
  persistent                    int default 0 not null,
  created_at                    timestamp not null,
  constraint uq_session_tokens_token_hash unique (token_hash),
  constraint pk_session_tokens primary key (id),
  foreign key (user_id) references users (id) on delete cascade on update restrict
);

create table tags (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamp not null,
  name                          text not null,
  constraint pk_tags primary key (id),
  foreign key (author_id) references users (id) on delete cascade on update restrict
);

create table tasks (
  id                            uuid not null,
  kind                          text not null,
  payload                       text not null,
  state                         text not null,
  priority                      integer not null,
  available_at                  timestamp not null,
  attempts                      integer not null,
  max_attempts                  integer not null,
  lease_id                      text,
  lease_expires_at              timestamp,
  cancel_requested              int default 0 not null,
  dedup_key                     text,
  terminal_state_at             timestamp,
  version                       integer not null,
  constraint pk_tasks primary key (id)
);

create table user_data_exports (
  id                            uuid not null,
  user_id                       uuid not null,
  state                         text not null,
  format_version                integer not null,
  requested_at                  timestamp not null,
  task_id                       uuid,
  completed_at                  timestamp,
  expires_at                    timestamp,
  storage_key                   text,
  byte_size                     integer,
  sha256                        text,
  media_type                    text,
  file_extension                text,
  failure_code                  text,
  constraint pk_user_data_exports primary key (id),
  foreign key (user_id) references users (id) on delete cascade on update restrict
);

create table user_data_import_issues (
  id                            uuid not null,
  import_id                     uuid not null,
  kind                          text not null,
  line                          integer,
  subject                       text,
  detail                        text,
  constraint pk_user_data_import_issues primary key (id),
  foreign key (import_id) references user_data_imports (id) on delete cascade on update restrict
);

create table user_data_imports (
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
  foreign key (user_id) references users (id) on delete cascade on update restrict
);

create table users (
  id                            uuid not null,
  name                          text not null,
  created_at                    timestamp not null,
  soft_deleted_at               timestamp,
  constraint pk_users primary key (id)
);

create table user_password_hashes (
  id                            uuid not null,
  user_id                       uuid not null,
  hash                          text not null,
  algorithm                     text(6) not null,
  created_at                    timestamp not null,
  constraint ck_user_password_hashes_algorithm check ( algorithm in ('BCRYPT')),
  constraint pk_user_password_hashes primary key (id),
  foreign key (user_id) references users (id) on delete cascade on update restrict
);

-- foreign keys and indices
create unique index ix_boards_author_name_nocase on boards (author_id, name collate nocase);
create unique index ux_media_downloads_pin on media_downloads (pin_id);
create index ix_media_frames_media on media_frames (media_id);
create index ix_media_frames_band_0 on media_frames (((hash_0 >> 48) & 65535));
create index ix_media_frames_band_1 on media_frames (((hash_0 >> 32) & 65535));
create index ix_media_frames_band_2 on media_frames (((hash_0 >> 16) & 65535));
create index ix_media_frames_band_3 on media_frames (((hash_0 >> 0) & 65535));
create index ix_media_frames_band_4 on media_frames (((hash_1 >> 48) & 65535));
create index ix_media_frames_band_5 on media_frames (((hash_1 >> 32) & 65535));
create index ix_media_frames_band_6 on media_frames (((hash_1 >> 16) & 65535));
create index ix_media_frames_band_7 on media_frames (((hash_1 >> 0) & 65535));
create index ix_media_frames_band_8 on media_frames (((hash_2 >> 48) & 65535));
create index ix_media_frames_band_9 on media_frames (((hash_2 >> 32) & 65535));
create index ix_media_frames_band_10 on media_frames (((hash_2 >> 16) & 65535));
create index ix_media_frames_band_11 on media_frames (((hash_2 >> 0) & 65535));
create index ix_media_frames_band_12 on media_frames (((hash_3 >> 48) & 65535));
create index ix_media_frames_band_13 on media_frames (((hash_3 >> 32) & 65535));
create index ix_media_frames_band_14 on media_frames (((hash_3 >> 16) & 65535));
create index ix_media_frames_band_15 on media_frames (((hash_3 >> 0) & 65535));
create index ix_media_content_hash on media (content_hash);
create unique index ix_persons_author_name_nocase_urls on persons (author_id, name collate nocase, urls);
create unique index ux_pin_boards_pin_board on pin_boards (pin_id, board_id);
create index ix_pin_boards_board on pin_boards (board_id);
create unique index ux_pin_creators_pin_person on pin_creators (pin_id, person_id);
create index ix_pin_creators_person on pin_creators (person_id);
create unique index ux_pin_duplicates_pins on pin_duplicates (first_pin_id, second_pin_id);
create index ix_pin_duplicates_second_pin on pin_duplicates (second_pin_id);
create index ix_pins_author on pins (author_id);
create index ix_pins_publisher on pins (publisher_id);
create unique index ux_pin_tags_pin_tag on pin_tags (pin_id, tag_id);
create index ix_pin_tags_tag on pin_tags (tag_id);
create unique index ux_remote_collections_author_url on remote_collections (author_id, url);
create index ix_remote_collections_board on remote_collections (board_id);
create index ix_session_tokens_expires_at on session_tokens (expires_at);
create index ix_session_tokens_user on session_tokens (user_id);
create unique index ix_tags_author_name_nocase on tags (author_id, name collate nocase);
create index ix_tasks_state_terminal_state_at on tasks (state,terminal_state_at);
create index ix_tasks_claim on tasks (state, priority desc, available_at asc, id asc);
create unique index ux_tasks_dedup on tasks (dedup_key) where dedup_key is not null and state in ('PENDING','RUNNING');
create unique index uq_user_data_exports_pending on user_data_exports (user_id) where state = 'PENDING';
create index ix_user_data_exports_user on user_data_exports (user_id);
create index ix_user_data_import_issues_import on user_data_import_issues (import_id);
create unique index uq_user_data_imports_active on user_data_imports (user_id) where state in ('AWAITING_ARCHIVE','PENDING','RUNNING');
create index ix_user_data_imports_user_state on user_data_imports (user_id,state);
create unique index ix_users_name_nocase on users (name collate nocase);
create unique index ix_user_password_hashes_user_created on user_password_hashes (user_id, created_at);
