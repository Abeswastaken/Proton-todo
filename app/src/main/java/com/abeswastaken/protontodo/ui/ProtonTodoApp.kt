package com.abeswastaken.protontodo.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.ui.screens.EventsScreen
import com.abeswastaken.protontodo.ui.screens.FormMode
import com.abeswastaken.protontodo.ui.screens.FormState
import com.abeswastaken.protontodo.ui.screens.HomeScreen
import com.abeswastaken.protontodo.ui.screens.ItemForm
import com.abeswastaken.protontodo.ui.screens.SearchScreen
import com.abeswastaken.protontodo.ui.screens.SettingsScreen
import com.abeswastaken.protontodo.ui.screens.TodoScreen
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
private const val ITEM = "item/{id}"
private const val DRAFT = "draft/{id}"

/** Holds "unsaved changes" so any navigation away from a form asks first. */
class LeaveGuard {
    var dirty by mutableStateOf(false)
    var pending by mutableStateOf<(() -> Unit)?>(null)

    fun attempt(action: () -> Unit) {
        if (dirty) pending = action else action()
    }

    fun confirm() {
        val action = pending
        pending = null
        dirty = false
        action?.invoke()
    }
}

private fun NavHostController.openItem(item: Item) = navigate(if (item.isDraft) "draft/${item.id}" else "item/${item.id}")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtonTodoApp(vm: AppViewModel, openItemId: Long?, onOpenedItem: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.HOME.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val loaded by vm.items.collectAsState()
    val items = loaded ?: emptyList()
    val settings by vm.settings.collectAsState()
    val guard = remember { LeaveGuard() }
    val context = LocalContext.current
    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    val complete = { item: Item ->
        vm.complete(item) { next -> toast(if (next != null) "Done. Next: ${dateText(next)}" else "Completed") }
        Unit
    }

    LaunchedEffect(openItemId, loaded) {
        if (openItemId != null && loaded != null) {
            items.firstOrNull { it.id == openItemId }?.let { nav.openItem(it) }
            onOpenedItem()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (tab == null) IconButton(onClick = { guard.attempt { nav.popBackStack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        val section = when (route) {
                            ITEM -> "Details"
                            DRAFT -> "Draft"
                            SEARCH -> "Search"
                            SETTINGS -> "Settings"
                            else -> tab?.label ?: ""
                        }
                        Text(section.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(headerDate(LocalDate.now()), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { guard.attempt { nav.navigate(SEARCH) { launchSingleTop = true } } }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { guard.attempt { nav.navigate(SETTINGS) { launchSingleTop = true } } }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = {
                            guard.attempt {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(items, settings, onOpen = { nav.openItem(it) }, onCheck = complete)
            }
            composable(Tab.NEW.route) {
                var formKey by remember { mutableIntStateOf(0) }
                val initial = remember(formKey) { FormState.defaults(settings) }
                ItemForm(
                    initial = initial,
                    mode = FormMode.NEW,
                    onDirtyChange = { guard.dirty = it },
                    onSave = { st ->
                        vm.save(st.toItem(Item())) { toast("Saved") }
                        guard.dirty = false
                        formKey++
                    },
                    onDraft = { st ->
                        vm.saveDraft(st.toItem(Item())) { toast("Saved as a draft") }
                        guard.dirty = false
                        formKey++
                    },
                    onDiscard = { guard.attempt { formKey++ } },
                )
            }
            composable(Tab.TODO.route) {
                TodoScreen(items, onOpen = { nav.openItem(it) }, onCheck = complete, onUncheck = { vm.reopen(it) })
            }
            composable(Tab.EVENTS.route) {
                EventsScreen(items, onOpen = { nav.openItem(it) })
            }
            composable(SEARCH) {
                SearchScreen(items, onOpen = { nav.openItem(it) })
            }
            composable(SETTINGS) {
                SettingsScreen(vm, items, settings, onOpenDraft = { nav.openItem(it) })
            }
            composable(ITEM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                val item = items.firstOrNull { it.id == id }
                if (item == null) {
                    Missing()
                } else {
                    val initial = remember(item.id, item.updatedAt) { FormState.from(item, settings) }
                    ItemForm(
                        initial = initial,
                        mode = FormMode.DETAIL,
                        completed = item.completedAt != null,
                        onDirtyChange = { guard.dirty = it },
                        onSave = { st -> vm.save(st.toItem(item)) { toast("Updated") } },
                        onDiscard = { guard.attempt { nav.popBackStack() } },
                        onComplete = {
                            guard.dirty = false
                            complete(item)
                            nav.popBackStack()
                        },
                        onReopen = { vm.reopen(item) },
                    )
                }
            }
            composable(DRAFT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                val draft = items.firstOrNull { it.id == id }
                var confirmDelete by remember { mutableStateOf(false) }
                if (draft == null) {
                    Missing()
                } else {
                    val initial = remember(draft.id) { FormState.from(draft, settings) }
                    ItemForm(
                        initial = initial,
                        mode = FormMode.DRAFT,
                        onDirtyChange = { guard.dirty = it },
                        onSave = { st ->
                            guard.dirty = false
                            vm.save(st.toItem(draft)) { toast("Saved") }
                            nav.popBackStack()
                        },
                        onDraft = { st ->
                            guard.dirty = false
                            vm.saveDraft(st.toItem(draft)) { toast("Draft updated") }
                            nav.popBackStack()
                        },
                        onDiscard = { confirmDelete = true },
                    )
                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Delete this draft?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    guard.dirty = false
                                    vm.delete(draft)
                                    nav.popBackStack()
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                        )
                    }
                }
            }
        }
        // Registered after NavHost so it takes priority over its own back handling.
        BackHandler(enabled = guard.dirty) { guard.attempt { nav.popBackStack() } }
    }

    if (guard.pending != null) {
        AlertDialog(
            onDismissRequest = { guard.pending = null },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes.") },
            confirmButton = { TextButton(onClick = { guard.confirm() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { guard.pending = null }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Missing() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("This item no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
