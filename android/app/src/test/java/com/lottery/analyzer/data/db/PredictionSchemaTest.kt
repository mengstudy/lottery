package com.lottery.analyzer.data.db

import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class PredictionSchemaTest {
    @Test fun v2ToV3PreservesEveryOldColumnIdAndLegacyFlag() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val sql=MemoryArchiveStoreTest.JdbcSql(connection)
            sql.execute("CREATE TABLE user_predictions(id INTEGER PRIMARY KEY AUTOINCREMENT, lottery_type TEXT, target_issue TEXT, red_balls TEXT, blue_balls TEXT, strategy TEXT, created_at TEXT, note TEXT)")
            PredictionSchema.additions.filterKeys{it!="archive_entry_id"}.forEach{(name,definition)->sql.execute("ALTER TABLE user_predictions ADD COLUMN $name $definition")}
            sql.execute("INSERT INTO user_predictions(id,lottery_type,target_issue,red_balls,blue_balls,strategy,created_at,note,prediction_kind) VALUES(7,'KL8','2026001','1,2,3','','old strategy','12-31 23:00','keep note','LEGACY_UNCONFIRMED')")
            sql.execute("INSERT INTO user_predictions(id,lottery_type,target_issue,red_balls,blue_balls,strategy,created_at,note,prediction_kind,blue_source,created_at_epoch) VALUES(91,'SSQ','2026002','1,2,3,4,5,6','1','new strategy','2026-01-01T00:00:00Z','keep other','SINGLE','PROVIDED',123456)")
            sql.execute("PRAGMA user_version=2")
            val before=sql.query("SELECT * FROM user_predictions ORDER BY id")
            val oldColumns=sql.query("PRAGMA table_info(user_predictions)").map{it["name"]!!}.toSet()
            sql.transaction {
                PredictionSchema.upgrades(oldColumns).forEach{sql.execute(it)}
                MemoryArchiveSchema.statements.forEach{sql.execute(it)}
                sql.execute(PredictionSchema.QUARANTINE_INVALID_TYPES)
                sql.execute("PRAGMA user_version=3")
            }
            val after=sql.query("SELECT * FROM user_predictions ORDER BY id")
            assertEquals(before,after.map{it.filterKeys{key->key in oldColumns}})
            assertTrue(after.all{it["archive_entry_id"]==null})
            assertEquals("91",sql.query("SELECT seq FROM sqlite_sequence WHERE name='user_predictions'").single()["seq"])
            assertEquals("3",sql.query("PRAGMA user_version").single()["user_version"])
            assertEquals("0",sql.query("SELECT COUNT(*) AS n FROM ai_request_archive").single()["n"])
        }
    }

    @Test fun interruptedV3MigrationRollsBackSchemaAndRetainsV2Data() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val sql=MemoryArchiveStoreTest.JdbcSql(connection)
            sql.execute("CREATE TABLE user_predictions(id INTEGER PRIMARY KEY, lottery_type TEXT)")
            sql.execute("INSERT INTO user_predictions VALUES(42,'SSQ')")
            sql.execute("PRAGMA user_version=2")
            try {
                sql.transaction<Unit> {
                    sql.execute("ALTER TABLE user_predictions ADD COLUMN archive_entry_id TEXT")
                    MemoryArchiveSchema.statements.forEach{sql.execute(it)}
                    error("Simulated migration interruption")
                }
                fail("Expected rollback")
            } catch (_: IllegalStateException) { }
            assertEquals(listOf(mapOf("id" to "42","lottery_type" to "SSQ")),sql.query("SELECT * FROM user_predictions"))
            assertEquals("2",sql.query("PRAGMA user_version").single()["user_version"])
            assertTrue(sql.query("SELECT name FROM sqlite_master WHERE name='ai_request_archive'").isEmpty())
        }
    }

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
