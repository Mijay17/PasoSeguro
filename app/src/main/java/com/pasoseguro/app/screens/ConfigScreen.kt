package com.pasoseguro.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.ui.theme.*
import com.pasoseguro.app.utils.ConfirmActionState
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.rememberConfirmAction
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

// Las 3 pestañas de esta pantalla — cada una muestra únicamente su propio
// contenido (ver `AnimatedContent` en el cuerpo de ConfigScreen). Los botones
// de la barra inferior ya no son atajos de activar/desactivar: seleccionan
// una de estas pestañas (reutilizando el campo `selected` de [BarAction] para
// resaltar cuál está activa) siguiendo el mismo patrón de doble toque
// (armar + confirmar) que el resto de los controles de esta pantalla.
private enum class ConfigTab { METODO, VIBRACION, APARIENCIA }

// Mensajes del primer toque (armar) de los 3 botones de pestaña — nombran el
// botón y explican su función, sin ejecutar ninguna acción todavía.
private const val METODO_TAB_DESCRIPTION =
    "Método. Permite configurar el modo de interacción y las opciones de voz. Toca dos veces para confirmar."
private const val VIBRACION_TAB_DESCRIPTION =
    "Vibración. Permite configurar la respuesta háptica de la aplicación. Toca dos veces para confirmar."
private const val APARIENCIA_TAB_DESCRIPTION =
    "Apariencia. Permite configurar las opciones visuales de la aplicación. Toca dos veces para confirmar."

// Resúmenes hablados reutilizados tanto por los comandos de voz
// ("configuración de voz"/"configuración de vibración") como por el segundo
// toque (confirmar) del botón de la barra inferior correspondiente — una
// sola redacción por pestaña, sin duplicar el texto en dos lugares.
private fun metodoAnnouncement(prefs: UserPreferences): String = buildString {
    append("Mostrando Método. ")
    append(
        "Modo de interacción: ${
            when (prefs.interactionMode) {
                InteractionMode.DOUBLE_TAP -> "doble toque"
                InteractionMode.LONG_PRESS -> "pulsación prolongada"
            }
        }. "
    )
    append("Lectura por voz: ${if (prefs.ttsEnabled) "activada" else "desactivada"}. ")
    append(
        "Velocidad: ${
            when (prefs.ttsSpeed) {
                TtsSpeed.SLOW   -> "lenta"
                TtsSpeed.NORMAL -> "normal"
                TtsSpeed.FAST   -> "rápida"
            }
        }."
    )
}

private fun vibracionAnnouncement(prefs: UserPreferences): String = buildString {
    append("Mostrando Vibración. ")
    append("Vibración háptica: ${if (prefs.hapticEnabled) "activada" else "desactivada"}. ")
    append(
        "Intensidad: ${
            when (prefs.vibrationIntensity) {
                VibrationIntensity.SUAVE   -> "suave"
                VibrationIntensity.MEDIA   -> "media"
                VibrationIntensity.INTENSA -> "intensa"
            }
        }."
    )
}

private fun aparienciaAnnouncement(prefs: UserPreferences): String = buildString {
    append("Mostrando Apariencia. ")
    append("Alto contraste: ${if (prefs.highContrast) "activado" else "desactivado"}. ")
    append("Texto grande: ${if (prefs.largeFont) "activado" else "desactivado"}.")
}

// Restaurar configuración — flujo 100% accesible por voz/doble toque, sin
// ningún diálogo visual (ver [rememberConfirmAction] más abajo).
private const val RESET_DESCRIPTION =
    "Restaurar configuración. Restablece todos los ajustes a sus valores predeterminados. Toca dos veces para confirmar."
