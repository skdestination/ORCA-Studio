package com.litecut.app.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Section E: Timeline Area matching React (LAYERS column, Half-Pill buttons, Playhead Red Line, Canvas) exactly.
 */
@Composable
fun EditorTimelineArea(
    engine: TimelineEngine,
    modifier: Modifier = Modifier
) {
    var layersState by remember { mutableStateOf(engine.getAllLayers()) }

    fun refreshLayers() {
        layersState = engine.getAllLayers()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C0E))
            .border(1.dp, Color(0xFF202025))
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT COLUMN: Sticky "LAYERS" Header + Half-Pill Layer Row Controls
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(Color.Transparent)
                    .padding(vertical = 0.dp)
            ) {
                // "LAYERS" Header Capsule Pill matching React
                Box(
                    modifier = Modifier
                        .height(15.dp)
                        .fillMaxWidth()
                        .padding(bottom = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(14.dp)
                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                            .background(Color.Black)
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "LAYERS",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Scrollable Layer Half-Pill Capsules
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(layersState) { index, layer ->
                        var showTrackOptions by remember { mutableStateOf(false) }
                        val layerNum = layersState.size - index

                        // Half-Pill Capsule Layer Row (attached to left edge, right rounded)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(vertical = 1.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(82.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp))
                                    .padding(start = 6.dp, end = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Track Number watermark
                                Text(
                                    text = "$layerNum",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF71717A),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 2.dp, start = 2.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.CenterEnd),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mute Speaker
                                        Icon(
                                            imageVector = if (layer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute",
                                            tint = if (layer.isMuted) Color(0xFFEF4444) else Color(0xFFA1A1AA),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    layer.isMuted = !layer.isMuted
                                                    refreshLayers()
                                                }
                                        )

                                        // Visibility Eye
                                        Icon(
                                            imageVector = if (layer.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Hide",
                                            tint = if (layer.isHidden) Color(0xFFF59E0B) else Color(0xFFA1A1AA),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    layer.isHidden = !layer.isHidden
                                                    refreshLayers()
                                                }
                                        )

                                        // More Options Dots
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { showTrackOptions = !showTrackOptions }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Compact Add Layer Half-Pill Button (+)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp))
                                    .clickable {
                                        val newLayerId = "layer_${System.currentTimeMillis()}"
                                        val nextOrder = (engine.getAllLayers().maxOfOrNull { it.order } ?: -1) + 1
                                        engine.addLayer(Layer(id = newLayerId, order = nextOrder, isMuted = false, isHidden = false))
                                        refreshLayers()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Layer",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT AREA: Timeline Grid Canvas with Stationary Playhead Line
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Native Timeline Container
                TimelineContainer(
                    modifier = Modifier.fillMaxSize()
                )

                // Stationary Red Playhead Line Overlay with Polygon Top Marker matching React
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center)
                ) {
                    // Polygon top indicator cap
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(10.dp)
                            .background(Color(0xFFFF2D55), RoundedCornerShape(2.dp))
                    )

                    // Thin 1px Crimson Red Playhead Line traversing all tracks
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFFF2D55))
                    )
                }
            }
        }
    }
}

