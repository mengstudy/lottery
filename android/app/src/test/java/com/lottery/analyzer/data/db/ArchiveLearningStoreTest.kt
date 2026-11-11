package com.lottery.analyzer.data.db

import com.lottery.analyzer.data.ai.*
import com.lottery.analyzer.data.engine.*
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager
import java.util.concurrent.CancellationException

class ArchiveLearningStoreTest {
    private fun database():MemoryArchiveStoreTest.JdbcSql {
        Class.forName("org.sqlite.JDBC")
        return MemoryArchiveStoreTest.JdbcSql(DriverManager.getConnection("jdbc:sqlite::memory:")).also{sql->
            sql.execute("PRAGMA foreign_keys=ON");MemoryArchiveSchema.statements.forEach{sql.execute(it)}
        }
    }
    private fun draw(issue:String="2026002",reds:List<Int> =(1..6).toList())=LotteryRecord(issue,"2026-01-02",reds,listOf(1))
    private fun request():AiRecommendationRequest {
        val facts=AiAnalysisFactsBuilder.prepare(LotteryType.SSQ,listOf(draw("2026001")),"2026002",AiWindowOptions())
        return AiRecommendationRequest(AiConfig(),LotteryType.SSQ,facts.analysis,"2026002",AiAnalysisMode.FULL_RECOMMEND,emptySet(),betCount=2,windowContext=facts.windowContext)
    }
    private fun populate(sql:ArchiveSql,id:String="run"):SqlMemoryArchiveStore {
        val base=request();val r=base.copy(selectionContext=CandidatePoolEngine.generate(base,16,20))
        val store=SqlMemoryArchiveStore(sql)
        store.begin(ArchiveRunStart(id,"session",LotteryType.SSQ,"2026002",100,"p","example.com","m",.5f,SafeAiArchiveFacts.from(r),AiPromptBuilder.build(r),null))
        val raw="""{"candidate_ids":["C001","C002","bad-id"]}"""
        val audit=AiBetGuardrails.parse(raw,r)
        store.complete(id,AiRecommendationResult(r.mode,"2026002","analysis",raw,audit.bets,"test",LotteryType.SSQ,requestedCount=2,parsedCount=audit.parsedCount,rejectionReasons=audit.rejectionReasons,candidateAudit=audit.entries),200)
        return store
    }
    private fun collection(sql:ArchiveSql,issue:String,groups:List<Pair<List<Int>,List<Int>>>,origin:ArchiveOrigin=ArchiveOrigin.REALTIME_RECOMMEND):String =
        SqlMemoryArchiveStore(sql).saveCollection(CollectionSnapshot(LotteryType.SSQ,issue,"snapshot",groups,origin=origin),100)

    @Test fun repeatedSettlementIsIdempotentAndCorrectionRetainsRevisionsWithoutDoubleCounting() {
        val sql=database();sql.db.use{
            populate(sql);val engine=ArchiveLearningStore(sql)
            val first=engine.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            assertEquals(18,first.evaluated)
            val initial=sql.query("SELECT * FROM archive_evaluations")
            assertEquals(0,engine.settle(LotteryType.SSQ,mapOf("2026002" to draw())).evaluated)
            assertEquals(initial,sql.query("SELECT * FROM archive_evaluations"))
            engine.settle(LotteryType.SSQ,mapOf("2026002" to draw(reds=(20..25).toList())))
            assertEquals(36,sql.query("SELECT * FROM archive_evaluations").size)
            val report=engine.performance(LotteryType.SSQ,30,null)
            assertEquals(1,report.issues);assertEquals(0,report.formalIssues)
            assertEquals(2,report.rows.single{it.track=="AI_ACCEPTED"}.plans)
            assertNotNull(report.rows.single{it.track=="AI_ACCEPTED"}.poolRed)
            assertEquals(16,report.rows.single{it.track=="LOCAL_CANDIDATE"}.plans)
        }
    }

    @Test fun sourceRevisionDetectsNewEntriesForAlreadySettledIssue() {
        val sql=database();sql.db.use{
            collection(sql,"2026002",listOf((1..6).toList() to listOf(1)))
            val e=ArchiveLearningStore(sql);e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            collection(sql,"2026002",listOf((7..12).toList() to listOf(2)))
            assertEquals(2,e.settle(LotteryType.SSQ,mapOf("2026002" to draw())).evaluated)
            assertEquals(2,e.performance(LotteryType.SSQ,30,null).rows.single().plans)
        }
    }

