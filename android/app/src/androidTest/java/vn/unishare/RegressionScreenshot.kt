package vn.unishare

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun saveRegressionScreenshot(name: String) {
    val variant = InstrumentationRegistry.getArguments().getString("captureUi") ?: return
    require(variant in setOf("412-gesture", "360-three-button"))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(instrumentation.targetContext.cacheDir, "$name-$variant.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally {
        screenshot.recycle()
    }
}
