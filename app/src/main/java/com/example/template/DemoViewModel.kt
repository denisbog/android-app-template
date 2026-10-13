package com.example.template

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.data.DemoSection
import com.example.template.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DemoUiState(
    val displayName: String = "",
    val section: DemoSection = DemoSection.Overview,
    val denseRows: Boolean = false,
    /** Transient one-shot message for the snackbar, cleared by [consumeMessage]. */
    val message: String? = null,
    val busy: Boolean = false,
    val saveCount: Int = 0,
)

/**
 * The screen's state holder. It owns nothing but the state: reading [SettingsStore] once on
 * construction and writing to it whenever a value changes, so a restart lands back where the
 * user left off. Every value the UI shows is in [DemoUiState] — the composables stay
 * stateless and therefore previewable.
 */
class DemoViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(
        DemoUiState(
            displayName = settings.displayName,
            section = settings.section,
            denseRows = settings.denseRows,
        ),
    )
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value) }

    fun selectSection(section: DemoSection) {
        settings.section = section
        _state.update { it.copy(section = section) }
    }

    /** Persist what is typed in the sidebar — the demo's stand-in for "Save". */
    fun save() {
        val s = _state.value
        settings.displayName = s.displayName
        settings.section = s.section
        settings.denseRows = s.denseRows
        _state.update { it.copy(saveCount = it.saveCount + 1, message = "Saved “${s.displayName}”") }
    }

    fun clear() {
        settings.displayName = ""
        _state.update { it.copy(displayName = "", message = "Cleared") }
    }

    fun setDenseRows(enabled: Boolean) {
        settings.denseRows = enabled
        _state.update { it.copy(denseRows = enabled) }
    }

    /** Shows how to do a suspend action from a sidebar row: a busy flag plus a snackbar. */
    fun simulateWork() = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, message = "Working…") }
        delay(1_200)
        _state.update { it.copy(busy = false, message = "Done — nothing was really loaded 🙂") }
    }

    fun reset() {
        settings.displayName = ""
        settings.section = DemoSection.Overview
        settings.denseRows = false
        _state.update {
            it.copy(
                displayName = "",
                section = DemoSection.Overview,
                denseRows = false,
                message = "Reset to defaults",
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