private const val RESET_RESULT_MESSAGE =
    "La configuración ha sido restaurada correctamente a los valores predeterminados."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavController,
    repository: PreferencesRepository,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val voice   = LocalVoiceInteractionManager.current

    // Read current preferences from CompositionLocal (kept in sync by MainActivity)
    val prefs = LocalUserPreferences.current

    var selectedTab by remember { mutableStateOf(ConfigTab.METODO) }

    // Un ScrollState por pestaña, con vida en ConfigScreen (no dentro de cada
    // *TabContent) para que sobreviva al cambio de pestaña — AnimatedContent
    // dispone la subcomposición saliente, así que un ScrollState creado ADENTRO
    // de esa subcomposición se perdería y el scroll volvería siempre al tope.
    val metodoScroll = rememberScrollState()
    val vibracionScroll = rememberScrollState()
    val aparienciaScroll = rememberScrollState()

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

    // Restaurar configuración — mismo patrón de doble toque (armar + confirmar)
    // que el resto de los controles de esta pantalla, vía la infraestructura ya
    // existente de [rememberConfirmAction] (usada también por ProceduralBottomBar
    // y por los botones de doble toque de Ruta/Home). Sin diálogo visual: el
    // primer toque vibra y anuncia qué hace el botón; el segundo confirma y
    // ejecuta la restauración, anunciando el resultado por voz.
    val resetConfirm = rememberConfirmAction(
        pendingMessage = RESET_DESCRIPTION,
        onSpeak = voice::speak,
        onHaptic = { HapticHelper.vibrate(context, local.hapticEnabled, local.vibrationIntensity) },
        onConfirm = {
            save(UserPreferences.Default)
            voice.speak(RESET_RESULT_MESSAGE)
        },
    )

    val configVoiceContext = remember(local) {
        ScreenVoiceContext(
            screenName = "Configuración",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords = listOf("configuracion de voz", "abrir configuracion de voz", "ajustes de voz"),
                    onRecognized = { selectedTab = ConfigTab.METODO },
                    confirmationSpeech = metodoAnnouncement(local),
                ),
                ScreenVoiceCommand(
                    keywords = listOf(
                        "configuracion de vibracion", "abrir configuracion de vibracion", "ajustes de vibracion",
                    ),
                    onRecognized = { selectedTab = ConfigTab.VIBRACION },
                    confirmationSpeech = vibracionAnnouncement(local),
                ),
                // Accesibilidad del botón "Restaurar configuración": estos 3
                // comandos reutilizan el mismo [ConfirmActionState] que maneja el
                // toque físico — armar, confirmar y cancelar completamente por voz,
                // sin depender de ningún diálogo visual.
                ScreenVoiceCommand(
                    keywords = listOf("restaurar configuracion", "restablecer configuracion", "restaurar valores predeterminados"),
                    onRecognized = { if (!resetConfirm.isPending) resetConfirm.onTap() },
                    confirmationSpeech = null,
                ),
                ScreenVoiceCommand(
                    keywords = listOf("confirmar restauracion", "aceptar restauracion", "restaurar ahora"),
                    onRecognized = { if (resetConfirm.isPending) resetConfirm.onTap() },
                    confirmationSpeech = null,
                ),
                ScreenVoiceCommand(
                    keywords = listOf("cancelar restauracion", "no restaurar"),
                    onRecognized = {
                        if (resetConfirm.isPending) {
                            resetConfirm.reset()
                            voice.speak("Restauración cancelada.")
                        }
                    },
                    confirmationSpeech = null,
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Configuración de voz, Configuración de vibración, o Restaurar configuración.",
        )
    }
    val activeVoice = rememberAutoListenVoice(configVoiceContext)
    val voiceState by activeVoice.state.collectAsState()

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
                            HapticHelper.vibrate(context, local.hapticEnabled, local.vibrationIntensity)
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
            ProceduralBottomBar(
                left = BarAction(
                    icon           = Icons.Filled.TouchApp,
                    label          = "Método",
                    selected       = selectedTab == ConfigTab.METODO,
                    pendingMessage = METODO_TAB_DESCRIPTION,
                    onConfirm      = {
                        selectedTab = ConfigTab.METODO
                        voice.speak(metodoAnnouncement(local))
                    },
                ),
                center = BarAction(
                    icon           = Icons.Filled.Vibration,
                    label          = "Vibración",
                    selected       = selectedTab == ConfigTab.VIBRACION,
                    pendingMessage = VIBRACION_TAB_DESCRIPTION,
                    onConfirm      = {
                        selectedTab = ConfigTab.VIBRACION
                        voice.speak(vibracionAnnouncement(local))
                    },
                ),
                right = BarAction(
                    icon           = Icons.Filled.Contrast,
                    label          = "Apariencia",
                    selected       = selectedTab == ConfigTab.APARIENCIA,
                    pendingMessage = APARIENCIA_TAB_DESCRIPTION,
                    onConfirm      = {
                        selectedTab = ConfigTab.APARIENCIA
                        voice.speak(aparienciaAnnouncement(local))
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

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "configTab",
        ) { tab ->
            when (tab) {
                ConfigTab.METODO -> MetodoTabContent(
                    local = local,
                    scrollState = metodoScroll,
                    onModeSelect = { mode ->
                        save(local.copy(interactionMode = mode))
                        voice.speak(interactionModeChangedSpeech(mode))
                    },
                    onTtsToggle = { enabled ->
                        save(local.copy(ttsEnabled = enabled))
                        if (enabled) voice.speak("Lectura por voz activada.")
                    },
                    onSpeedSelect = { speed ->
                        save(local.copy(ttsSpeed = speed))
                        voice.speak(
                            when (speed) {
                                TtsSpeed.SLOW   -> "Velocidad de voz: lenta."
                                TtsSpeed.NORMAL -> "Velocidad de voz: normal."
                                TtsSpeed.FAST   -> "Velocidad de voz: rápida."
                            }
                        )
                    },
                )
                ConfigTab.VIBRACION -> VibracionTabContent(
                    local = local,
                    scrollState = vibracionScroll,
                    onHapticToggle = { enabled ->
                        save(local.copy(hapticEnabled = enabled))
                        if (local.ttsEnabled) {
                            voice.speak(if (enabled) "Vibración activada." else "Vibración desactivada.")
                        }
                    },
                    onIntensitySelect = { intensity ->
                        save(local.copy(vibrationIntensity = intensity))
                        HapticHelper.vibrate(context, true, intensity)
                        voice.speak(
                            when (intensity) {
                                VibrationIntensity.SUAVE   -> "Intensidad de vibración: suave."
                                VibrationIntensity.MEDIA   -> "Intensidad de vibración: media."
                                VibrationIntensity.INTENSA -> "Intensidad de vibración: intensa."
                            }
                        )
                    },
                )
                ConfigTab.APARIENCIA -> AparienciaTabContent(
                    local = local,
                    scrollState = aparienciaScroll,
                    resetConfirm = resetConfirm,
                    onHighContrastToggle = { enabled ->
                        save(local.copy(highContrast = enabled))
                        voice.speak(if (enabled) "Modo alto contraste activado." else "Modo alto contraste desactivado.")
                    },
                    onLargeFontToggle = { enabled ->
                        save(local.copy(largeFont = enabled))
                        voice.speak(if (enabled) "Texto grande activado." else "Texto grande desactivado.")
                    },
                )
            }
        }
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

// ── Tab contents ────────────────────────────────────────────────────────────
//
// Cada pestaña muestra únicamente su propio contenido — nada de las otras 2
// categorías. `verticalArrangement = Arrangement.spacedBy(.., CenterVertically)`
// mantiene el contenido centrado dentro del área disponible cuando entra
// completo, y sigue permitiendo scroll (vía el [ScrollState] recibido, con
// vida en ConfigScreen) cuando no entra — sin saltos al cambiar de pestaña.

@Composable
private fun MetodoTabContent(
    local: UserPreferences,
    scrollState: ScrollState,
    onModeSelect: (InteractionMode) -> Unit,
    onTtsToggle: (Boolean) -> Unit,
    onSpeedSelect: (TtsSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        SectionHeader(title = "Modo de interacción", icon = Icons.Filled.TouchApp)
        InteractionModeSelector(selected = local.interactionMode, prefs = local, onSelect = onModeSelect)

        Spacer(Modifier.height(8.dp))
        SectionHeader(title = "Voz y anuncios", icon = Icons.Filled.RecordVoiceOver)
        PreferenceSwitch(
            label           = "Lectura por voz (TTS)",
            description     = "Anuncia el nombre de cada botón al tocarlo.",
            checked         = local.ttsEnabled,
            prefs           = local,
            onConfirmToggle = onTtsToggle,
            icon            = Icons.Filled.RecordVoiceOver,
            iconDesc        = "Lectura por voz",
        )
        VoiceSpeedSelector(selected = local.ttsSpeed, enabled = local.ttsEnabled, prefs = local, onSelect = onSpeedSelect)
    }
}

@Composable
private fun VibracionTabContent(
    local: UserPreferences,
    scrollState: ScrollState,
    onHapticToggle: (Boolean) -> Unit,
    onIntensitySelect: (VibrationIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        SectionHeader(title = "Vibración háptica", icon = Icons.Filled.Vibration)
        PreferenceSwitch(
            label           = "Vibración háptica",
            description     = "Pulso de vibración corto al tocar botones.",
            checked         = local.hapticEnabled,
            prefs           = local,
            onConfirmToggle = onHapticToggle,
            icon            = Icons.Filled.Vibration,
            iconDesc        = "Vibración háptica",
        )
        VibrationIntensitySelector(
            selected = local.vibrationIntensity,
            enabled  = local.hapticEnabled,
            prefs    = local,
            onSelect = onIntensitySelect,
        )
    }
}

@Composable
private fun AparienciaTabContent(
    local: UserPreferences,
    scrollState: ScrollState,
    resetConfirm: ConfirmActionState,
    onHighContrastToggle: (Boolean) -> Unit,
    onLargeFontToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        SectionHeader(title = "Apariencia", icon = Icons.Filled.Contrast)
        PreferenceSwitch(
            label           = "Alto contraste",
            description     = "Texto negro puro sobre fondo blanco para mayor visibilidad.",
            checked         = local.highContrast,
            prefs           = local,
            onConfirmToggle = onHighContrastToggle,
            icon            = Icons.Filled.Contrast,
            iconDesc        = "Alto contraste",
        )
        PreferenceSwitch(
            label           = "Texto grande",
            description     = "Aumenta el tamaño del texto en toda la aplicación.",
            checked         = local.largeFont,
            prefs           = local,
            onConfirmToggle = onLargeFontToggle,
            icon            = Icons.Filled.FormatSize,
            iconDesc        = "Texto grande",
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick  = resetConfirm::onTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .semantics {
                    contentDescription = if (resetConfirm.isPending)
                        "Confirmar restauración. Toca de nuevo para restaurar los valores predeterminados."
                    else
                        "Restaurar configuración predeterminada. Toca dos veces para confirmar."
                },
            shape  = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(
                width = if (resetConfirm.isPending) 2.5.dp else 2.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = if (resetConfirm.isPending) 1f else 0.65f),
            ),
        ) {
            Icon(
                imageVector        = Icons.Filled.RestartAlt,
                contentDescription = null,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text       = if (resetConfirm.isPending) "Confirmar restauración" else "Restaurar configuración predeterminada",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
            )
        }
    }
}

