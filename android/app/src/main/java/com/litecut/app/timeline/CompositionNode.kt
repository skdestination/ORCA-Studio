package com.litecut.app.timeline

class CompositionNode {
    var id: String = ""
    var clipId: String = ""
    var layerId: String = ""
    var layerOrder: Int = 0
    var type: ClipType = ClipType.VIDEO
    var src: String = ""
    
    // Animatable values resolved at frame time via KeyframeEngine/AnimationEvaluator
    var opacity: Float = 1.0f
    var scaleX: Float = 1.0f
    var scaleY: Float = 1.0f
    var translationX: Float = 0.0f
    var translationY: Float = 0.0f
    var rotation: Float = 0.0f
    
    // Crop boundaries (normalized 0.0 to 1.0)
    var cropLeft: Float = 0.0f
    var cropTop: Float = 0.0f
    var cropRight: Float = 0.0f
    var cropBottom: Float = 0.0f
    
    // Audio configuration
    var volume: Float = 1.0f
    var relativeTimeOffset: Double = 0.0
    var isProxy: Boolean = false
    
    // Extensible fields for future production additions (Blend modes, Adjustment layers, Transitions)
    var blendMode: String = "NORMAL"
    var effectId: String? = null
    var isAdjustmentLayer: Boolean = false
    var transitionType: String? = null
    var transitionDuration: Double = 0.0

    fun copyFrom(other: CompositionNode) {
        this.id = other.id
        this.clipId = other.clipId
        this.layerId = other.layerId
        this.layerOrder = other.layerOrder
        this.type = other.type
        this.src = other.src
        this.opacity = other.opacity
        this.scaleX = other.scaleX
        this.scaleY = other.scaleY
        this.translationX = other.translationX
        this.translationY = other.translationY
        this.rotation = other.rotation
        this.cropLeft = other.cropLeft
        this.cropTop = other.cropTop
        this.cropRight = other.cropRight
        this.cropBottom = other.cropBottom
        this.volume = other.volume
        this.relativeTimeOffset = other.relativeTimeOffset
        this.isProxy = other.isProxy
        this.blendMode = other.blendMode
        this.effectId = other.effectId
        this.isAdjustmentLayer = other.isAdjustmentLayer
        this.transitionType = other.transitionType
        this.transitionDuration = other.transitionDuration
    }

    fun reset() {
        id = ""
        clipId = ""
        layerId = ""
        layerOrder = 0
        type = ClipType.VIDEO
        src = ""
        opacity = 1.0f
        scaleX = 1.0f
        scaleY = 1.0f
        translationX = 0.0f
        translationY = 0.0f
        rotation = 0.0f
        cropLeft = 0.0f
        cropTop = 0.0f
        cropRight = 0.0f
        cropBottom = 0.0f
        volume = 1.0f
        relativeTimeOffset = 0.0
        isProxy = false
        blendMode = "NORMAL"
        effectId = null
        isAdjustmentLayer = false
        transitionType = null
        transitionDuration = 0.0
    }
}
