package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.ChatSession
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.preview.PreviewSelectedSessionId
import com.xipian.chatxp_android.ui.screens.chat.preview.previewChatSessions
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.token.DrawerBackdropAlpha
import com.xipian.chatxp_android.ui.token.DrawerCloseThresholdFraction
import com.xipian.chatxp_android.ui.token.DrawerEdgeSwipeWidth
import com.xipian.chatxp_android.ui.token.DrawerEnterDurationMillis
import com.xipian.chatxp_android.ui.token.DrawerExitDurationMillis
import com.xipian.chatxp_android.ui.token.DrawerOpenThresholdFraction
import com.xipian.chatxp_android.ui.token.DrawerWidthFraction
import com.xipian.chatxp_android.ui.token.StandardEasing
import kotlin.math.roundToInt

@Composable
fun ThreadDrawer(
    isOpen: Boolean,
    sessions: List<ChatSession>,
    selectedSessionId: String?,
    isGenerating: Boolean,
    searchQuery: String,
    title: String,
    searchPlaceholder: String,
    emptySearchText: String,
    chatButtonText: String,
    profileLabel: String,
    onSearchQueryChange: (String) -> Unit,
    onSessionSelected: (String) -> Unit,
    onSessionRenamed: (String, String) -> Unit,
    onSessionPinToggled: (String) -> Unit,
    onSessionDeleted: (String) -> Unit,
    onClose: () -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var drawerWidthPx by remember { mutableIntStateOf(0) }
    var dragOffsetPx by remember(isOpen) { mutableFloatStateOf(0f) }
    val dragProgress = if (drawerWidthPx == 0) {
        1f
    } else {
        (1f + dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(
                animationSpec = tween(DrawerEnterDurationMillis, easing = StandardEasing)
            ),
            exit = fadeOut(
                animationSpec = tween(DrawerExitDurationMillis, easing = StandardEasing)
            )
        ) {
            DrawerBackdrop(
                backdropAlpha = DrawerBackdropAlpha * dragProgress,
                onClick = onClose
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(DrawerEnterDurationMillis, easing = StandardEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(DrawerExitDurationMillis, easing = StandardEasing)
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(DrawerWidthFraction)
                    .fillMaxHeight()
                    .onSizeChanged { drawerWidthPx = it.width }
                    .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                    .pointerInput(isOpen, drawerWidthPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount)
                                    .coerceIn(-drawerWidthPx.toFloat(), 0f)
                            },
                            onDragEnd = {
                                if (-dragOffsetPx >= drawerWidthPx * DrawerCloseThresholdFraction) {
                                    onClose()
                                } else {
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = { dragOffsetPx = 0f }
                        )
                    },
                shape = ChatCorner.Drawer,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ThreadDrawerHeader(title = title, onCloseClick = onClose)
                    ThreadSearchField(
                        searchQuery = searchQuery,
                        placeholder = searchPlaceholder,
                        onSearchQueryChange = onSearchQueryChange,
                        onClearSearchClick = { onSearchQueryChange("") }
                    )
                    ThreadList(
                        sessions = sessions,
                        selectedSessionId = selectedSessionId,
                        isGenerating = isGenerating,
                        emptySearchText = emptySearchText,
                        onSessionSelected = onSessionSelected,
                        onSessionRenameRequested = { renameTargetId = it },
                        onSessionPinToggled = onSessionPinToggled,
                        onSessionDeleteRequested = { deleteTargetId = it },
                        modifier = Modifier.weight(1f)
                    )
                    ThreadDrawerBottomBar(
                        chatButtonText = chatButtonText,
                        profileLabel = profileLabel,
                        onNewChatClick = onNewChatClick,
                        onProfileClick = onProfileClick
                    )
                }
            }
        }
    }

    renameTargetId?.let { sessionId ->
        sessions.firstOrNull { it.sessionId == sessionId }?.let { session ->
            RenameThreadDialog(
                currentTitle = session.sessionTitle,
                onDismiss = { renameTargetId = null },
                onConfirm = { title ->
                    renameTargetId = null
                    onSessionRenamed(sessionId, title)
                }
            )
        }
    }

    deleteTargetId?.let { sessionId ->
        DeleteThreadDialog(
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                deleteTargetId = null
                onSessionDeleted(sessionId)
            }
        )
    }
}

@Composable
fun DrawerEdgeSwipeArea(
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val density = LocalDensity.current
    val drawerWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx() * DrawerWidthFraction
    }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(DrawerEdgeSwipeWidth)
            .fillMaxHeight()
            .pointerInput(drawerWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistancePx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f) {
                            change.consume()
                            dragDistancePx += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (dragDistancePx >= drawerWidthPx * DrawerOpenThresholdFraction) {
                            onOpen()
                        }
                        dragDistancePx = 0f
                    },
                    onDragCancel = { dragDistancePx = 0f }
                )
            }
    )
}

@ChatComponentPreview
@Composable
private fun ThreadDrawerPreview() {
    var searchQuery by remember { mutableStateOf("") }

    ChatPreviewFrame {
        ThreadDrawer(
            isOpen = true,
            sessions = previewChatSessions(),
            selectedSessionId = PreviewSelectedSessionId,
            isGenerating = false,
            searchQuery = searchQuery,
            title = stringResource(R.string.drawer_title),
            searchPlaceholder = stringResource(R.string.drawer_search_placeholder),
            emptySearchText = stringResource(R.string.drawer_search_empty),
            chatButtonText = stringResource(R.string.drawer_chat_button),
            profileLabel = stringResource(R.string.drawer_profile_label),
            onSearchQueryChange = { searchQuery = it },
            onSessionSelected = {},
            onSessionRenamed = { _, _ -> },
            onSessionPinToggled = {},
            onSessionDeleted = {},
            onClose = {},
            onNewChatClick = {},
            onProfileClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
