package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.ma.MusicData
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `HomeState.withMusic` overlay: HA stays device truth, MA fills in what HA reports worse. */
class CompositeMergeTest {

    private val haTrack = MediaTrack(
        title = "Sunlight",
        artist = "Selma Higgins",
        album = "Singles",
        artworkUrl = "http://192.168.1.49:8095/imageproxy/abc?size=512&fmt=jpg",
        durationSec = 212,
    )

    private fun homePlaying(track: MediaTrack?): HomeState {
        val base = seedHome()
        val room = base.rooms.getValue(Room.LivingRoom)
        val audio = requireNotNull(room.audio).copy(nowPlaying = track)
        return base.copy(rooms = base.rooms + (Room.LivingRoom to room.copy(audio = audio)))
    }

    private fun musicWith(current: MediaTrack?) = MusicData.EMPTY.copy(
        nowPlayingByRoom = current?.let { mapOf(Room.LivingRoom to it) } ?: emptyMap(),
    )

    private fun HomeState.livingNowPlaying(): MediaTrack? =
        rooms.getValue(Room.LivingRoom).audio?.nowPlaying

    @Test
    fun matchingTitle_takesMaArtworkAndHandlesButKeepsHaMetadata() {
        val merged = homePlaying(haTrack).withMusic(
            musicWith(
                MediaTrack(
                    title = "sunlight ", // same track, differently cased/padded
                    artist = "",
                    album = null,
                    artworkUrl = "https://yt3.googleusercontent.com/x=w720-h720-p",
                    durationSec = 0,
                    uri = "ytmusic://track/aeCbRZNUt8M",
                    queueItemId = "q9",
                ),
            )
        )

        val track = merged.livingNowPlaying()
        assertEquals("https://yt3.googleusercontent.com/x=w720-h720-p", track?.artworkUrl)
        assertEquals("ytmusic://track/aeCbRZNUt8M", track?.uri)
        assertEquals("q9", track?.queueItemId)
        // HA remains authoritative for everything it reports well.
        assertEquals("Sunlight", track?.title)
        assertEquals("Selma Higgins", track?.artist)
        assertEquals("Singles", track?.album)
        assertEquals(212, track?.durationSec)
    }

    @Test
    fun mismatchedTitle_keepsHaTrackWhole() {
        val merged = homePlaying(haTrack).withMusic(
            musicWith(
                MediaTrack(
                    title = "Some Other Song",
                    artist = "",
                    album = null,
                    artworkUrl = "https://yt3.googleusercontent.com/wrong=w720-h720-p",
                    durationSec = 0,
                    uri = "ytmusic://track/wrong",
                ),
            )
        )
        assertEquals(haTrack, merged.livingNowPlaying())
    }

    @Test
    fun noMaCurrentItem_leavesHaTrackUntouched() {
        assertEquals(haTrack, homePlaying(haTrack).withMusic(MusicData.EMPTY).livingNowPlaying())
    }

    @Test
    fun queueIsReplacedByTheMaQueueAndShelvesOnlyWhenMaHasThem() {
        val queue = listOf(
            MediaTrack("Next", "Someone", album = null, durationSec = 100, queueItemId = "q1"),
        )
        val merged = homePlaying(haTrack).withMusic(
            MusicData.EMPTY.copy(queuesByRoom = mapOf(Room.LivingRoom to queue)),
        )
        assertEquals(queue, merged.rooms.getValue(Room.LivingRoom).audio?.queue)
        // An empty MA shelf never blanks what HA (or the seed) already provided.
        assertEquals(seedHome().quickPicks, merged.quickPicks)
    }
}
