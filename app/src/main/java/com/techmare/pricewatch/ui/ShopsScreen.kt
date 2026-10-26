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
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (snap != null) {
            snap.own?.let { own ->
                SectionTitle("Your shop")
                SiteRow(own, included = null, onToggle = {}, onOpen = { openUrl(context, own.url) })
            }
            SectionTitle("Competitors", "${snap.competitors.count { it.id !in state.excluded }} watched")
            snap.competitors.forEachIndexed { i, s ->
                SiteRow(s, included = s.id !in state.excluded, onToggle = { vm.setSiteIncluded(s.id, it) }, onOpen = { openUrl(context, s.url) })
                if (i < snap.competitors.lastIndex) HorizontalDivider(color = c.rule)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Ignore small differences")
        Text("Prices within this amount count as the same.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 20, 50, 100, 200).forEach { v ->
                FilterChip(
                    selected = state.threshold == v, onClick = { vm.setThreshold(v) },
                    label = { Text(if (v == 0) "Exact" else "$v dh") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.signal, selectedLabelColor = Color(0xFF1C1F23)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().toggleable(state.notify, role = Role.Switch, onValueChange = vm::setNotify).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Morning notification", style = MaterialTheme.typography.bodyLarge, color = c.ink)
                Text("At 9:00, when a competitor's price changes on one of your products.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Switch(
                checked = state.notify, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = c.signal, checkedThumbColor = Color(0xFF1C1F23), checkedBorderColor = c.signal),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Prices are checked every morning at about 6:15 from each shop's public product pages.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        Spacer(Modifier.height(16.dp))
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
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(status))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(
                when {
                    s.ok -> "${s.products.size} products, checked ${prettyTime(s.checked)}"
                    s.products.isNotEmpty() -> "Couldn't be read ${prettyTime(s.checked)}; showing older prices"
                    else -> "Couldn't be read: ${s.error ?: "unknown reason"}"
                },
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            Text(s.url.removePrefix("https://").removePrefix("www."), style = MaterialTheme.typography.bodySmall, color = c.muted,
                modifier = Modifier.clickable(onClick = onOpen))
        }
        if (included != null) {
            Switch(
                checked = included, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = c.signal, checkedThumbColor = Color(0xFF1C1F23), checkedBorderColor = c.signal),
            )
        }
    }
}
