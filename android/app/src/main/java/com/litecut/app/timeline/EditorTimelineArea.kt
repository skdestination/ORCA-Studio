package com.litecut.app.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Section E: Timeline Area matching React Image 2 (LAYERS column, Playhead Red Line, Clips) exactly.
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
            .background(Color(0xFF09090B))
            .border(1.dp, Color(0xFF18181B))
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT COLUMN: "LAYERS" Header + Compact Layer Row Controls matching Image 2
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0D0D10))
                    .border(1.dp, Color(0x1AFFFFFF))
                    .padding(vertical = 6.dp, horizontal = 6.dp)
            ) {
                // "LAYERS" Header
                Text(
                    text = "LAYERS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFA1A1AA),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )

                // Scrollable Layer Capsules
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(layersState) { index, layer ->
                        var showTrackOptions by remember { mutableStateOf(false) }
                        val layerNum = layersState.size - index

                        // Compact Layer Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141418))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Layer Number
                                Text(
                                    text = "$layerNum",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA1A1AA)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Mute Speaker
                                    Icon(
                                        imageVector = if (layer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = if (layer.isMuted) Color(0xFFEF4444) else Color(0x80FFFFFF),
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
                                        tint = if (layer.isHidden) Color(0xFFF59E0B) else Color(0x80FFFFFF),
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
                                        tint = Color(0x80FFFFFF),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { showTrackOptions = !showTrackOptions }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Add Layer Button (+) matching React Image 2
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0x1AFFFFFF), CircleShape)
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

            // RIGHT AREA: Timeline Grid Canvas with Stationary Red Playhead Line
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Native Timeline Container
                TimelineContainer(
                    modifier = Modifier.fillMaxSize()
                )

                // Single Center Playhead Red Line Overlay with Triangle Indicator Top matching React Image 2
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center)
                ) {
                    // Top Red Triangle Arrow Indicator pointing down
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Playhead Marker",
                        tint = Color(0xFFFF2D55),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-4).dp)
                            .size(18.dp)
                    )

                    // Single 1px Thin Red Playhead Line traversing all tracks
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
