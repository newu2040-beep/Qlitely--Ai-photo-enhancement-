package com.example.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.data.AppearanceSettings
import com.example.core.designsystem.QltyBottomSheet
import com.example.core.designsystem.QltyButton
import com.example.core.designsystem.QltyButtonStyle
import com.example.core.designsystem.QltyCard
import com.example.core.designsystem.QltyChip
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyListItem
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyThemePreset
import com.example.core.designsystem.QltyTopBar
import com.example.core.designsystem.QltyType
import com.example.core.domain.AppPreferences
import com.example.core.domain.ExportFormat
import com.example.core.domain.OutputResolution
import com.example.core.domain.ProcessingMode

@Composable
fun SettingsScreen(
    userDisplayName: String,
    userEmail: String,
    userPhotoUrl: String?,
    preferences: AppPreferences,
    appearance: AppearanceSettings,
    notificationGranted: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectThemePreset: (QltyThemePreset) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenGalleryPicker: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onUpdatePreferences: ((AppPreferences) -> AppPreferences) -> Unit,
    onDeleteCloudData: () -> Unit,
    onSignOut: () -> Unit,
    contentPadding: PaddingValues
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        QltyTopBar(
            title = "Settings",
            subtitle = "Themes, permissions & studio defaults"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QltySpace.S16)
                .padding(bottom = contentPadding.calculateBottomPadding() + QltySpace.S24),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S24)
        ) {
            // Google Account Card
            QltyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    Surface(
                        shape = QltyRadius.PillShape,
                        color = QltyColors.Surface2,
                        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(QltyRadius.PillShape)
                    ) {
                        if (!userPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userPhotoUrl,
                                contentDescription = "User avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userDisplayName.firstOrNull()?.uppercaseChar() ?: 'Q').toString(),
                                    style = QltyType.Heading,
                                    color = QltyColors.Text
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
                    ) {
                        Text(
                            text = userDisplayName.ifBlank { "QLITEY Account" },
                            style = QltyType.Heading,
                            color = QltyColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userEmail.ifBlank { "Signed in with Google" },
                            style = QltyType.Caption,
                            color = QltyColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Appearance & Lore Themes Section
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text("Appearance & Studio Themes", style = QltyType.Heading, color = QltyColors.Text)

                QltyCard {
                    QltyListItem(
                        title = if (appearance.isDarkMode) "Dark Mode" else "Light Mode",
                        subtitle = if (appearance.isDarkMode) {
                            "Deep studio dark canvas active"
                        } else {
                            "Bright high-contrast studio canvas active"
                        },
                        leadingIcon = if (appearance.isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        trailingContent = {
                            Switch(
                                checked = appearance.isDarkMode,
                                onCheckedChange = onToggleDarkMode,
                                colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary),
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }
                    )

                    Text("Lore Theme Palette", style = QltyType.BodyMedium, color = QltyColors.Text)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                    ) {
                        QltyThemePreset.entries.forEach { preset ->
                            val isSelected = appearance.themePreset == preset
                            val primaryColor = if (appearance.isDarkMode) preset.darkPrimary else preset.lightPrimary
                            val highlightColor = if (appearance.isDarkMode) preset.darkPrimaryHighlight else preset.lightPrimaryHighlight

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(QltySpace.S8),
                                modifier = Modifier
                                    .width(76.dp)
                                    .clip(QltyRadius.CardShape)
                                    .clickable { onSelectThemePreset(preset) }
                                    .padding(vertical = QltySpace.S4)
                                    .testTag("theme_preset_${preset.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(QltyRadius.PillShape)
                                        .background(Brush.horizontalGradient(listOf(primaryColor, highlightColor)))
                                        .border(
                                            width = if (isSelected) 2.dp else QltySizes.Hairline,
                                            color = if (isSelected) QltyColors.Text else QltyColors.Border,
                                            shape = QltyRadius.PillShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected theme",
                                            tint = QltyColors.OnPrimary,
                                            modifier = Modifier.size(QltySizes.IconChip)
                                        )
                                    }
                                }
                                Text(
                                    text = preset.title,
                                    style = if (isSelected) QltyType.CaptionMedium else QltyType.Caption,
                                    color = if (isSelected) QltyColors.PrimaryHighlight else QltyColors.TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Permissions & Scoped File Access Section
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text("Permissions & File Access", style = QltyType.Heading, color = QltyColors.Text)

                QltyCard {
                    QltyListItem(
                        title = "Enhancement Notifications",
                        subtitle = when {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                                "Enabled automatically on your Android version"
                            notificationGranted ->
                                "Granted • Alerts when enhancements & exports finish"
                            else ->
                                "Tap to allow completion notifications"
                        },
                        leadingIcon = Icons.Rounded.Notifications,
                        trailingContent = {
                            Surface(
                                shape = QltyRadius.PillShape,
                                color = if (notificationGranted) QltyColors.Success.copy(alpha = 0.16f) else QltyColors.Primary.copy(alpha = 0.2f),
                                border = BorderStroke(
                                    QltySizes.Hairline,
                                    if (notificationGranted) QltyColors.Success.copy(alpha = 0.5f) else QltyColors.Primary
                                )
                            ) {
                                Text(
                                    text = if (notificationGranted) "Granted" else "Grant",
                                    style = QltyType.CaptionMedium,
                                    color = if (notificationGranted) QltyColors.Success else QltyColors.PrimaryHighlight,
                                    modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                                )
                            }
                        },
                        onClick = onRequestNotificationPermission,
                        testTag = "settings_perm_notifications"
                    )

                    QltyListItem(
                        title = "Photo Gallery Picker",
                        subtitle = "Zero-permission scoped system access • Tap to import photos",
                        leadingIcon = Icons.Rounded.PhotoLibrary,
                        trailingContent = {
                            Text(
                                text = "Open",
                                style = QltyType.CaptionMedium,
                                color = QltyColors.PrimaryHighlight
                            )
                        },
                        onClick = onOpenGalleryPicker,
                        testTag = "settings_perm_gallery"
                    )

                    QltyListItem(
                        title = "Device Files & Folders (SAF)",
                        subtitle = "Browse Downloads, Drive, or SD folders without broad storage permissions",
                        leadingIcon = Icons.Rounded.FolderOpen,
                        trailingContent = {
                            Text(
                                text = "Browse",
                                style = QltyType.CaptionMedium,
                                color = QltyColors.PrimaryHighlight
                            )
                        },
                        onClick = onOpenFileBrowser,
                        testTag = "settings_perm_files"
                    )

                    Text(
                        text = "QLITEY follows Google Play's Photo & Video privacy standard: system Photo Picker and Storage Access Framework grant instant, user-controlled access to gallery and files with zero invasive storage permissions.",
                        style = QltyType.Caption,
                        color = QltyColors.TextMuted
                    )
                }
            }

            // Processing & Export Defaults
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text("Studio Defaults", style = QltyType.Heading, color = QltyColors.Text)

                QltyCard {
                    Text("Default Processing Path", style = QltyType.BodyMedium, color = QltyColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)) {
                        ProcessingMode.entries.forEach { mode ->
                            QltyChip(
                                label = mode.label,
                                selected = preferences.defaultProcessingMode == mode.wire,
                                onClick = {
                                    onUpdatePreferences { it.copy(defaultProcessingMode = mode.wire) }
                                },
                                testTag = "pref_mode_${mode.wire}"
                            )
                        }
                    }

                    Text("Default Export Format", style = QltyType.BodyMedium, color = QltyColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)) {
                        ExportFormat.entries.forEach { fmt ->
                            QltyChip(
                                label = fmt.wire,
                                selected = preferences.defaultFormat == fmt.wire,
                                onClick = {
                                    onUpdatePreferences { it.copy(defaultFormat = fmt.wire) }
                                },
                                testTag = "pref_format_${fmt.wire}"
                            )
                        }
                    }

                    Text("Default Resolution", style = QltyType.BodyMedium, color = QltyColors.Text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                    ) {
                        listOf(
                            OutputResolution.ORIGINAL,
                            OutputResolution.X2,
                            OutputResolution.UHD_4K
                        ).forEach { res ->
                            QltyChip(
                                label = res.label,
                                selected = preferences.defaultResolution == res.wire,
                                onClick = {
                                    onUpdatePreferences { it.copy(defaultResolution = res.wire) }
                                },
                                testTag = "pref_res_${res.wire}"
                            )
                        }
                    }

                    QltyListItem(
                        title = "Auto Save to Gallery",
                        subtitle = "Export completed results to Pictures/QLITEY automatically",
                        leadingIcon = Icons.Rounded.Download,
                        trailingContent = {
                            Switch(
                                checked = preferences.autoSave,
                                onCheckedChange = { checked ->
                                    onUpdatePreferences { it.copy(autoSave = checked) }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary),
                                modifier = Modifier.testTag("settings_autosave_switch")
                            )
                        }
                    )
                }
            }

            // Privacy & Consent Controls
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text("Privacy & Cloud AI", style = QltyType.Heading, color = QltyColors.Text)

                QltyCard {
                    QltyListItem(
                        title = "Strip GPS Location on Export",
                        subtitle = "Remove location EXIF tags when saving enhanced photos",
                        leadingIcon = Icons.Rounded.Lock,
                        trailingContent = {
                            Switch(
                                checked = preferences.stripLocationMetadata,
                                onCheckedChange = { checked ->
                                    onUpdatePreferences { it.copy(stripLocationMetadata = checked) }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary),
                                modifier = Modifier.testTag("settings_strip_gps_switch")
                            )
                        }
                    )

                    QltyListItem(
                        title = "Cloud AI Upload Consent",
                        subtitle = if (preferences.cloudAiConsentGranted) {
                            "Granted • Toggle off to require prompt before next upload"
                        } else {
                            "Not granted • Prompt shown before any Cloud AI upload"
                        },
                        leadingIcon = Icons.Rounded.Cloud,
                        trailingContent = {
                            Switch(
                                checked = preferences.cloudAiConsentGranted,
                                onCheckedChange = { checked ->
                                    onUpdatePreferences { it.copy(cloudAiConsentGranted = checked) }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary),
                                modifier = Modifier.testTag("settings_cloud_consent_switch")
                            )
                        }
                    )

                    QltyListItem(
                        title = "Privacy Policy & Data Handling",
                        subtitle = "Learn how local & Cloud AI data are handled",
                        leadingIcon = Icons.Rounded.Security,
                        onClick = { showPrivacySheet = true },
                        testTag = "settings_privacy_policy"
                    )

                    QltyListItem(
                        title = "Delete All Cloud History",
                        subtitle = "Remove all synced job & library records from Firestore",
                        leadingIcon = Icons.Rounded.DeleteForever,
                        onClick = { showDeleteConfirm = true },
                        testTag = "settings_delete_cloud_data"
                    )
                }
            }

            // Sign Out
            QltyButton(
                text = "Sign Out",
                onClick = onSignOut,
                leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                style = QltyButtonStyle.Secondary,
                testTag = "settings_sign_out_button"
            )
        }
    }

    if (showDeleteConfirm) {
        QltyBottomSheet(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Cloud History?"
        ) {
            Text(
                text = "This removes all synced enhancement jobs and library metadata stored in your private Firestore account.",
                style = QltyType.Body,
                color = QltyColors.TextMuted
            )
            QltyButton(
                text = "Delete All Cloud Records",
                onClick = {
                    showDeleteConfirm = false
                    onDeleteCloudData()
                },
                style = QltyButtonStyle.Danger,
                testTag = "confirm_delete_cloud_data"
            )
            QltyButton(
                text = "Cancel",
                onClick = { showDeleteConfirm = false },
                style = QltyButtonStyle.Secondary
            )
        }
    }

    if (showPrivacySheet) {
        QltyBottomSheet(
            onDismissRequest = { showPrivacySheet = false },
            title = "Privacy & Processing"
        ) {
            QltyListItem(
                title = "On-Device Local AI",
                subtitle = "Images enhanced with Local AI never leave your Android device.",
                leadingIcon = Icons.Rounded.Lock
            )
            QltyListItem(
                title = "Cloud AI via Firebase AI Logic",
                subtitle = "Protected by Firebase App Check. Photos are sent only after explicit consent and are not retained as public URLs.",
                leadingIcon = Icons.Rounded.AutoAwesome
            )
            QltyButton(
                text = "Done",
                onClick = { showPrivacySheet = false },
                style = QltyButtonStyle.Secondary
            )
        }
    }
}
