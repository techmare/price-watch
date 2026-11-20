package com.techmare.pricewatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.MainViewModel

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    TODAY("Today", "Price Watch", Icons.Outlined.QueryStats),
    PRODUCTS("Products", "Your products", Icons.Outlined.Inventory2),
    SEARCH("Market", "Market", Icons.Outlined.Search),
    SHOPS("Shops", "Shops", Icons.Outlined.Storefront),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val c = LocalAppColors.current
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var productFilter by rememberSaveable { mutableStateOf(Filter.CHEAPER) }
    var openUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.comparisons.firstOrNull { it.own.url == openUrl }
    BackHandler(enabled = openUrl != null) { openUrl = null }
    val tabs = rememberSaveableStateHolder()

    Scaffold(
        containerColor = c.paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(c.paper).statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (open != null) IconButton(onClick = { openUrl = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.ink) }
                else Spacer(Modifier.width(12.dp))
                Text(if (open != null) "Product" else tab.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                if (state.loading) CircularProgressIndicator(Modifier.padding(14.dp).size(20.dp), color = c.signal, strokeWidth = 2.dp)
                else IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Refresh", tint = c.muted) }
            }
        },
        bottomBar = {
            BottomBar(selected = tab.takeIf { open == null }) { openUrl = null; tab = it }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.snapshot != null,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (open != null) DetailScreen(vm, open, state.threshold)
                // each tab keeps its search, filters and scroll position while you look at a product
                else tabs.SaveableStateProvider(tab.name) { when (tab) {
                    Tab.TODAY -> TodayScreen(
                        vm, state,
                        onOpen = { openUrl = it.own.url },
                        onShops = { tab = Tab.SHOPS },
                        onFilter = { productFilter = it; tab = Tab.PRODUCTS },
                        onSearch = { tab = Tab.SEARCH },
                    )
                    Tab.SEARCH -> MarketScreen(state)
                    Tab.PRODUCTS -> ProductsScreen(state, productFilter) { openUrl = it.own.url }
                    Tab.SHOPS -> ShopsScreen(vm, state)
                } }
            }
        }
    }
}

/** Flat bar: the selected tab gets a yellow line along the top and full-strength text. */
@Composable
private fun BottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(c.panel).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(36.dp).height(2.dp).background(if (on) c.signal else c.panel))
                    Spacer(Modifier.height(8.dp))
                    Icon(t.icon, null, Modifier.size(22.dp), tint = if (on) c.ink else c.faint)
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.faint)
                }
            }
        }
    }
}
