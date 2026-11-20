package com.techmare.pricewatch.screens

import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.techmare.pricewatch.MainViewModel
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.ui.App
import com.techmare.pricewatch.ui.PriceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * Walks through every screen with the repo's real data and saves a screenshot of each:
 * ./gradlew testDebugUnitTest -Pscreens --tests '*ScreenTour*'  (images in app/build/screens)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-night-xxhdpi")
class ScreenTour {
    @get:Rule val compose = createComposeRule()

    private val out = System.getProperty("screens.dir") ?: "build/screens"

    private fun shot(name: String) {
        // let background loads (price history) land before the picture
        repeat(10) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(60) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun tab(label: String) = compose.onAllNodes(hasText(label) and !hasSetTextAction()).onFirst().performClick()

    private fun back() = compose.onNode(hasContentDescription("Back")).performClick()

    private fun search(q: String) {
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performTextClearance(); field.performTextInput(q)
    }

    private fun openProduct(name: String) {
        val item = hasText(name) and !hasSetTextAction()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(item)
        compose.onAllNodes(item).onFirst().performClick()
    }

    @Test fun tour() {
        System.setProperty("pricewatch.base", "file:///nonexistent")   // no network: show the cached day
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(app.filesDir, "data").apply { mkdirs() }
        for (n in listOf("latest.json", "changes.json", "history.json")) File("../data/$n").copyTo(File(dir, n), true)

        val vm = MainViewModel(app)
        compose.setContent { PriceTheme { App(vm) } }
        // run what background work posts to the main thread until the first load is done
        val until = System.currentTimeMillis() + 240_000
        while (vm.state.value.loading || vm.state.value.snapshot == null) {
            check(System.currentTimeMillis() < until) { "app never finished loading" }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        compose.waitForIdle()
        val comps = vm.state.value.comparisons

        shot("01_today")
        tab("Products"); shot("02_products")

        val top = comps.filter { it.offers.size >= 3 }.maxBy { it.diff ?: 0.0 }
        openProduct(Matcher.shortName(top.own.name)); shot("03_detail")
        back()

        comps.firstOrNull { it.possible.isNotEmpty() }?.let { p ->
            compose.onAllNodes(hasText("All") and !hasSetTextAction()).onFirst().performScrollTo().performClick()
            search(Matcher.shortName(p.own.name)); openProduct(Matcher.shortName(p.own.name)); shot("04_detail_possible")
            back()
        }
        comps.firstOrNull { it.offers.isEmpty() && it.possible.isEmpty() }?.let { n ->
            search(Matcher.shortName(n.own.name)); openProduct(Matcher.shortName(n.own.name)); shot("05_detail_nomatch")
            back()
        }
        tab("Market"); shot("07_market")
        search("insta360 x5"); shot("08_market_search")
        tab("Shops"); shot("09_shops")
    }

    /** The "Find at another shop" card (dialogs can't be captured here, so it's shown on its own). */
    @Test fun findDialog() {
        val snap = com.techmare.pricewatch.core.Snapshot("", "midox", listOf(
            com.techmare.pricewatch.core.Site("a", "Kamerty", "", false, true, null, null, false, listOf(
                com.techmare.pricewatch.core.Offer("a", "Kamerty", "u1", "Sony Alpha A6700 + 16-50mm f/3.5-5.6 OSS", 15990.0, null, true, null),
                com.techmare.pricewatch.core.Offer("a", "Kamerty", "u2", "Sony A6700 Boîtier nu", 13490.0, null, false, null),
            )),
        ))
        compose.setContent {
            PriceTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(24.dp)) {
                    com.techmare.pricewatch.ui.MatchSearchContent({ com.techmare.pricewatch.core.Compare.search(snap, it) }, {}, {}, initial = "a6700")
                }
            }
        }
        shot("06_find_dialog")
    }

    /** The ticker tape with sample moves (it only shows on days with price changes). */
    @Test fun tickerTape() {
        compose.mainClock.autoAdvance = false   // the tape scrolls forever
        compose.setContent {
            PriceTheme {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(vertical = 24.dp)) {
                    com.techmare.pricewatch.ui.TickerTape(listOf(
                        com.techmare.pricewatch.ui.Tick("Sony A7 IV  Kamerty 20 499", "−1.5k", true),
                        com.techmare.pricewatch.ui.Tick("DJI Mic 3  Fullpix 2 390", "+200", false),
                        com.techmare.pricewatch.ui.Tick("Canon R50  Photo Store 6 990", "−300", true),
                    )) {}
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("$out/10_ticker.png")
    }
}
