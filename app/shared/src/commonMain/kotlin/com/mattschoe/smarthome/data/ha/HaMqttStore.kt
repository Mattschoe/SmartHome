package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.data.reminderKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The reminder table, held in Home Assistant's MQTT broker and read over the same socket and token
 * the rest of the adapter already uses.
 *
 * It is a store rather than a message bus: every rule is a **retained** message, so subscribing to
 * [REMINDER_TOPIC_FILTER] replays the whole table (Home Assistant re-subscribes to the broker for
 * each new subscription precisely to force that replay), and publishing a change pushes it to every
 * other device at once. Deleting is publishing an empty retained payload, which is how a retained
 * topic is cleared — so removal needs no special case anywhere above this.
 *
 * A home without a broker — or a token whose user is not an admin, which `mqtt/subscribe` requires —
 * degrades to an empty table and a logged warning: reminders go quiet, nothing else breaks.
 */
internal class HaMqttStore(
    /** The adapter's own request/subscribe machinery; passing it keeps one socket and one id space. */
    private val request: suspend (
        type: String,
        extra: JsonObject?,
        onEvent: ((JsonObject) -> Unit)?,
    ) -> JsonElement,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _rules = MutableStateFlow(ReminderRules.Empty)

    /** The table as it currently stands. Kept across a dropped socket — the replay refills it. */
    val rules: StateFlow<ReminderRules> = _rules.asStateFlow()

    /**
     * Take out the one subscription the whole table arrives on. Called per session, since a
     * subscription dies with the socket that carried it.
     */
    suspend fun subscribe() {
        runCatching {
            request("mqtt/subscribe", buildJsonObject { put("topic", REMINDER_TOPIC_FILTER) }) { event ->
                handleMessage(event)
            }
        }.onFailure {
            println("HaMqttStore: reminder subscribe failed (${it.message}); reminders are off")
        }
    }

    /**
     * Set, silence or clear one event's rule. A `null` [rule] removes the record entirely, so the
     * event falls back to its calendar's default; a rule whose offset is null publishes an explicit
     * silence that overrides that default.
     */
    suspend fun setEventReminder(
        sourceId: String,
        uid: String,
        recurrenceId: String?,
        rule: ReminderRule?,
    ) {
        val topic = eventReminderTopic(sourceId, uid, recurrenceId)
        val payload = rule?.let {
            eventReminderPayload(sourceId, uid, recurrenceId, it.offsetMin).toString()
        }
        publish(topic, payload)
        // The broker echoes this back onto our own subscription within milliseconds, but the picker
        // that just closed should not blink through the old value while it does.
        val key = reminderKey(sourceId, uid, recurrenceId)
        _rules.update {
            if (rule == null) it.copy(byEvent = it.byEvent - key)
            else it.copy(byEvent = it.byEvent + (key to rule))
        }
    }

    /** Set or clear a calendar's standing default. `null` clears it. */
    suspend fun setCalendarDefault(sourceId: String, offsetMin: Int?) {
        publish(calendarReminderTopic(sourceId), offsetMin?.let { calendarReminderPayload(it).toString() })
        _rules.update {
            if (offsetMin == null) it.copy(byCalendar = it.byCalendar - sourceId)
            else it.copy(byCalendar = it.byCalendar + (sourceId to offsetMin))
        }
    }

    /**
     * Publish [payload] retained on [topic], or clear the topic when it is `null`. Awaits the reply
     * and propagates failure, unlike the adapter's fire-and-forget device calls: the caller is a
     * picker that has to say whether the change stuck.
     */
    private suspend fun publish(topic: String, payload: String?) {
        request(
            "call_service",
            buildJsonObject {
                put("domain", "mqtt")
                put("service", "publish")
                put(
                    "service_data",
                    buildJsonObject {
                        put("topic", topic)
                        // An empty retained payload is the delete: it removes the retained message
                        // for every subscriber, now and on their next connect.
                        put("payload", payload.orEmpty())
                        put("qos", 1)
                        put("retain", true)
                    },
                )
                put("target", buildJsonObject { })
            },
            null,
        )
    }

    private fun handleMessage(event: JsonObject) {
        val topic = event["topic"]?.jsonPrimitive?.contentOrNull ?: return
        // A cleared retained topic arrives as an empty payload, which is exactly the delete marker
        // [applyReminderMessage] reads — so an absent payload key is treated the same way.
        val payload = event["payload"]?.jsonPrimitive?.contentOrNull.orEmpty()
        _rules.update { applyReminderMessage(it, topic, payload, json) }
    }
}
