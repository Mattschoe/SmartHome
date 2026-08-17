package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.Room
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The Home Assistant entities that back one [Room]'s controls. A room typically has several lights, so
 * light **writes** target [areaId] (the whole room responds) while light **reads** aggregate over every
 * id in [lightIds]. [switchIds] are `switch.*` entities driving a lamp (a lamp on a smart plug/switch):
 * they read as on/off (no dimming) and are written via the `switch` service domain, not `light`. Audio
 * is a single [mediaPlayerId]. Any field may be absent.
 */
data class RoomEntities(
    val areaId: String? = null,
    val lightIds: List<String> = emptyList(),
    val switchIds: List<String> = emptyList(),
    val mediaPlayerId: String? = null,
)

/**
 * Explicitly-configured `switch.*` entities that drive a lamp and should count as one of a room's
 * lights. Unlike `light.*` entities (auto-discovered by area), switches are opt-in per entity so
 * non-lamp switches (fans, plugs) are never swept in. This is an opinionated single-home constant.
 */
val SWITCH_LIGHTS_BY_ROOM: Map<Room, List<String>> = mapOf(
    Room.LivingRoom to listOf("switch.donut"),
)

/**
 * The `media_player.*` entity that is a room's real speaker, when the room's HA area contains more than
 * one media_player and area-order would otherwise pick the wrong one. The Bedroom area holds both the
 * Sonos and an Apple TV; without this override the alphabetically-first id (`apple_tv_*`) wins, so the
 * room's now-playing never resolves. An explicit entry pins the speaker. Opinionated single-home constant.
 */
val MEDIA_PLAYER_BY_ROOM: Map<Room, String> = mapOf(
    Room.Bedroom to "media_player.sovevaerelse",
)

/** Home Assistant's `CalendarEntityFeature.CREATE_EVENT` bit — what makes a calendar writable. */
private const val CalendarCreateEvent = 1

/**
 * Home Assistant's `TodoListEntityFeature.SET_DUE_DATE_ON_ITEM` bit. The dashboard's todos are
 * bucketed by day, so a list that cannot carry a due date (the HAOS-default `todo.shopping_list`
 * reports 15 — create/delete/update/move and nothing else) structurally cannot back the panel.
 */
private const val TodoSetDueDate = 16

/**
 * `TodoListEntityFeature.SET_DESCRIPTION_ON_ITEM`. Unlike [TodoSetDueDate] this is not required to
 * use a list, only to record when a task was closed — see [todoSupportsDescription].
 */
private const val TodoSetDescription = 64

/**
 * The home's calendars, from the `get_states` snapshot: every `calendar.*` entity, labelled by its
 * `friendly_name` and marked writable from its own `supported_features`. Derived rather than
 * configured, so adding a calendar in Home Assistant is all it takes for the app to see it — and a
 * read-only subscription (an external ICS feed) is correctly refused as a write target.
 *
 * Ordered by entity id so each calendar keeps the same agenda dot color across sessions.
 */
fun discoverCalendarSources(
    states: List<HaStateDto>,
    entities: List<HaEntityRegistryDto> = emptyList(),
): List<CalendarSource> {
    val colorById = entities.associate { it.entity_id to it.calendarColor() }
    return states
        .filter { it.entity_id.startsWith("calendar.") }
        .sortedBy { it.entity_id }
        .map { state ->
            CalendarSource(
                id = state.entity_id,
                displayName = state.attrString("friendly_name") ?: state.entity_id.removePrefix("calendar."),
                canWrite = (state.attrInt("supported_features") ?: 0) and CalendarCreateEvent != 0,
                color = colorById[state.entity_id],
            )
        }
}

/**
 * The color set on a calendar in Home Assistant, one of its own color *names* (`amber`, `primary`,
 * `dark-grey`, …) — it is a registry option rather than an entity attribute, so it arrives with
 * `config/entity_registry/list` and not with `get_states`. `null` when no color has been picked.
 */
