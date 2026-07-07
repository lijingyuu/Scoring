DROP TABLE IF EXISTS match_report_meta;
DROP TABLE IF EXISTS match_theme_config;
DROP TABLE IF EXISTS match_lineup_config;
DROP TABLE IF EXISTS match_record;
DROP TABLE IF EXISTS tournament_referee_grant;
DROP TABLE IF EXISTS tournament_referee_config;
DROP TABLE IF EXISTS tournament_team_member;
DROP TABLE IF EXISTS player;
DROP TABLE IF EXISTS tournament_favorite;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS tournament;

CREATE TABLE tournament (
  id VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  location VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 0,
  sport_type TINYINT NOT NULL DEFAULT 0,
  tournament_type TINYINT NOT NULL DEFAULT 0,
  group_size INT,
  knockout_slots INT,
  qualifiers_per_group INT,
  current_stage TINYINT NOT NULL DEFAULT 1,
  knockout_generated BOOLEAN NOT NULL DEFAULT TRUE,
  best_of INT NOT NULL DEFAULT 3,
  games_to_win INT NOT NULL DEFAULT 2,
  points_to_win INT NOT NULL DEFAULT 21,
  enable_deuce BOOLEAN NOT NULL DEFAULT TRUE,
  cap_point INT NOT NULL DEFAULT 30,
  round_robin_rounds TINYINT NOT NULL DEFAULT 1,
  creator_user_id VARCHAR(32) NOT NULL,
  favorite_count INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE app_user (
  id VARCHAR(32) NOT NULL,
  openid VARCHAR(64) NOT NULL,
  nickname VARCHAR(64),
  avatar_url VARCHAR(512),
  profile_completed BOOLEAN NOT NULL DEFAULT FALSE,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_openid UNIQUE (openid)
);

CREATE TABLE tournament_favorite (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_favorite_user_tournament UNIQUE (user_id, tournament_id)
);

CREATE INDEX idx_favorite_tournament_id ON tournament_favorite (tournament_id);

CREATE TABLE tournament_referee_config (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_referee_config_tournament UNIQUE (tournament_id)
);

CREATE TABLE tournament_referee_grant (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_referee_grant_tournament_user UNIQUE (tournament_id, user_id)
);

CREATE INDEX idx_referee_grant_user_id ON tournament_referee_grant (user_id);

CREATE TABLE player (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  seed_rank INT,
  group_no INT,
  group_position INT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX idx_player_tournament_id ON player (tournament_id);

CREATE TABLE tournament_team_member (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  participant_id VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  jersey_number INT NOT NULL,
  is_libero BOOLEAN NOT NULL DEFAULT FALSE,
  is_captain BOOLEAN NOT NULL DEFAULT FALSE,
  display_order INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX idx_team_member_tournament_id ON tournament_team_member (tournament_id);
CREATE INDEX idx_team_member_participant_id ON tournament_team_member (participant_id);

CREATE TABLE match_record (
  id VARCHAR(32) NOT NULL,
  tournament_id VARCHAR(32) NOT NULL,
  round_num INT NOT NULL,
  match_index INT NOT NULL DEFAULT 0,
  stage_type TINYINT NOT NULL DEFAULT 1,
  group_no INT,
  left_player_id VARCHAR(32),
  right_player_id VARCHAR(32),
  score_display VARCHAR(255),
  winner_id VARCHAR(32),
  left_game_wins INT,
  right_game_wins INT,
  game_scores CLOB,
  status TINYINT NOT NULL DEFAULT 0,
  next_match_id VARCHAR(32),
  next_match_slot VARCHAR(10),
  retired_side VARCHAR(10),
  PRIMARY KEY (id)
);

CREATE INDEX idx_match_tournament_id ON match_record (tournament_id);
CREATE INDEX idx_match_next_match_id ON match_record (next_match_id);

CREATE TABLE match_lineup_config (
  id VARCHAR(32) NOT NULL,
  match_id VARCHAR(32) NOT NULL,
  game_no INT NOT NULL,
  left_court_json CLOB NOT NULL,
  right_court_json CLOB NOT NULL,
  left_middle_pair_indexes_json VARCHAR(64) NOT NULL,
  right_middle_pair_indexes_json VARCHAR(64) NOT NULL,
  left_libero1_id VARCHAR(32),
  left_libero2_id VARCHAR(32),
  right_libero1_id VARCHAR(32),
  right_libero2_id VARCHAR(32),
  serve_side VARCHAR(10) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_lineup_match_game UNIQUE (match_id, game_no)
);

CREATE TABLE match_theme_config (
  id VARCHAR(32) NOT NULL,
  match_id VARCHAR(32) NOT NULL,
  theme_json CLOB NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_match_theme_match UNIQUE (match_id)
);

CREATE TABLE match_report_meta (
  id VARCHAR(32) NOT NULL,
  match_id VARCHAR(32) NOT NULL,
  meta_json CLOB NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_match_report_meta_match UNIQUE (match_id)
);

CREATE TABLE match_event (
  id VARCHAR(32) NOT NULL,
  match_id VARCHAR(32) NOT NULL,
  event_seq INT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  game_no INT NOT NULL,
  left_score INT NOT NULL,
  right_score INT NOT NULL,
  serve_side VARCHAR(10) NOT NULL,
  payload_json CLOB NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_match_event_seq UNIQUE (match_id, event_seq)
);

CREATE INDEX idx_match_event_match_id ON match_event (match_id);
