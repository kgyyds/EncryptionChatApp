package com.kgapp.encryptionchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
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
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            items(state.value.items, key = { it.uid }) { item ->
                val displayName = item.remark.ifBlank { item.uid }
                val unread = unreadCounts.value[item.uid] ?: 0
                SwipeableRecentItem(
                    item = item,
                    displayName = displayName,
                    unread = unread,
                    onOpenChat = { onOpenChat(item.uid) },
                    onOpenMenu = { menuTarget.value = item },
                    onTogglePinned = {
                        viewModel.togglePinned(item.uid)
                    },
                    onHide = {
                        viewModel.hideFromRecent(item.uid)
                    }
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
    val offsetX = remember { Animatable(0f) }
    val removed = remember { mutableStateOf(false) }

    val handleDragDelta: (Float) -> Unit = { delta ->
        val rawNext = offsetX.value + delta
        val maxOffset = rowWidthPx.floatValue
        val minOffset = -rowWidthPx.floatValue
        val adjustedDelta = if (rawNext > maxOffset || rawNext < minOffset) {
            delta * 0.3f
        } else {
            delta
        }
        val next = (offsetX.value + adjustedDelta).coerceIn(minOffset, maxOffset)
        scope.launch {
            offsetX.snapTo(next)
        }
    }

    val handleDragEnd: (Float) -> Unit = handleDragEnd@{ velocity ->
        if (rowWidthPx.floatValue <= 0f) return@handleDragEnd
        val shouldDelete = when {
            velocity < -velocityThreshold -> true
            velocity > velocityThreshold -> false
            else -> offsetX.value <= -openThreshold
        }
        val shouldPin = when {
            velocity > velocityThreshold -> true
            velocity < -velocityThreshold -> false
            else -> offsetX.value >= openThreshold
        }
        if (shouldDelete) {
            scope.launch {
                offsetX.animateTo(
                    -rowWidthPx.floatValue,
                    spring(dampingRatio = 0.9f, stiffness = 450f)
                )
                removed.value = true
                onHide()
            }
        } else if (shouldPin) {
            onTogglePinned()
            scope.launch {
                offsetX.animateTo(
                    0f,
                    spring(dampingRatio = 0.9f, stiffness = 450f)
                )
            }
        } else {
            scope.launch {
                offsetX.animateTo(
                    0f,
                    spring(dampingRatio = 0.9f, stiffness = 450f)
                )
            }
        }
    }

    if (removed.value) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val velocityTracker = VelocityTracker()
                    val down = awaitFirstDown()
                    val touchSlop = viewConfig.touchSlop
                    var totalDx = 0f
                    var totalDy = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            if (dragging) {
                                val velocity = velocityTracker.calculateVelocity().x
                                handleDragEnd(velocity)
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
            .combinedClickable(
                onClick = { onOpenChat() },
                onLongClick = { onOpenMenu() }
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
            (abs(offsetX.value) / rowWidthPx.floatValue).coerceIn(0f, 1f)
        } else {
            0f
        }
        val leftVisible = offsetX.value < 0f
        val rightVisible = offsetX.value > 0f
        if (leftVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFE53935).copy(alpha = swipeProgress)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        } else if (rightVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF2196F3).copy(alpha = swipeProgress)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.pinned) "取消置顶" else "置顶",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
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

        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
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

@Composable
private fun SwipeActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
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
