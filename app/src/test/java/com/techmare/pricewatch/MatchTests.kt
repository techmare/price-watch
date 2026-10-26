package com.techmare.pricewatch

import com.techmare.pricewatch.core.Compare
import com.techmare.pricewatch.core.Matcher
import com.techmare.pricewatch.core.Offer
import com.techmare.pricewatch.core.Overrides
import com.techmare.pricewatch.core.Position
import com.techmare.pricewatch.core.Site
import com.techmare.pricewatch.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MatchTests {
    private fun same(a: String, b: String) = assertNotNull("$a | $b", Matcher.score(Matcher.key(a), Matcher.key(b)))
    private fun diff(a: String, b: String) = assertNull("$a | $b", Matcher.score(Matcher.key(a), Matcher.key(b)))

    @Test fun sameProducts() {
        same("Canon EOS R50 + RF-S 18-45mm F4.5-6.3 IS STM", "Appareil photo hybride Canon EOS R50 Kit 18-45mm")
        same("SONY ALPHA A7 IV", "Sony A7 Mark IV Boîtier")
        same("Sony ILCE-7M4 boitier nu", "SONY ALPHA A7 IV")
        same("DJI Osmo Pocket 3", "DJI Pocket 3 Caméra 4K stabilisée")
        same("Fujifilm X-T50 Charcoal", "FUJIFILM XT50 noir")
        same("Sigma 30mm F1.4 DC DN Sony E", "Sigma 30mm f/1.4 DC DN Contemporary (Sony E)")
        same("Canon EOS 5D Mark IV", "CANON 5D MK4 boîtier")
    }

    @Test fun differentProducts() {
        diff("Canon EOS R50 boîtier", "Canon EOS R50 + 18-45mm")
        diff("DJI Osmo Pocket 3", "DJI Osmo Pocket 3 Creator Combo")
        diff("Sony A7 III", "Sony A7 IV")
        diff("Hollyland Lark M2", "Hollyland Lark M2S")
        diff("Sigma 30mm F1.4", "Sigma 30mm F2.8")
        diff("Sony A7 IV occasion", "Sony A7 IV")
        diff("Canon EOS R6 Mark II", "Canon EOS R6")
    }

    private fun offer(site: String, name: String, price: Double, stock: Boolean = true) =
        Offer(site, site, "https://$site/${name.hashCode()}", name, price, null, stock, null)

    @Test fun comparison() {
        val mine = offer("midox", "DJI Osmo Pocket 3", 5600.0)
        val snap = Snapshot("now", "midox", listOf(
            Site("midox", "Midox", "", true, true, null, null, false, listOf(mine)),
            Site("a", "A", "", false, true, null, null, false, listOf(offer("a", "DJI Pocket 3", 5399.0), offer("a", "DJI Osmo Pocket 3 Creator Combo", 6500.0))),
            Site("b", "B", "", false, true, null, null, false, listOf(offer("b", "Osmo Pocket 3", 4999.0, stock = false))),
        ))
        val r = Compare.run(snap).single()
        assertEquals(2, r.offers.size)
        assertEquals("a", r.best!!.siteId)          // out-of-stock B is not "best"
        assertEquals(Position.CHEAPER_ELSEWHERE, r.position)
        assertEquals(201.0, r.diff!!, 0.01)

        val rejected = Compare.run(snap, Overrides(rejected = mapOf(mine.url to setOf(r.best!!.url)))).single()
        assertEquals(1, rejected.offers.size)
    }
}
