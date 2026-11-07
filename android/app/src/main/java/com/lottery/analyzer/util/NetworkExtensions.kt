package com.lottery.analyzer.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 协程安全的 OkHttp 挂起执行与正文读取：
 * 将协同取消强力覆盖至【等待响应头 + 阻塞读取正文数据流】全生命周期。
 * 一旦外部协程取消，invokeOnCancellation 立即同步触发 call.cancel()，
 * 瞬间切断底层的 Socket 连接，打断正在阻塞中的 body.string() 流读取！
 */
suspend fun Call.executeAndReadString(maxBodyBytes: Long = 4L * 1024 * 1024): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
    require(maxBodyBytes in 1 until Long.MAX_VALUE)
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {}
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // Include close() in the protected operation and resume only after ownership ends.
            val result = try {
                response.use { resp ->
                    if (continuation.isCancelled) return
                    val body = resp.body
                    if (body != null && (body.contentLength() > maxBodyBytes || body.source().request(maxBodyBytes + 1))) {
                        throw IOException("Response body exceeds $maxBodyBytes bytes")
                    }
                    resp.code to body?.string().orEmpty()
                }
            } catch (e: Exception) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
                return
            }
            if (!continuation.isCancelled) continuation.resume(result)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}
