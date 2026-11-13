package com.example.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class QltyButtonStyle {
    Primary,
    Secondary,
    Danger
}

@Composable
fun QltyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: QltyButtonStyle = QltyButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fillWidth: Boolean = true,
    testTag: String? = null
) {
    val containerColor = when (style) {
        QltyButtonStyle.Primary -> Color.Transparent
        QltyButtonStyle.Secondary -> QltyColors.Surface2
        QltyButtonStyle.Danger -> QltyColors.Danger.copy(alpha = 0.16f)
    }
    val contentColor = when (style) {
        QltyButtonStyle.Primary -> QltyColors.OnPrimary
        QltyButtonStyle.Secondary -> QltyColors.Text
        QltyButtonStyle.Danger -> QltyColors.Danger
    }
    val border = when (style) {
        QltyButtonStyle.Primary -> null
        QltyButtonStyle.Secondary -> BorderStroke(QltySizes.Hairline, QltyColors.Border)
        QltyButtonStyle.Danger -> BorderStroke(QltySizes.Hairline, QltyColors.Danger.copy(alpha = 0.45f))
    }

    val baseMod = modifier
        .let { if (fillWidth) it.fillMaxWidth() else it }
        .height(QltySizes.ButtonHeight)
        .defaultMinSize(minWidth = QltySizes.TouchTarget, minHeight = QltySizes.ButtonHeight)
        .let { if (testTag != null) it.testTag(testTag) else it }
        .alpha(if (enabled) 1f else 0.5f)

    val bgMod = if (style == QltyButtonStyle.Primary) {
        baseMod
            .clip(QltyRadius.PillShape)
            .background(QltyColors.PrimaryGradient)
    } else {
        baseMod
    }

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = QltyRadius.PillShape,
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = QltySpace.S16),
        modifier = bgMod
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(QltySizes.IconChip),
                color = contentColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(QltySpace.S8))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(QltySizes.IconChip),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(QltySpace.S8))
        }
        Text(
            text = text,
            style = QltyType.BodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!isLoading && trailingIcon != null) {
            Spacer(modifier = Modifier.width(QltySpace.S8))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(QltySizes.IconChip),
                tint = contentColor
            )
        }
    }
}

@Composable
fun QltyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = QltyColors.Text,
    containerColor: Color = QltyColors.Surface2.copy(alpha = 0.88f),
    testTag: String? = null
) {
    Surface(
        shape = QltyRadius.PillShape,
        color = containerColor,
        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
        modifier = modifier
            .size(QltySizes.TouchTarget)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(QltySizes.TouchTarget)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(QltySizes.IconDefault)
            )
        }
    }
}

@Composable
fun QltyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(QltySpace.S16),
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) QltyColors.Primary else QltyColors.Border,
        animationSpec = tween(180),
        label = "card_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) QltyColors.Surface2 else QltyColors.Surface,
        animationSpec = tween(180),
        label = "card_bg"
    )

    Surface(
        shape = QltyRadius.CardShape,
        color = bgColor,
        border = BorderStroke(QltySizes.Hairline, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(QltyRadius.CardShape)
            .let {
                if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it
            }
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S12),
            content = content
        )
    }
}

@Composable
fun QltyListItem(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = QltySizes.TouchTarget)
            .clip(QltyRadius.CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = QltySpace.S12, vertical = QltySpace.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(QltyRadius.PillShape)
                    .background(QltyColors.Surface2)
                    .border(QltySizes.Hairline, QltyColors.Border, QltyRadius.PillShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = QltyColors.PrimaryHighlight,
                    modifier = Modifier.size(QltySizes.IconChip)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
        ) {
            Text(
                text = title,
                style = QltyType.BodyMedium,
                color = QltyColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = QltyType.Caption,
                    color = QltyColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun QltyInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    testTag: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QltySpace.S8)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = QltyType.CaptionMedium,
                color = QltyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QltySizes.InputHeight)
                .clip(QltyRadius.PillShape)
                .background(QltyColors.Surface2)
                .border(
                    width = QltySizes.Hairline,
                    color = if (errorText != null) QltyColors.Danger else QltyColors.Border,
                    shape = QltyRadius.PillShape
                )
                .padding(horizontal = QltySpace.S16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = QltyColors.TextMuted,
                    modifier = Modifier.size(QltySizes.IconChip)
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = QltyType.InputText,
                        color = QltyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = QltyType.InputText,
                    singleLine = singleLine,
                    cursorBrush = SolidColor(QltyColors.PrimaryHighlight),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (testTag != null) it.testTag(testTag) else it }
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = QltyType.Caption,
                color = QltyColors.Danger
            )
        }
    }
}

