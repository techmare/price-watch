package com.techmare.pricewatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.MainViewModel

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    TODAY("Today", "Price Watch", Icons.Outlined.Today),
    PRODUCTS("Products", "Your products", Icons.Outlined.Inventory2),
    SEARCH("Market", "Other shops", Icons.Outlined.Search),
    SHOPS("Shops", "Shops", Icons.Outlined.Storefront),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val c = LocalAppColors.current
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var productFilter by remember { mutableStateOf(Filter.CHEAPER) }
    var openUrl by remember { mutableStateOf<String?>(null) }
    val open = state.comparisons.firstOrNull { it.own.url == openUrl }
    BackHandler(enabled = openUrl != null) { openUrl = null }

    Scaffold(
        containerColor = c.paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (open != null) IconButton(onClick = { openUrl = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.ink) }
                else Spacer(Modifier.width(12.dp))
                Text(if (open != null) "Compare" else tab.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                if (state.loading) CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp), color = c.signal, strokeWidth = 2.dp)
                else IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Refresh", tint = c.muted) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && open == null,
                        onClick = { openUrl = null; tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = c.signal, selectedIconColor = Color(0xFF1C1F23), selectedTextColor = c.ink,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.snapshot != null,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    open != null -> DetailScreen(vm, open, state.threshold)
                    tab == Tab.TODAY -> TodayScreen(
                        state,
                        onOpen = { openUrl = it.own.url },
                        onShops = { tab = Tab.SHOPS },
                        onSeeAll = { productFilter = Filter.CHEAPER; tab = Tab.PRODUCTS },
                        onSearch = { tab = Tab.SEARCH },
                    )
                    tab == Tab.SEARCH -> MarketScreen(state)
                    tab == Tab.PRODUCTS -> ProductsScreen(state, productFilter) { openUrl = it.own.url }
                    else -> ShopsScreen(vm, state)
                }
            }
        }
    }
}
