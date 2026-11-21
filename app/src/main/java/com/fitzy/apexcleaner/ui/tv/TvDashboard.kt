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
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = TvViolet)) {
        Surface(Modifier.fillMaxSize()) {
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            LazyColumn(
                Modifier.fillMaxSize().padding(48.dp).focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { Text("Apex Cleaner", style = MaterialTheme.typography.displaySmall) }
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
