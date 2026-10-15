package com.abeswastaken.protontodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.abeswastaken.protontodo.MainActivity
import com.abeswastaken.protontodo.R
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.ui.Lists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home screen widget: open to-dos (Proton Calendar covers events). Tap a circle to complete one. */
class ProtonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.app.repo
        val first = repo.all()
        provideContent {
            // Live while the widget session is open, so changes made in the app show up straight away.
            val items by repo.items.collectAsState(initial = first)
            GlanceTheme { Content(items) }
        }
    }

    @Composable
    private fun Content(items: List<Item>) {
        val today = LocalDate.now()
        val tasks = Lists.openTasks(items)
            .sortedWith(compareByDescending<Item> { Lists.isOverdue(it, today) }.thenByDescending { it.important }.thenBy { it.createdAt })
            .take(12)
        val primary = GlanceTheme.colors.onSurface
        val muted = GlanceTheme.colors.onSurfaceVariant
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "To-Do (${tasks.size})",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            LazyColumn {
                if (tasks.isEmpty()) {
                    item { Text("All caught up.", style = TextStyle(color = muted, fontSize = 13.sp)) }
                }
                items(tasks.size) { i ->
                    val t = tasks[i]
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "◯",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp),
                            modifier = GlanceModifier.clickable(actionRunCallback<CompleteAction>(actionParametersOf(ItemKey to t.id))),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            mark(t) + t.title,
                            maxLines = 1,
                            style = TextStyle(color = if (Lists.isOverdue(t, today)) GlanceTheme.colors.error else primary, fontSize = 14.sp),
                            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                        )
                    }
                }
            }
        }
    }

    private fun mark(item: Item) = if (item.important) "! " else ""

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws every widget whenever the items change while the app is running. */
        fun keepUpdated(context: Context) {
            val app = context.applicationContext
            scope.launch {
                app.app.repo.items.distinctUntilChanged().collect { runCatching { ProtonWidget().updateAll(app) } }
            }
        }
    }
}

val ItemKey = ActionParameters.Key<Long>("itemId")

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemKey] ?: return
        val repo = context.app.repo
        repo.get(id)?.let { repo.complete(it) }
        ProtonWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ProtonWidget().updateAll(context)
    }
}

class ProtonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtonWidget()
}
