package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaDiscoveryTest {

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
