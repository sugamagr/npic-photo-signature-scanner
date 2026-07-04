package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Single-line text input. 52dp tall, `NpicShapes.md`, 1dp border that swaps to Saffron on
 * focus and Terracotta on error. Optional label above and helper/error below.
 *
 * Used by:
 * - Save dialog (name + serial fields)
 * - Filter/search (later releases)
 *
 * Deliberately does NOT use Material's `OutlinedTextField` — that component drags in the
 * container behavior we don't want (floating label, container tint animation) and is hard to
 * bend into a warm-editorial visual.
 */
@Composable
fun NpicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    errorText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    val chrome = LocalNpicChrome.current
    val hasError = errorText != null
    var focused by remember { mutableStateOf(false) }

    val border by animateColorAsState(
        targetValue = when {
            hasError -> chrome.terracotta
            focused  -> NpicColors.Saffron
            !enabled -> chrome.borderSoft
            else     -> chrome.borderStrong
        },
        animationSpec = NpicMotion.fast(),
        label = "field_border",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text  = label,
                color = if (enabled) chrome.inkMuted else chrome.inkFaint,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(NpicSpacing.xxs))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(NpicShapes.md)
                .background(NpicColors.Surface, NpicShapes.md)
                .border(1.dp, border, NpicShapes.md)
                .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = chrome.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text  = placeholder,
                        color = chrome.inkFaint,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyLarge.copy(
                            color = if (enabled) NpicColors.Ink else chrome.inkFaint,
                        ),
                    ),
                    cursorBrush = SolidColor(NpicColors.Saffron),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }

            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = chrome.inkMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .let { m ->
                            if (onTrailingIconClick != null) {
                                m.clip(NpicShapes.full).clickable(onClick = onTrailingIconClick)
                            } else m
                        },
                )
            }
        }

        val subText = errorText ?: helper
        if (subText != null) {
            Spacer(Modifier.size(NpicSpacing.xxs))
            Text(
                text  = subText,
                color = if (hasError) chrome.terracotta else chrome.inkMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "TextFields — states")
@Composable
private fun TextFieldPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.md)) {
                NpicTextField("", {}, label = "Student name", placeholder = "e.g. Ananya Sharma")
                NpicTextField("0007", {}, label = "Serial", keyboardType = KeyboardType.Number, helper = "Auto-incremented per class")
                NpicTextField("A@", {}, label = "Filename", errorText = "Only letters, digits, and underscore")
            }
        }
    }
}
