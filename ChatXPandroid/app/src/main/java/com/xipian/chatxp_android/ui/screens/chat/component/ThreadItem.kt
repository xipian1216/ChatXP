package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.preview.previewChatSessions
import com.xipian.chatxp_android.ui.token.SmallIconSize
import com.xipian.chatxp_android.ui.token.Space2
import com.xipian.chatxp_android.ui.token.Space4
import com.xipian.chatxp_android.ui.token.ThreadItemHorizontalPadding
import com.xipian.chatxp_android.ui.token.ThreadItemVerticalPadding
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadItem(
    title: String,
    isPinned: Boolean,
    isSelected: Boolean,
    isDeleteEnabled: Boolean,
    onClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var pendingPressPosition by remember { mutableStateOf<Offset?>(null) }
    var menuAnchorPosition by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { itemSize = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        pendingPressPosition = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        ).position
                    }
                }
                .combinedClickable(
                    onClick = {
                        pendingPressPosition = null
                        onClick()
                    },
                    onLongClick = {
                        menuAnchorPosition = pendingPressPosition
                            ?: Offset(
                                x = itemSize.width / 2f,
                                y = itemSize.height / 2f
                            )
                        pendingPressPosition = null
                        isMenuExpanded = true
                    }
                ),
            shape = MaterialTheme.shapes.medium,
            color = if (isSelected || isPinned) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ThreadItemHorizontalPadding,
                    vertical = ThreadItemVerticalPadding
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.pin),
                        contentDescription = stringResource(R.string.icon_pinned),
                        modifier = Modifier.size(SmallIconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Space2))
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier.offset {
                IntOffset(
                    menuAnchorPosition.x.roundToInt(),
                    menuAnchorPosition.y.roundToInt()
                )
            }
        ) {
            ThreadSessionMenu(
                expanded = isMenuExpanded,
                isPinned = isPinned,
                isDeleteEnabled = isDeleteEnabled,
                onDismiss = {
                    isMenuExpanded = false
                    pendingPressPosition = null
                },
                onRenameClick = {
                    isMenuExpanded = false
                    pendingPressPosition = null
                    onRenameRequest()
                },
                onTogglePinClick = {
                    isMenuExpanded = false
                    pendingPressPosition = null
                    onTogglePin()
                },
                onDeleteClick = {
                    isMenuExpanded = false
                    pendingPressPosition = null
                    onDeleteRequest()
                }
            )
        }
    }
}

@ChatComponentPreview
@Composable
private fun ThreadItemPreview() {
    val session = previewChatSessions().first()

    ChatPreviewFrame(contentAlignment = Alignment.TopCenter) {
        ThreadItem(
            title = session.sessionTitle,
            isPinned = true,
            isSelected = false,
            isDeleteEnabled = true,
            onClick = {},
            onRenameRequest = {},
            onTogglePin = {},
            onDeleteRequest = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space4)
        )
    }
}
