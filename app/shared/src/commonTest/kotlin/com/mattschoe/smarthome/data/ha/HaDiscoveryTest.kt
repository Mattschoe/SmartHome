package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaDiscoveryTest {

    private companion object {
        const val STUE = "media_player.stue"
        const val BEDROOM = "media_player.sovevaerelse"
        val ROOM_BY_ENTITY = mapOf(STUE to Room.LivingRoom, BEDROOM to Room.Bedroom)
    }

    @Test
    fun roomForAreaName_matchesEnglishAreaNamesToRoomConstants() {
        assertEquals(Room.LivingRoom, roomForAreaName("Living Room"))
        assertEquals(Room.Kitchen, roomForAreaName("Kitchen"))
        assertEquals(Room.Bedroom, roomForAreaName("bedroom"))     // case-insensitive
        assertEquals(Room.Bathroom, roomForAreaName("  Bathroom ")) // whitespace-insensitive
        assertEquals(Room.Hall, roomForAreaName("Hall"))
        assertNull(roomForAreaName("Garage")) // no matching room
    }

    @Test
    fun discovery_mapsEntitiesViaTheirDeviceArea() {
        val areas = listOf(
            HaAreaDto("living_room", "Living Room"),
            HaAreaDto("kitchen", "Kitchen"),
            HaAreaDto("garage", "Garage"), // unrecognized → dropped
        )
        val devices = listOf(
            HaDeviceDto("dev-lamp", area_id = "living_room"),
            HaDeviceDto("dev-sonos", area_id = "living_room"),
        )
        val entities = listOf(
            // light + media_player inherit the living-room area from their device
            HaEntityRegistryDto("light.floor_lamp", area_id = null, device_id = "dev-lamp"),
            HaEntityRegistryDto("media_player.sonos", area_id = null, device_id = "dev-sonos"),
            // kitchen light via an entity-level area override (no device)
            HaEntityRegistryDto("light.kitchen_ceiling", area_id = "kitchen", device_id = null),
            // garage entity is ignored
            HaEntityRegistryDto("light.garage", area_id = "garage", device_id = null),
        )

        val map = discoverRoomEntities(areas, devices, entities)

        assertEquals("living_room", map[Room.LivingRoom]?.areaId)
        assertEquals(listOf("light.floor_lamp"), map[Room.LivingRoom]?.lightIds)
        assertEquals("media_player.sonos", map[Room.LivingRoom]?.mediaPlayerId)
        assertEquals(listOf("light.kitchen_ceiling"), map[Room.Kitchen]?.lightIds)
        assertNull(map[Room.Kitchen]?.mediaPlayerId)
        assertTrue(Room.Hall !in map) // no entities → absent
    }

    @Test
    fun discovery_addsConfiguredSwitchLightAlongsideAreaLights() {
        val areas = listOf(HaAreaDto("living_room", "Living Room"))
        val entities = listOf(HaEntityRegistryDto("light.floor_lamp", area_id = "living_room"))

        val map = discoverRoomEntities(
            areas, devices = emptyList(), entities = entities,
            switchLightsByRoom = mapOf(Room.LivingRoom to listOf("switch.donut")),
        )

        assertEquals(listOf("light.floor_lamp"), map[Room.LivingRoom]?.lightIds)
        assertEquals(listOf("switch.donut"), map[Room.LivingRoom]?.switchIds)
    }

    @Test
    fun discovery_emitsRoomWithOnlyAConfiguredSwitchLight() {
        // Living Room has no area-discovered entities (its switch's HA area is unset), but the
        // configured switch-light still lands it in the map.
        val map = discoverRoomEntities(
            areas = emptyList(), devices = emptyList(), entities = emptyList(),
            switchLightsByRoom = mapOf(Room.LivingRoom to listOf("switch.donut")),
        )

        assertEquals(listOf("switch.donut"), map[Room.LivingRoom]?.switchIds)
        assertTrue(map[Room.LivingRoom]?.lightIds.isNullOrEmpty())
        assertNull(map[Room.LivingRoom]?.areaId)
    }

    @Test
    fun discovery_defaultsToNoSwitchLights() {
        val areas = listOf(HaAreaDto("living_room", "Living Room"))
        val entities = listOf(HaEntityRegistryDto("light.floor_lamp", area_id = "living_room"))

        val map = discoverRoomEntities(areas, devices = emptyList(), entities = entities)

        assertTrue(map[Room.LivingRoom]?.switchIds.isNullOrEmpty())
    }

    @Test
    fun discovery_defaultsToFirstMediaPlayerByIdWhenAreaHasSeveral() {
        // No override: the alphabetically-first media_player wins (the pre-fix behavior).
        val areas = listOf(HaAreaDto("bedroom", "Bedroom"))
        val entities = listOf(
            HaEntityRegistryDto("media_player.sovevaerelse", area_id = "bedroom"),
            HaEntityRegistryDto("media_player.apple_tv", area_id = "bedroom"),
        )

        val map = discoverRoomEntities(areas, devices = emptyList(), entities = entities)

        assertEquals("media_player.apple_tv", map[Room.Bedroom]?.mediaPlayerId)
    }

    @Test
    fun discovery_pinsSpeakerViaMediaPlayerOverrideWhenAreaHasSeveral() {
        // With an override the configured speaker wins over the alphabetically-first id.
        val areas = listOf(HaAreaDto("bedroom", "Bedroom"))
        val entities = listOf(
            HaEntityRegistryDto("media_player.sovevaerelse", area_id = "bedroom"),
            HaEntityRegistryDto("media_player.apple_tv", area_id = "bedroom"),
        )

        val map = discoverRoomEntities(
            areas, devices = emptyList(), entities = entities,
            mediaPlayerByRoom = mapOf(Room.Bedroom to "media_player.sovevaerelse"),
        )

        assertEquals("media_player.sovevaerelse", map[Room.Bedroom]?.mediaPlayerId)
    }

    // --- Sync groups (`group_members`) ---

    @Test
    fun syncLeaders_readBothSidesOfAGroupWithTheFirstEntryLeading() {
        val leaders = resolveSyncLeaders(
            groupMembersByRoom = mapOf(
                Room.LivingRoom to listOf(STUE, BEDROOM),
                Room.Bedroom to listOf(STUE, BEDROOM),
            ),
            roomByEntityId = ROOM_BY_ENTITY,
        )

        assertEquals(Room.LivingRoom, leaders[Room.LivingRoom])
        assertEquals(Room.LivingRoom, leaders[Room.Bedroom])
    }

    @Test
    fun syncLeaders_areResolvedWhenOnlyOneSideReportsTheGroup() {
        // Only the leader lists the pair — the follower reports nothing but is named by it.
        val fromLeader = resolveSyncLeaders(
            groupMembersByRoom = mapOf(
                Room.LivingRoom to listOf(STUE, BEDROOM),
                Room.Bedroom to emptyList(),
            ),
            roomByEntityId = ROOM_BY_ENTITY,
        )
        assertEquals(Room.LivingRoom, fromLeader[Room.LivingRoom])
        assertEquals(Room.LivingRoom, fromLeader[Room.Bedroom])

        // And the mirror image: only the follower lists it, still naming the leader first.
        val fromFollower = resolveSyncLeaders(
            groupMembersByRoom = mapOf(
                Room.LivingRoom to emptyList(),
                Room.Bedroom to listOf(STUE, BEDROOM),
            ),
            roomByEntityId = ROOM_BY_ENTITY,
        )
        assertEquals(Room.LivingRoom, fromFollower[Room.LivingRoom])
        assertEquals(Room.LivingRoom, fromFollower[Room.Bedroom])
    }

    @Test
    fun syncLeaders_areNullForRoomsPlayingAlone() {
        val leaders = resolveSyncLeaders(
            groupMembersByRoom = mapOf(
                Room.LivingRoom to listOf(STUE), // the idle self-only shape
                Room.Bedroom to emptyList(),
            ),
            roomByEntityId = ROOM_BY_ENTITY,
        )

        assertNull(leaders[Room.LivingRoom])
        assertNull(leaders[Room.Bedroom])
    }

    @Test
    fun syncLeaders_ignoreSpeakersThisDashboardDoesNotModel() {
        val leaders = resolveSyncLeaders(
            groupMembersByRoom = mapOf(
                // Grouped with a speaker that backs no Room — as far as the dashboard sees, alone.
                Room.LivingRoom to listOf(STUE, "media_player.garage"),
                Room.Bedroom to emptyList(),
            ),
            roomByEntityId = ROOM_BY_ENTITY,
        )

        assertNull(leaders[Room.LivingRoom])
        assertNull(leaders[Room.Bedroom])
    }

    @Test
    fun discovery_collectsAllLightsInAnAreaSorted() {
        val areas = listOf(HaAreaDto("bathroom", "Bathroom"))
        val entities = listOf(
            HaEntityRegistryDto("light.bathroom_lamp_2", area_id = "bathroom"),
            HaEntityRegistryDto("light.bathroom_lamp_1", area_id = "bathroom"),
            HaEntityRegistryDto("light.bathroom_lamp_3", area_id = "bathroom"),
        )

        val map = discoverRoomEntities(areas, devices = emptyList(), entities = entities)

        assertEquals(
            listOf("light.bathroom_lamp_1", "light.bathroom_lamp_2", "light.bathroom_lamp_3"),
            map[Room.Bathroom]?.lightIds,
        )
    }
}
