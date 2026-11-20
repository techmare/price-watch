package com.techmare.pricewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Matcher

private enum class Sort(val label: String) { CHEAP("Cheapest"), EXPENSIVE("Priciest"), NAME("A–Z") }

/** Every competitor product in one list, with search, shop filter and sorting. */
@Composable
fun MarketScreen(state: UiState) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var shop by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.CHEAP) }
    var inStockOnly by rememberSaveable { mutableStateOf(true) }
    var gearOnly by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val snap = state.snapshot
    val shops = snap?.competitors?.filter { it.id !in state.excluded && it.products.isNotEmpty() }.orEmpty()
    // normalised names computed once per download
    val all = remember(snap, state.excluded) {
        shops.flatMap { s -> s.products.map { Triple(it, Matcher.normalize(it.name), Matcher.isGear(it.name)) } }
    }
    val words = Matcher.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val shown = remember(all, query, shop, sort, inStockOnly, gearOnly) {
        all.asSequence()
            .filter { (o, _, _) -> shop == null || o.siteId == shop }
            .filter { (o, _, _) -> !inStockOnly || o.inStock }
            .filter { (_, _, gear) -> !gearOnly || gear }
            .filter { (_, n, _) -> words.all { n.contains(it) } }
            .map { it.first }
            .let { seq ->
                when (sort) {
                    Sort.CHEAP -> seq.sortedBy { it.price }
                    Sort.EXPENSIVE -> seq.sortedByDescending { it.price }
                    Sort.NAME -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }
    LaunchedEffect(query, shop, sort, inStockOnly, gearOnly) { listState.scrollToItem(0) }

    Column {
        SearchBox(query, { query = it }, "Search every shop, e.g. R50 18-45", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        TextTabs(listOf<String?>(null) + shops.map { it.id }, shop, { id -> shops.firstOrNull { it.id == id }?.name ?: "All shops" }) { shop = it }
        RowDivider()
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceRow(Sort.entries, sort, { it.label }, { sort = it })
            ToggleChip("In stock", inStockOnly) { inStockOnly = !inStockOnly }
            ToggleChip("Camera gear", gearOnly) { gearOnly = !gearOnly }
        }
        Text(
            when {
                snap == null -> "No prices downloaded yet."
                shown.isEmpty() -> "Nothing matches. Try fewer words, or turn off a filter."
                else -> "${shown.size} products"
            },
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = c.faint,
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            itemsIndexed(shown, key = { _, o -> o.siteId + o.url }) { i, o ->
                QuoteRow(
                    Matcher.displayName(o.name),
                    o.siteName + if (!o.inStock) ", out of stock" else "",
                    Modifier.clickable { openUrl(context, o.url) },
                    dim = !o.inStock,
                ) {
                    Price(o.price, Num.rowStrong, if (o.inStock) c.ink else c.muted)
                    o.regular?.let { r ->
                        Text(
                            num(r), style = Num.small.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                            color = c.faint,
                        )
                    }
                }
                if (i < shown.lastIndex) RowDivider()
            }
        }
    }
}
