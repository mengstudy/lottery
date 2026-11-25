package com.lottery.analyzer.data.db

import com.lottery.analyzer.data.ai.*
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class ArchiveReviewStoreTest {
    private fun database(): MemoryArchiveStoreTest.JdbcSql {
        Class.forName("org.sqlite.JDBC")
        return MemoryArchiveStoreTest.JdbcSql(DriverManager.getConnection("jdbc:sqlite::memory:")).also { sql ->
            sql.execute("PRAGMA foreign_keys=ON")
            MemoryArchiveSchema.statements.forEach { sql.execute(it) }
            // 创建 user_predictions 表以验证兼容查询
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
    fun `review aggregates all stages and computes edit hit deltas`() {
        val sql = database()
        sql.db.use {
            val draw2026001 = LotteryRecord("2026001", "2026-01-01", listOf(1, 2, 3, 4, 5, 6), listOf(1))
            val draw2026002 = LotteryRecord("2026002", "2026-01-04", listOf(1, 2, 3, 4, 5, 10), listOf(7))
            val draws = mapOf("2026001" to draw2026001, "2026002" to draw2026002)

            val store = SqlMemoryArchiveStore(
                sql = sql,
                drawLookup = { _, issues -> issues.mapNotNull { i -> draws[i]?.let { i to it } }.toMap() },
                knownDraw = { _, issue -> issue in draws }
            )

            // 构造 2026002 期归档流程：收集批次 + AI 推演 + 用户改号保存
            val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, listOf(draw2026001), "2026002", AiWindowOptions())
            val req = AiRecommendationRequest(
                AiConfig(apiKey = "TEST", baseUrl = "https://example.com/v1"),
                LotteryType.SSQ, facts.analysis, "2026002", AiAnalysisMode.FULL_RECOMMEND,
                emptySet(), betCount = 2, windowContext = facts.windowContext
            )
            val collection = CollectionSnapshot(
                LotteryType.SSQ, "2026002", "01 02 03 04 05 06 + 01",
                listOf(listOf(1, 2, 3, 4, 5, 6) to listOf(1)), sourceLabel = "群友合买"
            )
            store.begin(
                ArchiveRunStart(
                    "run-1", "sess-1", LotteryType.SSQ, "2026002", 100L,
                    "DeepSeek", "example.com", "deepseek-chat", 0.5f,
                    SafeAiArchiveFacts.from(req), AiPromptBuilder.build(req), collection
                )
            )
            val raw = "【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01\n【推荐第2注】红球: 01 02 03 04 05 99 + 蓝球: 02"
            val audit = AiBetGuardrails.parse(raw, req)
            val result = AiRecommendationResult(
                AiAnalysisMode.FULL_RECOMMEND, "2026002", raw, raw, audit.bets, "test", LotteryType.SSQ,
                requestedCount = 2, parsedCount = audit.parsedCount, rejectionReasons = audit.rejectionReasons, candidateAudit = audit.entries
            )
            val acceptedIds = store.complete("run-1", result, 200L)
            assertEquals(1, acceptedIds.size)

            // 用户改号：将红球 06 改为 10（命中），蓝球 01 改为 07（命中）
            val userRecord = PredictionRecord(
                lotteryType = LotteryType.SSQ,
                targetIssue = "2026002",
                redBalls = listOf(1, 2, 3, 4, 5, 10),
                blueBalls = listOf(7),
                strategy = "AI精选微调",
                createdAt = "",
                kind = PredictionKind.SINGLE,
                blueSource = BlueSource.PROVIDED,
                createdAtEpoch = 300L,
                archiveParentEntryId = acceptedIds.first(),
                archiveEditedAtEpoch = 280L
            )
            store.confirm(userRecord)

            // 执行结算
            store.settle(LotteryType.SSQ)

            // 验证复盘期号列表
            val options = store.reviewOptions(LotteryType.SSQ)
            assertEquals(1, options.size)
            assertEquals("2026002", options[0].issue)
            assertTrue(options[0].hasDraw)
            assertTrue(options[0].hasCollection)
            assertTrue(options[0].hasAiRun)
            assertTrue(options[0].hasUserFinal)

            // 验证单期复盘详情
            val detail = store.issueReview(LotteryType.SSQ, "2026002")
            assertNotNull(detail.drawRecord)
            assertEquals(1, detail.collectedItems.size)
            // 收集参考池不判奖，仅统计命中
            assertFalse(detail.collectedItems[0].isWon)
            assertEquals(5, detail.collectedItems[0].redHits)
            assertEquals(0, detail.collectedItems[0].blueHits)

            // AI 原始方案：1条采纳 + 1条拒收
            assertEquals(2, detail.aiOriginalItems.size)
            val acceptedAi = detail.aiOriginalItems.first { !it.isRejected }
            assertEquals(5, acceptedAi.redHits)
            assertEquals(0, acceptedAi.blueHits)

            // 用户最终保存：改号后中 6+1 一等奖
            assertEquals(1, detail.userFinalItems.size)
            val userFinal = detail.userFinalItems[0]
            assertTrue(userFinal.isWon)
            assertEquals(6, userFinal.redHits)
            assertEquals(1, userFinal.blueHits)

            // 改号前后差异对比
            assertEquals(1, detail.editComparisons.size)
            val comp = detail.editComparisons[0]
            assertEquals(listOf(10), comp.addedReds)
            assertEquals(listOf(6), comp.removedReds)
            assertEquals(listOf(7), comp.addedBlues)
            assertEquals(listOf(1), comp.removedBlues)
            assertEquals(1, comp.redHitDelta)
            assertEquals(1, comp.blueHitDelta)
        }
    }

    @Test
    fun `draw correction and repeated settle update review accurately without duplicating entries`() {
        val sql = database()
        sql.db.use {
            val draw2026001 = LotteryRecord("2026001", "2026-01-01", listOf(1, 2, 3, 4, 5, 6), listOf(1))
            var activeDraw = LotteryRecord("2026002", "2026-01-04", listOf(1, 2, 3, 4, 5, 6), listOf(1))
            val store = SqlMemoryArchiveStore(
                sql = sql,
                drawLookup = { _, issues -> issues.mapNotNull { i -> when (i) { "2026001" -> i to draw2026001; "2026002" -> i to activeDraw; else -> null } }.toMap() },
                knownDraw = { _, issue -> issue in setOf("2026001", "2026002") }
            )

            // 保存复式池与单注记录
            val compoundBatch = CollectionSnapshot(
                LotteryType.SSQ, "2026002", "01 02 03 04 05 06 07 + 01",
                listOf(listOf(1, 2, 3, 4, 5, 6, 7) to listOf(1)),
                declaredKind = PredictionKind.COMPOUND
            )
            store.saveCollection(compoundBatch, 100L)

            store.confirm(
                PredictionRecord(
                    lotteryType = LotteryType.SSQ,
                    targetIssue = "2026002",
                    redBalls = listOf(1, 2, 3, 4, 5, 6),
                    blueBalls = listOf(1),
                    strategy = "单注对照",
                    createdAt = "",
                    kind = PredictionKind.SINGLE,
                    blueSource = BlueSource.PROVIDED,
                    createdAtEpoch = 200L
                )
            )

            // 第一次结算 & 重复结算
            store.settle(LotteryType.SSQ)
            store.settle(LotteryType.SSQ)
            val firstReview = store.issueReview(LotteryType.SSQ, "2026002")
            assertEquals(1, firstReview.collectedItems.size)
            assertEquals(1, firstReview.userFinalItems.size)
            // 复式池即使命中全部红蓝球也绝不按单注判奖
            assertFalse(firstReview.collectedItems[0].isWon)
            assertEquals(6, firstReview.collectedItems[0].redHits)
            assertTrue(firstReview.userFinalItems[0].isWon)
            val firstRevision = firstReview.drawRevision

            // 模拟官方更正开奖号
            activeDraw = LotteryRecord("2026002", "2026-01-04", listOf(11, 12, 13, 14, 15, 16), listOf(16))
            store.settle(LotteryType.SSQ)
            store.settle(LotteryType.SSQ)

            val correctedReview = store.issueReview(LotteryType.SSQ, "2026002")
            assertNotEquals(firstRevision, correctedReview.drawRevision)
            assertEquals(1, correctedReview.collectedItems.size)
            assertEquals(1, correctedReview.userFinalItems.size)
            assertEquals(0, correctedReview.userFinalItems[0].redHits)
            assertEquals(0, correctedReview.userFinalItems[0].blueHits)
            assertFalse(correctedReview.userFinalItems[0].isWon)
        }
    }
}
