-- apply alter tables
-- SQLite cannot alter a column's NOT NULL constraint in place (the generator left a
-- "not supported" placeholder here). Rebuild `pins` with `source_context_url` made nullable,
-- preserving every existing column, the primary key, and the `author_id` foreign key. Existing
-- rows are copied as they stand: nothing backfills and nothing blanks.
create table pins_tmp_rebuild (
  id                            uuid not null,
  author_id                     uuid not null,
  source_context_url            text,
  source_media_url              text,
  description                   text not null,
  when_created                  timestamp not null,
  when_modified                 timestamp not null,
  soft_deleted_at               timestamp,
  constraint pk_pins primary key (id),
  foreign key (author_id) references users (id) on delete restrict on update restrict
);
insert into pins_tmp_rebuild (id, author_id, source_context_url, source_media_url, description, when_created, when_modified, soft_deleted_at)
  select id, author_id, source_context_url, source_media_url, description, when_created, when_modified, soft_deleted_at
  from pins;
drop table pins;
alter table pins_tmp_rebuild rename to pins;
