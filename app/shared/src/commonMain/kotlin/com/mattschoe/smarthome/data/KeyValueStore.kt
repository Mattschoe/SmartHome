package com.mattschoe.smarthome.data

/**
 * A minimal string key/value store — the persistence seam the wall tablet needs to survive reloads.
 * Deliberately tiny: the only thing kept between runs is the calendar snapshot, and a full settings
 * or database dependency would be far more machinery than one JSON blob deserves.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * The platform's own store, or `null` where one cannot be built without help — on Android a store
 * needs a `Context`, so the Android entry point supplies it to [com.mattschoe.smarthome.AppContainer]
 * instead. A `null` simply means no offline cache, never a failure.
 */
expect fun platformKeyValueStore(): KeyValueStore?
