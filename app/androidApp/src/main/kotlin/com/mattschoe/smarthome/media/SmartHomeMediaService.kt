package com.mattschoe.smarthome.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mattschoe.smarthome.AppApplication
import com.mattschoe.smarthome.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Publishes the active audio room's playback as a system media session, so the notification shade,
 * the lock screen and the volume keys control the room without the app in front.
 *
 * The session is a remote control over [RoomPlayer] — the app never renders audio, so the service
 * exists only while there is something to show: it stops itself when the room falls silent, and when
 * the task is swiped away.
 */
class SmartHomeMediaService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val bridge = (application as AppApplication).appContainer.nowPlaying
        session = MediaSession.Builder(this, RoomPlayer(bridge, scope))
            .setSessionActivity(openAppIntent())
            .build()
        // Nothing playing anywhere is nothing to show: the notification would otherwise sit in the
        // tray as a dead remote for a room that stopped hours ago.
        scope.launch {
            bridge.snapshot.collect { snapshot -> if (snapshot == null) stopSelf() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Tapping the notification returns to the dashboard rather than launching a second task. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
