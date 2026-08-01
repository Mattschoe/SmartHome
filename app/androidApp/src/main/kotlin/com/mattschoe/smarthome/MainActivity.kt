package com.mattschoe.smarthome

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mattschoe.smarthome.data.SharedPreferencesStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as AppApplication
        val appContainer = app.appContainer

        setContent {
            App(appContainer)
        }
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