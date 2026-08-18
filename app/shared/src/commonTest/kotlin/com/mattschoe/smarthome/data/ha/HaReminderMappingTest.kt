package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.data.reminderKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Json_ = Json { ignoreUnknownKeys = true; isLenient = true }

private fun apply(rules: ReminderRules, topic: String, payload: String) =
    applyReminderMessage(rules, topic, payload, Json_)

class HaReminderMappingTest {

    @Test
    fun `a topic segment round-trips whatever a uid happens to be`() {
        val awkward = listOf(
            "calendar.matt|abc123",
            "calendar.c_arbejde|shift/2026-08-20#T14:00+02:00",
            "calendar.matt|æøå ünïcode",
            "calendar.matt|a",
            "calendar.matt|ab",
        )
        awkward.forEach { key ->
            val encoded = encodeTopicSegment(key)
            assertTrue(
                encoded.none { it == '/' || it == '+' || it == '#' || it == '=' },
                "'$encoded' must be a legal single MQTT topic segment",
            )
            assertEquals(key, decodeTopicSegment(encoded))
        }
    }

    @Test
    fun `a non-base64url segment decodes to null rather than to nonsense`() {
        assertNull(decodeTopicSegment("not a segment!"))
        assertNull(decodeTopicSegment(""))
    }

    @Test
    fun `an event record is keyed off its payload, not off its topic`() {
        val payload = eventReminderPayload("calendar.matt", "uid-1", null, 60).toString()
        val rules = apply(ReminderRules.Empty, eventReminderTopic("calendar.matt", "uid-1"), payload)
        assertEquals(
            mapOf(reminderKey("calendar.matt", "uid-1") to ReminderRule(60)),
            rules.byEvent,
        )
    }

    @Test
    fun `an occurrence record keys on its recurrence id`() {
        val topic = eventReminderTopic("calendar.matt", "uid-1", "20260820T120000")
        val payload = eventReminderPayload("calendar.matt", "uid-1", "20260820T120000", 10).toString()
        val rules = apply(ReminderRules.Empty, topic, payload)
        assertEquals(
            setOf(reminderKey("calendar.matt", "uid-1", "20260820T120000")),
            rules.byEvent.keys,
        )
    }

    @Test
    fun `a record with no offset is an explicit silence, not a delete`() {
        val payload = eventReminderPayload("calendar.matt", "uid-1", null, null).toString()
        val rules = apply(ReminderRules.Empty, eventReminderTopic("calendar.matt", "uid-1"), payload)
        assertEquals(ReminderRule.None, rules.byEvent[reminderKey("calendar.matt", "uid-1")])
    }

    @Test
    fun `an empty payload deletes the rule`() {
        val key = reminderKey("calendar.matt", "uid-1")
        val start = ReminderRules(byEvent = mapOf(key to ReminderRule(60)))
        val rules = apply(start, eventReminderTopic("calendar.matt", "uid-1"), "")
        assertTrue(rules.byEvent.isEmpty())
    }

    @Test
    fun `a calendar default is set, replaced and cleared`() {
        val topic = calendarReminderTopic("calendar.arbejde")
        var rules = apply(ReminderRules.Empty, topic, calendarReminderPayload(30).toString())
        assertEquals(mapOf("calendar.arbejde" to 30), rules.byCalendar)
        rules = apply(rules, topic, calendarReminderPayload(60).toString())
        assertEquals(mapOf("calendar.arbejde" to 60), rules.byCalendar)
        rules = apply(rules, topic, "")
        assertTrue(rules.byCalendar.isEmpty())
    }

    @Test
    fun `a record from another version is ignored`() {
        val future = """{"v":99,"sourceId":"calendar.matt","uid":"uid-1","offsetMin":60}"""
        val rules = apply(ReminderRules.Empty, eventReminderTopic("calendar.matt", "uid-1"), future)
        assertTrue(rules.byEvent.isEmpty())
    }

    @Test
    fun `garbage and foreign topics leave the table alone`() {
        val start = ReminderRules(
            byEvent = mapOf(reminderKey("calendar.matt", "uid-1") to ReminderRule(60)),
            byCalendar = mapOf("calendar.arbejde" to 30),
        )
        assertEquals(start, apply(start, "homeassistant/status", "online"))
        assertEquals(start, apply(start, "$REMINDER_TOPIC_ROOT/nonsense/x", "{}"))
        assertEquals(start, apply(start, eventReminderTopic("calendar.matt", "uid-2"), "not json"))
    }
}
