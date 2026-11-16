package com.fitzy.apexcleaner.ui.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fitzy.apexcleaner.core.HelperState
import com.fitzy.apexcleaner.core.NeededPermission
import com.fitzy.apexcleaner.core.formatBytes
import com.fitzy.apexcleaner.ui.AppEntry
import com.fitzy.apexcleaner.ui.UiState
import com.fitzy.apexcleaner.ui.theme.Accent
import com.fitzy.apexcleaner.ui.theme.ApexTheme
import com.fitzy.apexcleaner.ui.theme.Bad
import com.fitzy.apexcleaner.ui.theme.Warn

@Composable
fun MobileDashboard(
    state: UiState,
    onClean: () -> Unit,
    onBoost: () -> Unit,
    onCool: () -> Unit,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit,
    onGrantFiles: () -> Unit,
    onStorageSettings: () -> Unit,
    onGrant: (NeededPermission) -> Unit,
    onHelper: () -> Unit,
    onChargeLimit: (Int) -> Unit,
    onFastCharge: (Boolean) -> Unit,
) {
    ApexTheme {
        var dismissed by rememberSaveable { mutableStateOf(false) }
        val first = state.missing.firstOrNull()
        if (first != null && !dismissed) {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("Allow ${first.title}?") },
                text = { Text(first.why + "\n\n" + state.missing.size + " permission(s) still to set up.") },
                confirmButton = { Button(onClick = { onGrant(first) }) { Text("Allow") } },
                dismissButton = { OutlinedButton(onClick = { dismissed = true }) { Text("Later") } },
            )
        }
        Scaffold { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Apex Cleaner", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
                state.message?.let { m ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m, Modifier.weight(1f))
                                OutlinedButton(onClick = onDismiss) { Text("OK") }
                            }
                        }
                    }
                }
                state.health?.let { h ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Gauge("Storage free", h.storageFreePct)
                            Gauge("Memory free", h.ramFreePct)
                            Gauge("Battery", h.batteryPct)
                        }
                    }
                    item {
                        Text(
                            "${formatBytes(h.storageFreeBytes)} free of ${formatBytes(h.storageTotalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item { ChargingCard(state.charge, onChargeLimit, onFastCharge) }
                item {
                    Button(onClick = onClean, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        val size = " (${formatBytes((state.scan?.totalBytes ?: 0L) + state.otherCacheBytes)})"
                        Text(if (state.busy) "Working…" else "Safe Clean$size")
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBoost, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("1-Tap Boost") }
                        OutlinedButton(onClick = onCool, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Cool Down") }
                    }
                }
                if (!state.filesAccess) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Why does it say 0 B?", style = MaterialTheme.typography.titleMedium)
                                Text("Without file access, Apex Cleaner can only check its own tiny cache. Turn on deep clean to find leftover installers, thumbnails, logs and empty folders. Your photos, documents, logins and settings are never touched.")
                                Button(onClick = onGrantFiles, modifier = Modifier.fillMaxWidth()) { Text("Turn on deep clean") }
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Deeper clean (optional)", style = MaterialTheme.typography.titleMedium)
                            Text(helperExplain(state.helper))
                            helperButton(state.helper)?.let { Button(onClick = onHelper, modifier = Modifier.fillMaxWidth()) { Text(it) } }
                        }
                    }
                }
                item { OutlinedButton(onClick = onStorageSettings, modifier = Modifier.fillMaxWidth()) { Text("Free more space: see what uses storage") } }
                state.scan?.let { s ->
                    item { Text("What can be cleaned", style = MaterialTheme.typography.titleMedium) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Other apps' caches"); Text("~" + formatBytes(state.otherCacheBytes))
                        }
                    }
                    items(s.bytesByCategory.entries.toList()) { (cat, b) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat.label); Text(formatBytes(b))
                        }
                    }
                }
                item { Text("Protected apps (never interrupted)", style = MaterialTheme.typography.titleMedium) }
                items(state.apps, key = { it.pkg }) { AppRow(it, onToggleApp) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onToggle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(app.label)
            if (app.automatic) Text("Automatically protected", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = app.protectedByUser || app.automatic, onCheckedChange = { onToggle(app.pkg) }, enabled = !app.automatic)
    }
}

@Composable
fun Gauge(label: String, percent: Int) {
    val color = when { percent < 15 -> Bad; percent < 30 -> Warn; else -> Accent }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                val inset = 5.dp.toPx()
                val sz = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 135f, 270f, false, Offset(inset, inset), sz, style = stroke)
                drawArc(color, 135f, 270f * percent.coerceIn(0, 100) / 100f, false, Offset(inset, inset), sz, style = stroke)
            }
            Text("$percent%", style = MaterialTheme.typography.titleMedium, color = Color.Unspecified)
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun helperExplain(h: HelperState) = when (h) {
    HelperState.NotInstalled -> "Android 13 hides other apps' cache folders from normal apps. The free Shizuku helper app lets Apex Cleaner clear them safely, like SD Maid does on a rooted phone. It only ever removes cache files, never logins, settings or your photos."
    HelperState.NotRunning -> "Shizuku is installed but not started. Open it and tap Start, then come back here."
    HelperState.NeedsPermission -> "Shizuku is running. Tap below to let Apex Cleaner use it for deeper cache cleaning."
    HelperState.Ready -> "Deeper clean is on. Safe Clean now also clears other apps' cache folders."
}

internal fun helperButton(h: HelperState) = when (h) {
    HelperState.NotInstalled -> "Get the helper app"
    HelperState.NotRunning -> "Open the helper app"
    HelperState.NeedsPermission -> "Allow deeper clean"
    HelperState.Ready -> null
}
