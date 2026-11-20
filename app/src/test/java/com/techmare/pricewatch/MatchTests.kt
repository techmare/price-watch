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
        same("GoPro HERO 13 Black – Caméra d'action 5.3K | Disponible au Maroc", "GoPro HERO13 Black")
        same("Godox AD200 Pro II – Flash portable 200Ws TTL & HSS | Midox Shop Maroc", "Godox AD200Pro II TTL Flash 200Ws")
        same("DJI RS4 Mini", "DJI RS 4 Mini – Stabilisateur Camera Professionnel")
        same("RODE Wireless GO II", "RODE Wireless Go II")
        same("RODE PodMic", "Rode PodMic – Microphone dynamique de podcast")
        same("Nikon ZR – Appareil photo hybride Nikon | Midox Shop Maroc", "Nikon ZR 6K Cinema Camera")
        same("Sony FE 24-70mm F/2.8 GM - Neuf", "SONY FE 24-70mm f/2.8 GM (SEL2470GM)")
        same("Godox V1 Pro (C,N,S)", "Godox V1 Pro Flash Canon")
        same("Godox X2T (C,N,S)", "Godox X2T-S – Déclencheur de Flash TTL")
        same("Godox SL60 II D", "Godox SL60IID – LED (Daylight)")
        same("Sandisk extrème pro micro SD 128go 200 mb/s", "SanDisk MicroSDXC Extreme Pro 128Go")
        same("Batterie Canon LP-E17", "Batterie LP-E17 Pour Appareil Photo Canon EOS")
        same("DJI Mic 3 (2 émetteurs + 1 récepteur)", "DJI MIC 3 (2 TX + 1 RX + BOITIER DE RECHARGE)")
        same("Insta360 X5", "Insta360 X5 – 8K 360")
    }

    @Test fun accessoriesAreNotTheProduct() {
        diff("Insta360 X5", "Insta360 capuchon d'objectif X5")
        diff("Insta360 X5", "Insta360 X5 Mic Adapter")
        diff("Insta360 X5", "INSTA360 X5 ETUI DE TRANSPORT CINSBAMC")
        diff("Sony Alpha A7S III - Neuf", "Nitze Cage Pour SONY a7S III")
        diff("GoPro HERO13 Black", "Chargeur double + 2 batteries Enduro HERO13 Black")
        diff("Insta360 capuchon d'objectif X5", "INSTA360 X5 ETUI DE TRANSPORT")   // two different accessories
        same("Batterie sony np-fw50", "Batterie SONY NP-FW50")                 // the same accessory still matches
        diff("Insta360 X5", "Insta360 X5 caméra 360 8K – Pack Essentiel")      // bundle
        diff("Nikon ZR", "NIKON ZR + SMALLRIG CAGE PROTEGE 5647 + SANDISK 256GB")
    }

    @Test fun differentProducts() {
        diff("Canon EOS R50 boîtier", "Canon EOS R50 + 18-45mm")
        diff("DJI Osmo Pocket 3", "DJI Osmo Pocket 3 Creator Combo")
        diff("Sony A7 III", "Sony A7 IV")
        diff("Hollyland Lark M2", "Hollyland Lark M2S")
        diff("Sigma 30mm F1.4", "Sigma 30mm F2.8")
        diff("Sony A7 IV occasion", "Sony A7 IV")
        diff("Canon EOS R6 Mark II", "Canon EOS R6")
        diff("GoPro HERO 11 Black", "Magic Arm 11″")
        diff("DJI RSC 2", "IMOU Cue 2 Maroc")
        diff("Support mural de 4", "Sony Playstation 4 Blanche")
        diff("RODE VideoMic GO II", "Rode Wireless GO II")
        diff("Canon RF 50mm f/1.8 STM", "Canon EF 50mm f/1.8 STM")
        diff("Sony FE 24-70mm f/4 ZA OSS", "Sony FE 24-70mm F/2.8 GM")
        diff("DJI Mic 3 (2 émetteurs + 1 récepteur)", "DJI MIC 3 (1 TX + 1 RX)")
        diff("Sandisk extrème pro Micro SD 256go", "SanDisk Extreme PRO SDXC 256GB")
        diff("SanDisk Ultra 128Go 140 Mo/s", "Sandisk Ultra Dual 128 Go - Next Level PC Maroc")
        diff("Canon EOS R6", "Canon EOS R6 V – Caméra Hybride Full Frame 7K")
        diff("RODE NT-USB+", "Rode NT-USB")
        diff("DJI Mic Mini", "DJI Mic Mini 2 (1 RX mobile + 2 TX)")
    }

    @Test fun farPriceIsOnlyPossible() {
        val mine = offer("midox", "Canon EOS R5 Body", 21500.0)
        val snap = Snapshot("now", "midox", listOf(
            Site("midox", "Midox", "", true, true, null, null, false, listOf(mine)),
            Site("a", "A", "", false, true, null, null, false, listOf(offer("a", "CANON EOS R5 – BOÎTIER NU", 45000.0))),
        ))
        val r = Compare.run(snap).single()
        assertEquals(0, r.offers.size)
        assertEquals(1, r.possible.size)
        assertEquals(Position.NO_MATCH, r.position)
        // once confirmed it counts
        val ok = Compare.run(snap, Overrides(confirmed = mapOf(mine.url to setOf(r.possible[0].url)))).single()
        assertEquals(1, ok.offers.size)
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
