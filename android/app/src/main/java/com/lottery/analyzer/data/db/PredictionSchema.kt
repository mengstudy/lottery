package com.lottery.analyzer.data.db

object PredictionSchema {
    const val VERSION = 2
    val additions = linkedMapOf(
        "prediction_kind" to "TEXT NOT NULL DEFAULT 'LEGACY_UNCONFIRMED'",
        "kl8_play_type" to "TEXT",
        "blue_source" to "TEXT NOT NULL DEFAULT 'LEGACY_UNKNOWN'",
        "created_at_epoch" to "INTEGER NOT NULL DEFAULT 0",
        "quarantined" to "INTEGER NOT NULL DEFAULT 0"
    )

    fun upgrades(existingColumns: Set<String>): List<String> = additions
        .filterKeys { it !in existingColumns }
        .map { (name, definition) -> "ALTER TABLE user_predictions ADD COLUMN $name $definition" }

    const val QUARANTINE_INVALID_TYPES = "UPDATE user_predictions SET quarantined = 1 WHERE quarantined = 0 AND lottery_type NOT IN ('SSQ','DLT','KL8')"
}
