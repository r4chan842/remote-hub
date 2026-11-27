package app.remotehub

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.remotehub.platform.AndroidEnv

class RemoteHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidEnv.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            SESSION_CHANNEL,
            getString(R.string.session_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
        val alerts = NotificationChannel(
            ALERT_CHANNEL,
            getString(R.string.expiry_title),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(alerts)
    }

    companion object {
        const val SESSION_CHANNEL = "remotehub.session"
        const val ALERT_CHANNEL = "remotehub.alerts"
    }
}
