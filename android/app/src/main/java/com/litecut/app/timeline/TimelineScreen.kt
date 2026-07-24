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

    var currentProjectRatio by remember { mutableStateOf("9:16") }
    var exportResolution by remember { mutableStateOf("1080p") }
    var exportFps by remember { mutableStateOf("30") }
    var isFullscreen by remember { mutableStateOf(false) }
    var isMediaDrawerOpen by remember { mutableStateOf(false) }
    var isMediaPermissionGranted by remember { mutableStateOf(true) }

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

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(TimelineTheme.backgroundColor),
            surface = Color(TimelineTheme.headerBackgroundColor),
            primary = Color(0xFFFF2D55)
        )
    ) {
        Scaffold(
            topBar = {
                EditorHeaderBar(
                    currentProjectRatio = currentProjectRatio,
                    onRatioChanged = { newRatio ->
                        currentProjectRatio = newRatio
                    },
                    exportResolution = exportResolution,
                    onResolutionChanged = { newRes ->
                        exportResolution = newRes
                    },
                    exportFps = exportFps,
                    onFpsChanged = { newFps ->
                        exportFps = newFps
                    },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                    },
                    onBackClick = onBackClick,
                    onExportClick = {
                        // Launch native export pipeline
                    }
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
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF09090A))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val activeClipName = if (selectedClipIds.isNotEmpty()) {
                    engine.getClip(selectedClipIds.first())?.name
                } else null

                EditorPreviewCanvas(
                    aspectRatioString = currentProjectRatio,
                    currentTime = currentTime,
                    selectedClipName = activeClipName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. MID-TRANSPORT CONTROLS BAR (Section D)
            val activeClipId = selectedClipIds.firstOrNull()
            EditorTransportBar(
                isPlaying = isPlaying,
                onTogglePlay = { isPlaying = !isPlaying },
                currentTime = currentTime,
                totalDuration = 30.0,
                hasKeyframeAtCurrentTime = false,
                onToggleKeyframe = {
                    // Drop / remove keyframe logic
                },
                onOpenKeyframeCurves = {
                    // Open keyframe curve interpolation menu
                },
                hasSelectedClip = activeClipId != null,
                onSplit = {
                    activeClipId?.let { clipId ->
                        val command = SplitCommand(clipId, engine.currentTime, java.util.UUID.randomUUID().toString())
                        engine.executeCommand(command)
                    }
                },
                onDelete = {
                    if (selectedClipIds.isNotEmpty()) {
                        val command = DeleteCommand(selectedClipIds)
                        engine.executeCommand(command)
                        engine.selectedClipIds.clear()
                    }
                },
                canUndo = canUndo,
                onUndo = { engine.undo() },
                canRedo = canRedo,
                onRedo = { engine.redo() }
            )

            // 3. TIMELINE TRACKS AREA (Section E)
            EditorTimelineArea(
                engine = engine,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
            )

            // Media Library Drawer Sheet Overlay
            EditorMediaDrawer(
                isVisible = isMediaDrawerOpen,
                onCloseDrawer = { isMediaDrawerOpen = false },
                isPermissionGranted = isMediaPermissionGranted,
                onRequestPermission = { isMediaPermissionGranted = true },
                onMediaSelected = { mediaItem ->
                    val track = engine.getAllLayers().firstOrNull()
                    if (track != null) {
                        val newClip = Clip(
                            id = "clip_${System.currentTimeMillis()}",
                            layerId = track.id,
                            type = when (mediaItem.type) {
                                "audio" -> ClipType.AUDIO
                                "image" -> ClipType.IMAGE
                                else -> ClipType.VIDEO
                            },
                            src = mediaItem.path.ifEmpty { "file:///path/${mediaItem.name}" },
                            name = mediaItem.name,
                            leftSeconds = engine.currentTime,
                            durationSeconds = if (mediaItem.type == "image") 5.0 else 10.0,
                            trimStartSeconds = 0.0
                        )
                        engine.executeCommand(CreateClipCommand(newClip))
                    }
                    isMediaDrawerOpen = false
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
}
