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
import androidx.compose.material.icons.automirrored.filled.*
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
 * Section E: Timeline Tracks Area.
 * Combines Sticky Left Track/Layer Sidebar Controls (Half-pill capsules, Mute, Hide, Track Options)
 * with Stationary Center Playhead Red Line and native canvas multi-track clips grid.
 */
@Composable
fun EditorTimelineArea(
    engine: TimelineEngine,
    modifier: Modifier = Modifier
) {
    var layersState by remember { mutableStateOf(engine.getAllLayers()) }

    // Re-sync layer list whenever commands execute
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
            // LEFT TRACK/LAYER SIDEBAR PANEL (Sticky Half-Pill Control Capsules)
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF121216))
                    .border(1.dp, Color(0x1AFFFFFF))
                    .padding(vertical = 8.dp, horizontal = 6.dp)
            ) {
                // Header Label for Tracks
                Text(
                    text = "TRACKS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFA1A1AA),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )

                // Scrollable Layers Control Capsules List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(layersState) { index, layer ->
                        var showTrackOptions by remember { mutableStateOf(false) }

                        // Half-Pill Layer Control Capsule
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (layer.isLocked) Color(0xFF1C1C22) else Color(0xFF1E1E24))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Top Row: Track Index Badge + Option Dots
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Track Index Badge
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6366F1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    // Track Options Menu Trigger (MoreVertical)
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Track Options",
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { showTrackOptions = !showTrackOptions }
                                        )

                                        // Track Options Dropdown Popup
                                        if (showTrackOptions) {
                                            Popup(
                                                onDismissRequest = { showTrackOptions = false },
                                                properties = PopupProperties(focusable = true)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 16.dp)
                                                        .width(130.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF27272A))
                                                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                                        .padding(6.dp)
                                                ) {
                                                    Column {
                                                        // Duplicate Track
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    showTrackOptions = false
                                                                    // Duplicate layer logic
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White, modifier = Modifier.size(12.dp))
                                                            Text("Duplicate", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                        }

                                                        // Lock Track
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    layer.isLocked = !layer.isLocked
                                                                    showTrackOptions = false
                                                                    refreshLayers()
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                if (layer.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                                                contentDescription = "Lock",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                if (layer.isLocked) "Unlock" else "Lock Track",
                                                                fontSize = 10.sp,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }

                                                        // Delete Track
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    engine.removeLayer(layer.id)
                                                                    showTrackOptions = false
                                                                    refreshLayers()
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                                            Text("Delete", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom Row: Quick Toggles (Mute Volume, Hide Eye)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Mute Toggle (Volume2 / VolumeOff)
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (layer.isMuted) Color(0x33EF4444) else Color(0x1AFFFFFF))
                                            .clickable {
                                                layer.isMuted = !layer.isMuted
                                                refreshLayers()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute",
                                            tint = if (layer.isMuted) Color(0xFFEF4444) else Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    // Hide Toggle (Eye / VisibilityOff)
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (layer.isHidden) Color(0x33F59E0B) else Color(0x1AFFFFFF))
                                            .clickable {
                                                layer.isHidden = !layer.isHidden
                                                refreshLayers()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Hide",
                                            tint = if (layer.isHidden) Color(0xFFF59E0B) else Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ADD NEW TRACK BUTTON (+ Icon Half-Pill Button)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E24))
                        .border(1.dp, Color(0x336366F1), RoundedCornerShape(16.dp))
                        .clickable {
                            val newLayerId = "layer_${System.currentTimeMillis()}"
                            val nextOrder = (engine.getAllLayers().maxOfOrNull { it.order } ?: -1) + 1
                            engine.addLayer(Layer(id = newLayerId, order = nextOrder, isMuted = false, isHidden = false))
                            refreshLayers()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Track",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Track",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8)
                        )
                    }
                }
            }

            // RIGHT AREA: NATIVE CANVAS MULTI-TRACK CLIPS GRID & STATIONARY PLAYHEAD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Native Canvas Timeline Container
                TimelineContainer(
                    modifier = Modifier.fillMaxSize()
                )

                // Stationary Center Playhead Line Overlay
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center)
                ) {
                    // Top Red Downward Triangle Cap
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-2).dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFFF2D55))
                    )

                    // 1px Thin Solid Red Line traversing all tracks
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFFF2D55))
                    )
                }
            }
        }
    }
}
