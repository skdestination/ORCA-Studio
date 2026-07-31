const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf8');

code = code.replace(/val hasKeyframeAtCurrentTime = remember\(currentTime, activeClipId\) \{[\s\S]*?\}\n/, `    val hasKeyframeAtCurrentTime = remember(currentTime, selectedClipIds) {
        val activeClipId = selectedClipIds.firstOrNull()
        if (activeClipId == null) false
        else {
            val clip = engine.getClip(activeClipId)
            if (clip == null) false
            else {
                val timeOffset = engine.currentTime - clip.leftSeconds
                var found = false
                val kfsObj = clip.additionalProperties["keyframes"]
                if (kfsObj is org.json.JSONArray) {
                    for (i in 0 until kfsObj.length()) {
                        val kf = kfsObj.optJSONObject(i)
                        if (kf != null && kotlin.math.abs(kf.optDouble("timeOffset", 0.0) - timeOffset) < 0.05) {
                            found = true
                            break
                        }
                    }
                }
                found
            }
        }
    }
`);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', code);
