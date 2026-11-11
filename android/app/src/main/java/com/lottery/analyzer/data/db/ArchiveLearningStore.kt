package com.lottery.analyzer.data.db

import com.google.gson.*
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.data.engine.*

/** Versioned settlement and per-issue statistics. Historical revisions remain auditable. */
class ArchiveLearningStore(private val sql: ArchiveSql) {
    private val gson=Gson()
    private data class Subject(val id:String,val row:Map<String,String?>,val reds:List<Int>,val blues:List<Int>,val facts:JsonObject?,val seed:Long)
    private fun insert(table:String,fields:Map<String,Any?>) = sql.execute("INSERT INTO $table (${fields.keys.joinToString()}) VALUES (${fields.keys.joinToString{"?"}})",fields.values.toList())
    private fun numbers(raw:String?)=raw?.let{JsonParser.parseString(it).asJsonArray.map{n->
        require(n.isJsonPrimitive&&n.asJsonPrimitive.isNumber);requireNotNull(n.asString.toIntOrNull())}}?:emptyList()

    fun targets(type:LotteryType):List<String> = sql.query("SELECT target_issue FROM recommendation_entries WHERE lottery_type=? UNION SELECT target_issue FROM collected_batches WHERE lottery_type=?",listOf(type.name,type.name))
        .mapNotNull{it["target_issue"]}.filter{it.length in 5..12&&it.all{c->c in '0'..'9'}}

    private fun subjects(type:LotteryType,issue:String):List<Subject> {
        val facts=sql.query("SELECT id,facts_json FROM ai_request_archive WHERE lottery_type=? AND target_issue=?",listOf(type.name,issue)).associate{it["id"]!! to runCatching{
            ArchivePayloadCodec.decodeFacts(it["facts_json"]!!).also { f->
                require(f["lotteryType"].asString==type.name&&f["targetIssue"].asString==issue)
                require(f.getAsJsonObject("analysis")["issue"].asString.toBigInteger()<issue.toBigInteger())
                require(f["mode"].asString in AiAnalysisMode.values().map{mode->mode.name})
            }
        }.getOrNull()}
        val entries=sql.query("SELECT * FROM recommendation_entries WHERE lottery_type=? AND target_issue=?",listOf(type.name,issue)).mapNotNull{row->runCatching{
            val reds=numbers(row["red_json"]);val blues=numbers(row["blue_json"])
            val kind=PredictionKind.valueOf(row["kind"]!!);val play=row["play_type"]?.let{Kl8PlayType.valueOf(it)}
            PredictionRules.validate(PredictionDraft(type,reds,blues,"核验",kind,play,BlueSource.PROVIDED))
            Subject(row["id"]!!,row,reds,blues,row["run_id"]?.let{facts[it]},row["baseline_seed"]?.toLong()?:StableSeed.of(row["id"]!!))
        }.getOrNull()}
        val collected=sql.query("SELECT * FROM collected_batches WHERE lottery_type=? AND target_issue=?",listOf(type.name,issue)).flatMap{batch->
            runCatching{ArchivePayloadCodec.decodeGroups(batch["groups_json"]!!).mapIndexedNotNull{index,group->runCatching{
                val kind=PredictionKind.valueOf(batch["declared_kind"]!!);val play=batch["play_type"]?.let{Kl8PlayType.valueOf(it)}
                if(kind==PredictionKind.REFERENCE&&group.first.isEmpty()) require(group.second.isNotEmpty()&&group.second.distinct().size==group.second.size&&group.second.all{it in 1..type.maxBlueNumber})
                else PredictionRules.validate(PredictionDraft(type,group.first,group.second,"收集核验",kind,play,BlueSource.PROVIDED))
                val id="B:${batch["id"]}:$index"
                val row=batch+mapOf("stage" to "COLLECTED","kind" to kind.name,"accepted_index" to "0","run_id" to null,"strategy_id" to null)
                Subject(id,row,group.first,group.second,null,StableSeed.of("${batch["baseline_seed"]?:batch["id"]}:$index"))
            }.getOrNull()}}.getOrDefault(emptyList())
        }
        return entries+collected
    }

    private fun rules(type:LotteryType,subject:Subject):CandidateRules {
        val f=subject.facts
        val hard=f?.getAsJsonObject("windowContext")?.get("strategy")?.asString?.takeIf{it in listOf("SEGMENT","CONSECUTIVE")}
        val pools=f?.getAsJsonArray("collectedGroups")?.mapNotNull{value->value.asJsonObject.getAsJsonArray("reds")?.map{it.asInt}?.takeIf{it.isNotEmpty()}}.orEmpty().ifEmpty{
            f?.getAsJsonArray("otherRedFrequency")?.map{it.asJsonObject.get("number").asInt}?.takeIf{it.isNotEmpty()}?.let{listOf(it)}?:emptyList()
        }
        return CandidateRules(type,subject.reds.size,subject.blues.size,hard,f?.get("mode")?.asString==AiAnalysisMode.HEART_FUSION.name,pools)
    }

