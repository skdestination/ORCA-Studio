const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', 'utf8');

// Fix 1: val deltaSeconds -> var deltaSeconds at 215
code = code.replace('val deltaSeconds = dx / engine.pixelsPerSecond', 'var deltaSeconds = dx / engine.pixelsPerSecond');

// Fix 2: .has("_originalTrimStart") -> .containsKey("_originalTrimStart")
code = code.replace(/clip\.additionalProperties\.has\("_originalTrimStart"\)/g, 'clip.additionalProperties.containsKey("_originalTrimStart")');

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', code);