    @Test fun averagesArePerIssueAndReferenceShapeAndOriginsStaySeparate() {
        val sql=database();sql.db.use{
            collection(sql,"2026002",List(8){(1..6).toList() to listOf(1)})
            collection(sql,"2026003",listOf((7..12).toList() to listOf(2)))
            collection(sql,"2026003",listOf((1..12).toList() to listOf(2)))
            collection(sql,"2026003",listOf((1..6).toList() to listOf(1)),ArchiveOrigin.HISTORY_REVIEW)
            val e=ArchiveLearningStore(sql);e.settle(LotteryType.SSQ,mapOf("2026002" to draw(),"2026003" to draw("2026003")))
            val rows=e.performance(LotteryType.SSQ,30,null).rows
            val six=rows.single{it.redCount==6&&it.eligibility=="UNKNOWN"}
            assertEquals(2,six.issues);assertEquals(9,six.plans);assertEquals(3.0,six.redMean,1e-9)
            assertEquals(3,rows.size)
            val cutoff=e.performance(LotteryType.SSQ,30,"2026002")
            assertEquals(1,cutoff.rows.size);assertEquals(6.0,cutoff.rows.single().redMean,1e-9)
        }
    }

    @Test fun cancellationAndDatabaseFailureDoNotPublishPartialIssue() {
        val sql=database();sql.db.use{
            populate(sql);val e=ArchiveLearningStore(sql)
            var count=0
            try{e.settle(LotteryType.SSQ,mapOf("2026002" to draw())){if(++count==10)throw CancellationException()};fail()}catch(_:CancellationException){}
            assertTrue(sql.query("SELECT * FROM archive_draw_heads").isEmpty())
            sql.execute("CREATE TRIGGER reject_eval BEFORE INSERT ON archive_evaluations WHEN NEW.track='AI_ORIGINAL' BEGIN SELECT RAISE(ABORT,'failure'); END")
            try{e.settle(LotteryType.SSQ,mapOf("2026002" to draw()));fail()}catch(_:java.sql.SQLException){}
            assertTrue(sql.query("SELECT * FROM archive_evaluations").isEmpty());assertTrue(sql.query("SELECT * FROM archive_draw_heads").isEmpty())
        }
    }

    @Test fun unknownAndRetrospectiveDataNeverTrainEvenIfTimingLabelIsChanged() {
        val sql=database();sql.db.use{
            populate(sql);val e=ArchiveLearningStore(sql)
            e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            val request=request().copy(analysis=request().analysis.copy(issue="2026002"),targetNextIssue="2026003")
            assertEquals(0,e.learning(request,AiMemoryOptions()).formalIssues)
            sql.execute("UPDATE recommendation_entries SET timing_status='CONFIRMED_PRE_DRAW',evaluation_protocol=NULL")
            e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            assertEquals(0,e.performance(LotteryType.SSQ,30,null).formalIssues)
            assertEquals("INSUFFICIENT_DATA",e.learning(request,AiMemoryOptions()).policyStatus)
        }
    }

    @Test fun corruptFactsCannotQualifyOrFabricateConstrainedBaseline() {
        val sql=database();sql.db.use{
            populate(sql)
            sql.execute("UPDATE ai_request_archive SET facts_json='{}'")
            sql.execute("UPDATE recommendation_entries SET timing_status='CONFIRMED_PRE_DRAW'")
            val e=ArchiveLearningStore(sql);e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            assertEquals(0,e.performance(LotteryType.SSQ,30,null).formalIssues)
        }
    }

    @Test fun policyCannotUseFutureStateAndRepeatedSameIssueCannotRetune() {
        val sql=database();sql.db.use{
            populate(sql);val e=ArchiveLearningStore(sql)
            e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            val r=request();val first=e.learning(r,AiMemoryOptions())
            val second=e.learning(r,AiMemoryOptions());assertEquals(first,second)
            sql.execute("UPDATE archive_policy_state SET as_of_issue='2026999',weights_json='{\"HOT\":1}'")
            val scoped=e.learning(r,AiMemoryOptions())
            assertEquals("HISTORICAL_SCOPED",scoped.policyStatus)
            assertEquals("2026999",sql.query("SELECT as_of_issue FROM archive_policy_state").single()["as_of_issue"])
            assertTrue(scoped.weights.values.all{kotlin.math.abs(it-.2)<1e-9})
        }
    }

