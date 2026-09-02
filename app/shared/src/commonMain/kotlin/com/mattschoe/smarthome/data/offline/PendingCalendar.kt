package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.expandCalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.sortCalendarEvents

/**
 * The calendar half of the offline outbox: how queued calendar writes fold into each other, and what
 * the panel draws while they wait. Pure — no socket, no store, no clock — so the rules can be read
 * and tested on their own.
 */

/** The uid a created event is addressed by until Home Assistant mints a real one. */
fun localUid(writeId: String): String = "$LocalUidPrefix$writeId"

/** Whether [uid] names an event that exists only in this device's outbox. */
fun isLocalUid(uid: String?): Boolean = uid?.startsWith(LocalUidPrefix) == true

private const val LocalUidPrefix = "local:"

/**
 * Fold a calendar [write] into the queue, so what is replayed on reconnect is a sequence Home
 * Assistant can actually accept:
 *
 * - an **edit of a still-queued create** rewrites that create's draft and is itself dropped. The
 *   backend has never heard of the uid, so the update would be rejected outright — and there is
 *   nothing to update anyway, since the create has not gone out;
 * - a **delete of a still-queued create** removes the create and is itself dropped: the event never
 *   existed anywhere but here, so nothing needs to be told about it;
 * - a second **edit of the same target** replaces the first — last write wins, exactly as a live
 *   connection would have ended up;
 * - a **delete** drops the edits it makes moot and is kept.
 *
 * "The same target" and "makes moot" are the same question, answered by [covers]: a write scoped to
 * one occurrence reaches only that occurrence, while one scoped to the series reaches all of them.
 */
fun coalesceCalendar(pending: List<PendingWrite>, write: PendingWrite): List<PendingWrite> = when (write) {
    is PendingWrite.CreateEvent -> pending + write

    is PendingWrite.UpdateEvent -> {
        val queuedCreate = pending.queuedCreateFor(write.uid)
        if (queuedCreate != null) {
            pending.map { if (it === queuedCreate) queuedCreate.copy(draft = write.draft) else it }
        } else {
            pending.filterNot { it is PendingWrite.UpdateEvent && write.covers(it) } + write
        }
    }

    is PendingWrite.DeleteEvent -> {
        val queuedCreate = pending.queuedCreateFor(write.uid)
        if (queuedCreate != null) {
            pending.filterNot { it === queuedCreate }
        } else {
            pending.filterNot { it is PendingWrite.UpdateEvent && write.covers(it) } + write
        }
    }

    // A write from another family has nothing to do with the calendar's rules. Unreachable while the
    // calendar is the only family — the branch is here so adding one costs no edit to this file.
    else -> pending + write
}

/** The queued create that minted [uid], or `null` — [uid] names an event the backend already has. */
private fun List<PendingWrite>.queuedCreateFor(uid: String): PendingWrite.CreateEvent? =
    if (!isLocalUid(uid)) null
    else filterIsInstance<PendingWrite.CreateEvent>().firstOrNull { localUid(it.id) == uid }

/**
 * Whether this write's target contains [other]'s: the same event, and either this one reaches the
 * whole series or the two name the same occurrence.
 */
private fun PendingWrite.covers(other: PendingWrite): Boolean {
    val (uid, recurrenceId, range) = target() ?: return false
    val (otherUid, otherRecurrenceId, _) = other.target() ?: return false
    if (uid != otherUid) return false
    if (recurrenceId == null || range != RecurrenceRange.ThisEvent) return true
    return recurrenceId == otherRecurrenceId
}

/** What a write addresses, or `null` for one that addresses no existing event. */
private fun PendingWrite.target(): Triple<String, String?, RecurrenceRange>? = when (this) {
    is PendingWrite.UpdateEvent -> Triple(uid, recurrenceId, range)
    is PendingWrite.DeleteEvent -> Triple(uid, recurrenceId, range)
    else -> null
}

/**
 * The calendar as it will be once the queue drains: [events] as the home last reported them, with
 * every queued write applied on top and its rows marked [CalendarEvent.pending].
 *
 * This is what keeps an event added during an outage on screen — through a poll that refetches
 * without it, through a rebuild, and through a restart, since the queue itself is persisted. Writes
 * are applied in queue order, so a create followed by a delete of the same local event leaves
 * nothing behind.
 *
 * One limit is deliberate: a queued write's recurrence rule is not expanded, so a repeating event
 * created offline shows as its first occurrence alone until the write lands and Home Assistant sends
 * back the series. Expanding an RRULE here would mean a second implementation of the backend's own
 * expansion, disagreeing with it in exactly the cases that are hard.
 */
fun applyPendingEvents(events: List<CalendarEvent>, pending: List<PendingWrite>): List<CalendarEvent> {
    if (pending.isEmpty()) return events
    var rows = events
    for (write in pending) {
        rows = when (write) {
            is PendingWrite.CreateEvent ->
                rows + write.draft.expand(write.sourceId, uid = localUid(write.id), recurrenceId = null)

            is PendingWrite.UpdateEvent ->
                rows.filterNot { it.isTargetedBy(write.uid, write.recurrenceId, write.range) } +
                    write.draft.expand(
                        write.sourceId,
                        uid = write.uid,
                        // A series-wide edit replaces the whole uid with one un-expanded event; an
                        // edit of one occurrence stays that occurrence.
                        recurrenceId = write.recurrenceId.takeIf { write.range == RecurrenceRange.ThisEvent },
                    )

            is PendingWrite.DeleteEvent ->
                rows.filterNot { it.isTargetedBy(write.uid, write.recurrenceId, write.range) }

            // A write from another family leaves the calendar alone. Unreachable today, for the same
            // reason as in [coalesceCalendar], and kept for the same one.
            else -> rows
        }
    }
    return sortCalendarEvents(rows)
}

/** Whether this row is one a write scoped by [uid]/[recurrenceId]/[range] replaces or removes. */
private fun CalendarEvent.isTargetedBy(uid: String, recurrenceId: String?, range: RecurrenceRange): Boolean {
    if (this.uid != uid) return false
    if (recurrenceId == null || range != RecurrenceRange.ThisEvent) return true
    return this.recurrenceId == recurrenceId
}

/** The per-day rows a queued draft draws as, marked as not yet acknowledged by the home. */
private fun CalendarEventDraft.expand(
    sourceId: String,
    uid: String,
    recurrenceId: String?,
): List<CalendarEvent> = expandCalendarEvent(
    sourceId = sourceId,
    title = summary,
    start = start,
    end = end,
    allDay = allDay,
    uid = uid,
    recurrenceId = recurrenceId,
    location = location,
    rrule = rrule,
    pending = true,
)
