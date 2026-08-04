CREATE TABLE tournament_qualification_override (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  group_no INT NOT NULL,
  rank_slot INT NOT NULL,
  player_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_qualification_override_slot UNIQUE (tournament_id, group_no, rank_slot),
  CONSTRAINT uk_qualification_override_player UNIQUE (tournament_id, group_no, player_id)
);

CREATE INDEX idx_qualification_override_tournament
  ON tournament_qualification_override (tournament_id);
