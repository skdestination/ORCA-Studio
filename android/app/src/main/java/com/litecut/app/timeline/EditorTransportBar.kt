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
 * Section D: Mid-Transport Controls Bar & Quick Actions.
 * Contains Play/Pause, Monospace Timecode Counter, Keyframe Add/Remove & Curve buttons,
 * Quick Edits (Split, Delete), and History (Undo, Redo).
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
    val currentTimeString = remember(currentTime) {
        val mins = (currentTime / 60).toInt()
        val secs = (currentTime % 60).toInt()
        val millis = ((currentTime % 1) * 100).toInt()
        String.format("%02d:%02d.%02d", mins, secs, millis)
    }

    val totalDurationString = remember(totalDuration) {
        val mins = (totalDuration / 60).toInt()
        val secs = (totalDuration % 60).toInt()
        val millis = ((totalDuration % 1) * 100).toInt()
        String.format("%02d:%02d.%02d", mins, secs, millis)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF0C0C0E))
            .border(width = 1.dp, color = Color(0x1AFFFFFF))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT GROUP: Play/Pause circle + Monospace Timecode Counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Play/Pause Circle Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFFF2D55) else Color(0xFF6366F1))
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Timecode Counter (Current / Total)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentTimeString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = " / ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0x66FFFFFF)
                )
                Text(
                    text = totalDurationString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFA1A1AA)
                )
            }
        }

        // CENTER GROUP: Keyframe Add/Remove Diamond & Curve Interpolation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Keyframe Add/Remove Diamond
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasKeyframeAtCurrentTime) Color(0x33818CF8) else Color(0xFF18181B)
                    )
                    .border(
                        1.dp,
                        if (hasKeyframeAtCurrentTime) Color(0xFF818CF8) else Color(0x1AFFFFFF),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleKeyframe() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = "Keyframe",
                    tint = if (hasKeyframeAtCurrentTime) Color(0xFF818CF8) else Color(0xFFA1A1AA),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Keyframe Curve Interpolation Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF18181B))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .clickable { onOpenKeyframeCurves() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = "Keyframe Curves",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // RIGHT GROUP: Quick Edits (Split, Delete) & History Controls (Undo, Redo)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Split Button (Scissors)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasSelectedClip) Color(0xFF27272A) else Color(0xFF141416))
                    .clickable(enabled = hasSelectedClip) { onSplit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Split",
                    tint = if (hasSelectedClip) Color.White else Color(0x33FFFFFF),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Delete Button (Trash)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasSelectedClip) Color(0x33EF4444) else Color(0xFF141416))
                    .border(
                        1.dp,
                        if (hasSelectedClip) Color(0xFFEF4444) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = hasSelectedClip) { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (hasSelectedClip) Color(0xFFEF4444) else Color(0x33FFFFFF),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Vertical divider spacer
            Spacer(
                modifier = Modifier
                    .height(18.dp)
                    .width(1.dp)
                    .background(Color(0x1AFFFFFF))
            )

            // Undo Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (canUndo) Color(0xFF27272A) else Color.Transparent)
                    .clickable(enabled = canUndo) { onUndo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color(0x33FFFFFF),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Redo Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (canRedo) Color(0xFF27272A) else Color.Transparent)
                    .clickable(enabled = canRedo) { onRedo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color(0x33FFFFFF),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
