package com.litecut.app.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Section D: Mid-Transport Controls Bar matching React Image 2 exactly.
 */
@Composable
fun EditorTransportBar(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    currentTime: Double,
    totalDuration: Double,
    hasKeyframeAtCurrentTime: Boolean,
    onToggleKeyframe: () -> Unit,
    onOpenKeyframeCurves: () -> Unit,
    hasSelectedClip: Boolean,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeFormatted = remember(currentTime) {
        val mins = (currentTime / 60).toInt()
        val secs = (currentTime % 60).toInt()
        String.format("%02d:%02d", mins, secs)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF09090B))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT GROUP: Timecode text + Step Back + Bright White Circle Play Button with Black Arrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Monospace Time Readout matching "00:03" in React Image 2
            Text(
                text = currentTimeFormatted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFA1A1AA)
            )

            // Step Back Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF18181B))
                    .clickable { /* Step back */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Step Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // White Circular Play/Pause Button with Black Arrow matching React Image 2 exactly!
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // RIGHT GROUP: Single Unified Frosted Capsule containing Keyframe, Curve, Split, Trash, Undo, Redo
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF18181B))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Keyframe Add/Remove Diamond
            Icon(
                imageVector = Icons.Default.Diamond,
                contentDescription = "Keyframe",
                tint = if (hasKeyframeAtCurrentTime) Color(0xFF818CF8) else Color(0x66FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleKeyframe() }
            )

            // Speed Curve
            Icon(
                imageVector = Icons.Default.ShowChart,
                contentDescription = "Curves",
                tint = Color(0x66FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onOpenKeyframeCurves() }
            )

            // Divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0x1AFFFFFF))
            )

            // Split Scissors
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = "Split",
                tint = if (hasSelectedClip) Color.White else Color(0x33FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(enabled = hasSelectedClip) { onSplit() }
            )

            // Delete Trash
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = if (hasSelectedClip) Color(0xFFEF4444) else Color(0x33FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(enabled = hasSelectedClip) { onDelete() }
            )

            // Divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0x1AFFFFFF))
            )

            // Undo
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0x33FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(enabled = canUndo) { onUndo() }
            )

            // Redo
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0x33FFFFFF),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(enabled = canRedo) { onRedo() }
            )
        }
    }
}
