package com.techmare.pricewatch.core

data class Offer(
    val siteId: String,
    val siteName: String,
    val url: String,
    val name: String,
    val price: Double,
    val regular: Double?,
    val inStock: Boolean,
    val image: String?,
)

data class Site(
    val id: String,
    val name: String,
    val url: String,
    val own: Boolean,
    val ok: Boolean,
    val error: String?,
    val checked: String?,
    val stale: Boolean,
    val products: List<Offer>,
)

data class Snapshot(val generated: String, val ownId: String, val sites: List<Site>) {
    val own: Site? get() = sites.firstOrNull { it.id == ownId }
    val competitors: List<Site> get() = sites.filter { it.id != ownId }
}

data class Change(val siteId: String, val url: String, val name: String, val type: String, val old: Double?, val new: Double?)

data class PricePoint(val date: String, val price: Double, val inStock: Boolean)

/** Manual decisions the user made about matches. Keys are own product URLs. */
data class Overrides(
    val confirmed: Map<String, Set<String>> = emptyMap(),
    val rejected: Map<String, Set<String>> = emptyMap(),
)

enum class Position {
    CHEAPER_ELSEWHERE, SAME, CHEAPEST,
    /** Other shops list it, but none has it in stock right now. */
    OUT_OF_STOCK_ELSEWHERE,
    NO_MATCH,
}

data class Comparison(
    val own: Offer,
    /** Matching competitor offers, cheapest first, one per shop. */
    val offers: List<Offer>,
    val position: Position,
    /** Your price minus the cheapest in-stock competitor (positive = you're more expensive). */
    val diff: Double?,
    val autoMatched: Set<String>,
    /** Same model code but a price too far from yours to trust: shown for you to confirm, not counted. */
    val possible: List<Offer> = emptyList(),
) {
    /** Cheapest shop that has it in stock. */
    val best: Offer? get() = offers.firstOrNull { it.inStock }
}

object Compare {

    fun run(
        snapshot: Snapshot,
        overrides: Overrides = Overrides(),
        excludedSites: Set<String> = emptySet(),
        threshold: Double = 20.0,
        includeUsed: Boolean = false,
        /** An automatic match must cost between half and double your price; others become "possible". */
        minRatio: Double = 0.5,
        maxRatio: Double = 2.0,
    ): List<Comparison> {
        val own = snapshot.own ?: return emptyList()
        val sites = snapshot.competitors.filter { it.id !in excludedSites }
        val byUrl = HashMap<String, Offer>()
        // index competitor offers by their model codes: a match needs identical codes
        val index = HashMap<Set<String>, MutableList<Pair<Offer, ProductKey>>>()
        for (s in sites) for (o in s.products) {
            byUrl[o.url] = o
            val k = Matcher.key(o.name)
            if (k.usable) index.getOrPut(k.indexKey) { ArrayList() } += o to k
        }

        return own.products.mapNotNull { mine ->
            val mk = Matcher.key(mine.name)
            if (mk.used && !includeUsed) return@mapNotNull null
            val rejected = overrides.rejected[mine.url].orEmpty()
            val confirmed = overrides.confirmed[mine.url].orEmpty()

            val perSite = HashMap<String, Pair<Offer, Double>>()
            val possiblePerSite = HashMap<String, Offer>()
            val auto = HashSet<String>()
            for ((o, k) in index[mk.indexKey].orEmpty()) {
                if (o.url in rejected || o.url in confirmed) continue
                val sc = Matcher.score(mk, k) ?: continue
                val ratio = o.price / mine.price
                if (ratio < minRatio || ratio > maxRatio) {
                    val cur = possiblePerSite[o.siteId]
                    if (cur == null || kotlin.math.abs(o.price - mine.price) < kotlin.math.abs(cur.price - mine.price)) possiblePerSite[o.siteId] = o
                    continue
                }
                val cur = perSite[o.siteId]
                if (cur == null || sc > cur.second || (sc == cur.second && o.price < cur.first.price)) {
                    perSite[o.siteId] = o to sc
                }
            }
            perSite.values.forEach { auto += it.first.url }
            // confirmed matches always win for their shop
            for (url in confirmed) {
                val o = byUrl[url] ?: continue
                perSite[o.siteId] = o to 2.0
            }
            val offers = perSite.values.map { it.first }.sortedWith(compareBy({ !it.inStock }, { it.price }))
            val possible = possiblePerSite.filterKeys { it !in perSite }.values.sortedBy { it.price }
            // only shops that can sell it today decide where you stand
            val best = offers.firstOrNull { it.inStock }
            val diff = best?.let { mine.price - it.price }
            val pos = when {
                offers.isEmpty() -> Position.NO_MATCH
                best == null -> Position.OUT_OF_STOCK_ELSEWHERE
                diff!! > threshold -> Position.CHEAPER_ELSEWHERE
                diff < -threshold -> Position.CHEAPEST
                else -> Position.SAME
            }
            Comparison(mine, offers, pos, diff, auto - confirmed, possible)
        }
    }

    /** Every competitor product, for manual matching. */
    fun search(snapshot: Snapshot, query: String, limit: Int = 60): List<Offer> {
        val q = Matcher.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (q.isEmpty()) return emptyList()
        return snapshot.competitors.asSequence().flatMap { it.products.asSequence() }
            .filter { o -> val n = Matcher.normalize(o.name); q.all { n.contains(it) } }
            .take(limit).toList()
    }
}
