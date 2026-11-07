package com.lottery.analyzer.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lottery.analyzer.data.model.LotteryRecord
import com.lottery.analyzer.data.model.LotteryType
import java.time.LocalDate
import java.security.MessageDigest
import java.util.Locale

/** Raw pagination and validated records must never be conflated. */
data class LotteryPage(
    val records: List<LotteryRecord>,
    val rawCount: Int,
    val invalidCount: Int,
    val totalPages: Int?,
    val signature: String
) {
    fun hasMore(page: Int, pageSize: Int): Boolean =
        totalPages?.let { page < it } ?: (rawCount >= pageSize)
}

data class SyncReport(val updatedCount: Int, val invalidCount: Int = 0)

object LotteryPageParser {
    private val spaces = Regex("\\s+")

    fun parse(type: LotteryType, text: String): LotteryPage {
        val root = JsonParser.parseString(text).asJsonObject
        val container = if (type == LotteryType.DLT) {
            requireNotNull(root.getAsJsonObject("value")) { "Missing lottery response value" }
        } else root
        val name = if (type == LotteryType.DLT) "list" else "result"
        val array = requireNotNull(container.getAsJsonArray(name)) { "Missing lottery record array" }
        val records = mutableListOf<LotteryRecord>()
        for (item in array) {
            val record = runCatching { parseRecord(type, item.asJsonObject) }.getOrNull()
            if (record != null) records.add(record)
        }
        val pages = sequenceOf(container, root).mapNotNull { obj ->
            listOf("pageCount", "totalPages", "pages").firstNotNullOfOrNull { key ->
                obj.get(key)?.let { runCatching { it.asInt }.getOrNull() }?.takeIf { it >= 0 }
            }
        }.firstOrNull()
        require(pages != 0 || array.size() == 0) { "Non-empty response with zero page count" }
        return LotteryPage(records, array.size(), array.size() - records.size, pages, MessageDigest.getInstance("SHA-256").digest(array.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) })
    }

    private fun parseRecord(type: LotteryType, item: JsonObject): LotteryRecord {
        val issue = item.get(if (type == LotteryType.DLT) "lotteryDrawNum" else "code").asString.trim()
        require(issue.isNotBlank() && issue.length <= 12 && issue.all { it in '0'..'9' })
        val date = item.get(if (type == LotteryType.DLT) "lotteryDrawTime" else "date").asString.take(10)
        LocalDate.parse(date)
        val reds: List<Int>
        val blues: List<Int>
        if (type == LotteryType.DLT) {
            val numbers = item.get("lotteryDrawResult").asString.trim().split(spaces).map { it.toInt() }
            require(numbers.size == 7)
            reds = numbers.take(5)
            blues = numbers.drop(5)
        } else {
            reds = item.get("red").asString.split(',').map { it.trim().toInt() }
            blues = if (type.blueCount > 0) item.get("blue").asString.split(',').map { it.trim().toInt() } else emptyList()
        }
        require(reds.size == type.redCount && reds.distinct().size == reds.size && reds.all { it in 1..type.maxRedNumber })
        require(blues.size == type.blueCount && blues.distinct().size == blues.size && blues.all { it in 1..type.maxBlueNumber })
        return LotteryRecord(issue, date, reds.sorted(), blues.sorted())
    }
}
