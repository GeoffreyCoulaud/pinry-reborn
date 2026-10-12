-- apply changes
create table boards (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  soft_deleted_at               timestamptz,
  name                          varchar collate case_insensitive not null,
  description                   varchar not null,
  constraint pk_boards primary key (id)
);

create table media_downloads (
  id                            uuid not null,
  pin_id                        uuid not null,
  task_id                       uuid not null,
  requested_at                  timestamptz not null,
  updated_at                    timestamptz not null,
  source_url                    varchar not null,
  status                        varchar not null,
  reason_code                   varchar,
  constraint pk_media_downloads primary key (id)
);

create table media_frames (
  id                            uuid not null,
  media_id                      uuid not null,
  hash_0                        bigint not null,
  hash_1                        bigint not null,
  hash_2                        bigint not null,
  hash_3                        bigint not null,
  constraint pk_media_frames primary key (id)
);

create table media (
  id                            uuid not null,
  pin_id                        uuid not null,
  width                         integer not null,
  height                        integer not null,
  byte_size                     bigint not null,
  created_at                    timestamptz not null,
  frames                        integer default 1 not null,
  duration_millis               bigint,
  video_bit_rate                bigint,
  audio_channels                integer,
  audio_bit_rate                bigint,
  fingerprint_version           integer,
  mime_type                     varchar not null,
  content_hash                  varchar not null,
  storage_key                   varchar not null,
  constraint ck_media_variant check (
    case when mime_type like 'video/%'
      then duration_millis is not null and video_bit_rate is not null
      else duration_millis is null and video_bit_rate is null and audio_channels is null
    end
    and (audio_channels is null) = (audio_bit_rate is null)),
  constraint uq_media_pin_id unique (pin_id),
  constraint pk_media primary key (id)
);

create table persons (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamptz not null,
  name                          varchar collate case_insensitive not null,
  urls                          varchar not null,
  constraint pk_persons primary key (id)
);

create table pin_boards (
  pin_id                        uuid not null,
  board_id                      uuid not null
);

create table pin_creators (
  pin_id                        uuid not null,
  person_id                     uuid not null
);

create table pin_duplicates (
  id                            uuid not null,
  first_pin_id                  uuid not null,
  second_pin_id                 uuid not null,
  rejected_at                   timestamptz,
  constraint ck_pin_duplicates_order check (first_pin_id < second_pin_id),
  constraint pk_pin_duplicates primary key (id)
);

create table pins (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  soft_deleted_at               timestamptz,
  publisher_id                  uuid,
  published_at                  timestamptz,
  source_context_url            varchar,
  source_media_url              varchar,
  description                   varchar collate case_insensitive not null,
  constraint pk_pins primary key (id)
);

create table pin_tags (
  pin_id                        uuid not null,
  tag_id                        uuid not null
);

create table remote_collections (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamptz not null,
  board_id                      uuid not null,
  url                           varchar not null,
  name                          varchar collate case_insensitive not null,
  constraint pk_remote_collections primary key (id)
);

create table session_tokens (
  id                            uuid not null,
  user_id                       uuid not null,
  expires_at                    timestamptz not null,
  persistent                    boolean default false not null,
  created_at                    timestamptz not null,
  token_hash                    varchar not null,
  constraint uq_session_tokens_token_hash unique (token_hash),
  constraint pk_session_tokens primary key (id)
);

create table tags (
  id                            uuid not null,
  author_id                     uuid not null,
  created_at                    timestamptz not null,
  name                          varchar collate case_insensitive not null,
  constraint pk_tags primary key (id)
);

create table tasks (
  id                            uuid not null,
  priority                      integer not null,
  available_at                  timestamptz not null,
  attempts                      integer not null,
  max_attempts                  integer not null,
  lease_expires_at              timestamptz,
  cancel_requested              boolean default false not null,
  terminal_state_at             timestamptz,
  version                       bigint not null,
  kind                          varchar not null,
  payload                       varchar not null,
  state                         varchar not null,
  lease_id                      varchar,
  dedup_key                     varchar,
  constraint ck_tasks_terminal_state_at check (
    (state in ('SUCCEEDED','DEAD','CANCELLED')) = (terminal_state_at is not null)),
  constraint pk_tasks primary key (id)
);

