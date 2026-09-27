-- apply changes
-- Written by hand, the generator knowing nothing of the rows: a pair repeated before the unique
-- indexes existed keeps its first copy, and the indexes below then refuse a second one.
delete from pin_board_model where rowid not in (select min(rowid) from pin_board_model group by pin_id, board_id);
delete from pin_tag_model where rowid not in (select min(rowid) from pin_tag_model group by pin_id, tag_id);

-- foreign keys and indices
create unique index ux_pin_board_model_pin_board on pin_board_model (pin_id, board_id);
create index ix_pin_board_model_board on pin_board_model (board_id);
create unique index ux_pin_tag_model_pin_tag on pin_tag_model (pin_id, tag_id);
create index ix_pin_tag_model_tag on pin_tag_model (tag_id);
