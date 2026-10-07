/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher - app icon.
 *
 * Name      : AppIcon.kt
 * Version   : 1.0.0
 * Purpose   : One icon on the Home screen: the app's own icon in the launcher's
 *             icon shape, with its label, press animation and long-press
 *             gesture. Icons are the most numerous thing on screen, so this is
 *             written to stay close to allocation-free per frame - the bitmap
 *             is resolved once per package by AppRepository and passed in as a
 *             value.
 *
 * Notes     : The icon plate is drawn by the launcher rather than by the app's
 *             own drawable. That is what keeps a grid of icons from looking
 *             like a bag of different shapes, and it is what lets an
 *             uninstalled app degrade to a readable tile instead of a blank.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics

/**
 * One app shortcut, or a folder rendered on the same plate.
 *
 * [iconSize] is the resolved icon box; the shape's corner radius is a fixed
 * fraction of that box, so a shortcut stays visually identical from a 56dp dock
 * tile to a 96dp Home icon.
 *
 * Long-press opens the menu; the drag is started by the cell that hosts this,
 * which is the layer that knows the gesture can move the item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    label: String,
    icon: ImageBitmap?,
    iconSize: Dp,
    showLabel: Boolean,
    pressed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed || pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "iconPress",
    )

    val shape = RoundedCornerShape(percent = 23)
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressedScale; scaleY = pressedScale }
            // One accessibility node per shortcut: the artwork, the label below
            // it and the badge that a folder draws are one thing a finger can
            // hit, so a screen reader should announce them together rather than
            // offering three separate stops.
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .shadow(
                    elevation = if (isPressed) 2.dp else 6.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawWithCache {
                    // Hairline rim, drawn before the artwork so an icon that
                    // failed to load still has an edge.
                    val stroke = Stroke(width = 0.5.dp.toPx())
                    val corner = CornerRadius(size.width * 0.23f)
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.14f),
                            size = Size(size.width, size.height),
                            cornerRadius = corner,
                            style = stroke,
                            topLeft = Offset.Zero,
                        )
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {
                        haptics.play(Haptic.Tap)
                        onClick()
                    },
                    onLongClick = {
                        haptics.play(Haptic.Expand)
                        onLongClick()
                    },
                ),
        ) {
            background?.invoke()

            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(iconSize * 0.06f),
                )
            } else if (background == null) {
                // No artwork and no caller-supplied panel: the initial, which is
                // what an uninstalled or icon-less app gets instead of a hole.
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = (iconSize.value * 0.36f).sp,
                    ),
                    color = glassContentColor().copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            badge?.let {
                Box(Modifier.align(Alignment.TopEnd)) { it() }
            }
        }

        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = glassContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )
        }
    }
}

/**
 * The icon box for a given cell size: the design's icon is a fixed fraction of
 * the cell, so the grid keeps its gutters at every window size.
 */
@Composable
fun iconSizeFor(cell: Dp): Dp {
    val scale by LauncherSettings.iconScale.state.collectAsStateWithLifecycle()
    return cell * 0.78f * scale.coerceIn(0.75f, 1.25f)
}