// ── Preference row with Switch ─────────────────────────────────────────────
//
// Mismo patrón de doble toque que el resto de la pantalla: el primer toque
// arma (vibra + anuncia qué hace y hacia dónde cambiará), el segundo confirma
// y recién ahí llama a [onConfirmToggle] con el nuevo valor. El Switch queda
// como indicador visual puro (`onCheckedChange = null`): toda la tarjeta es
// el único punto de interacción, evitando un segundo camino de activación.

@Composable
private fun PreferenceSwitch(
    label: String,
    description: String,
    checked: Boolean,
    prefs: UserPreferences,
    onConfirmToggle: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDesc: String,
) {
    val context = LocalContext.current
    val voice = LocalVoiceInteractionManager.current
    val confirm = rememberConfirmAction(
        pendingMessage = "$label. $description Toca dos veces para ${if (checked) "desactivar" else "activar"}.",
        onSpeak  = voice::speak,
        onHaptic = { HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity) },
        onConfirm = { onConfirmToggle(!checked) },
    )
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (confirm.isPending)
                    "Confirmar: $label. Toca de nuevo."
                else
                    "$label: ${if (checked) "activado" else "desactivado"}. Toca dos veces para cambiar."
            },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (confirm.isPending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick   = confirm::onTap,
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
                onCheckedChange = null,
            )
        }
    }
}

