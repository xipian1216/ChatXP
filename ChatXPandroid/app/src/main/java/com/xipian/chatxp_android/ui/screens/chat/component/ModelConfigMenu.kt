package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.ModelOption
import com.xipian.chatxp_android.ui.screens.chat.ReasoningModeUi
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.token.DisabledAlpha
import com.xipian.chatxp_android.ui.token.ModelMenuOptionMinHeight
import com.xipian.chatxp_android.ui.token.ModelMenuPadding
import com.xipian.chatxp_android.ui.token.ModelMenuSectionPadding
import com.xipian.chatxp_android.ui.token.ModelMenuWidth
import com.xipian.chatxp_android.ui.token.SmallIconSize
import com.xipian.chatxp_android.ui.token.Space1
import com.xipian.chatxp_android.ui.token.Space2

@Composable
fun ModelConfigMenu(
    modelOptions: List<ModelOption>,
    selectedModelId: String,
    selectedReasoningMode: ReasoningModeUi,
    isCatalogLoaded: Boolean,
    isUpdating: Boolean,
    errorText: String?,
    onModelSelected: (String) -> Unit,
    onReasoningModeSelected: (ReasoningModeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(ModelMenuWidth),
        shape = ChatCorner.Dialog,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(ModelMenuPadding)) {
            ModelMenuSectionLabel(
                text = stringResource(R.string.model_menu_models),
                showLoading = isUpdating
            )
            modelOptions.forEach { option ->
                ModelMenuOption(
                    label = option.displayName,
                    selected = option.id == selectedModelId,
                    enabled = isCatalogLoaded && !isUpdating,
                    onClick = { onModelSelected(option.id) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(
                    horizontal = ModelMenuSectionPadding,
                    vertical = Space1
                )
            )
            ModelMenuSectionLabel(stringResource(R.string.model_menu_reasoning))
            ReasoningModeUi.entries.forEach { mode ->
                val label = stringResource(mode.labelRes)
                ModelMenuOption(
                    label = label,
                    selected = mode == selectedReasoningMode,
                    enabled = isCatalogLoaded && !isUpdating,
                    onClick = { onReasoningModeSelected(mode) }
                )
            }

            val statusText = errorText ?: if (!isCatalogLoaded) {
                stringResource(R.string.model_menu_catalog_unavailable)
            } else null
            statusText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = ModelMenuSectionPadding,
                        vertical = Space2
                    )
                )
            }
        }
    }
}

@Composable
private fun ModelMenuSectionLabel(
    text: String,
    showLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ModelMenuSectionPadding,
                vertical = Space1
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        if (showLoading) {
            val loadingDescription = stringResource(R.string.model_menu_loading)
            CircularProgressIndicator(
                modifier = Modifier
                    .size(SmallIconSize)
                    .semantics { contentDescription = loadingDescription },
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun ModelMenuOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val selectedDescription = stringResource(R.string.model_menu_selected, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ModelMenuOptionMinHeight)
            .alpha(if (enabled || selected) 1f else DisabledAlpha)
            .semantics {
                if (selected) contentDescription = selectedDescription
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ModelMenuSectionPadding, vertical = Space2),
        horizontalArrangement = Arrangement.spacedBy(Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = stringResource(R.string.model_menu_check),
                modifier = Modifier.size(SmallIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.size(SmallIconSize))
        }
    }
}

private val ReasoningModeUi.labelRes: Int
    get() = when (this) {
        ReasoningModeUi.STANDARD -> R.string.model_reasoning_standard
        ReasoningModeUi.ADVANCED -> R.string.model_reasoning_advanced
    }

@ChatComponentPreview
@Composable
private fun ModelConfigMenuPreview() {
    ChatPreviewFrame {
        ModelConfigMenu(
            modelOptions = listOf(
                ModelOption(
                    id = "chat-5.5",
                    displayName = stringResource(R.string.model_fallback_55),
                    reasoningModes = ReasoningModeUi.entries.toSet(),
                    isDefault = true
                ),
                ModelOption(
                    id = "chat-5.6",
                    displayName = stringResource(R.string.model_fallback_56),
                    reasoningModes = ReasoningModeUi.entries.toSet()
                )
            ),
            selectedModelId = "chat-5.5",
            selectedReasoningMode = ReasoningModeUi.STANDARD,
            isCatalogLoaded = true,
            isUpdating = false,
            errorText = null,
            onModelSelected = {},
            onReasoningModeSelected = {}
        )
    }
}
