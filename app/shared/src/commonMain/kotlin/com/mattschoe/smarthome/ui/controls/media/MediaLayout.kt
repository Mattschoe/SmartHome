package com.mattschoe.smarthome.ui.controls.media

import com.mattschoe.smarthome.ui.theme.Dimensions

/**
 * Which arrangement the shared media kit renders in. The two surfaces are the *same* composables
 * with the same state — this only re-flows the three things a phone cannot take from the tablet:
 * the now-playing header (a row beside the art vs. a centered column under it), how many tiles a
 * browse row fits, and whether a quick-picks shelf may page horizontally.
 */
enum class MediaLayout {
    /** The right card at its reference width — the tablet dashboard. */
    Tablet,

    /** A full portrait page — the phone's Music page. */
    Phone;

    /** Tiles per browse row: three across the card, two across the narrower page. */
    val browseGridColumns: Int
        get() = when (this) {
            Tablet -> Dimensions.browseGridColumns
            Phone -> Dimensions.phoneBrowseGridColumns
        }

    /**
     * Whether a deep shelf may render as a horizontally-paged grid. On the phone it may not: that
     * pager sits *inside* the page pager and the two would fight for the same horizontal drag, so
     * the shelf falls back to one flat vertical grid (which is what the phone mock shows anyway).
     */
    val allowsPagedShelves: Boolean get() = this == Tablet
}
