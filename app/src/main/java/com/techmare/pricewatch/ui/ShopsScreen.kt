package com.techmare.pricewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.MainViewModel
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Site

@Composable
fun ShopsScreen(vm: MainViewModel, state: UiState) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val snap = state.snapshot
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (snap != null) {
            snap.own?.let { own ->
                SectionTitle("Your shop")
                SiteRow(own, included = null, onToggle = {}, onOpen = { openUrl(context, own.url) })
            }
            SectionTitle("Competitors", "${snap.competitors.count { it.ok && it.id !in state.excluded }} of ${snap.competitors.size} working")
            // working shops first, then the ones that couldn't be read
            val sorted = snap.competitors.sortedWith(compareBy({ !it.ok }, { it.name.lowercase() }))
            sorted.forEachIndexed { i, s ->
                SiteRow(s, included = s.id !in state.excluded, onToggle = { vm.setSiteIncluded(s.id, it) }, onOpen = { openUrl(context, s.url) })
                if (i < sorted.lastIndex) RowDivider()
            }
        }

        SectionTitle("Count as the same price")
        Text("Differences up to this amount are treated as equal.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(listOf(0, 20, 50, 100, 200), state.threshold, { if (it == 0) "Exact" else "$it dh" }, vm::setThreshold)

        SectionTitle("Notifications")
        Row(
            Modifier.fillMaxWidth().toggleable(state.notify, role = Role.Switch, onValueChange = vm::setNotify).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Price changes on your products", style = MaterialTheme.typography.bodyLarge, color = c.ink)
                Text("Once a day, when the new prices arrive. Never at night.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Switch(
                checked = state.notify, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.signal, checkedThumbColor = Color(0xFF15181C), checkedBorderColor = c.signal,
                    uncheckedTrackColor = c.panel, uncheckedThumbColor = c.faint, uncheckedBorderColor = c.rule,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Note("Prices are read once a day, overnight, from each shop's public product pages. A shop that takes too long is skipped and keeps its last prices.")
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SiteRow(s: Site, included: Boolean?, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val c = LocalAppColors.current
    val status = when {
        !s.ok && s.products.isNotEmpty() -> c.mid
        !s.ok -> c.high
        else -> c.ok
    }
    val off = included == false
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (off) c.faint else status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, color = if (off) c.muted else c.ink)
                Spacer(Modifier.width(8.dp))
                Text(s.url.removePrefix("https://").removePrefix("www.").trimEnd('/'), style = MaterialTheme.typography.bodySmall, color = c.faint, maxLines = 1)
            }
            Text(
                when {
                    off -> "Hidden from comparisons"
                    s.ok -> "Read ${prettyTime(s.checked)}"
                    s.products.isNotEmpty() -> "Couldn't be read ${prettyTime(s.checked)}. Showing older prices"
                    s.own -> "Couldn't read your prices ${prettyTime(s.checked)}"
                    else -> "Couldn't read its products ${prettyTime(s.checked)}"
                },
                style = MaterialTheme.typography.bodySmall, color = if (!s.ok && !off) status else c.muted,
            )
        }
        if (s.products.isNotEmpty()) {
            Text(num(s.products.size.toDouble()), style = Num.row, color = if (off) c.faint else c.muted)
            Spacer(Modifier.width(12.dp))
        }
        if (included != null) {
            Switch(
                checked = included, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.ink, checkedThumbColor = c.paper, checkedBorderColor = c.ink,
                    uncheckedTrackColor = c.paper, uncheckedThumbColor = c.faint, uncheckedBorderColor = c.faint,
                ),
            )
        }
    }
}
