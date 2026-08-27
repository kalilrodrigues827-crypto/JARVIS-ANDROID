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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
        setContent { JarvisApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun JarvisApp(viewModel: JarvisViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var recognizer by remember { mutableStateOf<VoiceRecognizer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recognizer?.start() else viewModel.showError("Preciso da permissão do microfone para ouvir seus comandos.")
    }

    LaunchedEffect(Unit) {
        recognizer = VoiceRecognizer(
            context = context,
            onResult = { text -> viewModel.setCommand(text); viewModel.submitCommand(text) },
            onListening = viewModel::setListening,
            onError = viewModel::showError
        )
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF05070A),
            surface = Color(0xFF0C1118),
            primary = Color(0xFF77D7FF),
            secondary = Color(0xFF7A8CFF),
            onBackground = Color(0xFFEAF7FF),
            onSurface = Color(0xFFEAF7FF)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF10253A), Color(0xFF071019), Color(0xFF05070A)),
                        center = Offset(350f, 250f),
                        radius = 900f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(state)
                Spacer(Modifier.height(22.dp))
                JarvisCore(state.isListening, state.isBusy)
                Spacer(Modifier.height(18.dp))
                Text(
                    state.message,
                    color = Color(0xFFC8DAE6),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                Spacer(Modifier.height(22.dp))
                CommandBox(
                    value = state.commandText,
                    onValueChange = viewModel::setCommand,
                    onSend = { viewModel.submitCommand() },
                    onMic = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            recognizer?.start()
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    listening = state.isListening,
                    busy = state.isBusy
                )
                Spacer(Modifier.height(18.dp))
                SpotifyPanel(state, viewModel)
                if (state.history.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    HistoryPanel(state.history) { viewModel.setCommand(it); viewModel.submitCommand(it) }
                }
                Spacer(Modifier.height(28.dp))
                Text("JARVIS // PERSONAL AI SYSTEM • V0.1", color = Color(0xFF536778), fontSize = 10.sp, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Header(state: JarvisUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("JARVIS", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("PERSONAL INTELLIGENCE", fontSize = 9.sp, color = Color(0xFF7F99AA), letterSpacing = 2.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x1516E0A5))
                .border(1.dp, Color(0x4437E6BE), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(if (state.status == "ATENÇÃO") Color(0xFFFFB86B) else Color(0xFF3CE6B1), CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(state.status, fontSize = 9.sp, color = Color(0xFFB7FBE7), letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun JarvisCore(listening: Boolean, busy: Boolean) {
    val transition = rememberInfiniteTransition(label = "core")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(if (busy) 550 else 1500), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension / 2
            drawCircle(Color(0x0C67DFFF), radius = base * 0.96f)
            drawCircle(Color(0x2867DFFF), radius = base * 0.78f, style = Stroke(width = 1.3f))
            drawArc(Color(0xFF79D9FF), -65f, 115f, false, topLeft = Offset(base*.22f, base*.22f), size = Size(base*1.56f, base*1.56f), style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawArc(Color(0xFF7588FF), 130f, 95f, false, topLeft = Offset(base*.34f, base*.34f), size = Size(base*1.32f, base*1.32f), style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
        Box(
            Modifier
                .size((104 * pulse).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFB8EEFF), Color(0xFF438DE7), Color(0xFF182B56))))
                .border(1.dp, Color(0xFFBEEFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (listening) "●●●" else "J", fontSize = if (listening) 14.sp else 38.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun CommandBox(value: String, onValueChange: (String)->Unit, onSend:()->Unit, onMic:()->Unit, listening:Boolean, busy:Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xB30B1118)).border(1.dp, Color(0x334E89A9), RoundedCornerShape(24.dp)).padding(12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ex.: Jarvis, toque Starboy", color = Color(0xFF617786)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF5DC9FF), unfocusedBorderColor = Color(0x223AA9DA),
                focusedContainerColor = Color(0x4410202C), unfocusedContainerColor = Color(0x3310202C)
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onMic,
                enabled = !busy,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFF175A72) else Color(0xFF10283A))
            ) { Text(if (listening) "OUVINDO…" else "🎙  FALAR", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Button(
                onClick = onSend,
                enabled = value.isNotBlank() && !busy,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6A97))
            ) { Text(if (busy) "PROCESSANDO" else "EXECUTAR", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun SpotifyPanel(state: JarvisUiState, vm: JarvisViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xA80C1217)).border(1.dp, Color(0x2735DF8B), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1ED760)), contentAlignment = Alignment.Center) {
                    Text("♪", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 21.sp)
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("SPOTIFY CONTROL", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    Text("Controle por comando do Android", color = Color(0xFF6DE6A3), fontSize = 11.sp)
                }
            }
            TextButton(onClick = { vm.submitCommand("abra o spotify") }) {
                Text("ABRIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (state.nowPlaying != null) {
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = Color(0x1FFFFFFF))
            Spacer(Modifier.height(13.dp))
            Text("ÚLTIMO PEDIDO", fontSize = 9.sp, color = Color(0xFF687D8A), letterSpacing = 1.4.sp)
            Spacer(Modifier.height(4.dp))
            Text(state.nowPlaying, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HistoryPanel(history: List<String>, onTap:(String)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("COMANDOS RECENTES", fontSize = 10.sp, color = Color(0xFF6B8292), letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        history.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x55111920)).clickable { onTap(item) }.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("›", color = Color(0xFF68D4FF), fontSize = 20.sp)
                Spacer(Modifier.width(9.dp))
                Text(item, color = Color(0xFFB9C9D3), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
