package app.remotehub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.remotehub.app.AndroidAppHost

class MainActivity : ComponentActivity() {
    private lateinit var host: AndroidAppHost

    /**
     * Android only lets an Activity ask for VPN consent. The controller raises the
     * request as a side effect; the result comes back here and is handed straight on.
     */
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        host.onVpnConsent(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        host = AndroidAppHost(this)
        host.requestVpnConsent = { intent -> runCatching { vpnConsent.launch(intent) } }
        host.setImmersive = { enabled -> immersive(enabled) }
        setContent { host.Content() }
    }

    /** Fullscreen is a view mode for the remote desktop, never a disconnect. */
    private fun immersive(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        host.resume()
    }

    override fun onStop() {
        host.pause()
        super.onStop()
    }

    override fun onDestroy() {
        host.release()
        super.onDestroy()
    }

    fun openExternal(url: String): Boolean {
        if (url.isBlank()) return false
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { startActivity(view); true }
            .recoverCatching { startActivity(Intent.createChooser(view, url)); true }
            .getOrElse {
                android.widget.Toast.makeText(this, url, android.widget.Toast.LENGTH_LONG).show()
                false
            }
    }
}
