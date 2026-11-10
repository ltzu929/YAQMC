package org.yaqmc.android.nativeui

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Only the offline UI suite uses this runner; production and live tests keep YaqmcApplication. */
class NativeOfflineTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
