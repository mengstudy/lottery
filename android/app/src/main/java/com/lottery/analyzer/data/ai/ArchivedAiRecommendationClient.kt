package com.lottery.analyzer.data.ai

import com.lottery.analyzer.data.db.MemoryArchiveStore
import com.lottery.analyzer.data.db.CredentialScope
import com.lottery.analyzer.data.model.*
import kotlinx.coroutines.*
import java.net.URI
import java.util.UUID
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiHttpFailure(val statusCode: Int, message: String) : Exception(message)

class ArchivedAiRecommendationClient(
    private val delegate: AiRecommendationClient,
    private val archive: MemoryArchiveStore,
    private val sessionId: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun generate(request: AiRecommendationRequest, collection: CollectionSnapshot?): Result<AiRecommendationResult> {
        currentCoroutineContext().ensureActive()
        collection?.let { snapshot -> CollectionInputPolicy.error(snapshot.rawText)?.let {
            return Result.failure(IllegalArgumentException(it))
        } }
        val endpointHost = try {
            CredentialScope.requireEndpoint(request.config.baseUrl)
            requireNotNull(URI(request.config.baseUrl.trim()).host)
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("AI接口地址无效，请填写合法的HTTP(S)地址，不包含账号、查询参数或片段"))
        }
        val id = UUID.randomUUID().toString()
        var dispatched = false
        var received = false
        try {
            currentCoroutineContext().ensureActive()
            val prompt = AiPromptBuilder.build(request)
            // A failed pre-send commit prevents any HTTP call. Never archive AiConfig or exception bodies.
            val start = ArchiveRunStart(id, sessionId, request.lotteryType, request.targetNextIssue, now(),
                request.config.provider.title, endpointHost, request.config.modelName,
                request.config.temperature, SafeAiArchiveFacts.from(request), prompt, collection)
            withContext(NonCancellable + io) { archive.begin(start) }
            currentCoroutineContext().ensureActive()
            dispatched = true
            val response = delegate.generateRecommendation(request.copy(preparedPrompt = prompt))
            received = true
            val receivedAt = response.getOrNull()?.responseReceivedAtEpoch ?: now()
            if (response.isFailure) {
                val failure = requireNotNull(response.exceptionOrNull())
                if (failure is CancellationException) throw failure
                withContext(NonCancellable + io) { archive.finish(id, ArchiveRunStatus.FAILED, receivedAt, category(failure)) }
                return response
            }
            val result = response.getOrThrow()
            val ids = withContext(NonCancellable + io) { archive.complete(id, result, receivedAt) }
            return Result.success(result.copy(archiveRunId = id, archiveEntryIds = ids))
        } catch (e: CancellationException) {
            withContext(NonCancellable + io) { runCatching { archive.finish(id, ArchiveRunStatus.CANCELLED, now(), if (dispatched) "LOCAL_CANCEL_REMOTE_UNKNOWN" else "CANCELLED_BEFORE_SEND") } }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable + io) { runCatching { archive.finish(id, ArchiveRunStatus.FAILED, now(), if (received) "ARCHIVE_FINALIZE_FAILED_RESPONSE_RECEIVED" else "EXECUTION_OR_ARCHIVE_FAILED") } }
            return Result.failure(IllegalStateException("推演或归档未完成，请检查存储和网络；不会自动重发", e))
        }
    }

    private fun category(error: Throwable): String {
        var cause: Throwable? = error
        repeat(8) {
            when (val current = cause) {
                is AiHttpFailure -> return "HTTP_${current.statusCode}"
                is SocketTimeoutException -> return "NETWORK_TIMEOUT"
                is UnknownHostException -> return "NETWORK_DNS"
            }
            cause = cause?.cause
        }
        return "REQUEST_FAILED"
    }
}
