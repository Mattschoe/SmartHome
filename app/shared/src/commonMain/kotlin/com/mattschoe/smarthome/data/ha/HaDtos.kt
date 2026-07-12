package com.mattschoe.smarthome.data.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire (JSON) models for the Home Assistant WebSocket API. These are kept separate from the domain
 * models in `data.model`; the adapter maps DTO -> domain. All parsing is lenient
 * (`ignoreUnknownKeys`) — HA payloads carry far more than we read.
 *
 * Field names are snake_case to match the API exactly (no `@SerialName` noise).
 */

/** An entity's live state, from `get_states` and from `state_changed` events (`new_state`). */
@Serializable
data class HaStateDto(
    val entity_id: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
)

/** One row of `config/area_registry/list`. */
@Serializable
data class HaAreaDto(
    val area_id: String,
    val name: String,
)

/** One row of `config/device_registry/list`. A device inherits its [area_id] to its entities. */
@Serializable
data class HaDeviceDto(
    val id: String,
    val area_id: String? = null,
)

/**
 * One row of `config/entity_registry/list`. [area_id] is an entity-level override; when absent the
 * entity inherits the area of its [device_id].
 */
@Serializable
data class HaEntityRegistryDto(
    val entity_id: String,
    val area_id: String? = null,
    val device_id: String? = null,
)
