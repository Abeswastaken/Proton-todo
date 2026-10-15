package com.abeswastaken.protontodo.ack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abeswastaken.protontodo.app
import com.abeswastaken.protontodo.data.Item
import com.abeswastaken.protontodo.settings.Challenge
import com.abeswastaken.protontodo.ui.theme.ProtonTodoTheme
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A randomized check, so the final reminder can't be cleared on autopilot. */
data class ChallengeTask(val prompt: String, val answer: String, val numeric: Boolean) {
    fun accepts(input: String) = input.trim().equals(answer, ignoreCase = true)

    companion object {
        private val phrases = listOf(
            "I got it", "I understand", "On it now", "Not forgetting this",
            "I see this one", "Handled today", "Noted and on it",
        )

        fun create(type: Challenge, random: Random = Random.Default): ChallengeTask {
            val pick = if (type == Challenge.MIXED) (if (random.nextBoolean()) Challenge.PHRASE else Challenge.MATH) else type
            return if (pick == Challenge.MATH) {
                val a = random.nextInt(12, 60)
                val b = random.nextInt(7, 40)
                ChallengeTask("What is $a + $b?", (a + b).toString(), numeric = true)
            } else {
                val p = phrases[random.nextInt(phrases.size)]
                ChallengeTask("Type \"$p\"", p, numeric = false)
            }
        }
    }
}

class AckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val settings = app.settings
        val challenge = ChallengeTask.create(settings.current.challenge)
        setContent {
            val s by settings.flow.collectAsState()
            var item by remember { mutableStateOf<Item?>(null) }
            val scope = rememberCoroutineScope()
            androidx.compose.runtime.LaunchedEffect(id) { item = app.repo.get(id) }
            ProtonTodoTheme(s.theme) {
                AckScreen(
                    title = item?.title ?: "Final reminder",
                    challenge = challenge,
                    onPassed = {
                        scope.launch {
                            app.repo.acknowledge(id)
                            finish()
                        }
                    },
                    onLater = { finish() },
                )
            }
        }
    }

    companion object { const val EXTRA_ID = "itemId" }
}

@Composable
private fun AckScreen(title: String, challenge: ChallengeTask, onPassed: () -> Unit, onLater: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FINAL REMINDER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("To clear this reminder, ${challenge.prompt.replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; wrong = false },
            singleLine = true,
            isError = wrong,
            supportingText = { if (wrong) Text("Not quite. Try again.") },
            keyboardOptions = KeyboardOptions(keyboardType = if (challenge.numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { if (challenge.accepts(input)) onPassed() else wrong = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Acknowledge")
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text("Not now (the reminder stays)")
        }
    }
}
