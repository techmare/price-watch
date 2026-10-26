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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.MainViewModel
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Offer
import com.techmare.pricewatch.core.Position
import com.techmare.pricewatch.core.PricePoint

private val SERIES = listOf(Color(0xFF4F8BD6), Color(0xFFB86BD1), Color(0xFF3FA3A0), Color(0xFFD6884F))

@Composable
fun DetailScreen(vm: MainViewModel, cmp: Comparison, threshold: Int) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var history by remember(cmp) { mutableStateOf<List<Pair<Color, List<PricePoint>>>>(emptyList()) }

    LaunchedEffect(cmp) {
        val own = c.signal to vm.history(cmp.own)
        val others = cmp.offers.take(4).mapIndexed { i, o -> SERIES[i] to vm.history(o) }
        history = listOf(own) + others
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Text(cmp.own.name, style = MaterialTheme.typography.titleLarge, color = c.panelText)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dh(cmp.own.price), style = MaterialTheme.typography.headlineMedium, color = c.panelText)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (cmp.position) {
                        Position.CHEAPER_ELSEWHERE -> "${signedDh(cmp.diff!!)} more than the cheapest"
                        Position.CHEAPEST -> "${dh(-cmp.diff!!)} below the cheapest competitor"
                        Position.SAME -> "about the same as the cheapest"
                        Position.NO_MATCH -> "not found at other shops"
                    },
                    Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (cmp.position) { Position.CHEAPER_ELSEWHERE -> c.high; Position.CHEAPEST -> c.ok; else -> c.panelMuted },
                )
            }
            if (!cmp.own.inStock) Text("Shown as out of stock on your site", style = MaterialTheme.typography.bodySmall, color = c.mid)
            if (cmp.offers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PriceLadder(cmp, height = 32)
                Row(Modifier.fillMaxWidth()) {
                    val prices = cmp.offers.map { it.price } + cmp.own.price
                    Text(dh(prices.min()), style = MaterialTheme.typography.bodySmall, color = c.panelMuted)
                    Spacer(Modifier.weight(1f))
                    Text(dh(prices.max()), style = MaterialTheme.typography.bodySmall, color = c.panelMuted)
                }
            }
        }

        if (cmp.offers.isNotEmpty()) {
            SectionTitle("At other shops", "${cmp.offers.size}")
            cmp.offers.forEachIndexed { i, o ->
                OfferRow(
                    o, color = SERIES.getOrNull(i), diff = o.price - cmp.own.price, threshold = threshold,
                    auto = o.url in cmp.autoMatched,
                    onOpen = { openUrl(context, o.url) },
                    onReject = { vm.reject(cmp.own.url, o) },
                )
                if (i < cmp.offers.lastIndex) HorizontalDivider(color = c.rule)
            }
        } else {
            Note("No other shop seems to sell this, or their product title is worded too differently to match automatically.")
        }
        OutlinedButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (cmp.offers.isEmpty()) "Find it at another shop" else "Add a missing shop")
        }

        val hasHistory = history.sumOf { it.second.size } > history.size
        if (hasHistory) {
            SectionTitle("Price history")
            HistoryChart(history)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.signal, "You")
                cmp.offers.take(4).forEachIndexed { i, o -> Legend(SERIES[i], o.siteName) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your price is read from your website. Matches are made from product titles; tap \"Not this product\" when one is wrong and it won't come back.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        Spacer(Modifier.height(12.dp))
    }

    if (searching) MatchSearchDialog(vm, onDismiss = { searching = false }, onPick = { vm.confirm(cmp.own.url, it); searching = false })
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun OfferRow(o: Offer, color: Color?, diff: Double, threshold: Int, auto: Boolean, onOpen: () -> Unit, onReject: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
            }
            Text(o.siteName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = c.ink)
            Column(horizontalAlignment = Alignment.End) {
                Text(dh(o.price), style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text(
                    when { diff < -threshold -> "${dh(-diff)} cheaper than you"; diff > threshold -> "${dh(diff)} more than you"; else -> "same as you" },
                    style = MaterialTheme.typography.bodySmall, color = diffColor(-diff, threshold),
                )
            }
            IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open page", tint = c.muted) }
        }
        Text(o.name, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    if (o.inStock) "In stock" else "Out of stock",
                    o.regular?.let { "was ${dh(it)}" },
                ).joinToString(", "),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (o.inStock) c.muted else c.mid,
            )
            if (auto) TextButton(onClick = onReject) { Text("Not this product") }
        }
    }
}

@Composable
private fun MatchSearchDialog(vm: MainViewModel, onDismiss: () -> Unit, onPick: (Offer) -> Unit) {
    val c = LocalAppColors.current
    var q by remember { mutableStateOf("") }
    val results = remember(q) { if (q.length >= 2) vm.search(q) else emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find at another shop") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("e.g. R50 18-45") }, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.url }) { o ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 8.dp)) {
                            Text(o.name, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${o.siteName}, ${dh(o.price)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        HorizontalDivider(color = c.rule)
                    }
                }
                if (q.length >= 2 && results.isEmpty()) Text("No product found with those words.", color = c.muted)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
