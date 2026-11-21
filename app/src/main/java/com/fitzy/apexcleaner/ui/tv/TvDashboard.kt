@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.fitzy.apexcleaner.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.fitzy.apexcleaner.ui.focusFeedback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.fitzy.apexcleaner.core.HelperState
import com.fitzy.apexcleaner.core.NeededPermission
import com.fitzy.apexcleaner.ui.mobile.helperButton
import com.fitzy.apexcleaner.ui.mobile.helperExplain
import com.fitzy.apexcleaner.core.formatBytes
import com.fitzy.apexcleaner.ui.UiState
import com.fitzy.apexcleaner.ui.theme.TvViolet
import com.fitzy.apexcleaner.ui.theme.TvObsidian

/** Every focusable thing grows at least 5% AND gets a 3dp accent border (spec: >=1.03x or >=2dp). */
private val FocusBorder = Border(BorderStroke(3.dp, TvViolet), shape = RoundedCornerShape(12.dp))
private const val FocusScale = 1.05f

@Composable
fun TvDashboard(
    state: UiState,
    onClean: () -> Unit,
    onBoost: () -> Unit,
    onCool: () -> Unit,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit,
    onGrant: (NeededPermission) -> Unit,
    onHelper: () -> Unit,
    onShield: (Boolean) -> Unit,
    onRule: (String, com.fitzy.apexcleaner.net.Rule?) -> Unit,
    onNightClean: (Boolean) -> Unit,
    onEncryptedDns: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onLimiter: (Boolean) -> Unit,
    onClearLog: () -> Unit = {},
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = TvViolet, background = TvObsidian, surface = TvObsidian)) {
        Surface(Modifier.fillMaxSize()) {
            val firstFocus = remember { FocusRequester() }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var blockedOnly by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Apex Cleaner", style = MaterialTheme.typography.displaySmall)
                // Tabs switch instantly: plain conditional content, no animation (0 ms).
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                    listOf("Home", "Shield", "Storage", "Logs", "Settings").forEachIndexed { i, name ->
                        TvButton(if (i == tab) "● $name" else name, { tab = i })
                    }
                }
            LazyColumn(
                Modifier.fillMaxSize().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (tab == 0) {
                    items(state.missing) { p ->
                        Card(onClick = { onGrant(p) }, modifier = Modifier.fillMaxWidth().focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                            Column(Modifier.padding(20.dp)) {
                                Text("Allow ${p.title} (press to set up)", style = MaterialTheme.typography.titleMedium)
                                Text(p.why, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (state.helper != HelperState.Ready) {
                        item {
                            Card(onClick = onHelper, modifier = Modifier.fillMaxWidth().focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                                Column(Modifier.padding(20.dp)) {
                                    Text("Deeper clean: ${helperButton(state.helper)} (press)", style = MaterialTheme.typography.titleMedium)
                                    Text(helperExplain(state.helper), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    state.update?.let { u ->
                        item {
                            val appCtx = androidx.compose.ui.platform.LocalContext.current
                            Card(onClick = { com.fitzy.apexcleaner.core.UpdateInstaller.start(appCtx, u) }, modifier = Modifier.fillMaxWidth().focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                                Text(state.updateStatus ?: "New version ready (${u.tag}): press to install", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    state.message?.let { m ->
                        item {
                            Card(onClick = onDismiss, modifier = Modifier.fillMaxWidth().focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                                Text(m, Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    state.health?.let { h ->
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.focusRestorer()) {
                                item { StatCard("Storage free", "${h.storageFreePct}%", formatBytes(h.storageFreeBytes), onClick = onClean) }
                                item { StatCard("Memory free", "${h.ramFreePct}%", if (h.lowRam) "Low" else "Healthy", onClick = onBoost) }
                                item { StatCard("Battery", "${h.batteryPct}%", "${"%.0f".format(h.batteryTempC)}°C", onClick = onCool) }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton("Safe Clean${" (${formatBytes((state.scan?.totalBytes ?: 0L) + state.otherCacheBytes)})"}", onClean, Modifier.focusRequester(firstFocus))
                            TvButton("1-Tap Boost", onBoost)
                            TvButton("Cool Down", onCool)
                        }
                    }
                }
                if (tab == 1) {
                    item {
                        Card(onClick = { onShield(!state.shieldOn) }, modifier = Modifier.fillMaxWidth().focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                            Column(Modifier.padding(20.dp)) {
                                Text("Ad & tracker shield: ${if (state.shieldOn) "ON" else "OFF"} (press to switch)", style = MaterialTheme.typography.titleMedium)
                                Text(if (state.shieldOn) "${state.blockedCount} ads, trackers and TV spying lookups blocked. Banking and push alerts are never touched.${if (state.recentBlocked.isNotEmpty()) " Latest: " + state.recentBlocked.take(3).joinToString(", ") else ""}" else "Blocks ad, tracker and smart-TV spying lookups on this device.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (state.history.isNotEmpty()) {
                        item { Text("Lookup history (press to switch: normal, always allow, always block)", style = MaterialTheme.typography.titleLarge) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                                items(state.history.take(12), key = { it.domain }) { d ->
                                    val rule = state.rules[d.domain]
                                    Card(
                                        onClick = {
                                            onRule(d.domain, when (rule) { null -> com.fitzy.apexcleaner.net.Rule.ALLOW; com.fitzy.apexcleaner.net.Rule.ALLOW -> com.fitzy.apexcleaner.net.Rule.DENY; com.fitzy.apexcleaner.net.Rule.DENY -> null })
                                        },
                                        modifier = Modifier.width(280.dp).focusFeedback(),
                                        scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder),
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(d.domain, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                            Text(when (rule) { com.fitzy.apexcleaner.net.Rule.ALLOW -> "Always allowed"; com.fitzy.apexcleaner.net.Rule.DENY -> "Always blocked"; null -> if (d.blocked > 0) "Blocked ${d.blocked}x" else "Allowed ${d.allowed}x" }, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (tab == 2) {
                    if (state.breakdown.isNotEmpty()) {
                        item { Text("Biggest cache users", style = MaterialTheme.typography.titleLarge) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                                items(state.breakdown, key = { it.pkg }) { a ->
                                    Card(onClick = {}, modifier = Modifier.width(260.dp).focusFeedback(), scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder)) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(a.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                            Text(formatBytes(a.total), style = MaterialTheme.typography.headlineSmall)
                                            a.bytesByKind.entries.sortedByDescending { it.value }.take(3).forEach { (k, b) ->
                                                Text("${k.label}: ${formatBytes(b)}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.breakdown.isEmpty()) {
                        item { Text("Nothing to show yet. Turn on the deeper clean helper on Home to see which apps use the most cache.", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                if (tab == 3) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                            item { StatCard("Show", if (blockedOnly) "Blocked only" else "Everything", "Press to switch", onClick = { blockedOnly = !blockedOnly }) }
                            item { StatCard("Clear log", "${state.history.size}", "Names remembered. Press to clear", onClick = onClearLog) }
                        }
                    }
                    item { Text("Press a name: normal, always allow, always block. Banking and push alerts stay protected.", style = MaterialTheme.typography.bodyLarge) }
                    val rows = com.fitzy.apexcleaner.net.LogView.filter(state.history, "", blockedOnly, state.rules).take(40)
                    if (rows.isEmpty()) item { Text(if (state.history.isEmpty()) "Nothing yet. Turn the shield on and use the TV." else "No blocked names yet.", style = MaterialTheme.typography.bodyLarge) }
                    items(rows, key = { it.domain }) { d ->
                        val rule = state.rules[d.domain]
                        Card(
                            onClick = { onRule(d.domain, when (rule) { null -> com.fitzy.apexcleaner.net.Rule.ALLOW; com.fitzy.apexcleaner.net.Rule.ALLOW -> com.fitzy.apexcleaner.net.Rule.DENY; com.fitzy.apexcleaner.net.Rule.DENY -> null }) },
                            modifier = Modifier.fillMaxWidth().focusFeedback(),
                            scale = CardDefaults.scale(focusedScale = FocusScale), border = CardDefaults.border(focusedBorder = FocusBorder),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(d.domain, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(when (rule) { com.fitzy.apexcleaner.net.Rule.ALLOW -> "Always allowed by you"; com.fitzy.apexcleaner.net.Rule.DENY -> "Always blocked by you"; null -> if (d.blocked > 0) "Blocked (${com.fitzy.apexcleaner.net.LogView.category(d.domain)}) ${d.blocked}x" else "Allowed ${d.allowed}x" }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (tab == 4) {
                    item { Text("Settings (press to change)", style = MaterialTheme.typography.titleLarge) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                            item { StatCard("Night clean 3 AM", if (state.nightClean) "ON" else "OFF", "Charging, idle only", onClick = { onNightClean(!state.nightClean) }) }
                            item { StatCard("Streaming priority", if (state.limiter) (if (state.limiting) "ACTIVE" else "ON") else "OFF", "Other apps slow down while you stream", onClick = { onLimiter(!state.limiter) }) }
                        item { StatCard("Encrypted DNS", if (state.encryptedDns) "ON" else "OFF", "Private lookups", onClick = { onEncryptedDns(!state.encryptedDns) }) }
                            item { StatCard("Version", state.version, "Press to check for updates", onClick = onCheckUpdate) }
                        }
                    }
                    item { Text("Protected apps (press to toggle)", style = MaterialTheme.typography.titleLarge) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                            items(state.apps, key = { it.pkg }) { app ->
                                Card(
                                    onClick = { if (!app.automatic) onToggleApp(app.pkg) },
                                    modifier = Modifier.width(220.dp).focusFeedback(),
                                    scale = CardDefaults.scale(focusedScale = FocusScale),
                                    border = CardDefaults.border(focusedBorder = FocusBorder),
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text(
                                            when {
                                                app.automatic -> "Auto-protected"
                                                app.protectedByUser -> "Protected"
                                                else -> "Not protected"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(240.dp).focusFeedback(),
        scale = CardDefaults.scale(focusedScale = FocusScale),
        border = CardDefaults.border(focusedBorder = FocusBorder),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.displayMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TvButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.focusFeedback(),
        scale = ButtonDefaults.scale(focusedScale = FocusScale),
        border = ButtonDefaults.border(focusedBorder = FocusBorder),
    ) { Text(label) }
}
