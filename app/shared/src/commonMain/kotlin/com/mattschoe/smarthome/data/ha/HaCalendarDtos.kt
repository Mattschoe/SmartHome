package com.mattschoe.smarthome.data.ha

import kotlinx.serialization.Serializable

/**
 * Wire models for Home Assistant's calendar and todo payloads, following the same conventions as
 * [HaStateDto] and friends: snake_case field names matching the API exactly, everything optional,
 * parsed leniently (HA sends more than we read).
 */

/**
 * One event from `GET /api/calendars/{entity_id}?start=&end=`. [start]/[end] are objects carrying
 * *either* `date` (all-day) or `dateTime` (timed). [end] is **exclusive** in both forms, per iCal.
 * [recurrence_id] is present only on an occurrence of a recurring series.
 */
@Serializable
data class HaCalendarEventDto(
    val summary: String = "",
    val description: String? = null,
    val location: String? = null,
    val uid: String? = null,
    val recurrence_id: String? = null,
    val rrule: String? = null,
    val start: HaCalendarDateDto = HaCalendarDateDto(),
    val end: HaCalendarDateDto = HaCalendarDateDto(),
)

/** A calendar boundary: `date` for an all-day event, `dateTime` for a timed one. Never both. */
@Serializable
data class HaCalendarDateDto(
    val date: String? = null,
    val dateTime: String? = null,
)

/**
 * One item pushed by the `todo/item/subscribe` stream. [status] is `needs_action` or `completed`;
 * [due] is a date (`YYYY-MM-DD`) on a list that supports due dates, or a datetime.
 */
@Serializable
data class HaTodoItemDto(
    val uid: String,
    val summary: String = "",
    val status: String = "needs_action",
    val due: String? = null,
    val description: String? = null,
)

/** The `todo/item/subscribe` event payload: the whole list, re-sent on every change. */
@Serializable
data class HaTodoItemsDto(
    val items: List<HaTodoItemDto> = emptyList(),
)
