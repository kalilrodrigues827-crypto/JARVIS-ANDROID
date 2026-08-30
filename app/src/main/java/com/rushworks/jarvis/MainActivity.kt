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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
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
    val core = remember { JarvisCore() }
    val coreState by core.state.collectAsStateWithLifecycle()

    var recognizer by remember {
        mutableStateOf<VoiceRecognizer?>(null)
    }

    var speaker by remember {
        mutableStateOf<JarvisSpeaker?>(null)
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
            }
        }

    LaunchedEffect(Unit) {

        speaker = JarvisSpeaker(context)

        recognizer = VoiceRecognizer(
            context = context,

            onResult = { text ->

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

                        core.sleep()

                        speaker?.speak(
                            "Até logo, senhor."
                        )
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
                }
            },

            onError = { error ->

                core.setError(error)

                viewModel.showError(error)
            }
        )
    }

    LaunchedEffect(
        state.message,
        state.isBusy,
        state.isListening
    ) {

        when {

            state.isListening -> {
                core.setListening()
            }

            state.isBusy -> {
                core.setExecuting(
                    state.message
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

    DisposableEffect(Unit) {

        onDispose {
            recognizer?.destroy()
            speaker?.shutdown()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF02070B),
            surface = Color(0xFF07131A),
            primary = Color(0xFF42DFFF),
            onBackground = Color.White
        )
    ) {

        JarvisScreen(
            mode = coreState.mode,
            message = coreState.message,
            onMicClick = {

                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                ) {

                    core.setListening()

                    recognizer?.start()

                } else {

                    permissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }
        )
    }
}

@Composable
private fun JarvisScreen(
    mode: JarvisMode,
    message: String,
    onMicClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF062238),
                        Color(0xFF03121C),
                        Color(0xFF010407)
                    ),
                    radius = 1400f
                )
            )
            .padding(
                horizontal = 24.dp,
                vertical = 26.dp
            )
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            TopBar(
                online =
                    mode != JarvisMode.SLEEPING
            )

            Spacer(
                Modifier.height(28.dp)
            )

            Text(
                text = "J A R V I S",
                color = Color(0xFF61E7FF),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp
            )

            Spacer(
                Modifier.height(5.dp)
            )

            Text(
                text = "AI ASSISTANT",
                color = Color(0xFF557988),
                fontSize = 10.sp,
                letterSpacing = 3.sp
            )

            Spacer(
                Modifier.weight(0.6f)
            )

            JarvisOrb(
                mode = mode
            )

            Spacer(
                Modifier.height(28.dp)
            )

            Text(
                text = modeLabel(mode),
                color = Color(0xFF55DFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text = message,
                color = Color(0xFFA8C1CC),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp
                    )
            )

            Spacer(
                Modifier.weight(1f)
            )

            MicButton(
                listening =
                    mode == JarvisMode.LISTENING,
                onClick = onMicClick
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                text =
                    if (
                        mode == JarvisMode.LISTENING
                    ) {
                        "OUVINDO..."
                    } else {
                        "TOQUE PARA FALAR"
                    },
                color = Color(0xFF557582),
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )

            Spacer(
                Modifier.height(26.dp)
            )

            Text(
                text =
                    "JARVIS CORE // V${BuildConfig.VERSION_NAME}",
                color = Color(0xFF29424D),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun TopBar(
    online: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = "☰",
            color = Color(0xFF55DFFF),
            fontSize = 25.sp
        )

        Row(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(50)
                )
                .background(
                    Color(0xAA07151D)
                )
                .border(
                    1.dp,
                    Color(0x334DDEFF),
                    RoundedCornerShape(50)
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 7.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (online) {
                            Color(0xFF40EFA5)
                        } else {
                            Color(0xFF66767D)
                        },
                        CircleShape
                    )
            )

            Spacer(
                Modifier.width(7.dp)
            )

            Text(
                text =
                    if (online) {
                        "Online"
                    } else {
                        "Standby"
                    },
                color = Color(0xFFA6D5C6),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun JarvisOrb(
    mode: JarvisMode
) {

    val transition =
        rememberInfiniteTransition(
            label = "jarvis"
        )

    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(4500),
                    repeatMode =
                        RepeatMode.Restart
                ),
            label = "rotation"
        )

    val pulse by
        transition.animateFloat(
            initialValue = 0.94f,
            targetValue =
                if (
                    mode == JarvisMode.LISTENING
                ) {
                    1.10f
                } else {
                    1.02f
                },
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(750),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "pulse"
        )

    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment =
            Alignment.Center
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val center =
                Offset(
                    size.width / 2f,
                    size.height / 2f
                )

            val radius =
                size.minDimension / 2f

            drawCircle(
                color = Color(0x183EDFFF),
                radius = radius * 0.90f,
                center = center,
                style =
                    Stroke(
                        width = 2f
                    )
            )

            drawCircle(
                color = Color(0x3044E4FF),
                radius = radius * 0.70f,
                center = center,
                style =
                    Stroke(
                        width = 2f
                    )
            )

            drawArc(
                color = Color(0xFF62E7FF),
                startAngle = rotation,
                sweepAngle = 82f,
                useCenter = false,
                topLeft =
                    Offset(
                        radius * 0.10f,
                        radius * 0.10f
                    ),
                size =
                    Size(
                        radius * 1.80f,
                        radius * 1.80f
                    ),
                style =
                    Stroke(
                        width = 5f,
                        cap =
                            StrokeCap.Round
                    )
            )

            drawArc(
                color = Color(0x8046BFFF),
                startAngle = -rotation,
                sweepAngle = 120f,
                useCenter = false,
                topLeft =
                    Offset(
                        radius * 0.28f,
                        radius * 0.28f
                    ),
                size =
                    Size(
                        radius * 1.44f,
                        radius * 1.44f
                    ),
                style =
                    Stroke(
                        width = 3f,
                        cap =
                            StrokeCap.Round
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(
                    (140 * pulse).dp
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors =
                            if (
                                mode ==
                                JarvisMode.SLEEPING
                            ) {
                                listOf(
                                    Color(0xFF27343A),
                                    Color(0xFF10171B),
                                    Color(0xFF05090B)
                                )
                            } else {
                                listOf(
                                    Color(0xFFD8FAFF),
                                    Color(0xFF58E3FF),
                                    Color(0xFF147BE8),
                                    Color(0xFF071934)
                                )
                            }
                    )
                )
                .border(
                    2.dp,
                    Color(0xFF9AF2FF),
                    CircleShape
                ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    when (mode) {

                        JarvisMode.LISTENING ->
                            "•••"

                        JarvisMode.THINKING ->
                            "◌"

                        JarvisMode.SPEAKING ->
                            "≋"

                        JarvisMode.EXECUTING ->
                            "⌁"

                        JarvisMode.ERROR ->
                            "!"

                        else ->
                            "J"
                    },
                color = Color.White,
                fontSize = 43.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        if (listening) {
                            Color(0xFF9AF5FF)
                        } else {
                            Color(0xFF4FE4FF)
                        },
                        Color(0xFF0A79DA),
                        Color(0xFF061725)
                    )
                )
            )
            .border(
                2.dp,
                Color(0xFF9AF2FF),
                CircleShape
            )
            .clickable {
                onClick()
            },
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = "●",
            color = Color.White,
            fontSize = 23.sp
        )
    }
}

private fun modeLabel(
    mode: JarvisMode
): String {

    return when (mode) {

        JarvisMode.SLEEPING ->
            "STANDBY"

        JarvisMode.ONLINE ->
            "SYSTEM ONLINE"

        JarvisMode.LISTENING ->
            "LISTENING"

        JarvisMode.THINKING ->
            "PROCESSING"

        JarvisMode.SPEAKING ->
            "SPEAKING"

        JarvisMode.EXECUTING ->
            "EXECUTING"

        JarvisMode.ERROR ->
            "ATTENTION"
    }
}
