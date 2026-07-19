package com.litecut.app.timeline

import org.json.JSONObject

data class MaskShape(
    var centerX: Float = 0.5f,
    var centerY: Float = 0.5f,
    var width: Float = 0.3f,
    var height: Float = 0.3f,
    var rotation: Float = 0.0f,
    var roundness: Float = 0.0f, // 0.0f to 1.0f (used for rounded corners in rectangles)
    var sides: Int = 4, // used for Polygon primitives
    val path: MaskPath = MaskPath() // used for BEZIER, FREE_DRAW, AI_ROTO, etc.
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("centerX", centerX.toDouble())
        json.put("centerY", centerY.toDouble())
        json.put("width", width.toDouble())
        json.put("height", height.toDouble())
        json.put("rotation", rotation.toDouble())
        json.put("roundness", roundness.toDouble())
        json.put("sides", sides)
        json.put("path", path.toJSONObject())
        return json
    }

    fun copy(): MaskShape {
        return MaskShape(
            centerX = centerX,
            centerY = centerY,
            width = width,
            height = height,
            rotation = rotation,
            roundness = roundness,
            sides = sides,
            path = path.copy()
        )
    }

    companion object {
        fun fromJSONObject(json: JSONObject): MaskShape {
            val shape = MaskShape(
                centerX = json.optDouble("centerX", 0.5).toFloat(),
                centerY = json.optDouble("centerY", 0.5).toFloat(),
                width = json.optDouble("width", 0.3).toFloat(),
                height = json.optDouble("height", 0.3).toFloat(),
                rotation = json.optDouble("rotation", 0.0).toFloat(),
                roundness = json.optDouble("roundness", 0.0).toFloat(),
                sides = json.optInt("sides", 4)
            )
            val pathObj = json.optJSONObject("path")
            if (pathObj != null) {
                // Initialize from path JSON
                val loadedPath = MaskPath.fromJSONObject(pathObj)
                shape.path.isClosed = loadedPath.isClosed
                shape.path.points.clear()
                shape.path.points.addAll(loadedPath.points)
            }
            return shape
        }
    }
}
