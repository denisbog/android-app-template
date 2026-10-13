package com.example.template.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Everything the app remembers between launches, in one place.
 *
 * A `SharedPreferences` file in the app's private storage is enough for settings; the point of
 * the class is that the *screen never touches storage itself* — it reads a snapshot from the
 * ViewModel and the ViewModel writes here, so state has exactly one owner. Swap the body for
 * DataStore/Room later without touching the UI.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("template", Context.MODE_PRIVATE)

    /** Free text typed in the sidebar — persisted so the demo survives a restart. */
    var displayName: String
        get() = prefs.getString(KEY_NAME, "")?.trim().orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    /** The selected [DemoSection]; unknown or missing values fall back to the default. */
    var section: DemoSection
        get() = prefs.getString(KEY_SECTION, null)
            ?.let { name -> DemoSection.entries.firstOrNull { it.name == name } }
            ?: DemoSection.Overview
        set(value) = prefs.edit().putString(KEY_SECTION, value.name).apply()

    /** Whether the demo's "dense rows" switch is on. */
    var denseRows: Boolean
        get() = prefs.getBoolean(KEY_DENSE, false)
        set(value) = prefs.edit().putBoolean(KEY_DENSE, value).apply()

    private companion object {
        const val KEY_NAME = "display_name"
        const val KEY_SECTION = "section"
        const val KEY_DENSE = "dense_rows"
    }
}

/** The three destinations the demo sidebar can select. */
enum class DemoSection(val label: String, val blurb: String) {
    Overview("Overview", "What the template gives you"),
    Sidebar("Sidebar", "Header, fields, ranges, rows, footer"),
    Gestures("Gestures", "Edge drag, scrim, back, wide screens"),
}

/**
 * Small helper for reading a persisted value into a `remember`ed state holder — handy for
 * screens that do not need a ViewModel. See `SettingsStore` for the real pattern.
 */
@Composable
fun <T> rememberPersisted(key: Any?, initial: () -> T): Pair<T, (T) -> Unit> {
    var value by remember(key) { mutableStateOf(initial()) }
    return value to { value = it }
}
