package com.pasoseguro.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pasoseguro.app.components.AssistantMicButton
import com.pasoseguro.app.components.BarAction
import com.pasoseguro.app.components.ProceduralBottomBar
import com.pasoseguro.app.data.*
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.ui.theme.*
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.VoiceInteractionState
import com.pasoseguro.app.voice.rememberAutoListenVoice
import kotlinx.coroutines.launch

/**
 * Mensaje único y explícito para cuando cambia el método de interacción —
 * usado tanto por el botón de la barra inferior como por el selector de
 * tarjetas, para no tener dos redacciones distintas del mismo aviso. Dice
 * explícitamente QUÉ gesto físico debe hacer el usuario de ahora en más, en
 * vez de solo nombrar el modo.
 */
private fun interactionModeChangedSpeech(mode: InteractionMode): String = when (mode) {
    InteractionMode.DOUBLE_TAP ->
        "Método cambiado a doble toque. A partir de ahora, presiona dos veces seguidas para confirmar una acción."
    InteractionMode.LONG_PRESS ->
        "Método cambiado a pulsación prolongada. A partir de ahora, mantén presionado durante dos segundos para confirmar una acción."
}

// Índices de item dentro del LazyColumn de abajo — deben mantenerse en sync
// con el orden real de los `item { }` si la lista cambia (ver comandos de
// voz "configuración de voz"/"configuración de vibración").
private const val VOICE_SECTION_INDEX = 3
private const val VIBRATION_SECTION_INDEX = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavController,
    repository: PreferencesRepository,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val voice   = LocalVoiceInteractionManager.current
    val listState = rememberLazyListState()

    // Read current preferences from CompositionLocal (kept in sync by MainActivity)
    val prefs = LocalUserPreferences.current

    LaunchedEffect(Unit) {
        // Sin "Configuración": el micrófono se arma casi al mismo tiempo que
        // este mensaje suena (ver VoiceCommand.kt).
        // flush=false: no cortar la confirmación de navegación que puede seguir sonando al entrar.
        voice.speak("Aquí puedes personalizar la aplicación según tus preferencias.", flush = false)
    }

    // Local mutable copy for immediate UI feedback; saved to DataStore on each change
    var local by remember(prefs) { mutableStateOf(prefs) }

    fun save(updated: UserPreferences) {
        local = updated
        scope.launch { repository.save(updated) }
    }

    val configVoiceContext = remember(local) {
        ScreenVoiceContext(
            screenName = "Configuración",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords = listOf("configuracion de voz", "abrir configuracion de voz", "ajustes de voz"),
                    onRecognized = { scope.launch { listState.animateScrollToItem(VOICE_SECTION_INDEX) } },
                    confirmationSpeech = buildString {
                        append("Lectura por voz: ${if (local.ttsEnabled) "activada" else "desactivada"}. ")
                        append(
                            "Velocidad: ${
                                when (local.ttsSpeed) {
                                    TtsSpeed.SLOW   -> "lenta"
                                    TtsSpeed.NORMAL -> "normal"
                                    TtsSpeed.FAST   -> "rápida"
                                }
                            }."
                        )
                    },
                ),
                ScreenVoiceCommand(
                    keywords = listOf(
                        "configuracion de vibracion", "abrir configuracion de vibracion", "ajustes de vibracion",
                    ),
                    onRecognized = { scope.launch { listState.animateScrollToItem(VIBRATION_SECTION_INDEX) } },
                    confirmationSpeech = buildString {
                        append("Vibración háptica: ${if (local.hapticEnabled) "activada" else "desactivada"}. ")
                        append(
                            "Intensidad: ${
                                when (local.vibrationIntensity) {
                                    VibrationIntensity.SUAVE   -> "suave"
                                    VibrationIntensity.MEDIA   -> "media"
                                    VibrationIntensity.INTENSA -> "intensa"
                                }
                            }."
                        )
                    },
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Configuración de voz, o Configuración de vibración.",
        )
    }
    val activeVoice = rememberAutoListenVoice(configVoiceContext)
    val voiceState by activeVoice.state.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Configuración",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            voice.speak("Volviendo a la pantalla principal")
                            navController.popBackStack()
                        },
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowBackIosNew,
                            contentDescription = "Volver al inicio",
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    AssistantMicButton(
                        pending = voiceState != VoiceInteractionState.Idle,
                        onClick = voice::requestHelp,
                        tint    = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            val quickAppearanceOn = local.highContrast && local.largeFont

            ProceduralBottomBar(
                left = BarAction(
                    icon           = Icons.Filled.TouchApp,
                    label          = "Método",
                    pendingMessage = "Cambiar método de interacción. Toca dos veces para confirmar.",
                    onConfirm      = {
                        val newMode = if (local.interactionMode == InteractionMode.DOUBLE_TAP)
                            InteractionMode.LONG_PRESS else InteractionMode.DOUBLE_TAP
                        save(local.copy(interactionMode = newMode))
                        voice.speak(interactionModeChangedSpeech(newMode))
                    },
                ),
                center = BarAction(
                    icon           = Icons.Filled.Vibration,
                    label          = if (local.hapticEnabled) "Vibración: ON" else "Vibración: OFF",
                    selected       = local.hapticEnabled,
                    pendingMessage = "Has seleccionado Vibración. Toca dos veces para confirmar.",
                    onConfirm      = {
                        val enabled = !local.hapticEnabled
                        save(local.copy(hapticEnabled = enabled))
                        voice.speak(if (enabled) "Vibración activada." else "Vibración desactivada.")
                    },
                ),
                right = BarAction(
                    icon           = Icons.Filled.Contrast,
                    label          = "Apariencia",
                    selected       = quickAppearanceOn,
                    pendingMessage = "Has seleccionado Apariencia. Toca dos veces para confirmar.",
                    onConfirm      = {
                        val activate = !quickAppearanceOn
                        save(local.copy(highContrast = activate, largeFont = activate))
                        voice.speak(
                            if (activate) "Alto contraste y texto grande activados."
                            else "Alto contraste y texto grande desactivados."
                        )
                    },
                ),
                accentColor   = ConfigSlate,
                onSpeak       = voice::speak,
                hapticEnabled = local.hapticEnabled,
                vibrationIntensity = local.vibrationIntensity,
                modifier      = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->

        LazyColumn(
            state          = listState,
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── MODO DE INTERACCIÓN ────────────────────────────────────────
            item {
                SectionHeader(title = "Modo de interacción", icon = Icons.Filled.TouchApp)
            }
            item {
                InteractionModeSelector(
                    selected = local.interactionMode,
                    onSelect = { mode ->
                        save(local.copy(interactionMode = mode))
                        voice.speak(interactionModeChangedSpeech(mode))
                    },
                )
            }

            // ── VOZ ────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(title = "Voz y anuncios", icon = Icons.Filled.RecordVoiceOver)
            }
            item {
                PreferenceSwitch(
                    label       = "Lectura por voz (TTS)",
                    description = "Anuncia el nombre de cada botón al tocarlo.",
                    checked     = local.ttsEnabled,
                    onChecked   = { enabled ->
                        save(local.copy(ttsEnabled = enabled))
                        if (enabled) voice.speak("Lectura por voz activada.")
                    },
                    icon        = Icons.Filled.RecordVoiceOver,
                    iconDesc    = "Lectura por voz",
                )
            }
            item {
                VoiceSpeedSelector(
                    selected = local.ttsSpeed,
                    enabled  = local.ttsEnabled,
                    onSelect = { speed ->
                        save(local.copy(ttsSpeed = speed))
                        val label = when (speed) {
                            TtsSpeed.SLOW   -> "Velocidad de voz: lenta."
                            TtsSpeed.NORMAL -> "Velocidad de voz: normal."
                            TtsSpeed.FAST   -> "Velocidad de voz: rápida."
                        }
                        voice.speak(label)
                    },
                )
            }
            // ── VIBRACIÓN ─────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(title = "Vibración háptica", icon = Icons.Filled.Vibration)
            }
            item {
                PreferenceSwitch(
                    label       = "Vibración háptica",
                    description = "Pulso de vibración corto al tocar botones.",
                    checked     = local.hapticEnabled,
                    onChecked   = { enabled ->
                        save(local.copy(hapticEnabled = enabled))
                        if (local.ttsEnabled) {
                            voice.speak(
                                if (enabled) "Vibración activada." else "Vibración desactivada."
                            )
                        }
                    },
                    icon        = Icons.Filled.Vibration,
                    iconDesc    = "Vibración háptica",
                )
            }
            item {
                VibrationIntensitySelector(
                    selected = local.vibrationIntensity,
                    enabled  = local.hapticEnabled,
                    onSelect = { intensity ->
                        save(local.copy(vibrationIntensity = intensity))
                        HapticHelper.vibrate(context, true, intensity)
                        val label = when (intensity) {
                            VibrationIntensity.SUAVE   -> "Intensidad de vibración: suave."
                            VibrationIntensity.MEDIA   -> "Intensidad de vibración: media."
                            VibrationIntensity.INTENSA -> "Intensidad de vibración: intensa."
                        }
                        voice.speak(label)
                    },
                )
            }

            // ── APARIENCIA ────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(title = "Apariencia", icon = Icons.Filled.Contrast)
            }
            item {
                PreferenceSwitch(
                    label       = "Alto contraste",
                    description = "Texto negro puro sobre fondo blanco para mayor visibilidad.",
                    checked     = local.highContrast,
                    onChecked   = { enabled ->
                        save(local.copy(highContrast = enabled))
                        voice.speak(
                            if (enabled) "Modo alto contraste activado." else "Modo alto contraste desactivado."
                        )
                    },
                    icon        = Icons.Filled.Contrast,
                    iconDesc    = "Alto contraste",
                )
            }
            item {
                PreferenceSwitch(
                    label       = "Texto grande",
                    description = "Aumenta el tamaño del texto en toda la aplicación.",
                    checked     = local.largeFont,
                    onChecked   = { enabled ->
                        save(local.copy(largeFont = enabled))
                        voice.speak(
                            if (enabled) "Texto grande activado." else "Texto grande desactivado."
                        )
                    },
                    icon        = Icons.Filled.FormatSize,
                    iconDesc    = "Texto grande",
                )
            }

            // ── RESTAURAR ─────────────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }
            item {
                OutlinedButton(
                    onClick  = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .semantics {
                            contentDescription = "Restaurar configuración predeterminada"
                        },
                    shape  = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier           = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text       = "Restaurar configuración predeterminada",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── Reset confirmation dialog ──────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text       = "Restaurar configuración",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text("¿Deseas restablecer todas las preferencias a sus valores predeterminados?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        save(UserPreferences.Default)
                        voice.speak("Configuración restaurada.")
                    },
                ) {
                    Text(
                        text  = "Restaurar",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

// ── Reusable section header ────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// ── Preference row with Switch ─────────────────────────────────────────────

@Composable
private fun PreferenceSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDesc: String,
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: ${if (checked) "activado" else "desactivado"}" },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick   = { onChecked(!checked) },
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = iconDesc,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text      = description,
                    fontSize  = 13.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked         = checked,
                onCheckedChange = onChecked,
            )
        }
    }
}

