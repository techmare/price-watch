package com.techmare.pricewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.core.Position

enum class Filter(val label: String) {
    CHEAPER("Cheaper elsewhere"), SAME("Same"), CHEAPEST("You're cheapest"), NONE("Not elsewhere"), ALL("All")
}

@Composable
fun ProductsScreen(state: UiState, initialFilter: Filter, onOpen: (Comparison) -> Unit) {
    val c = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    val comps = state.comparisons
    val listState = rememberLazyListState()

    fun inFilter(cmp: Comparison, f: Filter) = when (f) {
        Filter.CHEAPER -> cmp.position == Position.CHEAPER_ELSEWHERE
        Filter.CHEAPEST -> cmp.position == Position.CHEAPEST
        Filter.SAME -> cmp.position == Position.SAME
        Filter.NONE -> cmp.position == Position.NO_MATCH || cmp.position == Position.OUT_OF_STOCK_ELSEWHERE
        Filter.ALL -> true
    }
    val q = Matcher.normalize(query).split(" ").filter { it.isNotBlank() }
    val shown = comps.filter { inFilter(it, filter) }
        .filter { cmp -> q.isEmpty() || Matcher.normalize(cmp.own.name).let { n -> q.all { n.contains(it) } } }
        .let { list ->
            when (filter) {
                Filter.CHEAPER -> list.sortedByDescending { it.diff ?: 0.0 }
                Filter.CHEAPEST -> list.sortedBy { it.diff ?: 0.0 }
                else -> list.sortedBy { Matcher.shortName(it.own.name).lowercase() }
            }
        }
    LaunchedEffect(filter, query) { listState.scrollToItem(0) }

    // the same product listed more than once on your site, often at different prices
    val sameName = remember(comps) { comps.groupBy { Matcher.shortName(it.own.name).lowercase() } }

    Column {
        SearchBox(query, { query = it }, "Search your ${comps.size} products", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        TextTabs(Filter.entries, filter, { it.label }, { f -> comps.count { inFilter(it, f) } }) { filter = it }
        RowDivider()
        if (shown.isEmpty()) {
            Text(
                when {
                    comps.isEmpty() -> "Your products appear here once your own prices are read. Until then, use Market to see competitor prices."
                    query.isNotBlank() -> "None of these products match \"$query\"."
                    else -> "No products here right now."
                },
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            itemsIndexed(shown, key = { _, it -> it.own.url }) { i, cmp ->
                val best = cmp.best
                val twin = sameName[Matcher.shortName(cmp.own.name).lowercase()].orEmpty().firstOrNull { it !== cmp }
                val sub = when {
                    cmp.offers.isEmpty() -> "Not found at other shops"
                    best == null -> "Out of stock at ${cmp.offers.size} shop${if (cmp.offers.size > 1) "s" else ""}"
                    else -> "${best.siteName} ${dh(best.price)}" + if (cmp.offers.size > 1) "  +${cmp.offers.size - 1} shop${if (cmp.offers.size > 2) "s" else ""}" else ""
                } + (twin?.let { ". Also listed at ${dh(it.own.price)}" } ?: "")
                QuoteRow(
                    Matcher.shortName(cmp.own.name), sub, Modifier.clickable { onOpen(cmp) },
                    edge = when (cmp.position) {
                        Position.CHEAPER_ELSEWHERE -> c.high
                        Position.CHEAPEST -> c.ok
                        Position.SAME -> c.faint
                        else -> c.rule
                    },
                    dim = !cmp.own.inStock,
                ) {
                    Price(cmp.own.price, Num.rowStrong)
                    when (cmp.position) {
                        Position.CHEAPER_ELSEWHERE, Position.CHEAPEST -> Text(signedNum(cmp.diff ?: 0.0), style = Num.small, color = diffColor(cmp.diff, state.threshold))
                        Position.SAME -> Text("same", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        else -> if (!cmp.own.inStock) Text("out of stock", style = MaterialTheme.typography.bodySmall, color = c.mid)
                    }
                }
                if (i < shown.lastIndex) RowDivider()
            }
        }
    }
}
