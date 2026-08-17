package com.mattschoe.smarthome.data.ma

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.roundToInt

/**
 * Wire (JSON) models for the **Music Assistant** server WebSocket API (`:8095/ws`), kept separate
 * from the domain models in `data.model`; the adapter maps DTO -> domain. Parsing is lenient
 * (`ignoreUnknownKeys`) — MA media objects carry far more than we read. Field names are snake_case
 * to match the API exactly (no `@SerialName` noise), matching the HA DTO style.
 *
 * Shapes verified live against MA v2.9.9 / schema 31 (see memory `ma-direct-api-capabilities`).
 */

/**
 * A duration in seconds, as Music Assistant actually reports it: usually a whole number, but a
 * provider that knows a stream's real length sends a float (`157.71`). Decoding that straight into
 * `Int` throws, and one such track anywhere in a reply takes the **whole** reply down with it — which
 * is how a single song could blank every room's queue. The number is read as a double and rounded;
 * nothing on screen shows sub-second precision.
 */
internal object MaSecondsSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MaSeconds", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.decodeDouble().roundToInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** An image reference. [path] is a full https URL for remotely-accessible art (ytmusic), else a
 *  provider-relative path resolved through the MA image proxy. [type] is MA's image role — `"thumb"`
 *  is the square cover, `"landscape"` a 16:9 still; the square one is what our tiles want. */
@Serializable
data class MaImageRef(
    val path: String? = null,
    val provider: String? = null,
    val remotely_accessible: Boolean = false,
    val type: String? = null,
)

/** Only the images list matters to us off a media item's metadata block. */
@Serializable
data class MaMetadata(
    val images: List<MaImageRef> = emptyList(),
)

/** A minimal artist/album reference — we read only the display [name]. */
@Serializable
data class MaNamedRef(
    val name: String = "",
)

/** One provider's binding for a media item. [provider_domain] is the provider *kind*
 *  (`ytmusic`, `builtin`) — the stable discriminator; [provider_instance] is the configured
 *  instance (`ytmusic--zas2oSHz`). A `library://` item carries a mapping per backing provider.
 *  [available] is that binding's own playability: a library item every provider has marked
 *  unavailable is one MA will refuse to start ("No playable item found to start playback").
 *  Defaulted `true`, so an item that reports no mappings at all is left alone. */
@Serializable
data class MaProviderMapping(
    val provider_domain: String? = null,
    val provider_instance: String? = null,
    val available: Boolean = true,
)

/**
 * A Music Assistant media item — the common shape returned as recommendation items, playlist
 * library rows, and a queue item's `media_item`. [media_type] is track/album/playlist/folder/…;
 * [uri] is what we play. Tracks carry [artists]/[album]/[duration]; playlists carry [owner].
 */
@Serializable
data class MaMediaItem(
    val name: String = "",
    val uri: String? = null,
    val media_type: String? = null,
    val is_playable: Boolean = false,
    val provider: String? = null,
    val provider_mappings: List<MaProviderMapping> = emptyList(),
    @Serializable(with = MaSecondsSerializer::class) val duration: Int? = null,
    val subtitle: String? = null,
    val owner: String? = null,
    val artists: List<MaNamedRef> = emptyList(),
    val album: MaNamedRef? = null,
    val image: MaImageRef? = null,
    val metadata: MaMetadata? = null,
)

/**
 * The result of `music/search`, one list per media type. The server also returns `genres`, `radio`,
 * `podcasts` and `audiobooks`; lenient parsing drops them, since no such providers are configured.
 */
@Serializable
data class MaSearchResults(
    val tracks: List<MaMediaItem> = emptyList(),
    val albums: List<MaMediaItem> = emptyList(),
    val artists: List<MaMediaItem> = emptyList(),
    val playlists: List<MaMediaItem> = emptyList(),
)

/** One folder (shelf) of `music/recommendations`, e.g. "Quick picks", "Listen again". */
@Serializable
data class MaRecommendationFolder(
    val name: String = "",
    val translation_key: String? = null,
    val subtitle: String? = null,
    val items: List<MaMediaItem> = emptyList(),
)

/**
 * One row of `player_queues/all`. [display_name] carries the room/player label; [items] is a count.
 * [current_index] is the queue position being played — everything before it is history, so the
 * "up next" page starts at `current_index + 1`. [index_in_buffer] is the furthest item MA has already
 * buffered; `move_item` rejects anything at or below it. [current_item] is the playing entry, carrying
 * the same high-quality art the browse shelves use.
 */
@Serializable
data class MaQueue(
    val queue_id: String,
    val display_name: String? = null,
    val items: Int = 0,
    val active: Boolean = false,
    val dont_stop_the_music_enabled: Boolean = false,
    val current_index: Int? = null,
    val index_in_buffer: Int? = null,
    val current_item: MaQueueItem? = null,
)

/**
 * One row of `player_queues/items` — a page of the queue. [media_item] is the underlying track.
 * [queue_item_id] is the stable handle every queue command takes (`play_index`, `move_item`); the
 * row's own `index` field is page-relative (it restarts at 0 for every `offset`) and is not read.
 */
@Serializable
data class MaQueueItem(
    val queue_item_id: String,
    val name: String = "",
    @Serializable(with = MaSecondsSerializer::class) val duration: Int? = null,
    val image: MaImageRef? = null,
    val media_item: MaMediaItem? = null,
)
