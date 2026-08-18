package com.mattschoe.smarthome.data.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * The device-data models. [HomeState] is the single object a `HomeAdapter` exposes; the UI-selection
 * state (`activeRoom`/`panel`) lives on `HomeScreenState` in the ViewModel, not here.
 */

/**
 * Color-temperature presets for a room's light, coldest -> warmest ordering. [displayName] is
 * UI-facing, so it is Danish — the phone's warmth rows label each preset, where the tablet's swatch
 * circles carry the name only as an accessibility description.
 */
enum class Warmth(val displayName: String) {
    Candle("Stearinlys"),
    Warm("Varm"),
    Soft("Blød"),
    Neutral("Neutral"),
    Cool("Kølig"),
}

/**
 * The fixed set of rooms for this home. [displayName] is UI-facing, so it is Danish. [hasSpeaker]
 * marks which rooms have audio: the light selector lists every room, the AUDIO selector lists only
 * speaker rooms ([audioRooms]). To move or add a speaker, flip the boolean on that one line.
 */
enum class Room(val displayName: String, val hasSpeaker: Boolean) {
    LivingRoom("Stue", hasSpeaker = true),
    Kitchen("Køkken", hasSpeaker = false),
    Bedroom("Soveværelse", hasSpeaker = true),
    Bathroom("Badeværelse", hasSpeaker = false),
    Hall("Entré", hasSpeaker = false);

    companion object {
        /** The rooms the AUDIO selector offers — those with a speaker. Derived from [hasSpeaker]. */
        val audioRooms: List<Room> = entries.filter { it.hasSpeaker }
    }
}

/**
 * The mutually-exclusive right-card panels. Declaration order is the tab order, and the panel swap
 * slides off the ordinal, so a new panel belongs where it sits in the row.
 */
enum class Panel { Media, Calendar, Opgaver }

/** How the Calendar panel draws the days: the month grid, or the selected day's week as a time grid. */
enum class CalendarView { Month, Week }

/**
 * Which provider's listening the Media panel's **browse** shelves show — the two people sharing this
 * home each have their own account feeding one merged Music Assistant library. [badge] is the letter
 * on the toggle; [providerDomain] is the MA `provider_domain` / `provider` prefix that identifies a
 * row as this source's. Search is deliberately *not* scoped by this — it stays combined.
 */
enum class MusicSource(val badge: String, val providerDomain: String) {
    YtMusic("M", "ytmusic"),
    Spotify("C", "spotify"),
}

/** Repeat mode for a room's audio session, mirroring Home Assistant's `media_player` repeat states. */
enum class RepeatMode { Off, All }

/**
 * Where an enqueued item lands in the up-next list, which reads as `[playing] [user block] [auto
 * block]`: [Next] puts it at the **top** of the user block, [Last] at its **bottom** — still above the
 * auto-appended "Don't Stop the Music" continuations. With an empty user block the two do the same
 * thing.
 */
enum class QueueMode { Next, Last }


/**
 * @param album HA media_album_name
 * @param artworkUrl HA media_image_url
 * @param uri Music Assistant item uri (e.g. `ytmusic--…://track/…`); play/queue source, `null` when unknown.
 * @param queueItemId Music Assistant queue-item handle — stable per queue entry, and what the
 *   skip-to/reorder intents address. `null` for tracks that come from anywhere but a queue.
 */
@Immutable
data class MediaTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String? = null,
    val durationSec: Int,
    val uri: String? = null,
    val queueItemId: String? = null,
)

/**
 * What a browse tile actually is. The search grid routes an [Artist] tile to the artist surface
 * instead of playing it; every other kind is a play target. Media types we don't model collapse to
 * [Other].
 */
enum class BrowseKind { Track, Album, Artist, Playlist, Other }

/**
 * One tile in a browse shelf (Quick Picks, Mixed For You, Playlists). Home-wide, not owned by any
 * room — any room can play it. [subtitle] is the secondary line (artist/owner; there is no reliable
 * track-count source from Music Assistant). [artworkUrl] is real cover art when available (else the
 * tile falls back to a colored glyph); [uri] is the Music Assistant uri tapped to play.
 */
@Immutable
data class BrowseItem(
    val name: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val uri: String? = null,
    val kind: BrowseKind = BrowseKind.Other,
)

/**
 * An artist's drill-in payload: [topTracks] is played as an ordered block (tap a hit and it plays
 * from there on), [albums] are individual play targets in a rail.
 */