    fun settle(type:LotteryType,draws:Map<String,LotteryRecord>,check:()->Unit={}):SettlementResult {
        var revised=0;var evaluated=0;var skipped=0
        val heads=sql.query("SELECT * FROM archive_draw_heads WHERE lottery_type=?",listOf(type.name)).associateBy{it["issue"]!!}
        val versions=sql.query("SELECT issue,version FROM archive_source_versions WHERE lottery_type=?",listOf(type.name)).associate{it["issue"]!! to it["version"]!!}
        for((issue,draw)in draws) {
            check()
            if(draw.redBalls.size!=type.redCount||draw.redBalls.distinct().size!=type.redCount||draw.redBalls.any{it !in 1..type.maxRedNumber}||draw.blueBalls.size!=type.blueCount||draw.blueBalls.distinct().size!=type.blueCount||draw.blueBalls.any{it !in 1..type.maxBlueNumber}){skipped++;continue}
            val fingerprint=versions[issue]?:"0";val revision=ArchiveEvaluationEngine.revision(type,draw)
            val head=heads[issue]
            if(head?.get("draw_revision")==revision&&head["rule_version"]==ArchiveEvaluationEngine.VERSION&&head["source_digest"]==fingerprint)continue
            val source=subjects(type,issue)
            val poolMeans=source.filter{it.row["stage"]=="LOCAL_CANDIDATE"}.groupBy{it.row["run_id"]}.mapValues{(run,values)->
                val size=source.count{it.row["run_id"]==run&&it.row["stage"]=="AI_ORIGINAL"&&it.row["accepted_index"]!=null&&it.row["rejection_reason"]==null}
                val chosen=values.sortedBy{it.row["baseline_order"]?.toInt()?:Int.MAX_VALUE}.take(size)
                if(chosen.isEmpty())null else (chosen.map{ArchiveEvaluationEngine.hits(it.reds,draw.redBalls)}.average() to chosen.map{ArchiveEvaluationEngine.hits(it.blues,draw.blueBalls)}.average())
            }
            val rows=source.map{subject->
                check()
                val matching=if(subject.row["run_id"]!=null&&subject.facts==null)null else runCatching{rules(type,subject)}.getOrNull()
                val safeRules=matching?:CandidateRules(type,subject.reds.size,subject.blues.size)
                val random=ArchiveEvaluationEngine.baseline(safeRules,subject.seed,draw,false,check)
                val constrained=if(matching==null) BaselineResult(null,null,0) else if(matching.hardStrategy==null&&!matching.fusion)random else ArchiveEvaluationEngine.baseline(matching,StableSeed.of("${subject.seed}:constraints"),draw,true,check)
                val pool=if(subject.row["stage"]=="AI_ORIGINAL"&&subject.row["accepted_index"]!=null)poolMeans[subject.row["run_id"]] else null
                linkedMapOf<String,Any?>("subject_id" to subject.id,"lottery_type" to type.name,"issue" to issue,"draw_revision" to revision,
                    "rule_version" to ArchiveEvaluationEngine.VERSION,"track" to subject.row["stage"],"kind" to subject.row["kind"],"play_type" to subject.row["play_type"],
                    "red_count" to subject.reds.size,"blue_count" to subject.blues.size,"strategy_id" to subject.row["strategy_id"],"policy_key" to subject.row["policy_key"],"run_id" to subject.row["run_id"],
                    "timing_status" to subject.row["timing_status"],"origin" to subject.row["origin"],
                    "red_hits" to ArchiveEvaluationEngine.hits(subject.reds,draw.redBalls),"blue_hits" to ArchiveEvaluationEngine.hits(subject.blues,draw.blueBalls),
                    "expected_red" to subject.reds.size.toDouble()*type.redCount/type.maxRedNumber,"expected_blue" to if(type.maxBlueNumber==0)0.0 else subject.blues.size.toDouble()*type.blueCount/type.maxBlueNumber,
                    "random_red" to random.red,"random_blue" to random.blue,"constrained_red" to constrained.red,"constrained_blue" to constrained.blue,"constrained_samples" to constrained.samples,
                    "pool_red" to pool?.first,"pool_blue" to pool?.second,"prospective" to if(subject.row["evaluation_protocol"]==ArchiveEvaluationEngine.VERSION&&(subject.row["run_id"]==null||subject.facts!=null)&&matching!=null)1 else 0,
                    "accepted" to if(subject.row["accepted_index"]!=null&&subject.row["rejection_reason"]==null)1 else 0,
                    "numbers_hash" to StableSeed.hash("${subject.reds.sorted()}|${subject.blues.sorted()}"),"evaluated_at_epoch" to System.currentTimeMillis())
            }
            sql.transaction {
                check()
                if((sql.query("SELECT version FROM archive_source_versions WHERE lottery_type=? AND issue=?",listOf(type.name,issue)).firstOrNull()?.get("version")?:"0")!=fingerprint){skipped++;return@transaction}
                for(row in rows) {
                    val exists=sql.query("SELECT subject_id FROM archive_evaluations WHERE subject_id=? AND draw_revision=? AND rule_version=?",listOf(row["subject_id"],revision,ArchiveEvaluationEngine.VERSION)).isNotEmpty()
                    if(!exists)insert("archive_evaluations",row)
                    else {
                        val updates=row.filterKeys{it !in setOf("subject_id","draw_revision","rule_version")}
                        sql.execute("UPDATE archive_evaluations SET ${updates.keys.joinToString{"$it=?"}} WHERE subject_id=? AND draw_revision=? AND rule_version=?",updates.values.toList()+listOf(row["subject_id"],revision,ArchiveEvaluationEngine.VERSION))
                    }
                }
                if(head==null)insert("archive_draw_heads",mapOf("lottery_type" to type.name,"issue" to issue,"draw_revision" to revision,"rule_version" to ArchiveEvaluationEngine.VERSION,"source_digest" to fingerprint,"updated_at_epoch" to System.currentTimeMillis()))
                else sql.execute("UPDATE archive_draw_heads SET draw_revision=?,rule_version=?,source_digest=?,updated_at_epoch=? WHERE lottery_type=? AND issue=?",listOf(revision,ArchiveEvaluationEngine.VERSION,fingerprint,System.currentTimeMillis(),type.name,issue))
                revised++;evaluated+=rows.size
            }
        }
        return SettlementResult(revised,evaluated,skipped)
    }

