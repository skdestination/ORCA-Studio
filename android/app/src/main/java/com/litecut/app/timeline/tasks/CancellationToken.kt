package com.litecut.app.timeline.tasks

import java.util.concurrent.CopyOnWriteArrayList

class CancellationToken {
    @Volatile
    private var cancelled = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {
                    // Ignore listener errors
                }
            }
        }
    }

    fun isCancelled(): Boolean = cancelled

    fun register(onCancel: () -> Unit) {
        if (cancelled) {
            onCancel()
        } else {
            listeners.add(onCancel)
        }
    }
}
