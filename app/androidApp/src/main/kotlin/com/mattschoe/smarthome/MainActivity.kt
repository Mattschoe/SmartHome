package com.mattschoe.smarthome

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mattschoe.smarthome.data.SharedPreferencesStore
import com.mattschoe.smarthome.media.SmartHomeMediaService
import com.mattschoe.smarthome.reminders.AndroidAlarmScheduler
import com.mattschoe.smarthome.reminders.ReminderSyncWorker
import com.mattschoe.smarthome.reminders.SystemNotificationPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Declined is a fine outcome: without it the session still runs, it just has no notification.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Alive between onStart and onStop — the window in which starting the service is legal. */
    private var foregroundScope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val app = application as AppApplication
        val appContainer = app.appContainer

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App(appContainer)
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 14 refuses a foreground-service start from the background, so the service is only
        // ever started from here — while the activity is visible — and only once the active audio
        // room actually has a track. Once running it outlives the activity; it stops itself when the
        // room falls silent (see SmartHomeMediaService).
        val bridge = (application as AppApplication).appContainer.nowPlaying
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        foregroundScope = scope
        scope.launch {
            bridge.snapshot.collect { snapshot ->
                if (snapshot != null) {
                    startService(Intent(this@MainActivity, SmartHomeMediaService::class.java))
                }
            }
        }
    }

    override fun onStop() {
        foregroundScope?.cancel()
        foregroundScope = null
        super.onStop()
    }

    // The system nudges bars back on focus loss/regain (e.g. after a dialog, app-switcher, or
    // notification shade); this is the documented hook to re-hide them for a wall-mounted kiosk.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

class AppApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Android's key/value store needs a Context, which only exists here — hence supplying it
        // rather than letting AppContainer resolve one (see platformKeyValueStore).
        val store = SharedPreferencesStore(this)
        val role = deviceRole()
        appContainer = AppContainer(
            keyValueStore = store,
            deviceRole = role,
            alarmScheduler = AndroidAlarmScheduler(this, store),
            notifications = SystemNotificationPresenter(this),
        )
        // The phone keeps its alarms current even when nobody opens the dashboard, so an event the
        // *other* phone created still reminds here. The wall display arms nothing, so it syncs
        // nothing — and drops a schedule left behind by an earlier build.
        if (role == DeviceRole.Phone) ReminderSyncWorker.schedule(this) else ReminderSyncWorker.cancel(this)
    }

    /**
     * Which device this is. Read off the screen's smallest dimension, the same 600dp line
     * `DashboardLayout.from` draws — a phone is somebody's own device and reminds; the wall tablet
     * is furniture and stays silent.
     */
    private fun deviceRole(): DeviceRole =
        if (resources.configuration.smallestScreenWidthDp >= WallDisplayMinWidthDp) DeviceRole.WallDisplay
        else DeviceRole.Phone

    private companion object {
        /** Matches `DashboardLayout.compactMaxWidth`, in the units the configuration reports. */
        const val WallDisplayMinWidthDp = 600
    }
}
