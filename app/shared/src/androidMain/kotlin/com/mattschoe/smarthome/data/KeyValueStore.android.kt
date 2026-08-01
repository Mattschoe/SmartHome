package com.mattschoe.smarthome.data

import android.content.Context

/**
 * Android has no ambient application context to reach for, so the store is built by the entry point
 * ([SharedPreferencesStore]) and handed to `AppContainer` rather than resolved from here.
 */
actual fun platformKeyValueStore(): KeyValueStore? = null

/** A [KeyValueStore] over `SharedPreferences`. Constructed in `AppApplication`, which has the context. */
class SharedPreferencesStore(context: Context) : KeyValueStore {

    private val prefs = context.getSharedPreferences("smarthome", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
