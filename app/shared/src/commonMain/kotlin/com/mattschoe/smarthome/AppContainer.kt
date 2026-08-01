package com.mattschoe.smarthome

import com.mattschoe.smarthome.data.CalendarFilterStore
import com.mattschoe.smarthome.data.CompositeHomeAdapter
import com.mattschoe.smarthome.data.HaConfig
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.HomeAssistantAdapter
import com.mattschoe.smarthome.data.InMemoryCalendarFilterStore
import com.mattschoe.smarthome.data.KeyValueCalendarCache
import com.mattschoe.smarthome.data.KeyValueCalendarFilterStore
import com.mattschoe.smarthome.data.KeyValueStore
import com.mattschoe.smarthome.data.MaConfig
import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.MusicAssistantAdapter
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
)

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
