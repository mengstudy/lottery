package com.lottery.analyzer.data.db

object MemoryArchiveSchema {
    val additions = mapOf(
        "collected_batches" to mapOf("baseline_seed" to "INTEGER", "evaluation_protocol" to "TEXT"),
        "ai_request_archive" to mapOf("baseline_seed" to "INTEGER", "evaluation_protocol" to "TEXT"),
        "recommendation_entries" to mapOf("strategy_id" to "TEXT", "candidate_id" to "TEXT", "baseline_order" to "INTEGER",
            "baseline_seed" to "INTEGER", "evaluation_protocol" to "TEXT", "policy_key" to "TEXT")
    )
    fun upgrades(table: String, columns: Set<String>): List<String> = additions[table].orEmpty()
        .filterKeys { it !in columns }.map { (name, definition) -> "ALTER TABLE $table ADD COLUMN $name $definition" }
    val statements = listOf(
        """CREATE TABLE IF NOT EXISTS collected_batches (
            id TEXT PRIMARY KEY, lottery_type TEXT NOT NULL, target_issue TEXT NOT NULL,
            origin TEXT NOT NULL, source_label TEXT NOT NULL, created_at_epoch INTEGER NOT NULL,
            snapshot_hash TEXT NOT NULL, parser_version TEXT NOT NULL, raw_text TEXT,
            groups_json TEXT NOT NULL, declared_kind TEXT NOT NULL, play_type TEXT,
            timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL, baseline_seed INTEGER, evaluation_protocol TEXT,
            UNIQUE(lottery_type,target_issue,origin,snapshot_hash))""",
        """CREATE TABLE IF NOT EXISTS ai_request_archive (
            id TEXT PRIMARY KEY, session_id TEXT NOT NULL, lottery_type TEXT NOT NULL,
            target_issue TEXT NOT NULL, origin TEXT NOT NULL, status TEXT NOT NULL,
            timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL,
            created_at_epoch INTEGER NOT NULL, response_at_epoch INTEGER, updated_at_epoch INTEGER NOT NULL,
            provider_name TEXT NOT NULL, endpoint_host TEXT NOT NULL, model_name TEXT NOT NULL,
            temperature REAL NOT NULL, requested_count INTEGER NOT NULL, parsed_count INTEGER NOT NULL DEFAULT 0,
            accepted_count INTEGER NOT NULL DEFAULT 0, collection_batch_id TEXT,
            facts_json TEXT NOT NULL, prompt_version TEXT NOT NULL, rule_version TEXT NOT NULL,
            rejection_json TEXT NOT NULL DEFAULT '{}', finish_reason TEXT, failure_category TEXT,
            baseline_seed INTEGER, evaluation_protocol TEXT)""",
        """CREATE TABLE IF NOT EXISTS ai_archive_payload (
            run_id TEXT PRIMARY KEY, raw_prompt TEXT, raw_response TEXT,
            prompt_truncated INTEGER NOT NULL DEFAULT 0, response_truncated INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(run_id) REFERENCES ai_request_archive(id))""",
        """CREATE TABLE IF NOT EXISTS recommendation_entries (
            id TEXT PRIMARY KEY, run_id TEXT, parent_entry_id TEXT, collection_batch_id TEXT,
            lottery_type TEXT NOT NULL, target_issue TEXT NOT NULL, stage TEXT NOT NULL,
            origin TEXT NOT NULL, ordinal INTEGER NOT NULL, accepted_index INTEGER,
            red_json TEXT, blue_json TEXT, kind TEXT NOT NULL, play_type TEXT, blue_source TEXT,
            rejection_reason TEXT, raw_line TEXT, timing_status TEXT NOT NULL, timing_evidence TEXT NOT NULL,
            created_at_epoch INTEGER NOT NULL, response_at_epoch INTEGER, edited_at_epoch INTEGER,
            confirmed_at_epoch INTEGER, prediction_id INTEGER UNIQUE, strategy_id TEXT, candidate_id TEXT,
            baseline_order INTEGER, baseline_seed INTEGER, evaluation_protocol TEXT, policy_key TEXT,
            FOREIGN KEY(run_id) REFERENCES ai_request_archive(id),
            FOREIGN KEY(parent_entry_id) REFERENCES recommendation_entries(id),
            FOREIGN KEY(collection_batch_id) REFERENCES collected_batches(id))""",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_archive_run_ordinal ON recommendation_entries(run_id,ordinal) WHERE stage='AI_ORIGINAL'",
        "CREATE INDEX IF NOT EXISTS idx_archive_type_time ON ai_request_archive(lottery_type,created_at_epoch DESC)",
        "CREATE INDEX IF NOT EXISTS idx_archive_entry_target ON recommendation_entries(lottery_type,target_issue,stage)",
        "CREATE INDEX IF NOT EXISTS idx_archive_batch_target ON collected_batches(lottery_type,target_issue)",
        """CREATE TABLE IF NOT EXISTS archive_evaluations (
            subject_id TEXT NOT NULL, lottery_type TEXT NOT NULL, issue TEXT NOT NULL, draw_revision TEXT NOT NULL,
            rule_version TEXT NOT NULL, track TEXT NOT NULL, kind TEXT NOT NULL, play_type TEXT,
            red_count INTEGER NOT NULL, blue_count INTEGER NOT NULL, strategy_id TEXT, policy_key TEXT, run_id TEXT,
            timing_status TEXT NOT NULL, origin TEXT NOT NULL, red_hits INTEGER NOT NULL, blue_hits INTEGER NOT NULL,
            expected_red REAL NOT NULL, expected_blue REAL NOT NULL, random_red REAL NOT NULL, random_blue REAL NOT NULL,
            constrained_red REAL, constrained_blue REAL, constrained_samples INTEGER NOT NULL,
            pool_red REAL, pool_blue REAL, prospective INTEGER NOT NULL, accepted INTEGER NOT NULL,
            numbers_hash TEXT NOT NULL, evaluated_at_epoch INTEGER NOT NULL,
            PRIMARY KEY(subject_id,draw_revision,rule_version))""",
        """CREATE TABLE IF NOT EXISTS archive_draw_heads (
            lottery_type TEXT NOT NULL, issue TEXT NOT NULL, draw_revision TEXT NOT NULL,
            rule_version TEXT NOT NULL, source_digest TEXT NOT NULL, updated_at_epoch INTEGER NOT NULL,
            PRIMARY KEY(lottery_type,issue))""",
        """CREATE TABLE IF NOT EXISTS archive_policy_state (
            lottery_type TEXT NOT NULL, policy_key TEXT NOT NULL, history_periods INTEGER NOT NULL,
            as_of_issue TEXT NOT NULL, weights_json TEXT NOT NULL, evidence_digest TEXT NOT NULL,
            status TEXT NOT NULL, policy_version TEXT NOT NULL,
            PRIMARY KEY(lottery_type,policy_key,history_periods))""",
        """CREATE TABLE IF NOT EXISTS archive_source_versions (
            lottery_type TEXT NOT NULL,issue TEXT NOT NULL,version INTEGER NOT NULL,
            PRIMARY KEY(lottery_type,issue))""",
        """INSERT INTO archive_source_versions(lottery_type,issue,version)
            SELECT lottery_type,target_issue,1 FROM (
              SELECT lottery_type,target_issue FROM recommendation_entries UNION SELECT lottery_type,target_issue FROM collected_batches
            ) s WHERE NOT EXISTS(SELECT 1 FROM archive_source_versions v WHERE v.lottery_type=s.lottery_type AND v.issue=s.target_issue)""",
        sourceTrigger("entries_insert","recommendation_entries","INSERT"),
        sourceTrigger("entries_update","recommendation_entries","UPDATE OF timing_status,origin,red_json,blue_json,kind,play_type,stage,strategy_id,baseline_order,baseline_seed,accepted_index,rejection_reason,evaluation_protocol,policy_key"),
        sourceTrigger("request_update","ai_request_archive","UPDATE OF facts_json"),
        sourceTrigger("batches_insert","collected_batches","INSERT"),
        sourceTrigger("batches_update","collected_batches","UPDATE OF timing_status,origin,groups_json,declared_kind,play_type,evaluation_protocol"),
        "CREATE INDEX IF NOT EXISTS idx_evaluation_active ON archive_evaluations(lottery_type,issue,draw_revision,rule_version)",
        """CREATE VIEW IF NOT EXISTS accepted_ai_entries AS SELECT * FROM recommendation_entries
            WHERE stage='AI_ORIGINAL' AND accepted_index IS NOT NULL AND rejection_reason IS NULL"""
    )
    private fun sourceTrigger(name:String,table:String,event:String) = """CREATE TRIGGER IF NOT EXISTS archive_$name AFTER $event ON $table BEGIN
        UPDATE archive_source_versions SET version=version+1 WHERE lottery_type=NEW.lottery_type AND issue=NEW.target_issue;
        INSERT INTO archive_source_versions(lottery_type,issue,version) SELECT NEW.lottery_type,NEW.target_issue,1
        WHERE NOT EXISTS(SELECT 1 FROM archive_source_versions WHERE lottery_type=NEW.lottery_type AND issue=NEW.target_issue);
        END"""
}
