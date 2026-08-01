package com.mattschoe.smarthome.data

import platform.Foundation.NSUserDefaults

/** iOS keeps the snapshot in the app's own `NSUserDefaults` — no context or file handling needed. */
actual fun platformKeyValueStore(): KeyValueStore? = UserDefaultsStore()

private class UserDefaultsStore : KeyValueStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String) {
        defaults.setObject(value, key)
    }
}
