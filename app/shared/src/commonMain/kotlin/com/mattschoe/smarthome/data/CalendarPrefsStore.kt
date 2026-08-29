package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarPaletteColor
import com.mattschoe.smarthome.data.model.CalendarSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What this device has been told about the home's calendars: the color each one is drawn in, and how
 * long a new event on it lasts by default.
 *
 * Both are deliberately **per device, never shared**. A calendar's color is how one person finds
 * their own things in a grid full of everyone's — Matt may want "Papkassehuset" blue while Cecilie
 * wants it yellow and the tablet in the hall keeps it green, and none of them is wrong. The same goes
 * for how long an event lasts: whoever books the shared calendar in two-hour blocks should not have
 * to impose that on the phone that only ever adds half-hour errands.
 *
 * This is the opposite choice from [com.mattschoe.smarthome.data.model.ReminderRules], which lives in
 * Home Assistant precisely *because* it must be agreed on — it is HA that fires the notification, and
 * a reminder only one device knows about is a reminder that misses when that device is asleep.
 *
 * Both maps are keyed by calendar entity id and hold only what has actually been chosen, so a
 * calendar added in Home Assistant later arrives with its HA color and the standard hour rather than
 * with whatever an absent entry would have had to mean.
 */
@Serializable
data class CalendarPrefs(
    val colorById: Map<String, CalendarPaletteColor> = emptyMap(),
    /** How long a new event lasts, in minutes. Absent means [DefaultEventDurationMinutes]. */
    val durationById: Map<String, Int> = emptyMap(),
) {
    /** Give [sourceId] a color of its own. */
    fun withColor(sourceId: String, color: CalendarPaletteColor): CalendarPrefs =
        copy(colorById = colorById + (sourceId to color))

    /**
     * Set how long a new event on [sourceId] lasts. Anything outside what the grid can sensibly draw
     * is clamped rather than refused — the surface only offers [EVENT_DURATIONS], but a blob written
     * by a future version can hand this anything.
     */
    fun withDuration(sourceId: String, minutes: Int): CalendarPrefs =
        copy(durationById = durationById + (sourceId to clampEventDuration(minutes)))

    /** How long a new event on [sourceId] lasts, falling back to the app's standard hour. */
    fun durationFor(sourceId: String): Int =
        durationById[sourceId]?.let(::clampEventDuration) ?: DefaultEventDurationMinutes
}

/** How long a new event lasts on a calendar that has not been given a length of its own. */
const val DefaultEventDurationMinutes = 60

/** The lengths the settings surface offers, in minutes. */
val EVENT_DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180, 240, 360, 480)
/**
 * A length the week grid can actually draw: at least a slot tall, and never past the end of the day
 * it starts on.
 */
fun clampEventDuration(minutes: Int): Int = minutes.coerceIn(EVENT_DURATIONS.first(), 24 * 60)

/**
 * Fold this device's choices onto the calendars the adapter reported. Applied on the way out of the
 * ViewModel rather than inside the adapter, which keeps the offline snapshot
 * ([CalendarCache]) plain device truth — a stale snapshot can then never reinstate a color somebody
 * has since changed.
 */
fun applyCalendarPrefs(sources: List<CalendarSource>, prefs: CalendarPrefs): List<CalendarSource> {
    // The same list back when nothing is overridden — including when every stored choice names a
    // calendar that is gone. This runs on every state emission, so the no-op case allocates nothing.
    if (prefs.colorById.isEmpty() || sources.none { it.id in prefs.colorById }) return sources
    return sources.map { source ->
        prefs.colorById[source.id]?.let { source.copy(colorOverride = it) } ?: source
    }
}

/** Where [CalendarPrefs] are kept between runs. Reads and writes are best-effort, never fatal. */
interface CalendarPrefsStore {
    fun read(): CalendarPrefs
    fun write(prefs: CalendarPrefs)
}

/** A [CalendarPrefsStore] over a platform [KeyValueStore], holding the choices as one JSON string. */
class KeyValueCalendarPrefsStore(private val store: KeyValueStore) : CalendarPrefsStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): CalendarPrefs {
        val raw = runCatching { store.get(Key) }.getOrNull() ?: return CalendarPrefs()
        // A blob that can't be understood falls back to the defaults for the same reason the filters
        // do: the calendars still draw, in their HA colors, and the next choice writes a good one.
        return runCatching { json.decodeFromString<CalendarPrefs>(raw) }.getOrDefault(CalendarPrefs())
    }

    override fun write(prefs: CalendarPrefs) {
        runCatching { store.put(Key, json.encodeToString(prefs)) }
    }

    private companion object {
        const val Key = "calendar.prefs"
    }
}

/** Choices that live only as long as the process — the fallback where no [KeyValueStore] exists. */
class InMemoryCalendarPrefsStore : CalendarPrefsStore {
    private var prefs = CalendarPrefs()

    override fun read(): CalendarPrefs = prefs

    override fun write(prefs: CalendarPrefs) {
        this.prefs = prefs
    }
}
