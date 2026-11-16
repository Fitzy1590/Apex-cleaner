package com.fitzy.apexcleaner.ui.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fitzy.apexcleaner.power.ChargeUi
import com.fitzy.apexcleaner.ui.theme.Accent
import com.fitzy.apexcleaner.ui.theme.Warn

/** Live charging card: wattage dial, speed curve, battery health numbers and the charge limit. */
@Composable
fun ChargingCard(c: ChargeUi, onLimit: (Int) -> Unit, onFast: (Boolean) -> Unit) {
    val scaleMax = if ((c.watts ?: 0f) > 30f) 65f else 30f
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Charging", style = MaterialTheme.typography.titleMedium)
                if (c.fastActive) Text("Fast-Charge Active", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
            if (!c.pluggedIn) {
                Text("Not plugged in. Plug in a charger and Apex Cleaner switches on fast-charge care by itself.")
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    WattDial(c.watts, scaleMax)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.profile.label, style = MaterialTheme.typography.titleMedium)
                        Text(c.etaText)
                        Text("Battery ${c.level}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (c.tooWarm) {
                    Text(
                        "Your phone is warm (%.1f°C). Charging can slow down when it is hot. Taking it out of its case helps.".format(c.tempC),
                        color = Warn,
                    )
                }
                Text("Charging speed since you plugged in", style = MaterialTheme.typography.labelLarge)
                if (c.curve.size >= 2) {
                    SpeedCurve(c, scaleMax)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Plugged in", style = MaterialTheme.typography.bodySmall)
                        Text("Top line = ${scaleMax.toInt()} W", style = MaterialTheme.typography.bodySmall)
                        Text("Now", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("The speed curve appears after a few minutes of charging.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Battery health", style = MaterialTheme.typography.labelLarge)
                StatRow("Temperature", "%.1f°C".format(c.tempC), if (c.tooWarm) Warn else Color.Unspecified)
                StatRow("Voltage", "%.2f V".format(c.voltageV))
                StatRow("Current", c.currentA?.let { "%.2f A".format(it) } ?: "Not available on this phone")
                StatRow("Power (estimate)", c.watts?.let { "%.1f W".format(it) } ?: "Not available on this phone")
                StatRow("Charger type", c.profile.label)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Charge limit", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onLimit(c.limitPct - 5) }, enabled = c.limitPct > 50) { Text("−") }
                    Text("${c.limitPct}%", Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { onLimit(c.limitPct + 5) }, enabled = c.limitPct < 100) { Text("+") }
                }
            }
            Text(
                (if (c.limitPct >= 100) "You get an alert when the phone is fully charged." else "You get an alert at ${c.limitPct}% and again at 100%.") +
                    " Most phones do not let apps cut the charger power, so Apex Cleaner tells you the moment to unplug and ends fast-charge care at 100%.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Fast-charge care when plugged in", Modifier.weight(1f))
                Switch(checked = c.fastEnabled, onCheckedChange = onFast)
            }
            Text(
                "While on, Apex Cleaner pauses only its own background work. Alarms, widgets, music and your protected apps are never touched.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = valueColor)
    }
}

@Composable
private fun WattDial(watts: Float?, scaleMax: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val frac = ((watts ?: 0f) / scaleMax).coerceIn(0f, 1f)
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = 5.dp.toPx()
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), sz, style = stroke)
            drawArc(Accent, 135f, 270f * frac, false, Offset(inset, inset), sz, style = stroke)
        }
        Text(watts?.let { "%.1f W".format(it) } ?: "—", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SpeedCurve(c: ChargeUi, scaleMax: Float) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        fun yFor(watts: Float) = h - h * (watts / scaleMax).coerceIn(0f, 1f)
        val span = maxOf(c.curve.last().seconds, 120).toFloat()
        val path = Path()
        c.curve.forEachIndexed { i, p ->
            val x = w * p.seconds / span
            if (i == 0) path.moveTo(x, yFor(p.watts)) else path.lineTo(x, yFor(p.watts))
        }
        drawPath(path, Accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        c.watts?.let { drawCircle(Accent, 4.dp.toPx(), Offset(w * c.curve.last().seconds / span, yFor(it))) }
    }
}
