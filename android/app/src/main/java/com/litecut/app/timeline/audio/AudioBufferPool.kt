package com.litecut.app.timeline.audio

import com.litecut.app.timeline.resources.ManagedCache
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a reusable PCM float buffer container.
 * Wraps left and right channel arrays to represent stereo data.
 */
class AudioBuffer(val capacity: Int) {
    val leftChannel = FloatArray(capacity)
    val rightChannel = FloatArray(capacity)
    var size: Int = 0
    var channels: Int = 2
    var sampleRate: Int = 48000

    fun reset() {
        size = 0
        leftChannel.fill(0.0f)
        rightChannel.fill(0.0f)
    }

    /**
     * Copy PCM data from another buffer.
     */
    fun copyFrom(other: AudioBuffer) {
        this.size = other.size
        this.channels = other.channels
        this.sampleRate = other.sampleRate
        System.arraycopy(other.leftChannel, 0, this.leftChannel, 0, other.size)
        System.arraycopy(other.rightChannel, 0, this.rightChannel, 0, other.size)
    }
}

/**
 * Thread-safe object pool for reusable stereo AudioBuffers.
 * Implements ManagedCache to register with ResourceManager and release assets under memory pressure.
 */
class AudioBufferPool private constructor() : ManagedCache {
    
    override val categoryName: String = "audio_buffer_pool"
    
    private val bufferCapacity = 4096 // Handles up to 4096 samples per tick
    private val pool = ConcurrentLinkedQueue<AudioBuffer>()
    private val allocatedBytes = AtomicLong(0)

    companion object {
        @Volatile
        private var instance: AudioBufferPool? = null

        fun getInstance(): AudioBufferPool {
            return instance ?: synchronized(this) {
                instance ?: AudioBufferPool().also { instance = it }
            }
        }
    }

    /**
     * Leases a clean AudioBuffer from the pool.
     */
    fun obtain(): AudioBuffer {
        val buffer = pool.poll() ?: run {
            val newBuf = AudioBuffer(bufferCapacity)
            allocatedBytes.addAndGet(bufferCapacity * 4L * 2L) // Left + Right Float arrays (4 bytes per float)
            newBuf
        }
        buffer.reset()
        return buffer
    }

    /**
     * Releases a leased AudioBuffer back into the pool.
     */
    fun release(buffer: AudioBuffer) {
        buffer.reset()
        pool.offer(buffer)
    }

    override fun getCurrentSizeBytes(): Long {
        return allocatedBytes.get()
    }

    override fun trimMemory(bytesToFree: Long) {
        synchronized(this) {
            // Drop pooled idle buffers to reclaim memory
            var freedBytes = 0L
            while (!pool.isEmpty() && freedBytes < bytesToFree) {
                val buffer = pool.poll()
                if (buffer != null) {
                    val bytes = bufferCapacity * 4L * 2L
                    allocatedBytes.addAndGet(-bytes)
                    freedBytes += bytes
                }
            }
        }
    }

    override fun clear() {
        synchronized(this) {
            pool.clear()
            allocatedBytes.set(0)
        }
    }
}