// ── Interaction mode selector (large toggle buttons) ──────────────────────

@Composable
private fun InteractionModeSelector(
    selected: InteractionMode,
    onSelect: (InteractionMode) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InteractionModeButton(
            label       = "Doble toque",
            description = "Dos toques consecutivos para activar",
            icon        = Icons.Filled.TouchApp,
            isSelected  = selected == InteractionMode.DOUBLE_TAP,
            onClick     = { onSelect(InteractionMode.DOUBLE_TAP) },
            modifier    = Modifier.weight(1f),
        )
        InteractionModeButton(
            label       = "Pulsación prolongada",
            description = "Mantener 2 segundos para activar",
            icon        = Icons.Filled.Timer,
            isSelected  = selected == InteractionMode.LONG_PRESS,
            onClick     = { onSelect(InteractionMode.LONG_PRESS) },
            modifier    = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InteractionModeButton(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "$label: ${if (isSelected) "seleccionado" else "no seleccionado"}"
            },
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (isSelected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text       = label,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text     = description,
            fontSize = 11.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Voice speed selector ───────────────────────────────────────────────────

@Composable
private fun VoiceSpeedSelector(
    selected: TtsSpeed,
    enabled: Boolean,
    onSelect: (TtsSpeed) -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.Speed,
                    contentDescription = "Velocidad de voz",
                    tint               = if (enabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = "Velocidad de voz",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    TtsSpeed.SLOW   to "Lenta",
                    TtsSpeed.NORMAL to "Normal",
                    TtsSpeed.FAST   to "Rápida",
                ).forEach { (speed, label) ->
                    SpeedChip(
                        label      = label,
                        isSelected = selected == speed,
                        enabled    = enabled,
                        onClick    = { if (enabled) onSelect(speed) },
                        modifier   = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    semanticsPrefix: String = "Velocidad",
) {
    val bg = when {
        isSelected && enabled -> MaterialTheme.colorScheme.primary
        else                  -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isSelected && enabled -> MaterialTheme.colorScheme.onPrimary
        else                  -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$semanticsPrefix $label${if (isSelected) ", seleccionada" else ""}" }
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text       = label,
            fontWeight = if (isSelected && enabled) FontWeight.Bold else FontWeight.Normal,
            fontSize   = 14.sp,
            color      = textColor,
        )
    }
}

// ── Vibration intensity selector ───────────────────────────────────────────

@Composable
private fun VibrationIntensitySelector(
    selected: VibrationIntensity,
    enabled: Boolean,
    onSelect: (VibrationIntensity) -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.Vibration,
                    contentDescription = "Intensidad de vibración",
                    tint               = if (enabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = "Intensidad de vibración",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    VibrationIntensity.SUAVE   to "Suave",
                    VibrationIntensity.MEDIA   to "Media",
                    VibrationIntensity.INTENSA to "Intensa",
                ).forEach { (intensity, label) ->
                    SpeedChip(
                        label           = label,
                        isSelected      = selected == intensity,
                        enabled         = enabled,
                        onClick         = { if (enabled) onSelect(intensity) },
                        modifier        = Modifier.weight(1f),
                        semanticsPrefix = "Intensidad",
                    )
                }
            }
        }
    }
}