create table user_data_exports (
  id                            uuid not null,
  user_id                       uuid not null,
  format_version                integer not null,
  requested_at                  timestamptz not null,
  task_id                       uuid,
  completed_at                  timestamptz,
  expires_at                    timestamptz,
  archive_held                  boolean default false not null,
  byte_size                     bigint,
  state                         varchar not null,
  sha256                        varchar,
  failure_code                  varchar,
  constraint ck_user_data_exports_ready check (
    state <> 'READY' or (byte_size is not null and sha256 is not null and completed_at is not null)),
  constraint pk_user_data_exports primary key (id)
);

create table user_data_import_issues (
  id                            uuid not null,
  import_id                     uuid not null,
  line                          integer,
  kind                          varchar not null,
  subject                       varchar,
  detail                        varchar,
  constraint pk_user_data_import_issues primary key (id)
);

create table user_data_imports (
  id                            uuid not null,
  user_id                       uuid not null,
  requested_at                  timestamptz not null,
  last_activity_at              timestamptz not null,
  task_id                       uuid,
  run_token                     uuid,
  uploaded_bytes                bigint not null,
  archive_completed_at          timestamptz,
  started_at                    timestamptz,
  completed_at                  timestamptz,
  archive_held                  boolean default false not null,
  byte_size                     bigint,
  format_version                integer,
  announced_pins                integer,
  created_pins                  integer not null,
  skipped_pins                  integer not null,
  created_boards                integer not null,
  skipped_boards                integer not null,
  created_tags                  integer not null,
  skipped_tags                  integer not null,
  issue_count                   integer not null,
  issue_detail_truncated        boolean default false not null,
  state                         varchar not null,
  failure_code                  varchar,
  constraint pk_user_data_imports primary key (id)
);

create table users (
  id                            uuid not null,
  created_at                    timestamptz not null,
  soft_deleted_at               timestamptz,
  name                          varchar collate case_insensitive not null,
  constraint pk_users primary key (id)
);

create table user_password_hashes (
  id                            uuid not null,
  user_id                       uuid not null,
  created_at                    timestamptz not null,
  hash                          varchar not null,
  algorithm                     varchar(6) not null,
  constraint ck_user_password_hashes_algorithm check ( algorithm in ('BCRYPT')),
  constraint pk_user_password_hashes primary key (id)
);

-- foreign keys and indices
alter table boards add constraint fk_boards_author_id foreign key (author_id) references users (id) on delete cascade on update restrict;

alter table media_downloads add constraint fk_media_downloads_pin_id foreign key (pin_id) references pins (id) on delete cascade on update restrict;

alter table media_frames add constraint fk_media_frames_media_id foreign key (media_id) references media (id) on delete cascade on update restrict;

alter table media add constraint fk_media_pin_id foreign key (pin_id) references pins (id) on delete cascade on update restrict;

alter table persons add constraint fk_persons_author_id foreign key (author_id) references users (id) on delete cascade on update restrict;

alter table pin_boards add constraint fk_pin_boards_pin_id foreign key (pin_id) references pins (id) on delete cascade on update restrict;

alter table pin_boards add constraint fk_pin_boards_board_id foreign key (board_id) references boards (id) on delete cascade on update restrict;

alter table pin_creators add constraint fk_pin_creators_pin_id foreign key (pin_id) references pins (id) on delete cascade on update restrict;

alter table pin_creators add constraint fk_pin_creators_person_id foreign key (person_id) references persons (id) on delete cascade on update restrict;

alter table pin_duplicates add constraint fk_pin_duplicates_first_pin_id foreign key (first_pin_id) references pins (id) on delete cascade on update restrict;

alter table pin_duplicates add constraint fk_pin_duplicates_second_pin_id foreign key (second_pin_id) references pins (id) on delete cascade on update restrict;

