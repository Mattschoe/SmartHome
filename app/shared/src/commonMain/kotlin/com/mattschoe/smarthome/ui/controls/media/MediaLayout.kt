package com.mattschoe.smarthome.ui.controls.media

/**
 * Which arrangement the shared media kit renders in. The two surfaces are the *same* composables with
 * the same state, and by now they differ in exactly one thing: the now-playing header, a row beside the
 * art on the card and a centered column under it on the page. Everything else — the browse grid, the
 * paged shelves, the transport, the queue — is identical, because a portrait page turns out to be about
 * as wide as the right card is.
 */
enum class MediaLayout {
    /** The right card at its reference width — the tablet dashboard. */
    Tablet,

    /** A full portrait page — the phone's Music page. */
    Phone,
}
