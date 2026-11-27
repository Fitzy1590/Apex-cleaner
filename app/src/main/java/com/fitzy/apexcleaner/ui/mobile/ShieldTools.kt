package com.fitzy.apexcleaner.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitzy.apexcleaner.net.PinLock
import com.fitzy.apexcleaner.net.QuietHours
import com.fitzy.apexcleaner.net.QuietRule
import com.fitzy.apexcleaner.net.TrafficLog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import com.fitzy.apexcleaner.net.RouterScripts

/** Settings rows for the shield tools: Quiet hours (PIN-locked) and the router script maker. */
@Composable
fun ShieldToolsRows() {
    HttpsCleanerCard()
    LiveTrafficCard()
    QuietHoursCard()
    RouterScriptCard()
}

@Composable
private fun QuietHoursCard() {
    val ctx = LocalContext.current
    var rules by remember { mutableStateOf(QuietHours.rules(ctx)) }
    var unlockedUntil by remember { mutableStateOf(0L) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }   // action waiting for the PIN
    var editing by remember { mutableStateOf<Pair<Int, QuietRule>?>(null) }   // index -1 = new
    fun commit(r: List<QuietRule>) { rules = r; QuietHours.save(ctx, r) }
    fun gated(action: () -> Unit) { if (System.currentTimeMillis() < unlockedUntil) action() else pending = action }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
            Text("Blocks chosen websites between set times, for example social media from 10 PM to 6 AM. Changes need your 4-digit PIN. Banking, clock, alarm and push names can never be blocked. Apps already connected may keep working until they reconnect.", style = MaterialTheme.typography.bodySmall)
            rules.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${QuietRule.fmt(r.startMin)} to ${QuietRule.fmt(r.endMin)}" + if (r.social) ", social media" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { gated { editing = i to r } }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Edit") }
                    Switch(checked = r.enabled, onCheckedChange = { on -> gated { commit(rules.toMutableList().also { it[i] = r.copy(enabled = on) }) } })
                }
            }
            OutlinedButton(onClick = { gated { editing = -1 to QuietRule("Night", true, emptyList(), 22 * 60, 6 * 60, true) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Add a quiet-hours rule") }
        }
    }

    pending?.let { act ->
        PinDialog(onDone = { ok -> pending = null; if (ok) { unlockedUntil = System.currentTimeMillis() + 120_000; act() } })
    }
    editing?.let { (idx, r) ->
        RuleDialog(r, canDelete = idx >= 0,
            onSave = { n -> commit(rules.toMutableList().also { if (idx < 0) it.add(n) else it[idx] = n }); editing = null },
            onDelete = { commit(rules.toMutableList().also { it.removeAt(idx) }); editing = null },
            onCancel = { editing = null })
    }
}

/** Asks for the PIN (or creates one the first time). Calls [onDone] with true when it is right. */
@Composable
private fun PinDialog(onDone: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var lock by remember { mutableStateOf(PinLock.load(ctx)) }
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val creating = !lock.isSet
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(if (creating) "Choose a 4-digit PIN" else "Enter your PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
                if (creating) Text("There is no way to recover a lost PIN. Clearing the app's data resets it.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val now = System.currentTimeMillis()
                if (creating) { if (lock.set(pin)) { PinLock.store(ctx, lock); onDone(true) } else msg = "Use exactly 4 digits." }
                else {
                    val ok = lock.check(pin, now); PinLock.store(ctx, lock)
                    if (ok) onDone(true) else { pin = ""; val w = lock.lockedFor(now); msg = if (w > 0) "Too many tries. Locked for ${w / 60_000 + 1} min." else "Wrong PIN." }
                }
            }, modifier = Modifier.heightIn(min = 44.dp)) { Text(if (creating) "Save PIN" else "Unlock") }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Cancel") } })
}

