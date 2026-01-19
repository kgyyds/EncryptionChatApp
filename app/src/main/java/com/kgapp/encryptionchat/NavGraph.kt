package com.kgapp.encryptionchat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.sync.MessageSyncManager
import com.kgapp.encryptionchat.ui.screens.AddContactScreen
import com.kgapp.encryptionchat.ui.screens.ChatScreen
import com.kgapp.encryptionchat.ui.screens.ContactsScreen
import com.kgapp.encryptionchat.ui.screens.DebugScreen
import com.kgapp.encryptionchat.ui.screens.DecoyTabs
import com.kgapp.encryptionchat.ui.screens.GateScreen
import com.kgapp.encryptionchat.ui.screens.KeyManagementScreen
import com.kgapp.encryptionchat.ui.screens.RecentScreen
import com.kgapp.encryptionchat.ui.screens.SecuritySettingsScreen
import com.kgapp.encryptionchat.ui.screens.SettingsScreen
import com.kgapp.encryptionchat.ui.screens.ThemeSettingsScreen
import com.kgapp.encryptionchat.security.SessionState
import com.kgapp.encryptionchat.security.SessionMode
import com.kgapp.encryptionchat.security.DuressAction
import com.kgapp.encryptionchat.util.MessagePullPreferences
import com.kgapp.encryptionchat.util.PullMode

sealed class Screen(val route: String) {
    data object Gate : Screen("gate")
    data object Tabs : Screen("tabs")
    data object Recent : Screen("recent")
    data object Contacts : Screen("contacts")
    data object Settings : Screen("settings")
    data object AddContact : Screen("add_contact")
    data object KeyManagement : Screen("key_management")
    data object ThemeSettings : Screen("theme_settings")
    data object SecuritySettings : Screen("security_settings")
    data object DecoyTabs : Screen("decoy_tabs")
    data object DecoyChat : Screen("decoy/chat/{cid}") {
        fun createRoute(cid: String) = "decoy/chat/$cid"
    }
    data object Debug : Screen("debug")
    data object Chat : Screen("chat/{uid}") {
        fun createRoute(uid: String) = "chat/$uid"
    }
}

@Composable
fun EncryptionChatApp(repository: ChatRepository, messageSyncManager: MessageSyncManager) {
    val navController = rememberNavController()
    val sessionMode by SessionState.sessionMode.collectAsState()
    val unlocked by SessionState.unlocked.collectAsState()
    val duressAction by SessionState.duressAction.collectAsState()
    NavHost(
        navController = navController,
        startDestination = Screen.Gate.route,
        modifier = Modifier
    ) {
        composable(Screen.Gate.route) {
            GateScreen(
                onUnlocked = {
                    val target = if (SessionState.sessionMode.value == SessionMode.DURESS) {
                        Screen.DecoyTabs.route
                    } else {
                        Screen.Tabs.route
                    }
                    navController.navigate(target) {
                        popUpTo(Screen.Gate.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Tabs.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.Tabs.route) { inclusive = true }
                }
                return@composable
            }
            TabScaffold(
                repository = repository,
                messageSyncManager = messageSyncManager,
                onOpenChat = { uid -> navController.navigate(Screen.Chat.createRoute(uid)) },
                onOpenAddContact = { navController.navigate(Screen.AddContact.route) },
                onOpenKeyManagement = { navController.navigate(Screen.KeyManagement.route) },
                onOpenThemeSettings = { navController.navigate(Screen.ThemeSettings.route) },
                onOpenSecurity = { navController.navigate(Screen.SecuritySettings.route) }
            )
        }
        composable(Screen.AddContact.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.AddContact.route) { inclusive = true }
                }
                return@composable
            }
            AddContactScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.KeyManagement.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.KeyManagement.route) { inclusive = true }
                }
                return@composable
            }
            KeyManagementScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ThemeSettings.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.ThemeSettings.route) { inclusive = true }
                }
                return@composable
            }
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SecuritySettings.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.SecuritySettings.route) { inclusive = true }
                }
                return@composable
            }
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.DecoyTabs.route) {
            if (!unlocked || sessionMode != SessionMode.DURESS) {
                navController.navigate(Screen.Tabs.route) {
                    popUpTo(Screen.DecoyTabs.route) { inclusive = true }
                }
                return@composable
            }
            DecoyTabs(
                duressAction = duressAction,
                onOpenChat = { id -> navController.navigate(Screen.DecoyChat.createRoute(id)) },
                onBackFromChat = { navController.popBackStack() },
                activeChatId = null
            )
        }
        composable(Screen.DecoyChat.route) { backStackEntry ->
            if (!unlocked || sessionMode != SessionMode.DURESS) {
                navController.navigate(Screen.Tabs.route) {
                    popUpTo(Screen.DecoyChat.route) { inclusive = true }
                }
                return@composable
            }
            val id = backStackEntry.arguments?.getString("cid")
            DecoyTabs(
                duressAction = duressAction,
                onOpenChat = { navController.navigate(Screen.DecoyChat.createRoute(it)) },
                onBackFromChat = { navController.popBackStack() },
                activeChatId = id
            )
        }
        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.Chat.route) { inclusive = true }
                }
                return@composable
            }
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            ChatScreen(
                repository = repository,
                messageSyncManager = messageSyncManager,
                uid = uid,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Debug.route) {
            if (!unlocked || sessionMode != SessionMode.NORMAL) {
                navController.navigate(Screen.Gate.route) {
                    popUpTo(Screen.Debug.route) { inclusive = true }
                }
                return@composable
            }
            DebugScreen(repository = repository, onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TabScaffold(
    repository: ChatRepository,
    messageSyncManager: MessageSyncManager,
    onOpenChat: (String) -> Unit,
    onOpenAddContact: () -> Unit,
    onOpenKeyManagement: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenSecurity: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(Screen.Recent.route) }
    val tabs = listOf(Screen.Recent, Screen.Contacts, Screen.Settings)
    val pullMode by MessagePullPreferences.mode.collectAsState()

    LaunchedEffect(selectedTab, pullMode) {
        if (selectedTab != Screen.Recent.route) {
            messageSyncManager.updateMode(pullMode, null)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { screen ->
                    val (icon, label) = when (screen) {
                        Screen.Recent -> Icons.Outlined.Chat to "最近聊天"
                        Screen.Contacts -> Icons.Outlined.People to "联系人"
                        Screen.Settings -> Icons.Outlined.Settings to "设置"
                        else -> Icons.Outlined.Chat to "最近聊天"
                    }
                    NavigationBarItem(
                        selected = selectedTab == screen.route,
                        onClick = { selectedTab = screen.route },
                        icon = { androidx.compose.material3.Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "tab-transition"
        ) { route ->
            when (route) {
                Screen.Recent.route -> RecentScreen(
                    repository = repository,
                    messageSyncManager = messageSyncManager,
                    onOpenChat = onOpenChat
                )
                Screen.Contacts.route -> ContactsScreen(
                    repository = repository,
                    onAddContact = onOpenAddContact,
                    onOpenChat = onOpenChat,
                    onOpenKeyManagement = onOpenKeyManagement
                )
                Screen.Settings.route -> SettingsScreen(
                    repository = repository,
                    messageSyncManager = messageSyncManager,
                    onOpenKeyManagement = onOpenKeyManagement,
                    onOpenThemeSettings = onOpenThemeSettings,
                    onOpenSecurity = onOpenSecurity
                )
            }
        }
    }
}
