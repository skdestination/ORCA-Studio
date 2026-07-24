package com.litecut.app.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.litecut.app.timeline.ProjectEngine
import com.litecut.app.timeline.ProjectDocument
import com.litecut.app.timeline.thumbnail.ThumbnailEngine
import java.io.File
import java.util.Calendar

// Data model representing a project in the Home Screen
data class HomeProjectItem(
    val id: String,
    val name: String,
    val ratio: String = "9:16",
    val duration: String = "01:29",
    val type: String = "video", // video, image, audio, text, empty
    val thumbnailPath: String? = null,
    val document: ProjectDocument? = null
)

fun ProjectDocument.toHomeProjectItem(): HomeProjectItem {
    val ratioStr = when {
        metadata.width == 1080 && metadata.height == 1920 -> "9:16"
        metadata.width == 1920 && metadata.height == 1080 -> "16:9"
        metadata.width == 1080 && metadata.height == 1080 -> "1:1"
        metadata.width > 0 && metadata.height > 0 -> "${metadata.width}:${metadata.height}"
        else -> "9:16"
    }
    val durSecs = metadata.durationMs / 1000
    val durMin = durSecs / 60
    val durSecRem = durSecs % 60
    val durStr = String.format("%02d:%02d", durMin, durSecRem)

    return HomeProjectItem(
        id = metadata.id,
        name = metadata.name,
        ratio = ratioStr,
        duration = durStr,
        type = "video",
        thumbnailPath = metadata.thumbnailPath.ifEmpty { null },
        document = this
    )
}

enum class ProjectSortBy(val label: String) {
    RECENT("Recent Order"),
    NAME("Alphabetical"),
    DURATION("Duration")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenProject: (HomeProjectItem) -> Unit = {},
    onCreateProject: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val projectEngine = remember { ProjectEngine.getInstance(context) }
    val thumbnailEngine = remember { ThumbnailEngine.getInstance(context) }

    // Real persistent projects loaded from ProjectEngine
    var projectDocs by remember { mutableStateOf(projectEngine.listProjects()) }
    val projects = remember(projectDocs) {
        projectDocs.map { it.toHomeProjectItem() }
    }

