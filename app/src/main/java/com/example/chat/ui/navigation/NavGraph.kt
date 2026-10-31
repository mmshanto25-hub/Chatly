package com.example.chat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chat.ui.auth.AuthViewModel
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.auth.RegisterScreen
import com.example.chat.ui.chats.ChatListScreen
import com.example.chat.ui.chats.ChatListViewModel
import com.example.chat.ui.contacts.UserSearchScreen
import com.example.chat.ui.contacts.UserSearchViewModel
import com.example.chat.ui.conversation.ConversationScreen
import com.example.chat.ui.conversation.ConversationViewModel
import com.example.chat.ui.profile.ProfileScreen
import com.example.chat.ui.settings.SettingsScreen

@Composable
fun ChatNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    val startDestination = if (authViewModel.isUserLoggedIn()) "chats" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavMain = {
                    navController.navigate("chats") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavRegister = { navController.navigate("register") },
            )
        }
        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                onNavMain = {
                    navController.navigate("chats") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavLogin = { navController.popBackStack() }
            )
        }
        composable("chats") {
            val chatListViewModel: ChatListViewModel = viewModel()
            ChatListScreen(
                viewModel = chatListViewModel,
                onNavToChat = { convId, recipientId, recipientName ->
                    navController.navigate("conversation/$convId/$recipientId/$recipientName")
                },
                onNavToSearch = { navController.navigate("contacts") },
                onNavToProfile = { navController.navigate("profile") },
                onNavToSettings = { navController.navigate("settings") },
                onNavToLogin = {
                    navController.navigate("login") {
                        popUpTo("chats") { inclusive = true }
                    }
                },
                onSwitchAccount = {
                    navController.navigate("chats") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("contacts") {
            val searchViewModel: UserSearchViewModel = viewModel()
            UserSearchScreen(
                viewModel = searchViewModel,
                onNavBack = { navController.popBackStack() },
                onNavToChat = { convId, recipientId, recipientName ->
                    navController.navigate("conversation/$convId/$recipientId/$recipientName") {
                        popUpTo("chats")
                    }
                }
            )
        }
        composable(
            route = "conversation/{conversationId}/{recipientId}/{recipientName}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("recipientId") { type = NavType.StringType },
                navArgument("recipientName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val recipientName = backStackEntry.arguments?.getString("recipientName") ?: "Chat"
            val conversationViewModel = rememberInstanceViewModel(conversationId)

            ConversationScreen(
                viewModel = conversationViewModel,
                recipientName = recipientName,
                onNavBack = { navController.popBackStack() },
                onNavToChat = { convId, recipientId, name ->
                    navController.navigate("conversation/$convId/$recipientId/$name") {
                        popUpTo("chats")
                    }
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                onNavBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavBack = { navController.popBackStack() },
                onNavToLogin = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun rememberInstanceViewModel(conversationId: String): ConversationViewModel {
    return viewModel(key = conversationId) {
        ConversationViewModel(conversationId)
    }
}
