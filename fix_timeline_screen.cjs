const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf8');

const onToggleKeyframeReplace = `                        onToggleKeyframe = {
                            activeClipId?.let { clipId ->
                                val clip = engine.getClip(clipId)
                                if (clip != null) {
                                    val timeOffset = engine.currentTime - clip.leftSeconds
                                    if (timeOffset >= 0 && timeOffset <= clip.durationSeconds) {
                                        // add keyframe
                                        val kf = Keyframe(
                                            id = "kf_" + java.util.UUID.randomUUID().toString(),
                                            timeOffset = timeOffset,
                                            value = 1.0,
                                            property = "scale",
                                            interpolation = InterpolationType.LINEAR
                                        )
                                        engine.executeCommand(AddKeyframeCommand(clipId, kf))
                                    }
                                }
                            }
                        }`;

code = code.replace(
    '                        onToggleKeyframe = {\n                            // Drop / remove keyframe logic\n                        }',
    onToggleKeyframeReplace
);

// Determine hasKeyframeAtCurrentTime
const hasKeyframeInsert = `
    val hasKeyframeAtCurrentTime = remember(currentTime, activeClipId) {
        if (activeClipId == null) false
        else {
            val clip = engine.getClip(activeClipId)
            if (clip == null) false
            else {
                val timeOffset = engine.currentTime - clip.leftSeconds
                val kfList = KeyframeEngine.findKeyframesAtTime(clip, timeOffset, 0.05)
                kfList.isNotEmpty()
            }
        }
    }
`;

code = code.replace('    var canUndo by remember { mutableStateOf(engine.canUndo()) }', '    var canUndo by remember { mutableStateOf(engine.canUndo()) }\n' + hasKeyframeInsert);

code = code.replace('hasKeyframeAtCurrentTime = false,', 'hasKeyframeAtCurrentTime = hasKeyframeAtCurrentTime,');

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', code);
