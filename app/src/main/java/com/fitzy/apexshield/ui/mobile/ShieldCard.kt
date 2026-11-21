package com.fitzy.apexshield.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitzy.apexshield.vpn.DohProvider
import com.fitzy.apexshield.vpn.QueryLog
import com.fitzy.apexshield.vpn.ShieldUi
import java.text.DateFormat
import java.util.Date

@Composable
fun ShieldCard(
    s: ShieldUi,
    onShield: (Boolean) -> Unit,
    onProvider: (DohProvider) -> Unit,
    onToggleDomain: (String) -> Unit,
    onClearLog: () -> Unit,
) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DNS Firewall", style = MaterialTheme.typography.titleMedium)
                    Text(if (s.running) "Active: ${s.blockedTotal} ads & trackers blocked" else "Off")
                }
                Switch(checked = s.running, onCheckedChange = onShield)
            }
            Text("${s.queriesTotal} lookups checked · ${s.rules} blocking rules · ${s.bypassApps} protected apps bypass the shield")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onProvider(DohProvider.values()[(s.provider.ordinal + 1) % DohProvider.values().size]) },
                    modifier = Modifier.weight(1f),
                ) { Text("Secure DNS: ${s.provider.label}") }
                OutlinedButton(onClick = { showLog = true }, modifier = Modifier.weight(1f)) { Text("Network Logs") }
            }
            Text(
                "Only website lookups pass through the shield; it never sees what you read or type. Banking and streaming sites are never blocked.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (showLog) {
        Dialog(onDismissRequest = { showLog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { LogScreen(s.log, onToggleDomain, onClearLog) { showLog = false } }
        }
    }
}

@Composable
private fun LogScreen(log: List<QueryLog>, onToggle: (String) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var onlyBlocked by rememberSaveable { mutableStateOf(false) }
    val shown = log.filter { (!onlyBlocked || it.blocked) && it.domain.contains(q.trim(), ignoreCase = true) }
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Network Logs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
            Button(onClick = onClose) { Text("Close") }
        }
        OutlinedTextField(q, { q = it }, label = { Text("Search websites") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(onlyBlocked, { onlyBlocked = it })
            Text("  Show blocked only")
        }
        Text("Tap a website to block it, or allow it again.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.time.toString() + it.domain }) { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.domain)
                        Text(
                            fmt.format(Date(e.time)) + " · " + (e.category?.let { "Blocked: ${it.label}" } ?: "Allowed"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = { onToggle(e.domain) }) { Text(if (e.blocked) "Allow" else "Block") }
                }
            }
        }
    }
}
