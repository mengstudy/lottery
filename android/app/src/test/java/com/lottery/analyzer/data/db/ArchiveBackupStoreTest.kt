package com.lottery.analyzer.data.db

import com.lottery.analyzer.data.ai.*
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class ArchiveBackupStoreTest {
    private fun newDb(): MemoryArchiveStoreTest.JdbcSql {
        Class.forName("org.sqlite.JDBC")
        return MemoryArchiveStoreTest.JdbcSql(DriverManager.getConnection("jdbc:sqlite::memory:")).also { sql ->
            sql.execute("PRAGMA foreign_keys=ON")
            MemoryArchiveSchema.statements.forEach { sql.execute(it) }
            sql.execute(
                """CREATE TABLE IF NOT EXISTS user_predictions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lottery_type TEXT NOT NULL,
                    target_issue TEXT NOT NULL,
                    red_balls TEXT NOT NULL,
                    blue_balls TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    note TEXT DEFAULT '',
                    prediction_kind TEXT DEFAULT 'LEGACY_UNCONFIRMED',
                    kl8_play_type TEXT,
                    blue_source TEXT DEFAULT 'NONE',
                    created_at_epoch INTEGER DEFAULT 0,
                    quarantined INTEGER DEFAULT 0,
                    archive_entry_id TEXT
                )"""
            )
        }
    }

    @Test
    fun `export excludes api keys and restores full archive and review chain to fresh device db`() {
        val draw1 = LotteryRecord("2026001", "2026-01-01", listOf(1, 2, 3, 4, 5, 6), listOf(1))
        val draw2 = LotteryRecord("2026002", "2026-01-04", listOf(1, 2, 3, 4, 5, 10), listOf(7))
        val draws = mapOf("2026001" to draw1, "2026002" to draw2)
        val lookup: (LotteryType, List<String>) -> Map<String, LotteryRecord> = { _, issues ->
            issues.mapNotNull { i -> draws[i]?.let { i to it } }.toMap()
        }
        val known: (LotteryType, String) -> Boolean = { _, issue -> issue in draws }

        val sourceSql = newDb()
        val exportedJson = sourceSql.db.use {
            val sourceStore = SqlMemoryArchiveStore(sourceSql, lookup, known)
            val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, listOf(draw1), "2026002", AiWindowOptions())
            val req = AiRecommendationRequest(
                AiConfig(apiKey = "SECRET_KEY_MUST_NOT_BE_EXPORTED", baseUrl = "https://example.com/v1"),
                LotteryType.SSQ, facts.analysis, "2026002", AiAnalysisMode.FULL_RECOMMEND,
                emptySet(), betCount = 1, windowContext = facts.windowContext
            )
            val collection = CollectionSnapshot(
                LotteryType.SSQ, "2026002", "01 02 03 04 05 06 + 01",
                listOf(listOf(1, 2, 3, 4, 5, 6) to listOf(1)), sourceLabel = "老设备收集"
            )
            sourceStore.begin(
                ArchiveRunStart(
                    "run-1", "sess-1", LotteryType.SSQ, "2026002", 100L,
                    "DeepSeek", "example.com", "deepseek-chat", 0.5f,
                    SafeAiArchiveFacts.from(req), AiPromptBuilder.build(req), collection
                )
            )
            val raw = "【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01"
            val audit = AiBetGuardrails.parse(raw, req)
            val aiRes = AiRecommendationResult(
                AiAnalysisMode.FULL_RECOMMEND, "2026002", raw, raw, audit.bets, "test", LotteryType.SSQ,
                requestedCount = 1, parsedCount = audit.parsedCount, rejectionReasons = audit.rejectionReasons, candidateAudit = audit.entries
            )
            val acceptedIds = sourceStore.complete("run-1", aiRes, 200L)

            val entryId = sourceStore.confirm(
                PredictionRecord(
                    lotteryType = LotteryType.SSQ,
                    targetIssue = "2026002",
                    redBalls = listOf(1, 2, 3, 4, 5, 10),
                    blueBalls = listOf(7),
                    strategy = "改号保存",
                    createdAt = "2026-01-04 10:00",
                    kind = PredictionKind.SINGLE,
                    blueSource = BlueSource.PROVIDED,
                    createdAtEpoch = 300L,
                    archiveParentEntryId = acceptedIds.first(),
                    archiveEditedAtEpoch = 290L
                )
            )
            sourceSql.execute(
                """INSERT INTO user_predictions(id, lottery_type, target_issue, red_balls, blue_balls, strategy, created_at,
                    prediction_kind, blue_source, created_at_epoch, quarantined, archive_entry_id)
                   VALUES (42, 'SSQ', '2026002', '1,2,3,4,5,10', '7', '改号保存', '2026-01-04 10:00', 'SINGLE', 'PROVIDED', 300, 0, ?)""",
                listOf(entryId)
            )
            sourceStore.settle(LotteryType.SSQ)

            val json = sourceStore.exportBackupJson(1732500000000L)
            assertFalse(json.contains("SECRET_KEY_MUST_NOT_BE_EXPORTED"))
            assertFalse(json.contains("ai_lottery_credentials"))
            assertFalse(json.contains("Authorization"))
            json
        }

        // 模拟换机：在新空库执行校验和恢复
        val targetSql = newDb()
        targetSql.db.use {
            val targetStore = SqlMemoryArchiveStore(targetSql, lookup, known)
            val summary = targetStore.inspectBackup(exportedJson)
            assertEquals(1, summary.predictionCount)
            assertEquals(1, summary.collectionBatchCount)
            assertEquals(1, summary.aiRunCount)
            assertTrue(summary.excludesCredentials)

            val restored = targetStore.restoreBackupJson(exportedJson)
            assertEquals(1, restored.predictionCount)

            // 验证换机后复盘、改号差异、历史记录及外键关联完整可用
            val review = targetStore.issueReview(LotteryType.SSQ, "2026002")
            assertEquals(1, review.collectedItems.size)
            assertEquals(1, review.aiOriginalItems.size)
            assertEquals(1, review.userFinalItems.size)
            assertEquals(1, review.editComparisons.size)
            assertEquals(1, review.editComparisons[0].redHitDelta)
            assertEquals(1, review.editComparisons[0].blueHitDelta)

            // 验证恢复后继续写入新预测时 AUTOINCREMENT 序列从 43 开始且不冲突
            targetSql.execute(
                "INSERT INTO user_predictions(lottery_type, target_issue, red_balls, blue_balls, strategy, created_at) VALUES ('SSQ','2026003','1,2,3,4,5,6','1','新记录','2026-01-07')"
            )
            val maxId = targetSql.query("SELECT MAX(id) AS m FROM user_predictions").single().getValue("m")!!.toInt()
            assertEquals(43, maxId)
        }
    }

    @Test
    fun `invalid or incompatible backup rolls back atomically without losing existing data`() {
        val targetSql = newDb()
        targetSql.db.use {
            targetSql.execute(
                "INSERT INTO user_predictions(id, lottery_type, target_issue, red_balls, blue_balls, strategy, created_at) VALUES (1, 'SSQ', '2026001', '1,2,3,4,5,6', '1', '已有记录', '2026-01-01')"
            )
            val store = SqlMemoryArchiveStore(targetSql)

            // 1. 高版本备份拒绝恢复
            val higherVersion = """{"format":"lottery-archive-backup","backup_version":99,"db_schema_version":4,"exported_at_epoch":1,"tables":{"user_predictions":[],"collected_batches":[],"ai_request_archive":[]}}"""
            assertThrows(IllegalArgumentException::class.java) {
                store.restoreBackupJson(higherVersion)
            }

            // 2. 断链父级引用在事务中触发回滚，已有记录必须保留
            val brokenChain = """{"format":"lottery-archive-backup","backup_version":1,"db_schema_version":4,"exported_at_epoch":1,"tables":{
                "collected_batches":[],
                "ai_request_archive":[],
                "recommendation_entries":[{"id":"child-1","parent_entry_id":"missing-parent","lottery_type":"SSQ","target_issue":"2026001","stage":"USER_FINAL","origin":"REALTIME_RECOMMEND","ordinal":0,"kind":"SINGLE","timing_status":"POST_DRAW","timing_evidence":"LOCAL_DRAW_PRESENT","created_at_epoch":10}],
                "user_predictions":[]
            }}"""
            assertThrows(IllegalArgumentException::class.java) {
                store.restoreBackupJson(brokenChain)
            }

            val count = targetSql.query("SELECT COUNT(*) AS n FROM user_predictions").single().getValue("n")!!.toInt()
            assertEquals(1, count)
        }
    }
}
