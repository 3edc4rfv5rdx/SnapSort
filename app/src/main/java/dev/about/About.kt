package dev.about

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import dev.updater.UpdaterConfig

/**
 * Stub for the shared About dialog (normally compiled from ../about, a
 * sibling repo not available on this machine). Same call shape as the
 * real module, so dropping it in later needs no change at the call sites.
 */
data class AboutConfig(val updater: UpdaterConfig, val buildDate: String)

object About {
    fun show(activity: ComponentActivity, config: AboutConfig) {
        AlertDialog.Builder(activity)
            .setTitle(activity.applicationInfo.loadLabel(activity.packageManager))
            .setMessage("Version: ${versionName(activity)}\nBuild date: ${config.buildDate}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // From the installed package rather than a new AboutConfig field, so the
    // call site stays the shape the real module expects.
    private fun versionName(activity: ComponentActivity): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
}
