package com.litecut.app.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val engine = remember { TimelineEngine.getInstance() }
    var currentTime by remember { mutableStateOf(engine.currentTime) }
    var selectedClipIds by remember { mutableStateOf(engine.selectedClipIds.toList()) }
    var canUndo by remember { mutableStateOf(engine.canUndo()) }
    var canRedo by remember { mutableStateOf(engine.canRedo()) }
    var isPlaying by remember { mutableStateOf(false) }

    // Periodically poll playhead state for UI display synchronization
    LaunchedEffect(isPlaying) {
        while (true) {
            currentTime = engine.currentTime
            selectedClipIds = engine.selectedClipIds.toList()
            canUndo = engine.canUndo()
            canRedo = engine.canRedo()
            kotlinx.coroutines.delay(100)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ORCA Studio Pro",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Undo button
                    IconButton(
                        onClick = {
                            engine.undo()
                            canUndo = engine.canUndo()
                            canRedo = engine.canRedo()
                        },
                        enabled = canUndo
                    ) {
                        Text(
                            "↶",
                            color = if (canUndo) Color.White else Color.Gray,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Redo button
                    IconButton(
                        onClick = {
                            engine.redo()
                            canUndo = engine.canUndo()
                            canRedo = engine.canRedo()
                        },
                        enabled = canRedo
                    ) {
                        Text(
                            "↷",
                            color = if (canRedo) Color.White else Color.Gray,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Glossy Export Button
                    Button(
                        onClick = { /* Launch export profile pipeline */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("EXPORT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(TimelineTheme.headerBackgroundColor)
                )
            )
        },
        containerColor = Color(TimelineTheme.backgroundColor)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. PREVIEW CONTAINER (Video Monitor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF09090A))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // High-fidelity Mock Video Frame Layer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "PREVIEW MONITOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0x66FFFFFF),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Aspect ratio box mimicking player video stream
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141416))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Active Clip: " + if (selectedClipIds.isNotEmpty()) {
                                    val clip = engine.getClip(selectedClipIds.first())
                                    clip?.name ?: "Selected Asset"
                                } else "No selection",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%02d:%02d.%02d", (currentTime / 60).toInt(), (currentTime % 60).toInt(), ((currentTime % 1) * 100).toInt()),
                                color = Color(0xFFFF2D55),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated Playback controller
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = { engine.currentTime = 0.0 }) {
                            Text("⏮", color = Color.White, fontSize = 18.sp)
                        }
                        IconButton(onClick = {
                            isPlaying = !isPlaying
                            if (isPlaying) {
                                OrcaEventBus.getInstance().publish(OrcaEvent.PlaybackStarted(engine.currentTime))
                            } else {
                                OrcaEventBus.getInstance().publish(OrcaEvent.PlaybackPaused(engine.currentTime))
                            }
                        }) {
                            Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 24.sp)
                        }
                        IconButton(onClick = {
                            val duration = engine.getTotalDurationSeconds()
                            engine.currentTime = duration
                        }) {
                            Text("⏭", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }
            }

            // Divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(TimelineTheme.trackSeparatorColor))
            )

            // 2. TIMELINE CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
            ) {
                TimelineContainer(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(TimelineTheme.trackSeparatorColor))
            )

            // 3. BOTTOM TOOL AREA
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                color = Color(TimelineTheme.headerBackgroundColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeId = selectedClipIds.firstOrNull()

                    // Split Command Button
                    Button(
                        onClick = {
                            activeId?.let { clipId ->
                                val command = SplitCommand(clipId, engine.currentTime, java.util.UUID.randomUUID().toString())
                                engine.executeCommand(command)
                            }
                        },
                        enabled = activeId != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E2E38),
                            disabledContainerColor = Color(0xFF1E1E22)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "✂ SPLIT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeId != null) Color.White else Color.DarkGray
                        )
                    }

                    // Delete Command Button
                    Button(
                        onClick = {
                            if (selectedClipIds.isNotEmpty()) {
                                val command = DeleteCommand(selectedClipIds)
                                engine.executeCommand(command)
                                engine.selectedClipIds.clear()
                            }
                        },
                        enabled = selectedClipIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                            disabledContainerColor = Color(0xFF1E1E22)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(
                            1.dp,
                            if (selectedClipIds.isNotEmpty()) Color(0xFFEF4444) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text(
                            text = "🗑 DELETE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedClipIds.isNotEmpty()) Color(0xFFEF4444) else Color.DarkGray
                        )
                    }

                    // Add Clip Placeholder Button
                    Button(
                        onClick = {
                            // Insert a new mock video clip at the current playhead
                            val track = engine.getAllLayers().firstOrNull()
                            if (track != null) {
                                val newClip = Clip(
                                    id = "clip_added_${System.currentTimeMillis()}",
                                    layerId = track.id,
                                    type = ClipType.VIDEO,
                                    src = "file:///path/added",
                                    name = "Added Shot",
                                    leftSeconds = engine.currentTime,
                                    durationSeconds = 8.0,
                                    trimStartSeconds = 0.0
                                )
                                engine.executeCommand(CreateClipCommand(newClip))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E22)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ ADD CLIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