@Composable
private fun RuleDialog(start: QuietRule, canDelete: Boolean, onSave: (QuietRule) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var r by remember { mutableStateOf(start) }
    var custom by remember { mutableStateOf(start.custom.joinToString(", ")) }
    fun step(v: Int, d: Int) = (v + d + 1440) % 1440
    @Composable fun Stepper(label: String, v: Int, set: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${QuietRule.fmt(v)}", Modifier.weight(1f))
        OutlinedButton(onClick = { set(step(v, -30)) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("-30 min") }
        OutlinedButton(onClick = { set(step(v, 30)) }, modifier = Modifier.heightIn(min = 44.dp).padding(start = 8.dp)) { Text("+30 min") }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Quiet-hours rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = r.name, onValueChange = { r = r.copy(name = it.take(30)) }, label = { Text("Name") }, singleLine = true)
                Stepper("From", r.startMin) { r = r.copy(startMin = it) }
                Stepper("Until", r.endMin) { r = r.copy(endMin = it) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Block social media", Modifier.weight(1f)); Switch(checked = r.social, onCheckedChange = { r = r.copy(social = it) }) }
                OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Other websites, separated by commas") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(r.copy(name = r.name.ifBlank { "Rule" }, custom = custom.split(",").map { it.trim().lowercase() }.filter { it.contains('.') })) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Save") }
        },
        dismissButton = {
            Row { if (canDelete) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 44.dp)) { Text("Delete") }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 44.dp)) { Text("Cancel") } }
        })
}

@Composable
private fun RouterScriptCard() {
    val clip = LocalClipboardManager.current
    var kind by remember { mutableStateOf(RouterScripts.Kind.OPENWRT) }
    var ip by remember { mutableStateOf("192.168.1.1") }
    var copied by remember { mutableStateOf(false) }
    val script = RouterScripts.generate(kind, ip)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Router script maker", style = MaterialTheme.typography.titleMedium)
            Text("Makes TVs and Chromecasts that use their own DNS (like 8.8.8.8) go through your ad-blocking DNS, and refuses private DNS (port 853). Pick your router, type the address of the DNS server (usually the router), copy the text and paste it into the router. Not tested on real routers yet.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouterScripts.Kind.values().forEach { k ->
                    if (k == kind) Button(onClick = { kind = k; copied = false }, modifier = Modifier.heightIn(min = 44.dp)) { Text(k.label, maxLines = 1) }
                    else OutlinedButton(onClick = { kind = k; copied = false }, modifier = Modifier.heightIn(min = 44.dp)) { Text(k.label, maxLines = 1) }
                }
            }
            OutlinedTextField(value = ip, onValueChange = { ip = it; copied = false }, label = { Text("DNS server address") }, singleLine = true,
                isError = script == null, supportingText = { if (script == null) Text("Type an IPv4 address like 192.168.1.1") })
            Button(onClick = { script?.let { clip.setText(AnnotatedString(it)); copied = true } }, enabled = script != null, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(if (copied) "Copied" else "Copy script") }
        }
    }
}

