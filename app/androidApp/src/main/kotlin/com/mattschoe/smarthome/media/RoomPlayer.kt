package com.mattschoe.smarthome.media

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mattschoe.smarthome.data.NowPlayingBridge
import com.mattschoe.smarthome.data.NowPlayingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The [Player] the media session is built on. The app plays no audio itself — Music Assistant does,
 * in the room — so this is a remote control rather than a playback engine: it renders whatever the
 * [bridge] publishes about the active audio room, and forwards the notification's transport back
 * through the bridge's commands.
 *
 * The device volume is the room's, hence [DeviceInfo.PLAYBACK_TYPE_REMOTE] with a 0–100 range: the
 * hardware keys and the notification's volume row set the speaker's level, not the phone's.
 *
 * Every state change arrives as a snapshot on [scope], which invalidates the state media3 then pulls
 * back through [getState].
 */
class RoomPlayer(
    private val bridge: NowPlayingBridge,
    scope: CoroutineScope,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    init {
        scope.launch {
            bridge.snapshot.collect { invalidateState() }
        }
    }

    override fun getState(): State {
        val snapshot = bridge.snapshot.value
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackState(if (snapshot == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlayWhenReady(
                snapshot?.isPlaying == true,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setDeviceInfo(REMOTE_DEVICE)
            .setDeviceVolume(snapshot?.volumePct ?: 0)
        if (snapshot != null) {
            builder
                .setPlaylist(listOf(snapshot.toMediaItemData()))
                .setContentPositionMs(
                    // Extrapolated while playing, so the notification's progress bar advances between
                    // the (coarse) position updates the home reports.
                    if (snapshot.isPlaying) {
                        PositionSupplier.getExtrapolating(snapshot.positionSec * 1_000L, 1f)
                    } else {
                        PositionSupplier.getConstant(snapshot.positionSec * 1_000L)
                    },
                )
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // The home owns the actual play/pause state; the session only asks for the toggle, and the
        // next snapshot is what confirms it.
        if (playWhenReady != (bridge.snapshot.value?.isPlaying == true)) bridge.commands?.togglePlay()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                bridge.commands?.next()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                bridge.commands?.previous()
            else -> bridge.commands?.seek((positionMs / 1_000L).toInt().coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        bridge.commands?.setVolume(deviceVolume.coerceIn(0, MAX_VOLUME))
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        stepVolume(VOLUME_STEP)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        stepVolume(-VOLUME_STEP)

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun stepVolume(delta: Int): ListenableFuture<*> {
        val current = bridge.snapshot.value?.volumePct ?: return Futures.immediateVoidFuture()
        bridge.commands?.setVolume((current + delta).coerceIn(0, MAX_VOLUME))
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val MAX_VOLUME = 100

        /** What one press of a volume key moves the room by — the slider's own keyboard step. */
        const val VOLUME_STEP = 5

        val REMOTE_DEVICE: DeviceInfo =
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(MAX_VOLUME).build()

        /**
         * Transport plus remote volume, and the metadata commands the notification needs to read the
         * track. Deliberately no shuffle/repeat/queue: those are the in-app panel's, and a
         * notification that offered them would be offering a queue it never shows.
         */
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_RELEASE,
            )
            .build()

        /** The single-item "playlist" the session shows: the room's current track. */
        fun NowPlayingSnapshot.toMediaItemData(): MediaItemData {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
            return MediaItemData.Builder("${room.name}:$title:$artist")
                .setMediaItem(MediaItem.Builder().setMediaMetadata(metadata).build())
                .setMediaMetadata(metadata)
                .setDurationUs(
                    if (durationSec > 0) durationSec * 1_000_000L else C.TIME_UNSET,
                )
                .setIsSeekable(durationSec > 0)
                .setIsDynamic(false)
                .build()
        }
    }
}
