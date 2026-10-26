package com.techmare.pricewatch.core

import java.text.Normalizer

/**
 * What identifies a product regardless of how a shop words its title:
 * brand, model codes, variant words, lens range for kits, condition.
 */
data class ProductKey(
    val brand: String?,
    val models: Set<String>,
    val variants: Set<String>,
    val lens: String?,
    val body: Boolean,
    val used: Boolean,
    val words: Set<String>,
) {
    /** True when there is enough to match on (a model code or a lens/number). */
    val usable get() = models.isNotEmpty()
}

object Matcher {

    private val BRANDS = listOf(
        "canon", "nikon", "sony", "fujifilm", "panasonic", "olympus", "om system", "leica", "hasselblad", "ricoh", "pentax",
        "dji", "gopro", "insta360", "zhiyun", "feiyutech", "hohem", "hollyland", "rode", "saramonic", "boya", "deity",
        "sennheiser", "comica", "synco", "shure", "sigma", "tamron", "samyang", "viltrox", "tokina", "laowa", "ttartisan",
        "7artisans", "meike", "yongnuo", "godox", "aputure", "amaran", "nanlite", "smallrig", "manfrotto", "benro",
        "ulanzi", "neewer", "sandisk", "lexar", "prograde", "kingston", "elgato", "blackmagic", "atomos", "feelworld",
        "kodak", "instax", "polaroid", "tilta", "moza", "peak design", "lowepro", "think tank", "k&f",
    )
    private val BRAND_ALIASES = mapOf("fuji" to "fujifilm", "røde" to "rode", "om-system" to "om system", "osmo" to "dji", "ronin" to "dji", "lumix" to "panasonic")

    private val VARIANTS = setOf(
        "pro", "max", "mini", "plus", "combo", "creator", "solo", "duo", "ultra", "lite", "standard", "fly", "adventure",
        "vlog", "kit", "c", "s", "r", "ii", "evo", "air", "nano", "go", "neo", "flip", "action", "pocket",
    )
    private val COLORS = setOf(
        "noir", "black", "blanc", "white", "gris", "grey", "gray", "silver", "argent", "beige", "marron", "brown", "rose",
        "pink", "bleu", "blue", "vert", "green", "rouge", "red", "graphite", "charcoal", "titan", "titane",
    )
    private val STOP = setOf(
        "appareil", "photo", "photos", "hybride", "reflex", "camera", "cameras", "video", "numerique", "digital", "avec",
        "objectif", "lens", "pack", "neuf", "new", "maroc", "morocco", "prix", "de", "du", "des", "la", "le", "les", "pour",
        "et", "and", "with", "garantie", "an", "ans", "officiel", "original", "version", "edition", "a", "au", "en",
        "sans", "fil", "wireless", "microphone", "micro", "systeme", "system", "stabilisateur", "gimbal", "cardan",
        "kit", "eos", "alpha", "the", "of", "f", "is", "stm", "usm", "oss", "dg", "dn", "di", "iii-a", "rf", "ef", "fe",
        "rfs", "efs", "z", "nikkor", "lumix", "zuiko", "series", "serie",
    )
    private val USED = Regex("\\b(occasion|occas|utilise|used|seconde main|reconditionne|refurb\\w*)\\b")
    private val BODY = Regex("\\b(boitier|body|nu|seul|only)\\b")
    private val LENS_RANGE = Regex("(\\d{1,3})\\s*-\\s*(\\d{1,3})\\s*(mm)?")

    fun normalize(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        t = t.replace('α', 'a').replace('×', 'x').replace("ø", "o")
        return t
    }

