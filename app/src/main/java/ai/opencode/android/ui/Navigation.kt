package ai.opencode.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.opencode.android.ui.chat.ChatScreen
import ai.opencode.android.ui.codeviewer.CodeViewerScreen
import ai.opencode.android.ui.session.SessionListScreen
import ai.opencode.android.ui.settings.SettingsScreen

object Routes {
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"
    const val CODE_VIEWER = "code/{filePath}"

    fun chat(sessionId: String) = "chat/$sessionId"
    fun codeViewer(filePath: String) = "code/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
}

@Composable
fun OpenCodeNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SESSIONS) {
        composable(Routes.SESSIONS) {
            SessionListScreen(
                onSessionClick = { sessionId -> navController.navigate(Routes.chat(sessionId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId") ?: return@composable
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onViewFile = { path -> navController.navigate(Routes.codeViewer(path)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CODE_VIEWER,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType }),
        ) { entry ->
            val filePath = entry.arguments?.getString("filePath")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return@composable
            CodeViewerScreen(
                filePath = filePath,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
