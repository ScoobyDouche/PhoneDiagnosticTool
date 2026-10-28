package com.phonediagnostic.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.phonediagnostic.ui.AppScreen
import com.phonediagnostic.ui.DeviceViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * LazyListState that restores the last first-visible index/offset for [screen]
 * from the [viewModel], and writes back on dispose and when scrolling stops.
 */
@Composable
fun rememberPersistedLazyListState(
    screen: AppScreen,
    viewModel: DeviceViewModel
): LazyListState {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.listScrollIndex(screen),
        initialFirstVisibleItemScrollOffset = viewModel.listScrollOffset(screen)
    )
    DisposableEffect(screen) {
        onDispose {
            viewModel.saveListScroll(
                screen,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset
            )
        }
    }
    LaunchedEffect(screen, state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { inProgress -> !inProgress }
            .collect {
                viewModel.saveListScroll(
                    screen,
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset
                )
            }
    }
    return state
}
