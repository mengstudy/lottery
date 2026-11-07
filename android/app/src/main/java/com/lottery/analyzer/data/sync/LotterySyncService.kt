package com.lottery.analyzer.data.sync

import android.util.Log
import com.lottery.analyzer.data.db.LotteryDao
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.executeAndReadString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LotterySyncService(private val dao: LotteryDao) {
    private val client = OkHttpClient.Builder()

        .connectTimeout(12, TimeUnit.SECONDS)

        .readTimeout(15, TimeUnit.SECONDS)

        .build()

    companion object {
        private const val TAG = "LotterySyncService"
}

    /**
     * 1. 同步最新开奖数据 (最近 1 页 / 30 期，轻量快速，秒级返回)
     */
    suspend fun syncLatest(type: LotteryType): Result<Int> = withContext(Dispatchers.IO) {
        ensureActive()

        AppLogger.i(TAG, "发起彩种最新同步: ${type.title} (${type.code})")

        try {
            val apiUrl = when (type) {
                LotteryType.SSQ -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&pageNo=1&pageSize=30&systemType=PC"

                LotteryType.DLT -> "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&provinceId=0&pageSize=30&isVerify=1&pageNo=1"

                LotteryType.KL8 -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=kl8&pageNo=1&pageSize=30&systemType=PC"
}

            val records = fetchRecordsByUrl(type, apiUrl)

            ensureActive()

            val updatedCount = dao.batchInsertOrUpdateRecords(type, records)

            AppLogger.i(TAG, "最新同步成功: ${type.title}, 更新记录数: $updatedCount")

            Result.success(updatedCount)
} catch (e: CancellationException) {
            AppLogger.i(TAG, "最新同步任务被取消: ${type.title}")

            throw e
} catch (e: Exception) {
            AppLogger.w(TAG, "最新同步失败: ${type.title}, 异常: ${e.message}", e)

            Result.failure(e)
}
}

    /**
     * 2. 同步全部历史开奖数据 (循环分页抓取近 2000~3000 期全量历史大底数据)
     * @param onProgress 进度回调 (当前页码, 累计抓取记录数)
     */
    suspend fun syncAllHistory(
        type: LotteryType,
        onProgress: ((page: Int, totalRecords: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "发起彩种全量历史同步: ${type.title} (${type.code})")

        var totalUpdated = 0
        var page = 1
        val maxPages = when (type) {
            LotteryType.SSQ -> 100 // 每页30期，约3000期

            LotteryType.DLT -> 35  // 每页100期，约3500期

            LotteryType.KL8 -> 25  // 每页100期，约2500期
}

        val pageSize = if (type == LotteryType.SSQ) 30 else 100

        try {
            while (page <= maxPages) {
                // 分页请求前检查协程取消

                ensureActive()

                val apiUrl = when (type) {
                    LotteryType.SSQ -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&pageNo=$page&pageSize=$pageSize&systemType=PC"

                    LotteryType.DLT -> "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&provinceId=0&pageSize=$pageSize&isVerify=1&pageNo=$page"

                    LotteryType.KL8 -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=kl8&pageNo=$page&pageSize=$pageSize&systemType=PC"
}

                val records = fetchRecordsByUrl(type, apiUrl)
                if (records.isEmpty()) {
                    // 已无更早开奖历史，终止循环

                    break
}

                // 写库事务前再次检查协程取消

                ensureActive()

                val count = dao.batchInsertOrUpdateRecords(type, records)

                totalUpdated += count

                onProgress?.invoke(page, totalUpdated)

                // 若本页记录不足请求数量，说明已达历史最后一页

                if (records.size < pageSize) {
                    break
}

                page++
}

            AppLogger.i(TAG, "全量历史同步成功: ${type.title}, 累计记录数: $totalUpdated")

            Result.success(totalUpdated)
} catch (e: CancellationException) {
            AppLogger.i(TAG, "全量历史同步被取消: ${type.title}, 已完成持久化期数: $totalUpdated")

            throw e
} catch (e: Exception) {
            AppLogger.w(TAG, "全量历史同步异常中断: ${type.title}, 当前完成数: $totalUpdated, 异常: ${e.message}", e)

            val err = Exception(if (totalUpdated > 0) "全量同步未完成，已拉取 $totalUpdated 期后中断: ${e.message}" else (e.message ?: "网络请求失败"))

            Result.failure(err)
}
}

    private suspend fun fetchRecordsByUrl(type: LotteryType, url: String): List<LotteryRecord> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", if (type == LotteryType.DLT) "https://www.lottery.gov.cn/" else "https://www.cwl.gov.cn/")
            .build()

        val (code, body) = client.newCall(request).executeAndReadString()
        if (code !in 200..299) {
            throw Exception("HTTP 响应异常: $code")
        }
        if (body.isBlank()) return emptyList()
        return parseResponse(type, body)
    }

    private fun parseResponse(type: LotteryType, jsonStr: String): List<LotteryRecord> {
        val list = mutableListOf<LotteryRecord>()

        try {
            val root = JSONObject(jsonStr)

            when (type) {
                LotteryType.SSQ -> {
                    val resultArr = root.optJSONArray("result") ?: return emptyList()
                    for (i in 0 until resultArr.length()) {
                        val item = resultArr.getJSONObject(i)
                        val code = item.getString("code")
                        val date = item.getString("date").take(10)
                        val red = item.getString("red").split(",").mapNotNull { it.trim().toIntOrNull() }
                        val blue = item.getString("blue").split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (red.size == 6 && blue.isNotEmpty()) {
                            list.add(LotteryRecord(code, date, red, blue))
}
}
}

                LotteryType.DLT -> {
                    val valObj = root.optJSONObject("value") ?: return emptyList()
                    val listArr = valObj.optJSONArray("list") ?: return emptyList()
                    for (i in 0 until listArr.length()) {
                        val item = listArr.getJSONObject(i)
                        val code = item.getString("lotteryDrawNum")
                        val date = item.getString("lotteryDrawTime").take(10)
                        val resultStr = item.getString("lotteryDrawResult")
                        val tokens = resultStr.split(" ").mapNotNull { it.trim().toIntOrNull() }
                        if (tokens.size >= 7) {
                            val red = tokens.take(5)
                            val blue = tokens.drop(5).take(2)

                            list.add(LotteryRecord(code, date, red, blue))
}
}
}

                LotteryType.KL8 -> {
                    val resultArr = root.optJSONArray("result") ?: return emptyList()
                    for (i in 0 until resultArr.length()) {
                        val item = resultArr.getJSONObject(i)
                        val code = item.getString("code")
                        val date = item.getString("date").take(10)
                        val red = item.getString("red").split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (red.size == 20) {
                            list.add(LotteryRecord(code, date, red))
}
}
}
}
} catch (e: Exception) {
            Log.e(TAG, "解析开奖数据异常: ${e.message}", e)

            throw IllegalStateException("解析开奖数据失败: ${e.message}", e)
}

        return list
}
}
