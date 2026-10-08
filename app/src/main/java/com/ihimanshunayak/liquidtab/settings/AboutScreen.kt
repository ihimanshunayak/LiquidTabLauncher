/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — About.
 *
 * Name      : AboutScreen.kt
 * Version   : 1.0.0
 * Purpose   : Tells the user what this launcher is, which build they are
 *             running, where the Home role stands, and what it is built out of.
 *             A launcher is the one app a user cannot uninstall their way out
 *             of a bad version of, so the screen that says which version it is
 *             has to be reachable, honest and specific — which is why the
 *             build's own date, the device it is running on and the licences of
 *             the code inside it are all stated here rather than paraphrased.
 *
 * Notes     : Every value on this screen is read, never written: the version
 *             comes from the generated [BuildConfig], the device from
 *             `Build`, the Home status from [isDefaultHome]. There is no
 *             card here for a feature that does not exist.
 *
 *             The open-source section is deliberately literal. The backdrop
 *             engine is vendored source, so its Apache-2.0 notice has to
 *             travel with the app, and a notice the user cannot read on the
 *             device is not much of a notice.
 */

package com.ihimanshunayak.liquidtab.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.BuildConfig
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.util.isDefaultHome
import com.ihimanshunayak.liquidtab.util.openDefaultHomeSettings
import com.ihimanshunayak.liquidtab.util.toast

/** Where the launcher's own project lives. Opened in the browser, never in-app. */
private const val PROJECT_URL = "https://github.com/ihimanshunayak"

