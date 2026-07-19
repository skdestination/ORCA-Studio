package com.litecut.app.timeline.tasks

sealed class TaskResult<out T> {
    data class Success<out T>(val value: T) : TaskResult<T>()
    data class Failure(val throwable: Throwable) : TaskResult<Nothing>()
}
