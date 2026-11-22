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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    onShield: (Boolean) -> Unit,
    onUpdateLists: () -> Unit,
    onRule: (String, com.fitzy.apexcleaner.net.Rule?) -> Unit,
    onClearHistory: () -> Unit,
    onNightClean: (Boolean) -> Unit,
    onEncryptedDns: (Boolean) -> Unit,
    onHeat: (Float) -> Unit,
    onCheckUpdate: () -> Unit,
    onLimiter: (Boolean) -> Unit,
) {
    ApexTheme {
        var showLogs by rememberSaveable { mutableStateOf(false) }
        if (showLogs) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showLogs = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) { NetworkLogScreen(state, onRule, onClearHistory) { showLogs = false } }
            }
        }
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
        var tab by rememberSaveable { mutableStateOf(0) }
        Scaffold(bottomBar = {
            // No animation on purpose: tabs switch instantly.
            NavigationBar {
                listOf("Home", "Shield", "Storage", "Apps", "Settings").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Spacer(Modifier.size(1.dp)) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Apex Cleaner", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
                state.update?.let { u ->
                    item {
                        val appCtx = androidx.compose.ui.platform.LocalContext.current
                        val working = state.updateStatus?.let { it.startsWith("Downloading") || it.startsWith("Opening") } == true
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.updateStatus ?: "A new version of Apex Cleaner is ready (${u.tag}).", Modifier.weight(1f))
                                Button(onClick = { com.fitzy.apexcleaner.core.UpdateInstaller.start(appCtx, u) }, enabled = !working, modifier = Modifier.heightIn(min = 44.dp)) { Text("Update") }
                            }
                        }
                    }
                }
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
                if (tab == 0) {
                state.health?.let { h ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Gauge("Storage free", h.storageFreePct)
                            Gauge("Memory free", h.ramFreePct)
                            Gauge("Battery", if (state.charge.pluggedIn) state.charge.level else h.batteryPct)
                        }
                    }
                    item {
                        Text(
                            "${formatBytes(h.storageFreeBytes)} free of ${formatBytes(h.storageTotalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                }
                if (tab == 1) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Ad & tracker shield", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (state.shieldOn) "On: ${state.blockedCount} ads and trackers blocked. Banking, alarms and push alerts are never touched (${state.bankingDomains} banking sites pass straight through)."
                                    else "Off. Blocks ad, tracker and smart-TV spying lookups on this device, using a private on-device filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (state.bankingAppsFound > 0) Text("${state.bankingAppsFound} banking or payment apps found here skip the shield completely, so their security checks stay valid.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = state.shieldOn, onCheckedChange = onShield)
                        }
                        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                            OutlinedButton(onClick = onUpdateLists, enabled = !state.busy, modifier = Modifier.heightIn(min = 44.dp)) { Text("Update ad lists") }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Network Logs", style = MaterialTheme.typography.titleMedium)
                            Text(if (state.history.isEmpty()) "Websites your apps look up will appear here so you can allow or block them." else "${state.history.size} websites seen. Open the log to allow or block any of them.", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { showLogs = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Open Network Logs") }
                        }
                    }
                }
                }
                if (tab == 0) {
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
                }
                if (tab == 2) {
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
                if (state.breakdown.isNotEmpty()) {
                    item { Text("Biggest cache users", style = MaterialTheme.typography.titleMedium) }
                    items(state.breakdown, key = { it.pkg }) { a ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(a.label, style = MaterialTheme.typography.titleSmall); Text(formatBytes(a.total))
                                }
                                a.bytesByKind.entries.sortedByDescending { it.value }.forEach { (k, b) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(k.label, style = MaterialTheme.typography.bodySmall); Text(formatBytes(b), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
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
                }
                if (tab == 3) {
                item { Text("Protected apps (never interrupted)", style = MaterialTheme.typography.titleMedium) }
                items(state.apps, key = { it.pkg }) { AppRow(it, onToggleApp) }
                }
                if (tab == 4) {
                    item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
                    item { SettingRow("Night clean at 3 AM", "Cleans while charging, over 80% battery, with the screen off for 30 minutes.", state.nightClean, onNightClean) }
                    item { SettingRow("Streaming priority", "While a video, music or game app is on screen, other apps share a small slice of the connection so the stream stays smooth. Streaming apps, banking and your protected apps are never slowed. Needs Usage Access and the shield switched on. Apps outside the limiter skip the ad shield.", state.limiter, onLimiter) }
                    item { SettingRow("Encrypted DNS", "The shield looks up websites over a private connection. Turn off if a network blocks it.", state.encryptedDns, onEncryptedDns) }
                    item {
                        val c = androidx.compose.ui.platform.LocalContext.current
                        var nx by androidx.compose.runtime.remember { mutableStateOf(com.fitzy.apexcleaner.core.AppSettings.nxdomainReply(c)) }
                        SettingRow("Collapse blocked ads", "Blocked ad names are answered \"this site does not exist\", so most apps drop the ad slot instead of showing an empty frame. Turn off if an app misbehaves.", nx) { nx = it; com.fitzy.apexcleaner.core.AppSettings.setNxdomainReply(c, it) }
                    }
                    item {
                        val c = androidx.compose.ui.platform.LocalContext.current
                        var hide by androidx.compose.runtime.remember { mutableStateOf(com.fitzy.apexcleaner.core.AppSettings.hideAdBoxes(c)) }
                        SettingRow("Hide leftover ad boxes", "Covers empty boxes that apps label as ads with a plain panel. Needs the Apex Ad Box Hider switched on in Android's Accessibility settings. It never reads or stores what is on screen, and skips banking and protected apps. The space stays, only the box is hidden.", hide) {
                            hide = it; com.fitzy.apexcleaner.core.AppSettings.setHideAdBoxes(c, it)
                            if (it && !com.fitzy.apexcleaner.net.AdBoxHider.isEnabled(c)) runCatching {
                                c.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                    item {
                        val c = androidx.compose.ui.platform.LocalContext.current
                        var hideN by androidx.compose.runtime.remember { mutableStateOf(com.fitzy.apexcleaner.core.AppSettings.hideAdNotices(c)) }
                        SettingRow("Hide \"ads aren't displayed\" notices", "In web browsers, taps the close button on pop-ups that ask you to turn off your ad blocker (like the one on XDA). If a pop-up has no close button it is covered with a black panel. Uses the same Accessibility switch as above. It only checks for those few warning phrases, never stores or sends anything, and skips banking and protected apps.", hideN) {
                            hideN = it; com.fitzy.apexcleaner.core.AppSettings.setHideAdNotices(c, it)
                            if (it && !com.fitzy.apexcleaner.net.AdBoxHider.isEnabled(c)) runCatching {
                                c.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Heat warning at ${"%.0f".format(state.hotC)}°C", style = MaterialTheme.typography.titleMedium)
                                Text("You get an alert while charging when the battery gets this hot.", style = MaterialTheme.typography.bodySmall)
                                androidx.compose.material3.Slider(value = state.hotC, onValueChange = onHeat, valueRange = 38f..46f, steps = 7)
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Version ${state.version}", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onCheckUpdate, enabled = !state.checking, modifier = Modifier.heightIn(min = 44.dp)) { Text(if (state.checking) "Checking…" else "Check for updates") }
                                    OutlinedButton(onClick = onUpdateLists, enabled = !state.busy, modifier = Modifier.heightIn(min = 44.dp)) { Text("Update ad lists") }
                                }
                                OutlinedButton(onClick = onClearHistory, modifier = Modifier.heightIn(min = 44.dp)) { Text("Clear network log") }
                            }
                        }
                    }
                }
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
            if (app.automatic) Text(if (app.banking) "Banking app: skips the shield" else "Automatically protected", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun SettingRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = onChange)
        }
    }
}
