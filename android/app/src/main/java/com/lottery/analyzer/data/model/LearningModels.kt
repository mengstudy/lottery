package com.lottery.analyzer.data.model

data class AiMemoryOptions(
    val enabled: Boolean = true, val twoStage: Boolean = true, val adaptive: Boolean = true,
    val historyPeriods: Int = 30, val candidateCount: Int = 16
)

data class LearningContext(
    val asOfIssue: String, val historyPeriods: Int, val formalIssues: Int, val observationIssues: Int,
    val evaluatedPlans: Int, val requestedGroups: Int, val retainedGroups: Int,
    val formalSummary: String, val weights: Map<String, Double>, val policyKey: String,
    val policyStatus: String, val policyVersion: String, val evidenceDigest: String
)

data class LocalCandidate(
    val id: String, val strategy: String, val reds: List<Int>, val blues: List<Int>,
    val sum: Int, val odd: Int, val zones: List<Int>, val consecutive: Int,
    val heartOverlap: Int, val minOtherOverlap: Int, val maxOtherOverlap: Int
)

data class CandidateSelectionContext(
    val seed: Long, val candidates: List<LocalCandidate>, val baselineOrder: List<String>,
    val generatorVersion: String = "candidate-v1", val evaluationProtocol: String = "eval-v1",
    val basis: String = "FREQUENCY"
)

data class SettlementResult(val revisedIssues: Int = 0, val evaluated: Int = 0, val skipped: Int = 0)

data class PerformanceRow(
    val track: String, val kind: String, val play: String?, val redCount: Int, val blueCount: Int,
    val eligibility: String, val issues: Int, val plans: Int, val redMean: Double, val blueMean: Double,
    val randomRed: Double, val randomBlue: Double, val constrainedRed: Double?, val constrainedBlue: Double?,
    val poolRed: Double?, val poolBlue: Double?
)

data class PerformanceReport(
    val rows: List<PerformanceRow> = emptyList(), val issues: Int = 0, val formalIssues: Int = 0,
    val observationIssues: Int = 0, val pendingIssues: Int = 0, val ruleVersion: String = "eval-v1",
    val requestedGroups: Int = 0, val retainedGroups: Int = 0
)
