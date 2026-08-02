package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.PageIndicator
import com.mattschoe.smarthome.ui.components.PageIndicatorOrientation
import com.mattschoe.smarthome.ui.layout.CompactArrangement
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.SageGreen
import com.mattschoe.smarthome.ui.theme.SageSurface

/**
 * The phone dashboard: the tablet's three cards re-flowed onto swipeable pages. The arrangement comes
 * from the window's aspect ratio ([CompactArrangement]), so rotating — or resizing the desktop window —
 * swaps between them.
 *
 * This phase builds the navigation skeleton only: the pagers, their indicators, and named placeholder
 * pages. Phases P2–P5 fill the pages in with controls promoted out of the tablet cards, which is why
 * [ready] and [viewModel] are already threaded down to the page composables.
 */
@Composable
fun CompactDashboard(ready: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (CompactArrangement.from(maxWidth, maxHeight)) {
            CompactArrangement.Portrait -> PortraitPages(ready, viewModel)
            CompactArrangement.Landscape -> LandscapePages(ready, viewModel)
        }
    }
}

/** The portrait pages, left to right. The pager opens on [PORTRAIT_START_PAGE] — Light Control. */
private val PortraitPageTitles = listOf("Apps", "Lysstyring", "Musik", "Medier · Kalender")
private const val PORTRAIT_START_PAGE = 1

/** The landscape pages, top to bottom, as (left card, right card) titles. */
private val LandscapeCardTitles = listOf(
    "Lys" to "Rum",
    "Afspiller nu" to "Medier",
    "Apps" to "Kalender",
)

/**
 * Portrait: four horizontally paged screens on a full-bleed cream surface — no floating card, the
 * content sits directly on the page. The dot row floats bottom-centre over it.
 */
@Composable
private fun PortraitPages(ready: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    val pagerState = rememberPagerState(
        initialPage = PORTRAIT_START_PAGE,
        pageCount = { PortraitPageTitles.size },
    )
    Box(Modifier.fillMaxSize().background(Card)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            PortraitPage(
                page = page,
                ready = ready,
                viewModel = viewModel,
                // Insets are applied here rather than around the pager so a page's content clears the
                // status bar and gesture pill while the pager itself still spans the whole screen.
                // The side margin is deliberately *not* applied here: a page that runs a control to the
                // screen edge (the light page's chip row) needs the full width, so each page pads itself.
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
        PageIndicator(
            state = pagerState,
            orientation = PageIndicatorOrientation.Horizontal,
            idleColor = SageGreen,
            activeLength = Dimensions.pageIndicatorActive,
            gap = Dimensions.pageIndicatorGap,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = Dimensions.pageIndicatorInset),
        )
    }
}

/**
 * Landscape: three vertically paged screens on the sage surface, each holding two equal cream
 * [CardContainer]s — literally the tablet's card. The indicator floats in the right-hand margin beside
 * them, so the cards keep the full width between the outer paddings.
 */
@Composable
private fun LandscapePages(ready: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    val pagerState = rememberPagerState(pageCount = { LandscapeCardTitles.size })
    Box(Modifier.fillMaxSize().background(SageSurface)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Dimensions.phoneSurfacePad),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.phoneCardGap),
            ) {
                LandscapePage(page = page, ready = ready, viewModel = viewModel)
            }
        }
        PageIndicator(
            state = pagerState,
            orientation = PageIndicatorOrientation.Vertical,
            idleColor = Card.copy(alpha = 0.45f),
            activeLength = Dimensions.pageIndicatorActive,
            gap = Dimensions.pageIndicatorGap,
            // Centred in the outer margin the cards already leave, rather than adding one of its own.
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = (Dimensions.phoneSurfacePad - Dimensions.pageDotSize) / 2),
        )
    }
}

/** One portrait screen. P3–P4 replace the two remaining placeholders. */
@Composable
private fun PortraitPage(
    page: Int,
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    when (page) {
        0 -> PortraitAppsPage(modifier)
        1 -> PortraitLightPage(ready, viewModel, modifier)
        else -> PagePlaceholder(PortraitPageTitles[page], modifier)
    }
}

/** One landscape screen's pair of cards, laid into the [Row] the caller supplies. P5 fills them in. */
@Composable
private fun RowScope.LandscapePage(
    page: Int,
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
) {
    val (left, right) = LandscapeCardTitles[page]
    CardContainer(Modifier.weight(1f).fillMaxHeight()) {
        PagePlaceholder(left, Modifier.fillMaxSize())
    }
    CardContainer(Modifier.weight(1f).fillMaxHeight()) {
        PagePlaceholder(right, Modifier.fillMaxSize())
    }
}

/** Names an as-yet-unbuilt page so paging is legible while the scaffold is verified. */
@Composable
private fun PagePlaceholder(title: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(title, color = InkSoft, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
