package com.litecut.app.controls

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.ClipType

@Composable
fun EditorBottomToolBar(
    onAddMediaClick: () -> Unit,
    onVoiceoverClick: () -> Unit,
    onAudioClick: () -> Unit,
    onAddTextClick: () -> Unit,
    onCropClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEffectsClick: () -> Unit,
    selectedClip: Clip? = null,
    activeMenu: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Translucent Pill Action Container matching React Image 2
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE618181B))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Add Media Button (Simple + icon, no giant purple circle, matching Image 2)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onAddMediaClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Media",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Thin Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color(0x33FFFFFF))
            )

            // 2. Mic / Voiceover
            ToolBarIcon(
                icon = Icons.Default.Mic,
                label = "Mic",
                isSelected = activeMenu == "voiceover",
                onClick = onVoiceoverClick
            )

            // 3. Speaker / Volume
            ToolBarIcon(
                icon = Icons.Default.VolumeUp,
                label = "Audio",
                isSelected = activeMenu == "audio",
                onClick = onAudioClick
            )

            // 4. Text
            ToolBarIcon(
                icon = Icons.Default.TextFields,
                label = "Text",
                isSelected = activeMenu == "text",
                onClick = onAddTextClick
            )

            // 5. Crop
            ToolBarIcon(
                icon = Icons.Default.Crop,
                label = "Crop",
                isEnabled = selectedClip != null && (selectedClip.type == ClipType.VIDEO || selectedClip.type == ClipType.IMAGE),
                isSelected = activeMenu == "crop",
                onClick = onCropClick
            )

            // 6. Adjustments
            ToolBarIcon(
                icon = Icons.Default.Tune,
                label = "Adjust",
                isEnabled = selectedClip != null,
                isSelected = activeMenu == "adjust",
                onClick = onAdjustClick
            )

            // 7. Speed Clock
            ToolBarIcon(
                icon = Icons.Default.Speed,
                label = "Speed",
                isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                isSelected = activeMenu == "speed",
                onClick = onSpeedClick
            )

            // 8. Sparkles / Effects
            ToolBarIcon(
                icon = Icons.Default.AutoAwesome,
                label = "Effects",
                isSelected = activeMenu == "effects",
                onClick = onEffectsClick
            )
        }
    }
}

@Composable
private fun ToolBarIcon(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isSelected -> Color.White
        isEnabled -> Color(0xFFD4D4D8)
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
            modifier = Modifier.size(18.dp)
        )
    }
}
