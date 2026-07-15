package com.pasoseguro.app.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.theme.AlertRed
import com.pasoseguro.app.ui.theme.ScanTeal
import com.pasoseguro.app.ui.theme.ScanTealLight
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.rememberAutoListenVoice
import kotlinx.coroutines.delay

// ── Cycling messages shown during analysis ─────────────────────────────────

private val ANALYZING_MESSAGES = listOf(
    "Explorando el entorno…",
    "Analizando el espacio…",
    "Mapeando zonas…",
    "Identificando objetos…",
    "Procesando imagen…",
)

// ── Screen entry point ─────────────────────────────────────────────────────

@Composable
fun ExploreScreen(navController: NavController) {
    val vm: ExploreViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val prefs   = LocalUserPreferences.current

    LaunchedEffect(prefs.hapticEnabled) {
        vm.updateHapticEnabled(prefs.hapticEnabled)
    }

    // ── Asistente IA por voz — escucha automática y continua ────────────────
    val exploreVoiceContext = remember {
        ScreenVoiceContext(
            screenName = "Explorar",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords            = listOf("explorar nuevamente", "analizar nuevamente", "explorar de nuevo"),
                    onRecognized        = vm::restartExploration,
                    confirmationSpeech  = null, // ya habla su propio mensaje de bienvenida
                ),
                ScreenVoiceCommand(
                    keywords            = listOf("describir entorno", "describe el entorno", "que hay alrededor"),
                    onRecognized        = vm::describeEnvironmentAgain,
                    confirmationSpeech  = null, // ya re-habla la descripción por su cuenta
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Explorar nuevamente, o Describir entorno.",
            onActive = vm::resumeIfBackgrounded,
            onInactive = vm::pauseExploration,
        )
    }
    rememberAutoListenVoice(exploreVoiceContext)

    // Camera permission
    var cameraGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    // Two-tap close button
    var closePendingConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(closePendingConfirm) {
        if (closePendingConfirm) {
            delay(3_500L)
            closePendingConfirm = false
            vm.resumeExploration()
        }
    }
    fun onCloseButtonTapped() {
        if (closePendingConfirm) { closePendingConfirm = false; navController.popBackStack() }
        else                     { closePendingConfirm = true;  vm.stopExplorationForConfirm() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // ── Camera + overlay — 76 % ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.76f),
        ) {
            if (cameraGranted) {
                ExploreCameraView(Modifier.fillMaxSize())
            } else {
                NoPermissionPlaceholder(Modifier.fillMaxSize())
            }

            ExploreCanvasOverlay(
                phase      = uiState.phase,
                activeZone = uiState.activeZone,
                modifier   = Modifier.fillMaxSize(),
            )

            ExploreTopBar(
                phase               = uiState.phase,
                closePendingConfirm = closePendingConfirm,
                onCloseButtonTapped = ::onCloseButtonTapped,
                modifier            = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        // ── Bottom panel — 24 % ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AnimatedContent(
                targetState  = uiState.phase,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically { it / 4 }) togetherWith
                    (fadeOut(tween(250)))
                },
                modifier = Modifier.fillMaxSize(),
                label    = "phase_content",
            ) { phase ->
                when (phase) {
                    ExplorePhase.STARTING  -> StartingPanel()
                    ExplorePhase.ANALYZING -> AnalyzingPanel()
                    ExplorePhase.COMPLETE  -> CompletePanel()
                    ExplorePhase.READY     -> ReadyPanel(
                        activeZone      = uiState.activeZone,
                        zoneDetail      = uiState.zoneDetail,
                        onZoneRequested = vm::requestZoneDetail,
                    )
                }
            }
        }
    }
}

// ── Camera preview ─────────────────────────────────────────────────────────

@Composable
private fun ExploreCameraView(modifier: Modifier = Modifier) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            val view   = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview  = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    // TODO: bind ImageAnalysis use case here for real AI inference
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier,
    )
}

