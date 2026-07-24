package com.litecut.app.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Section B: Video Preview Canvas Area for ORCA Studio.
 * Handles dynamic aspect ratios, real-time transform matrices (scale, translation, rotation),
 * on-screen selection bounding box handles, and snap alignment guides.
 */
@Composable
fun EditorPreviewCanvas(
    aspectRatioString: String,
    currentTime: Double,
    selectedClipName: String?,
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
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }

    // Snap Guides visibility states
    var showSnapX by remember { mutableStateOf(false) } // Vertical center guide (Magenta)
    var showSnapY by remember { mutableStateOf(false) } // Horizontal center guide (Cyan)

    val isClipSelected = selectedClipName != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080A))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            // Calculate bounding dimensions to keep aspect ratio inside container
            Box(
                modifier = Modifier
                    .aspectRatio(ratioValue)
                    .fillMaxHeight(if (ratioValue < 1f) 0.95f else 0.8f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0C0E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
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

                                // Snap alignment logic (threshold = 10px)
                                showSnapX = abs(newX) < 10f
                                offsetX = if (showSnapX) 0f else newX

                                showSnapY = abs(newY) < 10f
                                offsetY = if (showSnapY) 0f else newY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Clip Rendering Container with Realtime CSS/Matrix Transforms
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = offsetX
                                translationY = offsetY
                                scaleX = scale
                                scaleY = scale
                                rotationZ = rotation
                            }
                            .fillMaxSize(0.85f)
                            .background(
                                if (isClipSelected) Color(0xFF1E1E24) else Color(0xFF141416),
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = selectedClipName ?: "No Clip Selected",
                                color = if (isClipSelected) Color.White else Color(0x66FFFFFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(
                                    "%02d:%02d.%02d",
                                    (currentTime / 60).toInt(),
                                    (currentTime % 60).toInt(),
                                    ((currentTime % 1) * 100).toInt()
                                ),
                                color = Color(0xFFFF2D55),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // On-Screen Bounding Box & Transform Handles
                        if (isClipSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.5.dp, Color(0xFF6366F1), RoundedCornerShape(6.dp))
                            ) {
                                // 4 Corner Scale Handles
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset((-4).dp, (-4).dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(4.dp, (-4).dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset((-4).dp, 4.dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(4.dp, 4.dp)
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                )

                                // Top Rotation Stem Handle
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-20).dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color.White, CircleShape)
                                            .border(1.5.dp, Color(0xFF6366F1), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.5.dp)
                                            .height(10.dp)
                                            .background(Color(0xFF6366F1))
                                    )
                                }
                            }
                        }
                    }

                    // Snap Alignment Guides (Center Crosshair)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Magenta Vertical Snap Guide
                        if (showSnapX) {
                            drawLine(
                                color = Color(0xFFFF007F),
                                start = Offset(canvasW / 2f, 0f),
                                end = Offset(canvasW / 2f, canvasH),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        // Cyan Horizontal Snap Guide
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
