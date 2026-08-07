package com.mattschoe.smarthome.data.ma

import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.Room

/**
 * Pure DTO -> domain mapping for the Music Assistant data. Kept separate from the WS adapter so the
 * folder-selection and item-shaping rules are unit-tested without a live server (like `HaDiscovery`).
 */

/** The two idle browse shelves the UI renders, derived from MA's recommendation folders. */
data class BrowseShelves(
    val quickPicks: List<BrowseItem>,
    val mixedForYou: List<BrowseItem>,
)

// What the pager can reach at once: 3 pages of the 3×3 Quick Picks grid.
private const val QUICK_PICKS_WINDOW = 27
// Candidates kept behind the window for the rotation to cycle through, bounded so a large feed can't
// grow `MusicData` without limit.
private const val QUICK_PICKS_POOL_LIMIT = 108
private const val MIXED_FOR_YOU_LIMIT = 24

// MA folder identity is fuzzy — some folders carry a translation_key and some don't, so name
// (case-insensitive substring) is the one identifier that addresses them all. Listed in priority
// order: the round-robin takes from each in turn, so the earlier a folder sits the sooner it lands on
// the first page. A name no folder matches is simply skipped — which providers serve which folders
// varies, and the last two are not offered by every server.
private val QUICK_PICKS_SOURCE_NAMES = listOf(
    "listen again",
    "recently played",
    "albums for you",
    "recently added tracks",
    "recently added albums",
    "recently favorited tracks",
    "random albums",
    "covers and remixes",
    "forgotten favorites",
)
private val MIXED_FOR_YOU_NAMES = setOf("mixed for you")

/** Quick Picks is a grid of single songs, so the supermix/podcast/artist entries its sources mix in are dropped. */
private val QUICK_PICKS_TYPES = setOf("track", "album")

/**
 * Select the [BrowseShelves] from MA's `music/recommendations` folders. Quick Picks round-robins the
 * [QUICK_PICKS_SOURCE_NAMES] folders so each one reaches the first page, keeping only
 * [QUICK_PICKS_TYPES] items, de-duplicated and capped at [QUICK_PICKS_POOL_LIMIT]; [rotation] then
 * picks which [QUICK_PICKS_WINDOW]-wide slice of that pool is the visible grid, so a dashboard left
 * running turns its tiles over instead of showing one fixed set. Mixed For You is the "Mixed for you"
 * folder of supermixes.
 */
fun mapRecommendations(folders: List<MaRecommendationFolder>, rotation: Int = 0): BrowseShelves {
    val quickSources = QUICK_PICKS_SOURCE_NAMES.mapNotNull { name -> folders.pickShelf(setOf(name)) }
    // Filtered per folder before interleaving so a folder of dropped podcasts contributes no slots.
    val quickRaw = quickSources
        .map { folder -> folder.items.filter { it.media_type in QUICK_PICKS_TYPES } }
        .roundRobin()
    val pool = quickRaw.toBrowseItems().distinctBy { it.uri }.take(QUICK_PICKS_POOL_LIMIT)
    val start = if (pool.size <= QUICK_PICKS_WINDOW) 0 else (rotation * QUICK_PICKS_WINDOW).mod(pool.size)
    val quickPicks = pool.windowFrom(start, QUICK_PICKS_WINDOW)

    val mixed = folders.pickShelf(MIXED_FOR_YOU_NAMES)

    return BrowseShelves(
        quickPicks = quickPicks,
        mixedForYou = mixed?.items.orEmpty().toBrowseItems().distinctBy { it.uri }.take(MIXED_FOR_YOU_LIMIT),
    )
}

private const val YTMUSIC_PROVIDER_DOMAIN = "ytmusic"
private const val SPOTIFY_PROVIDER_DOMAIN = "spotify"

// The Spotify account is shared with playlists it merely follows (Spotify's own mixes, other users'),
// so the rail is narrowed to the ones she actually owns. This also drops the `Liked Songs <name>` row
// MA synthesizes for whichever account is authenticated, which survives a re-auth as a stale row.
private const val SPOTIFY_PLAYLIST_OWNER = "Cecilie Weber Andersen"

// A YouTube channel id — i.e. an artist. YTM album ids start "MPREb_".
private const val YT_CHANNEL_ID_PREFIX = "UC"

