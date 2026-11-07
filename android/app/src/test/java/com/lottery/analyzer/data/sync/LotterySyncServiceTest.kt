package com.lottery.analyzer.data.sync

import com.google.gson.Gson
import com.lottery.analyzer.data.model.LotteryType
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class LotterySyncServiceTest {
    private fun row(issue: Int, valid: Boolean = true) = mapOf(
        "code" to issue.toString(), "date" to "2026-11-01",
        "red" to if (valid) "01,02,03,04,05,06" else "01,02", "blue" to "01"
    )

    @Test fun invalidRowDoesNotTerminateFullPage() = runBlocking {
        var requests = 0
        var written = 0
        val client = client {
            requests++
            if (requests == 1) Gson().toJson(mapOf("result" to (List(29) { row(it + 1) } + row(30, false))))
            else Gson().toJson(mapOf("result" to listOf(row(31))))
        }
        try {
            val result = LotterySyncService({ _, records -> records.size.also { written += it } }, client)
                .syncAllHistory(LotteryType.SSQ).getOrThrow()
            assertEquals(2, requests)
            assertEquals(30, written)
            assertEquals(1, result.invalidCount)
        } finally { client.dispatcher.executorService.shutdown() }
    }

    @Test fun missingArrayIsFailureRatherThanSuccessfulEndOfHistory() = runBlocking {
        val client = client { "{\"message\":\"rate limited\"}" }
        try {
            val result = LotterySyncService({ _, records -> records.size }, client).syncAllHistory(LotteryType.SSQ)
            assertTrue(result.isFailure)
        } finally { client.dispatcher.executorService.shutdown() }
    }

    @Test fun malformedIndividualRowIsCountedAndMetadataKeepsPagination() {
        val text = Gson().toJson(mapOf("result" to listOf(row(1),mapOf("code" to "2")), "pageCount" to 2))
        val page = LotteryPageParser.parse(LotteryType.SSQ, text)
        assertEquals(2, page.rawCount)
        assertEquals(1, page.invalidCount)
        assertTrue(page.hasMore(1,30))
    }

    @Test fun repeatingPageFailsInsteadOfClaimingCompleteHistory() = runBlocking {
        val text = Gson().toJson(mapOf("result" to List(30) { row(it+1) }))
        val client = client { text }
        try {
            assertTrue(LotterySyncService({ _, records -> records.size }, client).syncAllHistory(LotteryType.SSQ).isFailure)
        } finally { client.dispatcher.executorService.shutdown() }
    }

    private fun client(body: () -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body().toResponseBody("application/json".toMediaType())).build()
    }.build()
}
