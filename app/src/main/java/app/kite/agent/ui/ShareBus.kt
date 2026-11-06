package app.kite.agent.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Text shared from other apps into Kite. */
object ShareBus {
    private val _text = MutableStateFlow<String?>(null)
    val text = _text.asStateFlow()

    fun post(value: String?) {
        if (!value.isNullOrBlank()) _text.value = value
    }

    fun clear() {
        _text.value = null
    }
}
