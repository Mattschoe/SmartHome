package com.mattschoe.smarthome.data.ma

import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaMappingTest {

    private fun track(
        name: String,
        uri: String? = "ytmusic--zas2oSHz://track/$name",
        artist: String? = "Artist",
        playable: Boolean = true,
        art: String? = "https://img/$name.jpg",
        type: String = "track",
    ) = MaMediaItem(
        name = name,
        uri = uri,
        media_type = type,
        is_playable = playable,
        artists = artist?.let { listOf(MaNamedRef(it)) } ?: emptyList(),
        metadata = art?.let { MaMetadata(images = listOf(MaImageRef(path = it, remotely_accessible = true))) },
    )

    private fun folder(name: String, key: String? = null, items: List<MaMediaItem>) =
        MaRecommendationFolder(name = name, translation_key = key, items = items)

    /** A playlist library row as MA reports it: `provider = "library"`, the real source in the mappings. */
    private fun playlistRow(name: String, providerDomain: String) = MaMediaItem(
        name = name,
        uri = "library://playlist/$name",
        media_type = "playlist",
        is_playable = true,
        provider = "library",
        provider_mappings = listOf(MaProviderMapping(provider_domain = providerDomain)),
    )

    @Test
    fun quick_picks_interleaves_its_source_folders_in_priority_order_and_shapes_items() {
        val shelves = mapRecommendations(
            listOf(
                folder("Covers and remixes", items = listOf(track("Cover"))),
                folder("Forgotten favorites", items = listOf(track("Forgotten"))),
                folder("Listen again", items = listOf(track("Song A"), track("Song B"))),
            )
        )
        // One from each folder in priority order, then the second round — so a one-item folder still
        // lands on the first page.
        assertEquals(listOf("Song A", "Cover", "Forgotten", "Song B"), shelves.quickPicks.map { it.name })
        val a = shelves.quickPicks.first()
        assertEquals("Artist", a.subtitle)
        assertEquals("https://img/Song A.jpg", a.artworkUrl)
        assertEquals("ytmusic--zas2oSHz://track/Song A", a.uri)
    }

    @Test
    fun quick_picks_keeps_tracks_and_albums_but_drops_playlists_podcasts_and_artists() {
        val shelves = mapRecommendations(
            listOf(
                folder(
                    "Listen again",
                    items = listOf(
                        track("Song", type = "track"),
                        track("Record", type = "album"),
                        track("Supermix", type = "playlist"),
                        track("Show", type = "podcast"),
                        track("Band", type = "artist"),
                    ),
                )
            )
        )
        assertEquals(listOf("Song", "Record"), shelves.quickPicks.map { it.name })
    }

    @Test
    fun quick_picks_keeps_an_artist_channel_mislabelled_as_an_album_as_a_navigation_target() {
        val shelves = mapRecommendations(
            listOf(
                folder(
                    "Listen again",
                    items = listOf(
                        track("Song"),
                        track("DeathbyRomy", uri = "ytmusic--zas2oSHz://album/UCz8gfgZsjQW6zsuI4DgOC8A", type = "album"),
                    ),
                )
            )
        )
        // It arrives typed `album`, so QUICK_PICKS_TYPES keeps it — only its kind changes.
        assertEquals(listOf("Song", "DeathbyRomy"), shelves.quickPicks.map { it.name })
        assertEquals(BrowseKind.Artist, shelves.quickPicks[1].kind)
    }

    @Test
    fun quick_picks_dedupes_by_uri_and_caps_at_three_pages() {
        val shared = track("Shared")
        val shelves = mapRecommendations(
            listOf(
                folder("Listen again", items = listOf(shared) + (1..20).map { track("L$it") }),
                folder("Covers and remixes", items = listOf(shared) + (1..20).map { track("C$it") }),
            )
        )
        assertEquals(27, shelves.quickPicks.size)
        assertEquals(1, shelves.quickPicks.count { it.name == "Shared" })
    }

    @Test
    fun quick_picks_rotation_advances_a_whole_window_through_the_pool() {
        val folders = listOf(folder("Listen again", items = (1..60).map { track("L$it") }))

        val first = mapRecommendations(folders, rotation = 0).quickPicks.map { it.name }
        val second = mapRecommendations(folders, rotation = 1).quickPicks.map { it.name }

        assertEquals("L1", first.first())
        assertEquals("L28", second.first())
        assertEquals(emptyList(), first.intersect(second.toSet()).toList())
    }

    @Test
    fun quick_picks_rotation_wraps_past_the_end_and_still_fills_the_window() {
        val folders = listOf(folder("Listen again", items = (1..40).map { track("L$it") }))

        val wrapped = mapRecommendations(folders, rotation = 1).quickPicks.map { it.name }

        // Starts at 27, runs out at 40, and takes the remaining 14 tiles from the front of the pool.
        assertEquals(27, wrapped.size)
        assertEquals("L28", wrapped.first())
        assertEquals("L14", wrapped.last())
    }

    @Test
    fun quick_picks_rotation_is_a_no_op_when_the_pool_fits_in_one_window() {
        val folders = listOf(folder("Listen again", items = (1..10).map { track("L$it") }))

        val unrotated = mapRecommendations(folders, rotation = 0).quickPicks.map { it.name }
        val rotated = mapRecommendations(folders, rotation = 5).quickPicks.map { it.name }

        assertEquals(unrotated, rotated)
        assertEquals(10, rotated.size)
    }

    @Test
    fun quick_picks_is_empty_when_no_source_folder_matches() {
        val shelves = mapRecommendations(
            listOf(
                folder("Random artists", key = "random_artists", items = listOf(track("Band", type = "artist"))),
                folder("Mixed for you", items = listOf(track("Mix", type = "playlist"))),
            )
        )
        assertTrue(shelves.quickPicks.isEmpty())
    }

    @Test
    fun non_playable_and_uriless_items_are_dropped() {
        val shelves = mapRecommendations(
            listOf(
                folder(
                    "Listen again",
                    items = listOf(
                        track("Good"),
                        track("NoUri", uri = null),
                        track("NotPlayable", playable = false),
                    ),
                )
            )
        )
        assertEquals(listOf("Good"), shelves.quickPicks.map { it.name })
    }

    @Test
    fun non_http_artwork_falls_back_to_null() {
        val item = track("Local", art = "logo.png").toBrowseItemOrNull()
        assertNull(item?.artworkUrl)
    }

    @Test
    fun mixed_for_you_rail_comes_from_the_mixed_for_you_folder() {
        val shelves = mapRecommendations(
            listOf(
                folder("Listen again", items = listOf(track("Song"))),
                folder("Mixed for you", items = listOf(track("Mix", type = "playlist"))),
            )
        )
        assertEquals(listOf("Mix"), shelves.mixedForYou.map { it.name })
    }

    @Test
    fun playlists_keep_youtube_music_rows_and_drop_ma_builtin_ones() {
        val kept = mapPlaylists(
            listOf(
                playlistRow("Workout", providerDomain = "ytmusic"),
                playlistRow("All favorited tracks", providerDomain = "builtin"),
                playlistRow("Random Album", providerDomain = "builtin"),
            )
        )
        assertEquals(listOf("Workout"), kept.map { it.name })
    }

    @Test
    fun playlists_drop_excluded_names_even_when_from_youtube_music() {
        val kept = mapPlaylists(
            listOf(
                playlistRow("Workout", providerDomain = "ytmusic"),
                playlistRow("Memes", providerDomain = "ytmusic"),
                playlistRow("Liked Music (YouTube Music)", providerDomain = "ytmusic"),
            )
        )
        assertEquals(listOf("Workout"), kept.map { it.name })
    }

    @Test
    fun playlist_subtitle_uses_owner_when_no_artist() {
        val playlist = playlistRow("My Mix", providerDomain = "ytmusic").copy(owner = "Music Assistant")
        assertEquals("Music Assistant", playlist.browseSubtitle())
        assertEquals("My Mix", mapPlaylists(listOf(playlist)).single().name)
    }

    @Test
    fun spotify_playlists_keep_only_the_rows_she_owns() {
        val kept = mapSpotifyPlaylists(
            listOf(
                playlistRow("Taylor Swift", providerDomain = "spotify").copy(owner = "Cecilie Weber Andersen"),
                // The stale row MA synthesizes for whichever account was last authenticated.
                playlistRow("Liked Songs Kenneth Weber Andersen", providerDomain = "spotify")
                    .copy(owner = "Kenneth Weber Andersen"),
                // Followed, not owned: a Spotify-curated mix sitting in the same library.
                playlistRow("Your Top Songs 2025", providerDomain = "spotify").copy(owner = "Spotify"),
            )
        )
        assertEquals(listOf("Taylor Swift"), kept.map { it.name })
    }

    @Test
    fun the_two_playlist_shelves_do_not_take_each_others_rows() {
        val ytRow = playlistRow("Workout", providerDomain = "ytmusic").copy(owner = "Cecilie Weber Andersen")
        val spotifyRow = playlistRow("Dance", providerDomain = "spotify").copy(owner = "Cecilie Weber Andersen")
        val rows = listOf(ytRow, spotifyRow)

        // Owner alone doesn't qualify a row — the ytmusic one stays on the M shelf and off the C one.
        assertEquals(listOf("Dance"), mapSpotifyPlaylists(rows).map { it.name })
        assertEquals(listOf("Workout"), mapPlaylists(rows).map { it.name })
    }

    @Test
    fun recently_played_keeps_only_the_requested_providers_items() {
        val folders = listOf(
            folder("Listen again", items = listOf(track("Nope"))),
            folder(
                "Recently played",
                key = "recently_played",
                items = listOf(
                    track("Cruel Summer", uri = "spotify--TkfLc2DT://track/1").copy(provider = "spotify--TkfLc2DT"),
                    track("Nightdrive", uri = "ytmusic--zas2oSHz://track/2").copy(provider = "ytmusic--zas2oSHz"),
                    track("Espresso", uri = "spotify--TkfLc2DT://track/3").copy(provider = "spotify--TkfLc2DT"),
                ),
            ),
        )

        assertEquals(listOf("Cruel Summer", "Espresso"), mapRecentlyPlayed(folders, "spotify").map { it.name })
        assertEquals(listOf("Nightdrive"), mapRecentlyPlayed(folders, "ytmusic").map { it.name })
    }

    @Test
    fun recently_played_is_empty_when_the_server_offers_no_such_folder() {
        // Selected by translation_key, so a folder merely *named* that way is not it.
        val folders = listOf(folder("Recently played", key = null, items = listOf(track("Song"))))

        assertEquals(emptyList(), mapRecentlyPlayed(folders, "ytmusic"))
    }

    @Test
    fun queues_match_rooms_by_danish_display_name_and_drop_non_room_players() {
        val map = matchQueuesToRooms(
            listOf(
                MaQueue(queue_id = "apb28", display_name = "Apple TV Cecilie", items = 0, active = true),
                MaQueue(queue_id = "RINCON_SOVE", display_name = "Soveværelse", items = 3, active = true),
                MaQueue(queue_id = "RINCON_STUE", display_name = "Stue", items = 1, active = false),
                MaQueue(queue_id = "ma_web", display_name = "Web (Chrome on Linux)", items = 16, active = true),
            )
        )
        assertEquals(
            mapOf(Room.Bedroom to "RINCON_SOVE", Room.LivingRoom to "RINCON_STUE"),
            map,
        )
    }

    @Test
    fun grows_google_size_token_but_never_shrinks_and_preserves_suffix() {
        // Small token grows to the target.
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w720-h720-l90-rj",
            upscaleArtworkUrl("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        // Already-large token is only ever grown, not shrunk, and keeps its suffix.
        assertEquals(
            "https://yt3.googleusercontent.com/abc=w900-h900-p",
            upscaleArtworkUrl("https://yt3.googleusercontent.com/abc=w900-h900-p"),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc=s720",
            upscaleArtworkUrl("https://lh3.googleusercontent.com/abc=s90"),
        )
    }

    @Test
    fun strips_downscale_query_from_ytimg_thumbnails() {
        assertEquals(
            "https://i.ytimg.com/vi/sHA_4wfQhE8/hq720.jpg",
            upscaleArtworkUrl("https://i.ytimg.com/vi/sHA_4wfQhE8/hq720.jpg?sqp=-oaymwEKCK&rs=AMzJL3m"),
        )
    }

    @Test
    fun leaves_tokenless_non_google_urls_unchanged() {
        val plain = "https://example.com/cover.jpg"
        assertEquals(plain, upscaleArtworkUrl(plain))
    }

    @Test
    fun browse_item_artwork_is_upscaled() {
        val item = track("Song", art = "https://lh3.googleusercontent.com/x=w60-h60").toBrowseItemOrNull()
        assertEquals("https://lh3.googleusercontent.com/x=w720-h720", item?.artworkUrl)
    }

    @Test
    fun queue_item_prefers_nested_media_item_and_reads_artist_album_uri() {
        val item = MaQueueItem(
            queue_item_id = "q1",
            name = "flat name",
            duration = 184,
            image = MaImageRef(path = "https://img/cover.jpg", remotely_accessible = true),
            media_item = MaMediaItem(
                name = "Sunlight",
                uri = "ytmusic://track/aeCbRZNUt8M",
                media_type = "track",
                is_playable = true,
                artists = listOf(MaNamedRef("Selma Higgins")),
                album = MaNamedRef("Singles"),
            ),
        ).toMediaTrack()
        assertEquals("Sunlight", item.title)
        assertEquals("Selma Higgins", item.artist)
        assertEquals("Singles", item.album)
        assertEquals(184, item.durationSec)
        assertEquals("ytmusic://track/aeCbRZNUt8M", item.uri)
        assertEquals("https://img/cover.jpg", item.artworkUrl)
        // The handle every queue command takes must survive the mapping.
        assertEquals("q1", item.queueItemId)
    }

    @Test
    fun queue_item_art_prefers_the_media_items_square_cover_over_its_own_video_still() {
        val item = MaQueueItem(
            queue_item_id = "q2",
            name = "flat name",
            // What ytmusic queues actually report: a 16:9 video frame with burned-in text.
            image = MaImageRef(path = "https://i.ytimg.com/vi/x/hqdefault.jpg", remotely_accessible = true),
            media_item = MaMediaItem(
                name = "Sunlight",
                uri = "ytmusic://track/1",
                media_type = "track",
                is_playable = true,
                metadata = MaMetadata(
                    images = listOf(
                        MaImageRef(path = "https://lh3.googleusercontent.com/c=w60-h60", type = "thumb"),
                    ),
                ),
            ),
        ).toMediaTrack()
        assertEquals("https://lh3.googleusercontent.com/c=w720-h720", item.artworkUrl)
    }

    @Test
    fun queue_item_still_falls_back_to_a_video_still_when_it_is_the_only_image() {
        val item = MaQueueItem(
            queue_item_id = "q3",
            name = "Only a still",
            image = MaImageRef(
                path = "https://i.ytimg.com/vi/x/hqdefault.jpg?sqp=-oaymwEKCK",
                remotely_accessible = true,
            ),
        ).toMediaTrack()
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", item.artworkUrl)
    }

    @Test
    fun up_next_starts_at_the_first_unplayed_entry() {
        assertEquals(0, upNextOffset(null)) // queue never started
        assertEquals(1, upNextOffset(0))
        assertEquals(6, upNextOffset(5))
    }

    @Test
    fun up_next_never_lists_the_playing_entry_itself() {
        fun row(id: String) = MediaTrack(title = id, artist = "", album = null, durationSec = 60, queueItemId = id)
        val slice = listOf(row("current"), row("next"))
        // A stale current_index right after a replace-play slides the playing entry into the slice.
        assertEquals(listOf(row("next")), slice.withoutQueueItem("current"))
        // No current item (queue never started) leaves the slice alone.
        assertEquals(slice, slice.withoutQueueItem(null))
    }

    @Test
    fun artwork_demotes_a_video_still_even_when_it_is_the_labelled_thumb() {
        val item = MaMediaItem(
            name = "Song",
            uri = "ytmusic://track/1",
            media_type = "track",
            is_playable = true,
            metadata = MaMetadata(
                images = listOf(
                    MaImageRef(path = "https://i.ytimg.com/vi/x/hqdefault.jpg", type = "thumb"),
                    MaImageRef(path = "https://lh3.googleusercontent.com/x=w60-h60", type = "landscape"),
                ),
            ),
        ).toBrowseItemOrNull()
        assertEquals("https://lh3.googleusercontent.com/x=w720-h720", item?.artworkUrl)
    }

    @Test
    fun artwork_prefers_the_square_thumb_over_a_landscape_still_and_upscales_it() {
        val item = MaMediaItem(
            name = "Song",
            uri = "ytmusic://track/1",
            media_type = "track",
            is_playable = true,
            metadata = MaMetadata(
                images = listOf(
                    MaImageRef(path = "https://i.ytimg.com/vi/x/maxresdefault.jpg", type = "landscape"),
                    MaImageRef(path = "https://yt3.googleusercontent.com/x=w60-h60-p", type = "thumb"),
                ),
            ),
        ).toBrowseItemOrNull()
        assertEquals("https://yt3.googleusercontent.com/x=w720-h720-p", item?.artworkUrl)
    }

    @Test
    fun untyped_images_keep_the_first_one() {
        val item = track("Song", art = "https://img/first.jpg").toBrowseItemOrNull()
        assertEquals("https://img/first.jpg", item?.artworkUrl)
    }

    @Test
    fun search_results_are_flattened_tracks_then_albums_then_artists_then_playlists() {
        val items = mapSearchResults(
            MaSearchResults(
                tracks = listOf(track("Song", type = "track")),
                albums = listOf(track("Record", type = "album")),
                artists = listOf(
                    // An artist hit carries no `artists` list of its own — its subtitle comes from MA.
                    MaMediaItem(
                        name = "Band",
                        uri = "ytmusic://artist/1",
                        media_type = "artist",
                        is_playable = true,
                        subtitle = "Kunstner",
                    ),
                ),
                playlists = listOf(track("Mix", type = "playlist")),
            )
        )
        assertEquals(listOf("Song", "Record", "Band", "Mix"), items.map { it.name })
        assertEquals("Kunstner", items[2].subtitle)
    }

    @Test
    fun search_results_drop_unplayable_and_uriless_hits_and_dedupe_across_types() {
        val shared = track("Shared", type = "track")
        val items = mapSearchResults(
            MaSearchResults(
                tracks = listOf(
                    shared,
                    track("NoUri", uri = null),
                    track("NotPlayable", playable = false),
                ),
                // The same item can come back under a second type — it must only tile once.
                albums = listOf(shared, track("Record", type = "album")),
            )
        )
        assertEquals(listOf("Shared", "Record"), items.map { it.name })
    }

    @Test
    fun search_results_map_metadata_images_and_tolerate_art_less_hits() {
        val items = mapSearchResults(
            MaSearchResults(
                tracks = listOf(
                    MaMediaItem(
                        name = "Song",
                        uri = "ytmusic://track/1",
                        media_type = "track",
                        is_playable = true,
                        metadata = MaMetadata(
                            images = listOf(
                                MaImageRef(
                                    path = "https://yt3.googleusercontent.com/x=w120-h120-p",
                                    type = "thumb",
                                    remotely_accessible = true,
                                ),
                            ),
                        ),
                    ),
                    track("Bare", art = null),
                ),
            )
        )
        assertEquals("https://yt3.googleusercontent.com/x=w720-h720-p", items[0].artworkUrl)
        assertNull(items[1].artworkUrl)
    }

    @Test
    fun queue_current_item_maps_to_a_track_carrying_its_art_and_uri() {
        val current = MaQueue(
            queue_id = "RINCON_STUE",
            display_name = "Stue",
            items = 400,
            current_index = 3,
            current_item = MaQueueItem(
                queue_item_id = "q9",
                name = "flat name",
                duration = 212,
                image = MaImageRef(path = "https://yt3.googleusercontent.com/c=w600-h600-p", type = "thumb"),
                media_item = MaMediaItem(
                    name = "Sunlight",
                    uri = "ytmusic://track/aeCbRZNUt8M",
                    media_type = "track",
                    is_playable = true,
                    artists = listOf(MaNamedRef("Selma Higgins")),
                ),
            ),
        ).current_item?.toMediaTrack()
        assertEquals("Sunlight", current?.title)
        assertEquals("https://yt3.googleusercontent.com/c=w720-h720-p", current?.artworkUrl)
        assertEquals("ytmusic://track/aeCbRZNUt8M", current?.uri)
        assertEquals("q9", current?.queueItemId)
    }

    // --- Artist drill-in ---

    @Test
    fun browseItems_carryTheirMediaTypeAsAKind() {
        assertEquals(BrowseKind.Track, track("A").toBrowseItemOrNull()?.kind)
        assertEquals(BrowseKind.Album, track("A", type = "album").toBrowseItemOrNull()?.kind)
        assertEquals(BrowseKind.Artist, track("A", type = "artist").toBrowseItemOrNull()?.kind)
        assertEquals(BrowseKind.Playlist, track("A", type = "playlist").toBrowseItemOrNull()?.kind)
        assertEquals(BrowseKind.Other, track("A", type = "audiobook").toBrowseItemOrNull()?.kind)
    }

    @Test
    fun aNonPlayableArtistSurvives_butANonPlayableTrackIsStillDropped() {
        // An artist tile is a navigation target, not a play target, and MA marks plenty of artist
        // hits non-playable — dropping them would empty the search grid of artists entirely.
        val artist = track("Nirvana", type = "artist", playable = false).toBrowseItemOrNull()
        assertEquals("Nirvana", artist?.name)
        assertEquals(BrowseKind.Artist, artist?.kind)

        assertNull(track("Ghost", playable = false).toBrowseItemOrNull())
        // The uri guard still applies to artists — there is nothing to drill into without one.
        assertNull(track("Nirvana", uri = null, type = "artist", playable = false).toBrowseItemOrNull())
    }

    @Test
    fun aYouTubeChannelIdTypedAlbumIsReclassifiedAsAnArtist() {
        // "Listen again" returns artist channels typed `album`; only an MPREb_ id is a real album.
        val channel = track("DeathbyRomy", uri = "ytmusic--zas2oSHz://album/UCz8gfgZsjQW6zsuI4DgOC8A", type = "album")
        assertEquals(BrowseKind.Artist, channel.toBrowseItemOrNull()?.kind)

        val album = track("House In The Woods", uri = "ytmusic--zas2oSHz://album/MPREb_jQV0GR88Mke", type = "album")
        assertEquals(BrowseKind.Album, album.toBrowseItemOrNull()?.kind)

        // Gated on ytmusic, so another provider's id scheme can't false-positive.
        val other = track("Andet", uri = "spotify://album/UCsomething", type = "album")
        assertEquals(BrowseKind.Album, other.toBrowseItemOrNull()?.kind)
    }

    @Test
    fun parseMaUri_splitsProviderMediaTypeAndItemId() {
        val ref = parseMaUri("ytmusic--zas2oSHz://artist/UCrPe3hLA51968GwxHSZ1llw")
        assertEquals("ytmusic--zas2oSHz", ref?.provider)
        assertEquals("artist", ref?.mediaType)
        assertEquals("UCrPe3hLA51968GwxHSZ1llw", ref?.itemId)

        val library = parseMaUri("library://artist/42")
        assertEquals("library", library?.provider)
        assertEquals("42", library?.itemId)
    }

    @Test
    fun parseMaUri_rejectsWhatItCannotAddress() {
        assertNull(parseMaUri("not-a-uri"))
        assertNull(parseMaUri("ytmusic://artist"))     // no item id
        assertNull(parseMaUri("://artist/1"))          // no provider
    }

    @Test
    fun artistTracksAndAlbums_dedupeByUriAndCapTheirLists() {
        val dupes = listOf(track("Hit"), track("Hit"), track("Andet"))
        assertEquals(listOf("Hit", "Andet"), mapArtistTracks(dupes).map { it.name })
        assertEquals(listOf("Hit", "Andet"), mapArtistAlbums(dupes).map { it.name })

        // The caps are what keep a 200-track discography from becoming a 22-page grid.
        val many = (1..40).map { track("T$it", type = "album") }
        assertEquals(27, mapArtistTracks(many).size)
        assertEquals(24, mapArtistAlbums(many).size)
    }
}
