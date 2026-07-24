package com.litecut.app.controls

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.litecut.app.timeline.Clip

import com.litecut.app.timeline.brightness
import com.litecut.app.timeline.contrast
import com.litecut.app.timeline.saturation
import com.litecut.app.timeline.temperature
import com.litecut.app.timeline.volume
import com.litecut.app.timeline.text
import com.litecut.app.timeline.BezierCurve

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun FlowBarSubPanelContainer(
    activeMenu: String?,
    selectedClip: Clip?,
    onClose: () -> Unit,
    onClipUpdate: (Clip) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeMenu == null) return

    Surface(
        modifier = modifier
            .width(218.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = Color(0xF50D0D12),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (activeMenu) {
                        "adjust" -> "COLOR ADJUSTMENTS"
                        "speed" -> "SPEED & CURVES"
                        "audio" -> "AUDIO & VOLUME"
                        "volume" -> "VOLUME CONTROL"
                        "voiceover" -> "VOICEOVER"
                        "text" -> "TEXT & TYPOGRAPHY"
                        "crop" -> "CROP & PRESETS"
                        "effects" -> "EFFECTS"
                        "move" -> "MOVE & TRANSFORM"
                        "blend" -> "BLEND & OPACITY"
                        "mask" -> "MASK SHAPES"
                        "stabilize" -> "STABILIZATION"
                        "motion" -> "MOTION BAR"
                        "animation" -> "ANIMATION CURVES"
                        else -> "CONTROLS"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub Panel Content Router
            when (activeMenu) {
                "adjust" -> AdjustmentControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "speed" -> SpeedControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "audio", "volume" -> AudioControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "voiceover" -> VoiceoverRecorderPanel()
                "text" -> TextEditorControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "crop" -> CropControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "effects" -> EffectsControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "move" -> MoveTransformControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "blend" -> BlendControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "mask" -> MaskControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "stabilize" -> StabilizeControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "motion" -> MotionControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "animation" -> AnimationControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
            }
        }
    }
}