    private fun scope(type:LotteryType,periods:Int,cutoff:String?):Pair<String,List<Any?>> {
        require(periods in 1..200)
        if(cutoff!=null)PredictionRules.validateTarget(cutoff)
        val end=if(cutoff==null)"" else " AND CAST(issue AS INTEGER)<=?"
        return "e.lottery_type=? AND e.issue IN (SELECT issue FROM archive_draw_heads WHERE lottery_type=?$end ORDER BY CAST(issue AS INTEGER) DESC LIMIT ?)" to
            (listOf<Any?>(type.name,type.name)+(if(cutoff==null)emptyList() else listOf(cutoff))+listOf(periods))
    }
    private val active="FROM archive_evaluations e JOIN archive_draw_heads h ON e.lottery_type=h.lottery_type AND e.issue=h.issue AND e.draw_revision=h.draw_revision AND e.rule_version=h.rule_version"
    private val eligibility="CASE WHEN origin<>'REALTIME_RECOMMEND' THEN 'REVIEW' WHEN timing_status='POST_DRAW' THEN 'POST_DRAW' WHEN timing_status='CONFIRMED_PRE_DRAW' AND prospective=1 THEN 'FORMAL' ELSE 'UNKNOWN' END"

    fun performance(type:LotteryType,periods:Int,cutoff:String?,policyKey:String?=null):PerformanceReport {
        val (scopeFilter,scopeArgs)=scope(type,periods,cutoff)
        val filter=scopeFilter+if(policyKey==null)"" else " AND e.policy_key=?"
        val args=scopeArgs+if(policyKey==null)emptyList() else listOf(policyKey)
        val columns="e.*"
        val base="SELECT $columns $active WHERE $filter"
        val tracks="SELECT * FROM ($base) UNION ALL SELECT subject_id,lottery_type,issue,draw_revision,rule_version,'AI_ACCEPTED' AS track,kind,play_type,red_count,blue_count,strategy_id,policy_key,run_id,timing_status,origin,red_hits,blue_hits,expected_red,expected_blue,random_red,random_blue,constrained_red,constrained_blue,constrained_samples,pool_red,pool_blue,prospective,accepted,numbers_hash,evaluated_at_epoch FROM ($base) WHERE track='AI_ORIGINAL' AND accepted=1"
        val grouped=sql.query("""SELECT issue,track,kind,play_type,red_count,blue_count,$eligibility AS eligibility,COUNT(*) AS plans,
            AVG(red_hits) AS r,AVG(blue_hits) AS b,AVG(random_red) AS rr,AVG(random_blue) AS rb,
            CASE WHEN MIN(constrained_samples)>=32 THEN AVG(constrained_red) END AS cr,CASE WHEN MIN(constrained_samples)>=32 THEN AVG(constrained_blue) END AS cb,AVG(pool_red) AS pr,AVG(pool_blue) AS pb
            FROM ($tracks) GROUP BY issue,track,kind,play_type,red_count,blue_count,eligibility""",args+args)
        val rows=grouped.groupBy{listOf(it["track"],it["kind"],it["play_type"],it["red_count"],it["blue_count"],it["eligibility"])}.map{(_,values)->
            val first=values.first()
            fun avg(key:String)=values.mapNotNull{it[key]?.toDouble()}.takeIf{it.size==values.size}?.average()
            PerformanceRow(first["track"]!!,first["kind"]!!,first["play_type"],first["red_count"]!!.toInt(),first["blue_count"]!!.toInt(),first["eligibility"]!!,
                values.size,values.sumOf{it["plans"]!!.toInt()},avg("r")!!,avg("b")!!,avg("rr")!!,avg("rb")!!,avg("cr"),avg("cb"),
                avg("pr").takeIf{first["track"]=="AI_ACCEPTED"},avg("pb").takeIf{first["track"]=="AI_ACCEPTED"})
        }.sortedWith(compareBy({it.eligibility},{it.track},{it.kind},{it.redCount},{it.blueCount}))
        val requests=sql.query("SELECT COALESCE(SUM(requested_count),0) AS requested,COALESCE(SUM(accepted_count),0) AS retained FROM ai_request_archive WHERE lottery_type=? AND target_issue IN (SELECT issue FROM archive_draw_heads WHERE lottery_type=?${if(cutoff==null)"" else " AND CAST(issue AS INTEGER)<=?"} ORDER BY CAST(issue AS INTEGER) DESC LIMIT ?)",scopeArgs).single()
        val settled=sql.query("SELECT issue FROM archive_draw_heads WHERE lottery_type=?",listOf(type.name)).map{it["issue"]}.toSet()
        return PerformanceReport(rows,grouped.map{it["issue"]}.distinct().size,grouped.filter{it["eligibility"]=="FORMAL"}.map{it["issue"]}.distinct().size,
            grouped.filter{it["eligibility"]!="FORMAL"}.map{it["issue"]}.distinct().size,targets(type).count{it !in settled},
            requestedGroups=requests["requested"]!!.toInt(),retainedGroups=requests["retained"]!!.toInt())
    }

