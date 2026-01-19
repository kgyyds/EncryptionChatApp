package com.kgapp.encryptionchat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.kgapp.encryptionchat.ui.screens.AddContactScreen
import com.kgapp.encryptionchat.ui.screens.ChatScreen
import com.kgapp.encryptionchat.ui.screens.ContactsScreen
import com.kgapp.encryptionchat.ui.screens.DebugScreen
import com.kgapp.encryptionchat.ui.screens.KeyManagementScreen
import com.kgapp.encryptionchat.ui.screens.RecentScreen
import com.kgapp.encryptionchat.ui.screens.SettingsScreen
import com.kgapp.encryptionchat.ui.screens.ThemeSettingsScreen

sealed class Screen(val route: String) {
    data object Tabs : Screen("tabs")
    data object Recent : Screen("recent")
    data object Contacts : Screen("contacts")
    data object Settings : Screen("settings")
    data object AddContact : Screen("add_contact")
    data object KeyManagement : Screen("key_management")
    data object ThemeSettings : Screen("theme_settings")
    data object Debug : Screen("debug")
    data object Chat : Screen("chat/{uid}") {
        fun createRoute(uid: String) = "chat/$uid"
    }
}

@Composable
fun EncryptionChatApp(repository: ChatRepository) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Tabs.route,
        modifier = Modifier
    ) {
        composable(Screen.Tabs.route) {
            TabScaffold(
                repository = repository,
                onOpenChat = { uid -> navController.navigate(Screen.Chat.createRoute(uid)) },
                onOpenAddContact = { navController.navigate(Screen.AddContact.route) },
                onOpenKeyManagement = { navController.navigate(Screen.KeyManagement.route) },
                onOpenThemeSettings = { navController.navigate(Screen.ThemeSettings.route) }
            )
        }
        composable(Screen.AddContact.route) {
            AddContactScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.KeyManagement.route) {
            KeyManagementScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            ChatScreen(
                repository = repository,
                uid = uid,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Debug.route) {
            DebugScreen(repository = repository, onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TabScaffold(
    repository: ChatRepository,
    onOpenChat: (String) -> Unit,
    onOpenAddContact: () -> Unit,
    onOpenKeyManagement: () -> Unit,
    onOpenThemeSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(Screen.Recent.route) }
    val tabs = listOf(Screen.Recent, Screen.Contacts, Screen.Settings)

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
                    onOpenKeyManagement = onOpenKeyManagement,
                    onOpenThemeSettings = onOpenThemeSettings
                )
            }
        }
    }
}