/** Writes [bytes] to the app's private export folder and offers it to Android's share sheet. */
internal fun shareExport(ctx: android.content.Context, name: String, mime: String, bytes: ByteArray) {
    runCatching {
        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = java.io.File(dir, name).apply { writeBytes(bytes) }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun size(b: Long) = when { b >= 1_000_000 -> "%.1f MB".format(b / 1e6); b >= 1_000 -> "%.1f KB".format(b / 1e3); else -> "$b B" }

@Composable
private fun LiveTrafficCard() {
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf(TrafficLog.recording) }
    var open by remember { mutableStateOf(false) }
    val flows by TrafficLog.flows.collectAsState()
    val stamp = { java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live Traffic", style = MaterialTheme.typography.titleMedium)
                    Text("Records which apps connect to which names and how much they send, only while this is on. Needs the shield on. Connection sizes show for apps inside the speed limiter; other apps show their lookups only. Keeps the last 500 entries on this phone and sends nothing anywhere.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = rec, onCheckedChange = { rec = it; TrafficLog.recording = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("Open list (${flows.size})") }
                OutlinedButton(onClick = { TrafficLog.clear() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { shareExport(ctx, "apex-traffic-${stamp()}.pcapng", "application/octet-stream", TrafficLog.snapshotPcapng()) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("Export .pcapng") }
                Button(onClick = { shareExport(ctx, "apex-traffic-${stamp()}.har", "application/json", TrafficLog.snapshotHar().toByteArray()) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("Export .har") }
            }
            Text(".pcapng holds the first 128 bytes of each packet (page contents are encrypted anyway). .har lists names and sizes only.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (rec) "Live Traffic (recording)" else "Live Traffic (recording is off)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Close") }
                }
                if (flows.isEmpty()) Text("Nothing yet. Switch recording on, use your apps, then come back.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(flows.take(200)) { e ->
                        Column {
                            Text("${e.proto}  ${e.host}${if (e.proto == "DNS") "" else ":" + e.port}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("${e.app}  " + (if (e.blocked) "BLOCKED ${e.note}" else "allowed") + (if (e.proto != "DNS") "  up ${size(e.up)}  down ${size(e.down)}" else ""), style = MaterialTheme.typography.bodySmall,
                                color = if (e.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Setup wizard and switch for the browser web page cleaner. Needs the one-time certificate install in Android. */
@Composable
private fun HttpsCleanerCard() {
    val ctx = LocalContext.current
    val sdkOk = android.os.Build.VERSION.SDK_INT >= 29
    var installed by remember { mutableStateOf(sdkOk && com.fitzy.apexcleaner.net.mitm.MitmEngine.isInstalled(ctx)) }
    var on by remember { mutableStateOf(com.fitzy.apexcleaner.core.AppSettings.httpsCleaner(ctx)) }
    var paywall by remember { mutableStateOf(com.fitzy.apexcleaner.core.AppSettings.paywallReader(ctx)) }
    var msg by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Web page cleaner (browsers)", style = MaterialTheme.typography.titleMedium)
                    Text("Removes empty ad boxes and ad containers from web pages in Chrome-family browsers and Samsung Internet. Works by opening those pages on this phone with a private certificate that only this phone holds. It never touches banking, payment, sign-in, government or video-licence sites, your Allow list, Firefox, or other apps. Needs Android 10+, the shield on, and Streaming priority on. Off by default.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = on && installed, enabled = installed && sdkOk, onCheckedChange = { on = it; com.fitzy.apexcleaner.core.AppSettings.setHttpsCleaner(ctx, it) })
            }
            if (!sdkOk) Text("This phone is older than Android 10, so the cleaner is not available.", style = MaterialTheme.typography.bodySmall)
            else {
                Text(if (installed) "Certificate installed in Android: yes" else "Certificate installed in Android: not yet", style = MaterialTheme.typography.bodyMedium)
                if (!installed) {
                    Text("Step 1: save the certificate file. Step 2: open Android's security settings and install it (usually Security, More security settings, Encryption and credentials, Install a certificate, CA certificate, then pick AdShield-Root-CA.crt from Downloads; Android shows a warning, that is normal). Step 3: come back and tap Check again. You can remove it later in the same menu.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { msg = if (com.fitzy.apexcleaner.net.mitm.MitmEngine.saveToDownloads(ctx)) "Saved AdShield-Root-CA.crt in Downloads." else "Could not save the file." }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("1. Save certificate to Downloads") }
                    OutlinedButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("2. Open Android security settings") }
                }
                OutlinedButton(onClick = { installed = com.fitzy.apexcleaner.net.mitm.MitmEngine.recheck(ctx); msg = if (installed) "Certificate found." else "Not found yet." }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("3. Check again") }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Paywall reader (news sites)", style = MaterialTheme.typography.titleMedium)
                        Text("On a list of news sites, your browser says it is Googlebot arriving from Google, paywall pop-ups are hidden and scrolling comes back, Piano and Tinypass names are blocked, and an Archived copy button appears for hard paywalls. Only sites that show search engines the full article respond; some sites forbid this in their terms and may block your visit. Needs the web page cleaner switched on. Off by default.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = paywall && on && installed, enabled = on && installed, onCheckedChange = { paywall = it; com.fitzy.apexcleaner.core.AppSettings.setPaywallReader(ctx, it); com.fitzy.apexcleaner.net.ShieldRules.apply(ctx, com.fitzy.apexcleaner.net.ShieldRules.live) })
                }
                Text("Limits: pages already loaded keep their ads until reloaded; sites that refuse our certificate show an error until you tap Allow on them in Network Logs.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
