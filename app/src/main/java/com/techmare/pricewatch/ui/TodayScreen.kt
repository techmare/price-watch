package com.techmare.pricewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.MainViewModel
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.core.Position
import com.techmare.pricewatch.core.PricePoint

@Composable
fun TodayScreen(
    vm: MainViewModel,
    state: UiState,
    onOpen: (Comparison) -> Unit,
    onShops: () -> Unit,
    onFilter: (Filter) -> Unit,
    onSearch: () -> Unit,
) {
    val c = LocalAppColors.current
    val snap = state.snapshot
    if (snap == null) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            if (state.loading) CircularProgressIndicator(color = c.signal)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No prices yet", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.offline) "Couldn't reach the internet. Pull down to try again."
                    else "The first price check hasn't finished yet. Pull down in a few minutes.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
            }
        }
        return
    }

    val comps = state.comparisons
    val matched = comps.filter { it.offers.isNotEmpty() }
    val cheaper = comps.filter { it.position == Position.CHEAPER_ELSEWHERE }.sortedByDescending { it.diff ?: 0.0 }
    val byUrl = comps.flatMap { cmp -> cmp.offers.map { it.url to cmp } }.toMap()
    val moves = state.changes.filter { it.url in byUrl && it.type in setOf("drop", "rise") && it.old != null && it.new != null }
        .sortedByDescending { kotlin.math.abs(it.new!! - it.old!!) }
    // only worth posting when nobody else has it in stock
    val outs = state.changes.filter { it.url in byUrl && it.type == "out" }
        .filter { ch -> byUrl.getValue(ch.url).offers.none { it.inStock } }
        .distinctBy { byUrl.getValue(it.url).own.url }
    val shops = snap.competitors.filter { it.id !in state.excluded }
    val working = shops.count { it.ok }
    val ownMissing = snap.own?.products.isNullOrEmpty()
    val siteName = { id: String -> snap.sites.firstOrNull { it.id == id }?.name ?: id }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        // freshness line: green when every watched shop answered, amber when some didn't
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onShops).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (working == shops.size) c.ok else c.mid, 7)
            Spacer(Modifier.width(8.dp))
            Text(
                "Checked ${prettyTime(snap.generated)}, $working of ${shops.size} shops" + if (state.offline) ". Offline" else "",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
        }

        TickerTape(
            moves.take(14).map { ch ->
                val cmp = byUrl.getValue(ch.url)
                Tick("${Matcher.shortName(cmp.own.name)}  ${siteName(ch.siteId)} ${num(ch.new!!)}", shortNum(ch.new - ch.old!!), down = ch.type == "drop")
            },
            onClick = { moves.firstOrNull()?.let { onOpen(byUrl.getValue(it.url)) } },
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            if (ownMissing) {
                Spacer(Modifier.height(12.dp))
                Panel {
                    Text("Your prices aren't connected yet", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The daily check couldn't read midoxshop.ma. Competitor prices are ready: " +
                            "${shops.sumOf { it.products.size }} products from $working shops.",
                        style = MaterialTheme.typography.bodyMedium, color = c.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Browse the market", Modifier.clickable(onClick = onSearch), style = MaterialTheme.typography.labelLarge, color = c.signal)
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Board(
                    cheaper = cheaper.size,
                    same = comps.count { it.position == Position.SAME },
                    cheapest = comps.count { it.position == Position.CHEAPEST },
                    onFilter = onFilter,
                )
                Text(
                    "${matched.size} of your ${comps.size} products found at other shops",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.faint,
                )
            }

            if (cheaper.isNotEmpty()) {
                SectionTitle("Biggest gaps", "See all ${cheaper.size}", onTrailing = { onFilter(Filter.CHEAPER) })
                cheaper.take(6).forEachIndexed { i, cmp ->
                    GapRow(vm, cmp, onClick = { onOpen(cmp) })
                    if (i < minOf(cheaper.size, 6) - 1) RowDivider()
                }
            }

            if (moves.isNotEmpty()) {
                SectionTitle("Moves since yesterday", "${moves.size}")
                moves.take(10).forEachIndexed { i, ch ->
                    val cmp = byUrl.getValue(ch.url)
                    val down = ch.type == "drop"
                    QuoteRow(
                        Matcher.shortName(cmp.own.name),
                        "${siteName(ch.siteId)}, was ${dh(ch.old!!)}",
                        Modifier.clickable { onOpen(cmp) },
                        edge = if (down) c.high else c.ok,
                    ) {
                        Price(ch.new!!)
                        Text((if (down) "▼ " else "▲ ") + signedNum(ch.new - ch.old), style = Num.small, color = if (down) c.high else c.ok)
                    }
                    if (i < minOf(moves.size, 10) - 1) RowDivider()
                }
                Note("Red: a shop got cheaper than before, so you're under more pressure. Green: they raised their price.")
            }

            if (outs.isNotEmpty()) {
                SectionTitle("Only you have these in stock", "${outs.size}")
                outs.take(10).forEachIndexed { i, ch ->
                    val cmp = byUrl.getValue(ch.url)
                    QuoteRow(
                        Matcher.shortName(cmp.own.name), "Just sold out at ${siteName(ch.siteId)}. A good one to post today",
                        Modifier.clickable { onOpen(cmp) }, edge = c.signal,
                    ) { Price(cmp.own.price) }
                    if (i < minOf(outs.size, 10) - 1) RowDivider()
                }
            }

            if (moves.isEmpty() && outs.isEmpty() && matched.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Note("No price changes on your products since the last check.")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Three big counts that open the matching product list. */
@Composable
private fun Board(cheaper: Int, same: Int, cheapest: Int, onFilter: (Filter) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(c.panel)
            .border(1.dp, c.rule, RoundedCornerShape(14.dp)),
    ) {
        BoardCell(Modifier.weight(1f), "▼", cheaper, "cheaper elsewhere", c.high) { onFilter(Filter.CHEAPER) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.rule))
        BoardCell(Modifier.weight(1f), "=", same, "about the same", c.muted) { onFilter(Filter.SAME) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.rule))
        BoardCell(Modifier.weight(1f), "▲", cheapest, "you're cheapest", c.ok) { onFilter(Filter.CHEAPEST) }
    }
}

@Composable
private fun BoardCell(modifier: Modifier, mark: String, value: Int, label: String, color: Color, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), style = Num.board, color = if (value == 0) c.faint else c.ink)
            Spacer(Modifier.width(6.dp))
            Text(mark, Modifier.padding(bottom = 5.dp), style = Num.small, color = color)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of the biggest gaps: name, how far above the cheapest you are, and the cheapest price's recent path. */
