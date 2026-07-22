package com.pasoseguro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pasoseguro.app.components.FeatureCarousel
import com.pasoseguro.app.components.PerimeterButton
import com.pasoseguro.app.data.InteractionMode
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.utils.*
import com.pasoseguro.app.voice.rememberVoiceAssistantTrigger
import kotlinx.coroutines.delay

// Bienvenida del Asistente IA — una entre varias, sin repetir la última
// usada. Vive a nivel de archivo (no de composición) para que la variación
// se respete incluso si el usuario vuelve a entrar a Home varias veces en
// la misma sesión.
private val welcomeMessages = NonRepeatingPicker(
    listOf(
        "Bienvenido a PasoSeguro. Soy tu asistente de navegación accesible. Desliza hacia la izquierda o hacia la derecha para explorar las funciones disponibles, o presiona dos veces el logotipo para hablar conmigo.",
        "Hola, bienvenido a PasoSeguro. Estoy listo para ayudarte. Explora las funciones deslizando la pantalla o presiona dos veces el logotipo para darme una instrucción.",
        "Bienvenido nuevamente a PasoSeguro. Desliza para conocer las funciones disponibles o presiona dos veces el logotipo si deseas hablar conmigo.",
        "Hola. Soy tu asistente de navegación. Puedes recorrer las funciones deslizando la pantalla o comunicarte conmigo presionando dos veces el logotipo de PasoSeguro.",
    ),
)

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = LocalUserPreferences.current
    val voice   = LocalVoiceInteractionManager.current

    val features    = Feature.entries
    val topFeatures = listOf(Feature.CONTACTS, Feature.ALERTS, Feature.CONFIG)
    val botFeatures = listOf(Feature.NAVIGATE, Feature.SCAN, Feature.ROUTE)

    // Virtual infinite pager: 1 000 full loops (7 000 pages).
    // Starting at loop 500 (page 3 500) keeps Float scroll offsets well within
    // safe range and lets the user swipe 3 500 slides in either direction before
    // ever hitting a wall — effectively circular.
    val actualPageCount = features.size + 1          // 7
    val startPage       = actualPageCount * 500      // 3 500  (middle of 7 000)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { actualPageCount * 1_000 })

    // Bienvenida + reset del carrusel — se dispara cada vez que se (re)entra a
    // Home (LaunchedEffect con clave Unit se reinicia porque Compose Navigation
    // descompone esta pantalla al salir y la recompone al volver — "Atrás",
    // "Regresar", "Volver" e "Inicio" deben terminar siempre en el Home base).
    // pagerState es la única excepción: por dentro usa rememberSaveable, así
    // que sobrevive ese ciclo y recuerda la última tarjeta vista — por eso hay
    // que reponerlo explícitamente al slide inicial aquí en cada (re)entrada.
    LaunchedEffect(Unit) {
        pagerState.scrollToPage(startPage)
        delay(700L)
        // flush=false: si venimos de "Atrás"/"Inicio" desde otra pantalla, esa
        // confirmación ("Volviendo a la pantalla...") todavía puede estar
        // sonando — no queremos cortarla, sino que esta bienvenida se
        // encole detrás y se escuche completa la secuencia.
        voice.speak(welcomeMessages.next(), flush = false)
    }

    val tapHandler = rememberDoubleTapHandler(onSpeak = voice::speak, prefs = prefs, onStop = voice::stopSpeaking) { feature ->
        navController.navigate(feature.route)
    }

    // ── Asistente IA por voz ────────────────────────────────────────────────
    // Home es la única pantalla que conserva el patrón de doble toque; el
    // resto usa escucha automática y continua (ver voice/AutoListenScreen.kt).
    val assistantConfirm = rememberVoiceAssistantTrigger()

    // Build a LongPressConfig for a given feature (null = use double-tap mode)
    fun longPressConfigFor(feature: Feature): LongPressConfig? {
        if (prefs.interactionMode != InteractionMode.LONG_PRESS) return null
        return LongPressConfig(
            onMidpoint = { HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity) },
            onComplete = {
                voice.speak("Abriendo ${feature.ttsText}")
                navController.navigate(feature.route)
            },
        )
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // ── TOP PERIMETER BAR ─────────────────────────────────────────────
        PerimeterBar(
            features            = topFeatures,
            isTop               = true,
            onFeatureTap        = tapHandler::onTap,
            longPressConfigFor  = ::longPressConfigFor,
        )

        // ── CAROUSEL ─────────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FeatureCarousel(
                pagerState         = pagerState,
                onFeatureTap       = tapHandler::onTap,
                longPressConfigFor = ::longPressConfigFor,
                onSpeak            = voice::speak,
                onHaptic           = { HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity) },
                assistantPending   = assistantConfirm.isPending,
                onAssistantTap     = assistantConfirm::onTap,
                modifier           = Modifier.fillMaxSize(),
            )
        }

        // ── BOTTOM PERIMETER BAR ──────────────────────────────────────────
        PerimeterBar(
            features            = botFeatures,
            isTop               = false,
            onFeatureTap        = tapHandler::onTap,
            longPressConfigFor  = ::longPressConfigFor,
        )
    }
}

// ── Reusable perimeter bar ────────────────────────────────────────────────

@Composable
private fun PerimeterBar(
    features: List<Feature>,
    isTop: Boolean,
    onFeatureTap: (Feature) -> Unit,
    longPressConfigFor: (Feature) -> LongPressConfig?,
) {
    val shape = if (isTop)
        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    else
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            features.forEach { feature ->
                PerimeterButton(
                    icon               = feature.icon,
                    label              = feature.label,
                    tint               = feature.tint,
                    container          = feature.container,
                    accessibilityLabel = feature.contentDescription,
                    onClick            = { onFeatureTap(feature) },
                    longPressConfig    = longPressConfigFor(feature),
                )
            }
        }
    }
}
