package com.techmare.pricewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Position

@Composable
fun TodayScreen(state: UiState, onOpen: (Comparison) -> Unit, onShops: () -> Unit, onSeeAll: () -> Unit, onSearch: () -> Unit) {
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
    val matched = comps.filter { it.position != Position.NO_MATCH }
    val cheaper = comps.filter { it.position == Position.CHEAPER_ELSEWHERE }.sortedByDescending { it.diff ?: 0.0 }
    val byUrl = comps.flatMap { cmp -> cmp.offers.map { it.url to cmp } }.toMap()
    val relevantChanges = state.changes.filter { it.url in byUrl && it.type in setOf("drop", "rise") }
    val outs = state.changes.filter { it.url in byUrl && it.type == "out" }
    val shops = snap.competitors.filter { it.id !in state.excluded }
    val failed = shops.filter { !it.ok }
    val ownMissing = snap.own?.products.isNullOrEmpty()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ownMissing) {
            Panel {
                Text("Your prices aren't connected yet", style = MaterialTheme.typography.headlineMedium, color = c.panelText)
                Spacer(Modifier.height(6.dp))
                Text(
                    "midoxshop.ma blocks automatic visitors, so the daily check can't read your prices. " +
                        "Competitor prices are ready: ${shops.sumOf { it.products.size }} products from ${shops.count { it.ok }} shops.",
                    style = MaterialTheme.typography.bodyMedium, color = c.panelMuted,
                )
                TextButton(onClick = onSearch, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Search competitor prices", color = c.signal)
                }
            }
        } else Panel {
            Text(
                when {
                    matched.isEmpty() -> "No matches yet"
                    cheaper.isEmpty() -> "Nobody beats your prices"
                    cheaper.size == 1 -> "1 product is cheaper elsewhere"
                    else -> "${cheaper.size} products are cheaper elsewhere"
                },
                style = MaterialTheme.typography.headlineMedium, color = c.panelText,
            )
            Text(
                "Out of ${matched.size} products found at ${shops.size} shops",
                style = MaterialTheme.typography.bodyMedium, color = c.panelMuted,
            )
            cheaper.take(4).forEach { cmp ->
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clickable { onOpen(cmp) }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(cmp.own.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = c.panelText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(signedDh(cmp.diff ?: 0.0), style = MaterialTheme.typography.titleSmall, color = c.high)
                    }
                    PriceLadder(cmp)
                    val best = cmp.best
                    if (best != null) {
                        Text("${best.siteName} ${dh(best.price)}, you ${dh(cmp.own.price)}", style = MaterialTheme.typography.bodySmall, color = c.panelMuted)
                    }
                }
            }
            if (cheaper.size > 4) {
                TextButton(onClick = onSeeAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("See all ${cheaper.size}", color = c.signal)
                }
            }
        }

        if (relevantChanges.isNotEmpty()) {
            SectionTitle("Since yesterday", "${relevantChanges.size} changes")
            relevantChanges.take(12).forEachIndexed { i, ch ->
                val cmp = byUrl.getValue(ch.url)
                val shop = snap.sites.firstOrNull { it.id == ch.siteId }?.name ?: ch.siteId
                val down = ch.type == "drop"
                Row(Modifier.fillMaxWidth().clickable { onOpen(cmp) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (down) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, null, Modifier.size(18.dp), tint = if (down) c.high else c.ok)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cmp.own.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$shop: ${ch.old?.let { dh(it) } ?: "?"} to ${ch.new?.let { dh(it) } ?: "?"}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                if (i < minOf(relevantChanges.size, 12) - 1) HorizontalDivider(color = c.rule)
            }
        }

        if (outs.isNotEmpty()) {
            SectionTitle("Out of stock at competitors")
            Note("A good moment to post these: buyers looking for them won't find them elsewhere.")
            outs.take(10).forEach { ch ->
                val cmp = byUrl.getValue(ch.url)
                val shop = snap.sites.firstOrNull { it.id == ch.siteId }?.name ?: ch.siteId
                ProductLine(cmp.own.name, "Sold out at $shop", dh(cmp.own.price), c.ink)
            }
        }

        if (relevantChanges.isEmpty() && outs.isEmpty() && matched.isNotEmpty()) {
            Note("No price changes on your products since the last check.")
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Checked ${prettyTime(snap.generated)}." + if (failed.isNotEmpty()) " ${failed.size} shop${if (failed.size > 1) "s" else ""} couldn't be read." else "",
            Modifier.clickable(onClick = onShops),
            style = MaterialTheme.typography.bodySmall, color = if (failed.isNotEmpty()) c.mid else c.muted,
        )
        if (state.offline) Text("Offline: showing the last prices downloaded.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(8.dp))
    }
}

/** "2026-10-26T05:17:44+00:00" -> "26/10 at 06:17" in local time */
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
