package com.mattschoe.smarthome.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mattschoe.smarthome.data.AppNotification

/**
 * What an armed alarm wakes up. It looks nothing up: everything the notification needs was flattened
 * into the intent when the alarm was set, because this can run with no connection, no ViewModel and
 * no Compose tree — after a reboot, or on a phone that has not been opened in days.
 *
 * It hands the notice to a [com.mattschoe.smarthome.data.NotificationPresenter] rather than building
 * the notification itself, so the wall display's coming notification centre becomes another
 * presenter with nothing here changing.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        SystemNotificationPresenter(context.applicationContext).post(
            AppNotification(
                id = key,
                source = "calendar",
                title = title,
                body = intent.getStringExtra(EXTRA_BODY),
                label = intent.getStringExtra(EXTRA_CALENDAR),
                colorArgb = intent.getIntExtra(EXTRA_COLOR, 0).takeIf { it != 0 },
                whenMillis = intent.getLongExtra(EXTRA_WHEN, System.currentTimeMillis()),
            ),
        )
    }

    companion object {
        const val EXTRA_KEY = "reminder.key"
        const val EXTRA_TITLE = "reminder.title"
        const val EXTRA_BODY = "reminder.body"
        const val EXTRA_CALENDAR = "reminder.calendar"
        const val EXTRA_COLOR = "reminder.color"
        const val EXTRA_WHEN = "reminder.when"
    }
}
