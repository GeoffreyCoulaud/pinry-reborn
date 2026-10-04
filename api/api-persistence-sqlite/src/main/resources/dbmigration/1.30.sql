-- apply alter tables
alter table media add column frames integer default 1 not null;
alter table media add column duration_millis integer;
