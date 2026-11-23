package com.alekseevich47.notes.ui.notes

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val stampZone: ZoneId = ZoneId.systemDefault()

// Shared by every row. A formatter built inside the function would allocate on each card.
private val stampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

internal fun formatStamp(epoch: Long, zone: ZoneId = stampZone): String {
    return stampFormatter.format(Instant.ofEpochMilli(epoch).atZone(zone))
}
