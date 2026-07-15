package com.pasoseguro.app.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.rememberAutoListenVoice

// ── Main screen ────────────────────────────────────────────────────────────

@Composable
fun NavigateScreen(navController: NavController) {
    val prefs   = LocalUserPreferences.current
    val context = LocalContext.current
    val vm: NavigateViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(prefs.hapticEnabled) {
        vm.updateHapticEnabled(prefs.hapticEnabled)
    }

    // ── Asistente IA por voz — escucha automática y continua ────────────────
    val navigateVoiceContext = remember {
        ScreenVoiceContext(
            screenName = "Navegar",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords = listOf(
                        "iniciar navegacion", "reanudar navegacion", "iniciar monitoreo", "reanudar monitoreo",
                    ),
                    onRecognized       = vm::resumeMonitoring,
                    confirmationSpeech = "Reanudando el monitoreo.",
                ),
                ScreenVoiceCommand(
                    keywords            = listOf("pausar navegacion", "pausar monitoreo", "detener monitoreo"),
                    onRecognized        = vm::stopMonitoringForConfirm,
                    confirmationSpeech  = null, // ya habla su propia pregunta de confirmación
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Iniciar navegación, o Pausar navegación.",
        )
    }
    rememberAutoListenVoice(navigateVoiceContext)

    // ── Camera permission ─────────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── Two-tap close logic (no dialog) ───────────────────────────────────
    //
    // First tap  → stop monitoring + TTS speaks warning + button turns red
    // Second tap  (within 3.5 s) → navigate back
    // No second tap → monitoring resumes automatically after timeout

    var closePendingConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(closePendingConfirm) {
        if (closePendingConfirm) {
            kotlinx.coroutines.delay(3500L)
            // User didn't confirm in time → restore monitoring
            closePendingConfirm = false
            vm.resumeMonitoring()
        }
    }

    fun onCloseButtonTapped() {
        if (closePendingConfirm) {
            // Second tap: confirmed exit
            closePendingConfirm = false
            navController.popBackStack()
        } else {
            // First tap: announce warning and arm the button
            closePendingConfirm = true
            vm.stopMonitoringForConfirm()
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {

        // ── Camera + overlay section (77 % of screen height) ─────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.77f),
        ) {
            if (hasCameraPermission) {
                CameraPreviewView(modifier = Modifier.fillMaxSize())
            } else {
                NoCameraPermissionView(
                    modifier            = Modifier.fillMaxSize(),
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }

            // Bounding box + scan line + danger vignette over the camera feed
            BoundingBoxOverlay(
                alert    = uiState.alert,
                modifier = Modifier.fillMaxSize(),
            )

            // Top overlay bar (AI badge left · X close button right)
            NavigationOverlayBar(
                severity            = uiState.alert.severity,
                isMonitoring        = uiState.isMonitoring,
                closePendingConfirm = closePendingConfirm,
                onCloseClick        = ::onCloseButtonTapped,
                modifier            = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        // ── Bottom panel (23 % of screen height) ─────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.23f)
                .background(Color(0xFF080E1C))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState    = uiState.alert,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 })
                        .togetherWith(fadeOut(tween(180)))
                },
                label          = "alertCard",
                modifier       = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { alert ->
                AlertInfoCard(alert = alert, modifier = Modifier.fillMaxSize())
            }

            // ── Botones de control (comentados — descomentar si se necesitan) ──
            //
            // Spacer(Modifier.height(4.dp))
            // Row(
            //     modifier              = Modifier.fillMaxWidth(),
            //     horizontalArrangement = Arrangement.spacedBy(10.dp),
            // ) {
            //     // Iniciar Monitoreo
            //     Button(
            //         onClick  = { vm.resumeMonitoring() },
            //         enabled  = !uiState.isMonitoring,
            //         shape    = RoundedCornerShape(14.dp),
            //         colors   = ButtonDefaults.buttonColors(
            //             containerColor         = Color(0xFF1A6B3C),
            //             disabledContainerColor = Color(0xFF1A6B3C).copy(alpha = 0.38f),
            //         ),
            //         modifier = Modifier
            //             .weight(1f)
            //             .height(52.dp)
            //             .semantics { contentDescription = "Iniciar Monitoreo" },
            //     ) {
            //         Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
            //         Spacer(Modifier.width(6.dp))
            //         Text("Iniciar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            //     }
            //
            //     // Pausar Monitoreo
            //     Button(
            //         onClick  = { vm.stopMonitoringForConfirm() },
            //         enabled  = uiState.isMonitoring,
            //         shape    = RoundedCornerShape(14.dp),
            //         colors   = ButtonDefaults.buttonColors(
            //             containerColor         = Color(0xFF7B3A00),
            //             disabledContainerColor = Color(0xFF7B3A00).copy(alpha = 0.38f),
            //         ),
            //         modifier = Modifier
            //             .weight(1f)
            //             .height(52.dp)
            //             .semantics { contentDescription = "Pausar Monitoreo" },
            //     ) {
            //         Icon(Icons.Filled.Pause, null, Modifier.size(20.dp))
            //         Spacer(Modifier.width(6.dp))
            //         Text("Pausar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            //     }
            // }
        }
    }
}

