package com.lottery.analyzer.data.db

import com.google.gson.*
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.data.engine.WindowFrequencyStats
import java.security.MessageDigest

/** Persisted JSON protocol, independent of business-class/Pair field names and R8 reflection. */
object ArchivePayloadCodec {
    const val VERSION = 2
    private val gson = Gson()

    private fun json(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name)
        is Map<*, *> -> JsonObject().apply { value.forEach { (key, item) -> add(key.toString(), json(item)) } }
        is Iterable<*> -> JsonArray().apply { value.forEach { add(json(it)) } }
        else -> error("Unsupported archive value")
    }
    private fun obj(vararg fields: Pair<String, Any?>) = JsonObject().apply {
        fields.forEach { (name, value) -> if (value != null) add(name, json(value)) }
    }
    private fun groups(values: List<Pair<List<Int>, List<Int>>>, legacy: Boolean = false) = values.map {
        if (legacy) obj("first" to it.first, "second" to it.second) else obj("reds" to it.first, "blues" to it.second)
    }

    fun encodeGroups(values: List<Pair<List<Int>, List<Int>>>): String = gson.toJson(obj(
        "payload_schema_version" to VERSION, "groups" to groups(values)))

    fun decodeGroups(raw: String): List<Pair<List<Int>, List<Int>>> {
        val root = JsonParser.parseString(raw)
        val rows = if (root.isJsonArray) root.asJsonArray else {
            val envelope = root.asJsonObject
            require(version(envelope) == VERSION) { "不支持的收集归档格式版本" }
            envelope.getAsJsonArray("groups")
        }
        return rows.map { element ->
            val row = element.asJsonObject
            numbers(row.get("reds") ?: row.get("first")) to numbers(row.get("blues") ?: row.get("second"))
        }
    }

    private fun numbers(value: JsonElement?): List<Int> {
        require(value != null && value.isJsonArray) { "归档号码数组损坏" }
        return value.asJsonArray.map { element ->
            require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) { "归档号码不是整数" }
            requireNotNull(element.asString.toIntOrNull()) { "归档号码不是整数" }
        }
    }
    private fun version(root: JsonObject): Int {
        val value = root.get("payload_schema_version") ?: return 1
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return requireNotNull(value.asString.toIntOrNull())
    }

    private fun collection(snapshot: CollectionSnapshot, legacy: Boolean): JsonObject = obj(
        "lotteryType" to snapshot.lotteryType, "targetIssue" to snapshot.targetIssue,
        "rawText" to snapshot.rawText, "groups" to groups(snapshot.groups, legacy),
        "sourceLabel" to snapshot.sourceLabel, "origin" to snapshot.origin, "parserVersion" to snapshot.parserVersion,
        "declaredKind" to snapshot.declaredKind, "kl8PlayType" to snapshot.kl8PlayType)

    private fun hash(value: JsonObject) = MessageDigest.getInstance("SHA-256")
        .digest(gson.toJson(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun collectionHash(snapshot: CollectionSnapshot): String = hash(obj(
        "payload_schema_version" to VERSION, "snapshot" to collection(snapshot, false)))
    // Reconstruct the old unversioned Gson representation explicitly, including HTML escaping.
    // This permits deduplication against existing V3 rows without rewriting their IDs or hashes.
    fun legacyCollectionHash(snapshot: CollectionSnapshot): String = hash(collection(snapshot, true))

    private fun levels(values: List<MissingGroupLevel>) = values.map {
        obj("level" to it.level, "allNumbers" to it.allNumbers, "hitNumbers" to it.hitNumbers)
    }
    private fun analysis(value: IssueAnalysis) = obj(
        "lotteryType" to value.lotteryType, "issue" to value.issue, "drawDate" to value.drawDate,
        "redBalls" to value.redBalls, "blueBalls" to value.blueBalls,
        "oddEvenRatio" to value.oddEvenRatio, "zoneRatio" to value.zoneRatio,
        "sumValue" to value.sumValue, "spanValue" to value.spanValue,
        "consecutivePairs" to value.consecutivePairs, "sameTailPairs" to value.sameTailPairs,
        "redMissingValues" to value.redMissingValues, "blueMissingValues" to value.blueMissingValues,
        "maxRedMissing" to value.maxRedMissing, "missingSum" to value.missingSum,
        "currentDrawnGroups" to value.currentDrawnGroups,
        "allMissingGroups" to levels(value.allMissingGroups), "nextSimulatedGroups" to levels(value.nextSimulatedGroups),
        "hotNumbers" to value.hotNumbers, "warmNumbers" to value.warmNumbers, "coldNumbers" to value.coldNumbers)

    private fun stats(value: WindowFrequencyStats) = obj(
        "type" to value.type, "requestedPeriods" to value.requestedPeriods, "sampleCount" to value.sampleCount,
        "firstIssue" to value.firstIssue, "lastIssue" to value.lastIssue, "excludedCount" to value.excludedCount,
        "redFrequency" to value.redFrequency, "blueFrequency" to value.blueFrequency,
        "sumMean" to value.sumMean, "sumMedian" to value.sumMedian, "sumStdDev" to value.sumStdDev,
        "sumHistogram" to value.sumHistogram, "oddHistogram" to value.oddHistogram,
        "consecutiveHistogram" to value.consecutiveHistogram)

    fun encodeFacts(value: SafeAiArchiveFacts): String = gson.toJson(obj(
        "payload_schema_version" to VERSION, "lotteryType" to value.lotteryType,
        "targetIssue" to value.targetIssue, "mode" to value.mode, "analysis" to analysis(value.analysis),
        "windowContext" to value.windowContext?.let { obj("stats" to stats(it.stats), "strategy" to it.strategy) },
        "hearts" to value.hearts,
        "otherRedFrequency" to value.otherRedFrequency.map { obj("number" to it.first, "count" to it.second) },
        "otherBlueFrequency" to value.otherBlueFrequency.map { obj("number" to it.first, "count" to it.second) },
        "collectedGroups" to groups(value.collectedGroups), "kl8PlayType" to value.kl8PlayType,
        "requestedCount" to value.requestedCount,
        "memoryContext" to value.memoryContext?.let(::memory),
        "selectionContext" to value.selectionContext?.let(::selection)))

    private fun memory(value: LearningContext) = obj("asOfIssue" to value.asOfIssue,"historyPeriods" to value.historyPeriods,
        "formalIssues" to value.formalIssues,"observationIssues" to value.observationIssues,"evaluatedPlans" to value.evaluatedPlans,
        "requestedGroups" to value.requestedGroups,"retainedGroups" to value.retainedGroups,"formalSummary" to value.formalSummary,
        "weights" to value.weights,"policyKey" to value.policyKey,"policyStatus" to value.policyStatus,
        "policyVersion" to value.policyVersion,"evidenceDigest" to value.evidenceDigest)
    private fun selection(value: CandidateSelectionContext) = obj("seed" to value.seed,"baselineOrder" to value.baselineOrder,
        "generatorVersion" to value.generatorVersion,"evaluationProtocol" to value.evaluationProtocol,"basis" to value.basis,
        "candidates" to value.candidates.map { obj("id" to it.id,"strategy" to it.strategy,"reds" to it.reds,"blues" to it.blues,
            "sum" to it.sum,"odd" to it.odd,"zones" to it.zones,"consecutive" to it.consecutive,"heartOverlap" to it.heartOverlap,
            "minOtherOverlap" to it.minOtherOverlap,"maxOtherOverlap" to it.maxOtherOverlap) })

    fun selectionFromFacts(raw: String): CandidateSelectionContext? {
        val value=decodeFacts(raw).getAsJsonObject("selectionContext")?:return null
        val candidates=value.getAsJsonArray("candidates").map { element -> val c=element.asJsonObject
            LocalCandidate(c["id"].asString,c["strategy"].asString,numbers(c["reds"]),numbers(c["blues"]),c["sum"].asInt,c["odd"].asInt,
                numbers(c["zones"]),c["consecutive"].asInt,c["heartOverlap"].asInt,c["minOtherOverlap"].asInt,c["maxOtherOverlap"].asInt)
        }
        require(candidates.size in 1..20&&candidates.map{it.id}.distinct().size==candidates.size)
        val order=value.getAsJsonArray("baselineOrder").map{it.asString}
        require(order.size==candidates.size&&order.toSet()==candidates.map{it.id}.toSet())
        return CandidateSelectionContext(value["seed"].asLong,candidates,order,value["generatorVersion"].asString,value["evaluationProtocol"].asString,value["basis"].asString)
    }

    /** Returns normalized protocol v2 JSON; never reflects into current business model classes. */
    fun decodeFacts(raw: String): JsonObject {
        val root = JsonParser.parseString(raw).asJsonObject
        val version = version(root)
        require(version in 1..VERSION) { "不支持的事实归档格式版本" }
        if (version == 1) {
            for (name in listOf("otherRedFrequency", "otherBlueFrequency")) {
                root.getAsJsonArray(name)?.let { rows -> root.add(name, json(rows.map { row ->
                    obj("number" to row.asJsonObject.get("first"), "count" to row.asJsonObject.get("second"))
                })) }
            }
            root.get("collectedGroups")?.let { root.add("collectedGroups", json(groups(decodeGroups(it.toString())))) }
        }
        root.addProperty("payload_schema_version", VERSION)
        return root
    }

    fun encodePrompt(value: AiPromptContent): String = gson.toJson(obj(
        "payload_schema_version" to VERSION, "system" to value.system, "user" to value.user))
    fun decodePrompt(raw: String): AiPromptContent {
        val root = JsonParser.parseString(raw).asJsonObject
        require(version(root) in 1..VERSION) { "不支持的提示词归档格式版本" }
        return AiPromptContent(root.get("system").asString, root.get("user").asString)
    }
}
