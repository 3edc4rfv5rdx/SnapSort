package dev.updater

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Stub for the shared in-app updater (normally compiled from ../updater,
 * a sibling repo not available on this machine). Same call shape as the
 * real module, so dropping it in later needs no change at the call sites.
 */
data class UpdaterConfig(val appKey: String, val repo: String)

object Updater {
    private const val PREFS = "updater_stub"
    private const val KEY_ENABLED = "enabled"

    fun checkOnStart(activity: ComponentActivity, config: UpdaterConfig) {
        // No-op: the real module would fetch the latest GitHub release here.
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
