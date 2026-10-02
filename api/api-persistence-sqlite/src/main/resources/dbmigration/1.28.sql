-- drop dependencies
-- The generator's two "not supported" foreign key drops are removed: `1.29__dropsFor_1.28` drops both tables.
drop index if exists ix_images_content_hash;
drop index if exists ux_image_download_pin;
-- apply changes
create table media_download (
  id                            uuid not null,
  pin_id                        uuid not null,
  source_url                    text not null,
  status                        text not null,
  reason_code                   text,
  last_error                    text,
  task_id                       uuid not null,
  requested_at                  timestamp not null,
  updated_at                    timestamp not null,
  constraint pk_media_download primary key (id),
  foreign key (pin_id) references pins (id) on delete restrict on update restrict
);

create table media (
  id                            uuid not null,
  pin_id                        uuid not null,
  mime_type                     text not null,
  width                         integer not null,
  height                        integer not null,
  animated                      int default 0 not null,
  byte_size                     integer not null,
  content_hash                  text not null,
  storage_key                   text not null,
  created_at                    timestamp not null,
  constraint uq_media_pin_id unique (pin_id),
  constraint pk_media primary key (id),
  foreign key (pin_id) references pins (id) on delete restrict on update restrict
);

-- foreign keys and indices
create unique index ux_media_download_pin on media_download (pin_id);
create index ix_media_content_hash on media (content_hash);