@Composable
private fun NoPermissionPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFF0A1628)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Explore, null, tint = ScanTeal.copy(alpha = 0.4f), modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text("Permiso de cámara requerido", color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Canvas overlay ─────────────────────────────────────────────────────────

@Composable
private fun ExploreCanvasOverlay(
    phase: ExplorePhase,
    activeZone: ExploreZone?,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "canvas")

    val scanProgress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(if (phase == ExplorePhase.ANALYZING) 2_200 else 3_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan",
    )
    val zoneAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.10f,
        targetValue   = 0.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "zone_alpha",
    )
    val bracketAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label         = "bracket_alpha",
    )

    Canvas(modifier = modifier) {
        val w    = size.width
        val h    = size.height
        val vpX  = w * 0.5f
        val vpY  = h * 0.90f
        val lTopX = w * 0.18f
        val rTopX = w * 0.82f
        val topY  = h * 0.05f

        // ── Zone fill (READY phase only) ───────────────────────────────────
        if (phase == ExplorePhase.READY && activeZone != null) {
            val fillPath = when (activeZone) {
                ExploreZone.LEFT  -> Path().apply {
                    moveTo(0f, 0f); lineTo(lTopX, topY); lineTo(vpX, vpY); lineTo(0f, h); close()
                }
                ExploreZone.FRONT -> Path().apply {
                    moveTo(lTopX, topY); lineTo(rTopX, topY); lineTo(vpX, vpY); close()
                }
                ExploreZone.RIGHT -> Path().apply {
                    moveTo(rTopX, topY); lineTo(w, 0f); lineTo(w, h); lineTo(vpX, vpY); close()
                }
            }
            drawPath(fillPath, color = ScanTeal.copy(alpha = zoneAlpha))
        }

        // ── Sector dividers (READY phase) ──────────────────────────────────
        if (phase == ExplorePhase.READY) {
            val divColor = Color.White.copy(alpha = 0.28f)
            val stroke   = Stroke(1.5.dp.toPx())
            drawPath(Path().apply { moveTo(vpX, vpY); lineTo(lTopX, topY) }, divColor, style = stroke)
            drawPath(Path().apply { moveTo(vpX, vpY); lineTo(rTopX, topY) }, divColor, style = stroke)
        }

        // ── Animated sweep line ────────────────────────────────────────────
        val scanY        = scanProgress * h
        val scanIntensity = if (phase == ExplorePhase.ANALYZING) 1f else 0.7f
        drawLine(
            brush       = Brush.horizontalGradient(listOf(Color.Transparent, ScanTeal.copy(alpha = 0.18f * scanIntensity), Color.Transparent)),
            start       = Offset(0f, scanY),
            end         = Offset(w, scanY),
            strokeWidth = 12.dp.toPx(),
        )
        drawLine(
            brush       = Brush.horizontalGradient(listOf(Color.Transparent, ScanTeal.copy(alpha = 0.85f * scanIntensity), Color.Transparent)),
            start       = Offset(0f, scanY),
            end         = Offset(w, scanY),
            strokeWidth = 2.dp.toPx(),
        )

        // ── Grid lines (ANALYZING only) ────────────────────────────────────
        if (phase == ExplorePhase.ANALYZING) {
            val gridColor = ScanTeal.copy(alpha = 0.12f)
            val gStroke   = 1.dp.toPx()
            val cols = 4; val rows = 6
            repeat(cols - 1) { i -> val x = w * (i + 1) / cols; drawLine(gridColor, Offset(x, 0f), Offset(x, h), gStroke) }
            repeat(rows - 1) { i -> val y = h * (i + 1) / rows; drawLine(gridColor, Offset(0f, y), Offset(w, y), gStroke) }
        }

        // ── Corner brackets ────────────────────────────────────────────────
        val bl   = 18.dp.toPx()
        val bw   = 2.2f.dp.toPx()
        val mg   = 10.dp.toPx()
        val bc   = ScanTeal.copy(alpha = bracketAlpha * 0.75f)
        drawLine(bc, Offset(mg, mg), Offset(mg + bl, mg), bw)
        drawLine(bc, Offset(mg, mg), Offset(mg, mg + bl), bw)
        drawLine(bc, Offset(w - mg, mg), Offset(w - mg - bl, mg), bw)
        drawLine(bc, Offset(w - mg, mg), Offset(w - mg, mg + bl), bw)
        drawLine(bc, Offset(mg, h - mg), Offset(mg + bl, h - mg), bw)
        drawLine(bc, Offset(mg, h - mg), Offset(mg, h - mg - bl), bw)
        drawLine(bc, Offset(w - mg, h - mg), Offset(w - mg - bl, h - mg), bw)
        drawLine(bc, Offset(w - mg, h - mg), Offset(w - mg, h - mg - bl), bw)
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────

@Composable
private fun ExploreTopBar(
    phase: ExplorePhase,
    closePendingConfirm: Boolean,
    onCloseButtonTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeButtonBg by animateColorAsState(
        targetValue   = if (closePendingConfirm) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.42f),
        animationSpec = tween(300),
        label         = "close_bg",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "close")
    val closePulse by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label         = "close_pulse",
    )

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Mode badge
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.50f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Explore, null, tint = ScanTeal, modifier = Modifier.size(16.dp))
            Text(
                text  = "Modo Exploración",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            // Pulsing activity dot
            val infiniteDot = rememberInfiniteTransition(label = "dot")
            val dotAlpha by infiniteDot.animateFloat(
                initialValue  = 0.3f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label         = "dot_a",
            )
            val isActive = phase == ExplorePhase.ANALYZING
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (isActive) ScanTeal.copy(alpha = dotAlpha) else Color.Gray.copy(alpha = 0.4f))
            )
        }

        // Close button (two-tap)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(closeButtonBg)
                .scale(if (closePendingConfirm) closePulse else 1f)
                .semantics {
                    contentDescription = if (closePendingConfirm)
                        "Botón Salir activo. Toca dos veces para confirmar la salida."
                    else
                        "Botón Salir. Presione dos veces para regresar al inicio."
                },
        ) {
            IconButton(onClick = onCloseButtonTapped) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Bottom panel — STARTING ────────────────────────────────────────────────

@Composable
private fun StartingPanel() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = ScanTeal, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Text("Iniciando modo exploración…", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

// ── Bottom panel — ANALYZING ───────────────────────────────────────────────

@Composable
private fun AnalyzingPanel() {
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_800L)
            messageIndex = (messageIndex + 1) % ANALYZING_MESSAGES.size
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large icon + spinner row
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ScanTealLight),
            ) {
                Icon(Icons.Filled.Explore, null, tint = ScanTeal, modifier = Modifier.size(24.dp))
            }
            Column {
                AnimatedContent(
                    targetState  = ANALYZING_MESSAGES[messageIndex],
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInVertically { -it / 2 }) togetherWith fadeOut(tween(200))
                    },
                    label = "msg",
                ) { msg ->
                    Text(
                        text  = msg,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Text(
                    text  = "Gira lentamente para mejorar el análisis",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color             = ScanTeal,
            trackColor        = ScanTealLight,
        )
    }
}

