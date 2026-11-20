package com.techmare.pricewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.core.Offer
import com.techmare.pricewatch.core.Position
import com.techmare.pricewatch.core.PricePoint

private val SERIES = listOf(Color(0xFF5B9BE6), Color(0xFFC07BE0), Color(0xFF3FB8B0), Color(0xFFE59A5C), Color(0xFF9AA3F0), Color(0xFFD4B48C), Color(0xFF7CC4E4))

@Composable
fun DetailScreen(vm: MainViewModel, cmp: Comparison, threshold: Int) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var history by remember(cmp) { mutableStateOf<List<Pair<Color, List<PricePoint>>>>(emptyList()) }

    LaunchedEffect(cmp) {
        val own = c.signal to vm.history(cmp.own)
        val others = cmp.offers.take(SERIES.size).mapIndexed { i, o -> SERIES[i] to vm.history(o) }
        history = listOf(own) + others
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // your quote
        Text(Matcher.displayName(cmp.own.name), style = MaterialTheme.typography.titleLarge, color = c.ink)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Price(cmp.own.price, Num.hero, c.ink)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val d = cmp.diff
                when (cmp.position) {
                    Position.CHEAPER_ELSEWHERE, Position.CHEAPEST -> {
                        Text(signedNum(d!!), style = Num.rowStrong, color = diffColor(d, threshold))
                        Text(if (d > 0) "above the cheapest" else "below the cheapest", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Position.SAME -> Text("same as the cheapest", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Position.OUT_OF_STOCK_ELSEWHERE -> Text("out of stock everywhere else", style = MaterialTheme.typography.bodySmall, color = c.ok)
                    Position.NO_MATCH -> Text("not found at other shops", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        if (!cmp.own.inStock) Text("Shown as out of stock on your site", style = MaterialTheme.typography.bodySmall, color = c.mid)
        if (cmp.offers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PriceLadder(cmp, height = 26, colors = SERIES)
            Row(Modifier.fillMaxWidth()) {
                val prices = cmp.offers.map { it.price } + cmp.own.price
                Text(num(prices.min()), style = Num.small, color = c.faint)
                Spacer(Modifier.weight(1f))
                Text(num(prices.max()), style = Num.small, color = c.faint)
            }
        }

        // every shop's quote, cheapest first
        if (cmp.offers.isNotEmpty()) {
            SectionTitle("Other shops", "${cmp.offers.size}")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Shop", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.faint)
                Text("Price", style = MaterialTheme.typography.labelMedium, color = c.faint)
                Spacer(Modifier.width(16.dp))
                Text("You're", Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium, color = c.faint, textAlign = TextAlign.End)
            }
            RowDivider()
            cmp.offers.forEachIndexed { i, o ->
                OfferRow(
                    o, color = SERIES.getOrNull(i), diff = o.price - cmp.own.price, threshold = threshold,
                    auto = o.url in cmp.autoMatched,
                    onOpen = { openUrl(context, o.url) },
                    onReject = { vm.reject(cmp.own.url, o) },
                )
                if (i < cmp.offers.lastIndex) RowDivider()
            }
        } else if (cmp.possible.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Note("No other shop seems to sell this, or their title is worded too differently to match automatically.")
        }

        if (cmp.possible.isNotEmpty()) {
            SectionTitle("Possible matches", "${cmp.possible.size}")
            Note("Same model, but the price is far from yours: a bundle, a used item or a typo? Confirm the ones that really are this product.")
            Spacer(Modifier.height(4.dp))
            cmp.possible.forEachIndexed { i, o ->
                PossibleRow(
                    o, diff = o.price - cmp.own.price,
                    onOpen = { openUrl(context, o.url) },
                    onConfirm = { vm.confirm(cmp.own.url, o) },
                    onReject = { vm.reject(cmp.own.url, o) },
                )
                if (i < cmp.possible.lastIndex) RowDivider()
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (cmp.offers.isEmpty()) "Find it at another shop" else "Add a shop that's missing",
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, c.rule, RoundedCornerShape(10.dp))
                .clickable { searching = true }.padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge, color = c.ink, textAlign = TextAlign.Center,
        )

        val hasHistory = history.any { it.second.map { p -> p.date }.distinct().size >= 2 }
        if (hasHistory) {
            SectionTitle("Price history")
            Spacer(Modifier.height(6.dp))
            HistoryChart(history)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.signal, "You")
                cmp.offers.take(SERIES.size).forEachIndexed { i, o -> Legend(SERIES[i], o.siteName) }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (cmp.offers.isNotEmpty() || cmp.possible.isNotEmpty()) Text(
            "Matches are made from product titles. Tap \"Not this\" when one is wrong and it won't come back.",
            style = MaterialTheme.typography.bodySmall, color = c.faint,
        )
        Spacer(Modifier.height(20.dp))
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

/** One shop's quote: colour key, shop, their title, price and difference, then quiet actions. */
@Composable
private fun OfferRow(o: Offer, color: Color?, diff: Double, threshold: Int, auto: Boolean, onOpen: () -> Unit, onReject: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color ?: c.muted))
            Spacer(Modifier.width(8.dp))
            Text(o.siteName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (o.inStock) c.ink else c.muted, maxLines = 1)
            Price(o.price, Num.row, if (o.inStock) c.ink else c.muted)
            Spacer(Modifier.width(16.dp))
            // same convention everywhere: your price minus theirs, red when you're more expensive
            Text(
                if (kotlin.math.abs(diff) <= threshold) "=" else signedNum(-diff),
                Modifier.width(76.dp), style = Num.row, color = diffColor(-diff, threshold), textAlign = TextAlign.End,
            )
        }
        Text(
            Matcher.displayName(o.name), Modifier.padding(start = 16.dp, top = 2.dp),
            style = MaterialTheme.typography.bodySmall, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (o.inStock) "In stock" else "Out of stock", style = MaterialTheme.typography.bodySmall, color = if (o.inStock) c.muted else c.mid)
            o.regular?.let { Text(", was ${dh(it)}", style = MaterialTheme.typography.bodySmall, color = c.faint) }
            Spacer(Modifier.weight(1f))
            Text(
                if (auto) "Not this" else "Remove match",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReject).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = c.faint,
            )
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open page", Modifier.padding(start = 6.dp).size(16.dp), tint = c.faint)
        }
    }
}

