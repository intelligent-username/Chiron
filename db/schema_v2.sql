-- Schema v2 (Chiron Database Version 14)

CREATE TABLE IF NOT EXISTS `exercise` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `image_uri` TEXT,
  `description` TEXT,
  `icon_name` TEXT DEFAULT 'default',
  `archived` INTEGER NOT NULL DEFAULT 0,
  `is_weight_based` INTEGER NOT NULL DEFAULT 1,
  `is_rep_based` INTEGER NOT NULL DEFAULT 1,
  `is_time_based` INTEGER NOT NULL DEFAULT 0,
  `is_distance_based` INTEGER NOT NULL DEFAULT 0,
  `is_bodyweight` INTEGER NOT NULL DEFAULT 0,
  `percent_bodyweight` REAL NOT NULL DEFAULT 100.0
);
CREATE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise`(`name`);

CREATE TABLE IF NOT EXISTS `workout_session` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `day_tag` TEXT NOT NULL,
  `date_iso` TEXT NOT NULL,
  `date_utc` INTEGER NOT NULL,
  `location_tag` TEXT NOT NULL,
  `notes` TEXT,
  `archived` INTEGER NOT NULL DEFAULT 0,
  `end_time_utc` INTEGER DEFAULT NULL
);
CREATE INDEX IF NOT EXISTS `index_workout_session_date_utc` ON `workout_session`(`date_utc`);

CREATE TABLE IF NOT EXISTS `exercise_entry` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `workout_id` INTEGER NOT NULL,
  `exercise_id` INTEGER NOT NULL,
  `slot_index` INTEGER NOT NULL,
  `group_id` INTEGER,
  `sequence_type` TEXT NOT NULL,
  `notes` TEXT,
  `archived` INTEGER NOT NULL DEFAULT 0,
  `num_exercises_in_superset` INTEGER NOT NULL DEFAULT 2,
  FOREIGN KEY(`workout_id`) REFERENCES `workout_session`(`id`) ON DELETE CASCADE,
  FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_entry_workout_id_slot_index` ON `exercise_entry`(`workout_id`, `slot_index`);
CREATE INDEX IF NOT EXISTS `index_exercise_entry_exercise_id` ON `exercise_entry`(`exercise_id`);

CREATE TABLE IF NOT EXISTS `set_entry` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `exercise_entry_id` INTEGER NOT NULL,
  `set_index` INTEGER NOT NULL,
  `weight_lbs` REAL,
  `reps` INTEGER,
  `duration_seconds` INTEGER DEFAULT NULL,
  `distance_meters` REAL DEFAULT NULL,
  `is_failed` INTEGER NOT NULL DEFAULT 0,
  `tempo` TEXT,
  `notes` TEXT,
  `timestamp_utc` INTEGER NOT NULL,
  `is_pr` INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(`exercise_entry_id`) REFERENCES `exercise_entry`(`id`) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_set_entry_exercise_entry_id_set_index` ON `set_entry`(`exercise_entry_id`, `set_index`);
CREATE INDEX IF NOT EXISTS `index_set_entry_exercise_entry_id` ON `set_entry`(`exercise_entry_id`);

CREATE TABLE IF NOT EXISTS `timer_presets` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `duration_seconds` INTEGER NOT NULL,
  `label` TEXT NOT NULL,
  `archived` INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS `exercise_pr` (
  `exercise_id` INTEGER NOT NULL,
  `bucket` REAL NOT NULL,
  `record` REAL NOT NULL,
  `set_id` INTEGER NOT NULL,
  `timestamp_utc` INTEGER NOT NULL,
  PRIMARY KEY(`exercise_id`, `bucket`),
  FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE,
  FOREIGN KEY(`set_id`) REFERENCES `set_entry`(`id`) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_exercise_pr_exercise_id` ON `exercise_pr`(`exercise_id`);
CREATE INDEX IF NOT EXISTS `index_exercise_pr_set_id` ON `exercise_pr`(`set_id`);

CREATE TABLE IF NOT EXISTS `exercise_1rm_estimate` (
  `exercise_id` INTEGER NOT NULL,
  `estimate_lbs` REAL NOT NULL,
  PRIMARY KEY(`exercise_id`),
  FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `goal` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `weekly_target` INTEGER NOT NULL,
  `archived` INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS `goal_exercise` (
  `goal_id` INTEGER NOT NULL,
  `exercise_id` INTEGER NOT NULL,
  PRIMARY KEY(`goal_id`, `exercise_id`),
  FOREIGN KEY(`goal_id`) REFERENCES `goal`(`id`) ON DELETE CASCADE,
  FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_goal_exercise_exercise_id` ON `goal_exercise`(`exercise_id`);

CREATE TABLE IF NOT EXISTS `body_weight_entry` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `timestamp_utc` INTEGER NOT NULL,
  `weight_lbs` REAL NOT NULL,
  `note` TEXT,
  `source` TEXT DEFAULT 'manual'
);
CREATE INDEX IF NOT EXISTS `index_body_weight_entry_timestamp_utc` ON `body_weight_entry`(`timestamp_utc`);
