package com.example.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.designsystem.QltyBottomSheet
import com.example.core.designsystem.QltyButton
import com.example.core.designsystem.QltyButtonStyle
import com.example.core.designsystem.QltyCard
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyListItem
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyType

@Composable
fun WelcomeAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTermsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        // Full-bleed atmospheric mountain artwork matching Screen 1 mockup
        Image(
            painter = painterResource(id = R.drawable.img_welcome_mountains),
            contentDescription = stringResource(R.string.cd_welcome_artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Editorial dark plum top & bottom gradient scrim for legible contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to QltyColors.Bg.copy(alpha = 0.94f),
                            0.42f to QltyColors.Bg.copy(alpha = 0.58f),
                            0.72f to QltyColors.Bg.copy(alpha = 0.78f),
                            1.0f to QltyColors.Bg.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QltySpace.S16, vertical = QltySpace.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(QltySpace.S24))

            // Centered QLITEY glass emblem + wordmark + tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(QltySpace.S12)
            ) {
                Surface(
                    shape = QltyRadius.CardShape,
                    color = QltyColors.Surface2.copy(alpha = 0.85f),
                    border = BorderStroke(QltySizes.Hairline, QltyColors.PrimaryHighlight.copy(alpha = 0.5f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(QltyColors.PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_qlitey_logo),
                            tint = QltyColors.OnPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "QLITEY",
                    style = QltyType.Title,
                    color = QltyColors.Text,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Enhance. Upscale. Rediscover.",
                    style = QltyType.Body,
                    color = QltyColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(QltySpace.S32))

            // Bottom glass auth card
            QltyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = QltyType.Caption,
                        color = QltyColors.Danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                QltyButton(
                    text = if (isLoading) "Signing in..." else "Continue with Google",
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    style = QltyButtonStyle.Primary,
                    leadingIcon = Icons.Rounded.VerifiedUser,
                    isLoading = isLoading,
                    enabled = !isLoading,
                    testTag = "google_sign_in_button"
                )

                Text(
                    text = "Your local photos stay on-device unless you explicitly consent to Cloud AI enhancement.",
                    style = QltyType.Caption,
                    color = QltyColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Terms & Privacy Policy",
                    style = QltyType.CaptionMedium.copy(textDecoration = TextDecoration.Underline),
                    color = QltyColors.PrimaryHighlight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(QltyRadius.SmallShape)
                        .clickable { showTermsSheet = true }
                        .padding(horizontal = QltySpace.S12, vertical = QltySpace.S8)
                        .testTag("terms_privacy_button")
                )
            }
        }
    }

    if (showTermsSheet) {
        QltyBottomSheet(
            onDismissRequest = { showTermsSheet = false },
            title = "Privacy & Terms"
        ) {
            QltyListItem(
                title = "Local AI stays on-device",
                subtitle = "On-device upscaling, denoising, and sharpening never upload your photos.",
                leadingIcon = Icons.Rounded.Lock
            )
            QltyListItem(
                title = "Explicit Cloud AI consent",
                subtitle = "Cloud AI enhancement requests consent before sending a photo via Firebase AI Logic.",
                leadingIcon = Icons.Rounded.AutoAwesome
            )
            QltyListItem(
                title = "Zero-permission Photo Picker",
                subtitle = "QLITEY only reads photos you actively select and strips GPS EXIF on export by default.",
                leadingIcon = Icons.Rounded.VerifiedUser
            )
            QltyButton(
                text = "Close",
                onClick = { showTermsSheet = false },
                style = QltyButtonStyle.Secondary,
                testTag = "close_terms_button"
            )
        }
    }
}
