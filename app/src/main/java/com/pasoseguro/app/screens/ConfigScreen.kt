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
import com.pasoseguro.app.components.AssistantMicButton
import com.pasoseguro.app.components.BarAction
import com.pasoseguro.app.components.ProceduralBottomBar
import com.pasoseguro.app.data.*
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.theme.*
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.TtsHelper
import com.pasoseguro.app.voice.rememberVoiceAssistantTrigger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavController,
    repository: PreferencesRepository,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val tts     = remember { TtsHelper(context) }

    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    // Read current preferences from CompositionLocal (kept in sync by MainActivity)
    val prefs = LocalUserPreferences.current

    // Sync TTS settings whenever prefs change (or on first load)
    LaunchedEffect(prefs.ttsEnabled, prefs.ttsSpeed) {
        tts.enabled = prefs.ttsEnabled
        tts.setSpeed(prefs.ttsSpeed)
    }

    LaunchedEffect(Unit) {
        tts.speak("Bienvenido a Configuración. Aquí puedes personalizar la aplicación según tus preferencias.")
    }

    val assistantConfirm = rememberVoiceAssistantTrigger(
        navController = navController,
        onSpeak       = tts::speak,
        onHaptic      = { HapticHelper.vibrate(context, prefs.hapticEnabled) },
        speakThenRun  = tts::speak,
    )

    // Local mutable copy for immediate UI feedback; saved to DataStore on each change
    var local by remember(prefs) { mutableStateOf(prefs) }

    fun save(updated: UserPreferences) {
        local = updated
        scope.launch { repository.save(updated) }
    }

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
                            tts.speak("Volver al inicio")
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
                        pending = assistantConfirm.isPending,
                        onClick = assistantConfirm::onTap,
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
                    pendingMessage = "Has seleccionado Método de interacción. Presiona nuevamente para confirmar.",
                    onConfirm      = {
                        val newMode = if (local.interactionMode == InteractionMode.DOUBLE_TAP)
                            InteractionMode.LONG_PRESS else InteractionMode.DOUBLE_TAP
                        save(local.copy(interactionMode = newMode))
                        tts.speak(
                            if (newMode == InteractionMode.DOUBLE_TAP) "Método cambiado a doble toque."
                            else "Método cambiado a pulsación prolongada."
                        )
                    },
                ),
                center = BarAction(
                    icon           = Icons.Filled.Vibration,
                    label          = if (local.hapticEnabled) "Vibración: ON" else "Vibración: OFF",
                    selected       = local.hapticEnabled,
                    pendingMessage = "Has seleccionado Vibración. Presiona nuevamente para confirmar.",
                    onConfirm      = {
                        val enabled = !local.hapticEnabled
                        save(local.copy(hapticEnabled = enabled))
                        tts.speak(if (enabled) "Vibración activada." else "Vibración desactivada.")
                    },
                ),
                right = BarAction(
                    icon           = Icons.Filled.Contrast,
                    label          = "Apariencia",
                    selected       = quickAppearanceOn,
                    pendingMessage = "Has seleccionado Apariencia. Presiona nuevamente para confirmar.",
                    onConfirm      = {
                        val activate = !quickAppearanceOn
                        save(local.copy(highContrast = activate, largeFont = activate))
                        tts.speak(
                            if (activate) "Alto contraste y texto grande activados."
                            else "Alto contraste y texto grande desactivados."
                        )
                    },
                ),
                accentColor   = ConfigSlate,
                tts           = tts,
                hapticEnabled = local.hapticEnabled,
                modifier      = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->

        LazyColumn(
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
                        val label = if (mode == InteractionMode.DOUBLE_TAP)
                            "Modo doble toque activado"
                        else
                            "Modo pulsación prolongada activado"
                        tts.speak(label)
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
                        tts.enabled = enabled
                        if (enabled) tts.speak("Lectura por voz activada.")
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
                        tts.setSpeed(speed)
                        val label = when (speed) {
                            TtsSpeed.SLOW   -> "Velocidad de voz: lenta."
                            TtsSpeed.NORMAL -> "Velocidad de voz: normal."
                            TtsSpeed.FAST   -> "Velocidad de voz: rápida."
                        }
                        tts.speak(label)
                    },
                )
            }
            item {
                ConfirmationPromptSelector(
                    selected = local.confirmationPrompt,
                    enabled  = local.ttsEnabled,
                    onSelect = { prompt ->
                        save(local.copy(confirmationPrompt = prompt))
                        val label = when (prompt) {
                            ConfirmationPrompt.PRESS_AGAIN      -> "Mensaje: Presione nuevamente para continuar."
                            ConfirmationPrompt.HOLD_TWO_SECONDS -> "Mensaje: Mantenga presionado durante dos segundos."
                        }
                        tts.speak(label)
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
                            tts.speak(
                                if (enabled) "Vibración activada." else "Vibración desactivada."
                            )
                        }
                    },
                    icon        = Icons.Filled.Vibration,
                    iconDesc    = "Vibración háptica",
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
                        tts.speak(
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
                        tts.speak(
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
                        val defaults = UserPreferences.Default
                        save(defaults)
                        tts.enabled = defaults.ttsEnabled
                        tts.setSpeed(defaults.ttsSpeed)
                        tts.speak("Configuración restaurada.")
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
            .semantics { contentDescription = "Velocidad $label${if (isSelected) ", seleccionada" else ""}" }
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

// ── Confirmation prompt selector ───────────────────────────────────────────

@Composable
private fun ConfirmationPromptSelector(
    selected: ConfirmationPrompt,
    enabled: Boolean,
    onSelect: (ConfirmationPrompt) -> Unit,
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
                    imageVector        = Icons.Filled.Campaign,
                    contentDescription = "Mensaje de confirmación",
                    tint               = if (enabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = "Mensaje de confirmación",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            listOf(
                ConfirmationPrompt.PRESS_AGAIN      to "Presione nuevamente para continuar.",
                ConfirmationPrompt.HOLD_TWO_SECONDS to "Mantenga presionado durante dos segundos para abrir esta opción.",
            ).forEach { (prompt, text) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected == prompt && enabled)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable(onClick = { if (enabled) onSelect(prompt) })
                        .semantics {
                            contentDescription = text + if (selected == prompt) ", seleccionado" else ""
                        }
                        .padding(12.dp),
                ) {
                    RadioButton(
                        selected = selected == prompt,
                        onClick  = { if (enabled) onSelect(prompt) },
                        enabled  = enabled,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text     = "\"$text\"",
                        fontSize = 13.sp,
                        color    = if (enabled) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