// ── CameraX real preview ───────────────────────────────────────────────────

@Composable
private fun CameraPreviewView(modifier: Modifier = Modifier) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            PreviewView(ctx).apply {
                scaleType    = PreviewView.ScaleType.FILL_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }.also { previewView ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val cameraProvider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // ── TODO: ImageAnalysis — connect AI model here ──────────
                    // val imageAnalysis = ImageAnalysis.Builder()
                    //     .setTargetResolution(android.util.Size(1280, 720))
                    //     .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    //     .build()
                    //     .also { analysis ->
                    //         analysis.setAnalyzer(
                    //             java.util.concurrent.Executors.newSingleThreadExecutor()
                    //         ) { imageProxy ->
                    //             // Run YOLO / TFLite model on imageProxy here,
                    //             // then post detected obstacles to NavigateViewModel.
                    //             imageProxy.close()
                    //         }
                    //     }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        // imageAnalysis,  ← uncomment when AI model is ready
                    )
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

// ── No-permission fallback ─────────────────────────────────────────────────

@Composable
private fun NoCameraPermissionView(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier         = modifier.background(Color(0xFF070D1B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.45f),
                modifier           = Modifier.size(64.dp),
            )
            Text(
                text       = "Permiso de cámara requerido",
                color      = Color.White,
                style      = MaterialTheme.typography.titleMedium,
                textAlign  = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text      = "PasoSeguro necesita acceso a la cámara para detectar obstáculos en su entorno.",
                color     = Color.White.copy(alpha = 0.70f),
                style     = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick  = onRequestPermission,
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Conceder permiso de cámara" },
            ) {
                Text("Conceder permiso", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── Bounding box + AR overlay (transparent Canvas over the camera feed) ────

@Composable
private fun BoundingBoxOverlay(
    alert: NavAlert,
    modifier: Modifier = Modifier,
) {
    val inf = rememberInfiniteTransition(label = "overlay")

    val bboxAlpha by inf.animateFloat(
        initialValue  = 0.45f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(580), RepeatMode.Reverse),
        label         = "bboxPulse",
    )
    val dangerPulse by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(510), RepeatMode.Reverse),
        label         = "dangerEdge",
    )
    val scanProgress by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label         = "scan",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Scan line — conveys that the AI is actively analysing the scene
        val scanY  = h * 0.14f + h * 0.70f * scanProgress
        val scanXL = w * 0.06f + w * 0.38f * scanProgress
        val scanXR = w * 0.94f - w * 0.38f * scanProgress
        drawPath(
            path  = Path().apply { moveTo(scanXL, scanY); lineTo(scanXR, scanY) },
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00B4FF).copy(alpha = 0.55f),
                    Color(0xFF00E5FF),
                    Color(0xFF00B4FF).copy(alpha = 0.55f),
                    Color.Transparent,
                ),
                startX = scanXL, endX = scanXR,
            ),
            style = Stroke(width = 2f),
        )

        // Decorative AR corner brackets framing the live view
        val fc   = 36f; val fm = 14f
        val fcol = Color.White.copy(alpha = 0.20f)
        val fs   = Stroke(width = 2f)
        fun bracket(ox: Float, oy: Float, dx: Float, dy: Float) = Path().apply {
            moveTo(ox, oy + dy * fc); lineTo(ox, oy); lineTo(ox + dx * fc, oy)
        }
        drawPath(bracket(fm,     fm,      1f,  1f), fcol, style = fs)
        drawPath(bracket(w - fm, fm,     -1f,  1f), fcol, style = fs)
        drawPath(bracket(fm,     h - fm,  1f, -1f), fcol, style = fs)
        drawPath(bracket(w - fm, h - fm, -1f, -1f), fcol, style = fs)

        // Dynamic bounding box — position, size and color track the current alert
        if (alert.direction != ObstacleDirection.NONE) {
            val boxColor = when (alert.severity) {
                AlertSeverity.CLEAR   -> Color(0xFF66BB6A)
                AlertSeverity.CAUTION -> Color(0xFFFFB300)
                AlertSeverity.DANGER  -> Color(0xFFEF5350)
            }
            val dist = alert.distanceMeters ?: 2.5f

            val cX = when (alert.direction) {
                ObstacleDirection.FRONT -> w * 0.50f
                ObstacleDirection.LEFT  -> w * 0.25f
                ObstacleDirection.RIGHT -> w * 0.75f
                ObstacleDirection.NONE  -> w * 0.50f
            }
            val relW = when {
                dist < 1f -> 0.20f
                dist < 2f -> 0.15f
                dist < 3f -> 0.11f
                else      -> 0.08f
            }
            val hW = w * relW
            val hH = hW * 1.45f
            val cY = when {
                dist < 1f -> h * 0.68f
                dist < 2f -> h * 0.58f
                dist < 3f -> h * 0.50f
                else      -> h * 0.44f
            }

            val l    = cX - hW; val r = cX + hW
            val t    = cY - hH; val b = cY + hH
            val cLen = hH * 0.28f

            drawRect(
                color   = boxColor.copy(alpha = bboxAlpha * 0.11f),
                topLeft = Offset(l, t),
                size    = Size(hW * 2f, hH * 2f),
            )

            val bPath = Path().apply {
                moveTo(l, t + cLen); lineTo(l, t); lineTo(l + cLen, t)
                moveTo(r - cLen, t); lineTo(r, t); lineTo(r, t + cLen)
                moveTo(l, b - cLen); lineTo(l, b); lineTo(l + cLen, b)
                moveTo(r - cLen, b); lineTo(r, b); lineTo(r, b - cLen)
            }
            drawPath(bPath, boxColor.copy(alpha = bboxAlpha), style = Stroke(width = 3f))

            val badgeW = 84f; val badgeH = 26f
            val bLeft  = cX - badgeW / 2f
            val bTop   = t - badgeH - 8f
            if (bTop > 0f) {
                drawRoundRect(
                    color        = boxColor.copy(alpha = bboxAlpha * 0.88f),
                    topLeft      = Offset(bLeft, bTop),
                    size         = Size(badgeW, badgeH),
                    cornerRadius = CornerRadius(6f),
                )
            }
        }

        // Red pulsing vignette on DANGER alerts
        if (alert.severity == AlertSeverity.DANGER) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFEF5350).copy(alpha = dangerPulse * 0.33f),
                    ),
                    center = Offset(w / 2f, h / 2f),
                    radius = maxOf(w, h) * 0.62f,
                )
            )
        }
    }
}