@Composable
fun AdjustmentControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var brightness by remember(selectedClip) { mutableStateOf(selectedClip?.brightness ?: 0f) }
    var contrast by remember(selectedClip) { mutableStateOf(selectedClip?.contrast ?: 0f) }
    var saturation by remember(selectedClip) { mutableStateOf(selectedClip?.saturation ?: 0f) }
    var temperature by remember(selectedClip) { mutableStateOf(selectedClip?.temperature ?: 0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjustmentSlider("Brightness", brightness, -100f..100f) {
            brightness = it
            selectedClip?.let { clip ->
                clip.brightness = it
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Contrast", contrast, -100f..100f) {
            contrast = it
            selectedClip?.let { clip ->
                clip.contrast = it
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Saturation", saturation, -100f..100f) {
            saturation = it
            selectedClip?.let { clip ->
                clip.saturation = it
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Temperature", temperature, -100f..100f) {
            temperature = it
            selectedClip?.let { clip ->
                clip.temperature = it
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFD4D4D8),
            fontSize = 10.sp,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6366F1),
                activeTrackColor = Color(0xFF6366F1),
                inactiveTrackColor = Color(0xFF27272A)
            )
        )
        Text(
            text = "${value.toInt()}",
            color = Color(0xFFA1A1AA),
            fontSize = 10.sp,
            modifier = Modifier.width(28.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeedControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var selectedMode by remember { mutableStateOf("normal") }
    var speedMultiplier by remember(selectedClip) { mutableStateOf(selectedClip?.speed?.toFloat() ?: 1.0f) }
    var selectedCurvePreset by remember { mutableStateOf("None") }

    val curvePresets = listOf("None", "Hero", "Bullet Time", "Montage", "Flash", "Jump")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E24))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedMode == "normal") Color(0xFF3F3F46) else Color.Transparent)
                    .clickable { selectedMode = "normal" }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Normal", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedMode == "curve") Color(0xFF3F3F46) else Color.Transparent)
                    .clickable { selectedMode = "curve" }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Curves", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (selectedMode == "normal") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Speed: ${"%.1f".format(speedMultiplier)}x", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(70.dp))
                Slider(
                    value = speedMultiplier,
                    onValueChange = {
                        speedMultiplier = it
                        selectedClip?.let { clip ->
                            onClipUpdate(clip.deepCopy(speed = it.toDouble()))
                        }
                    },
                    valueRange = 0.1f..10.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFA855F7),
                        activeTrackColor = Color(0xFFA855F7),
                        inactiveTrackColor = Color(0xFF27272A)
                    )
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                curvePresets.forEach { preset ->
                    val isSelected = selectedCurvePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                            .clickable {
                                selectedCurvePreset = preset
                                selectedClip?.let { clip ->
                                    val factor = when (preset) {
                                        "Hero" -> BezierCurve.evaluate(0.5, 0.12, 0.0, 0.39, 0.0) * 3.0 + 0.5
                                        "Bullet Time" -> BezierCurve.evaluate(0.5, 0.0, 1.0, 1.0, 0.0) * 0.2 + 0.1
                                        "Montage" -> BezierCurve.evaluate(0.5, 0.25, 0.1, 0.25, 1.0) * 2.5
                                        "Flash" -> BezierCurve.evaluate(0.5, 0.6, 0.04, 0.98, 0.335) * 4.0
                                        "Jump" -> BezierCurve.evaluate(0.5, 0.4, 0.0, 0.2, 1.0) * 2.0
                                        else -> 1.0
                                    }
                                    clip.additionalProperties["speedCurve"] = preset
                                    onClipUpdate(clip.deepCopy(speed = factor))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var volume by remember(selectedClip) { mutableStateOf((selectedClip?.volume ?: 1.0f) * 100f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjustmentSlider("Volume", volume, 0f..200f) {
            volume = it
            selectedClip?.let { clip ->
                clip.volume = it / 100f
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
fun VoiceoverRecorderPanel() {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            isRecording = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasMicPermission) {
            Text(
                text = "Microphone Permission Required",
                color = Color(0xFFEF4444),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Allow Mic Access", fontSize = 10.sp, color = Color.White)
            }
        } else {
            Text(
                text = if (isRecording) "RECORDING..." else "Tap mic to start",
                color = if (isRecording) Color(0xFFEF4444) else Color(0xFFA1A1AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF6366F1))
                    .clickable { isRecording = !isRecording },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Voiceover",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TextEditorControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var textInput by remember(selectedClip) { mutableStateOf(selectedClip?.text ?: "Sample Text") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                selectedClip?.let { clip ->
                    clip.text = it
                    onClipUpdate(clip)
                }
            },
            label = { Text("Overlay Text", color = Color(0xFFA1A1AA), fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0xFF3F3F46),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }
}

@Composable
fun CropControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    val ratios = listOf("Free", "1:1", "16:9", "9:16")
    var selectedRatio by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("cropRatio") as? String) ?: "Free") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ratios.forEach { ratio ->
            val isSelected = selectedRatio == ratio
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                    .clickable {
                        selectedRatio = ratio
                        selectedClip?.let { clip ->
                            clip.additionalProperties["cropRatio"] = ratio
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = ratio,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun EffectsControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    val effects = listOf("Slow-Mo", "DIS Flow", "Color Pop", "Glow")
    var selectedEffect by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("activeEffect") as? String) ?: "Slow-Mo") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        effects.forEach { effect ->
            val isSelected = selectedEffect == effect
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFFA855F7) else Color(0xFF27272A))
                    .clickable {
                        selectedEffect = effect
                        selectedClip?.let { clip ->
                            clip.additionalProperties["activeEffect"] = effect
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = effect,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun MoveTransformControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var scale by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("scale") as? Number)?.toFloat() ?: 1.0f) }
    var rotation by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("rotation") as? Number)?.toFloat() ?: 0.0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjustmentSlider("Scale", scale * 100f, 10f..300f) {
            scale = it / 100f
            selectedClip?.let { clip ->
                clip.additionalProperties["scale"] = scale
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Rotation", rotation, -180f..180f) {
            rotation = it
            selectedClip?.let { clip ->
                clip.additionalProperties["rotation"] = rotation
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
fun BlendControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var opacity by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("opacity") as? Number)?.toFloat() ?: 1.0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjustmentSlider("Opacity", opacity * 100f, 0f..100f) {
            opacity = it / 100f
            selectedClip?.let { clip ->
                clip.additionalProperties["opacity"] = opacity
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
fun MaskControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    val masks = listOf("None", "Circle", "Square", "Half")
    var selectedMask by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("mask") as? String) ?: "None") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        masks.forEach { mask ->
            val isSelected = selectedMask == mask
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                    .clickable {
                        selectedMask = mask
                        selectedClip?.let { clip ->
                            clip.additionalProperties["mask"] = mask
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = mask,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StabilizeControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var level by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("stabilizeLevel") as? Number)?.toFloat() ?: 50f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjustmentSlider("Smoothness", level, 0f..100f) {
            level = it
            selectedClip?.let { clip ->
                clip.additionalProperties["stabilizeLevel"] = level
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
fun MotionControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    Text(
        text = "Keyframe motion tracking active",
        color = Color(0xFFA5B4FC),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun AnimationControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    val curves = listOf("Linear", "Ease In", "Ease Out", "In Out")
    var selectedCurve by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("curve") as? String) ?: "Linear") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        curves.forEach { curve ->
            val isSelected = selectedCurve == curve
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                    .clickable {
                        selectedCurve = curve
                        selectedClip?.let { clip ->
                            clip.additionalProperties["curve"] = curve
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text(
                    text = curve,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
