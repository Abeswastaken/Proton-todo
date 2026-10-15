package com.abeswastaken.protontodo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.abeswastaken.protontodo.ui.screens.PlaceholderScreen
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    NEW("new", "New", Icons.Filled.AddBox),
    TODO("todo", "To-Do", Icons.Outlined.CheckCircle),
    EVENTS("events", "Events", Icons.Outlined.CalendarMonth),
}

private const val SEARCH = "search"
private const val SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtonTodoApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.HOME.route
    val tab = Tab.entries.firstOrNull { it.route == route }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    if (tab == null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            (tab?.label ?: route.replaceFirstChar { it.uppercase() }).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(headerDate(LocalDate.now()), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(SEARCH) }) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                    IconButton(onClick = { nav.navigate(SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) { PlaceholderScreen("Next 5 events and your 5 oldest open tasks go here.") }
            composable(Tab.NEW.route) { PlaceholderScreen("The New form goes here.") }
            composable(Tab.TODO.route) { PlaceholderScreen("Open tasks, then completed tasks by month.") }
            composable(Tab.EVENTS.route) { PlaceholderScreen("Next 30 days, then the past 12 months.") }
            composable(SEARCH) { PlaceholderScreen("Search titles and descriptions.") }
            composable(SETTINGS) { PlaceholderScreen("Drafts, Appearance, Email, and defaults.") }
        }
    }
}

/** "Wednesday, October 14th" */
fun headerDate(d: LocalDate): String {
    val day = d.dayOfMonth
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$dow, $month $day$suffix"
}