private fun HaEntityRegistryDto.calendarColor(): String? =
    ((options["calendar"] as? JsonObject)?.get("color") as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull

/**
 * The `todo.*` list backing the dashboard's checklist: the first that supports due dates (see
 * [TodoSetDueDate]). `null` when the home has no such list, which leaves the todo intents inert
 * rather than writing into the wrong list.
 */
fun discoverTodoEntity(states: List<HaStateDto>): String? = states
    .filter { it.entity_id.startsWith("todo.") }
    .sortedBy { it.entity_id }
    .firstOrNull { (it.attrInt("supported_features") ?: 0) and TodoSetDueDate != 0 }
    ?.entity_id

/**
 * Whether [entityId]'s list lets `todo.update_item` write a description — where the day a task was
 * ticked off is stamped (see `formatClosedMarker`). Not a requirement for using the list, only for
 * recording that day: a list without it still works, and its finished tasks fall back to sitting on
 * the day they were due.
 */
fun todoSupportsDescription(states: List<HaStateDto>, entityId: String?): Boolean = states
    .firstOrNull { it.entity_id == entityId }
    ?.let { (it.attrInt("supported_features") ?: 0) and TodoSetDescription != 0 }
    ?: false

/**
 * The home's weather entity, backing the outdoor climate tile: the first `weather.*` by entity id.
 * Derived rather than configured, so re-adding the integration under a new id needs no code change.
 */
fun discoverWeatherEntity(states: List<HaStateDto>): String? = states
    .map { it.entity_id }
    .filter { it.startsWith("weather.") }
    .minOrNull()

private fun HaStateDto.attr(key: String): JsonPrimitive? = attributes[key] as? JsonPrimitive
private fun HaStateDto.attrInt(key: String): Int? = attr(key)?.intOrNull
private fun HaStateDto.attrString(key: String): String? = attr(key)?.takeIf { it.isString }?.contentOrNull

/**
 * Normalize an area or room name for matching: lowercase, keep only letters/digits. So the HA area
 * `"Living Room"` and the enum constant `Room.LivingRoom` both reduce to `"livingroom"`.
 */
fun normalizeAreaName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/** The [Room] whose enum-constant name matches [areaName] after normalization, or `null`. */
fun roomForAreaName(areaName: String): Room? {
    val target = normalizeAreaName(areaName)
    return Room.entries.firstOrNull { normalizeAreaName(it.name) == target }
}

/**
 * Per-room sync leader from each speaker room's `group_members`. HA's convention is that the first
 * entry is the group leader, so a room's own list decides it; a room that reports nothing but is
 * *named* by another room's list inherits that group's leader, so either reporting side is enough.
 * A room whose list is empty (or names only itself, the idle shape) plays alone -> `null`. Entity
 * ids absent from [roomByEntityId] are speakers this dashboard doesn't model, and are ignored.
 *
 * Pure so the (defensive) reading of `group_members` is unit-tested without a live instance.
 */
fun resolveSyncLeaders(
    groupMembersByRoom: Map<Room, List<String>>,
    roomByEntityId: Map<String, Room>,
): Map<Room, Room?> {
    val leaders = mutableMapOf<Room, Room>()
    for (members in groupMembersByRoom.values) {
        val group = members.mapNotNull { roomByEntityId[it] }.distinct()
        if (group.size < 2) continue // alone: nothing, only itself, or only unmodelled speakers
        val leader = group.first()
        group.forEach { leaders[it] = leader }
    }
    return (groupMembersByRoom.keys + leaders.keys).associateWith { leaders[it] }
}

/**
 * Build the [Room] -> [RoomEntities] map from the three HA registries. An entity's effective area is
 * its own `area_id` if set, else its device's `area_id`. For each recognized room we collect **all**
 * light entities in that area (writes target the area, reads aggregate them) and one media_player —
 * [mediaPlayerByRoom] pins the speaker when an area has several, else the first by id.
 * [switchLightsByRoom] adds explicitly-configured `switch.*` lamps per room; a room is
 * emitted if it has any light, switch-light or speaker. Configured switch-lights land in their room
 * even if that room had no area-discovered entities (so a switch whose HA area is unset still works).
 *
 * Pure so the name-matching and grouping rules are unit-tested without a live instance.
 */
fun discoverRoomEntities(
    areas: List<HaAreaDto>,
    devices: List<HaDeviceDto>,
    entities: List<HaEntityRegistryDto>,
    switchLightsByRoom: Map<Room, List<String>> = emptyMap(),
    mediaPlayerByRoom: Map<Room, String> = emptyMap(),
): Map<Room, RoomEntities> {
    val roomByAreaId: Map<String, Room> =
        areas.mapNotNull { area -> roomForAreaName(area.name)?.let { area.area_id to it } }.toMap()
    val areaIdByDevice: Map<String, String?> = devices.associate { it.id to it.area_id }

    fun effectiveAreaId(entity: HaEntityRegistryDto): String? =
        entity.area_id ?: entity.device_id?.let { areaIdByDevice[it] }

    val idsByArea: Map<String, List<String>> = entities
        .mapNotNull { e -> effectiveAreaId(e)?.let { it to e.entity_id } }
        .groupBy({ it.first }, { it.second })

    val areaIdByRoom: Map<Room, String> = roomByAreaId.entries.associate { (areaId, room) -> room to areaId }
    val rooms = roomByAreaId.values.toSet() + switchLightsByRoom.keys

    return buildMap {
        for (room in rooms) {
            val areaId = areaIdByRoom[room]
            val ids = areaId?.let { idsByArea[it] }.orEmpty()
            val lights = ids.filter { it.startsWith("light.") }.sorted()
            val speaker = mediaPlayerByRoom[room]
                ?: ids.filter { it.startsWith("media_player.") }.sorted().firstOrNull()
            val switches = switchLightsByRoom[room].orEmpty().sorted()
            if (lights.isNotEmpty() || switches.isNotEmpty() || speaker != null) {
                put(room, RoomEntities(areaId = areaId, lightIds = lights, switchIds = switches, mediaPlayerId = speaker))
            }
        }
    }
}
