package app.remotehub.platform

import android.content.Context

object AndroidEnv {
    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
