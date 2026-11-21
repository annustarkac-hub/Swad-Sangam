package com.swadsangam.restaurant.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All times shown in India Standard Time (PRD §13). */
object TimeFmt {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val timeF = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateTimeF = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

    private fun parse(iso: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(iso) }.getOrNull()

    fun time(iso: String): String = parse(iso)?.atZoneSameInstant(IST)?.format(timeF) ?: ""
    fun dateTime(iso: String): String = parse(iso)?.atZoneSameInstant(IST)?.format(dateTimeF) ?: ""
    fun minutesSince(iso: String, nowMillis: Long = System.currentTimeMillis()): Long =
        parse(iso)?.let { (nowMillis - it.toInstant().toEpochMilli()) / 60000 } ?: 0
}
