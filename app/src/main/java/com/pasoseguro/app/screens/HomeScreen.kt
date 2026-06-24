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
import com.pasoseguro.app.utils.*

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = LocalUserPreferences.current
    val tts     = remember { TtsHelper(context) }

    // Sync TTS settings whenever preferences change
    LaunchedEffect(prefs.ttsEnabled, prefs.ttsSpeed) {
        tts.enabled = prefs.ttsEnabled
        tts.setSpeed(prefs.ttsSpeed)
    }

    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    val tapHandler = rememberDoubleTapHandler(tts = tts, prefs = prefs) { feature ->
        navController.navigate(feature.route)
    }

    val features    = Feature.entries
    val topFeatures = listOf(Feature.CONTACTS, Feature.ALERTS, Feature.CONFIG)
    val botFeatures = listOf(Feature.NAVIGATE, Feature.SCAN, Feature.ROUTE)

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { features.size + 1 })

    // Build a LongPressConfig for a given feature (null = use double-tap mode)
    fun longPressConfigFor(feature: Feature): LongPressConfig? {
        if (prefs.interactionMode != InteractionMode.LONG_PRESS) return null
        return LongPressConfig(
            onMidpoint = { HapticHelper.vibrate(context, prefs.hapticEnabled) },
            onComplete = {
                tts.speak("Abriendo ${feature.ttsText}")
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
                pagerState          = pagerState,
                onFeatureTap        = tapHandler::onTap,
                longPressConfigFor  = ::longPressConfigFor,
                modifier            = Modifier.fillMaxSize(),
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
