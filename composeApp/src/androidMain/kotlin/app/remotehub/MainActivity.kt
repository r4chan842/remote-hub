package app.remotehub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.remotehub.app.AndroidAppHost

class MainActivity : ComponentActivity() {
    private lateinit var host: AndroidAppHost

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        host = AndroidAppHost(this)
        setContent { host.Content() }
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
