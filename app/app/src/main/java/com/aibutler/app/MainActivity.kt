package com.aibutler.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibutler.app.calendar.CalendarScreen
import com.aibutler.app.chat.ChatScreen
import com.aibutler.app.files.FilesScreen
import com.aibutler.app.network.ServerConfig
import com.aibutler.app.pairing.PairingScreen
import com.aibutler.app.settings.SettingsScreen
import com.aibutler.app.skills.SkillsScreen
import com.aibutler.app.ui.theme.AiButlerTheme

class MainActivity : ComponentActivity() {
    // 앱이 이미 떠 있는 상태(singleTop)에서 딥링크로 재실행되면 onCreate가 아니라
    // onNewIntent가 호출되므로, 페어링 URI는 Compose가 관찰 가능한 State로 들고 있어야 합니다.
    private val pairingUriState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pairingUriState.value = intent?.data?.toString()
        setContent {
            AiButlerTheme {
                AppRoot(pairingUriState = pairingUriState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairingUriState.value = intent.data?.toString()
    }
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("chat", "채팅", Icons.AutoMirrored.Filled.Chat),
    BottomTab("calendar", "캘린더", Icons.Default.CalendarMonth),
    BottomTab("skills", "스킬", Icons.Default.SmartToy),
    BottomTab("files", "파일", Icons.Default.Folder),
    BottomTab("settings", "설정", Icons.Default.Settings),
)

@Composable
private fun AppRoot(pairingUriState: androidx.compose.runtime.State<String?>) {
    val context = LocalContext.current
    val serverConfig = remember { ServerConfig(context) }
    val connection by serverConfig.connectionFlow.collectAsState(initial = null)
    val pairingUri by pairingUriState

    var forcePairing by remember { mutableStateOf(false) }

    if (connection == null || forcePairing) {
        PairingScreen(
            initialPairingUri = pairingUri,
            onPaired = { forcePairing = false },
        )
    } else {
        MainScaffold(onUnpaired = { forcePairing = true })
    }
}

@Composable
private fun MainScaffold(onUnpaired: () -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(padding),
        ) {
            composable("chat") { ChatScreen() }
            composable("calendar") { CalendarScreen() }
            composable("skills") { SkillsScreen() }
            composable("files") { FilesScreen() }
            composable("settings") { SettingsScreen(onUnpaired = onUnpaired) }
        }
    }
}
