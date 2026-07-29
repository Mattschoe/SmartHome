package com.mattschoe.smarthome.data

/**
 * Connection details for the home's **Music Assistant** server, whose own WebSocket API exposes the
 * rich music data (YouTube Music recommendations, the full play queue, radio continuation) that the
 * Home Assistant `music_assistant.*` service proxy cannot reach. Sourced from `local.properties` via
 * the code-generated `BuildSecrets` (see [com.mattschoe.smarthome.maConfigFromSecrets]).
 *
 * [token] is a **separate** MA long-lived token, distinct from the HA one. [url] is optional: when
 * blank, the WS endpoint is derived from [haHost] as `ws://<ha-host>:8095/ws` (MA runs on the same
 * box as HA in this home). Supply [url] only when MA lives elsewhere or on a non-default port.
 */
data class MaConfig(
    val token: String,
    /** Explicit MA endpoint, e.g. `ws://192.168.1.49:8095/ws` or just `192.168.1.49:8095`. May be blank. */
    val url: String = "",
    /** HA host (`192.168.1.49:8123`) used to derive the MA endpoint when [url] is blank. */
    val haHost: String = "",
) {
    val hasToken: Boolean get() = token.isNotBlank()

    private val secure: Boolean get() = url.startsWith("https") || url.startsWith("wss")

    /** WebSocket endpoint for the MA server, e.g. `ws://192.168.1.49:8095/ws`. */
    val webSocketUrl: String
        get() = when {
            url.isBlank() -> "ws://${hostOnly(haHost)}:$DEFAULT_PORT/ws"
            // A bare host[:port] — assume the default ws scheme and /ws path.
            "://" !in url -> "ws://${appendDefaultPort(url.trimEnd('/'))}/ws"
            else -> {
                val scheme = if (secure) "wss" else "ws"
                val rest = url.substringAfter("://").trimEnd('/')
                val hostPort = appendDefaultPort(rest.substringBefore('/'))
                val path = rest.substringAfter('/', missingDelimiterValue = "").ifBlank { "ws" }
                "$scheme://$hostPort/$path"
            }
        }

    private fun hostOnly(hostPort: String): String = hostPort.substringBefore(':')

    /** Add the default MA port to a `host` that carries no explicit `:port`. */
    private fun appendDefaultPort(hostPort: String): String =
        if (':' in hostPort) hostPort else "$hostPort:$DEFAULT_PORT"

    private companion object {
        const val DEFAULT_PORT = 8095
    }
}
