package com.litecut.app.timeline.tasks

import java.util.concurrent.CopyOnWriteArrayList

class CancellationToken {
    @Volatile
    private var isCancelled = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun cancel() {
        if (!isCancelled) {
            isCancelled = true
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {
                    // Ignore listener errors
                }
            }
        }
    }

    fun isCancelled(): Boolean = isCancelled

    fun register(onCancel: () -> Unit) {
        if (isCancelled) {
            onCancel()
        } else {
            listeners.add(onCancel)
        }
    }
}