/**
 * The About screen.
 *
 * [onBack] is supplied by the host so this screen works both as its own page
 * and as a destination inside a larger settings flow.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Read once per entry, not per frame: the Home role can change while the
    // launcher is in the background, and it should be re-read the next time
    // this screen is opened rather than cost a PackageManager query per
    // recomposition.
    val homeStatus = remember { mutableStateOf(isDefaultHome(context)) }
    var showNotices by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        AboutTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AboutHeader()
            }

            item {
                AboutSection(title = stringResource(R.string.about_section_build)) {
                    FactRow(
                        label = stringResource(R.string.about_label_version),
                        value = stringResource(
                            R.string.about_version_full,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    )
                    RowDivider()
                    FactRow(label = stringResource(R.string.about_label_built), value = BuildConfig.BUILD_DATE)
                    RowDivider()
                    FactRow(label = stringResource(R.string.about_label_package), value = BuildConfig.APPLICATION_ID)
                    RowDivider()
                    FactRow(
                        label = stringResource(R.string.about_label_design_system),
                        value = stringResource(R.string.about_value_design_system),
                    )
                }
            }

            item {
                AboutSection(title = stringResource(R.string.about_section_home)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (homeStatus.value) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (homeStatus.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (homeStatus.value) {
                                    stringResource(R.string.about_home_yes)
                                } else {
                                    stringResource(R.string.about_home_no)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (homeStatus.value) {
                                    stringResource(R.string.about_home_yes_body)
                                } else {
                                    stringResource(R.string.about_home_no_body)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!homeStatus.value) {
                        RowDivider()
                        NavRow(
                            title = stringResource(R.string.about_choose_home),
                            subtitle = stringResource(R.string.about_choose_home_summary),
                            onClick = {
                                if (openDefaultHomeSettings(context)) homeStatus.value = isDefaultHome(context)
                                else toast(context, context.getString(R.string.toast_no_home_setting))
                            },
                        )
                    }
                }
            }

            item {
                AboutSection(title = stringResource(R.string.about_section_device)) {
                    FactRow(label = stringResource(R.string.about_label_android), value = Build.VERSION.RELEASE)
                    RowDivider()
                    FactRow(label = stringResource(R.string.about_label_api), value = Build.VERSION.SDK_INT.toString())
                    RowDivider()
                    FactRow(
                        label = stringResource(R.string.about_label_device),
                        value = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    )
                    RowDivider()
                    FactRow(
                        label = stringResource(R.string.about_label_screens),
                        value = stringResource(R.string.about_value_screens),
                    )
                }
            }

            item {
                AboutSection(title = stringResource(R.string.about_section_open_source)) {
                    NavRow(
                        title = stringResource(R.string.about_show_notices),
                        subtitle = if (showNotices) {
                            stringResource(R.string.about_show_notices_open)
                        } else {
                            stringResource(R.string.about_show_notices_closed)
                        },
                        onClick = { showNotices = !showNotices },
                    )
                    if (showNotices) {
                        RowDivider()
                        NoticeBlock()
                    }
                }
            }

            item {
                AboutSection(title = stringResource(R.string.about_section_credits)) {
                    FactRow(
                        label = stringResource(R.string.about_label_built_by),
                        value = stringResource(R.string.about_value_built_by),
                    )
                    RowDivider()
                    FactRow(
                        label = stringResource(R.string.about_label_studio),
                        value = stringResource(R.string.about_value_studio),
                    )
                    RowDivider()
                    LinkRow(
                        label = stringResource(R.string.about_label_project),
                        value = PROJECT_URL,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Chrome ────────────────────────────────────────────────────────────────────

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    onClickLabel = stringResource(R.string.action_back),
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The identity block: name, one-line purpose, version. */
@Composable
private fun AboutHeader() {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        val pillShape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.about_version_short, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The plate every section sits in. Shared with the Settings screen rather than
 * re-declared here: the two screens are one visual language, and a plate that
 * drifts is a plate users have to learn twice.
 */
@Composable
private fun AboutSection(
    title: String,
    rows: @Composable ColumnScope.() -> Unit,
) = SettingsSection(title = title, rows = rows)

/**
 * The licence text itself, one entry per dependency, each with the reason it is
 * in the build. Naming the licence without the component — or the component
 * without the licence — would be a notice in form only.
 */
@Composable
private fun NoticeBlock() {
    // Resolved outside the semantics block: the block is a snapshot lambda, not
    // a composable scope, so a stringResource call inside it would not compile.
    val noticesDescription = stringResource(R.string.about_software_notices)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = noticesDescription
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NOTICES.forEach { notice ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = notice.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = notice.licence,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(notice.roleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.about_licence_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One dependency, its licence, and why the launcher needs it.
 *
 * [name] and [licence] stay as literals: a licence is attributed under the name
 * and identifier it was granted under, and a translated component name or an
 * altered licence identifier would misstate the grant. Only [roleRes] — the
 * sentence explaining why the dependency is here — is localised.
 */
private data class Notice(
    val name: String,
    val licence: String,
    @StringRes val roleRes: Int,
)

/**
 * What is actually compiled into this app. Each entry corresponds to a real
 * dependency or vendored source tree in the project — the list is the build,
 * not a generic acknowledgement.
 */
private val NOTICES = listOf(
    Notice(
        name = "backdrop — Kyant0",
        licence = "Apache License 2.0 · Copyright 2025 Kyant0",
        roleRes = R.string.notice_role_backdrop,
    ),
    Notice(
        name = "Jetpack Compose",
        licence = "Apache License 2.0 · Copyright The Android Open Source Project",
        roleRes = R.string.notice_role_compose,
    ),
    Notice(
        name = "AndroidX Media3",
        licence = "Apache License 2.0 · Copyright The Android Open Source Project",
        roleRes = R.string.notice_role_media3,
    ),
    Notice(
        name = "Coil",
        licence = "Apache License 2.0 · Copyright Coil Contributors",
        roleRes = R.string.notice_role_coil,
    ),
    Notice(
        name = "Kotlin & kotlinx.coroutines, kotlinx.serialization",
        licence = "Apache License 2.0 · Copyright JetBrains s.r.o. and contributors",
        roleRes = R.string.notice_role_kotlin,
    ),
)