@Composable
private fun GapRow(vm: MainViewModel, cmp: Comparison, onClick: () -> Unit) {
    val c = LocalAppColors.current
    var path by remember(cmp.own.url) { mutableStateOf<List<PricePoint>>(emptyList()) }
    LaunchedEffect(cmp.own.url, cmp.offers) { path = vm.bestHistory(cmp) }
    val best = cmp.best ?: return
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Matcher.shortName(cmp.own.name), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Text(signedNum(cmp.diff ?: 0.0), style = Num.rowStrong, color = c.high)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("you ", style = MaterialTheme.typography.bodySmall, color = c.faint)
                    Price(cmp.own.price, Num.small, c.signal)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${best.siteName} ", style = MaterialTheme.typography.bodySmall, color = c.faint, maxLines = 1)
                    Price(best.price, Num.small, c.ink)
                }
            }
            if (path.map { it.date }.distinct().size >= 2) {
                Sparkline(path, cmp.own.price, Modifier.width(110.dp).height(30.dp))
            } else {
                Box(Modifier.width(110.dp)) { PriceLadder(cmp, height = 18) }
            }
        }
    }
}

/** "2026-10-26T05:17:44+00:00" -> "today at 06:17" in local time */
fun prettyTime(iso: String?): String = try {
    val t = java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val time = "%02d:%02d".format(t.hour, t.minute)
    when (t.toLocalDate()) {
        today -> "today at $time"
        today.minusDays(1) -> "yesterday at $time"
        else -> "%02d/%02d at %s".format(t.dayOfMonth, t.monthValue, time)
    }
} catch (e: Exception) { iso ?: "never" }
