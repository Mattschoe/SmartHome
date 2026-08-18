package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.data.reminderKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The wire format of the reminder table: one retained MQTT message per rule, where the **topic is the
 * key and the payload is the record**. Nothing is smuggled into a field meant for something else, and
 * the retained messages *are* the persistence — a reconnecting app is handed the whole table by the
 * broker, and a change made on one phone reaches every other device the moment it is published.
 *
 * Pure mapping only: [HaMqttStore] owns the socket, this owns what goes over it.
 */

/** Everything the app publishes and subscribes to lives under this one root. */
const val REMINDER_TOPIC_ROOT = "smarthome/reminders"

/** The wildcard the app takes out a single subscription on, which replays the whole table. */
const val REMINDER_TOPIC_FILTER = "$REMINDER_TOPIC_ROOT/#"

/**
 * Record version. A payload from a future version is ignored rather than half-read — an old app
 * left running on the wall must not act on a record it doesn't understand.
 */
const val REMINDER_RECORD_VERSION = 1

/**
 * The topic one event's rule lives on. The key is base64url-encoded because a calendar event's uid
 * is an arbitrary string from whatever produced it, and an MQTT topic segment can carry neither `/`
 * nor `+` nor `#`. The real key is repeated inside the payload, so reading never has to parse a
 * topic — only the delete path does, having no payload to read.
 */
fun eventReminderTopic(sourceId: String, uid: String, recurrenceId: String? = null): String =
    "$REMINDER_TOPIC_ROOT/event/${encodeTopicSegment(reminderKey(sourceId, uid, recurrenceId))}"

/**
 * The topic one calendar's standing default lives on. Encoded plainly, unlike an event key: a Home
 * Assistant entity id is `[a-z0-9_.]` and so is already a legal topic segment, and leaving it
 * readable is what makes the table legible in Developer tools → MQTT.
 */
fun calendarReminderTopic(sourceId: String): String = "$REMINDER_TOPIC_ROOT/calendar/$sourceId"

/** One event's rule, as published. A null [offsetMin] is an explicit "no reminder for this one". */
@Serializable
data class HaEventReminderDto(
    val v: Int = 0,
    val sourceId: String = "",
    val uid: String = "",
    val recurrenceId: String? = null,
    val offsetMin: Int? = null,
)

/** One calendar's standing default, as published. */
@Serializable
data class HaCalendarReminderDto(
    val v: Int = 0,
    val offsetMin: Int? = null,
)

/** The payload that sets an event's rule. */
fun eventReminderPayload(
    sourceId: String,
    uid: String,
    recurrenceId: String?,
    offsetMin: Int?,
): JsonObject = buildJsonObject {
    put("v", REMINDER_RECORD_VERSION)
    put("sourceId", sourceId)
    put("uid", uid)
    if (recurrenceId != null) put("recurrenceId", recurrenceId)
    if (offsetMin != null) put("offsetMin", offsetMin)
}

/** The payload that sets a calendar's default. */
fun calendarReminderPayload(offsetMin: Int): JsonObject = buildJsonObject {
    put("v", REMINDER_RECORD_VERSION)
    put("offsetMin", offsetMin)
}

/**
 * Fold one retained-or-live MQTT message into the rule table. An **empty payload is a delete** — the
 * same message that clears a retained topic on the broker — so removal needs no record of its own.
 * A message on an unknown topic, an unparseable payload, or a record from another
 * [REMINDER_RECORD_VERSION] leaves the table exactly as it was.
 */
fun applyReminderMessage(
    rules: ReminderRules,
    topic: String,
    payload: String,
    json: Json,
): ReminderRules {
    val rest = topic.removePrefix("$REMINDER_TOPIC_ROOT/")
    if (rest == topic) return rules
    val slash = rest.indexOf('/')
    if (slash <= 0 || slash == rest.lastIndex) return rules
    val kind = rest.substring(0, slash)
    val segment = rest.substring(slash + 1)
    val body = payload.trim()
    return when (kind) {
        "event" -> {
            if (body.isEmpty()) {
                val key = decodeTopicSegment(segment) ?: return rules
                if (key !in rules.byEvent) rules else rules.copy(byEvent = rules.byEvent - key)
            } else {
                val dto = runCatching { json.decodeFromString<HaEventReminderDto>(body) }.getOrNull()
                    ?: return rules
                if (dto.v != REMINDER_RECORD_VERSION || dto.sourceId.isEmpty() || dto.uid.isEmpty()) {
                    return rules
                }
                val key = reminderKey(dto.sourceId, dto.uid, dto.recurrenceId)
                rules.copy(byEvent = rules.byEvent + (key to ReminderRule(dto.offsetMin)))
            }
        }
        "calendar" -> {
            if (body.isEmpty()) {
                if (segment !in rules.byCalendar) rules else rules.copy(byCalendar = rules.byCalendar - segment)
            } else {
                val dto = runCatching { json.decodeFromString<HaCalendarReminderDto>(body) }.getOrNull()
                    ?: return rules
                if (dto.v != REMINDER_RECORD_VERSION) return rules
                // A default of "none" is simply not having one — the calendar's rows then inherit
                // nothing, which is what an absent key already means.
                val offset = dto.offsetMin
                    ?: return rules.copy(byCalendar = rules.byCalendar - segment)
                rules.copy(byCalendar = rules.byCalendar + (segment to offset))
            }
        }
        else -> rules
    }
}

// --- base64url, unpadded. Hand-rolled rather than pulled from an experimental stdlib API: it is
// twenty lines, it is exercised by its own test, and it must not change under a Kotlin upgrade
// while retained topics published by an older build are still sitting on the broker.

private const val Base64UrlAlphabet =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** UTF-8 bytes of [raw] as unpadded base64url — a legal single MQTT topic segment for any string. */
fun encodeTopicSegment(raw: String): String {
    val bytes = raw.encodeToByteArray()
    val sb = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
        sb.append(Base64UrlAlphabet[b0 ushr 2])
        sb.append(Base64UrlAlphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
        if (b1 < 0) break
        sb.append(Base64UrlAlphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
        if (b2 < 0) break
        sb.append(Base64UrlAlphabet[b2 and 0x3F])
        i += 3
    }
    return sb.toString()
}

/** The inverse of [encodeTopicSegment]; `null` for anything that is not one of its outputs. */
fun decodeTopicSegment(segment: String): String? {
    if (segment.isEmpty()) return null
    val out = ByteArray(segment.length * 3 / 4)
    var written = 0
    var buffer = 0
    var bits = 0
    for (c in segment) {
        val v = Base64UrlAlphabet.indexOf(c)
        if (v < 0) return null
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out[written++] = ((buffer ushr bits) and 0xFF).toByte()
        }
    }
    return out.decodeToString(0, written)
}