// The recently-played folder is one of the few that carries a stable translation_key, so it is
// addressed by key rather than by the name-substring matching the untranslated ytmusic shelves need.
private const val RECENTLY_PLAYED_KEY = "recently_played"

// A grid's worth, matching the Quick Picks window it sits beside.
private const val RECENTLY_PLAYED_LIMIT = 27

// MA exposes no field (favorite/is_dynamic/genre/…) that distinguishes these from any other synced
// YT Music playlist, so unwanted ones are dropped by name.
private val EXCLUDED_PLAYLIST_NAMES = setOf("Memes", "Liked Music (YouTube Music)")

/**
 * Map raw playlist library rows (`music/playlists/library_items`) to browse tiles, keeping only the
 * user's YouTube Music playlists. Every row reports `provider == "library"`, so the provider is read
 * off [MaMediaItem.provider_mappings]; MA's own generated playlists (Infinite Mix, Random Album, All
 * favorited tracks, …) map to `builtin`.
 */
fun mapPlaylists(items: List<MaMediaItem>): List<BrowseItem> =
    items.filter { item ->
        val fromYtMusic = item.provider_mappings.any { it.provider_domain == YTMUSIC_PROVIDER_DOMAIN }
        fromYtMusic && item.name !in EXCLUDED_PLAYLIST_NAMES
    }.toBrowseItems()

/**
 * The [MusicSource.Spotify] counterpart to [mapPlaylists]: the same library rows, kept when they are
 * backed by Spotify **and** owned by [SPOTIFY_PLAYLIST_OWNER]. Provider is read off
 * [MaMediaItem.provider_mappings] for the same reason as there — every row reports `provider ==
 * "library"`.
 */
fun mapSpotifyPlaylists(items: List<MaMediaItem>): List<BrowseItem> =
    items.filter { item ->
        val fromSpotify = item.provider_mappings.any { it.provider_domain == SPOTIFY_PROVIDER_DOMAIN }
        fromSpotify && item.owner == SPOTIFY_PLAYLIST_OWNER
    }.toBrowseItems()

/**
 * The [domain]-provided items of MA's "recently played" folder. That folder is library-level and
 * mixes every provider's history, so items are kept by their own [MaMediaItem.provider] — an instance
 * id (`spotify--TkfLc2DT`) prefixed by the domain.
 */
fun mapRecentlyPlayed(folders: List<MaRecommendationFolder>, domain: String): List<BrowseItem> {
    val folder = folders.firstOrNull { it.translation_key == RECENTLY_PLAYED_KEY } ?: return emptyList()
    return folder.items
        .filter { it.provider?.startsWith(domain) == true }
        .toBrowseItems()
        .distinctBy { it.uri }
        .take(RECENTLY_PLAYED_LIMIT)
}

/**
 * Flatten a `music/search` reply into one tile list. The grid shows no type sections, so the four
 * lists are simply concatenated in relevance order — tracks first (what a search is usually for),
 * then albums, artists, playlists — and de-duplicated, since an item can be returned under more than
 * one type.
 */
fun mapSearchResults(results: MaSearchResults): List<BrowseItem> {
    return (results.tracks + results.albums + results.artists + results.playlists)
        .toBrowseItems()
        .distinctBy { it.uri }
}

private fun List<MaRecommendationFolder>.pickShelf(names: Set<String>): MaRecommendationFolder? =
    firstOrNull { folder -> folder.matches(names) && folder.items.isNotEmpty() }

private fun MaRecommendationFolder.matches(names: Set<String>): Boolean {
    val lower = name.lowercase()
    return names.any { lower.contains(it) }
}

/**
 * Flatten lists by taking one item from each in turn — index 0 of every list, then index 1, and so on
 * — so a short list still contributes near the front instead of being pushed past a cap. Lists that
 * run out are skipped.
 */
private fun <T> List<List<T>>.roundRobin(): List<T> {
    val longest = maxOfOrNull { it.size } ?: return emptyList()
    return (0 until longest).flatMap { i -> mapNotNull { list -> list.getOrNull(i) } }
}

/**
 * The [window] items starting at [start], wrapping past the end of the list, so the result is a full
 * window at any offset. A list no longer than [window] is returned whole — there is nothing to
 * rotate through.
 */
private fun <T> List<T>.windowFrom(start: Int, window: Int): List<T> {
    if (size <= window) return this
    val head = drop(start)
    return if (head.size >= window) head.take(window) else head + take(window - head.size)
}

