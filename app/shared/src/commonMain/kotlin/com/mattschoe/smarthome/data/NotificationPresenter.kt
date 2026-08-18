package com.mattschoe.smarthome.data

/**
 * The seam between "something in the home wants to tell you" and however this device says it.
 * Modelled on [NowPlayingBridge]: commonMain decides *what* is worth saying, the platform decides
 * *how* — a system notification on a phone, nothing at all on the tablet today.
 *
 * Deliberately generic rather than calendar-shaped. The wall display's coming notification centre is
 * a paged overlay that accepts notices from any part of the dashboard, appends them live, and drops
 * a page when someone taps "done" on their phone; when it arrives it becomes a third
 * [NotificationPresenter] and nothing above this moves. Calendar reminders are simply its first
 * producer.
 */

/**
 * One notice, flattened so a presenter needs nothing else to show it.
 *
 * @param id stable identity — what [NotificationPresenter.dismiss] takes, and what the remote "done"
 *   will address once the overlay exists.
 * @param source which part of the dashboard raised it ("calendar" today), so the overlay can group
 *   or route by producer.
 * @param label a short attribution shown beside the title — for a reminder, the calendar's name,
 *   which is what makes a shared event's notification say whose it is.
 * @param colorArgb the producer's own colour (a calendar's dot colour), or `null` for the default.
 * @param actionable whether the notice carries something to act on. Nothing sets it yet; it is what
 *   the paged overlay will read to decide whether a page gets a "done".
 */
data class AppNotification(
    val id: String,
    val source: String,
    val title: String,
    val body: String?,
    val label: String?,
    val colorArgb: Int?,
    val whenMillis: Long,
    val actionable: Boolean = false,
)

/** How this device shows a notice, if it shows one at all. */
interface NotificationPresenter {
    fun post(notification: AppNotification)

    /** Take a notice back down — what the remote "done" will call once the overlay exists. */
    fun dismiss(id: String)
}

/**
 * The presenter for every device that says nothing: the wall display (which gets its notices from the
 * overlay it doesn't have yet, never from the system shade), desktop, and iOS.
 */
object NoOpNotificationPresenter : NotificationPresenter {
    override fun post(notification: AppNotification) = Unit
    override fun dismiss(id: String) = Unit
}
