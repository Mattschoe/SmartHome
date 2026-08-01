package com.mattschoe.smarthome.data

import java.io.File

/** Desktop keeps the snapshot under the user's home, one file per key — the laptop is a dev target. */
actual fun platformKeyValueStore(): KeyValueStore? = FileStore(
    File(System.getProperty("user.home"), ".smarthome")
)

private class FileStore(private val dir: File) : KeyValueStore {

    override fun get(key: String): String? = fileFor(key).takeIf { it.isFile }?.readText()

    override fun put(key: String, value: String) {
        dir.mkdirs()
        fileFor(key).writeText(value)
    }

    private fun fileFor(key: String) = File(dir, key.filter { it.isLetterOrDigit() || it == '.' })
}
