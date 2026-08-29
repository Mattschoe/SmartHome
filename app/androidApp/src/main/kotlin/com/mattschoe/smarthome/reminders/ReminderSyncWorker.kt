package com.mattschoe.smarthome.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mattschoe.smarthome.data.HomeAssistantAdapter
import com.mattschoe.smarthome.data.KeyValueCalendarCache
import com.mattschoe.smarthome.data.SharedPreferencesStore
import com.mattschoe.smarthome.data.KeyValueCalendarPrefsStore
import com.mattschoe.smarthome.data.applyCalendarPrefs
import com.mattschoe.smarthome.data.dueReminders
import com.mattschoe.smarthome.haConfigFromSecrets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Keeps this phone's alarms in step with the home while the dashboard is not running.
 *
 * Not optional, and not a nicety: alarms are otherwise only recomputed while the app is open, so a
 * phone whose owner never opens the dashboard would never learn about an event the *other* phone
 * created — and a reminder that only works for the person who set it is not the feature.
 *
 * It builds its own short-lived Home Assistant connection, waits for a live calendar (and the
 * retained reminder rules that arrive alongside it), recomputes the schedule, arms it and puts the
 * connection down again.
 */
class ReminderSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = haConfigFromSecrets()?.takeIf { it.hasToken } ?: return Result.success()
        val store = SharedPreferencesStore(applicationContext)
        val adapter = HomeAssistantAdapter(config, KeyValueCalendarCache(store))
        try {
            // A stale calendar is the cache, not the home. Waiting for it to go live is what makes
            // this worth running at all; without a live window there is nothing new to schedule.
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                adapter.subscribe().first { !it.calendar.stale }
            } ?: return Result.retry()
            // The rule table replays over MQTT independently of the calendar fetch, and usually
            // lands first; this is the small margin that keeps a race from arming with no rules.
            delay(RULES_SETTLE_MS)
            val calendar = adapter.subscribe().value.calendar
            AndroidAlarmScheduler(applicationContext, store).replaceAll(
                dueReminders(
                    events = calendar.events,
                    // The same store the app reads, so a rescheduled alarm keeps the color this
                    // device draws the calendar in.
                    sources = applyCalendarPrefs(calendar.sources, KeyValueCalendarPrefsStore(store).read()),
                    rules = calendar.reminders,
                    from = Clock.System.now(),
                ),
            )
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        } finally {
            adapter.close()
        }
    }

    companion object {
        private const val UniqueName = "reminder-sync"
        private const val CONNECT_TIMEOUT_MS = 60_000L
        private const val RULES_SETTLE_MS = 2_000L

        /**
         * Register the periodic sync. Idempotent — [ExistingPeriodicWorkPolicy.KEEP] leaves an
         * already-scheduled run alone, so calling this on every app start doesn't restart the clock.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Stop syncing — what the wall display does, having nothing to arm. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UniqueName)
        }
    }
}
