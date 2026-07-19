package com.litecut.app.timeline

interface PlaybackSyncListener {
    /**
     * Fired when the playback state shifts (e.g. from PAUSED to PLAYING).
     */
    fun onStateChanged(newState: PlaybackState)

    /**
     * Fired when the high-precision playback clock updates its time (frame accurate).
     */
    fun onTimeUpdated(seconds: Double, isScrubbing: Boolean)

    /**
     * Fired when a seek request starts.
     */
    fun onSeekStarted(targetSeconds: Double)

    /**
     * Fired when the seek is complete and the frame has been updated.
     */
    fun onSeekCompleted(actualSeconds: Double)

    /**
     * Fired when the synchronizer experiences a frame drop event.
     */
    fun onFrameDropped(targetTime: Double, actualTime: Double)

    /**
     * Fired when buffering state changes.
     */
    fun onBufferingChanged(isBuffering: Boolean)
}

open class SimplePlaybackSyncListener : PlaybackSyncListener {
    override fun onStateChanged(newState: PlaybackState) {}
    override fun onTimeUpdated(seconds: Double, isScrubbing: Boolean) {}
    override fun onSeekStarted(targetSeconds: Double) {}
    override fun onSeekCompleted(actualSeconds: Double) {}
    override fun onFrameDropped(targetTime: Double, actualTime: Double) {}
    override fun onBufferingChanged(isBuffering: Boolean) {}
}
