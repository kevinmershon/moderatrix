package com.moderatrix.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moderatrix.app.ui.config.ConfigScreen
import com.moderatrix.app.ui.history.HistoryScreen
import com.moderatrix.app.ui.log.LogScreen
import com.moderatrix.app.ui.overdue.OverdueScreen
import com.moderatrix.app.ui.theme.ModeratrixTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ModeratrixTheme {
                MainScaffold()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@androidx.compose.runtime.Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab("log", "Today", Icons.Filled.Edit),
        Tab("overdue", "Overdue", Icons.Filled.Warning),
        Tab("history", "History", Icons.Filled.DateRange),
        Tab("config", "Config", Icons.Filled.List)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                launchSingleTop = true
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "log",
            modifier = Modifier.padding(padding)
        ) {
            composable("log") { LogScreen() }
            composable("overdue") { OverdueScreen() }
            composable("history") { HistoryScreen() }
            composable("config") { ConfigScreen() }
        }
    }
}
