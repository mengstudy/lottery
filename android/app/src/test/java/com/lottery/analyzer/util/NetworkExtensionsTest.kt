package com.lottery.analyzer.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class NetworkExtensionsTest {
    @Test fun oversizedUnknownLengthBodyIsRejectedAndClosed() = runBlocking {
        val client = bodyClient(Buffer().writeUtf8("12345"))
        try {
            val failure = runCatching { client.newCall(request()).executeAndReadString(4) }.exceptionOrNull()
            assertTrue(failure is java.io.IOException)
        } finally { client.dispatcher.executorService.shutdown() }
    }
    @Test fun runtimeFailureInBodyIsDeliveredToCaller() = runBlocking {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        val client = bodyClient(object : Source {
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
            override fun read(sink: Buffer, byteCount: Long): Long = error("body failure")
        })
        try {
            val failure = runCatching {
                withTimeout(2000) { client.newCall(request()).executeAndReadString() }
            }.exceptionOrNull()
            assertTrue("Body failure must reach caller, not time out: $failure", failure is IllegalStateException)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.dispatcher.executorService.awaitTermination(3, TimeUnit.SECONDS)
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test fun cancellationDuringBodyReadCancelsCallAndClosesResponse() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val client = bodyClient(object : Source {
            override fun timeout() = Timeout.NONE
            override fun close() { closed.countDown() }
            override fun read(sink: Buffer, byteCount: Long): Long {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                return -1
            }
        })
        val call = client.newCall(request())
        val task = launch(Dispatchers.IO) { call.executeAndReadString() }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            withTimeout(1000) { task.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally {
            release.countDown()
            assertTrue(closed.await(3, TimeUnit.SECONDS))
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun request() = Request.Builder().url("https://offline.invalid/").build()

    private fun bodyClient(source: Source) = OkHttpClient.Builder().addInterceptor { chain ->
        val body = object : ResponseBody() {
            private val buffered = source.buffer()
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source() = buffered
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
    }.build()
}