    fun learning(request:AiRecommendationRequest,options:AiMemoryOptions):LearningContext = sql.transaction {
        val type=request.lotteryType;val cutoff=request.analysis.issue;val key=policyKey(SafeAiArchiveFacts.from(request))
        val report=performance(type,options.historyPeriods,cutoff,key)
        val (filter,args)=scope(type,options.historyPeriods,cutoff)
        val evidence=sql.query("""SELECT e.issue,e.strategy_id,AVG((red_hits-constrained_red)*1.0/MAX(red_count,1)+(blue_hits-constrained_blue)*1.0/MAX(blue_count,1)) AS score
            $active WHERE $filter AND track='LOCAL_CANDIDATE' AND e.policy_key=? AND timing_status='CONFIRMED_PRE_DRAW'
            AND origin='REALTIME_RECOMMEND' AND prospective=1 AND constrained_samples>=32 GROUP BY e.issue,e.strategy_id ORDER BY CAST(e.issue AS INTEGER)""",args+key)
        val families=WindowStrategy.values().filter{it!=WindowStrategy.CONSECUTIVE||CandidateRules.from(request).redCount>=2}.map{it.name}
        val scores=evidence.groupBy{it["strategy_id"]!!}.mapValues{(_,rows)->rows.map{it["score"]!!.toDouble()}}
        val issues=evidence.map{it["issue"]!!}.distinct();val asOf=issues.maxByOrNull{it.toBigInteger()}.orEmpty()
        val digest=StableSeed.hash(evidence.joinToString{"${it["issue"]}:${it["strategy_id"]}:${it["score"]}"})
        val saved=sql.query("SELECT * FROM archive_policy_state WHERE lottery_type=? AND policy_key=? AND history_periods=?",listOf(type.name,key,options.historyPeriods)).firstOrNull()
        val historical=saved?.get("as_of_issue")?.toBigIntegerOrNull()?.let{it>cutoff.toBigInteger()}?:false
        val old=saved.takeUnless{historical}
        var weights=old?.get("weights_json")?.let{raw->runCatching{JsonParser.parseString(raw).asJsonObject.entrySet().associate{it.key to it.value.asDouble}}.getOrNull()}
            ?.takeIf{it.keys==families.toSet()&&it.values.all{v->v.isFinite()&&v>=.1}&&kotlin.math.abs(it.values.sum()-1.0)<1e-8}?:AdaptivePolicyEngine.uniform(families)
        var status="INSUFFICIENT_DATA"
        if(issues.size<30)weights=AdaptivePolicyEngine.uniform(families)
        if(options.adaptive&&issues.size>=30) {
            val newer=old==null||old["as_of_issue"].isNullOrEmpty()||asOf.toBigInteger()>old["as_of_issue"]!!.toBigInteger()
            val proposed=AdaptivePolicyEngine.proposed(scores,families)
            if(newer)weights=AdaptivePolicyEngine.bounded(weights,proposed)
            status=if(old!=null&&!newer&&(old["evidence_digest"]!=digest||old["status"]=="CORRECTION_FROZEN"))"CORRECTION_FROZEN" else if(proposed.all{(name,value)->kotlin.math.abs(value-AdaptivePolicyEngine.uniform(families).getValue(name))<1e-10})"NO_ADVANTAGE_EVIDENCE" else "CONTROLLED"
        }
        if(!options.adaptive){weights=AdaptivePolicyEngine.uniform(families);status="DISABLED"}
        val version=StableSeed.hash("policy-v1:$key:$asOf:$weights:$digest").take(16)
        val highWater=listOfNotNull(old?.get("as_of_issue")?.takeIf{it.isNotBlank()},asOf.takeIf{it.isNotBlank()}).maxByOrNull{it.toBigInteger()}.orEmpty()
        if(options.adaptive&&!historical) {
            if(old==null)insert("archive_policy_state",mapOf("lottery_type" to type.name,"policy_key" to key,"history_periods" to options.historyPeriods,"as_of_issue" to asOf,"weights_json" to gson.toJson(weights),"evidence_digest" to digest,"status" to status,"policy_version" to version))
            else sql.execute("UPDATE archive_policy_state SET as_of_issue=?,weights_json=?,evidence_digest=?,status=?,policy_version=? WHERE lottery_type=? AND policy_key=? AND history_periods=?",listOf(highWater,gson.toJson(weights),digest,status,version,type.name,key,options.historyPeriods))
        }
        val shape=CandidateRules.from(request)
        val formal=report.rows.filter{it.eligibility=="FORMAL"&&it.kind==PredictionKind.SINGLE.name&&it.redCount==shape.redCount&&it.blueCount==shape.blueCount&&(type!=LotteryType.KL8||it.play==request.kl8PlayType.name)}
        val summary=if(formal.isEmpty())"没有具备开奖前时间证据的历史成绩；不根据观察成绩调整策略。" else formal.take(12).joinToString("；"){"${it.track}/${it.kind}/${it.redCount}+${it.blueCount}：${it.issues}期${it.plans}组，红均${"%.2f".format(java.util.Locale.ROOT,it.redMean)}，蓝均${"%.2f".format(java.util.Locale.ROOT,it.blueMean)}"}
        val uniquePlans=sql.query("SELECT COUNT(*) AS n $active WHERE $filter",args).single()["n"]!!.toInt()
        LearningContext(cutoff,options.historyPeriods,issues.size,report.observationIssues,uniquePlans,report.requestedGroups,report.retainedGroups,summary,weights,key,if(historical)"HISTORICAL_SCOPED" else status,version,digest)
    }

    companion object {
        private val locks=LotteryType.values().associateWith{Any()}
        fun lock(type:LotteryType)=locks.getValue(type)
        fun policyKey(f:SafeAiArchiveFacts):String {
            val play=if(f.lotteryType==LotteryType.KL8)f.kl8PlayType?.name?:"UNKNOWN" else "${f.lotteryType.redCount}+${f.lotteryType.blueCount}"
            val hard=f.windowContext?.strategy?.name?.takeIf{it in listOf("SEGMENT","CONSECUTIVE") }?:"NONE"
            val basis=f.windowContext?.let{"frequency-v1:${it.stats.requestedPeriods}"}?:"omission-v1"
            return "candidate-v1:$basis:$play:$hard:${f.mode==AiAnalysisMode.HEART_FUSION}"
        }
    }
}
