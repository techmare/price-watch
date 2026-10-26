package com.techmare.pricewatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Compare

/**
 * Search every competitor's catalogue at once, cheapest first.
 * Useful on its own, and the main view until your own prices are connected.
 */
@Composable
fun MarketScreen(state: UiState, header: (@Composable () -> Unit)? = null) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val snap = state.snapshot
    val shops = snap?.competitors?.filter { it.id !in state.excluded && it.products.isNotEmpty() }.orEmpty()
    val results = remember(query, snap, state.excluded) {
        if (snap == null || query.trim().length < 2) emptyList()
        else Compare.search(snap, query, limit = 200).filter { it.siteId !in state.excluded }
            .sortedWith(compareBy({ !it.inStock }, { it.price }))
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) item { header() }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                placeholder = { Text("Search ${shops.size} shops, e.g. R50 18-45") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
            )
        }
        if (query.trim().length >= 2) {
            item {
                Text(
                    if (results.isEmpty()) "No product found with those words." else "${results.size} results, cheapest first",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            itemsIndexed(results, key = { _, o -> o.url }) { i, o ->
                Column(Modifier.clickable { openUrl(context, o.url) }) {
                    ProductLine(
                        name = o.name,
                        sub = o.siteName + if (!o.inStock) ", out of stock" else "",
                        right = dh(o.price), rightColor = if (o.inStock) c.ink else c.muted,
                        rightSub = o.regular?.let { "was ${dh(it)}" },
                    )
                }
                if (i < results.lastIndex) HorizontalDivider(color = c.rule)
            }
        } else {
            item {
                Text(
                    "Type a model to see every shop's price side by side. Tap a result to open it on the shop's site.",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
            }
        }
    }
}
