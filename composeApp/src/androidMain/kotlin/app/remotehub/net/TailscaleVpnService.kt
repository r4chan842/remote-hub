package app.remotehub.net

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.remotehub.MainActivity
import app.remotehub.R
import app.remotehub.RemoteHubApplication
import app.remotehub.diag.Diagnostics

/**
 * Turns the tailnet into a device-wide tunnel.
 *
 * Android hands out one VPN interface. This service owns it, and the descriptor is
 * adopted by the Go node through tsnet.Server.Tun (available since tailscale v1.102),
 * so every app on the phone can reach 100.64.0.0/10 — aRDP, Remote Pane, a browser,
 * anything. Only the tailnet ranges are routed; the rest of the traffic is untouched.
 *
 * RemoteHub itself is excluded from the interface. Its WireGuard sockets must not be
 * routed back into the tunnel, and its own RDP client already reaches the tailnet
 * through netstack, so excluding the package removes any chance of a loop.
 *
 * The interface is declared systemExempted, the same foreground service type the
 * official Tailscale Android client uses for its VPN service.
 */
class TailscaleVpnService : VpnService() {

    interface Host {
        fun onTunReady(fd: Int, interfaceName: String)
        fun onTunFailed(reason: String)
        fun onTunRevoked()
    }

    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Diagnostics.info("vpn", "stop requested")
            closeTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val ipv4 = intent?.getStringExtra(EXTRA_IPV4).orEmpty()
        if (ipv4.isBlank()) {
            host?.onTunFailed("the tailnet address is not known yet")
            stopSelf()
            return START_NOT_STICKY
        }
        val ipv6 = intent?.getStringExtra(EXTRA_IPV6).orEmpty()
        val domain = intent?.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()

        startForeground(NOTIFICATION_ID, notification(label.ifBlank { ipv4 }))

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(ipv4, 32)
            .addRoute(CGNAT_V4, 10)
        if (ipv6.isNotBlank()) {
            runCatching {
                builder.addAddress(ipv6, 128)
                builder.addRoute(CGNAT_V6, 48)
            }.onFailure { Diagnostics.warn("vpn", "ipv6 route skipped", it) }
        }
        // MagicDNS resolves the tailnet names; everything else keeps the system resolver.
        runCatching {
            builder.addDnsServer(MAGIC_DNS)
            if (domain.isNotBlank()) builder.addSearchDomain(domain)
        }.onFailure { Diagnostics.warn("vpn", "dns setup skipped", it) }
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Diagnostics.warn("vpn", "could not exclude own package", it) }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            runCatching { builder.setMetered(false) }
        }

        val descriptor = runCatching { builder.establish() }.getOrElse { failure ->
            Diagnostics.error("vpn", "establish() threw", failure)
            null
        }
        if (descriptor == null) {
            // establish() returns null when consent was revoked or another VPN owns
            // the slot. Android allows exactly one VPN at a time.
            val reason = "Android refused the VPN interface: another VPN may be active, or permission was revoked"
            Diagnostics.error("vpn", reason)
            host?.onTunFailed(reason)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        tunnel = descriptor
        // detachFd hands the raw descriptor to Go and leaves the Java wrapper inert,
        // so a later close() on this side cannot take the interface down under it.
        val fd = descriptor.detachFd()
        Diagnostics.info("vpn", "interface established fd=$fd address=$ipv4 mtu=$MTU")
        host?.onTunReady(fd, "tun")
        return START_STICKY
    }

    override fun onRevoke() {
        Diagnostics.warn("vpn", "the system revoked the VPN")
        host?.onTunRevoked()
        closeTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }

    /**
     * The Go node closes the descriptor when it stops. This only releases the Java
     * wrapper if the node never took it, so the interface cannot leak.
     */
    private fun closeTunnel() {
        val current = tunnel ?: return
        tunnel = null
        runCatching { current.close() }
    }

    private fun notification(address: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TailscaleVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, RemoteHubApplication.SESSION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(address)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.vpn_notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 43
        const val ACTION_STOP = "app.remotehub.vpn.STOP"
        const val EXTRA_IPV4 = "ipv4"
        const val EXTRA_IPV6 = "ipv6"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_LABEL = "label"

        // Tailscale's default interface MTU. Larger values fragment inside DERP.
        const val MTU = 1280
        const val CGNAT_V4 = "100.64.0.0"
        const val CGNAT_V6 = "fd7a:115c:a1e0::"
        const val MAGIC_DNS = "100.100.100.100"

        @Volatile
        var host: Host? = null

        /** Null when consent is already granted; otherwise the system consent intent. */
        fun consentIntent(context: android.content.Context): Intent? =
            runCatching { VpnService.prepare(context) }.getOrNull()
    }
}