// ── Bottom panel — COMPLETE ────────────────────────────────────────────────

@Composable
private fun CompletePanel() {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = ScanTeal, modifier = Modifier.size(22.dp))
            Text(
                text  = "Exploración completada",
                style = MaterialTheme.typography.titleSmall.copy(
                    color      = ScanTeal,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text     = ENVIRONMENT_DISPLAY,
            style    = MaterialTheme.typography.bodySmall.copy(
                color      = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Bottom panel — READY ───────────────────────────────────────────────────

@Composable
private fun ReadyPanel(
    activeZone: ExploreZone?,
    zoneDetail: ExploreZoneDetail?,
    onZoneRequested: (ExploreZone) -> Unit,
) {
    // Mic pulse animation
    val micTransition = rememberInfiniteTransition(label = "mic")
    val micScale by micTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "mic_scale",
    )
    val micAlpha by micTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "mic_alpha",
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Description or active zone detail
        val displayText = zoneDetail?.text ?: ENVIRONMENT_DISPLAY
        val textColor   = if (zoneDetail != null) ScanTeal else MaterialTheme.colorScheme.onSurface

        Text(
            text     = displayText,
            style    = MaterialTheme.typography.bodySmall.copy(
                color      = textColor,
                lineHeight = 17.sp,
                fontStyle  = if (zoneDetail != null) FontStyle.Italic else FontStyle.Normal,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Descripción del entorno: $displayText" },
        )

        // Microphone indicator — suggests voice interaction
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(30.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(ScanTeal.copy(alpha = micAlpha)),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Mic,
                    contentDescription = "Micrófono activo. Selecciona una zona para escuchar su descripción.",
                    tint               = Color.White,
                    modifier           = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "¿Qué zona deseas explorar?",
                style = MaterialTheme.typography.labelSmall.copy(
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        // Zone buttons row — Izquierda / Frente / Derecha
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoneButton(
                label    = "Izquierda",
                icon     = Icons.Filled.ArrowBack,
                zone     = ExploreZone.LEFT,
                isActive = activeZone == ExploreZone.LEFT,
                onClick  = { onZoneRequested(ExploreZone.LEFT) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ZoneButton(
                label    = "Frente",
                icon     = Icons.Filled.ArrowUpward,
                zone     = ExploreZone.FRONT,
                isActive = activeZone == ExploreZone.FRONT,
                onClick  = { onZoneRequested(ExploreZone.FRONT) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ZoneButton(
                label    = "Derecha",
                icon     = Icons.Filled.ArrowForward,
                zone     = ExploreZone.RIGHT,
                isActive = activeZone == ExploreZone.RIGHT,
                onClick  = { onZoneRequested(ExploreZone.RIGHT) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// ── Zone button ────────────────────────────────────────────────────────────

@Composable
private fun ZoneButton(
    label: String,
    icon: ImageVector,
    zone: ExploreZone,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue   = if (isActive) ScanTeal else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(250),
        label         = "zone_bg_$zone",
    )
    val contentColor by animateColorAsState(
        targetValue   = if (isActive) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(250),
        label         = "zone_fg_$zone",
    )
    Button(
        onClick        = onClick,
        modifier       = modifier.semantics {
            contentDescription = "Explorar zona $label${if (isActive) ", activa" else ""}"
        },
        shape          = RoundedCornerShape(12.dp),
        colors         = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
