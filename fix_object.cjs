const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/PreviewSurfaceView.kt', 'utf8');

code = code.replace(/\(this as Object\)/g, '(this as java.lang.Object)');

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/PreviewSurfaceView.kt', code);
