package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.DeviceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Arms this device's own OS alarms from the home's events and reminder rules.
 *
 * Every device computes its own schedule and arms locally, rather than the home pushing a
 * notification when the moment comes: firing then survives Home Assistant being unreachable at the
 * one moment that matters. What is *shared* is the rules, not the alarms.
 */
class ReminderScheduler(
    private val adapter: HomeAdapter,
    /** This device's alarm surface, or `null` where the platform has none (desktop, iOS). */
    private val alarms: AlarmScheduler?,
    private val role: DeviceRole,
    /**
     * This device's calendar colors, so the notification's accent is the color the calendar is drawn
     * in on this device's own screen rather than the one Home Assistant happens to carry.
     */
    private val prefs: CalendarPrefsStore = InMemoryCalendarPrefsStore(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    init {
        // The wall display is deliberately silent: its notices will come from the paged overlay it
        // is getting, never from an alarm or the system shade. One condition to flip later.
        if (alarms != null && role == DeviceRole.Phone) {
            scope.launch { run() }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun run() {
        adapter.subscribe()
            .map { it.calendar }
            // Compared by identity, not by value: the state is rebuilt on every light and volume
            // change, and deep-comparing a few hundred events each time would cost far more than the
            // rescheduling it avoids. The adapter reuses the same list instances until they change.
            .distinctUntilChanged { old, new ->
                old.events === new.events &&
                    old.sources === new.sources &&
                    old.reminders === new.reminders
            }
            // A fetched window and a replayed rule table arrive as bursts; one rearm per burst.
            .debounce(RESCHEDULE_DEBOUNCE_MS)
            .collect { calendar ->
                alarms?.replaceAll(
                    dueReminders(
                        events = calendar.events,
                        sources = applyCalendarPrefs(calendar.sources, prefs.read()),
                        rules = calendar.reminders,
                        from = Clock.System.now(),
                        zone = zone,
                    ),
                )
            }
    }

    private companion object {
        /** Long enough to swallow a retained-rule replay and the fetch that usually follows it. */
        const val RESCHEDULE_DEBOUNCE_MS = 1_500L
    }
}

/**
 * What this device can arm. [replaceAll] is the whole contract: the schedule is recomputed wholesale
 * from the calendar every time, so an implementation cancels what it armed before and arms this
 * instead — there is no incremental add or remove to get out of step.
 */
interface AlarmScheduler {
    fun replaceAll(reminders: List<ScheduledReminder>)
}

/**
 * Where the armed schedule is kept between runs, in the store the calendar snapshot already uses.
 *
 * It exists because the things that fire a reminder run with nothing else: a boot receiver has no
 * connection and no Compose tree, and a background sync has no ViewModel. Persisting the computed
 * list is what lets both re-arm from what this device already knew. Reads and writes are
 * best-effort — a lost schedule is recomputed the next time the app or the sync runs.
 */
object ReminderStore {
    private val json = Json { ignoreUnknownKeys = true }

    /** The key beside `calendar.snapshot` in the same store. */
    const val Key = "reminder.armed"

    fun read(store: KeyValueStore): List<ScheduledReminder> =
        runCatching { store.get(Key)?.let { json.decodeFromString<List<ScheduledReminder>>(it) } }
            .getOrNull()
            .orEmpty()

    fun write(store: KeyValueStore, reminders: List<ScheduledReminder>) {
        runCatching { store.put(Key, json.encodeToString(reminders)) }
    }
}
