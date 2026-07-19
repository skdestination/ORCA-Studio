package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class MaskPath(
    val points: MutableList<MaskPoint> = mutableListOf(),
    var isClosed: Boolean = true
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("isClosed", isClosed)
        val pointsArray = JSONArray()
        for (point in points) {
            pointsArray.put(point.toJSONObject())
        }
        json.put("points", pointsArray)
        return json
    }

    fun copy(): MaskPath {
        val copiedPoints = points.map { it.copy() }.toMutableList()
        return MaskPath(copiedPoints, isClosed)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): MaskPath {
            val path = MaskPath(
                isClosed = json.optBoolean("isClosed", true)
            )
            val pointsArray = json.optJSONArray("points")
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    path.points.add(MaskPoint.fromJSONObject(pointsArray.getJSONObject(i)))
                }
            }
            return path
        }
    }
}
