package com.litecut.app.timeline

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

@CapacitorPlugin(name = "TimelineEngine")
class TimelineEnginePlugin : Plugin() {
    private val engine = TimelineEngine.getInstance()

    @PluginMethod
    fun initTimeline(call: PluginCall) {
        val projectJson = call.getString("project")
        if (projectJson != null) {
            engine.loadFromProjectJSON(projectJson)
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("Project JSON missing")
        }
    }

    @PluginMethod
    fun getTimelineState(call: PluginCall) {
        val state = engine.getProjectJSON()
        val jsState = JSObject(state.toString())
        call.resolve(jsState)
    }

    @PluginMethod
    fun createClip(call: PluginCall) {
        val clipObj = call.getObject("clip")
        if (clipObj != null) {
            val clip = Clip.fromJSONObject(JSONObject(clipObj.toString()))
            engine.executeCommand(CreateClipCommand(clip))
            
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("Clip object missing")
        }
    }

    @PluginMethod
    fun deleteClip(call: PluginCall) {
        val clipIdsArr = call.getArray("clipIds")
        if (clipIdsArr != null) {
            val clipIds = ArrayList<String>()
            for (i in 0 until clipIdsArr.length()) {
                clipIds.add(clipIdsArr.getString(i))
            }
            engine.executeCommand(DeleteCommand(clipIds))
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("Clip IDs missing")
        }
    }

    @PluginMethod
    fun splitClip(call: PluginCall) {
        val clipId = call.getString("clipId")
        val splitTime = call.getDouble("splitTime")
        val generatedId = call.getString("generatedId") ?: java.util.UUID.randomUUID().toString()
        if (clipId != null && splitTime != null) {
            val cmd = SplitCommand(clipId, splitTime, generatedId)
            engine.executeCommand(cmd)
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("clipId or splitTime missing")
        }
    }

    @PluginMethod
    fun moveClip(call: PluginCall) {
        val clipIdsArr = call.getArray("clipIds")
        val deltaSeconds = call.getDouble("deltaSeconds")
        val targetLayerId = call.getString("targetLayerId")
        val fallbackLayerId = call.getString("fallbackLayerId")

        if (clipIdsArr != null && deltaSeconds != null && targetLayerId != null && fallbackLayerId != null) {
            val clipIds = ArrayList<String>()
            for (i in 0 until clipIdsArr.length()) {
                clipIds.add(clipIdsArr.getString(i))
            }
            val cmd = MoveCommand(clipIds, deltaSeconds, targetLayerId, fallbackLayerId)
            engine.executeCommand(cmd)
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("Missing required parameters for move")
        }
    }

    @PluginMethod
    fun trimClip(call: PluginCall) {
        val clipId = call.getString("clipId")
        val side = call.getString("side")
        val deltaSeconds = call.getDouble("deltaSeconds")
        val snappingEnabled = call.getBoolean("snappingEnabled", true)!!
        val currentTime = call.getDouble("currentTime", 0.0)!!

        if (clipId != null && side != null && deltaSeconds != null) {
            val cmd = TrimCommand(clipId, side, deltaSeconds, snappingEnabled, currentTime)
            engine.executeCommand(cmd)
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("Missing parameters for trim")
        }
    }

    @PluginMethod
    fun rippleDelete(call: PluginCall) {
        val clipId = call.getString("clipId")
        if (clipId != null) {
            engine.executeCommand(RippleDeleteCommand(clipId))
            val response = JSObject()
            response.put("success", true)
            call.resolve(response)
        } else {
            call.reject("clipId missing")
        }
    }

    @PluginMethod
    fun setZoom(call: PluginCall) {
        val zoom = call.getDouble("zoom")
        if (zoom != null) {
            engine.zoomLevel = zoom
            call.resolve()
        } else {
            call.reject("zoom missing")
        }
    }

    @PluginMethod
    fun scrollTimeline(call: PluginCall) {
        val scroll = call.getDouble("scroll")
        if (scroll != null) {
            engine.scrollLeft = scroll
            call.resolve()
        } else {
            call.reject("scroll missing")
        }
    }

    @PluginMethod
    fun seek(call: PluginCall) {
        val time = call.getDouble("time")
        if (time != null) {
            engine.currentTime = time
            call.resolve()
        } else {
            call.reject("time missing")
        }
    }

    @PluginMethod
    fun undo(call: PluginCall) {
        engine.undo()
        val response = JSObject()
        response.put("canUndo", engine.canUndo())
        response.put("canRedo", engine.canRedo())
        call.resolve(response)
    }

    @PluginMethod
    fun redo(call: PluginCall) {
        engine.redo()
        val response = JSObject()
        response.put("canUndo", engine.canUndo())
        response.put("canRedo", engine.canRedo())
        call.resolve(response)
    }

    @PluginMethod
    fun clearTimeline(call: PluginCall) {
        engine.clear()
        call.resolve()
    }

    @PluginMethod
    fun timeToPixel(call: PluginCall) {
        val time = call.getDouble("time")
        if (time != null) {
            val p = engine.timeToPixel(time)
            val response = JSObject()
            response.put("pixel", p)
            call.resolve(response)
        } else {
            call.reject("time missing")
        }
    }

    @PluginMethod
    fun pixelToTime(call: PluginCall) {
        val pixel = call.getDouble("pixel")
        if (pixel != null) {
            val t = engine.pixelToTime(pixel)
            val response = JSObject()
            response.put("time", t)
            call.resolve(response)
        } else {
            call.reject("pixel missing")
        }
    }
}
