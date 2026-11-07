package com.lottery.analyzer.data.db

import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class PredictionSchemaTest {
    @Test fun legacyMigrationPreservesRowsAndDoesNotInventPlayOrYear(){
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE user_predictions(id INTEGER PRIMARY KEY, lottery_type TEXT, target_issue TEXT, red_balls TEXT, blue_balls TEXT, strategy TEXT, created_at TEXT, note TEXT)")
                sql.execute("INSERT INTO user_predictions VALUES(1,'KL8','2026001','1,2,3','','old strategy','12-31 23:00','old note')")
                sql.execute("INSERT INTO user_predictions VALUES(2,'BROKEN','2026001','1','','old','01-01 00:00','')")
                val oldColumns=mutableSetOf<String>()
                sql.executeQuery("PRAGMA table_info(user_predictions)").use {while(it.next())oldColumns.add(it.getString("name"))}
                PredictionSchema.upgrades(oldColumns).forEach{sql.execute(it)}
                sql.execute(PredictionSchema.QUARANTINE_INVALID_TYPES)
                sql.executeQuery("SELECT * FROM user_predictions WHERE id=1").use {
                    assertTrue(it.next());assertEquals("old strategy",it.getString("strategy"));assertEquals("12-31 23:00",it.getString("created_at"))
                    assertEquals("LEGACY_UNCONFIRMED",it.getString("prediction_kind"));assertNull(it.getString("kl8_play_type"));assertEquals(0L,it.getLong("created_at_epoch"))
                }
                sql.executeQuery("SELECT quarantined FROM user_predictions WHERE id=2").use {assertTrue(it.next());assertEquals(1,it.getInt(1))}
                assertTrue(PredictionSchema.upgrades(oldColumns+PredictionSchema.additions.keys).isEmpty())
            }
        }
    }
}
