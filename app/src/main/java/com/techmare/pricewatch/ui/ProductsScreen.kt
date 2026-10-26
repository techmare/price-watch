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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.UiState
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.core.Position

enum class Filter(val label: String) {
    CHEAPER("Cheaper elsewhere"), CHEAPEST("You're cheapest"), SAME("Same price"), NONE("Not found elsewhere"), ALL("All")
}

@Composable
fun ProductsScreen(state: UiState, initialFilter: Filter, onOpen: (Comparison) -> Unit) {
    val c = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    val comps = state.comparisons

    fun inFilter(cmp: Comparison, f: Filter) = when (f) {
        Filter.CHEAPER -> cmp.position == Position.CHEAPER_ELSEWHERE
        Filter.CHEAPEST -> cmp.position == Position.CHEAPEST
        Filter.SAME -> cmp.position == Position.SAME
        Filter.NONE -> cmp.position == Position.NO_MATCH
        Filter.ALL -> true
    }
    val q = Matcher.normalize(query).split(" ").filter { it.isNotBlank() }
    val shown = comps.filter { inFilter(it, filter) }
        .filter { cmp -> q.isEmpty() || Matcher.normalize(cmp.own.name).let { n -> q.all { n.contains(it) } } }
        .let { list -> if (filter == Filter.CHEAPER) list.sortedByDescending { it.diff ?: 0.0 } else list.sortedBy { it.own.name.lowercase() } }

    Column {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search your products") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f, onClick = { filter = f },
                    label = { Text("${f.label} ${comps.count { inFilter(it, f) }}") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.signal, selectedLabelColor = Color(0xFF1C1F23)),
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (comps.isEmpty()) "No products yet. Your prices are read from midoxshop.ma each morning."
                else "Nothing here.",
                Modifier.padding(24.dp), color = c.muted,
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, it -> it.own.url }) { i, cmp ->
                val best = cmp.best
                val sub = when {
                    best == null -> "Not found at other shops"
                    else -> "${best.siteName} ${dh(best.price)}" + if (cmp.offers.size > 1) ", ${cmp.offers.size} shops" else ""
                }
                Column(Modifier.clickable { onOpen(cmp) }) {
                    ProductLine(
                        name = cmp.own.name, sub = sub,
                        right = dh(cmp.own.price), rightColor = c.ink,
                        rightSub = cmp.diff?.let { if (cmp.position == Position.SAME) "same" else signedDh(it) },
                        rightSubColor = diffColor(cmp.diff, state.threshold),
                    )
                }
                if (i < shown.lastIndex) HorizontalDivider(color = c.rule)
            }
        }
    }
}