// ── Interaction mode selector (large toggle buttons) ──────────────────────

@Composable
private fun InteractionModeSelector(
    selected: InteractionMode,
    prefs: UserPreferences,
    onSelect: (InteractionMode) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InteractionModeButton(
            label           = "Doble toque",
            description     = "Dos toques consecutivos para activar",
            icon            = Icons.Filled.TouchApp,
            isSelected      = selected == InteractionMode.DOUBLE_TAP,
            prefs           = prefs,
            onConfirmSelect = { onSelect(InteractionMode.DOUBLE_TAP) },
            modifier        = Modifier.weight(1f),
        )
        InteractionModeButton(
            label           = "Pulsación prolongada",
            description     = "Mantener 2 segundos para activar",
            icon            = Icons.Filled.Timer,
            isSelected      = selected == InteractionMode.LONG_PRESS,
            prefs           = prefs,
            onConfirmSelect = { onSelect(InteractionMode.LONG_PRESS) },
            modifier        = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InteractionModeButton(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    prefs: UserPreferences,
    onConfirmSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voice = LocalVoiceInteractionManager.current
    val confirm = rememberConfirmAction(
        pendingMessage = "$label. $description. Toca dos veces para confirmar.",
        onSpeak  = voice::speak,
        onHaptic = { HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity) },
        onConfirm = onConfirmSelect,
    )

    val borderColor = if (confirm.isPending || isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val bgColor = if (confirm.isPending || isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = confirm::onTap)
            .padding(16.dp)
            .semantics {
                contentDescription = if (confirm.isPending)
                    "Confirmar: $label. Toca de nuevo."
                else
                    "$label: ${if (isSelected) "seleccionado" else "no seleccionado"}. Toca dos veces para seleccionar."
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
    prefs: UserPreferences,
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
                        label           = label,
                        isSelected      = selected == speed,
                        enabled         = enabled,
                        pendingMessage  = "Velocidad de voz: $label. Toca dos veces para confirmar.",
                        prefs           = prefs,
                        onConfirmSelect = { onSelect(speed) },
                        modifier        = Modifier.weight(1f),
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
    pendingMessage: String,
    prefs: UserPreferences,
    onConfirmSelect: () -> Unit,
    modifier: Modifier = Modifier,
    semanticsPrefix: String = "Velocidad",
) {
    val context = LocalContext.current
    val voice = LocalVoiceInteractionManager.current
    val confirm = rememberConfirmAction(
        pendingMessage = pendingMessage,
        onSpeak  = voice::speak,
        onHaptic = { HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity) },
        onConfirm = onConfirmSelect,
    )

    val highlighted = (isSelected || confirm.isPending) && enabled
    val bg = when {
        confirm.isPending && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        isSelected && enabled        -> MaterialTheme.colorScheme.primary
        else                         -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = confirm::onTap)
            .semantics {
                contentDescription = buildString {
                    append(semanticsPrefix)
                    append(" ")
                    append(label)
                    if (isSelected) append(", seleccionada")
                    if (confirm.isPending) append(". Toca de nuevo para confirmar.")
                }
            }
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text       = label,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
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
    prefs: UserPreferences,
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
                        pendingMessage  = "Intensidad de vibración: $label. Toca dos veces para confirmar.",
                        prefs           = prefs,
                        onConfirmSelect = { onSelect(intensity) },
                        modifier        = Modifier.weight(1f),
                        semanticsPrefix = "Intensidad",
                    )
                }
            }
        }
    }
}
