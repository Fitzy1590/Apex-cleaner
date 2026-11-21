package com.fitzy.apexcleaner.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitzy.apexcleaner.net.LogView
import com.fitzy.apexcleaner.net.Rule
import com.fitzy.apexcleaner.ui.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen log of every website name your apps looked up, with Allow / Block on each. */
@Composable
fun NetworkLogScreen(state: UiState, onRule: (String, Rule?) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(true) }
    val fmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    val rows = LogView.filter(state.history, query, blockedOnly, state.rules)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Network Logs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 44.dp)) { Text("Clear") }
            Button(onClick = onClose, modifier = Modifier.heightIn(min = 44.dp)) { Text("Close") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search websites") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = blockedOnly, onCheckedChange = { blockedOnly = it })
            Text("Show blocked only")
        }
        Text("Tap Allow to let a website through, or Block to stop it. Banking and push-alert sites are always protected.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item { Text(if (state.history.isEmpty()) "Nothing yet. Turn the shield on and use your phone." else "No websites match.", style = MaterialTheme.typography.bodyMedium) }
            items(rows, key = { it.domain }) { d ->
                val rule = state.rules[d.domain]
                val blocked = rule == Rule.DENY || (rule == null && d.blocked > 0 && d.allowed == 0) || (rule == null && d.blocked > 0)
                val status = when {
                    rule == Rule.ALLOW -> "Allowed by you"
                    rule == Rule.DENY -> "Blocked by you"
                    d.blocked > 0 -> "Blocked: ${LogView.category(d.domain)}"
                    else -> "Allowed"
                }
                val times = if (d.total > 1) " · ${d.total}x" else ""
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(d.domain, style = MaterialTheme.typography.bodyLarge)
                        Text("${fmt.format(Date(d.last)).lowercase()} · $status$times", style = MaterialTheme.typography.bodySmall)
                    }
                    when {
                        rule != null -> Button(onClick = { onRule(d.domain, null) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Undo") }
                        blocked -> OutlinedButton(onClick = { onRule(d.domain, Rule.ALLOW) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Allow") }
                        else -> OutlinedButton(onClick = { onRule(d.domain, Rule.DENY) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Block") }
                    }
                }
            }
        }
    }
}