private fun List<MaMediaItem>.toBrowseItems(): List<BrowseItem> = mapNotNull { it.toBrowseItemOrNull() }

/**
 * MA's `media_type` string -> the tile kind the UI dispatches on, corrected against the uri: the
 * "Listen again" folder returns artist channels typed `album` carrying a YouTube **channel** id
 * (`UC…`, where a real album is `MPREb_…`). Taken at its word they route to play, and MA can't
 * resolve `album/UC…`. The override is gated on the ytmusic provider so no other provider's id
 * scheme can false-positive.
 */
private fun browseKindOf(mediaType: String?, uri: String): BrowseKind {
    val kind = when (mediaType) {
        "track" -> BrowseKind.Track
        "album" -> BrowseKind.Album
        "artist" -> BrowseKind.Artist
        "playlist" -> BrowseKind.Playlist
        else -> BrowseKind.Other
    }
    if (kind != BrowseKind.Album) return kind
    val ref = parseMaUri(uri) ?: return kind
    if (!ref.provider.startsWith(YTMUSIC_PROVIDER_DOMAIN) || !ref.itemId.startsWith(YT_CHANNEL_ID_PREFIX)) return kind
    return BrowseKind.Artist
}

/**
 * A usable MA item -> browse tile, or `null` when it has no uri / isn't playable. **Artists are
 * exempt from the playability guard**: an artist tile is a navigation target (it opens the artist
 * surface), not a play target, and MA marks plenty of artist hits non-playable.
 */
fun MaMediaItem.toBrowseItemOrNull(): BrowseItem? {
    val itemUri = uri ?: return null
    val kind = browseKindOf(media_type, itemUri)
    if (!is_playable && kind != BrowseKind.Artist) return null
    return BrowseItem(
        name = name,
        subtitle = browseSubtitle(),
        artworkUrl = httpArtworkUrl(),
        uri = itemUri,
        kind = kind,
    )
}

// --- Artist drill-in ---

// One page-set of the 3×3 top-hits grid (mirroring QUICK_PICKS_WINDOW) and a rail's worth of albums.
// ytmusic caps its top-tracks reply at 25, so the track limit is only an upper bound in practice.
private const val ARTIST_TOP_TRACKS_LIMIT = 27
private const val ARTIST_ALBUMS_LIMIT = 24

/**
 * The parts of an MA uri: `ytmusic--zas2oSHz://artist/UC123` -> provider `ytmusic--zas2oSHz`,
 * media type `artist`, item id `UC123`.
 */
data class MaUriRef(val provider: String, val mediaType: String, val itemId: String)

/**
 * Split an MA uri into [MaUriRef], or `null` if it isn't one. MA's artist commands address an item
 * by `(item_id, provider_instance_id_or_domain)` rather than by uri, so this is how a tapped tile
 * becomes a request.
 */
fun parseMaUri(uri: String): MaUriRef? {
    val provider = uri.substringBefore("://", missingDelimiterValue = "")
    val rest = uri.substringAfter("://", missingDelimiterValue = "")
    val mediaType = rest.substringBefore('/', missingDelimiterValue = "")
    val itemId = rest.substringAfter('/', missingDelimiterValue = "")
    if (provider.isEmpty() || mediaType.isEmpty() || itemId.isEmpty()) return null
    return MaUriRef(provider, mediaType, itemId)
}

/** `music/artists/top_tracks` rows -> the Top hits grid, in MA's popularity order, de-duplicated and capped. */
fun mapArtistTracks(items: List<MaMediaItem>): List<BrowseItem> =
    items.toBrowseItems().distinctBy { it.uri }.take(ARTIST_TOP_TRACKS_LIMIT)

/** `music/artists/artist_albums` rows -> the Albums rail, de-duplicated and capped. */
fun mapArtistAlbums(items: List<MaMediaItem>): List<BrowseItem> =
    items.toBrowseItems().distinctBy { it.uri }.take(ARTIST_ALBUMS_LIMIT)

/** Secondary line: artist(s) for a track/album, else the playlist owner, else the item's own subtitle. */
internal fun MaMediaItem.browseSubtitle(): String? {
    val artistLine = artists.joinToString(", ") { it.name }.trim()
    return artistLine.ifBlank { (owner ?: subtitle).orEmpty() }.takeIf { it.isNotBlank() }
}

