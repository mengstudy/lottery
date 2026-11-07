package com.lottery.analyzer.data.repository

import com.lottery.analyzer.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LotterySnapshotCacheTest {
    private fun snapshot(issue: String) = LotterySnapshot(
        listOf(LotteryRecord(issue,"2026-11-01",listOf(1))),
        mapOf(issue to MissingStat(issue,mapOf(1 to issue.toInt())))
    )

    @Test fun refreshPublishesOnlyCompletedSnapshot() = runTest {
        var calls=0
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val cache=LotterySnapshotCache {
            calls++
            if(calls==2){entered.complete(Unit);release.await()}
            snapshot(calls.toString())
        }
        cache.load(LotteryType.SSQ)
        val refresh=async {cache.load(LotteryType.SSQ,true)}
        entered.await()
        val during=cache.load(LotteryType.SSQ)
        assertEquals("1",during.records.single().issue)
        assertEquals(setOf("1"),during.missingStats.keys)
        release.complete(Unit)
        val after=refresh.await()
        assertEquals("2",after.records.single().issue)
        assertEquals(setOf("2"),after.missingStats.keys)
    }

    @Test fun failedOrCancelledLoadNeverReplacesValidSnapshot() = runTest {
        var shouldFail=false
        val cache=LotterySnapshotCache {if(shouldFail)throw CancellationException("cancelled") else snapshot("1")}
        cache.load(LotteryType.SSQ);shouldFail=true
        try{cache.load(LotteryType.SSQ,true);fail("must cancel")}catch(_:CancellationException){}
        assertEquals("1",cache.load(LotteryType.SSQ).records.single().issue)
    }

    @Test fun concurrentInitialReadersShareOneLoad() = runTest {
        var calls=0
        val cache=LotterySnapshotCache {calls++;delay(10);snapshot("1")}
        List(20){async{cache.load(LotteryType.KL8)}}.awaitAll()
        assertEquals(1,calls)
    }
}
