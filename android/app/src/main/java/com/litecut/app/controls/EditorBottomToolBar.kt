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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Floating Frosted Pill Action Container
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF18181B))
                .border(1.dp, Color(0x2AFFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Add Media Button (Accent Gradient + Icon)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                        )
                    )
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

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color(0x33FFFFFF))
            )

            // 2. Mic / Voiceover Button
            ToolBarIconButton(
                icon = Icons.Default.Mic,
                label = "Mic",
                isSelected = activeMenu == "voiceover",
                onClick = onVoiceoverClick
            )

            // 3. Audio / Volume Button
            ToolBarIconButton(
                icon = Icons.Default.VolumeUp,
                label = "Audio",
                isSelected = activeMenu == "audio",
                onClick = onAudioClick
            )

            // 4. Text Button
            ToolBarIconButton(
                icon = Icons.Default.TextFields,
                label = "Text",
                isSelected = activeMenu == "text",
                onClick = onAddTextClick
            )

            // 5. Crop Button
            ToolBarIconButton(
                icon = Icons.Default.Crop,
                label = "Crop",
                isEnabled = selectedClip != null && (selectedClip.type == ClipType.VIDEO || selectedClip.type == ClipType.IMAGE),
                isSelected = activeMenu == "crop",
                onClick = onCropClick
            )

            // 6. Adjustments Button
            ToolBarIconButton(
                icon = Icons.Default.Tune,
                label = "Adjust",
                isEnabled = selectedClip != null,
                isSelected = activeMenu == "adjust",
                onClick = onAdjustClick
            )

            // 7. Speed Curve Button
            ToolBarIconButton(
                icon = Icons.Default.Speed,
                label = "Speed",
                isEnabled = selectedClip != null && selectedClip.type == ClipType.VIDEO,
                isSelected = activeMenu == "speed",
                onClick = onSpeedClick
            )

            // 8. Filters & Effects Button
            ToolBarIconButton(
                icon = Icons.Default.AutoAwesome,
                label = "Effects",
                isSelected = activeMenu == "effects",
                onClick = onEffectsClick
            )
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
    val bgColor = when {
        isSelected -> Color(0xFF3F3F46)
        isEnabled -> Color.Transparent
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> Color.White
        isEnabled -> Color(0xFFE4E4E7)
        else -> Color(0x40FFFFFF)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor)
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