    @Test fun certifiedSyntheticEvidenceTunesOncePerNewIssueAndCorrectionsFreezeWeights() {
        val sql=database();sql.db.use{
            populate(sql);val e=ArchiveLearningStore(sql)
            e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            // Explicit synthetic certification is test-only; production has no certification authority.
            sql.transaction {
                for(n in 100..199) {
                    val issue="2026$n"
                    sql.execute("INSERT INTO archive_draw_heads VALUES('SSQ',?,'synthetic','eval-v1','1',1)",listOf(issue))
                    sql.execute("""INSERT INTO archive_evaluations SELECT subject_id||?,lottery_type,?,'synthetic',rule_version,track,kind,play_type,
                        red_count,blue_count,strategy_id,policy_key,run_id,'CONFIRMED_PRE_DRAW',origin,
                        CASE WHEN strategy_id='HOT' THEN 6 ELSE 0 END,CASE WHEN strategy_id='HOT' THEN 1 ELSE 0 END,
                        expected_red,expected_blue,random_red,random_blue,
                        CASE WHEN strategy_id='HOT' THEN 0 ELSE 6 END,CASE WHEN strategy_id='HOT' THEN 0 ELSE 1 END,64,
                        pool_red,pool_blue,1,accepted,numbers_hash,evaluated_at_epoch
                        FROM archive_evaluations WHERE issue='2026002' AND track='LOCAL_CANDIDATE'""",listOf(":$issue",issue))
                }
            }
            val r=request().let{it.copy(analysis=it.analysis.copy(issue="2026200"),targetNextIssue="2026201")}
            val options=AiMemoryOptions(historyPeriods=100)
            val learned=e.learning(r,options)
            assertEquals(100,learned.formalIssues);assertEquals("CONTROLLED",learned.policyStatus)
            assertTrue(learned.weights.getValue("HOT")>.2)
            learned.weights.values.forEach{assertTrue(kotlin.math.abs(it-.2)<=.05000001);assertTrue(it>=.1)}
            assertEquals(learned.weights,e.learning(r,options).weights)
            sql.execute("UPDATE archive_evaluations SET red_hits=0 WHERE issue='2026100' AND strategy_id='HOT'")
            val corrected=e.learning(r,options)
            assertEquals("CORRECTION_FROZEN",corrected.policyStatus);assertEquals(learned.weights,corrected.weights)
            assertEquals("CORRECTION_FROZEN",e.learning(r,options).policyStatus)
            val before=e.learning(r.copy(analysis=r.analysis.copy(issue="2026099")),options)
            assertEquals(0,before.formalIssues);assertTrue(before.weights.values.all{kotlin.math.abs(it-.2)<1e-9})
        }
    }

    @Test fun payloadCleanupProtectsUnsettledCandidatesAndPreservesAllStructuredEvidence() {
        val sql=database();sql.db.use{
            val store=populate(sql)
            store.prunePayloads(0)
            assertEquals(1,sql.query("SELECT * FROM ai_archive_payload").size)
            ArchiveLearningStore(sql).settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            store.prunePayloads(0)
            assertTrue(sql.query("SELECT * FROM ai_archive_payload").isEmpty())
            assertEquals(19,sql.query("SELECT * FROM recommendation_entries").size)
            assertEquals(1,sql.query("SELECT * FROM ai_request_archive").size)
            assertEquals(18,sql.query("SELECT * FROM archive_evaluations").size)
        }
    }

    @Test fun invalidDrawCannotBecomeAnActiveHeadAndBlueOnlyPoolsAreScored() {
        val sql=database();sql.db.use{
            collection(sql,"2026002",listOf(emptyList<Int>() to listOf(1,2)))
            val e=ArchiveLearningStore(sql)
            assertEquals(1,e.settle(LotteryType.SSQ,mapOf("2026002" to draw(reds=listOf(1)))).skipped)
            assertTrue(sql.query("SELECT * FROM archive_draw_heads").isEmpty())
            e.settle(LotteryType.SSQ,mapOf("2026002" to draw()))
            val row=e.performance(LotteryType.SSQ,30,null).rows.single()
            assertEquals(0,row.redCount);assertEquals(2,row.blueCount);assertEquals(1.0,row.blueMean,1e-9)
        }
    }
}