// ── Navigation overlay bar ─────────────────────────────────────────────────
//
// Layout:
//   LEFT  — [🎤 Asistente IA ●]  (informational badge, mic icon)
//   RIGHT — [✕]                  (close button, X in circle, two-tap to confirm)

@Composable
private fun NavigationOverlayBar(
    severity: AlertSeverity,
    isMonitoring: Boolean,
    closePendingConfirm: Boolean,   // true = first tap done, button is "armed"
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (severity) {
        AlertSeverity.CLEAR   -> Color(0xFF66BB6A)
        AlertSeverity.CAUTION -> Color(0xFFFFB300)
        AlertSeverity.DANGER  -> Color(0xFFEF5350)
    }

    // Close button background transitions: dark → red when armed
    val closeButtonBg by animateColorAsState(
        targetValue   = if (closePendingConfirm) Color(0xFFEF5350) else Color.Black.copy(alpha = 0.65f),
        animationSpec = tween(280),
        label         = "closeBg",
    )

    // Close button pulses slightly when armed to draw visual attention.
    // The infinite animation always runs; it's only applied via Modifier.scale
    // when the button is actually armed (closePendingConfirm = true).
    val closePulse by rememberInfiniteTransition(label = "closePulse").animateFloat(
        initialValue  = 1f,
        targetValue   = 0.88f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label         = "closeScale",
    )

    Row(
        modifier          = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        // ── LEFT: Asistente IA badge with mic icon ─────────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isMonitoring)
                        "Asistente de inteligencia artificial activo"
                    else
                        "Asistente de inteligencia artificial en espera"
                },
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Mic,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(17.dp),
            )
            Text(
                text       = "Asistente IA",
                color      = Color.White,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PulsingDot(color = statusColor, active = isMonitoring)
        }

        Spacer(Modifier.weight(1f))

        // ── RIGHT: Close button — X inside a circle ───────────────────
        //
        // contentDescription announces the two-tap requirement.
        // TalkBack:   single tap → reads description.
        //             double tap → fires onClick (first tap logic: TTS speaks
        //                          warning + button turns red).
        //             second double tap → fires onClick again → exits screen.
        // No TalkBack: first tap → warning announced. second tap within 3.5 s → exits.

        IconButton(
            onClick  = onCloseClick,
            modifier = Modifier
                .size(54.dp)           // 54dp > 48dp minimum touch target
                .clip(CircleShape)
                .background(closeButtonBg)
                // scale applied last so the entire circle (fill + border) shrinks together
                .scale(if (closePendingConfirm) closePulse else 1f)
                .semantics {
                    contentDescription =
                        if (closePendingConfirm)
                            "Botón Salir activo. Presione nuevamente para confirmar salida."
                        else
                            "Botón Salir. ¿Desea volver al inicio? Presione dos veces para confirmar."
                },
        ) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(26.dp),
            )
        }
    }
}

