package com.lottery.analyzer.data.sync

import com.lottery.analyzer.data.db.LotteryDao
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.util.AppLogger
import com.lottery.analyzer.util.executeAndReadString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LotterySyncService(
    private val writeRecords: (LotteryType, List<LotteryRecord>) -> Int,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()
) {
    constructor(dao: LotteryDao) : this(dao::batchInsertOrUpdateRecords)

    suspend fun syncLatest(type: LotteryType): Result<SyncReport> = withContext(Dispatchers.IO) {
        try {
            ensureActive()
            val page = fetchPage(type, 1, 30)
            ensureActive()
            val count = writeRecords(type, page.records)
            logInvalid(type, 1, page)
            Result.success(SyncReport(count, page.invalidCount))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun syncAllHistory(
        type: LotteryType,
        onProgress: (suspend (page: Int, totalRecords: Int) -> Unit)? = null
    ): Result<SyncReport> = withContext(Dispatchers.IO) {
        var count = 0
        var invalid = 0
        val pageSize = if (type == LotteryType.SSQ) 30 else 100
        val seen = mutableSetOf<String>()
        try {
            for (number in 1..1000) {
                ensureActive()
                val page = fetchPage(type, number, pageSize)
                require(page.rawCount == 0 || seen.add(page.signature)) { "数据源返回重复页，已停止同步" }
                require(page.rawCount > 0 || !page.hasMore(number, pageSize)) { "分页信息与空页不一致" }
                ensureActive()
                count += writeRecords(type, page.records)
                invalid += page.invalidCount
                logInvalid(type, number, page)
                onProgress?.invoke(number, count)
                if (!page.hasMore(number, pageSize)) return@withContext Result.success(SyncReport(count, invalid))
            }
            error("同步超过安全页数，未确认历史数据完整性")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Result.failure(IllegalStateException("全量同步未完成，已入库 $count 条，跳过 $invalid 条异常记录：${e.message}", e))
        }
    }

    private suspend fun fetchPage(type: LotteryType, page: Int, size: Int): LotteryPage {
        val url = when (type) {
            LotteryType.DLT -> "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&provinceId=0&pageSize=$size&isVerify=1&pageNo=$page"
            else -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=${type.code}&pageNo=$page&pageSize=$size&systemType=PC"
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", if (type == LotteryType.DLT) "https://www.lottery.gov.cn/" else "https://www.cwl.gov.cn/").build()
        val (code, body) = client.newCall(request).executeAndReadString()
        check(code in 200..299) { "HTTP $code" }
        check(body.isNotBlank()) { "数据源返回空正文，无法确认同步完整性" }
        return LotteryPageParser.parse(type, body)
    }

    private fun logInvalid(type: LotteryType, page: Int, result: LotteryPage) {
        if (result.invalidCount > 0) AppLogger.w("LotterySyncService", "${type.title} 第 $page 页跳过 ${result.invalidCount}/${result.rawCount} 条异常记录")
    }
}
