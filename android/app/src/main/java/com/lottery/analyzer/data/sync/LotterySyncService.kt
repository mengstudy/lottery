package com.lottery.analyzer.data.sync

import android.util.Log
import com.lottery.analyzer.data.db.LotteryDao
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LotterySyncService(private val dao: LotteryDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "LotterySyncService"
    }

    /**
     * 联网同步最新开奖数据
     */
    suspend fun syncLatest(type: LotteryType): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 这里提供公开开放数据源同步示例，支持解析最新开奖并持久化到本地 SQLite
            val apiUrl = when (type) {
                LotteryType.SSQ -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount=10"
                LotteryType.DLT -> "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&provinceId=0&pageSize=10&isVerify=1&pageNo=1"
                LotteryType.KL8 -> "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=kl8&issueCount=10"
            }

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", if (type == LotteryType.DLT) "https://www.lottery.gov.cn/" else "https://www.cwl.gov.cn/")
                .build()

            var count = 0
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP 响应异常: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("响应内容为空"))
                val records = parseResponse(type, body)
                for (rec in records) {
                    if (dao.insertOrUpdateRecord(type, rec)) {
                        count++
                    }
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "同步失败: ${e.message}")
            Result.failure(e)
        }
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
            Log.e(TAG, "解析数据异常: ${e.message}")
        }
        return list
    }
}
