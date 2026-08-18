package com.mattschoe.smarthome.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.mattschoe.smarthome.data.AlarmScheduler
import com.mattschoe.smarthome.data.KeyValueStore
import com.mattschoe.smarthome.data.ReminderStore
import com.mattschoe.smarthome.data.ScheduledReminder

/**
 * Arms calendar reminders as OS alarms. Firing is local on purpose: the home tells this device
 * *which* reminders exist, and the device is then on its own for the moment they come due — which is
 * the moment Home Assistant is least guaranteed to be reachable.
 *
 * The schedule is always replaced wholesale. It is recomputed from the whole calendar every time, so
 * there is no incremental add or remove that could drift out of step with it; what was armed last
 * time is read back from [ReminderStore] and cancelled first.
 */
class AndroidAlarmScheduler(
    private val context: Context,
    private val store: KeyValueStore,
) : AlarmScheduler {

    override fun replaceAll(reminders: List<ScheduledReminder>) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        ReminderStore.read(store).forEach { previous ->
            pendingIntent(context, previous, create = false)?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
        // A reminder already past is dropped rather than fired late: an alarm set in the past goes
        // off immediately, which on a re-arm after a reboot would mean a burst of stale notices.
        val now = System.currentTimeMillis()
        val future = reminders.filter { it.whenMillis > now }
        future.forEach { reminder ->
            val intent = pendingIntent(context, reminder, create = true) ?: return@forEach
            if (canBeExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.whenMillis, intent)
            } else {
                // Denied exact alarms, the reminder still arrives — just inside the system's own
                // idle window rather than to the minute. Late is better than silent.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.whenMillis, intent)
            }
        }
        ReminderStore.write(store, future)
    }

    private fun canBeExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private companion object {
        /**
         * The alarm's [PendingIntent]. Its identity is the reminder's key, carried on the intent's
         * `data` as well as in the request code: extras are not part of `Intent.filterEquals`, so
         * without a distinct uri every reminder would collide onto one alarm.
         *
         * [create] false looks up an existing one to cancel and yields null when there is none.
         */
        fun pendingIntent(
            context: Context,
            reminder: ScheduledReminder,
            create: Boolean,
        ): PendingIntent? {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                data = Uri.parse("smarthome://reminder/${Uri.encode(reminder.key)}")
                putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
                putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                putExtra(ReminderReceiver.EXTRA_BODY, reminder.body)
                putExtra(ReminderReceiver.EXTRA_CALENDAR, reminder.calendarName)
                putExtra(ReminderReceiver.EXTRA_COLOR, reminder.calendarColorArgb)
                putExtra(ReminderReceiver.EXTRA_WHEN, reminder.whenMillis)
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
            return PendingIntent.getBroadcast(context, reminder.key.hashCode(), intent, flags)
        }
    }
}
