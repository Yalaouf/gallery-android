package com.mephilia.gallery.ui.screens

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.GalleryViewModel

/** What unlocks the locked folder: a strong biometric (fingerprint, face) or the screen lock (PIN, pattern, password). */
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Hides the window's content from screenshots, screen recording and the recent apps screen, while shown. */
@Composable
fun SecureWindow() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Whether the phone can protect the locked folder: it has a screen lock or an enrolled biometric. */
internal fun canAuthenticate(context: Context) =
    context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Asks for the fingerprint, face or screen lock, and unlocks the locked folder in [vm] if it's given.
 * While the prompt is shown, [GalleryViewModel.authenticating] is set: the PIN screen hides the app,
 * which must not relock the folder.
 */
internal fun authenticate(context: Context, vm: GalleryViewModel) {
    vm.authenticating = true
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(context.getString(R.string.locked_title))
        .setSubtitle(context.getString(R.string.locked_prompt))
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.authenticating = false
                vm.unlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                vm.authenticating = false
            }
        },
    )
}
