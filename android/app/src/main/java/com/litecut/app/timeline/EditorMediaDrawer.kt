package com.litecut.app.timeline

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MediaItemModel(
    val id: String,
    val name: String,
    val type: String, // "image", "video", "audio"
    val durationText: String = "",
    val folderName: String = "Media",
    val path: String = ""
)

data class FolderModel(
    val name: String,
    val itemCount: Int,
    val iconEmoji: String = "📁"
)

val DEFAULT_MOCK_MEDIA_ITEMS = listOf(
    MediaItemModel("1", "Camera_Capture_01.mp4", "video", "0:15", "Camera"),
    MediaItemModel("2", "Sunset_Vlog.mp4", "video", "0:42", "Camera"),
    MediaItemModel("3", "Neon_City.jpg", "image", "", "Pictures"),
    MediaItemModel("4", "Cyber_Portrait.png", "image", "", "Pictures"),
    MediaItemModel("5", "LoFi_Beat_Track.mp3", "audio", "2:30", "Music"),
    MediaItemModel("6", "Cinematic_Bass_Drop.wav", "audio", "0:05", "Music"),
    MediaItemModel("7", "Action_BRoll_4K.mp4", "video", "1:12", "Downloads"),
    MediaItemModel("8", "Aesthetic_Filter_Bg.jpg", "image", "", "Downloads")
)

val DEFAULT_FOLDERS = listOf(
    FolderModel("Camera", 12, "🎥"),
    FolderModel("Pictures", 34, "🖼️"),
    FolderModel("Music", 8, "🎵"),
    FolderModel("Downloads", 19, "📥")
)

@Composable
fun EditorMediaDrawer(
    isVisible: Boolean,
    onCloseDrawer: () -> Unit,
    onMediaSelected: (MediaItemModel) -> Unit,
    onRequestPermission: () -> Unit,
    isPermissionGranted: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("Video") } // "Image", "Video", "Audio", "Folders"
    var currentFolder by remember { mutableStateOf<String?>(null) }

    val filteredMediaItems = remember(selectedTab, currentFolder) {
        DEFAULT_MOCK_MEDIA_ITEMS.filter { item ->
            if (currentFolder != null) {
                item.folderName.equals(currentFolder, ignoreCase = true)
            } else {
                when (selectedTab) {
                    "Image" -> item.type == "image"
                    "Video" -> item.type == "video"
                    "Audio" -> item.type == "audio"
                    else -> true
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFA0D0D12))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Drag / Close Pill Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onCloseDrawer() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x4DFFFFFF))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // TOP HEADER: Drawer Title & Media Tabs Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEDIA LIBRARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    // 4 Compact Tab Capsules: Image, Video, Audio, Folders
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF18181B))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("Video", Icons.Default.PlayArrow, "Video"),
                            Triple("Image", Icons.Default.Image, "Image"),
                            Triple("Audio", Icons.Default.MusicNote, "Audio"),
                            Triple("Folders", Icons.Default.Folder, "Folders")
                        ).forEach { (tabKey, icon, label) ->
                            val isActive = selectedTab == tabKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isActive) Color(0xFF6366F1) else Color.Transparent)
                                    .clickable {
                                        selectedTab = tabKey
                                        currentFolder = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isActive) Color.White else Color(0xFFA1A1AA),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    if (isActive) {
                                        Text(
                                            text = label,
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

                Spacer(modifier = Modifier.height(12.dp))

                // PERMISSION STATE UI OR MEDIA GRID BODY
                if (!isPermissionGranted) {
                    // Storage Permission Request State Banner
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1F6366F1))
                                    .border(1.dp, Color(0x336366F1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Permission Required",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Grant Storage Access",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Allow storage access to import high-speed video clips, music, and captures into your timeline.",
                                fontSize = 11.sp,
                                color = Color(0xFFA1A1AA),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { onRequestPermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Allow Access",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // Media Content Body
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Current Folder Back Navigation Header
                        if (currentFolder != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF27272A))
                                        .clickable { currentFolder = null }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Back to Folders",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "Folder: $currentFolder",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        // Media Items Grid (3 or 4 Columns)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Import Custom Tile
                            item {
                                Box(
                                    modifier = Modifier
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF18181B))
                                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                        .clickable {
                                            // Browse device files picker
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Import",
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Import File",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Browse Storage",
                                            fontSize = 8.sp,
                                            color = Color(0xFF71717A)
                                        )
                                    }
                                }
                            }

                            // Folders view mode
                            if (selectedTab == "Folders" && currentFolder == null) {
                                items(DEFAULT_FOLDERS) { folder ->
                                    Box(
                                        modifier = Modifier
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF161619))
                                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                            .clickable { currentFolder = folder.name }
                                            .padding(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(folder.iconEmoji, fontSize = 22.sp)
                                            Column {
                                                Text(
                                                    text = folder.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${folder.itemCount} items",
                                                    fontSize = 8.sp,
                                                    color = Color(0xFF71717A)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Media items view
                                items(filteredMediaItems) { item ->
                                    Box(
                                        modifier = Modifier
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when (item.type) {
                                                    "audio" -> Color(0xFF1E1B2E)
                                                    "image" -> Color(0xFF12201A)
                                                    else -> Color(0xFF181822)
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when (item.type) {
                                                    "audio" -> Color(0x33A855F7)
                                                    "image" -> Color(0x3310B981)
                                                    else -> Color(0x336366F1)
                                                },
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onMediaSelected(item) }
                                            .padding(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Top icon / badge
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = when (item.type) {
                                                        "audio" -> Icons.Default.MusicNote
                                                        "image" -> Icons.Default.Image
                                                        else -> Icons.Default.PlayArrow
                                                    },
                                                    contentDescription = item.type,
                                                    tint = when (item.type) {
                                                        "audio" -> Color(0xFFC084FC)
                                                        "image" -> Color(0xFF34D399)
                                                        else -> Color(0xFF818CF8)
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                )

                                                if (item.durationText.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xCC000000))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = item.durationText,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            // Bottom name
                                            Text(
                                                text = item.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
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
}
