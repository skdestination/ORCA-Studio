package com.litecut.app.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

import coil.compose.AsyncImage

/**
 * Section B: Video Preview Canvas Area for ORCA Studio.
 * Renders the video/image preview canvas with rounded corners matching React exactly.
 */
@Composable
fun EditorPreviewCanvas(
    aspectRatioString: String,
    currentTime: Double,
    selectedClipName: String?,
    activeClipSrc: String? = null,
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Parse ratio float (Width / Height)
    val ratioValue = remember(aspectRatioString) {
        val parts = aspectRatioString.split(":")
        if (parts.size == 2) {
            val w = parts[0].toFloatOrNull() ?: 9f
            val h = parts[1].toFloatOrNull() ?: 16f
            if (h > 0) w / h else 9f / 16f
        } else {
            9f / 16f
        }
    }

    // Transform State for Active Clip On-Canvas Manipulation
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }

    // Snap Guides visibility states
    var showSnapX by remember { mutableStateOf(false) } // Vertical center guide
    var showSnapY by remember { mutableStateOf(false) } // Horizontal center guide

    val isClipSelected = selectedClipName != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Main Aspect Ratio Frame matching React
            Box(
                modifier = Modifier
                    .aspectRatio(ratioValue)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0C0E))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Outer Canvas Surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotate ->
                                scale = (scale * zoom).coerceIn(0.2f, 5f)
                                rotation += rotate

                                val newX = offsetX + pan.x
                                val newY = offsetY + pan.y

                                showSnapX = abs(newX) < 10f
                                offsetX = if (showSnapX) 0f else newX

                                showSnapY = abs(newY) < 10f
                                offsetY = if (showSnapY) 0f else newY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Aesthetic Media Frame Rendering
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = offsetX
                                translationY = offsetY
                                scaleX = scale
                                scaleY = scale
                                rotationZ = rotation
                            }
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF09090C)),
                        contentAlignment = Alignment.Center
                    ) {
                        // High quality cinematic portrait visual preview or real media source
                        if (!activeClipSrc.isNullOrBlank()) {
                            AsyncImage(
                                model = activeClipSrc,
                                contentDescription = "Preview Media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .clickable { onImportClick?.invoke() }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x1F6366F1))
                                        .border(1.dp, Color(0x336366F1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", color = Color(0xFF818CF8), fontSize = 24.sp, fontWeight = FontWeight.Light)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to Import Media",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add video, audio or photos",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // On-Screen Bounding Box & Transform Handles if selected
                        if (isClipSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.5.dp, Color(0xFF6366F1), RoundedCornerShape(24.dp))
                            ) {
                                // 4 Corner Scale Handles
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(8.dp, 8.dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset((-8).dp, 8.dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(8.dp, (-8).dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset((-8).dp, (-8).dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                            }
                        }
                    }

                    // Snap Alignment Guides (Center Crosshair)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        if (showSnapX) {
                            drawLine(
                                color = Color(0xFFFF007F),
                                start = Offset(canvasW / 2f, 0f),
                                end = Offset(canvasW / 2f, canvasH),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        if (showSnapY) {
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = Offset(0f, canvasH / 2f),
                                end = Offset(canvasW, canvasH / 2f),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                    }
                }
            }
        }
    }
}
