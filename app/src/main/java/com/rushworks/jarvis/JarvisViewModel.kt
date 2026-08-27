package com.rushworks.jarvis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JarvisUiState(
    val status: String = "ONLINE",
    val message: String = "Pronto para receber seu comando.",
    val commandText: String = "",
    val isListening: Boolean = false,
    val isBusy: Boolean = false,
    val spotifyReady: Boolean = true,
    val nowPlaying: String? = null,
    val history: List<String> = emptyList()
)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val music = MusicController(app)

    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    fun setCommand(text: String) {
        _state.value = _state.value.copy(commandText = text)
    }

    fun setListening(value: Boolean) {
        _state.value = _state.value.copy(
            isListening = value,
            status = if (value) "OUVINDO" else "ONLINE"
        )
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(
            message = message,
            isBusy = false,
            status = "ATENÇÃO"
        )
    }

    fun submitCommand(raw: String = _state.value.commandText) {
        val command = raw.trim()
        if (command.isBlank()) return

        _state.value = _state.value.copy(
            commandText = command,
            history = (listOf(command) + _state.value.history).take(4)
        )

        when (val parsed = CommandParser.parse(command)) {
            is JarvisCommand.OpenSpotify -> {
                music.openSpotify()
                _state.value = _state.value.copy(
                    status = "ONLINE",
                    message = "Abrindo Spotify."
                )
            }

            is JarvisCommand.PlaySpotify -> {
                _state.value = _state.value.copy(
                    isBusy = true,
                    status = "EXECUTANDO",
                    message = "Pedindo ao Spotify para tocar “${parsed.query}”..."
                )

                music.playOnSpotify(parsed.query)
                    .onSuccess { autoplayRequested ->
                        _state.value = _state.value.copy(
                            isBusy = false,
                            status = "ONLINE",
                            nowPlaying = parsed.query,
                            message = if (autoplayRequested) {
                                "Comando enviado ao Spotify."
                            } else {
                                "Abri a busca no Spotify. O autoplay não estava disponível neste aparelho."
                            }
                        )
                    }
                    .onFailure {
                        showError(it.message ?: "Não consegui abrir o Spotify.")
                    }
            }

            is JarvisCommand.Unknown -> {
                _state.value = _state.value.copy(
                    status = "ONLINE",
                    message = "Minha V1 entende Spotify. Ex.: “Jarvis, toque Starboy”."
                )
            }
        }
    }
}
