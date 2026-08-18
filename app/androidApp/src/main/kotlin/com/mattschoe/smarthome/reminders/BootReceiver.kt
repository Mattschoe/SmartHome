package com.mattschoe.smarthome.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mattschoe.smarthome.data.ReminderStore
import com.mattschoe.smarthome.data.SharedPreferencesStore

/**
 * Re-arms the reminders after a restart. A `PendingIntent` does not survive a reboot — nor an app
 * update, hence `MY_PACKAGE_REPLACED` — so without this a phone that rebooted at midnight would
 * silently fire nothing until the dashboard was next opened.
 *
 * It re-arms from the schedule this device already computed and persisted, touching neither Home
 * Assistant nor Compose: at boot there is very likely no network yet, and the alarms are already
 * known.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        val store = SharedPreferencesStore(app)
        // Re-arming with the same list is what puts the alarms back; the scheduler drops whatever
        // came due while the phone was off rather than firing a burst of stale notices.
        AndroidAlarmScheduler(app, store).replaceAll(ReminderStore.read(store))
    }
}