@Composable
fun QltyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val bgBrush = if (selected) QltyColors.PrimaryGradient else null
    val bgColor = if (selected) Color.Transparent else QltyColors.Surface2
    val textColor = if (selected) QltyColors.OnPrimary else QltyColors.TextMuted
    val borderColor = if (selected) Color.Transparent else QltyColors.Border

    Surface(
        shape = QltyRadius.PillShape,
        color = bgColor,
        border = BorderStroke(QltySizes.Hairline, borderColor),
        modifier = modifier
            .defaultMinSize(minWidth = QltySizes.TouchTarget, minHeight = QltySizes.TouchTarget)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clip(QltyRadius.PillShape)
            .let { if (bgBrush != null) it.background(bgBrush) else it }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = QltySpace.S16, vertical = QltySpace.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(QltySizes.IconChip)
                )
                Spacer(modifier = Modifier.width(QltySpace.S8))
            }
            Text(
                text = label,
                style = QltyType.CaptionMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QltyBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = QltyColors.Surface,
        contentColor = QltyColors.Text,
        shape = QltyRadius.CardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QltySpace.S16)
                .padding(bottom = QltySpace.S24),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S16)
        ) {
            Text(
                text = title,
                style = QltyType.Title,
                color = QltyColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content()
        }
    }
}

@Composable
fun QltyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(QltySizes.TopBarHeight)
            .padding(horizontal = QltySpace.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = QltyType.Title,
                color = QltyColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = QltyType.Caption,
                    color = QltyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QltySpace.S8),
            content = actions
        )
    }
}

data class QltyNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun QltyBottomNav(
    items: List<QltyNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = QltyColors.Surface.copy(alpha = 0.96f),
        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
        shape = QltyRadius.CardShape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QltySpace.S12, vertical = QltySpace.S8)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QltySizes.BottomNavHeight)
                .padding(horizontal = QltySpace.S4, vertical = QltySpace.S4),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val tabBg by animateColorAsState(
                    targetValue = if (selected) QltyColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "nav_tab_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(QltySizes.TouchTarget)
                        .clip(QltyRadius.PillShape)
                        .background(tabBg)
                        .clickable { onSelect(item.route) }
                        .testTag("nav_${item.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = if (selected) QltyColors.PrimaryHighlight else QltyColors.TextMuted,
                            modifier = Modifier.size(QltySizes.IconDefault)
                        )
                        Text(
                            text = item.label,
                            style = QltyType.CaptionMedium,
                            color = if (selected) QltyColors.Text else QltyColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QltyEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    QltyCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = QltySpace.S16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QltySpace.S12)
        ) {
            Box(
                modifier = Modifier
                    .size(QltySizes.TopBarHeight)
                    .clip(QltyRadius.PillShape)
                    .background(QltyColors.Surface2)
                    .border(QltySizes.Hairline, QltyColors.Border, QltyRadius.PillShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = QltyColors.PrimaryHighlight,
                    modifier = Modifier.size(QltySizes.IconDefault)
                )
            }
            Text(
                text = title,
                style = QltyType.Heading,
                color = QltyColors.Text,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = QltyType.Body,
                color = QltyColors.TextMuted,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(QltySpace.S4))
                QltyButton(
                    text = actionLabel,
                    onClick = onAction,
                    testTag = "empty_state_action"
                )
            }
        }
    }
}

@Composable
fun QltyToast(
    message: String?,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = QltyRadius.PillShape,
            color = if (isError) QltyColors.Danger else QltyColors.Surface2,
            border = BorderStroke(
                QltySizes.Hairline,
                if (isError) QltyColors.Danger else QltyColors.Primary
            ),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = QltySpace.S16)
        ) {
            Text(
                text = message.orEmpty(),
                style = QltyType.CaptionMedium,
                color = if (isError) QltyColors.OnPrimary else QltyColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = QltySpace.S16, vertical = QltySpace.S12)
            )
        }
    }
}

@Composable
fun QltySkeletonBox(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(QltyRadius.CardShape)
            .background(QltyColors.Surface2.copy(alpha = 0.6f))
            .border(QltySizes.Hairline, QltyColors.Border, QltyRadius.CardShape)
    )
}