/**
 * A directly-usable cover-art URL, or `null`. MA's ytmusic art is remotely-accessible (a full
 * `https://…googleusercontent`/`ytimg` URL) which loads as-is; provider-local art (proxy-only paths)
 * is dropped so the tile falls back to its colored glyph rather than a broken image. Any usable URL is
 * upscaled ([upscaleArtworkUrl]) so tiles aren't served MA's small thumbnail.
 */
internal fun MaMediaItem.httpArtworkUrl(): String? =
    pickHttpArt(listOfNotNull(image) + metadata?.images.orEmpty())

/** [httpArtworkUrl]'s body, over an explicit candidate list so a queue row can pool its own images in. */
internal fun pickHttpArt(images: List<MaImageRef>): String? {
    val path = pickSquareImage(images)?.path ?: return null
    if (!path.startsWith("http")) return null
    return upscaleArtworkUrl(path)
}

/**
 * The image to use as cover art. Every surface crops art into a square tile, so the order is: the
 * **square** one (`type == "thumb"`) MA labelled, then anything that isn't an `i.ytimg.com` still,
 * then whatever is left. A ytimg still is a 16:9 video frame with burned-in text — cropping it square
 * chops that text — so it only ever wins when a track has no other image at all.
 */
internal fun pickSquareImage(images: List<MaImageRef>): MaImageRef? =
    images.firstOrNull { it.type == "thumb" && !it.isVideoStill() }
        ?: images.firstOrNull { !it.isVideoStill() }
        ?: images.firstOrNull()

private fun MaImageRef.isVideoStill(): Boolean = path?.contains("i.ytimg.com") == true

private const val ART_TARGET_PX = 720

// Google-hosted art (googleusercontent/ggpht) carries a trailing size token: "=w600-h600[-p]" or "=s120".
private val GOOGLE_SIZE_WH = Regex("=w(\\d+)-h(\\d+)")
private val GOOGLE_SIZE_S = Regex("=s(\\d+)")

/**
 * Raise MA's cover-art URL to a sharp resolution, since it hands back small thumbnails:
 * - `i.ytimg.com` video thumbnails carry a `?sqp=…&rs=…` downscale/crop transform — dropping the query
 *   yields the full-size named variant (e.g. `hq720.jpg` at 1280×720).
 * - Google-hosted art carries a `=w…-h…`/`=s…` size token — we **grow** it to [ART_TARGET_PX] (never
 *   shrink an already-larger one), preserving any trailing suffix (`-p`, `-l90-rj`, …).
 * Any other URL is returned unchanged, so this is safe to apply to every http art URL.
 */
internal fun upscaleArtworkUrl(url: String): String = when {
    "i.ytimg.com" in url -> url.substringBefore('?')
    else -> url
        .replace(GOOGLE_SIZE_WH) { m ->
            "=w${grow(m.groupValues[1])}-h${grow(m.groupValues[2])}"
        }
        .replace(GOOGLE_SIZE_S) { m -> "=s${grow(m.groupValues[1])}" }
}

/** The larger of a parsed size and [ART_TARGET_PX], so we only ever upscale. */
private fun grow(size: String): Int = maxOf(size.toIntOrNull() ?: 0, ART_TARGET_PX)

// --- Queue mapping ---

/**
 * Map MA queues to rooms by matching each queue's `display_name` to a [Room]'s Danish display name
 * (normalized: lowercased, letters/digits only). Non-room players (a phone, a TV, a laptop) don't
 * match and are dropped. Returns Room -> queue_id — the queue_id doubles as the MA player target for
 * fetching items and for starting playback.
 */
fun matchQueuesToRooms(queues: List<MaQueue>): Map<Room, String> {
    val roomByName = Room.entries.associateBy { normalizeName(it.displayName) }
    return buildMap {
        for (queue in queues) {
            val room = queue.display_name?.let { roomByName[normalizeName(it)] } ?: continue
            if (room !in this) put(room, queue.queue_id) // first match wins for a room
        }
    }
}

private fun normalizeName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/**
 * The `player_queues/items` offset of the first **unplayed** entry, given a queue's `current_index`.
 * Everything below it is history (or the track playing right now), so paging from here is what "up
 * next" actually means. A queue that has never started (`null`) begins at 0.
 */
fun upNextOffset(currentIndex: Int?): Int = (currentIndex ?: -1) + 1

/**
 * Strip the playing entry out of a fetched up-next slice. [upNextOffset] already pages past it, but
 * right after a `replace` play MA can report a stale (or still-null) `current_index`, sliding the
 * playing entry itself into the page — and "up next" must never lead with the track already on.
 */
