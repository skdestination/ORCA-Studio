const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/EditorPreviewCanvas.kt', 'utf8');

// Replace AsyncImage block with AndroidView
const target = `                        // High quality cinematic portrait visual preview or real media source
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Preview Media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {`;

const replacement = `                        // Hardware-accelerated OpenGL Native Render Engine
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                PreviewSurfaceView(ctx)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        if (imageRequest == null) {`;

code = code.replace(target, replacement);
fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/EditorPreviewCanvas.kt', code);
