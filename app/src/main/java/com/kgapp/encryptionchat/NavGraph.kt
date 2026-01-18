package com.kgapp.encryptionchat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.ui.screens.AddContactScreen
import com.kgapp.encryptionchat.ui.screens.ChatScreen
import com.kgapp.encryptionchat.ui.screens.ContactsScreen
import com.kgapp.encryptionchat.ui.screens.DebugScreen
import com.kgapp.encryptionchat.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    data object Contacts : Screen("contacts")
    data object AddContact : Screen("add_contact")
    data object Settings : Screen("settings")
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
        startDestination = Screen.Contacts.route,
        modifier = Modifier
    ) {
        composable(Screen.Contacts.route) {
            ContactsScreen(
                repository = repository,
                onAddContact = { navController.navigate(Screen.AddContact.route) },
                onOpenChat = { uid -> navController.navigate(Screen.Chat.createRoute(uid)) },
                onOpenDebug = { navController.navigate(Screen.Debug.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.AddContact.route) {
            AddContactScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
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
        composable(Screen.Settings.route) {
            SettingsScreen(repository = repository, onBack = { navController.popBackStack() })
        }
    }
}
