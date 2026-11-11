package com.lottery.analyzer.data.db

import com.google.gson.Gson
import com.lottery.analyzer.data.ai.*
import com.lottery.analyzer.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryArchiveStoreTest {
    class JdbcSql(val db: Connection) : ArchiveSql {
        override fun execute(sql: String, args: List<Any?>) { db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }; statement.execute(); Unit
        } }
        override fun query(sql: String, args: List<Any?>): List<Map<String, String?>> = db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add((1..rows.metaData.columnCount).associate { rows.metaData.getColumnLabel(it) to rows.getString(it) }) } }
        }
        override fun <T> transaction(block: () -> T): T {
            if (!db.autoCommit) return block()
            db.autoCommit = false
            return try { block().also { db.commit() } } catch (e: Throwable) { db.rollback(); throw e } finally { db.autoCommit = true }
        }
    }
    private fun database(): JdbcSql {
        Class.forName("org.sqlite.JDBC")
        return JdbcSql(DriverManager.getConnection("jdbc:sqlite::memory:")).also { sql ->
            sql.execute("PRAGMA foreign_keys=ON")
            MemoryArchiveSchema.statements.forEach { sql.execute(it) }
        }
    }
    private fun request(): AiRecommendationRequest {
        val draw = LotteryRecord("2026001", "2026-01-01", (1..6).toList(), listOf(1))
        val facts = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, listOf(draw), "2026002", AiWindowOptions())
        return AiRecommendationRequest(AiConfig(apiKey="SECRET_DO_NOT_PERSIST", baseUrl="https://example.com/v1"),
            LotteryType.SSQ, facts.analysis, "2026002", AiAnalysisMode.FULL_RECOMMEND, emptySet(), betCount=2, windowContext=facts.windowContext)
    }
    private fun collection() = CollectionSnapshot(LotteryType.SSQ,"2026002","01 02 03 04 05 06 + 01",listOf((1..6).toList() to listOf(1)))
    private fun start(id:String="run", session:String="session") = request().let { r ->
        ArchiveRunStart(id,session,r.lotteryType,r.targetNextIssue,100,"provider","example.com",r.config.modelName,r.config.temperature,
            SafeAiArchiveFacts.from(r),AiPromptBuilder.build(r),collection())
    }
    private fun result():AiRecommendationResult {
        val raw="【推荐第1注】红球: 01 02 03 04 05 06 + 蓝球: 01\n【推荐第2注】红球: 01 02 03 04 05 99 + 蓝球: 01"
        val audit=AiBetGuardrails.parse(raw,request())
        return AiRecommendationResult(AiAnalysisMode.FULL_RECOMMEND,"2026002",raw,raw,audit.bets,"test",LotteryType.SSQ,
            requestedCount=2,parsedCount=audit.parsedCount,rejectionReasons=audit.rejectionReasons,candidateAudit=audit.entries)
    }

    @Test fun snapshotsAreDeduplicatedAndSecretNeverEntersAnyArchivedField() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql)
            val first=store.saveCollection(collection(),1)
            assertEquals(first,store.saveCollection(collection(),2))
            assertEquals(collection().groups,store.collection(first)!!.groups)
            assertEquals(1,store.overview(LotteryType.SSQ).recentBatches.single().count)
            store.begin(start())
            assertEquals("1",sql.query("SELECT COUNT(*) AS n FROM collected_batches").single()["n"])
            val contents=listOf("ai_request_archive","ai_archive_payload","collected_batches").flatMap { table -> sql.query("SELECT * FROM $table").flatMap { it.values } }.joinToString()
            assertFalse(contents.contains("SECRET_DO_NOT_PERSIST"));assertFalse(contents.contains("apiKey"));assertFalse(contents.contains("Authorization"))
            assertEquals("PENDING",sql.query("SELECT status FROM ai_request_archive").single()["status"])
        }
    }

    @Test fun legacyBatchReadsAndDeduplicatesWithoutRewritingItsIdHashOrPayload() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql)
            val snapshot=collection()
            val id=store.saveCollection(snapshot,100)
            sql.execute("UPDATE collected_batches SET groups_json=?,snapshot_hash=? WHERE id=?",
                listOf(Gson().toJson(snapshot.groups),ArchivePayloadCodec.legacyCollectionHash(snapshot),id))
            val before=sql.query("SELECT * FROM collected_batches").single()
            assertEquals(snapshot.groups,store.collection(id)!!.groups)
            assertEquals(1,store.overview(LotteryType.SSQ).recentBatches.single().count)
            assertEquals(id,store.saveCollection(snapshot,200))
            assertEquals(before,sql.query("SELECT * FROM collected_batches").single())
        }
    }

    @Test fun invalidEndpointAndOversizedInputFailClearlyBeforeArchiveOrDelegate()=runTest {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);var calls=0
            val client=object:AiRecommendationClient {override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> {calls++;return Result.success(result())}}
            val coordinator=ArchivedAiRecommendationClient(client,store,"session",StandardTestDispatcher(testScheduler))
            for(url in listOf("https://bad host/v1","relative/path","ftp://example.com","https://user:SECRET@example.com/v1","https://example.com/v1?key=SECRET")) {
                val invalid=request().let { it.copy(config=it.config.copy(baseUrl=url)) }
                val response=coordinator.generate(invalid,null)
                assertTrue(response.isFailure)
                assertTrue(response.exceptionOrNull()!!.message!!.contains("接口地址无效"))
                assertFalse(response.exceptionOrNull()!!.message!!.contains("SECRET"))
            }
            val long=collection().copy(rawText="x".repeat(CollectionInputPolicy.MAX_CHARS+1))
            val response=coordinator.generate(request(),long)
            assertTrue(response.exceptionOrNull()!!.message!!.contains("20000"))
            assertEquals(0,calls)
            assertEquals("0",sql.query("SELECT COUNT(*) AS n FROM ai_request_archive").single()["n"])
            val boundary=collection().copy(rawText=collection().rawText.padEnd(CollectionInputPolicy.MAX_CHARS,' '))
            assertTrue(coordinator.generate(request(),boundary).isSuccess)
            assertEquals(1,calls)
        }
    }

    @Test fun rawRejectedAndAcceptedAreOneLinkedTrackAndCompletionIsIdempotent() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);store.begin(start())
            val first=store.complete("run",result(),200)
            assertEquals(first,store.complete("run",result(),300))
            assertEquals(1,first.size)
            assertEquals("2",sql.query("SELECT COUNT(*) AS n FROM recommendation_entries").single()["n"])
            assertEquals("1",sql.query("SELECT COUNT(*) AS n FROM accepted_ai_entries").single()["n"])
            assertEquals("PARTIAL",sql.query("SELECT status FROM ai_request_archive").single()["status"])
            store.finish("run",ArchiveRunStatus.FAILED,400,"late")
            assertEquals("PARTIAL",sql.query("SELECT status FROM ai_request_archive").single()["status"])
            assertEquals(2,store.detail("run")!!.entries.size)
        }
    }

    @Test fun userEditsAreNewRowsAndNeverDowngradeOrOverwriteTheOriginal() {
        val sql=database();sql.db.use {
            var drawn=false
            val store=SqlMemoryArchiveStore(sql){_,_->drawn};store.begin(start())
            val parent=store.complete("run",result(),200).single()
            drawn=true
            val final=store.confirm(PredictionRecord(lotteryType=LotteryType.SSQ,targetIssue="2026002",redBalls=(1..6).toList(),
                blueBalls=listOf(2),strategy="final",createdAt="2026-01-01",kind=PredictionKind.SINGLE,createdAtEpoch=400,
                archiveParentEntryId=parent,archiveEditedAtEpoch=350))
            val original=sql.query("SELECT * FROM recommendation_entries WHERE id=?",listOf(parent)).single()
            val edited=sql.query("SELECT * FROM recommendation_entries WHERE id=?",listOf(final)).single()
            assertEquals("[1]",original["blue_json"]);assertEquals("[2]",edited["blue_json"])
            assertEquals("INSUFFICIENT_TIMING_EVIDENCE",original["timing_status"])
            assertEquals("POST_DRAW",edited["timing_status"])
            assertEquals(parent,edited["parent_entry_id"]);assertEquals("350",edited["edited_at_epoch"])
        }
    }

    @Test fun failedBeginRollsBackCollectionAndRunTogether() {
        val sql=database();sql.db.use {
            sql.execute("CREATE TRIGGER reject_payload BEFORE INSERT ON ai_archive_payload BEGIN SELECT RAISE(ABORT,'disk simulation'); END")
            try { SqlMemoryArchiveStore(sql).begin(start());fail("Expected rollback") } catch (_: java.sql.SQLException) { }
            for(table in listOf("collected_batches","ai_request_archive")) assertEquals("0",sql.query("SELECT COUNT(*) AS n FROM $table").single()["n"])
        }
    }

    @Test fun failedPredictionInsertRollsBackItsConfirmationArchiveInSameTransaction() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);store.begin(start())
            val parent=store.complete("run",result(),200).single()
            sql.execute("CREATE TABLE user_predictions(id INTEGER PRIMARY KEY, archive_entry_id TEXT)")
            sql.execute("CREATE TRIGGER reject_prediction BEFORE INSERT ON user_predictions BEGIN SELECT RAISE(ABORT,'prediction failed'); END")
            try {
                sql.transaction {
                    val entry=store.confirm(PredictionRecord(lotteryType=LotteryType.SSQ,targetIssue="2026002",redBalls=(1..6).toList(),blueBalls=listOf(1),strategy="test",createdAt="2026-01-01",kind=PredictionKind.SINGLE,createdAtEpoch=300,archiveParentEntryId=parent))
                    sql.execute("INSERT INTO user_predictions VALUES(1,?)",listOf(entry))
                }
                fail("Expected rollback")
            } catch (_: java.sql.SQLException) { }
            assertEquals("0",sql.query("SELECT COUNT(*) AS n FROM recommendation_entries WHERE stage='USER_FINAL'").single()["n"])
            assertEquals("2",sql.query("SELECT COUNT(*) AS n FROM recommendation_entries").single()["n"])
        }
    }

    @Test fun failureDiagnosticsStoreCategoryRatherThanSensitiveErrorBody()=runTest {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql)
            val client=object:AiRecommendationClient {override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> =
                Result.failure(Exception("SECRET_DO_NOT_PERSIST",java.net.SocketTimeoutException("private body"))) }
            assertTrue(ArchivedAiRecommendationClient(client,store,"session",StandardTestDispatcher(testScheduler)).generate(request(),null).isFailure)
            val row=sql.query("SELECT * FROM ai_request_archive").single()
            assertEquals("NETWORK_TIMEOUT",row["failure_category"])
            assertFalse(row.values.joinToString().contains("SECRET_DO_NOT_PERSIST"))
        }
    }

    @Test fun startupOnlyInterruptsOtherSessionsAndNeverResends() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);store.begin(start("old","old-session"));store.begin(start("current","new-session"))
            store.recoverInterrupted("new-session",300)
            assertEquals("INTERRUPTED",sql.query("SELECT status FROM ai_request_archive WHERE id='old'").single()["status"])
            assertEquals("PENDING",sql.query("SELECT status FROM ai_request_archive WHERE id='current'").single()["status"])
        }
    }

    @Test fun payloadCleanupKeepsPendingAndSuccessfulUnsettledFactsAndEntries() {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);store.begin(start("pending"));store.begin(start("failed"));store.begin(start("done"))
            store.finish("failed",ArchiveRunStatus.FAILED,200,"test");store.complete("done",result(),200)
            store.prunePayloads(0)
            assertEquals(setOf("pending","done"),sql.query("SELECT run_id FROM ai_archive_payload").map{it["run_id"]}.toSet())
            assertEquals("3",sql.query("SELECT COUNT(*) AS n FROM ai_request_archive").single()["n"])
            assertEquals("2",sql.query("SELECT COUNT(*) AS n FROM recommendation_entries").single()["n"])
        }
    }

    @Test fun beginMustCommitBeforeDelegateAndRawPromptMatchesTheSentEnvelope()=runTest {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql);var calls=0
            val client=object:AiRecommendationClient {
                override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> {
                    calls++
                    assertEquals("PENDING",sql.query("SELECT status FROM ai_request_archive").single()["status"])
                    val prompt=ArchivePayloadCodec.decodePrompt(sql.query("SELECT raw_prompt FROM ai_archive_payload").single()["raw_prompt"]!!)
                    assertEquals(prompt,request.preparedPrompt)
                    return Result.success(result())
                }
            }
            val coordinator=ArchivedAiRecommendationClient(client,store,"session",StandardTestDispatcher(testScheduler))
            val response=coordinator.generate(request(),collection()).getOrThrow()
            assertEquals(1,calls);assertEquals(1,response.archiveEntryIds.size);assertNotNull(response.archiveRunId)
            val broken=object:MemoryArchiveStore by store {override fun begin(start:ArchiveRunStart){error("storage failure")}}
            assertTrue(ArchivedAiRecommendationClient(client,broken,"session",StandardTestDispatcher(testScheduler)).generate(request(),null).isFailure)
            assertEquals(1,calls)
        }
    }

    @Test fun cancellationAfterSendIsArchivedAndDoesNotCreateAcceptedEntries()=runTest {
        val sql=database();sql.db.use {
            val store=SqlMemoryArchiveStore(sql)
            val client=object:AiRecommendationClient {override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> = awaitCancellation()}
            val coordinator=ArchivedAiRecommendationClient(client,store,"session",StandardTestDispatcher(testScheduler))
            val job=launch {coordinator.generate(request(),null)};runCurrent();job.cancel();advanceUntilIdle()
            assertEquals("CANCELLED",sql.query("SELECT status FROM ai_request_archive").single()["status"])
            assertEquals("0",sql.query("SELECT COUNT(*) AS n FROM recommendation_entries").single()["n"])
        }
    }
}
