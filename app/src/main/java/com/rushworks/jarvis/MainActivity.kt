                
                package com.rushworks.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JarvisApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun JarvisApp(
    viewModel: JarvisViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val core = remember {
        JarvisCore()
    }

    val coreState by core.state.collectAsStateWithLifecycle()

    var recognizer by remember {
        mutableStateOf<VoiceRecognizer?>(null)
    }

    var speaker by remember {
        mutableStateOf<JarvisSpeaker?>(null)
    }

    var capturingName by remember {
        mutableStateOf(false)
    }

    var onboardingStarted by remember {
        mutableStateOf(false)
    }

    var showSystemPanel by remember {
        mutableStateOf(false)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                core.setListening()
                recognizer?.start()
            } else {
                core.setError(
                    "Permissão de microfone necessária."
                )

                viewModel.showError(
                    "Preciso da permissão do microfone."
                )
            }
        }

    LaunchedEffect(Unit) {

        speaker = JarvisSpeaker(context)

        recognizer = VoiceRecognizer(
            context = context,

            onResult = { text ->

                if (capturingName) {

                    capturingName = false

                    val name = text.trim()

                    viewModel.saveUserName(name)

                    core.setSpeaking(
                        "Muito prazer, $name. JARVIS online."
                    )

                    speaker?.speak(
                        "Muito prazer, $name. JARVIS online."
                    )

                    return@VoiceRecognizer
                }

                when (
                    core.interpretSessionIntent(text)
                ) {

                    JarvisSessionIntent.WAKE -> {

                        core.wake()

                        speaker?.speak(
                            "Olá, senhor. Como posso ajudá-lo?"
                        )
                    }

                    JarvisSessionIntent.SLEEP -> {

                        core.setSpeaking(
                            "Até logo, senhor."
                        )

                        speaker?.speak(
                            "Até logo, senhor."
                        ) {
                            core.sleep()
                        }
                    }

                    JarvisSessionIntent.NONE -> {

                        core.setThinking()

                        viewModel.setCommand(text)

                        viewModel.submitCommand(text)
                    }
                }
            },

            onListening = { listening ->

                viewModel.setListening(listening)

                if (listening) {
                    core.setListening()
                } else if (!state.isBusy) {
                    core.setOnline()
                }
            },

            onError = { error ->

                core.setError(error)

                viewModel.showError(error)
            }
        )
    }

    LaunchedEffect(
        state.isBusy,
        state.isListening
    ) {

        when {

            state.isListening -> {
                core.setListening()
            }

            state.isBusy -> {
                core.setExecuting(
                    "Executando solicitação."
                )
            }

            coreState.mode != JarvisMode.SLEEPING &&
                coreState.mode != JarvisMode.ERROR -> {

                core.setOnline(
                    state.message
                )
            }
        }
    }

    LaunchedEffect(
        state.needsNameSetup,
        recognizer,
        speaker
    ) {

        if (
            state.needsNameSetup &&
            recognizer != null &&
            speaker != null &&
            !onboardingStarted
        ) {

            onboardingStarted = true

            core.setSpeaking(
                "Qual é o seu nome, senhor?"
            )

            speaker?.speak(
                "Qual é o seu nome, senhor?"
            ) {

                capturingName = true

                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {

                    core.setListening()

                    recognizer?.start()

                } else {

                   
                    base * 1.32f,
                    base * 1.32f
                ),
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Round
                )
            )
        }

        Box(
            Modifier
                .size(
                    (104 * pulse).dp
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFB8EEFF),
                            Color(0xFF438DE7),
                            Color(0xFF182B56)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color(0xFFBEEFFF),
                    CircleShape
                ),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                if (listening) {
                    "●●●"
                } else {
                    "J"
                },
                fontSize =
                    if (listening) {
                        14.sp
                    } else {
                        38.sp
                    },
                fontWeight =
                    FontWeight.Black,
                color = Color.White,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun CommandBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    listening: Boolean,
    busy: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp)
            )
            .background(
                Color(0xB30B1118)
            )
            .border(
                1.dp,
                Color(0x334E89A9),
                RoundedCornerShape(24.dp)
            )
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Ex.: abre o Spotify e toca Starboy",
                    color =
                        Color(0xFF617786)
                )
            },
            singleLine = true,
            shape =
                RoundedCornerShape(16.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                        Color(0xFF5DC9FF),
                    unfocusedBorderColor =
                        Color(0x223AA9DA),
                    focusedContainerColor =
                        Color(0x4410202C),
                    unfocusedContainerColor =
                        Color(0x3310202C)
                )
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onMic,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape =
                    RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (listening) {
                                Color(0xFF175A72)
                            } else {
                                Color(0xFF10283A)
                            }
                    )
            ) {
                Text(
                    if (listening) {
                        "OUVINDO…"
                    } else {
                        "🎙  FALAR"
                    },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onSend,
                enabled =
                    value.isNotBlank() &&
                        !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape =
                    RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF1F6A97)
                    )
            ) {
                Text(
                    if (busy) {
                        "PROCESSANDO"
                    } else {
                        "EXECUTAR"
                    },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SpotifyPanel(
    state: JarvisUiState,
    vm: JarvisViewModel
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(22.dp)
            )
            .background(
                Color(0xA80C1217)
            )
            .border(
                1.dp,
                Color(0x2735DF8B),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF1ED760)
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    "♪",
                    color = Color.Black,
                    fontWeight =
                        FontWeight.Black,
                    fontSize = 21.sp
                )
            }

            Spacer(Modifier.width(11.dp))

            Column {
                Text(
                    "SPOTIFY HYBRID CONTROL",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp
                )

                Text(
                    "Mídia: ${
                        if (
                            state.mediaControlEnabled
                        ) "ON" else "OFF"
                    } • Automação: ${
                        if (
                            state.accessibilityEnabled
                        ) "ON" else "OFF"
                    }",
                    color =
                        if (
                            state.accessibilityEnabled
                        ) {
                            Color(0xFF6DE6A3)
                        } else {
                            Color(0xFFFFC56D)
                        },
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!state.mediaControlEnabled) {
            OutlinedButton(
                onClick =
                    vm::requestMediaAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape =
                    RoundedCornerShape(14.dp)
            ) {
                Text(
                    "ATIVAR CONTROLE DE MÍDIA",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        if (!state.accessibilityEnabled) {
            Button(
                onClick =
                    vm::requestAccessibilityAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape =
                    RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF176B48)
                    )
            ) {
                Text(
                    "ATIVAR AUTOMAÇÃO DO SPOTIFY",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Ative “Jarvis Spotify Automation” na Acessibilidade. O serviço é limitado ao Spotify.",
                color = Color(0xFF7F99AA),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        } else {
            Button(
                onClick = {
                    vm.submitCommand(
                        "abre o Spotify e toca Starboy do The Weeknd"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape =
                    RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF176B48)
                    )
            ) {
                Text(
                    "TESTAR REPRODUÇÃO COMPLETA",
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        if (state.nowPlaying != null) {
            Spacer(Modifier.height(13.dp))

            HorizontalDivider(
                color = Color(0x1FFFFFFF)
            )

            Spacer(Modifier.height(13.dp))

            Text(
                "ÚLTIMO PEDIDO",
                fontSize = 9.sp,
                color = Color(0xFF687D8A),
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                state.nowPlaying,
                fontSize = 14.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UpdatePanel(
    state: JarvisUiState,
    vm: JarvisViewModel
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(22.dp)
            )
            .background(
                Color(0xA80C1217)
            )
            .border(
                1.dp,
                Color(0x334987FF),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            "JARVIS UPDATE SYSTEM",
            fontWeight =
                FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp
        )

        Spacer(Modifier.height(5.dp))

        Text(
            state.updateStatus,
            color =
                if (state.updateAvailable) {
                    Color(0xFF7BE9B2)
                } else {
                    Color(0xFF8298A8)
                },
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(12.dp))

        if (state.updateAvailable) {
            Button(
                onClick =
                    vm::installUpdate,
                enabled =
                    !state.downloadingUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape =
                    RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF295AA7)
                    )
            ) {
                Text(
                    if (
                        state.downloadingUpdate
                    ) {
                        "BAIXANDO..."
                    } else {
                        "ATUALIZAR AGORA"
                    },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        } else {
            OutlinedButton(
                onClick =
                    vm::checkForUpdate,
                enabled =
                    !state.checkingUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape =
                    RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (
                        state.checkingUpdate
                    ) {
                        "VERIFICANDO..."
                    } else {
                        "VERIFICAR ATUALIZAÇÃO"
                    },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<String>,
    onTap: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
    ) {
        Text(
            "COMANDOS RECENTES",
            fontSize = 10.sp,
            color = Color(0xFF6B8292),
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(8.dp))

        history.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        Color(0x55111920)
                    )
                    .clickable {
                        onTap(item)
                    }
                    .padding(13.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    "›",
                    color =
                        Color(0xFF68D4FF),
                    fontSize = 20.sp
                )

                Spacer(Modifier.width(9.dp))

                Text(
                    item,
                    color =
                        Color(0xFFB9C9D3),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}
