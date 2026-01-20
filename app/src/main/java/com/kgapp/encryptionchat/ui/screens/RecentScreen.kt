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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.viewmodel.RecentViewModel
import com.kgapp.encryptionchat.ui.viewmodel.RepositoryViewModelFactory
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

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
    val openItemUid = rememberSaveable { mutableStateOf<String?>(null) }

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
                    isOpen = openItemUid.value == item.uid,
                    onOpenChange = { openItemUid.value = it },
                    onOpenChat = { onOpenChat(item.uid) },
                    onTogglePinned = {
                        viewModel.togglePinned(item.uid)
                        openItemUid.value = null
                    },
                    onMarkUnread = {
                        UnreadCounter.markUnread(context, item.uid)
                        Toast.makeText(context, "已标为未读", Toast.LENGTH_SHORT).show()
                        openItemUid.value = null
                    },
                    onHide = {
                        viewModel.hideFromRecent(item.uid)
                        openItemUid.value = null
                    }
                )
                Divider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun SwipeableRecentItem(
    item: RecentViewModel.RecentItem,
    displayName: String,
    unread: Int,
    isOpen: Boolean,
    onOpenChange: (String?) -> Unit,
    onOpenChat: () -> Unit,
    onTogglePinned: () -> Unit,
    onMarkUnread: () -> Unit,
    onHide: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shape = MaterialTheme.shapes.medium
    val actionWidth = 86.dp
    val totalActionsWidth = actionWidth * 3
    val maxOffset = with(density) { -totalActionsWidth.toPx() }
    val threshold = maxOffset * 0.5f
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(isOpen, maxOffset) {
        val target = if (isOpen) maxOffset else 0f
        offsetX.animateTo(target, tween(durationMillis = 200))
    }

    val draggableState = rememberDraggableState { delta ->
        val next = (offsetX.value + delta).coerceIn(maxOffset, 0f)
        scope.launch {
            offsetX.snapTo(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            SwipeActionButton(
                text = if (item.pinned) "取消置顶" else "置顶",
                color = Color(0xFF2196F3),
                modifier = Modifier.width(actionWidth),
                onClick = onTogglePinned
            )
            SwipeActionButton(
                text = "标为未读",
                color = Color(0xFFFBC02D),
                modifier = Modifier.width(actionWidth),
                onClick = onMarkUnread
            )
            SwipeActionButton(
                text = "删除",
                color = Color(0xFFE53935),
                modifier = Modifier.width(actionWidth),
                onClick = onHide
            )
        }
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = {
                        val shouldOpen = offsetX.value <= threshold
                        val target = if (shouldOpen) maxOffset else 0f
                        scope.launch {
                            offsetX.animateTo(target, tween(durationMillis = 200))
                        }
                        onOpenChange(if (shouldOpen) item.uid else null)
                    }
                )
                .clickable {
                    if (isOpen) {
                        onOpenChange(null)
                    } else {
                        onOpenChat()
                    }
                }
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
