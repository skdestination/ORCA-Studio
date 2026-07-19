package com.litecut.app.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose wrapper container hosting our high-performance native TimelineView.
 * Ensures Compose does NOT handle rendering loops, delegating all drawing to low-level canvas.
 */
@Composable
fun TimelineContainer(
    modifier: Modifier = Modifier,
    onTimelineViewCreated: (TimelineView) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(TimelineTheme.backgroundColor))
    ) {
        AndroidView(
            factory = { context ->
                TimelineView(context).apply {
                    // Pre-fill with a professional editing layout of mock tracks/clips
                    setupDefaultMockTracks()
                    onTimelineViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Forces repaint ticks upon recomposition triggers
                view.invalidate()
            }
        )
    }
}
