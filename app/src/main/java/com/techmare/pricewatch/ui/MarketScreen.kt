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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Matcher

private enum class Sort(val label: String) { CHEAP("Cheapest"), EXPENSIVE("Most expensive"), NAME("A to Z") }

/** Every competitor product in one list, with search, shop filter and sorting. */
@Composable
fun MarketScreen(state: UiState) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var shop by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.CHEAP) }
    var inStockOnly by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val snap = state.snapshot
    val shops = snap?.competitors?.filter { it.id !in state.excluded && it.products.isNotEmpty() }.orEmpty()
    // normalised names computed once per download
    val all = remember(snap, state.excluded) {
        shops.flatMap { s -> s.products.map { it to Matcher.normalize(it.name) } }
    }
    val words = Matcher.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val shown = remember(all, query, shop, sort, inStockOnly) {
        all.asSequence()
            .filter { (o, _) -> shop == null || o.siteId == shop }
            .filter { (o, _) -> !inStockOnly || o.inStock }
            .filter { (_, n) -> words.all { n.contains(it) } }
            .map { it.first }
            .let { seq ->
                when (sort) {
                    Sort.CHEAP -> seq.sortedBy { it.price }
                    Sort.EXPENSIVE -> seq.sortedByDescending { it.price }
                    Sort.NAME -> seq.sortedBy { it.name.lowercase() }
                }
            }.toList()
    }
    LaunchedEffect(query, shop, sort, inStockOnly) { listState.scrollToItem(0) }

    Column {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search, e.g. R50 18-45") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.signal, selectedLabelColor = Color(0xFF1C1F23))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = shop == null, onClick = { shop = null }, label = { Text("All shops") }, colors = chipColors)
            shops.forEach { s ->
                FilterChip(selected = shop == s.id, onClick = { shop = if (shop == s.id) null else s.id }, label = { Text(s.name) }, colors = chipColors)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sort.entries.forEach { s ->
                FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }, colors = chipColors)
            }
            FilterChip(selected = inStockOnly, onClick = { inStockOnly = !inStockOnly }, label = { Text("In stock only") }, colors = chipColors)
        }
        Text(
            when {
                snap == null -> "No prices downloaded yet."
                shown.isEmpty() -> "No product found."
                else -> "${shown.size} products"
            },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, o -> o.siteId + o.url }) { i, o ->
                Column(Modifier.clickable { openUrl(context, o.url) }) {
                    ProductLine(
                        name = o.name,
                        sub = o.siteName + if (!o.inStock) ", out of stock" else "",
                        right = dh(o.price), rightColor = if (o.inStock) c.ink else c.muted,
                        rightSub = o.regular?.let { "was ${dh(it)}" },
                    )
                }
                if (i < shown.lastIndex) HorizontalDivider(color = c.rule)
            }
        }
    }
}
