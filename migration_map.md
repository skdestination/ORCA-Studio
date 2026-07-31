# React → Native Connection Map

## Classification Key
- **Category A**: Sandbox + Native (UI Simulation & Native UI execution)
- **Category B**: Native Only (Production logic, Media engines)

## Component Audit

### 1. `Timeline` (src/App.tsx, TimelineThumbnail.tsx, ClipRenderer.tsx, Renderers.tsx)
- **Classification**: Pure presentation & Editor interaction logic (Category A)
- **Corresponding Native UI**: `EditorTimelineArea.kt`, `TimelineContainer.kt`, `TimelineView.kt`, `TimelineRenderer.kt`, `TimelineScreen.kt`
- **Native Engine**: `TimelineEngine.kt`, `TimelineGestureHandler.kt`
- **Implementation Status**: Complete (Native).
- **Migration Plan**: Use `TimelineEngine.getProjectJSON()` as the snapshot source for React.

### 2. `Preview Canvas` (src/App.tsx, MaskControlOverlay.tsx, CropControlOverlay.tsx)
- **Classification**: Editor interaction logic & Media logic placeholder (Category A)
- **Corresponding Native UI**: `EditorPreviewCanvas.kt`
- **Native Engine**: `PreviewEngine.kt`, `PlaybackEngine.kt`, `OrcaEngine.kt`
- **Implementation Status**: Partial (Native has canvas but missing interactive crop/mask gestures).
- **Migration Plan**: Connect `PreviewEngine` to `EditorPreviewCanvas` for production media rendering. Sandbox emulates crop/mask handles using state from Native.

### 3. `Bottom Controls & Toolbars` (src/components/Controls.tsx, EditorBottomToolBar.tsx placeholders in React App.tsx)
- **Classification**: Pure presentation (Category A)
- **Corresponding Native UI**: `EditorBottomToolBar.kt`, `EditorHeaderBar.kt`, `EditorTransportBar.kt`
- **Native Engine**: Dispatch `Command` objects to `TimelineEngine.kt`
- **Implementation Status**: Complete (Native UI built).
- **Migration Plan**: Replace React controls with generic snapshot-driven metadata UI.

### 4. `Property Panels` (AdjustPanel.tsx, AnimationPanel.tsx, BlendPanel.tsx, etc.)
- **Classification**: Pure presentation (Category A)
- **Corresponding Native UI**: `ControlSubPanels.kt` (FlowBarSubPanelContainer, etc.)
- **Native Engine**: `TimelineEngine.executeCommand`
- **Implementation Status**: Complete (Native panels built).
- **Migration Plan**: Update Native models to export active panel state in snapshot.

### 5. `Media Export` (src/lib/videoExport.ts, ExportOverlay.tsx)
- **Classification**: Production media logic (Category B)
- **Corresponding Native UI**: `ExportPipeline.kt`, `ExportController.java`, `VideoEncoder.java`, `TextureRender.java`
- **Native Engine**: FFmpeg / MediaCodec / EglCore
- **Implementation Status**: Complete (Native backend exists).
- **Migration Plan**: Sandbox only triggers visual progress. Native handles full encode.

### 6. `Playback & Audio` (src/lib/playbackEngine.ts, VolumePanel.tsx)
- **Classification**: Production media logic (Category B)
- **Corresponding Native UI**: `PlaybackEngine.kt`, `AudioRenderCoordinator.kt`
- **Native Engine**: `ExoPlayer` (or standard MediaEngine)
- **Implementation Status**: Complete/Partial (Native engines exist).
- **Migration Plan**: Sandbox simulates playhead movement. Native does actual video playback.

### 7. `Media Processing & SlowMo` (src/lib/opticalFlow.ts, src/components/MotionPanel.tsx)
- **Classification**: Production media logic (Category B)
- **Corresponding Native UI**: `OpticalFlowEngine.java`, `RAFTOpticalFlowEngine.java`
- **Native Engine**: C++ / JNI / GPU Rendering
- **Implementation Status**: Complete (Native engines exist).
- **Migration Plan**: Strictly Native execution. Sandbox only shows UI toggles.