@Composable
private fun PossibleRow(o: Offer, diff: Double, onOpen: () -> Unit, onConfirm: () -> Unit, onReject: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.siteName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = c.ink)
            Price(o.price)
            Spacer(Modifier.width(16.dp))
            Text(signedNum(-diff), Modifier.width(76.dp), style = Num.row, color = c.mid, textAlign = TextAlign.End)
        }
        Text(Matcher.displayName(o.name), style = MaterialTheme.typography.bodySmall, color = c.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Text(
                "Not this", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReject).padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge, color = c.muted,
            )
            Text(
                "Same product", Modifier.clip(RoundedCornerShape(8.dp)).background(c.signal).clickable(onClick = onConfirm)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge, color = Color(0xFF15181C),
            )
        }
    }
}

@Composable
private fun MatchSearchDialog(vm: MainViewModel, onDismiss: () -> Unit, onPick: (Offer) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        MatchSearchContent(search = vm::search, onDismiss = onDismiss, onPick = onPick)
    }
}

/** The search card shown in the dialog; separate so it can be previewed and tested on its own. */
@Composable
fun MatchSearchContent(search: (String) -> List<Offer>, onDismiss: () -> Unit, onPick: (Offer) -> Unit, initial: String = "") {
    val c = LocalAppColors.current
    var q by remember { mutableStateOf(initial) }
    val results = remember(q) { if (q.trim().length >= 2) search(q) else emptyList() }
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp), color = c.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.rule),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Find at another shop", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(12.dp))
            SearchBox(q, { q = it }, "Model, e.g. R50 18-45")
            Spacer(Modifier.height(8.dp))
            // fixed height: a scrolling list inside a dialog must not size itself to its content
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                when {
                    q.trim().length < 2 -> Text("Type a model, e.g. a6700 or HERO13.", Modifier.padding(top = 8.dp), color = c.muted)
                    results.isEmpty() -> Text("No product found with those words.", Modifier.padding(top = 8.dp), color = c.muted)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.siteId + it.url }) { o ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 8.dp)) {
                                Text(o.name, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.siteName + if (!o.inStock) ", out of stock" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
                                    Price(o.price, Num.small)
                                }
                            }
                            RowDivider()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
