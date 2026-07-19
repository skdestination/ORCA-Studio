package com.litecut.app.timeline

enum class SnapPriority(val rank: Int) {
    NONE(0),
    FUTURE_BEAT(1),
    TIMELINE_EDGE(2),
    KEYFRAME(3),
    MARKER(4),
    CLIP_EDGE(5),
    PLAYHEAD(6);

    companion object {
        fun fromType(type: SnapTargetType): SnapPriority {
            return when (type) {
                SnapTargetType.PLAYHEAD -> PLAYHEAD
                SnapTargetType.CLIP_START, SnapTargetType.CLIP_END -> CLIP_EDGE
                SnapTargetType.MARKER -> MARKER
                SnapTargetType.KEYFRAME -> KEYFRAME
                SnapTargetType.TIMELINE_START, SnapTargetType.TIMELINE_END -> TIMELINE_EDGE
                SnapTargetType.BEAT_MARKER -> FUTURE_BEAT
                SnapTargetType.SUBTITLE -> FUTURE_BEAT
            }
        }
    }
}
