-- apply changes
create table media_frame (
  id                            uuid not null,
  media_id                      uuid not null,
  hash_0                        integer not null,
  hash_1                        integer not null,
  hash_2                        integer not null,
  hash_3                        integer not null,
  constraint pk_media_frame primary key (id)
);

create table pin_duplicate (
  id                            uuid not null,
  first_pin_id                  uuid not null,
  second_pin_id                 uuid not null,
  rejected_at                   timestamp,
  constraint pk_pin_duplicate primary key (id)
);

-- apply alter tables
alter table media add column fingerprint_version integer;
-- foreign keys and indices
create index ix_media_frame_media on media_frame (media_id);
create index ix_media_frame_band_0 on media_frame (((hash_0 >> 48) & 65535));
create index ix_media_frame_band_1 on media_frame (((hash_0 >> 32) & 65535));
create index ix_media_frame_band_2 on media_frame (((hash_0 >> 16) & 65535));
create index ix_media_frame_band_3 on media_frame (((hash_0 >> 0) & 65535));
create index ix_media_frame_band_4 on media_frame (((hash_1 >> 48) & 65535));
create index ix_media_frame_band_5 on media_frame (((hash_1 >> 32) & 65535));
create index ix_media_frame_band_6 on media_frame (((hash_1 >> 16) & 65535));
create index ix_media_frame_band_7 on media_frame (((hash_1 >> 0) & 65535));
create index ix_media_frame_band_8 on media_frame (((hash_2 >> 48) & 65535));
create index ix_media_frame_band_9 on media_frame (((hash_2 >> 32) & 65535));
create index ix_media_frame_band_10 on media_frame (((hash_2 >> 16) & 65535));
create index ix_media_frame_band_11 on media_frame (((hash_2 >> 0) & 65535));
create index ix_media_frame_band_12 on media_frame (((hash_3 >> 48) & 65535));
create index ix_media_frame_band_13 on media_frame (((hash_3 >> 32) & 65535));
create index ix_media_frame_band_14 on media_frame (((hash_3 >> 16) & 65535));
create index ix_media_frame_band_15 on media_frame (((hash_3 >> 0) & 65535));
create unique index ux_pin_duplicate_pins on pin_duplicate (first_pin_id, second_pin_id);
create index ix_pin_duplicate_second_pin on pin_duplicate (second_pin_id);
