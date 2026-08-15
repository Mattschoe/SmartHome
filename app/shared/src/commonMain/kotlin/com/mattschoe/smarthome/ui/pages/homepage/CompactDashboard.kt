package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.ui.components.PageIndicator
import com.mattschoe.smarthome.ui.components.PageIndicatorOrientation
import com.mattschoe.smarthome.ui.layout.CompactArrangement
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.SageGreen
import com.mattschoe.smarthome.ui.theme.SageSurface

/**
 * The phone dashboard: the tablet's three cards re-flowed onto swipeable pages. The arrangement comes
 * from the window's aspect ratio ([CompactArrangement]), so rotating — or resizing the desktop window —
 * swaps between them. The portrait pages page horizontally, the landscape ones vertically; both are
 * built out of the same controls the tablet cards compose.
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
private val PortraitPageTitles = listOf("Apps", "Lysstyring", "Musik", "Kalender")
private const val PORTRAIT_START_PAGE = 1
private const val PORTRAIT_CALENDAR_PAGE = 3

/** The landscape pages, top to bottom — their card pairs live in the pages themselves. */
private const val LANDSCAPE_PAGE_COUNT = 3
private const val LANDSCAPE_CALENDAR_PAGE = 2

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
    // The phone splits the tablet's right card across two pages, so *paging* is what selects the
    // panel here — there is no tab row to do it. Reading `targetPage` rather than `settledPage` swaps
    // the state while the page is still sliding in, so the calendar arrives already in week view
    // instead of showing a frame of the month grid; the pages themselves are cheap to compose either
    // way. Setting the view is not cosmetic: `visibleEvents` filters by the *state's* view, so a
    // forced week view rendered while state says month would draw the month's filter set.
    LaunchedEffect(pagerState, viewModel) {
        snapshotFlow { pagerState.targetPage }.collect { page ->
            if (page == PORTRAIT_CALENDAR_PAGE) {
                viewModel.selectPanel(Panel.Calendar)
                viewModel.setCalendarView(CalendarView.Week)
            } else {
                viewModel.selectPanel(Panel.Media)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Card)) {
        HorizontalPager(
            state = pagerState,
            // The editor's fields are `remember(target)`-local, so swiping away would silently discard
            // what has been typed. Its own back arrow is the way out.
            userScrollEnabled = ready.eventEditor == null,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
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
        // The same host the tablet floats over its cards — a failed play on the music page has to say
        // so here too. Cleared of the dot row it shares the bottom edge with.
        ToastHost(
            toast = ready.toast,
            onDismiss = viewModel::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = Dimensions.phonePageBottomClearance),
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
    val pagerState = rememberPagerState(pageCount = { LANDSCAPE_PAGE_COUNT })
    // The phone splits the tablet's right card across page 2's right card, so *paging* is what
    // selects the panel here — there is no tab row to do it, the same convention as the portrait
    // pager. Reading `targetPage` rather than `settledPage` swaps the state while the page is still
    // sliding in, so the calendar arrives already in week view instead of showing a frame of the
    // month grid. Setting the view is not cosmetic: `visibleEvents` filters by the *state's* view.
    LaunchedEffect(pagerState, viewModel) {
        snapshotFlow { pagerState.targetPage }.collect { page ->
            if (page == LANDSCAPE_CALENDAR_PAGE) {
                viewModel.selectPanel(Panel.Calendar)
                viewModel.setCalendarView(CalendarView.Week)
            } else {
                viewModel.selectPanel(Panel.Media)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(SageSurface)) {
        VerticalPager(
            state = pagerState,
            // The editor's fields are `remember(target)`-local, so swiping away would silently discard
            // what has been typed. Its own back arrow is the way out.
            userScrollEnabled = ready.eventEditor == null,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            LandscapePage(
                page = page,
                ready = ready,
                viewModel = viewModel,
                // Insets are applied here rather than around the pager so a page's content clears the
                // status bar and gesture pill while the pager itself still spans the whole screen. The
                // side margin is applied here too — unlike the portrait pages, no landscape control
                // runs to the screen edge, so the pages can share one padded modifier.
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Dimensions.phoneSurfacePad),
            )
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
        // The same host the tablet floats over its cards — a failed play on the music page has to say
        // so here too, like the portrait pages.
        ToastHost(
            toast = ready.toast,
            onDismiss = viewModel::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = Dimensions.phoneSurfacePad),
        )
    }
}

/** One portrait screen. */
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
        2 -> PortraitMusicPage(ready, viewModel, modifier)
        else -> PortraitCalendarPage(ready, viewModel, modifier)
    }
}

/** One landscape screen's pair of cards, laid out by the page that owns it. */
@Composable
private fun LandscapePage(
    page: Int,
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    when (page) {
        0 -> LandscapeHomePage(ready, viewModel, modifier)
        1 -> LandscapeMusicPage(ready, viewModel, modifier)
        else -> LandscapeCalendarPage(ready, viewModel, modifier)
    }
}
