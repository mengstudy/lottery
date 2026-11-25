package com.lottery.analyzer.data.model

/**
 * 档案备份摘要（用于导入前预览核对，明确排除 API Key 与诊断日志）
 */
data class ArchiveBackupSummary(
    val format: String,
    val backupVersion: Int,
    val dbSchemaVersion: Int,
    val exportedAtEpoch: Long,
    val predictionCount: Int,
    val collectionBatchCount: Int,
    val aiRunCount: Int,
    val entryCount: Int,
    val excludesCredentials: Boolean = true
)

/**
 * 备份恢复结果统计
 */
data class ArchiveBackupRestoreResult(
    val predictionCount: Int,
    val collectionBatchCount: Int,
    val aiRunCount: Int,
    val entryCount: Int
)
