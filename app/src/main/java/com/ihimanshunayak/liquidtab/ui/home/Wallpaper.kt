/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — Home background.
 *
 * Name      : Wallpaper.kt
 * Version   : 1.0.0
 * Purpose   : Draws the layered Home background and owns the parallax offset
 *             the background moves by. Three sources, per the design: a
 *             generated mesh gradient derived from the theme, a user-picked
 *             image, or the now-playing artwork.
 *
 * Notes     : The gradient is generated, not shipped as three bitmaps — a
 *             generated mesh is a handful of `Brush`es and costs nothing at
 *             any resolution, where a bitmap would have to ship at 4x and be
 *             upscaled on anything larger than the tablet it was cut for.
 *             Parallax is a fraction of a page of vertical offset on the
 *             background layer only; the icons never move, because icons that
 *             drift under a finger are icons you cannot tap.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.WallpaperMode

/**
 * The Home background. [parallaxFraction] is the current page's horizontal
 * position, 0f–1f, used only when parallax is enabled in settings.
 */
@Composable
fun HomeBackground(
    wallpaperMode: WallpaperMode,
    artworkUri: String?,
    parallaxFraction: Float,
    modifier: Modifier = Modifier,
) {
    val parallaxEnabled by LauncherSettings.parallax.state.collectAsStateWithLifecycle()
    val parallaxAmount by LauncherSettings.wallpaperParallaxAmount.state.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()

    val targetOffset = if (parallaxEnabled) {
        // A fraction of a screen of travel, centred, so the first and last page
        // shift by the same amount in opposite directions.
        (parallaxFraction - 0.5f) * parallaxAmount * 2f
    } else {
        0f
    }
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        label = "wallpaperParallax",
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (wallpaperMode) {
            WallpaperMode.GRADIENT -> GradientWallpaper(dark = dark, parallax = animatedOffset)

            WallpaperMode.IMAGE -> {
                val uri by LauncherSettings.wallpaperUri.state.collectAsStateWithLifecycle()
                if (uri.isBlank()) {
                    // A blank uri is what a cleared picker leaves behind; the
                    // gradient is the honest fallback rather than a black frame.
                    GradientWallpaper(dark = dark, parallax = animatedOffset)
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            // Overscan: parallax moves the background, so it has
                            // to be painted larger than the screen or its edge
                            // slides into view.
                            .graphicsLayer {
                                val overscan = size.width * 0.08f
                                scaleX = 1f + 0.08f
                                scaleY = 1f + 0.08f
                                translationX = -animatedOffset * overscan
                            },
                    )
                }
            }

            WallpaperMode.ARTWORK -> {
                if (artworkUri.isNullOrBlank()) {
                    GradientWallpaper(dark = dark, parallax = animatedOffset)
                } else {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(48.dp),
                        )
                        // Artwork behind text always needs a scrim, or a bright
                        // album cover turns every label white-on-white.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.30f),
                                            Color.Black.copy(alpha = 0.55f),
                                        ),
                                    ),
                                ),
                        )
                    }
                }
            }
        }

        // A soft vignette in both themes. It does more for legibility than any
        // single label colour choice does, and costs one gradient.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (dark) 0.55f else 0.18f),
                        ),
                        center = Offset.Unspecified,
                        radius = 1400f,
                    ),
                ),
        )
    }
}

/**
 * The generated mesh gradient: three overlapping radial washes whose centres
 * drift with the parallax offset, over a base that follows the theme. Not a
 * bitmap, so it is sharp at any tablet size.
 */
@Composable
private fun GradientWallpaper(dark: Boolean, parallax: Float) {
    val base = if (dark) Color(0xFF07070A) else Color(0xFFF4F5F8)
    val washA = if (dark) Color(0xFF2A0A12) else Color(0xFFFFE3E8)
    val washB = if (dark) Color(0xFF0A1A2A) else Color(0xFFE2ECFF)
    val washC = if (dark) Color(0xFF1A0A2A) else Color(0xFFF0E6FF)

    val shift = parallax * 0.10f

    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val radius = maxOf(w, h) * 0.85f
                val brushA = Brush.radialGradient(
                    colors = listOf(washA, Color.Transparent),
                    center = Offset(w * (0.18f + shift), h * 0.10f),
                    radius = radius,
                )
                val brushB = Brush.radialGradient(
                    colors = listOf(washB, Color.Transparent),
                    center = Offset(w * (0.88f - shift), h * 0.30f),
                    radius = radius * 0.9f,
                )
                val brushC = Brush.radialGradient(
                    colors = listOf(washC, Color.Transparent),
                    center = Offset(w * (0.50f + shift * 0.5f), h * 1.02f),
                    radius = radius,
                )
                onDrawBehind {
                    drawRect(brushA)
                    drawRect(brushB)
                    drawRect(brushC)
                }
            },
    )
}
