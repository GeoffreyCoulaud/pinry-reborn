-- apply changes
create table persons (
  id                            uuid not null,
  author_id                     uuid not null,
  when_created                  timestamp not null,
  name                          text not null,
  urls                          text not null,
  constraint pk_persons primary key (id),
  foreign key (author_id) references users (id) on delete restrict on update restrict
);

-- foreign keys and indices
create unique index ix_persons_author_name_nocase_urls on persons (author_id, name collate nocase, urls);
