package com.lottery.analyzer.data.repository

import com.lottery.analyzer.data.model.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LotterySnapshot(val records: List<LotteryRecord>, val missingStats: Map<String, MissingStat>)

/** Readers retain one complete snapshot; refreshes for the same type are serialized. */
class LotterySnapshotCache(private val loader: suspend (LotteryType) -> LotterySnapshot) {
    private val snapshots = ConcurrentHashMap<LotteryType, LotterySnapshot>()
    private val locks = LotteryType.values().associateWith { Mutex() }

    suspend fun load(type: LotteryType, force: Boolean = false): LotterySnapshot {
        if (!force) snapshots[type]?.let { return it }
        return locks.getValue(type).withLock {
            if (!force) snapshots[type]?.let { return@withLock it }
            loader(type).also { snapshots[type] = it }
        }
    }
}
