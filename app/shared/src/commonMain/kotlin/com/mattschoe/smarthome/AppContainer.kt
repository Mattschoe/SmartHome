package com.mattschoe.smarthome

import com.mattschoe.smarthome.data.CalendarFilterStore
import com.mattschoe.smarthome.data.AlarmScheduler
import com.mattschoe.smarthome.data.CompositeHomeAdapter
import com.mattschoe.smarthome.data.HaConfig
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.HomeAssistantAdapter
import com.mattschoe.smarthome.data.InMemoryCalendarFilterStore
import com.mattschoe.smarthome.data.InMemoryWeekZoomStore
import com.mattschoe.smarthome.data.KeyValueCalendarCache
import com.mattschoe.smarthome.data.KeyValueCalendarFilterStore
import com.mattschoe.smarthome.data.KeyValueStore
import com.mattschoe.smarthome.data.KeyValueWeekZoomStore
import com.mattschoe.smarthome.data.WeekZoomStore
import com.mattschoe.smarthome.data.MaConfig
import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.MusicAssistantAdapter
import com.mattschoe.smarthome.data.NoOpNotificationPresenter
import com.mattschoe.smarthome.data.NotificationPresenter
import com.mattschoe.smarthome.data.NowPlayingBridge
import com.mattschoe.smarthome.data.ReminderScheduler
import com.mattschoe.smarthome.data.platformKeyValueStore

/**
 * Manual DI container. Holds shared dependencies (adapters, repositories) and is constructed in each
 * platform entry point, then passed into `App()`.
 *
 * The [homeAdapter] is chosen by config (see [buildHomeAdapter]): a live [HomeAssistantAdapter] for
 * devices, wrapped in a [CompositeHomeAdapter] that adds a [MusicAssistantAdapter] when an MA token
 * is present, and the in-memory [MockAdapter] otherwise (the default, and what previews use).
 */
class AppContainer(
    haConfig: HaConfig? = haConfigFromSecrets(),
    maConfig: MaConfig? = maConfigFromSecrets(haConfig),
    /**
     * Where the offline calendar snapshot is kept. Defaults to the platform's own store; Android has
     * no ambient context to build one from, so `AppApplication` passes a
     * [com.mattschoe.smarthome.data.SharedPreferencesStore] instead. `null` simply disables the cache.
     */
    keyValueStore: KeyValueStore? = platformKeyValueStore(),
    val homeAdapter: HomeAdapter = buildHomeAdapter(haConfig, maConfig, keyValueStore),
    /**
     * Which calendars each Calendar view draws. Kept beside the snapshot in the same store; without
     * one the filters simply last as long as the process does.
     */
    val calendarFilters: CalendarFilterStore =
        keyValueStore?.let(::KeyValueCalendarFilterStore) ?: InMemoryCalendarFilterStore(),
    /**
     * How far the week grid's hours are pinched together. Kept beside the filters, and for the same
     * reason: a wall tablet is restarted by a power cut, not by anyone finishing with the calendar.
     */
    val weekZoom: WeekZoomStore = keyValueStore?.let(::KeyValueWeekZoomStore) ?: InMemoryWeekZoomStore(),
    /**
     * Where the ViewModel publishes the active audio room's playback for the platform's own media
     * surfaces. Android's media session reads it; the platforms without one never look.
     */
    val nowPlaying: NowPlayingBridge = NowPlayingBridge(),
    /**
     * What this device is in the home. Not a size class and not a setting: it decides whether the
     * device says anything at all when a reminder comes due, and the wall display deliberately says
     * nothing (see [ReminderScheduler]). The Android entry point works it out from the screen; every
     * other target is somebody's own device.
     */
    val deviceRole: DeviceRole = DeviceRole.Phone,
    /**
     * How this device arms OS alarms, or `null` where it has none. Android's needs a `Context`, so —
     * exactly like the key/value store — `AppApplication` builds one and passes it in rather than
     * this resolving it; desktop and iOS have no alarm surface at all and stay null.
     */
    alarmScheduler: AlarmScheduler? = null,
    /**
     * How this device shows a notice. The seam the wall display's coming notification centre plugs
     * into; today only the Android phone has a real one.
     */
    val notifications: NotificationPresenter = NoOpNotificationPresenter,
) {
    /**
     * Keeps this device's alarms in step with the home's events and reminder rules. Held rather than
     * merely constructed: it subscribes for the life of the process.
     */
    val reminders: ReminderScheduler = ReminderScheduler(homeAdapter, alarmScheduler, deviceRole)
}

/**
 * What this device is. Reminders fire on a [Phone]; a [WallDisplay] arms nothing and shows nothing —
 * a tablet on the wall buzzing at everyone who walks past it is the opposite of the point.
 */
enum class DeviceRole { Phone, WallDisplay }

/**
 * Pick the adapter stack: no HA token → [MockAdapter]; HA only → [HomeAssistantAdapter]; HA + MA →
 * the [CompositeHomeAdapter] (HA devices + MA music). MA never runs without HA — it only enriches an
 * existing live home. The mock needs no calendar cache: its fixtures are always there.
 */
private fun buildHomeAdapter(
    haConfig: HaConfig?,
    maConfig: MaConfig?,
    keyValueStore: KeyValueStore?,
): HomeAdapter {
    if (haConfig?.hasToken != true) return MockAdapter()
    val ha = HomeAssistantAdapter(haConfig, keyValueStore?.let(::KeyValueCalendarCache))
    return if (maConfig?.hasToken == true) CompositeHomeAdapter(ha, MusicAssistantAdapter(maConfig)) else ha
}

/**
 * Builds an [HaConfig] from the code-generated [BuildSecrets] (sourced from repo-root `local.properties`
 * by the `:shared` `generateBuildSecrets` Gradle task), shared by every platform entry point. Returns
 * `null` when no token is configured, so [AppContainer] falls back to the [MockAdapter].
 */
fun haConfigFromSecrets(): HaConfig? =
    BuildSecrets.HA_TOKEN
        .takeIf { it.isNotBlank() }
        ?.let { HaConfig(url = BuildSecrets.HA_URL, token = it) }

/**
 * Builds a [MaConfig] for the direct Music Assistant connection from [BuildSecrets]. Returns `null`
 * when no MA token is configured, so the composite falls back to the HA adapter alone (blank browse
 * shelves + queue, exactly as before MA was wired). [haHost] lets [MaConfig] derive the MA endpoint
 * from the HA host when `ma.url` is omitted.
 */
fun maConfigFromSecrets(haConfig: HaConfig? = haConfigFromSecrets()): MaConfig? =
    BuildSecrets.MA_TOKEN
        .takeIf { it.isNotBlank() }
        ?.let { MaConfig(token = it, url = BuildSecrets.MA_URL, haHost = haConfig?.host.orEmpty()) }
