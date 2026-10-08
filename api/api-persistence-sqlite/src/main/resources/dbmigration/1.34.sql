-- apply changes
create table pin_creator_model (
  pin_id                        uuid not null,
  person_id                     uuid not null,
  foreign key (pin_id) references pins (id) on delete restrict on update restrict,
  foreign key (person_id) references persons (id) on delete restrict on update restrict
);

-- apply alter tables
-- The key is inline: SQLite cannot add a constraint, the generator's placeholder said, but takes it on a new column.
alter table pins add column publisher_id uuid references persons (id) on delete restrict on update restrict;
alter table pins add column published_at timestamp;
-- foreign keys and indices

create unique index ux_pin_creator_model_pin_person on pin_creator_model (pin_id, person_id);
create index ix_pin_creator_model_person on pin_creator_model (person_id);
