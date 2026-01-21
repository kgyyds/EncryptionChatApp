package com.kgapp.encryptionchat.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    onOpenChat: (String) -> Unit
) {
    val viewModel: RecentViewModel = viewModel(factory = RepositoryViewModelFactory(repository))
    val state = viewModel.state.collectAsStateWithLifecycle()
    val unreadCounts = UnreadCounter.counts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val menuTarget = remember { mutableStateOf<RecentViewModel.RecentItem?>(null) }
    val confirmTarget = remember { mutableStateOf<RecentViewModel.RecentItem?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        messageSyncManager.updates.collect {
            viewModel.refresh()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("最近聊天") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            items(state.value.items, key = { it.uid }) { item ->
                val displayName = item.remark.ifBlank { item.uid }
                val unread = unreadCounts.value[item.uid] ?: 0

                SwipeableRecentItem(
                    modifier = Modifier,
                    item = item,
                    displayName = displayName,
                    unread = unread,
                    onOpenChat = { onOpenChat(item.uid) },
                    onOpenMenu = { menuTarget.value = item },
                    onTogglePinned = { viewModel.togglePinned(item.uid) },
                    onHide = { viewModel.hideFromRecent(item.uid) }
                )

                Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
        }
    }

    DropdownMenu(
        expanded = menuTarget.value != null,
        onDismissRequest = { menuTarget.value = null }
    ) {
        DropdownMenuItem(
            text = { Text("删除该联系人的全部聊天记录") },
            onClick = {
                confirmTarget.value = menuTarget.value
                menuTarget.value = null
            }
        )
    }

    val confirmItem = confirmTarget.value
    if (confirmItem != null) {
        AlertDialog(
            onDismissRequest = { confirmTarget.value = null },
            title = { Text("删除聊天记录") },
            text = { Text("确认删除该联系人的全部聊天记录？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(confirmItem.uid)
                    UnreadCounter.clear(context, confirmItem.uid)
                    Toast.makeText(context, "已删除聊天记录", Toast.LENGTH_SHORT).show()
                    confirmTarget.value = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget.value = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRecentItem(
    modifier: Modifier = Modifier,
    item: RecentViewModel.RecentItem,
    displayName: String,
    unread: Int,
    onOpenChat: () -> Unit,
    onOpenMenu: () -> Unit,
    onTogglePinned: () -> Unit,
    onHide: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current
    val shape = MaterialTheme.shapes.medium

    val rowWidthPx = remember { mutableFloatStateOf(0f) }
    val openThreshold = rowWidthPx.floatValue * 0.3f
    val velocityThreshold = with(density) { 900.dp.toPx() }

    val offsetX = remember { mutableFloatStateOf(0f) }
    val visible = remember { mutableStateOf(true) }

    val exitDurationMs = 220
    val animateSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 450f)

    suspend fun animateOffsetTo(target: Float) {
        val st = AnimationState(offsetX.floatValue)
        st.animateTo(target, animationSpec = animateSpec) {
            offsetX.floatValue = value
        }
    }

    val handleDragEnd: (Float) -> Unit = { velocity ->
        if (rowWidthPx.floatValue <= 0f) return@let

        val shouldDelete = when {
            velocity < -velocityThreshold -> true
            velocity > velocityThreshold -> false
            else -> offsetX.floatValue <= -openThreshold
        }

        val shouldPin = when {
            velocity > velocityThreshold -> true
            velocity < -velocityThreshold -> false
            else -> offsetX.floatValue >= openThreshold
        }

        if (shouldDelete) {
            scope.launch {
                animateOffsetTo(-rowWidthPx.floatValue)
                visible.value = false
                delay(exitDurationMs.toLong())
                onHide()
            }
        } else if (shouldPin) {
            onTogglePinned()
            scope.launch { animateOffsetTo(0f) }
        } else {
            scope.launch { animateOffsetTo(0f) }
        }
    }

    AnimatedVisibility(
        visible = visible.value,
        exit = shrinkVertically(animationSpec = tween(exitDurationMs)) +
            fadeOut(animationSpec = tween(exitDurationMs))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(shape)

                // ✅ 只在确定横向拖拽后 consume，避免抢点击
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val velocityTracker = VelocityTracker()
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchSlop = viewConfig.touchSlop

                        var totalDx = 0f
                        var totalDy = 0f
                        var dragging = false

                        fun handleDragDelta(delta: Float) {
                            val rawNext = offsetX.floatValue + delta
                            val maxOffset = rowWidthPx.floatValue
                            val minOffset = -rowWidthPx.floatValue

                            val adjustedDelta =
                                if (rawNext > maxOffset || rawNext < minOffset) delta * 0.3f else delta

                            offsetX.floatValue =
                                (offsetX.floatValue + adjustedDelta).coerceIn(minOffset, maxOffset)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUpIgnoreConsumed()) {
                                if (dragging) {
                                    val v = velocityTracker.calculateVelocity().x
                                    // ✅ 修复：这里别 return@let，直接执行即可
                                    if (rowWidthPx.floatValue > 0f) {
                                        val shouldDelete = when {
                                            v < -velocityThreshold -> true
                                            v > velocityThreshold -> false
                                            else -> offsetX.floatValue <= -openThreshold
                                        }
                                        val shouldPin = when {
                                            v > velocityThreshold -> true
                                            v < -velocityThreshold -> false
                                            else -> offsetX.floatValue >= openThreshold
                                        }

                                        if (shouldDelete) {
                                            scope.launch {
                                                animateOffsetTo(-rowWidthPx.floatValue)
                                                visible.value = false
                                                delay(exitDurationMs.toLong())
                                                onHide()
                                            }
                                        } else if (shouldPin) {
                                            onTogglePinned()
                                            scope.launch { animateOffsetTo(0f) }
                                        } else {
                                            scope.launch { animateOffsetTo(0f) }
                                        }
                                    }
                                    change.consume()
                                }
                                break
                            }

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (!dragging) {
                                totalDx += dx
                                totalDy += dy

                                if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) {
                                    dragging = true
                                    change.consume()
                                    handleDragDelta(totalDx)
                                    totalDx = 0f
                                    totalDy = 0f
                                }
                            } else {
                                handleDragDelta(dx)
                                change.consume()
                            }
                        }
                    }
                }

                // ✅ 点击/长按交给标准手势（稳定）
                .combinedClickable(
                    onClick = onOpenChat,
                    onLongClick = onOpenMenu
                )

                .then(
                    if (rowWidthPx.floatValue == 0f) {
                        Modifier.onSizeChanged { rowWidthPx.floatValue = it.width.toFloat() }
                    } else {
                        Modifier
                    }
                )
        ) {
            val swipeProgress = if (rowWidthPx.floatValue > 0f) {
                (abs(offsetX.floatValue) / rowWidthPx.floatValue).coerceIn(0f, 1f)
            } else 0f

            val leftVisible = offsetX.floatValue < 0f
            val rightVisible = offsetX.floatValue > 0f
            val leftEmphasis = offsetX.floatValue <= -openThreshold
            val rightEmphasis = offsetX.floatValue >= openThreshold

            if (leftVisible) {
                SwipeBackground(
                    color = Color(0xFFE53935).copy(alpha = swipeProgress),
                    text = "删除",
                    emphasize = leftEmphasis
                )
            } else if (rightVisible) {
                SwipeBackground(
                    color = Color(0xFF2196F3).copy(alpha = swipeProgress),
                    text = if (item.pinned) "取消置顶" else "置顶",
                    emphasize = rightEmphasis
                )
            }

            Row(
                modifier = Modifier
                    .offset { IntOffset(offsetX.floatValue.roundToInt(), 0) }
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarPlaceholder(
                    text = displayName,
                    unreadCount = unread,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = item.lastText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = item.lastTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SwipeBackground(
    color: Color,
    text: String,
    emphasize: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.graphicsLayer {
                val scale = if (emphasize) 1.08f else 1f
                scaleX = scale
                scaleY = scale
                alpha = if (emphasize) 1f else 0.9f
            }
        )
    }
}

@Composable
private fun AvatarPlaceholder(
    text: String,
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    val initial = text.trim().firstOrNull()?.toString()?.uppercase().orEmpty()
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (initial.isBlank()) "?" else initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }

        if (unreadCount > 0) {
            val badgeText = if (unreadCount > 99) "99+" else unreadCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}
