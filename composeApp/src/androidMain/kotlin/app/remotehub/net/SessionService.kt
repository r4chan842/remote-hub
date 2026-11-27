package app.remotehub.net

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.remotehub.MainActivity
import app.remotehub.R
import app.remotehub.RemoteHubApplication

class SessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: getString(R.string.session_running)
        val remaining = intent?.getStringExtra(EXTRA_REMAINING) ?: ""
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, RemoteHubApplication.SESSION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.session_running))
            .setContentText(if (remaining.isBlank()) label else "$label · $remaining")
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 41
        const val EXTRA_LABEL = "label"
        const val EXTRA_REMAINING = "remaining"
    }
}
