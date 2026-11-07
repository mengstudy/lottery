package com.lottery.analyzer.data.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A local suggestion is never an official issue number: saving always requires confirmation. */
object NextIssuePolicy {
    fun increment(issue: String): String {
        if (issue.isBlank() || issue.length > 12 || issue.any { it !in '0'..'9' }) return ""
        return (issue.toBigInteger() + BigInteger.ONE).toString().padStart(issue.length, '0')
    }

    fun suggest(record: LotteryRecord?, today: LocalDate = LocalDate.now()): String {
        record ?: return ""
        val date = runCatching { LocalDate.parse(record.drawDate.take(10)) }.getOrNull() ?: return ""
        // No invented annual draw counts or holiday calendar. Ask for the official issue at boundaries.
        if (date.year != today.year || ChronoUnit.DAYS.between(date, today) !in 0..14 ||
            (date.monthValue == 12 && date.dayOfMonth >= 25)) return ""
        return increment(record.issue)
    }
}
