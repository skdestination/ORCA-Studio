package com.litecut.app.controls

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.ClipType

/**
 * Floating FlowBar Action Pill
 * Matches React's w-[218px] floating pill layout positioned floating on top of the timeline.
 */
@Composable
fun EditorBottomToolBar(
    onAddMediaClick: () -> Unit,
    onVoiceoverClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onAddTextClick: () -> Unit,
    onCropClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onReverseClick: () -> Unit,
    onStabilizeClick: () -> Unit,
    onCopyClick: () -> Unit,
    onExtractAudioClick: () -> Unit,
    onMoveClick: () -> Unit,
    onMotionClick: () -> Unit,
    onAnimationClick: () -> Unit,
    onMagicClick: () -> Unit,
    onBlendClick: () -> Unit,
    onMaskClick: () -> Unit,
    selectedClip: Clip? = null,
    activeMenu: String? = null,
    modifier: Modifier = Modifier
) {
    var isPlusMediaActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Translucent Pill Container (w-[218.dp], matching React)
        Column(
            modifier = Modifier
                .width(218.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF50D0D12))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flowbar Tool Strip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 1. Add Media (+) Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isPlusMediaActive || activeMenu == "plus-media") Color(0xFF4F46E5) else Color.Transparent)
                        .clickable {
                            isPlusMediaActive = !isPlusMediaActive
                            onAddMediaClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Media",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Thin Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0x26FFFFFF))
                )

                // Horizontal Scrollable Tools Strip
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voiceover / Mic
                    ToolBarIconButton(
                        icon = Icons.Default.Mic,
                        label = "Voiceover",
                        isSelected = activeMenu == "voiceover",
                        onClick = onVoiceoverClick
                    )

                    // Volume
                    ToolBarIconButton(
                        icon = Icons.Default.VolumeUp,
                        label = "Volume",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "volume",
                        onClick = onVolumeClick
                    )

                    // Text
                    ToolBarIconButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isSelected = activeMenu == "text",
                        onClick = onAddTextClick
                    )

                    // Crop
                    ToolBarIconButton(
                        icon = Icons.Default.Crop,
                        label = "Crop",
                        isEnabled = selectedClip != null && (selectedClip.type == ClipType.VIDEO || selectedClip.type == ClipType.IMAGE),
                        isSelected = activeMenu == "crop",
                        onClick = onCropClick
                    )

                    // Adjust
                    ToolBarIconButton(
                        icon = Icons.Default.Tune,
                        label = "Adjust",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "adjust",
                        onClick = onAdjustClick
                    )

                    // Speed
                    ToolBarIconButton(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                        isSelected = activeMenu == "speed",
                        onClick = onSpeedClick
                    )

                    // Reverse
                    ToolBarIconButton(
                        icon = Icons.Default.RotateLeft,
                        label = "Reverse",
                        isEnabled = selectedClip != null && (selectedClip.type == ClipType.VIDEO || selectedClip.type == ClipType.AUDIO),
                        onClick = onReverseClick
                    )

                    // Stabilize
                    ToolBarIconButton(
                        icon = Icons.Default.GraphicEq,
                        label = "Stabilize",
                        isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                        isSelected = activeMenu == "stabilize",
                        onClick = onStabilizeClick
                    )

                    // Copy / Multi-select
                    ToolBarIconButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        isEnabled = selectedClip != null,
                        onClick = onCopyClick
                    )

                    // Extract Audio
                    ToolBarIconButton(
                        icon = Icons.Default.MusicNote,
                        label = "Extract Audio",
                        isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                        onClick = onExtractAudioClick
                    )

                    // Move / Transform
                    ToolBarIconButton(
                        icon = Icons.Default.OpenWith,
                        label = "Move",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "move",
                        onClick = onMoveClick
                    )

                    // Motion Bar ("M")
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (activeMenu == "motion") Color(0xFF4F46E5) else Color.Transparent)
                            .clickable(enabled = selectedClip != null) { onMotionClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = if (selectedClip != null) Color.White else Color(0x33FFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Animation (Sparkles)
                    ToolBarIconButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "Animation",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "animation",
                        onClick = onAnimationClick
                    )

                    // Magic Slow-Mo (Wand)
                    ToolBarIconButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Magic",
                        isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                        onClick = onMagicClick
                    )

                    // Blend / Opacity
                    ToolBarIconButton(
                        icon = Icons.Default.Layers,
                        label = "Blend",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "blend",
                        onClick = onBlendClick
                    )

                    // Mask
                    ToolBarIconButton(
                        icon = Icons.Default.FilterFrames,
                        label = "Mask",
                        isEnabled = selectedClip != null,
                        isSelected = activeMenu == "mask",
                        onClick = onMaskClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolBarIconButton(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isSelected -> Color.White
        isEnabled -> Color(0xFFE4E4E7)
        else -> Color(0x33FFFFFF)
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF27272A) else Color.Transparent)
            .clickable(enabled = isEnabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
    }
}
