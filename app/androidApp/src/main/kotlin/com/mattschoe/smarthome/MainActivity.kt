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
import com.mattschoe.smarthome.data.SharedPreferencesStore
import com.mattschoe.smarthome.media.SmartHomeMediaService
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
}

class AppApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Android's key/value store needs a Context, which only exists here — hence supplying it
        // rather than letting AppContainer resolve one (see platformKeyValueStore).
        appContainer = AppContainer(keyValueStore = SharedPreferencesStore(this))
    }
}