@Immutable
data class ArtistDetail(
    val topTracks: List<BrowseItem>,
    val albums: List<BrowseItem>,
) {
    companion object {
        /** What an adapter without a Music Assistant connection answers with. */
        val EMPTY = ArtistDetail(emptyList(), emptyList())
    }
}

/**
 * Per-room audio session. It is `null` on a [RoomState] for rooms without a speaker (no HA
 * `media_player` entity). Transport/volume transitions on a speaker-less room are no-ops.
 */
@Immutable
data class AudioState(
    val volumePct: Int,
    val isPlaying: Boolean,
    val nowPlaying: MediaTrack?,
    /**
     * HA's raw `media_position` — the position as it was *frozen* at [positionUpdatedAtIso], not
     * projected to now. Carrying the raw pair lets a playing track's state stay equal to itself
     * between device updates (see [positionUpdatedAtIso]); the projection to wall-clock time
     * happens where the value is consumed ([com.mattschoe.smarthome.data.livePositionSec]).
     */
    val positionSec: Int,
    /** HA `media_position_updated_at`, ISO-8601; `null` when absent (paused/idle, a mock, a seek). */
    val positionUpdatedAtIso: String? = null,
    val queue: List<MediaTrack>,
    val isShuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    /**
     * The room leading the sync group this room plays in: itself when it leads, another room when it
     * follows that room's playback, `null` when it plays alone. The additive grouping relation on top
     * of per-room ownership — rooms still own their own audio.
     */
    val syncLeader: Room? = null,
) { init { require(volumePct in 0..100 && positionSec >= 0) } }

/**
 * Per-room, mutable device state driven by the center card's controls. A room owns everything that
 * happens inside it, both its lights and its own audio playback ([audio], `null` when the room has
 * no speaker).
 */
@Immutable
data class RoomState(
    val brightnessPct: Int,
    val isLightOn: Boolean,
    val lightWarmth: Warmth,
    val audio: AudioState?,
) { init { require(brightnessPct in 0..100) } }

/** The condition set a Home Assistant `weather.*` entity reports as its state. */
enum class WeatherCondition {
    ClearNight, Cloudy, Exceptional, Fog, Hail, Lightning, LightningRainy,
    PartlyCloudy, Pouring, Rainy, Snowy, SnowyRainy, Sunny, Windy, WindyVariant,
}

/**
 * Read-only climate glance shown in the left card's 2×2 tile grid. Never mutated by controls. A field
 * is `null` when no sensor backs it (the real HA adapter has only a weather entity, so indoor temp,
 * humidity and energy stay null → those tiles render a "—" placeholder); the mock adapter populates
 * every field.
 *
 * [feelsLikeC] is an apparent temperature computed from the weather entity's readings rather than its
 * plain air temperature — the raw temp is a mapper input, not something the tile shows.
 */
@Immutable
data class ClimateState(
    val indoorTempC: Double?,
    val humidityPct: Int?,
    val energyKw: Double?,
    val feelsLikeC: Double?,
    val condition: WeatherCondition?,
) { init { require(humidityPct == null || humidityPct in 0..100) } }

/**
 * One of the home's calendars, as exposed by a Home Assistant `calendar.*` entity. [canWrite] comes
 * from the entity's `supported_features` (HA's `CREATE_EVENT` bit) rather than from any hardcoded
 * list, so a read-only subscription (a work roster fed from an external ICS feed) is distinguishable
 * from a locally-stored calendar the app may add events to.
 */
@Serializable
data class CalendarSource(
    val id: String,
    val displayName: String,
    val canWrite: Boolean,
    /**
     * The calendar's color as Home Assistant names it (`amber`, `primary`, `dark-grey`, …), read from
     * the entity registry's `options.calendar.color`. `null` for a calendar with no color set, which
     * leaves its dot the one assigned by position.
     */
    val color: String? = null,
)