alter table pins add constraint fk_pins_author_id foreign key (author_id) references users (id) on delete cascade on update restrict;

alter table pins add constraint fk_pins_publisher_id foreign key (publisher_id) references persons (id) on delete set null on update restrict;

alter table pin_tags add constraint fk_pin_tags_pin_id foreign key (pin_id) references pins (id) on delete cascade on update restrict;

alter table pin_tags add constraint fk_pin_tags_tag_id foreign key (tag_id) references tags (id) on delete cascade on update restrict;

alter table remote_collections add constraint fk_remote_collections_author_id foreign key (author_id) references users (id) on delete cascade on update restrict;

alter table remote_collections add constraint fk_remote_collections_board_id foreign key (board_id) references boards (id) on delete cascade on update restrict;

alter table session_tokens add constraint fk_session_tokens_user_id foreign key (user_id) references users (id) on delete cascade on update restrict;

alter table tags add constraint fk_tags_author_id foreign key (author_id) references users (id) on delete cascade on update restrict;

alter table user_data_exports add constraint fk_user_data_exports_user_id foreign key (user_id) references users (id) on delete cascade on update restrict;

alter table user_data_import_issues add constraint fk_user_data_import_issues_import_id foreign key (import_id) references user_data_imports (id) on delete cascade on update restrict;

alter table user_data_imports add constraint fk_user_data_imports_user_id foreign key (user_id) references users (id) on delete cascade on update restrict;

alter table user_password_hashes add constraint fk_user_password_hashes_user_id foreign key (user_id) references users (id) on delete cascade on update restrict;

create unique index ux_boards_author_name on boards (author_id, name);
create unique index ux_media_downloads_pin on media_downloads (pin_id);
create index if not exists ix_media_frames_media on media_frames (media_id);
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
create index if not exists ix_media_content_hash on media (content_hash);
create unique index ux_persons_author_name_urls on persons (author_id, name, urls);
create unique index ux_pin_boards_pin_board on pin_boards (pin_id, board_id);
create index if not exists ix_pin_boards_board on pin_boards (board_id);
create unique index ux_pin_creators_pin_person on pin_creators (pin_id, person_id);
create index if not exists ix_pin_creators_person on pin_creators (person_id);
create unique index ux_pin_duplicates_pins on pin_duplicates (first_pin_id, second_pin_id);
create index if not exists ix_pin_duplicates_second_pin on pin_duplicates (second_pin_id);
create index if not exists ix_pins_author on pins (author_id);
create index if not exists ix_pins_publisher on pins (publisher_id);
create unique index ux_pin_tags_pin_tag on pin_tags (pin_id, tag_id);
create index if not exists ix_pin_tags_tag on pin_tags (tag_id);
create unique index ux_remote_collections_author_url on remote_collections (author_id, url);
create index if not exists ix_remote_collections_board on remote_collections (board_id);
create index if not exists ix_session_tokens_expires_at on session_tokens (expires_at);
create index if not exists ix_session_tokens_user on session_tokens (user_id);
create unique index ux_tags_author_name on tags (author_id, name);
create index if not exists ix_tasks_state_terminal_state_at on tasks (state,terminal_state_at);
create index ix_tasks_claim on tasks (state, priority desc, available_at asc, id asc);
create unique index ux_tasks_dedup on tasks (dedup_key) where dedup_key is not null and state in ('PENDING','RUNNING');
create unique index uq_user_data_exports_pending on user_data_exports (user_id) where state = 'PENDING';
create index if not exists ix_user_data_exports_user on user_data_exports (user_id);
create index if not exists ix_user_data_import_issues_import on user_data_import_issues (import_id);
create unique index uq_user_data_imports_active on user_data_imports (user_id) where state in ('AWAITING_ARCHIVE','PENDING','RUNNING');
create index if not exists ix_user_data_imports_user_state on user_data_imports (user_id,state);
create unique index ux_users_name on users (name);
create unique index ix_user_password_hashes_user_created on user_password_hashes (user_id, created_at);