fun List<MediaTrack>.withoutQueueItem(queueItemId: String?): List<MediaTrack> =
    if (queueItemId == null) this else filterNot { it.queueItemId == queueItemId }

// --- Enqueue planning ---

/** One relative reorder, as Music Assistant's `player_queues/move_item` takes it. */
data class QueueMove(val queueItemId: String, val posShift: Int)

/** What an enqueue has to do after the insert landed: the moves, and the block's new bottom marker. */
data class EnqueuePlan(val moves: List<QueueMove>, val tailId: String?)

/**
 * Work out how an item that Music Assistant has just inserted with `play_media(option = "next")` must
 * be reordered to obey the `[playing] [user block] [auto block]` model, given the up-next queue-item
 * ids [before] and [after] the insert and the block's previous bottom marker [tailId].
 *
 * MA exposes no per-item provenance, so the user block is tracked as that one marker: it is the run of
 * [before] up to and including [tailId], and a marker no longer in the queue simply reads as an empty
 * block — which self-heals across track advances, replaces, and edits made in MA's own web UI.
 *
 * The inserted run is the **first** contiguous run of ids in [after] absent from [before]; a later one
 * is Don't-Stop-the-Music appending at the tail and is ignored. MA inserts after `index_in_buffer`
 * rather than after `current_index`, so the run is not reliably at the head — which is exactly why it
 * is found by diffing rather than assumed.
 *
 * [QueueMode.Next] is where MA already put it: no moves, and the block's bottom is unchanged (an empty
 * block, though, gains one — with nothing to insert above, the two modes agree). [QueueMode.Last] has
 * to sink the run below the block items it landed above, and `move_item`'s shift is relative and
 * per-item, so it moves **whichever side is cheaper**: the M inserted items down past the J block
 * items (reverse order, `+J` each), or those J items up past the run (forward order, `−M` each).
 * A `posShift` of `0` is never emitted — MA reads it as "move to the top".
 */
fun planEnqueue(
    before: List<String>,
    after: List<String>,
    tailId: String?,
    mode: QueueMode,
): EnqueuePlan {
    val known = before.toSet()
    val insertedAt = after.indexOfFirst { it !in known }
    // Nothing new in the window: the insert landed past it, or never happened. Leave the queue and
    // the marker exactly as they were.
    if (insertedAt < 0) return EnqueuePlan(emptyList(), tailId)
    val inserted = after.drop(insertedAt).takeWhile { it !in known }

    // How much of the user block the insert landed *above*, and so has to sink past. A stale (or
    // absent) marker means there is no block at all.
    val blockEnd = tailId?.let { before.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
    val below = (blockEnd - insertedAt).coerceAtLeast(0)

    if (mode == QueueMode.Next) {
        // The block only grew at the top, so its bottom is unchanged — unless there was no block, in
        // which case the run just inserted *is* the block.
        return EnqueuePlan(emptyList(), if (blockEnd > 0) tailId else inserted.last())
    }
    val moves = when {
        below == 0 -> emptyList()
        inserted.size <= below -> inserted.reversed().map { QueueMove(it, below) }
        else -> before.subList(insertedAt, blockEnd).map { QueueMove(it, -inserted.size) }
    }
    return EnqueuePlan(moves, inserted.last())
}

/** A queue row -> domain [MediaTrack], preferring the richer nested `media_item` when present. */
fun MaQueueItem.toMediaTrack(): MediaTrack {
    val item = media_item
    val title = item?.name?.takeIf { it.isNotBlank() } ?: name
    // Pool the queue row's own image with the media item's and let [pickSquareImage] rank them all —
    // the queue-level image is often a 16:9 video still while the media item carries the square cover.
    val artworkUrl = pickHttpArt(listOfNotNull(image) + listOfNotNull(item?.image) + item?.metadata?.images.orEmpty())
    return MediaTrack(
        title = title,
        artist = item?.artists?.joinToString(", ") { it.name }.orEmpty(),
        album = item?.album?.name,
        artworkUrl = artworkUrl,
        durationSec = duration ?: item?.duration ?: 0,
        uri = item?.uri,
        queueItemId = queue_item_id,
    )
}

fun mapQueueItems(items: List<MaQueueItem>): List<MediaTrack> = items.map { it.toMediaTrack() }
