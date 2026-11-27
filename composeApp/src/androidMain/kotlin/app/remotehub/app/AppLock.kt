package app.remotehub.app

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import app.remotehub.i18n.AppStrings

object AppLock {
    fun supported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
        return manager?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
    }

    fun deviceSecure(context: Context): Boolean {
        val manager = context.getSystemService(KeyguardManager::class.java)
        return manager?.isDeviceSecure == true
    }

    fun prompt(activity: ComponentActivity, strings: AppStrings, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 28) {
            onResult(deviceSecure(activity))
            return
        }
        val executor = activity.mainExecutor
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                onResult(false)
            }
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(strings.appName)
            .setSubtitle(strings.biometricLock)
            .setNegativeButton(strings.cancel, executor) { _, _ -> onResult(false) }
            .build()
        prompt.authenticate(CancellationSignal(), executor, callback)
    }
}
