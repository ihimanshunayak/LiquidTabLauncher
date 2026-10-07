/*
 * The Liquid Glass integration glue.
 *
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.ihimanshunayak.liquidtab.ui.glass.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from the
 * FreeMusic app's LiquidGlass.kt, which in turn was adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass (GPL-3.0).
 *
 * Difference from FreeMusic: the settings this reads (whether glass is on,
 * whether "reduce dynamic blur" is on) are supplied by the host application
 * through composition locals instead of a Settings store, so this module never
 * depends on any app's data layer.
 */
package com.ihimanshunayak.liquidtab.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.Backdrop
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.drawBackdrop
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.effects.blur
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.effects.colorControls
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.effects.lens
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.highlight.Highlight
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.highlight.HighlightElement
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.internal.ShapeProvider
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.shadow.Shadow

/** Whether the liquid glass surfaces are turned on — supplied by the host app. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

/** Whether "reduce dynamic blur" is on — supplied by the host app. */
val LocalReduceDynamicBlur = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Apple-matched defaults (Echo's GlassEffectConfig()), fixed rather than user sliders. */
private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * A third is nine times fewer pixels than full resolution through the colour
 * matrix, the blur and the lens shader, on as many as six surfaces at once in
 * the middle of a drag, and the blur is what hides the upscale.
 */
private const val GLASS_RESOLUTION_SCALE = 0.33f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against. Pure black
 * or white off the theme's luminance is the only tint that holds against
 * arbitrary artwork.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Selected indicator colour for a glass surface: the inverse of
 * [glassContentColor] rather than the same tint at lower alpha — white in
 * light theme, black in dark theme, so a pill reads as a shaded scrim.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White else Color.Black

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * Keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens
 * refraction or shadow rendering. For small, numerous surfaces — dock tiles,
 * folder backgrounds, control-center buttons — where a full glass pipeline per
 * element would not stay inside the Home-apprame budget.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassTint.copy(alpha = SURFACE_OPACITY) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint (light
 * glass on light theme, dark on dark). Returns the receiver unchanged on devices
 * without RenderEffect support — callers should still gate on [isGlassSupported]
 * to fall back to the regular translucent treatment there.
 *
 * Under "reduce dynamic blur" the surface is filled solid instead, which is what
 * that setting promises everywhere. It is checked here rather than at each call
 * site so there is one answer to it: the host activity also stops recording the
 * backdrop layer when it is on, and a surface that still tried to sample would
 * be sampling a layer nothing is drawing into.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect
 * throws for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(shape: CornerBasedShape): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur = LocalReduceDynamicBlur.current
    val enabled = LocalLiquidGlassEnabled.current
    if (reduceDynamicBlur || !enabled) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val surfaceTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(color = surfaceTintColor.copy(alpha = SURFACE_OPACITY), size = size)
        },
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}
