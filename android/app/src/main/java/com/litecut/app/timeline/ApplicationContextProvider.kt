package com.litecut.app.timeline

import android.content.Context

object ApplicationContextProvider {
    @Volatile
    var context: Context? = null
}
