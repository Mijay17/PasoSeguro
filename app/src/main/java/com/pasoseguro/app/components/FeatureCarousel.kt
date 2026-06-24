package com.pasoseguro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.theme.Brand800
import com.pasoseguro.app.utils.LongPressConfig

/**
 * 7-page horizontal pager:
 *  • Page 0     → CarouselWelcomePage (logo, bienvenida)
 *  • Pages 1–6  → CarouselFeaturePage for each Feature
 *
 * [longPressConfigFor] returns a [LongPressConfig] for a given feature when
 * the app is in long-press mode, or null when in double-tap mode.
 */
@Composable
fun FeatureCarousel(
    pagerState: PagerState,
    onFeatureTap: (Feature) -> Unit,
    modifier: Modifier = Modifier,
    longPressConfigFor: (Feature) -> LongPressConfig? = { null },
) {
    val features = Feature.entries

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics { contentDescription = "Carrusel de funciones, desliza para explorar" },
        ) { page ->
            if (page == 0) {
                CarouselWelcomePage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                )
            } else {
                val feature = features[page - 1]
                CarouselFeaturePage(
                    feature         = feature,
                    onOpenFeature   = { onFeatureTap(feature) },
                    longPressConfig = longPressConfigFor(feature),
                    modifier        = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PagerDots(
            pageCount   = features.size + 1,
            currentPage = pagerState.currentPage,
        )
    }
}

@Composable
private fun PagerDots(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue   = if (selected) 22.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label         = "dot_w",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) Brand800 else Color(0xFFBBCCE0)
                    ),
            )
        }
    }
}
