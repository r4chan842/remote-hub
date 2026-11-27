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

    fun openExternal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
