package com.lottery.analyzer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lottery.analyzer.data.db.AiSettingsStore
import com.lottery.analyzer.data.engine.LotteryAnalysisEngine
import com.lottery.analyzer.data.model.*
import com.lottery.analyzer.data.repository.*
import com.lottery.analyzer.data.sync.SyncReport
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LotteryViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val models=mutableListOf<LotteryViewModel>()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){models.forEach{it.viewModelScope.cancel()};Dispatchers.resetMain()}
    private fun model(repo:FakeRepository=FakeRepository(),handle:SavedStateHandle=SavedStateHandle(),client:AiRecommendationClient?=null):LotteryViewModel =
        LotteryViewModel(Application(),handle,repo,object:AiSettingsStore {
            override fun getAiConfig()=AiConfig(apiKey="offline-test")
            override fun saveAiConfig(config:AiConfig){}
            override fun selectProviderPreset(provider:AiProvider)=AiConfig(provider=provider)
        },dispatcher,dispatcher,client).also{models.add(it)}

    @Test fun aiUsesLatestRetainedFactsAndPersistsOriginalModelAndWindow()=runTest {
        val repo=FakeRepository()
        var captured:AiRecommendationRequest?=null
        val client=object:AiRecommendationClient {
            override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> {
                captured=request
                return Result.success(AiRecommendationResult(request.mode,request.targetNextIssue,"offline","offline",
                    listOf((1..6).toList() to listOf(1)),"offline",request.lotteryType,
                    requestedCount=request.betCount,parsedCount=1,windowContext=request.windowContext,
                    baseIssue=request.analysis.issue,providerName="captured-provider",modelName="snapshot-model"))
            }
        }
        val vm=model(repo,client=client);advanceUntilIdle()
        vm.updateAiWindowOptions(AiWindowOptions(periods=100))
        vm.runAiAnalysis(AiAnalysisMode.FULL_RECOMMEND,3);advanceUntilIdle()
        assertEquals("2026123",captured!!.analysis.issue)
        assertEquals(100,captured!!.windowContext!!.stats.requestedPeriods)
        assertEquals(1,captured!!.windowContext!!.stats.sampleCount)
        vm.saveAiBetToPrediction(0)
        val draft=vm.uiState.value.pendingPredictions.single()
        assertTrue(draft.strategy.contains("captured-provider/snapshot-model"))
        assertTrue(draft.strategy.contains("100期(实1)"))
        assertEquals(PredictionKind.SINGLE,draft.kind)
        assertTrue(repo.saved.isEmpty())
    }

    @Test fun changedAiWindowCancelsAndRejectsLateResponseAndRestoresSettings()=runTest {
        val gate=CompletableDeferred<Unit>()
        var captured:AiRecommendationRequest?=null
        val client=object:AiRecommendationClient {
            override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> {
                captured=request
                withContext(NonCancellable){gate.await()}
                return Result.success(AiRecommendationResult(request.mode,request.targetNextIssue,"late","late",emptyList(),"late"))
            }
        }
        val handle=SavedStateHandle()
        val vm=model(handle=handle,client=client);advanceUntilIdle()
        vm.runAiAnalysis(AiAnalysisMode.FULL_RECOMMEND);runCurrent()
        assertNotNull(captured);assertTrue(vm.uiState.value.isAiLoading)
        vm.updateAiWindowOptions(AiWindowOptions(periods=30,strategy=com.lottery.analyzer.data.engine.WindowStrategy.CONSECUTIVE))
        gate.complete(Unit);advanceUntilIdle()
        assertNull(vm.uiState.value.aiResult);assertFalse(vm.uiState.value.isAiLoading)
        val restored=model(handle=handle);advanceUntilIdle()
        assertEquals(vm.uiState.value.aiWindowOptions,restored.uiState.value.aiWindowOptions)
    }

    @Test fun kl8RequestHasNoWindowFactsEvenWhenWindowSettingIsEnabled()=runTest {
        var captured:AiRecommendationRequest?=null
        val client=object:AiRecommendationClient {
            override suspend fun generateRecommendation(request:AiRecommendationRequest):Result<AiRecommendationResult> {
                captured=request;return Result.failure(IllegalStateException("offline"))
            }
        }
        val vm=model(client=client);advanceUntilIdle();vm.switchLotteryType(LotteryType.KL8);advanceUntilIdle()
        vm.runAiAnalysis(AiAnalysisMode.FULL_RECOMMEND);advanceUntilIdle()
        assertEquals(LotteryType.KL8,captured!!.lotteryType);assertNull(captured!!.windowContext)
        assertFalse(vm.uiState.value.isAiLoading)
    }

    @Test fun clearedInputCannotBeRestoredByOldParser()=runTest {
        val vm=model();advanceUntilIdle()
        vm.updateCollectedBallsText("01020304 05 06 + 0510 12 16");vm.clearCollectedBalls();advanceUntilIdle()
        assertEquals("",vm.uiState.value.collectedBallsText);assertEquals(0,vm.uiState.value.frequencyResult.validGroupsCount)
        vm.updateCollectedBallsText("01020304 05 06 + 0510 12 16");advanceUntilIdle()
        assertEquals((1..6).toList(),vm.uiState.value.frequencyResult.parsedBets.single())
    }
    @Test fun progressFromOldSyncCannotTouchNewTypeOrSelection()=runTest {
        val repo=FakeRepository();val vm=model(repo);advanceUntilIdle()
        repo.syncGate=CompletableDeferred();vm.syncAllHistoryData();runCurrent()
        val old=repo.progress!!
        vm.switchLotteryType(LotteryType.DLT);advanceUntilIdle();vm.toggleHeartBall(35)
        withContext(Dispatchers.Default){old(99,999)}
        assertEquals(setOf(35),vm.uiState.value.myHeartBalls);assertNull(vm.uiState.value.syncAllProgress)
    }
    @Test fun currentSyncProgressPreservesUserSelection()=runTest {
        val repo=FakeRepository();val vm=model(repo);advanceUntilIdle()
        repo.syncGate=CompletableDeferred();vm.syncAllHistoryData();runCurrent();vm.toggleHeartBall(33)
        withContext(Dispatchers.Default){repo.progress!!(2,60)}
        assertEquals(setOf(33),vm.uiState.value.myHeartBalls);assertTrue(vm.uiState.value.syncAllProgress!!.contains("60"))
        repo.syncGate!!.complete(Unit);advanceUntilIdle();assertFalse(vm.uiState.value.isSyncingAll)
    }
    @Test fun trendFailureClearsBusyAndSurfacesError()=runTest {
        val repo=FakeRepository();repo.failTrend=true;val vm=model(repo);advanceUntilIdle()
        vm.loadTrendMatrixData();advanceUntilIdle()
        assertFalse(vm.uiState.value.isTrendLoading);assertTrue(vm.uiState.value.errorMessage!!.contains("走势"))
    }
    @Test fun saveRequiresConfirmationAndFailureDoesNotReportSuccess()=runTest {
        val repo=FakeRepository();val vm=model(repo);advanceUntilIdle()
        vm.savePredictionRecord(listOf(1,2),strategy="reference",kind=PredictionKind.REFERENCE)
        assertTrue(repo.saved.isEmpty());assertTrue(vm.uiState.value.pendingPredictions.isNotEmpty())
        repo.failSave=true;vm.confirmPendingPredictions("2026124");advanceUntilIdle()
        assertTrue(repo.saved.isEmpty());assertNotNull(vm.uiState.value.predictionSaveError);assertFalse(vm.uiState.value.isSavingPrediction)
        repo.failSave=false;vm.confirmPendingPredictions("2026124");advanceUntilIdle()
        assertEquals(PredictionKind.REFERENCE,repo.saved.single().kind);assertTrue(repo.saved.single().blueBalls.isEmpty())
        assertTrue(repo.saved.single().createdAtEpoch>0);assertTrue(vm.uiState.value.pendingPredictions.isEmpty())
    }
    @Test fun repeatedFeedbackHasDifferentIdentityAndCanBeConsumed()=runTest {
        val vm=model();advanceUntilIdle()
        vm.deletePredictionRecord(99);advanceUntilIdle();val first=vm.uiState.value.predictionFeedbackId
        vm.consumePredictionFeedback(first);assertNull(vm.uiState.value.predictionFeedback)
        vm.deletePredictionRecord(99);advanceUntilIdle()
        assertTrue(vm.uiState.value.predictionFeedbackId>first);assertNotNull(vm.uiState.value.predictionFeedback)
        vm.consumePredictionFeedback(first);assertNotNull(vm.uiState.value.predictionFeedback)
    }
    @Test fun savedBusinessInputIsRestoredWithoutPersistingApiKey()=runTest {
        val handle=SavedStateHandle(mapOf("type" to "KL8","hearts" to intArrayOf(1,80),"collected" to "010203","play" to "XUAN_THREE"))
        val vm=model(handle=handle);advanceUntilIdle()
        assertEquals(LotteryType.KL8,vm.uiState.value.currentType);assertEquals(setOf(1,80),vm.uiState.value.myHeartBalls)
        assertEquals(Kl8PlayType.XUAN_THREE,vm.uiState.value.kl8PlayType);assertEquals("010203",vm.uiState.value.collectedBallsText)
        assertFalse(handle.keys().any{it.contains("key",ignoreCase=true)})
    }
    @Test fun aiCannotUseOldFrequencyWhileNewInputIsParsing()=runTest {
        val vm=model();advanceUntilIdle();vm.updateCollectedBallsText("010203040506 + 01")
        vm.runAiAnalysis(AiAnalysisMode.HEART_FUSION)
        assertFalse(vm.uiState.value.isAiLoading);assertTrue(vm.uiState.value.aiError!!.contains("解析"))
        advanceUntilIdle()
    }

    @Test fun delayedCredentialReadDoesNotResetNewUserInput()=runTest {
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val finished=java.util.concurrent.CountDownLatch(1)
        val store=object:AiSettingsStore {
            override fun getAiConfig():AiConfig {entered.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS));finished.countDown();return AiConfig(apiKey="ready")}
            override fun saveAiConfig(config:AiConfig){}
            override fun selectProviderPreset(provider:AiProvider)=AiConfig(provider=provider)
        }
        val vm=LotteryViewModel(Application(),SavedStateHandle(),FakeRepository(),store,Dispatchers.IO,dispatcher).also{models.add(it)}
        runCurrent()
        withContext(Dispatchers.IO){assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))}
        vm.toggleHeartBall(33)
        release.countDown()
        withContext(Dispatchers.IO){assertTrue(finished.await(3,java.util.concurrent.TimeUnit.SECONDS))}
        advanceUntilIdle()
        assertEquals(setOf(33),vm.uiState.value.myHeartBalls)
        assertNotNull(LotteryViewModel::class.java.getConstructor(Application::class.java,SavedStateHandle::class.java))
    }

    @Test fun consecutiveConfigurationSavesRemainOrderedInStorageAndUi()=runTest {
        val read=java.util.concurrent.CountDownLatch(1)
        val first=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val second=java.util.concurrent.CountDownLatch(1)
        val written=java.util.Collections.synchronizedList(mutableListOf<String>())
        val store=object:AiSettingsStore {
            override fun getAiConfig():AiConfig {read.countDown();return AiConfig()}
            override fun saveAiConfig(config:AiConfig){
                if(config.modelName=="first"){first.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS))}
                written.add(config.modelName)
                if(config.modelName=="second")second.countDown()
            }
            override fun selectProviderPreset(provider:AiProvider)=AiConfig(provider=provider)
        }
        val vm=LotteryViewModel(Application(),SavedStateHandle(),FakeRepository(),store,Dispatchers.IO,dispatcher).also{models.add(it)}
        runCurrent();withContext(Dispatchers.IO){assertTrue(read.await(3,java.util.concurrent.TimeUnit.SECONDS))};advanceUntilIdle()
        vm.updateAiConfig(AiConfig(modelName="first"));runCurrent()
        withContext(Dispatchers.IO){assertTrue(first.await(3,java.util.concurrent.TimeUnit.SECONDS))}
        vm.updateAiConfig(AiConfig(modelName="second"));runCurrent()
        withContext(Dispatchers.IO){assertFalse(second.await(100,java.util.concurrent.TimeUnit.MILLISECONDS))}
        release.countDown()
        withContext(Dispatchers.IO){assertTrue(second.await(3,java.util.concurrent.TimeUnit.SECONDS))}
        advanceUntilIdle()
        assertEquals(listOf("first","second"),written);assertEquals("second",vm.uiState.value.aiConfig.modelName)
    }

    private class FakeRepository:LotteryRepositoryContract {
        var failTrend=false;var failSave=false
        var progress:(suspend(Int,Int)->Unit)?=null;var syncGate:CompletableDeferred<Unit>?=null
        val saved=mutableListOf<PredictionRecord>()
        override suspend fun getRecordsDesc(type:LotteryType)=listOf(LotteryRecord("2026123",LocalDate.now().toString(),(1..type.redCount).toList(),(1..type.blueCount).toList()))
        override suspend fun getAnalysisForIssue(type:LotteryType,issue:String)=LotteryAnalysisEngine.analyze(getRecordsDesc(type).single(),null,type)
        override suspend fun getNumberTrend(type:LotteryType,number:Int,isBlue:Boolean,limit:Int)=emptyList<Pair<String,Int>>()
        override suspend fun syncLatestData(type:LotteryType)=Result.success(SyncReport(0))
        override suspend fun syncAllHistoryData(type:LotteryType,onProgress:(suspend(Int,Int)->Unit)?):Result<SyncReport> {progress=onProgress;syncGate?.await();return Result.success(SyncReport(0))}
        override suspend fun savePredictions(records:List<PredictionRecord>):Int {if(failSave)error("disk failure");saved.addAll(records);return records.size}
        override suspend fun deletePrediction(id:Long)=false
        override suspend fun clearPredictions(type:LotteryType?)=true
        override suspend fun getVerifiedPredictionsPaged(type:LotteryType?,page:Int,pageSize:Int)=PagedPredictionsResult(0,0,emptyList())
        override suspend fun executeShrink(type:LotteryType,selectedBalls:List<Int>,config:ShrinkFilterConfig)=ShrinkResult(selectedBalls,0,emptyList(),0,0f,0,0,0)
        override suspend fun getTrendMatrixData(type:LotteryType,limit:Int,descOrder:Boolean):TrendMatrixData {if(failTrend)error("bad database");return TrendMatrixData(type,emptyList(),emptyList(),emptyList())}
    }
}
