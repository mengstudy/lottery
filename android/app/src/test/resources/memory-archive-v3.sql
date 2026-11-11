CREATE TABLE IF NOT EXISTS collected_batches (
            id TEXT PRIMARY KEY, lottery_type TEXT NOT NULL, target_issue TEXT NOT NULL,
            origin TEXT NOT NULL, source_label TEXT NOT NULL, created_at_epoch INTEGER NOT NULL,
            snapshot_hash TEXT NOT NULL, parser_version TEXT NOT NULL, raw_text TEXT,
            groups_json TEXT NOT NULL, declared_kind TEXT NOT NULL, play_type TEXT,
            timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL,
            UNIQUE(lottery_type,target_issue,origin,snapshot_hash));

CREATE TABLE IF NOT EXISTS ai_request_archive (
            id TEXT PRIMARY KEY, session_id TEXT NOT NULL, lottery_type TEXT NOT NULL,
            target_issue TEXT NOT NULL, origin TEXT NOT NULL, status TEXT NOT NULL,
            timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL,
            created_at_epoch INTEGER NOT NULL, response_at_epoch INTEGER, updated_at_epoch INTEGER NOT NULL,
            provider_name TEXT NOT NULL, endpoint_host TEXT NOT NULL, model_name TEXT NOT NULL,
            temperature REAL NOT NULL, requested_count INTEGER NOT NULL, parsed_count INTEGER NOT NULL DEFAULT 0,
            accepted_count INTEGER NOT NULL DEFAULT 0, collection_batch_id TEXT,
            facts_json TEXT NOT NULL, prompt_version TEXT NOT NULL, rule_version TEXT NOT NULL,
            rejection_json TEXT NOT NULL DEFAULT '{}', finish_reason TEXT, failure_category TEXT);

CREATE TABLE IF NOT EXISTS ai_archive_payload (
            run_id TEXT PRIMARY KEY, raw_prompt TEXT, raw_response TEXT,
            prompt_truncated INTEGER NOT NULL DEFAULT 0, response_truncated INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(run_id) REFERENCES ai_request_archive(id));

CREATE TABLE IF NOT EXISTS recommendation_entries (
            id TEXT PRIMARY KEY, run_id TEXT, parent_entry_id TEXT, collection_batch_id TEXT,
            lottery_type TEXT NOT NULL, target_issue TEXT NOT NULL, stage TEXT NOT NULL,
            origin TEXT NOT NULL, ordinal INTEGER NOT NULL, accepted_index INTEGER,
            red_json TEXT, blue_json TEXT, kind TEXT NOT NULL, play_type TEXT, blue_source TEXT,
            rejection_reason TEXT, raw_line TEXT, timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL,
            created_at_epoch INTEGER NOT NULL, response_at_epoch INTEGER, edited_at_epoch INTEGER,
            confirmed_at_epoch INTEGER, prediction_id INTEGER UNIQUE,
            FOREIGN KEY(run_id) REFERENCES ai_request_archive(id),
            FOREIGN KEY(parent_entry_id) REFERENCES recommendation_entries(id),
            FOREIGN KEY(collection_batch_id) REFERENCES collected_batches(id));

CREATE UNIQUE INDEX IF NOT EXISTS idx_archive_run_ordinal ON recommendation_entries(run_id,ordinal) WHERE stage='AI_ORIGINAL';

CREATE INDEX IF NOT EXISTS idx_archive_type_time ON ai_request_archive(lottery_type,created_at_epoch DESC);

CREATE INDEX IF NOT EXISTS idx_archive_entry_target ON recommendation_entries(lottery_type,target_issue,stage);

CREATE INDEX IF NOT EXISTS idx_archive_batch_target ON collected_batches(lottery_type,target_issue);

CREATE VIEW IF NOT EXISTS accepted_ai_entries AS SELECT * FROM recommendation_entries
            WHERE stage='AI_ORIGINAL' AND accepted_index IS NOT NULL AND rejection_reason IS NULL;
