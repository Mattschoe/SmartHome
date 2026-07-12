package com.mattschoe.smarthome.data

/**
 * Connection details for a live Home Assistant instance, sourced from `local.properties` via the
 * code-generated `BuildSecrets` (see [com.mattschoe.smarthome.haConfigFromSecrets]). [url] is the HA
 * host [token] is a long-lived access token.
 */
data class HaConfig(
    val url: String,
    val token: String,
) {
    val hasToken: Boolean get() = token.isNotBlank()

    /** `true` when the host asks for TLS (`https`/`wss`) — drives `ws` vs `wss` and `http` vs `https`. */
    val secure: Boolean get() = url.startsWith("https") || url.startsWith("wss")

    /** The host without any scheme prefix, e.g. `homeassistant.local:8123`. */
    val host: String
        get() = url
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("wss://").removePrefix("ws://")
            .trimEnd('/')

    /** WebSocket endpoint, e.g. `ws://homeassistant.local:8123/api/websocket`. */
    val webSocketUrl: String get() = "${if (secure) "wss" else "ws"}://$host/api/websocket"

    /** HTTP base for resolving relative artwork paths (`entity_picture`), e.g. `http://…:8123`. */
    val httpBase: String get() = "${if (secure) "https" else "http"}://$host"
}
