-- apply changes
-- The new key is filled before the rebuild copies it, so the copy names `id` on both sides like
-- every other rebuild. An existing row takes its own `pin_id`: SQLite has no uuid generator, and
-- `pin_id` was the primary key, so it is unique already and in whatever form the driver stores.
alter table image_download add column id uuid;
update image_download set id = pin_id;

-- apply alter tables
-- SQLite cannot turn a primary key column into a plain one (the generator left a "not supported"
-- placeholder here). Rebuild `image_download` on the surrogate `id` the cursor pagination pivots
-- on, keeping `pin_id` unique and its foreign key.
create table image_download_tmp_rebuild (
  id                            uuid not null,
  pin_id                        uuid not null,
  source_url                    text not null,
  status                        text not null,
  reason_code                   text,
  last_error                    text,
  task_id                       uuid not null,
  requested_at                  timestamp not null,
  updated_at                    timestamp not null,
  constraint pk_image_download primary key (id),
  foreign key (pin_id) references pins (id) on delete restrict on update restrict
);
insert into image_download_tmp_rebuild (id, pin_id, source_url, status, reason_code, last_error, task_id, requested_at, updated_at)
  select id, pin_id, source_url, status, reason_code, last_error, task_id, requested_at, updated_at
  from image_download;
drop table image_download;
alter table image_download_tmp_rebuild rename to image_download;

-- foreign keys and indices
create unique index ux_image_download_pin on image_download (pin_id);
