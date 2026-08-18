package com.mattschoe.smarthome.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mattschoe.smarthome.MainActivity
import com.mattschoe.smarthome.R
import com.mattschoe.smarthome.data.AppNotification
import com.mattschoe.smarthome.data.NotificationPresenter

/**
 * How an Android phone says something: a notification in the shade. The concrete side of
 * [NotificationPresenter] — commonMain decided *what* is worth saying, this decides how it looks.
 *
 * The calendar is named on the notification itself ([NotificationCompat.setSubText]) and coloured
 * with that calendar's own dot colour, because a reminder on a shared event has to answer "whose is
 * this" without being opened. Per-calendar *channels* are the natural next step if muting one
 * calendar's reminders is ever wanted; one channel is right while the answer for all of them is the
 * same.
 */
class SystemNotificationPresenter(private val context: Context) : NotificationPresenter {

    private val manager = NotificationManagerCompat.from(context)

    override fun post(notification: AppNotification) {
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setSubText(notification.label)
            .setWhen(notification.whenMillis)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply { notification.colorArgb?.let { setColor(it); setColorized(false) } }
            .build()
        // Denied POST_NOTIFICATIONS is a fine outcome, exactly as for the media session: nothing is
        // shown and nothing breaks.
        runCatching { manager.notify(notification.id.hashCode(), built) }
    }

    override fun dismiss(id: String) {
        manager.cancel(id.hashCode())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            "Kalenderpåmindelser",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Påmindelser om begivenheder i hjemmets kalendere"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val ChannelId = "calendar-reminders"
    }
}
