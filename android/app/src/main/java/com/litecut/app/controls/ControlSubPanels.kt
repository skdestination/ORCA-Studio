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
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Color(0xFF141418),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                        "speed" -> "SPEED & CURVE EDITOR"
                        "audio" -> "AUDIO & VOLUME"
                        "voiceover" -> "VOICEOVER RECORDER"
                        "text" -> "TEXT & TYPOGRAPHY"
                        "crop" -> "CROP & ASPECT RATIO"
                        "effects" -> "EFFECTS & FILTERS"
                        else -> "CONTROLS"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = Color(0xFFA1A1AA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub Panel Content Router
            when (activeMenu) {
                "adjust" -> AdjustmentControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "speed" -> SpeedControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "audio" -> AudioControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "voiceover" -> VoiceoverRecorderPanel()
                "text" -> TextEditorControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "crop" -> CropControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
                "effects" -> EffectsControlPanel(selectedClip = selectedClip, onClipUpdate = onClipUpdate)
            }
        }
    }
}

@Composable
fun AdjustmentControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var brightness by remember(selectedClip) { mutableFloatStateOf(selectedClip?.brightness ?: 0f) }
    var contrast by remember(selectedClip) { mutableFloatStateOf(selectedClip?.contrast ?: 0f) }
    var saturation by remember(selectedClip) { mutableFloatStateOf(selectedClip?.saturation ?: 0f) }
    var temperature by remember(selectedClip) { mutableFloatStateOf(selectedClip?.temperature ?: 0f) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    range: ClosedRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFD4D4D8),
            fontSize = 12.sp,
            modifier = Modifier.width(90.dp)
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
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeedControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var selectedMode by remember { mutableStateOf("normal") } // "normal" or "curve"
    var speedMultiplier by remember(selectedClip) { mutableFloatStateOf(selectedClip?.speed?.toFloat() ?: 1.0f) }
    var selectedCurvePreset by remember { mutableStateOf("None") }

    val curvePresets = listOf("None", "Hero", "Bullet Time", "Montage", "Flash", "Jump")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E24))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedMode == "normal") Color(0xFF3F3F46) else Color.Transparent)
                    .clickable { selectedMode = "normal" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Normal Speed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedMode == "curve") Color(0xFF3F3F46) else Color.Transparent)
                    .clickable { selectedMode = "curve" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Speed Curve", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (selectedMode == "normal") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Speed: ${"%.1f".format(speedMultiplier)}x", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                Slider(
                    value = speedMultiplier,
                    onValueChange = {
                        speedMultiplier = it
                        selectedClip?.let { clip ->
                            onClipUpdate(clip.copy(speed = it.toDouble()))
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
            // Speed Curve Presets Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                curvePresets.forEach { preset ->
                    val isSelected = selectedCurvePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                            .clickable {
                                selectedCurvePreset = preset
                                selectedClip?.let { clip ->
                                    // Evaluate custom speed curve factor using BezierCurve evaluator
                                    val factor = when (preset) {
                                        "Hero" -> BezierCurve.evaluate(0.5, 0.12, 0.0, 0.39, 0.0) * 3.0 + 0.5
                                        "Bullet Time" -> BezierCurve.evaluate(0.5, 0.0, 1.0, 1.0, 0.0) * 0.2 + 0.1
                                        "Montage" -> BezierCurve.evaluate(0.5, 0.25, 0.1, 0.25, 1.0) * 2.5
                                        "Flash" -> BezierCurve.evaluate(0.5, 0.6, 0.04, 0.98, 0.335) * 4.0
                                        "Jump" -> BezierCurve.evaluate(0.5, 0.4, 0.0, 0.2, 1.0) * 2.0
                                        else -> 1.0
                                    }
                                    clip.additionalProperties["speedCurve"] = preset
                                    onClipUpdate(clip.copy(speed = factor))
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset,
                            color = Color.White,
                            fontSize = 12.sp,
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
    var volume by remember(selectedClip) { mutableFloatStateOf((selectedClip?.volume ?: 1.0f) * 100f) }
    var fadeIn by remember(selectedClip) { mutableFloatStateOf((selectedClip?.additionalProperties?.get("fadeIn") as? Number)?.toFloat() ?: 0f) }
    var fadeOut by remember(selectedClip) { mutableFloatStateOf((selectedClip?.additionalProperties?.get("fadeOut") as? Number)?.toFloat() ?: 0f) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdjustmentSlider("Volume", volume, 0f..200f) {
            volume = it
            selectedClip?.let { clip ->
                clip.volume = it / 100f
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Fade In (s)", fadeIn, 0f..5f) {
            fadeIn = it
            selectedClip?.let { clip ->
                clip.additionalProperties["fadeIn"] = it
                onClipUpdate(clip)
            }
        }
        AdjustmentSlider("Fade Out (s)", fadeOut, 0f..5f) {
            fadeOut = it
            selectedClip?.let { clip ->
                clip.additionalProperties["fadeOut"] = it
                onClipUpdate(clip)
            }
        }
    }
}

@Composable
fun VoiceoverRecorderPanel() {
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isRecording) "RECORDING VOICE OVER..." else "Tap to start recording voiceover",
            color = if (isRecording) Color(0xFFEF4444) else Color(0xFFA1A1AA),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF6366F1))
                .clickable { isRecording = !isRecording },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Voiceover",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun TextEditorControlPanel(
    selectedClip: Clip?,
    onClipUpdate: (Clip) -> Unit
) {
    var textInput by remember(selectedClip) { mutableStateOf(selectedClip?.text ?: "Sample Text") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                selectedClip?.let { clip ->
                    clip.text = it
                    onClipUpdate(clip)
                }
            },
            label = { Text("Overlay Text Content", color = Color(0xFFA1A1AA)) },
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
    val ratios = listOf("16:9", "9:16", "1:1", "4:5", "21:9", "Free")
    var selectedRatio by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("cropRatio") as? String) ?: "9:16") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ratios.forEach { ratio ->
            val isSelected = selectedRatio == ratio
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF27272A))
                    .clickable {
                        selectedRatio = ratio
                        selectedClip?.let { clip ->
                            clip.additionalProperties["cropRatio"] = ratio
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = ratio,
                    color = Color.White,
                    fontSize = 12.sp,
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
    val effects = listOf("Smooth Slow-Mo (RAFT)", "DIS Flow", "AI Color Pop", "Glow Filter", "Retro Film")
    var selectedEffect by remember(selectedClip) { mutableStateOf((selectedClip?.additionalProperties?.get("activeEffect") as? String) ?: "Smooth Slow-Mo (RAFT)") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        effects.forEach { effect ->
            val isSelected = selectedEffect == effect
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFA855F7) else Color(0xFF27272A))
                    .clickable {
                        selectedEffect = effect
                        selectedClip?.let { clip ->
                            clip.additionalProperties["activeEffect"] = effect
                            onClipUpdate(clip)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = effect,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

