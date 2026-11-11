package com.lottery.analyzer.data.ai

import com.google.gson.JsonParser
import com.lottery.analyzer.data.engine.CandidateRules
import com.lottery.analyzer.data.model.*

data class AiCandidateSelectionResult(val validation: AiBetValidation,val explanation:String,val summary:String)

object AiCandidateSelectionParser {
    fun parse(content:String,request:AiRecommendationRequest,check:()->Unit={}):AiCandidateSelectionResult {
        val selection=requireNotNull(request.selectionContext)
        val candidates=selection.candidates.associateBy{it.id};val rules=CandidateRules.from(request)
        val accepted=mutableListOf<Pair<List<Int>,List<Int>>>()
        val audit=mutableListOf<ArchivedCandidate>();val rejected=linkedMapOf<String,Int>();val used=mutableSetOf<String>()
        fun reject(ordinal:Int,id:String,reason:String,candidate:LocalCandidate?=null) {
            rejected[reason]=(rejected[reason]?:0)+1
            audit+=ArchivedCandidate(ordinal,id.take(2048),candidate?.reds,candidate?.blues,reason,candidateId=id.take(100))
        }
        val body=content.trim().let{if(it.startsWith("```json")&&it.endsWith("```"))it.removePrefix("```json").removeSuffix("```").trim() else if(it.startsWith("```")&&it.endsWith("```"))it.removePrefix("```").removeSuffix("```").trim() else it}
        val root=runCatching{JsonParser.parseString(body).asJsonObject}.getOrNull()
        val ids=runCatching{root?.getAsJsonArray("candidate_ids")}.getOrNull()
        if(root==null||ids==null||ids.size()>200) {
            reject(1,"","未返回有效的候选编号JSON")
            return AiCandidateSelectionResult(AiBetValidation(emptyList(),0,rejected,audit),"响应格式不符合候选选择要求", "候选选择未通过")
        }
        for((index,value)in ids.withIndex()) {
            check()
            if(!value.isJsonPrimitive||!value.asJsonPrimitive.isString){reject(index+1,value.toString(),"候选编号必须为字符串");continue}
            val id=value.asString;val candidate=candidates[id]
            if(candidate==null){reject(index+1,id,"候选编号不存在");continue}
            if(!used.add(id)){reject(index+1,id,"候选编号重复",candidate);continue}
            val invalid=rules.rejection(candidate.reds,candidate.blues)
            if(invalid!=null){reject(index+1,id,invalid,candidate);continue}
            if(accepted.size>=request.betCount){reject(index+1,id,"超过请求注数",candidate);continue}
            audit+=ArchivedCandidate(index+1,id,candidate.reds,candidate.blues,acceptedIndex=accepted.size,candidateId=id)
            accepted+=candidate.reds to candidate.blues
        }
        fun text(name:String,fallback:String)=runCatching{root[name]?.takeIf{it.isJsonPrimitive&&it.asJsonPrimitive.isString}?.asString?.take(2000)}.getOrNull()?:fallback
        return AiCandidateSelectionResult(AiBetValidation(accepted,ids.size(),rejected,audit),text("explanation","模型未提供说明"),text("strategy_summary","从本地合法候选中选择"))
    }
}
