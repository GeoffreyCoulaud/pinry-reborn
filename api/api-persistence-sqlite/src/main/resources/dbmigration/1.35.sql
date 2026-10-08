-- apply changes
create table remote_collections (
  id                            uuid not null,
  author_id                     uuid not null,
  when_created                  timestamp not null,
  url                           text not null,
  name                          text not null,
  board_id                      uuid not null,
  constraint pk_remote_collections primary key (id),
  foreign key (author_id) references users (id) on delete restrict on update restrict,
  foreign key (board_id) references boards (id) on delete restrict on update restrict
);

-- foreign keys and indices
create unique index ux_remote_collections_author_url on remote_collections (author_id, url);
create index ix_remote_collections_board on remote_collections (board_id);
