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
    val mediaControlEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val nowPlaying: String? = null,
    val history: List<String> = emptyList()
)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val music = MusicController(app)

    private val _state = MutableStateFlow(
        JarvisUiState(
            mediaControlEnabled = music.hasMediaAccess(),
            accessibilityEnabled = music.hasAccessibilityAccess()
        )
    )
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    fun refreshPermissions() {
        _state.value = _state.value.copy(
            mediaControlEnabled = music.hasMediaAccess(),
            accessibilityEnabled = music.hasAccessibilityAccess()
        )
    }

    fun requestMediaAccess() {
        music.openMediaAccessSettings()
    }

    fun requestAccessibilityAccess() {
        music.openAccessibilitySettings()
    }

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
                    message = "Procurando e tentando tocar “${parsed.query}”..."
                )

                music.playOnSpotify(parsed.query) { result ->
                    when (result) {
                        SpotifyPlayResult.DirectPlaybackConfirmed -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ONLINE",
                                nowPlaying = parsed.query,
                                mediaControlEnabled = true,
                                message = "Spotify confirmou a reprodução."
                            )
                        }

                        SpotifyPlayResult.AutomationStarted -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "EXECUTANDO",
                                nowPlaying = parsed.query,
                                accessibilityEnabled = true,
                                message = "O controle direto foi ignorado. Automação do Spotify ativada."
                            )
                        }

                        SpotifyPlayResult.SearchOpened -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ATENÇÃO",
                                nowPlaying = parsed.query,
                                message = "Abri a busca, mas não consegui confirmar a reprodução."
                            )
                        }

                        SpotifyPlayResult.NeedsMediaAccess -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ATENÇÃO",
                                mediaControlEnabled = false,
                                message = "Ative o Controle de Mídia para melhorar a reprodução direta."
                            )
                        }

                        SpotifyPlayResult.NeedsAccessibility -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ATENÇÃO",
                                accessibilityEnabled = false,
                                message = "Ative a Automação do Spotify. Ela é o fallback que toca a música quando o Spotify ignora o controle direto."
                            )
                        }
                    }
                }
            }

            is JarvisCommand.Unknown -> {
                _state.value = _state.value.copy(
                    status = "ONLINE",
                    message = "V0.4 entende frases como “abre o Spotify e toca Starboy”, “tocar Starboy” e “reproduzir Starboy”."
                )
            }
        }
    }
}