// ── Alert info card (high-contrast, bottom panel) ─────────────────────────

@Composable
private fun AlertInfoCard(
    alert: NavAlert,
    modifier: Modifier = Modifier,
) {
    val sevColor = when (alert.severity) {
        AlertSeverity.CLEAR   -> Color(0xFF66BB6A)
        AlertSeverity.CAUTION -> Color(0xFFFFB300)
        AlertSeverity.DANGER  -> Color(0xFFEF5350)
    }
    val bgColor = when (alert.severity) {
        AlertSeverity.CLEAR   -> Color(0xFF0D2117)
        AlertSeverity.CAUTION -> Color(0xFF221800)
        AlertSeverity.DANGER  -> Color(0xFF220808)
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription =
                "${alert.title}. ${alert.detail}. Recomendación: ${alert.recommendation}"
        },
        shape          = RoundedCornerShape(14.dp),
        color          = bgColor,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(sevColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (alert.severity) {
                        AlertSeverity.CLEAR   -> Icons.Filled.CheckCircle
                        AlertSeverity.CAUTION -> Icons.Filled.Warning
                        AlertSeverity.DANGER  -> Icons.Filled.Error
                    },
                    contentDescription = null,
                    tint               = sevColor,
                    modifier           = Modifier.size(28.dp),
                )
            }

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text       = alert.title,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                )
                Text(
                    text     = alert.detail,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                )
                Text(
                    text     = alert.recommendation,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = sevColor.copy(alpha = 0.90f),
                    maxLines = 1,
                )
            }

            if (alert.direction != ObstacleDirection.NONE) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = when (alert.direction) {
                            ObstacleDirection.FRONT -> Icons.Filled.North
                            ObstacleDirection.LEFT  -> Icons.Filled.West
                            ObstacleDirection.RIGHT -> Icons.Filled.East
                            ObstacleDirection.NONE  -> Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint               = sevColor,
                        modifier           = Modifier.size(24.dp),
                    )
                    Text(
                        text     = when (alert.direction) {
                            ObstacleDirection.FRONT -> "Frente"
                            ObstacleDirection.LEFT  -> "Izq."
                            ObstacleDirection.RIGHT -> "Der."
                            ObstacleDirection.NONE  -> ""
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = sevColor,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

// ── Pulsing indicator dot ──────────────────────────────────────────────────

@Composable
private fun PulsingDot(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "pulseDot")
    val alpha by inf.animateFloat(
        initialValue  = 0.40f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "dotAlpha",
    )
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(
            color  = color.copy(alpha = if (active) alpha else 0.25f),
            radius = size.minDimension / 2f,
        )
    }
}