/**
 * A calendar event **as it appears on one day**: an event spanning several days is expanded to one
 * of these per day, so a day's agenda and the month grid's dots are plain filters on [date]. [time]
 * is a pre-formatted display string ("09:00", "Hele dagen", "til 02:00"); [startMinute] and
 * [endMinute] are that day's bounds in minutes from midnight — what the week grid gives a block its
 * position and height from — and are both `null` for an all-day entry (which sorts first).
 *
 * [sourceId] is the [CalendarSource] it came from — what the agenda dot is colored by, and what a
 * write intent targets. [uid] plus [recurrenceId] address it on the backend: a recurring series
 * shares one uid across occurrences, and the recurrence id picks out a single one.
 *
 * [start] and [end] are the **whole** event's real bounds, repeated on every day it was expanded to.
 * The per-day fields above are display truth and can't be reversed into them ("til 02:00" says
 * nothing about which day it started), so the edit surface reads these instead of parsing its own
 * rows back. Nullable: an entry read from a cache written before they existed simply has none.
 *
 * [rrule] is the series' repetition rule, repeated on **every** occurrence exactly as Home Assistant
 * sends it, so the editor can open on the rule whichever occurrence was tapped. `null` on a one-off —
 * and also on a series whose backend does not report one, which is why [recurrenceId] rather than
 * this is what says an event repeats at all.
 */
@Immutable
@Serializable
data class CalendarEvent(
    val date: LocalDate,
    val title: String,
    val time: String,
    val sourceId: String = "",
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val uid: String? = null,
    val recurrenceId: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val rrule: String? = null,
)

/**
 * Which occurrences of a recurring series a write applies to — Home Assistant's `recurrence_range`.
 * [ThisEvent] edits or deletes the single occurrence addressed; [ThisAndFuture] splits the series at
 * that occurrence and applies to it and everything after.
 */
enum class RecurrenceRange { ThisEvent, ThisAndFuture }

/**
 * What a save or a delete on a recurring event was meant to reach — the question the editor asks
 * before writing, in the words it asks it in.
 *
 * This is the *surface's* vocabulary, not the wire's: the ViewModel turns it into the pair Home
 * Assistant understands, addressing the occurrence for [ThisEvent] and [ThisAndFuture] and the
 * series itself (no recurrence id at all) for [AllEvents]. Kept here beside [RecurrenceRange] so the
 * two are read together rather than one being mistaken for the other.
 */
enum class EventEditScope { ThisEvent, ThisAndFuture, AllEvents }

/**
 * An event to create or to replace an existing one with — what a create/edit surface fills in and
 * hands to the write intents. Times are wall-clock in the home's own zone; an all-day event
 * ([allDay]) ignores the time parts. [end] is **exclusive**, matching iCal and Home Assistant.
 * Attendees are deliberately absent: Home Assistant's calendar model has no such field, and which
 * calendar an event lives on already says whose it is.
 */
data class CalendarEventDraft(
    val summary: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val description: String? = null,
    val location: String? = null,
    /** An iCal RRULE (e.g. `FREQ=WEEKLY;BYDAY=MO`), or `null` for a one-off. */
    val rrule: String? = null,
)

/**
 * A to-do item, shaped to map onto a Home Assistant `todo.*` list item: [id] ↔ HA `uid` (client-
 * stable so backend echoes re-key existing rows instead of tearing them down), [label] ↔ `summary`,
 * [done] ↔ `status` (needs_action/completed), [due] the day it is bound to. "Todos for a day" is a
 * client-side filter on [due] over one shared list — the per-day bucket is a UI idea, not backend
 * structure.
 *
 * [completedOn] is the day the task was ticked off — what puts a finished task on the page of the day
 * it was finished rather than leaving it standing on every later one. Home Assistant's `todo` API
 * carries no completion timestamp (only needs_action/completed), so the day is **written into the
 * item's `description`** as a marker and read back from there. That makes it shared truth living in
 * HA rather than a per-device note: tick a task on the tablet and the phone reads the same day back,
 * whether or not it was even running at the time.
 *
 * `null` while the task is open, and cleared when a row is un-ticked so closing it again re-stamps
 * it. A completed row without a marker — ticked from the Home Assistant app, or closed before this
 * was recorded — falls back to its [due] day via [closedOn]. Every client derives that identically,
 * so they still agree.
 *
 * [createdOn] is the day the task came into existence, carried in the same `description` field by the
 * same argument: a task must not appear on days it did not yet exist on. [due] alone cannot say that
 * — it is the day the task is *for*, and nothing stops it landing in the past (a list item added from
 * the Home Assistant app with a back-dated due, an item with no due date at all, or a deliberate add
 * on a passed page). `null` for every item this app did not add, which reads as "as old as its [due]"
 * and is exactly how the checklist behaved before the day was recorded.
 */
