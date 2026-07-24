package com.litecut.app.timeline

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

data class AspectRatioItem(
    val ratio: String,
    val label: String,
    val description: String
)

val DEFAULT_ASPECT_RATIOS = listOf(
    AspectRatioItem("9:16", "Reels, TikTok", "Vertical video"),
    AspectRatioItem("16:9", "YouTube", "Horizontal video"),
    AspectRatioItem("1:1", "Instagram", "Square post")
)

@Composable
fun EditorHeaderBar(
    currentProjectRatio: String,
    onRatioChanged: (String) -> Unit,
    exportResolution: String,
    onResolutionChanged: (String) -> Unit,
    exportFps: String,
    onFpsChanged: (String) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBackClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRatioExpanded by remember { mutableStateOf(false) }
    var isExportExpanded by remember { mutableStateOf(false) }
    var customRatioW by remember { mutableStateOf("9") }
    var customRatioH by remember { mutableStateOf("16") }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isRatioExpanded) 180f else 0f,
        label = "chevron_rotate"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF08080A))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT GROUP: Back button + Aspect Ratio selector capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF27272A))
                        .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = Color(0xFFA1A1AA),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Aspect Ratio Capsule
                Box(modifier = Modifier.wrapContentSize()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isRatioExpanded) Color.White else Color(0xFF27272A)
                            )
                            .border(
                                1.dp,
                                if (isRatioExpanded) Color.Transparent else Color(0x0DFFFFFF),
                                RoundedCornerShape(24.dp)
                            )
                            .clickable { isRatioExpanded = !isRatioExpanded }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Ratio Icon Preview Box
                        RatioShapeIcon(
                            ratio = currentProjectRatio,
                            isExpanded = isRatioExpanded
                        )

                        Text(
                            text = "Aspect: $currentProjectRatio",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isRatioExpanded) Color.Black else Color(0xFFF4F4F5),
                            letterSpacing = 0.5.sp
                        )

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand Ratio",
                            tint = if (isRatioExpanded) Color.Black else Color(0xFFA1A1AA),
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    // Aspect Ratio Dropdown Popup
                    if (isRatioExpanded) {
                        Popup(
                            onDismissRequest = { isRatioExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 40.dp)
                                    .width(240.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFA09090B))
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "CHANGE ASPECT RATIO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA),
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )

                                    DEFAULT_ASPECT_RATIOS.forEach { item ->
                                        val isSelected = currentProjectRatio == item.ratio
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) Color.White else Color.Transparent
                                                )
                                                .clickable {
                                                    onRatioChanged(item.ratio)
                                                    isRatioExpanded = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                RatioShapeIcon(
                                                    ratio = item.ratio,
                                                    isExpanded = isSelected
                                                )
                                                Column {
                                                    Text(
                                                        text = item.ratio,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color.Black else Color(0xFFD4D4D8)
                                                    )
                                                    Text(
                                                        text = item.label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) Color(0xFF3F3F46) else Color(0xFF71717A)
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Divider(
                                        color = Color(0x1AFFFFFF),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    Text(
                                        text = "CUSTOM RATIO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA),
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // W input
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF18181B))
                                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            BasicTextField(
                                                value = customRatioW,
                                                onValueChange = { customRatioW = it.filter { char -> char.isDigit() } },
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                cursorBrush = SolidColor(Color.White),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Text(":", color = Color(0xFF71717A), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                        // H input
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF18181B))
                                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            BasicTextField(
                                                value = customRatioH,
                                                onValueChange = { customRatioH = it.filter { char -> char.isDigit() } },
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                cursorBrush = SolidColor(Color.White),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Apply Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .clickable {
                                                    val w = customRatioW.toIntOrNull() ?: 0
                                                    val h = customRatioH.toIntOrNull() ?: 0
                                                    if (w > 0 && h > 0) {
                                                        onRatioChanged("$w:$h")
                                                        isRatioExpanded = false
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Apply",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT GROUP: Fullscreen Toggle + EXPORT Button with dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fullscreen Toggle Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF27272A))
                        .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                        .clickable { onToggleFullscreen() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFullscreen) "⤢" else "⤡",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Export Button Capsule & Popup
                Box {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { isExportExpanded = !isExportExpanded }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "EXPORT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Export Menu Popup
                    if (isExportExpanded) {
                        Popup(
                            onDismissRequest = { isExportExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 36.dp)
                                    .width(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF27272A))
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Resolution selector row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Resolution",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0x80FFFFFF)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("1080p", "2K", "4K").forEach { res ->
                                                val isSelected = exportResolution == res
                                                Text(
                                                    text = res,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color(0xFFFFD700) else Color.White,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) Color(0x33FFD700) else Color.Transparent)
                                                        .clickable { onResolutionChanged(res) }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Frame Rate selector row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Frame Rate",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0x80FFFFFF)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("24", "30", "60").forEach { fps ->
                                                val isSelected = exportFps == fps
                                                Text(
                                                    text = "${fps}fps",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color(0xFFFFD700) else Color.White,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) Color(0x33FFD700) else Color.Transparent)
                                                        .clickable { onFpsChanged(fps) }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Direct Launch Export Button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFF2D55))
                                            .clickable {
                                                isExportExpanded = false
                                                onExportClick()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Start Render ($exportResolution @ ${exportFps}fps)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatioShapeIcon(
    ratio: String,
    isExpanded: Boolean
) {
    val borderColor = if (isExpanded) Color.Black else Color.White.copy(alpha = 0.8f)
    when (ratio) {
        "9:16" -> {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 12.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(1.dp))
            )
        }
        "16:9" -> {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 6.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(1.dp))
            )
        }
        "1:1" -> {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 10.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(1.dp))
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 8.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(1.dp))
            )
        }
    }
}
