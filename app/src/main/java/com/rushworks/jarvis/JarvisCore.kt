package com.rushworks.jarvis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JarvisMode {
    SLEEPING,
    ONLINE,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING,
    ERROR
}

data class JarvisCoreState(
    val mode: JarvisMode = JarvisMode.ONLINE,
    val active: Boolean = true,
    val message: String = "JARVIS online.",
    val lastInteraction: String = ""
)

enum class JarvisSessionIntent {
    WAKE,
    SLEEP,
    NONE
}

class JarvisCore {

    private val _state = MutableStateFlow(
        JarvisCoreState()
    )

    val state: StateFlow<JarvisCoreState> =
        _state.asStateFlow()

    fun wake() {
        _state.value = _state.value.copy(
            mode = JarvisMode.ONLINE,
            active = true,
            message = "Olá, senhor. Como posso ajudá-lo?"
        )
    }

    fun sleep() {
        _state.value = _state.value.copy(
            mode = JarvisMode.SLEEPING,
            active = false,
            message = "Entrando em espera."
        )
    }

    fun setListening() {
        _state.value = _state.value.copy(
            mode = JarvisMode.LISTENING,
            active = true,
            message = "Estou ouvindo."
        )
    }

    fun setThinking() {
        _state.value = _state.value.copy(
            mode = JarvisMode.THINKING,
            active = true,
            message = "Processando."
        )
    }

    fun setSpeaking(message: String) {
        _state.value = _state.value.copy(
            mode = JarvisMode.SPEAKING,
            active = true,
            message = message,
            lastInteraction = message
        )
    }

    fun setExecuting(message: String) {
        _state.value = _state.value.copy(
            mode = JarvisMode.EXECUTING,
            active = true,
            message = message,
            lastInteraction = message
        )
    }

    fun setOnline(message: String = "JARVIS online.") {
        _state.value = _state.value.copy(
            mode = JarvisMode.ONLINE,
            active = true,
            message = message,
            lastInteraction = message
        )
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(
            mode = JarvisMode.ERROR,
            active = true,
            message = message,
            lastInteraction = message
        )
    }

    fun interpretSessionIntent(
        text: String
    ): JarvisSessionIntent {

        val normalized = text
            .lowercase()
            .trim()

        val wakePhrases = listOf(
            "jarvis",
            "acorda jarvis",
            "jarvis acorda",
            "ei jarvis",
            "fala jarvis"
        )

        val sleepPhrases = listOf(
            "pode se retirar",
            "pode sair",
            "pode desligar",
            "se desliga",
            "desliga jarvis",
            "valeu jarvis",
            "obrigado jarvis",
            "já pode sair",
            "já pode se retirar",
            "koe já pode sair",
            "koe pode sair",
            "pode meter o pé"
        )

        if (
            wakePhrases.any {
                normalized == it
            }
        ) {
            return JarvisSessionIntent.WAKE
        }

        if (
            sleepPhrases.any {
                normalized.contains(it)
            }
        ) {
            return JarvisSessionIntent.SLEEP
        }

        return JarvisSessionIntent.NONE
    }
}