@Serializable
data class TodoItem(
    val id: String,
    val due: LocalDate,
    val label: String,
    val done: Boolean,
    val completedOn: LocalDate? = null,
    val createdOn: LocalDate? = null,
) {
    /** The day this belongs on once ticked off — [completedOn] where we have it, else [due]. */
    val closedOn: LocalDate get() = completedOn ?: due

    /**
     * The first day this is shown on: its [due] day, but never before it existed. An open task then
     * carries forward from here onto every later page, and backwards onto none.
     */
    val showsFrom: LocalDate get() = createdOn?.let { maxOf(due, it) } ?: due
}

/**
 * One event's own reminder rule. [offsetMin] is how many minutes before the event to remind, and
 * `null` means **explicitly no reminder** — which is not the same as having no rule at all. An event
 * absent from [ReminderRules.byEvent] inherits its calendar's default; one present with a null offset
 * overrides that default with silence, which is how a single shift of a read-only work roster is
 * muted without anything being written to the roster itself.
 */
data class ReminderRule(val offsetMin: Int?) {
    companion object {
        /** The explicit "don't remind me about this one", as opposed to a missing rule. */
        val None = ReminderRule(null)
    }
}

/**
 * The home's reminder rules, as they arrive from the backend beside the events they describe. A
 * reminder is a property of the **event or the calendar**, never of the device that set it: set "1
 * time før" on a shared event here and every phone that can see the event reminds for it.
 *
 * [byEvent] is keyed by [com.mattschoe.smarthome.data.reminderKey] — `sourceId|uid` for a whole
 * series, `sourceId|uid#recurrenceId` for one occurrence of it, the occurrence winning where both
 * exist. [byCalendar] is a calendar's standing default, keyed by source id; it is what gives a
 * read-only calendar reminders at all, and what a new event's picker opens on.
 *
 * Resolution order is [com.mattschoe.smarthome.data.offsetFor]: occurrence → series → calendar
 * default → no reminder.
 */
@Immutable
data class ReminderRules(
    val byEvent: Map<String, ReminderRule> = emptyMap(),
    val byCalendar: Map<String, Int> = emptyMap(),
) {
    companion object {
        /** What a home with no reminder backend (or none set yet) has. */
        val Empty = ReminderRules()
    }
}

/**
 * The calendar payload the adapter exposes: a flat list of [events] and [todos] over whatever window
 * the adapter fetched, plus the [sources] those events came from. The current day and the displayed
 * month are UI selection (they come from the system clock / the ViewModel), not device data, so they
 * are not on here.
 *
 * [stale] marks data being rendered from the offline cache rather than from a live backend — the
 * panel still shows the last-known calendar, labelled as such, instead of going blank.
 */
@Immutable
data class CalendarState(
    val events: List<CalendarEvent>,
    val todos: List<TodoItem>,
    val sources: List<CalendarSource> = emptyList(),
    val stale: Boolean = false,
    /**
     * Whether the home has a todo list the panel can actually write to (one that carries due dates).
     * `false` makes the checklist say so instead of offering an add row whose input nothing stores —
     * the todo intents are inert without such a list.
     */
    val hasTodoList: Boolean = true,
    /**
     * Which events and calendars remind, and how long before. Device-truth like the events
     * themselves — it arrives from the backend, not from this device's own settings — so it rides
     * along here rather than needing a flow of its own.
     */
    val reminders: ReminderRules = ReminderRules.Empty,
) {
    /** The calendars an event may be written to — the add/edit surface's only legal targets. */
    val writableSources: List<CalendarSource> by lazy { sources.filter { it.canWrite } }
}

/**
 * The device-truth state for the whole home, exposed by a `HomeAdapter`
 * Each room owns its own lights *and* audio in [rooms];
 * @param playlists is the shared library any room can play from;
 * @param climate is read-only.
 * @param quickPicks single songs/albums from Music Assistant's `music/recommendations` shelves
 * @param mixedForYou the supermixes from Music Assistant's "Mixed for you" shelf
 * @param spotifyPlaylists the Spotify side's playlists, the [MusicSource.Spotify] counterpart to [playlists]
 * @param spotifyRecentlyPlayed Spotify items from MA's "recently played" folder; Spotify serves no
 *   algorithmic feed of its own, so this and [spotifyPlaylists] are all its browse side has.
 */
@Immutable
data class HomeState(
    val rooms: Map<Room, RoomState>,
    val climate: ClimateState,
    val playlists: List<BrowseItem>,
    val quickPicks: List<BrowseItem>,
    val mixedForYou: List<BrowseItem>,
    val calendar: CalendarState,
    val spotifyPlaylists: List<BrowseItem> = emptyList(),
    val spotifyRecentlyPlayed: List<BrowseItem> = emptyList(),
)
