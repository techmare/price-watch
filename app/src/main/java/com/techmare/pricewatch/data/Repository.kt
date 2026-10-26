package com.techmare.pricewatch.data

import android.content.Context
import com.techmare.pricewatch.core.Change
import com.techmare.pricewatch.core.Offer
import com.techmare.pricewatch.core.Overrides
import com.techmare.pricewatch.core.PricePoint
import com.techmare.pricewatch.core.Site
import com.techmare.pricewatch.core.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the daily results from GitHub and caches them on the phone. */
class Repository(private val context: Context) {

    private val dir = File(context.filesDir, "data").apply { mkdirs() }

    enum class Fetch { OK, NOT_READY, OFFLINE }

    /** Fetches fresh files; cached files stay usable when this fails. */
    fun refresh(): Fetch {
        var result = Fetch.OK
        for (name in listOf("latest.json", "changes.json", "history.json")) {
            val (code, bytes) = download("$BASE/$name")
            if (bytes == null) {
                if (result == Fetch.OK) result = if (code == 404) Fetch.NOT_READY else Fetch.OFFLINE
                continue
            }
            File(dir, "$name.tmp").apply { writeBytes(bytes); renameTo(File(dir, name)) }
        }
        historyCache = null
        return result
    }

    fun hasCache() = File(dir, "latest.json").exists()

    private fun download(url: String): Pair<Int, ByteArray?> = try {
        val c = URL("$url?t=${System.currentTimeMillis() / 300_000}").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("Accept-Encoding", "gzip")
        try {
            val code = c.responseCode
            if (code != 200) code to null else {
                val stream = if ("gzip".equals(c.contentEncoding, true)) java.util.zip.GZIPInputStream(c.inputStream) else c.inputStream
                code to stream.use { it.readBytes() }
            }
        } finally { c.disconnect() }
    } catch (e: Exception) { 0 to null }

    private fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    fun snapshot(): Snapshot? {
        val o = JSONObject(read("latest.json") ?: return null)
        val sites = o.optJSONObject("sites") ?: return null
        val list = ArrayList<Site>()
        for (id in sites.keys()) {
            val s = sites.getJSONObject(id)
            val name = s.optString("name", id)
            val prods = s.optJSONArray("products") ?: JSONArray()
            val offers = ArrayList<Offer>(prods.length())
            for (i in 0 until prods.length()) {
                val p = prods.getJSONObject(i)
                val price = p.optDouble("price", 0.0)
                if (price <= 0) continue
                offers += Offer(
                    siteId = id, siteName = name, url = p.optString("url"), name = p.optString("name"),
                    price = price, regular = p.optDouble("regular").takeIf { !it.isNaN() && it > 0 },
                    inStock = p.optBoolean("inStock", true), image = p.optString("image").ifBlank { null }?.takeIf { it != "null" },
                )
            }
            list += Site(
                id = id, name = name, url = s.optString("url"), own = s.optBoolean("own"), ok = s.optBoolean("ok"),
                error = s.optString("error").takeIf { it.isNotBlank() && it != "null" }, checked = s.optString("checked").ifBlank { null },
                stale = s.optBoolean("stale"), products = offers,
            )
        }
        return Snapshot(o.optString("generated"), o.optString("own", "midox"), list.sortedBy { it.name.lowercase() })
    }

    fun changes(): List<Change> {
        val o = JSONObject(read("changes.json") ?: return emptyList())
        val arr = o.optJSONArray("changes") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            Change(
                c.optString("site"), c.optString("url"), c.optString("name"), c.optString("type"),
                c.optDouble("old").takeIf { !it.isNaN() }, c.optDouble("new").takeIf { !it.isNaN() },
            )
        }
    }

    /** Price history for one product page. */
    @Volatile private var historyCache: JSONObject? = null

    fun history(siteId: String, url: String): List<PricePoint> {
        val root = historyCache ?: (read("history.json")?.let { try { JSONObject(it) } catch (e: Exception) { null } } ?: return emptyList())
            .also { historyCache = it }
        val arr = root.optJSONObject(siteId)?.optJSONArray(url) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val pt = arr.getJSONArray(i)
            PricePoint(pt.getString(0), pt.getDouble(1), pt.optInt(2, 1) == 1)
        }
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/techmare/price-watch/main/data"
    }
}

/** User choices: confirmed/rejected matches, hidden shops, threshold. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var threshold: Int
        get() = sp.getInt("threshold", 20)
        set(v) = sp.edit().putInt("threshold", v).apply()

    var notify: Boolean
        get() = sp.getBoolean("notify", true)
        set(v) = sp.edit().putBoolean("notify", v).apply()

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    var overrides: Overrides
        get() = Overrides(readMap("confirmed"), readMap("rejected"))
        set(v) { writeMap("confirmed", v.confirmed); writeMap("rejected", v.rejected) }

    var lastNotified: String
        get() = sp.getString("lastNotified", "")!!
        set(v) = sp.edit().putString("lastNotified", v).apply()

    private fun readMap(key: String): Map<String, Set<String>> = try {
        val o = JSONObject(sp.getString(key, "{}")!!)
        o.keys().asSequence().associateWith { k -> val a = o.getJSONArray(k); (0 until a.length()).map { a.getString(it) }.toSet() }
    } catch (e: Exception) { emptyMap() }

    private fun writeMap(key: String, m: Map<String, Set<String>>) {
        val o = JSONObject()
        m.filterValues { it.isNotEmpty() }.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) }
        sp.edit().putString(key, o.toString()).apply()
    }
}
