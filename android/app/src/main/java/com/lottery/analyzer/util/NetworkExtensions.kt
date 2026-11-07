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
suspend fun Call.executeAndReadString(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {}
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                if (continuation.isCancelled) return
                val code = resp.code
                try {
                    val body = resp.body?.string().orEmpty()
                    if (!continuation.isCancelled) {
                        continuation.resume(code to body)
                    }
                } catch (e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}
