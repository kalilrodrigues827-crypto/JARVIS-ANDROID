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
    val mediaControlEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val nowPlaying: String? = null,
    val history: List<String> = emptyList(),
    val updateStatus: String = "Versão ${BuildConfig.VERSION_NAME}",
    val updateAvailable: Boolean = false,
    val updateVersion: String? = null,
    val checkingUpdate: Boolean = false,
    val downloadingUpdate: Boolean = false
)

class JarvisViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val music = MusicController(app)
    private val appLauncher = AppLauncher(app)
    private val updater = UpdateManager(app)
    private var pendingUpdate: UpdateInfo? = null

    private val _state = MutableStateFlow(
        JarvisUiState(
            mediaControlEnabled = music.hasMediaAccess(),
            accessibilityEnabled = music.hasAccessibilityAccess()
        )
    )

    val state: StateFlow<JarvisUiState> =
        _state.asStateFlow()

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
        _state.value = _state.value.copy(
            commandText = text
        )
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

    fun submitCommand(
        raw: String = _state.value.commandText
    ) {
        val command = raw.trim()
        if (command.isBlank()) return

        _state.value = _state.value.copy(
            commandText = command,
            history = (
                listOf(command) + _state.value.history
                ).take(4)
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
                    message = "Tentando tocar “${parsed.query}”..."
                )

                music.playOnSpotify(parsed.query) { result ->
                    when (result) {
                        SpotifyPlayResult.PlaybackConfirmed -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ONLINE",
                                nowPlaying = parsed.query,
                                message = "Reprodução confirmada: ${parsed.query}."
                            )
                        }

                        SpotifyPlayResult.AutomationStarted -> {
                            _state.value = _state.value.copy(
                                status = "EXECUTANDO",
                                nowPlaying = parsed.query,
                                accessibilityEnabled = true,
                                message = "Spotify ignorou o comando direto. Usando automação..."
                            )
                        }

                        SpotifyPlayResult.AutomationCouldNotConfirm -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ATENÇÃO",
                                message = "A automação tentou tocar a música, mas não consegui confirmar a reprodução."
                            )
                        }

                        SpotifyPlayResult.NeedsAccessibility -> {
                            _state.value = _state.value.copy(
                                isBusy = false,
                                status = "ATENÇÃO",
                                accessibilityEnabled = false,
                                message = "Ative Jarvis Spotify Automation para permitir o clique automático."
                            )
                        }
                    }
                }
            }

            is JarvisCommand.OpenApp -> {
                val result = appLauncher.openApp(parsed.appName)

                _state.value = _state.value.copy(
                    status = if (result.opened) "ONLINE" else "ATENÇÃO",
                    message = if (result.opened) {
                        "Abrindo ${result.appName ?: parsed.appName}."
                    } else {
                        "Não encontrei o aplicativo ${parsed.appName}."
                    }
                )
            }

            is JarvisCommand.Unknown -> {
                _state.value = _state.value.copy(
                    status = "ONLINE",
                    message = "Ex.: “Jarvis, abre o Spotify e toca Starboy do The Weeknd”."
                )
            }
        }
    }

    fun checkForUpdate() {
        _state.value = _state.value.copy(
            checkingUpdate = true,
            updateStatus = "Procurando atualização..."
        )

        updater.checkForUpdate { result ->
            when (result) {
                is UpdateCheckResult.Available -> {
                    pendingUpdate = result.info

                    _state.value = _state.value.copy(
                        checkingUpdate = false,
                        updateAvailable = true,
                        updateVersion = result.info.versionName,
                        updateStatus = "JARVIS ${result.info.versionName} disponível"
                    )
                }

                UpdateCheckResult.UpToDate -> {
                    pendingUpdate = null

                    _state.value = _state.value.copy(
                        checkingUpdate = false,
                        updateAvailable = false,
                        updateVersion = null,
                        updateStatus = "Você está na versão mais recente"
                    )
                }

                is UpdateCheckResult.Error -> {
                    _state.value = _state.value.copy(
                        checkingUpdate = false,
                        updateStatus = "Erro: ${result.message}"
                    )
                }
            }
        }
    }

    fun installUpdate() {
        val info = pendingUpdate ?: run {
            checkForUpdate()
            return
        }

        if (!updater.canInstallPackages()) {
            updater.openInstallPermission()

            _state.value = _state.value.copy(
                updateStatus = "Autorize “Instalar apps desconhecidos” para o Jarvis e volte."
            )
            return
        }

        _state.value = _state.value.copy(
            downloadingUpdate = true,
            updateStatus = "Preparando atualização..."
        )

        updater.downloadAndInstall(
            info = info,
            onStatus = { text ->
                _state.value = _state.value.copy(
                    updateStatus = text
                )
            },
            onError = { error ->
                _state.value = _state.value.copy(
                    downloadingUpdate = false,
                    updateStatus = "Erro: $error"
                )
            }
        )
    }
}
