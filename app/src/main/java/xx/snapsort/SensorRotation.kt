package xx.snapsort

import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The device's physical tilt in 90-degree steps (0/90/180/270), straight off
 * the accelerometer — not the system's "auto-rotate screen" setting, which
 * this app ignores on purpose: the activity is locked portrait, so a photo
 * held sideways would otherwise never turn to fill the screen. Snapping with
 * a dead zone around each quarter-turn is the same rule a camera preview
 * uses for its own on-screen controls, so the photo does not flicker between
 * two rotations while the phone sits near a boundary.
 */
@Composable
fun rememberDeviceRotation(): Int {
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                rotation = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation < 135 -> 270
                    orientation < 225 -> 180
                    else -> 90
                }
            }
        }
        // Without a working accelerometer (common on an emulator with no
        // virtual sensors configured) this fires with meaningless values
        // instead of just not firing — trust it only once it says it can.
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}
