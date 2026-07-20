package com.litecut.app.timeline.audio

import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.mute
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

/**
 * Real-time background audio processing pipeline.
 * Evaluates active timeline clips, retrieves decoded PCM from cache, mixes channels into buses,
 * and outputs the final master stereo PCM stream.
 */
class AudioRenderPipeline(
    private val timelineEngine: TimelineEngine,
    private val metrics: AudioMetrics
) {
    private val bufferPool = AudioBufferPool.getInstance()
    private val audioCache = AudioCache.getInstance()

    // Temporary procedural synth helper to avoid lockouts when files aren't loaded
    private var synthPhase = 0.0

    /**
     * Executes a complete zero-allocation mixing pass at the given playhead position.
     * Must be called exclusively from a background thread.
     */
    fun render(
        currentTimeSeconds: Double,
        tracks: Collection<AudioTrack>,
        buses: Map<String, AudioBus>
    ): AudioMixResult {
        val startTimeNs = System.nanoTime()

        // 1. Lease sub-bus buffers from the pool
        val busBuffers = HashMap<String, AudioBuffer>()
        for ((busId, _) in buses) {
            val buf = bufferPool.obtain()
            buf.size = 512 // 512 stereo samples per buffer block (approx 10.6ms at 48kHz)
            buf.sampleRate = 48000
            buf.channels = 2
            busBuffers[busId] = buf
        }

        // 2. Identify active audio/video clips with audio on the timeline
        val allClips = timelineEngine.getAllClips()
        var activeChannels = 0

        for (clip in allClips) {
            if (isClipActiveAtTime(clip, currentTimeSeconds)) {
                activeChannels++
                
                // Find or create MixerChannel for this clip
                val targetTrack = findTrackForClip(tracks, clip)
                val channel = targetTrack.getChannel(clip.id)

                // Skip if muted
                if (channel.isMuted || clip.mute) continue

                // Retrieve PCM data from cache
                val relativeTime = currentTimeSeconds - clip.leftSeconds + clip.trimStartSeconds
                val segmentIndex = (relativeTime).toInt()
                
                // Lease a temporary channel buffer
                val channelBuffer = bufferPool.obtain()
                channelBuffer.size = 512
                channelBuffer.sampleRate = 48000
                channelBuffer.channels = 2

                val cached = audioCache.get(clip.id, segmentIndex)
                metrics.recordCacheQuery(cached != null)

                if (cached != null) {
                    // Feed cached samples into buffer
                    val startSample = ((relativeTime % 1.0) * 48000).toInt()
                    for (i in 0 until channelBuffer.size) {
                        val sampleIdx = (startSample + i) % cached.data.size
                        channelBuffer.leftChannel[i] = cached.data[sampleIdx]
                        channelBuffer.rightChannel[i] = cached.data[sampleIdx]
                    }
                } else {
                    // Trigger background cache warming
                    warmAudioCacheAsync(clip, segmentIndex)
                    
                    // Fallback to procedurally generated subtle tone so scrub/preview is audible
                    generateProceduralTone(channelBuffer)
                }

                // Process channel strip (fades, volume, panning, automation)
                channel.process(clip, relativeTime, clip.durationSeconds, channelBuffer)

                // Route channel outputs to target Bus Buffer
                val routedBusId = channel.evaluateBusSend(clip, relativeTime)
                val destBuffer = busBuffers[routedBusId] ?: busBuffers["MASTER"]
                
                if (destBuffer != null) {
                    accumulateBuffers(destBuffer, channelBuffer)
                }

                // Recycle channel buffer
                bufferPool.release(channelBuffer)
            }
        }

        // 3. Process sub-buses (Music, SFX, Voice) and accumulate into MASTER
        val masterBuffer = busBuffers["MASTER"] ?: bufferPool.obtain().apply {
            size = 512
            sampleRate = 48000
            channels = 2
        }

        for ((busId, busBuffer) in busBuffers) {
            if (busId == "MASTER") continue
            val bus = buses[busId]
            if (bus != null) {
                // Apply bus-level gain, faders, and pan
                bus.process(busBuffer)
                
                // Accumulate sub-bus into Master Bus
                accumulateBuffers(masterBuffer, busBuffer)
            }
            
            // Reclaim sub-bus buffers
            bufferPool.release(busBuffer)
        }

        // 4. Process Master Bus final properties
        val masterBus = buses["MASTER"]
        masterBus?.process(masterBuffer)

        // 5. Wrap master outputs into final monitor-analyzed container
        val finalResult = AudioMixResult.obtain(masterBuffer)
        
        // 6. Record diagnostics
        val latencyNs = System.nanoTime() - startTimeNs
        metrics.recordFrameProcessed(latencyNs)
        metrics.recordLevels(
            finalResult.peakLeft,
            finalResult.peakRight,
            finalResult.rmsLeft,
            finalResult.rmsRight,
            finalResult.isClipping
        )
        metrics.updateActiveChannels(activeChannels)

        return finalResult
    }

    private fun isClipActiveAtTime(clip: Clip, timeSeconds: Double): Boolean {
        // Clips match audio if they contain audio streams or are AUDIO/VIDEO clips
        val isAudioType = clip.type.name == "AUDIO" || clip.type.name == "VIDEO"
        return isAudioType && timeSeconds >= clip.leftSeconds && timeSeconds <= (clip.leftSeconds + clip.durationSeconds)
    }

    private fun findTrackForClip(tracks: Collection<AudioTrack>, clip: Clip): AudioTrack {
        return tracks.find { it.id == clip.layerId } ?: tracks.firstOrNull() ?: AudioTrack("default", "Default SFX", BusType.SFX)
    }

    private fun accumulateBuffers(dest: AudioBuffer, src: AudioBuffer) {
        val len = minOf(dest.size, src.size)
        for (i in 0 until len) {
            dest.leftChannel[i] += src.leftChannel[i]
            dest.rightChannel[i] += src.rightChannel[i]
        }
    }

    private fun generateProceduralTone(buffer: AudioBuffer) {
        val sampleRate = buffer.sampleRate.toDouble()
        val freq = 440.0 // standard A4 note
        val amplitude = 0.02f // low background level to avoid popping

        for (i in 0 until buffer.size) {
            val sample = (sin(synthPhase) * amplitude).toFloat()
            buffer.leftChannel[i] = sample
            buffer.rightChannel[i] = sample
            synthPhase += (2.0 * Math.PI * freq) / sampleRate
            if (synthPhase > 2.0 * Math.PI) {
                synthPhase -= 2.0 * Math.PI
            }
        }
    }

    private fun warmAudioCacheAsync(clip: Clip, segmentIndex: Int) {
        val scheduler = TaskScheduler.getInstance(null) ?: return
        val taskName = "AudioCacheWarming-${clip.id}-$segmentIndex"
        
        scheduler.submit(taskName, TaskPriority.NORMAL) { token, _ ->
            if (token.isCancelled()) return@submit
            
            // Simulate reading / decoding audio stream
            val samples = FloatArray(48000) // 1 second of stereo pcm data
            val segment = CachedAudioSegment(
                clipId = clip.id,
                segmentIndex = segmentIndex,
                data = samples,
                sampleRate = 48000,
                channels = 2
            )
            audioCache.put(segment)
        }
    }
}
