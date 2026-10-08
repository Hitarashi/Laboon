package org.shilpo.laboon.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper

/** Restore stock presentation on the next launch if a themed UI fails before recovery can be opened. */
internal object VisualThemeCrashRecovery {
    @SuppressLint("ApplySharedPref") // Persist recovery before delegating to a handler that terminates the process.
    fun install(context: Context) {
        val preferences = context.getSharedPreferences("visual_theme", Context.MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread == Looper.getMainLooper().thread && hasVisualThemeFailure(error)) {
                runCatching {
                    if (preferences.getString("selected_theme_id", null) != null) {
                        preferences.edit().remove("selected_theme_id")
                            .putBoolean("recovered_ui_failure", true).commit()
                    }
                }
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(1)
            }
        }
    }
}

internal fun hasVisualThemeFailure(error: Throwable): Boolean {
    val visited = HashSet<Throwable>()
    var current: Throwable? = error
    while (current != null && visited.size < 16 && visited.add(current)) {
        if (current.stackTrace.any { frame ->
                frame.className.startsWith("org.shilpo.laboon.theme.") ||
                        frame.className.startsWith("androidx.compose.") ||
                        frame.className == "android.graphics.RuntimeShader"
            }) return true
        current = current.cause
    }
    return false
}