    fun key(name: String): ProductKey {
        var s = normalize(name)
        val used = USED.containsMatchIn(s)
        val body = BODY.containsMatchIn(s)

        // brand
        var brand: String? = null
        for ((alias, real) in BRAND_ALIASES) if (Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(s)) { brand = real; break }
        for (b in BRANDS) if (Regex("(^|[^a-z])${Regex.escape(b)}([^a-z]|$)").containsMatchIn(s)) { brand = b; break }

        // lens range before tokenising
        var lens: String? = null
        LENS_RANGE.find(s)?.let { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (b > a && b >= 10) { lens = "$a-$b"; s = s.replace(m.value, " ") }
        }

        // Sony codes: ilce-7m4 -> a7 mk4, ilce-6400 -> a6400
        s = s.replace(Regex("ilce-?(\\d)([a-z]*)m(\\d)\\b")) { "a${it.groupValues[1]}${it.groupValues[2]} mk${it.groupValues[3]}" }
        s = s.replace(Regex("ilce-?(\\d+[a-z]*)")) { "a${it.groupValues[1]}" }
        s = s.replace(Regex("\\balpha\\s*(\\d)")) { "a${it.groupValues[1]}" }
        // "a7 iv", "a7iv", "a7m4", "a7r v"
        s = s.replace(Regex("\\b(a\\d[a-z]?)\\s*(m)?(iv|iii|ii|vi|v|i)\\b")) { "${it.groupValues[1]} ${roman(it.groupValues[3])}" }
        s = s.replace(Regex("\\b(a\\d[a-z]?)m(\\d)\\b")) { "${it.groupValues[1]} mk${it.groupValues[2]}" }
        // "mark iv", "mk ii", "mkii", "mk2", "mark 2"
        s = s.replace(Regex("\\b(mark|mk)\\s*(iv|iii|ii|vi|v|i)\\b")) { roman(it.groupValues[2]) }
        s = s.replace(Regex("\\b(mark|mk)\\s*(\\d)\\b")) { "mk${it.groupValues[2]}" }
        // join letter-dash-digit codes: x-t50 -> xt50, zv-e10 -> zve10, rf-s -> rfs
        s = s.replace(Regex("\\b([a-z]{1,3})-([a-z]?\\d+[a-z]*)\\b")) { it.groupValues[1] + it.groupValues[2] }
        s = s.replace(Regex("\\b([a-z]{1,3})-([a-z]{1,2})\\b")) { it.groupValues[1] + it.groupValues[2] }
        // apertures: "f/2.8", "F4.5-6.3" -> "f2.8", "f4.5" (widest only)
        s = s.replace(Regex("(\\d),(\\d)")) { "${it.groupValues[1]}.${it.groupValues[2]}" }
        s = s.replace(Regex("\\bf\\s*/?\\s*(\\d+(\\.\\d+)?)(\\s*-\\s*\\d+(\\.\\d+)?)?")) { " f${it.groupValues[1]} " }

        val tokens = s.split(Regex("[^a-z0-9.]+")).map { it.trim('.') }.filter { it.isNotBlank() }
        val brandWords = brand?.split(" ")?.toSet() ?: emptySet()
        val models = LinkedHashSet<String>()
        val variants = LinkedHashSet<String>()
        val words = LinkedHashSet<String>()
        val apertures = LinkedHashSet<String>()
        for ((i, tk) in tokens.withIndex()) {
            if (tk in brandWords || tk in COLORS || tk in STOP) continue
            if (BRAND_ALIASES.containsKey(tk) || tk in BRANDS) continue
            val hasDigit = tk.any { it.isDigit() }
            val hasLetter = tk.any { it.isLetter() }
            when {
                tk.matches(Regex("mk\\d")) -> models += tk
                tk.matches(Regex("\\d+mm")) -> if (lens == null) lens = tk else words += tk
                tk.matches(Regex("f\\d+(\\.\\d+)?")) -> apertures += tk
                tk.matches(Regex("\\d+k")) -> words += tk
                tk.matches(Regex("\\d+(gb|go|tb|to|mah|w|k|p|fps|m)")) -> words += tk
                hasDigit && hasLetter -> models += tk
                hasDigit -> models += tk // "pocket 3", "mini 4"
                tk in VARIANTS -> variants += tk
                tk.length > 1 -> words += tk
            }
            @Suppress("UNUSED_VARIABLE") val unused = i
        }
        // a prime lens is defined by its aperture; for zooms and kits shops often leave it out
        if (lens?.contains('-') == true) words += apertures else models += apertures
        return ProductKey(brand, models, variants, lens, body, used, words)
    }

    private fun roman(r: String) = "mk" + when (r) { "i" -> 1; "ii" -> 2; "iii" -> 3; "iv" -> 4; "v" -> 5; "vi" -> 6; else -> 0 }

    /** Similarity 0..1, or null if the two keys can't be the same product. */
    fun score(a: ProductKey, b: ProductKey): Double? {
        if (!a.usable || !b.usable) return null
        if (a.brand != null && b.brand != null && a.brand != b.brand) return null
        if (a.used != b.used) return null
        if (a.models != b.models) return null
        if (a.lens != b.lens) return null
        val va = a.variants - setOf("kit"); val vb = b.variants - setOf("kit")
        if (va != vb) return null
        val union = (a.words + b.words).size
        val jac = if (union == 0) 1.0 else (a.words intersect b.words).size.toDouble() / union
        return 0.6 + 0.4 * jac
    }
}
