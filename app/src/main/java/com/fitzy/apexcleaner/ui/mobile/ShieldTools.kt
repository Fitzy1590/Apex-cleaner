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
import com.fitzy.apexcleaner.net.RouterScripts

/** Settings rows for the shield tools: Quiet hours (PIN-locked) and the router script maker. */
@Composable
fun ShieldToolsRows() {
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
