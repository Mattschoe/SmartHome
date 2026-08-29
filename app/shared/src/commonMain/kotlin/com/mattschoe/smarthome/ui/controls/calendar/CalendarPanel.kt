package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.CalendarPrefs
import com.mattschoe.smarthome.data.EventMove
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarPaletteColor
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.EventEditScope
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.ui.pages.homepage.CalendarSettingsRoute
import com.mattschoe.smarthome.ui.pages.homepage.DayMarks
import com.mattschoe.smarthome.ui.pages.homepage.EventEditorTarget
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The calendar kit's entry point — the surface swap, built like the media kit's `MediaPanel`: the
 * month/week views, the event editor, or the settings. Each surface owns its own scroll, so the
 * transition never fights one.
 *
 * Both the tablet's right card and the phone's Calendar page compose *this*; what differs between
 * them is [headerTrailing] (the tablet hangs the month/week toggle there, the phone the add button,
 * since it has no tab row to hang one from) and which [calendarView] they ask for.
 */
@Composable
fun CalendarPanel(
    eventEditor: EventEditorTarget?,
    savingEvent: Boolean,
    today: LocalDate,
    displayedMonth: LocalDate,
    selectedDay: LocalDate,
    calendarView: CalendarView,
    /** Every visible event grouped by day; each view's pages slice the days they draw out of it. */
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    /** The week view's seven columns (Monday first) for the week being shown. */
    weekDays: List<LocalDate>,
    /** The span the adapter holds events for — the range both view pagers are bounded to. */
    calendarWindow: ClosedRange<LocalDate>,
    /** Minutes from midnight — where the week grid draws its "now" line. */
    nowMinutes: Int,
    calendarSources: List<CalendarSource>,
    calendarStale: Boolean,
    /** Per-day marks for the month grid's cells, keyed by date. */
    dayMarks: Map<LocalDate, DayMarks>,
    /** The week grid's hour-row height in dp — the reader's pinch level. */
    weekHourHeight: Float,
    onShowMonth: (LocalDate) -> Unit,
    onShowWeek: (LocalDate) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    /** An empty week-grid slot was tapped: open a blank form on that day, at that time. */
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    /**
     * A week block was long-pressed and dropped on a new slot of the same week. The day and minute
     * are already resolved and clamped to the week shown; what happens next — write it, or ask which
     * occurrences of a series it applies to — is the ViewModel's call.
     */
    onMoveEvent: (EventMove) -> Unit,
    onWeekHourHeight: (Float) -> Unit,
    /** The home's reminder rules — the editor's reminder row reads and resolves out of these. */
    reminders: ReminderRules,
    /** Set the reminder on the event the editor is open on (existing events only; see the editor). */
    onSetEventReminder: (ReminderRule?) -> Unit,
    onSaveEvent: (String, CalendarEventDraft, ReminderRule?, EventEditScope) -> Unit,
    onDeleteEvent: (EventEditScope) -> Unit,
    onCloseEventEditor: () -> Unit,
    /** How long a new event on a given calendar lasts — this device's setting (see `CalendarPrefs`). */
    defaultDurationFor: (String) -> Int,
    /** Which settings level has taken the panel over — the header gear opens the first of them. */
    settings: CalendarSettingsRoute?,
    /** Which calendars each view draws (this device's, per view). */
    calendarFilters: CalendarFilters,
    /** This device's own calendar colours and default event lengths. */
    calendarPrefs: CalendarPrefs,
    onToggleCalendarFilter: (String) -> Unit,
    onSetCalendarColor: (String, CalendarPaletteColor) -> Unit,
    onSetCalendarDuration: (String, Int) -> Unit,
    onSetCalendarReminderDefault: (String, Int?) -> Unit,
    /** Drill into a settings level — a calendar picked out of the list. */
    onOpenSettingsRoute: (CalendarSettingsRoute) -> Unit,
    /** The settings' back arrow: one level up, and out of the settings from the root. */
    onSettingsBack: () -> Unit,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable RowScope.() -> Unit = {},
) {
    AnimatedContent(
        targetState = when {
            eventEditor != null -> CalendarSurface.Editor
            settings != null -> CalendarSurface.Settings
            else -> CalendarSurface.Views
        },
        modifier = modifier.fillMaxSize(),
        // `using null` for the same reason as the media panel: both surfaces fill the panel, so there
        // is no container height to animate.
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { h -> h / 8 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { h -> h / 8 }) using null
        },
        label = "calendar-surface",
    ) { target ->
        // The editor state is read inside the transition, so it can be null on the frame the surface
        // animates out — the views stand in for that frame, as the browse surface does for Media. The
        // settings level is read there for the same reason, and falls back to the list it opens on.
        if (target == CalendarSurface.Settings) {
            CalendarSettingsSurface(
                route = settings ?: CalendarSettingsRoute.Calendars,
                view = calendarView,
                sources = calendarSources,
                filters = calendarFilters,
                prefs = calendarPrefs,
                reminders = reminders,
                onToggleVisible = onToggleCalendarFilter,
                onSetColor = onSetCalendarColor,
                onSetDuration = onSetCalendarDuration,
                onSetReminderDefault = onSetCalendarReminderDefault,
                onNavigate = onOpenSettingsRoute,
                onBack = onSettingsBack,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (target == CalendarSurface.Editor && eventEditor != null) {
            EventEditorSurface(
                target = eventEditor,
                saving = savingEvent,
                sources = calendarSources,
                reminders = reminders,
                onSetEventReminder = onSetEventReminder,
                onSave = onSaveEvent,
                onDelete = onDeleteEvent,
                onBack = onCloseEventEditor,
                defaultDurationFor = defaultDurationFor,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CalendarViews(
                today = today,
                displayedMonth = displayedMonth,
                selectedDay = selectedDay,
                view = calendarView,
                eventsByDay = eventsByDay,
                weekDays = weekDays,
                calendarWindow = calendarWindow,
                nowMinutes = nowMinutes,
                sources = calendarSources,
                stale = calendarStale,
                dayMarks = dayMarks,
                weekHourHeight = weekHourHeight,
                onShowMonth = onShowMonth,
                onShowWeek = onShowWeek,
                onSelectDay = onSelectDay,
                onOpenEvent = onOpenEvent,
                onOpenEventDetail = onOpenEventDetail,
                onNewEventAt = onNewEventAt,
                onMoveEvent = onMoveEvent,
                // A block cannot be picked up while a write is already going out: the drop that
                // started it is still being answered or written, and the grid may be holding the
                // moved event optimistically.
                dragEnabled = !savingEvent,
                onWeekHourHeight = onWeekHourHeight,
                headerTrailing = headerTrailing,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Which of the Calendar panel's surfaces is showing — the month/week views, the editor, or settings. */
private enum class CalendarSurface { Views, Editor, Settings }