    var sortBy by remember { mutableStateOf(ProjectSortBy.RECENT) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var activeCardId by remember { mutableStateOf<String?>(projects.firstOrNull()?.id) }
    var showStatsModal by remember { mutableStateOf<HomeProjectItem?>(null) }
    var projectToDelete by remember { mutableStateOf<HomeProjectItem?>(null) }
    var isCreatingProject by remember { mutableStateOf(false) }
    var focusedRatio by remember { mutableStateOf("9:16") }
    var customRatioW by remember { mutableStateOf("1080") }
    var customRatioH by remember { mutableStateOf("1920") }

    // Sorted Projects list
    val sortedProjects = remember(projects, sortBy) {
        when (sortBy) {
            ProjectSortBy.NAME -> projects.sortedBy { it.name }
            ProjectSortBy.DURATION -> projects.sortedBy { it.duration }
            ProjectSortBy.RECENT -> projects
        }
    }

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when {
            currentHour < 12 -> "GOOD MORNING,"
            currentHour < 18 -> "GOOD AFTERNOON,"
            else -> "GOOD EVENING,"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
    ) {
        // Background Lighting Glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top Center Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width / 2f, -100f),
                    radius = 800f
                ),
                radius = 800f,
                center = Offset(size.width / 2f, -100f)
            )
            // Bottom Right Orange Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF97316).copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width + 100f, size.height + 100f),
                    radius = 700f
                ),
                radius = 700f,
                center = Offset(size.width + 100f, size.height + 100f)
            )
            // Top Left Purple Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFA855F7).copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(-100f, size.height * 0.3f),
                    radius = 600f
                ),
                radius = 600f,
                center = Offset(-100f, size.height * 0.3f)
            )
        }

        // Main Column Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Profile
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18181B))
                            .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(1.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFFF97316)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "R",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = greeting,
                            color = Color(0xFF71717A),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Ritwik",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }

                // Notification Bell
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF18181B).copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color(0xFFD4D4D8),
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF97316))
                    )
                }
            }

            // Edit Dashboard Button Below Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF18181B).copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Dashboard",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Center Title Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "ORCA",
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CREATIVE STUDIO",
                        color = Color(0xFFA1A1AA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF97316))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Engine Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF6366F1).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF818CF8).copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "React Sandbox Preview",
                        color = Color(0xFFA5B4FC),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Projects Section Header & Sort Dropdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Projects",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                // Sort Dropdown Button
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xFF18181B).copy(alpha = 0.8f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
                            .clickable { isSortMenuOpen = !isSortMenuOpen }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when (sortBy) {
                                ProjectSortBy.RECENT -> "Recent"
                                ProjectSortBy.NAME -> "Alphabetical"
                                ProjectSortBy.DURATION -> "Duration"
                            },
                            color = Color(0xFFE4E4E7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Sort Menu",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Sort Dropdown Popup Menu
                    DropdownMenu(
                        expanded = isSortMenuOpen,
                        onDismissRequest = { isSortMenuOpen = false },
                        modifier = Modifier
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    ) {
                        ProjectSortBy.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.label,
                                            color = if (sortBy == option) Color.White else Color(0xFFA1A1AA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (sortBy == option) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFFB923C),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    sortBy = option
                                    isSortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Snap Carousel of Project Cards
            val listState = rememberLazyListState()
            val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

            LazyRow(
                state = listState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(sortedProjects) { index, project ->
                    val isActive = activeCardId == project.id || (activeCardId == null && index == 0)
                    val cardScale by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.88f,
                        animationSpec = tween(durationMillis = 300),
                        label = "cardScale"
                    )
                    val cardOpacity by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.6f,
                        animationSpec = tween(durationMillis = 300),
                        label = "cardOpacity"
                    )

                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .height(370.dp)
                            .scale(cardScale)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color(0xFF18181B))
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(32.dp)
                            )
                            .clickable {
                                if (isActive) {
                                    val file = File(projectEngine.getProjectsDir(), "${project.id}.orca")
                                    if (file.exists()) {
                                        projectEngine.loadProject(file)
                                    }
                                    onOpenProject(project)
                                } else {
                                    activeCardId = project.id
                                }
                            }
                    ) {
                        // Card Cover Visual Artwork
                        val thumbnailBitmap = remember(project.thumbnailPath) {
                            if (!project.thumbnailPath.isNullOrEmpty() && File(project.thumbnailPath).exists()) {
                                try {
                                    BitmapFactory.decodeFile(project.thumbnailPath)
                                } catch (e: Exception) { null }
                            } else null
                        }

                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap.asImageBitmap(),
                                contentDescription = project.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            when (project.type) {
                            "audio" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF3B0764), Color(0xFF18181B), Color(0xFF1E1B4B))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.Bottom,
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            listOf(20, 35, 25, 40, 30, 45, 32, 38, 22).forEach { h ->
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(h.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(Color(0xFFA855F7), Color(0xFFC084FC))
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(100.dp))
                                                .background(Color(0xFFA855F7).copy(alpha = 0.2f))
                                                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color(0xFFC084FC),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "AUDIO TRACK",
                                                color = Color(0xFFE9D5FF),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            "text" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF1E1B4B), Color(0xFF18181B), Color(0xFF0F172A))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.TextFields,
                                            contentDescription = null,
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "\"${project.name}\"",
                                            color = Color(0xFFE0E7FF),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                            else -> { // Video / Image / Default
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF27272A), Color(0xFF09090B))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color(0xFF71717A),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "TIMELINE READY",
                                            color = Color(0xFF52525B),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                        // Glass Top Highlight Line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                        )

                        // Bottom Frosted Panel Overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(170.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF09090B).copy(alpha = 0.85f),
                                            Color(0xFF050507)
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = project.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (project.ratio == "9:16") "9:16 Portrait" else if (project.ratio == "16:9") "16:9 Landscape" else project.ratio,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        text = project.duration,
                                        color = Color(0xFFE4E4E7),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                // Action Buttons on Active Card
                                if (isActive) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Delete Button
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .clickable { projectToDelete = project },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Duplicate Button
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .clickable {
                                                    projectEngine.duplicateProject(project.id)
                                                    projectDocs = projectEngine.listProjects()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Duplicate",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Info / Details Button
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .clickable { showStatsModal = project },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreHoriz,
                                                contentDescription = "Details",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
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

        // Persistent Bottom Floating Action Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .width(320.dp)
                    .height(54.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // New Project Button
                Button(
                    onClick = { isCreatingProject = true },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF18181B).copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Project",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF18181B).copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFD4D4D8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Format Selection Modal / Overlay
        if (isCreatingProject) {
            Dialog(
                onDismissRequest = { isCreatingProject = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF050507))
                ) {
                    // Top Header "CHOOSE FORMAT"
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color(0xFF3F3F46))
                                    )
                                )
                        )
                        Text(
                            text = "CHOOSE FORMAT",
                            color = Color(0xFFA1A1AA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 3.sp
                        )
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF3F3F46), Color.Transparent)
                                    )
                                )
                        )
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 32.dp, end = 24.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { isCreatingProject = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Formats Carousel
                    val formats = remember {
                        listOf(
                            Triple("9:16", "PORTRAIT", Pair(160, 320)),
                            Triple("16:9", "LANDSCAPE", Pair(320, 160)),
                            Triple("1:1", "SQUARE", Pair(220, 220)),
                            Triple("custom", "CUSTOM", Pair(220, 120))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        formats.forEach { (ratio, label, dimensions) ->
                            val isSelected = focusedRatio == ratio
                            val boxWidth = if (isSelected) dimensions.first.dp else (dimensions.first * 0.85).dp
                            val boxHeight = if (isSelected) dimensions.second.dp else (dimensions.second * 0.85).dp

                            Box(
                                modifier = Modifier
                                    .width(boxWidth)
                                    .height(boxHeight)
                                    .clip(RoundedCornerShape(36.dp))
                                    .background(Color(0xFF050507))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        brush = if (isSelected) {
                                            Brush.linearGradient(
                                                listOf(Color(0xFFA78BFA), Color(0xFFFB923C))
                                            )
                                        } else {
                                            Brush.linearGradient(
                                                listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))
                                            )
                                        },
                                        shape = RoundedCornerShape(36.dp)
                                    )
                                    .clickable { focusedRatio = ratio },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (ratio == "custom" && isSelected) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF18181B).copy(alpha = 0.6f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customRatioW,
                                                onValueChange = { customRatioW = it },
                                                modifier = Modifier.width(50.dp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            Text(":", color = Color.White, fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = customRatioH,
                                                onValueChange = { customRatioH = it },
                                                modifier = Modifier.width(50.dp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = if (ratio == "custom") "Custom" else ratio,
                                            color = if (isSelected) Color.White else Color(0xFF52525B),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFFA1A1AA) else Color(0xFF3F3F46),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 2.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Create Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                    ) {
                        Button(
                            onClick = {
                                val (w, h) = when (focusedRatio) {
                                    "9:16" -> 1080 to 1920
                                    "16:9" -> 1920 to 1080
                                    "1:1" -> 1080 to 1080
                                    "4:5" -> 1080 to 1350
                                    "custom" -> (customRatioW.toIntOrNull() ?: 1080) to (customRatioH.toIntOrNull() ?: 1920)
                                    else -> 1080 to 1920
                                }
                                val newDoc = projectEngine.createProject("New Project", w, h)
                                projectDocs = projectEngine.listProjects()
                                isCreatingProject = false
                                onCreateProject(newDoc.metadata.id)
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF050507)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "CREATE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 3.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Project Info / Stats Modal
        showStatsModal?.let { item ->
            Dialog(onDismissRequest = { showStatsModal = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Project Info",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF27272A))
                                    .clickable { showStatsModal = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "Name" to item.name,
                                "Aspect Ratio" to item.ratio,
                                "Resolution" to (item.document?.let { "${it.metadata.width} x ${it.metadata.height}" } ?: "1080 x 1920"),
                                "Duration" to item.duration,
                                "Frame Rate" to (item.document?.let { "${it.metadata.frameRate} fps" } ?: "30 fps"),
                                "Assets & Tracks" to (item.document?.let { "${it.assets.size} items" } ?: "0 items"),
                                "Last Modified" to (item.document?.let { doc ->
                                    val cal = Calendar.getInstance().apply { timeInMillis = doc.metadata.modifiedAtMs }
                                    String.format("%02d/%02d/%d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.YEAR))
                                } ?: "Today"),
                                "Render Engine" to "ORCA Native (GL/Vulkan)"
                            ).forEach { (key, valStr) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF27272A).copy(alpha = 0.6f))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = key, color = Color(0xFFA1A1AA), fontSize = 12.sp)
                                    Text(
                                        text = valStr,
                                        color = if (key == "Render Engine") Color(0xFFFB923C) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val projectToOpen = showStatsModal
                                showStatsModal = null
                                projectToOpen?.let { onOpenProject(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = "Open in Native NLE",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Modal
        projectToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = {
                    Text(
                        text = "Delete Project",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete \"${item.name}\"? This action cannot be undone.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            projectEngine.deleteProject(item.id)
                            projectDocs = projectEngine.listProjects()
                            projectToDelete = null
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF18181B),
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}
