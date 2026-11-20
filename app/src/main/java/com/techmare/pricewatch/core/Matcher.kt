package com.techmare.pricewatch.core

import java.text.Normalizer

/**
 * What identifies a product regardless of how a shop words its title:
 * brand, model codes, variant words, lens range and aperture, condition,
 * and whether it is an accessory (a case, a battery, a cage...) rather than the product itself.
 */
data class ProductKey(
    val brand: String?,
    val models: Set<String>,
    val variants: Set<String>,
    val lens: String?,
    val aperture: String?,
    /** Lens mount when the title says it ("rf", "ef", "rfs"...): an RF 50mm isn't an EF 50mm. */
    val mount: String?,
    /** Number of transmitters for wireless mics ("2 TX + 1 RX", "Dual"). */
    val tx: Int?,
    val body: Boolean,
    val used: Boolean,
    /** Accessory types found in the title ("case", "battery"...). Empty for the product itself. */
    val accessory: Set<String>,
    /** Sold with extra items ("+ SmallRig cage + 256GB card"). */
    val bundle: Boolean,
    val words: Set<String>,
) {
    /** Enough to match on: a model code, or a lens range. */
    val usable get() = models.isNotEmpty() || lens != null

    /** Competitor offers are indexed by this; a match needs the same value. */
    val indexKey: Set<String> get() = if (lens != null) models + "lens:$lens" else models

    /** At least one code with letters and digits (e.g. "x5", "a6700"), not just a number. */
    val strongCode get() = models.any { m -> m.length >= 2 && m.any { it.isLetter() } && m.any { it.isDigit() } }
}

object Matcher {

    private val BRANDS = listOf(
        "canon", "nikon", "sony", "fujifilm", "panasonic", "olympus", "om system", "leica", "hasselblad", "ricoh", "pentax",
        "dji", "gopro", "insta360", "zhiyun", "feiyutech", "hohem", "hollyland", "rode", "saramonic", "boya", "deity",
        "sennheiser", "comica", "synco", "shure", "sigma", "tamron", "samyang", "viltrox", "tokina", "laowa", "ttartisan",
        "7artisans", "meike", "yongnuo", "godox", "aputure", "amaran", "nanlite", "smallrig", "manfrotto", "benro",
        "ulanzi", "neewer", "sandisk", "lexar", "prograde", "kingston", "elgato", "blackmagic", "atomos", "feelworld",
        "kodak", "instax", "polaroid", "tilta", "moza", "peak design", "lowepro", "think tank", "k&f", "pixel", "jmary",
        "weifeng", "nitze", "imou", "xiaomi", "jbl", "goui", "apple", "samsung", "dison", "sirui", "nisi", "pgytech",
        "telesin", "freewell", "falcon eyes", "jupio", "patona", "baseus", "anker", "logitech", "corsair", "nintendo",
        "playstation", "fifine", "maono", "zoom", "tascam", "yeti", "hyperx", "razer", "ugreen", "orico",
    )
    private val BRAND_ALIASES = mapOf(
        "fuji" to "fujifilm", "røde" to "rode", "om-system" to "om system", "osmo" to "dji", "ronin" to "dji",
        "lumix" to "panasonic", "insta 360" to "insta360", "godoxx" to "godox", "godo" to "godox", "amaran" to "aputure",
    )

    private val VARIANTS = setOf(
        "pro", "max", "mini", "plus", "combo", "creator", "ultra", "lite", "standard", "fly", "adventure",
        "vlog", "kit", "c", "s", "r", "evo", "air", "nano", "go", "neo", "flip", "pocket", "usb", "single",
        "gm", "art", "microsd", "mid", "bundle", "zeiss", "za", "cine", "rgb", "bi", "bicolor", "daylight", "se", "ultimate",
    )
    private val COLORS = setOf(
        "noir", "black", "blanc", "white", "gris", "grey", "gray", "silver", "argent", "beige", "marron", "brown", "rose",
        "pink", "bleu", "blue", "vert", "green", "rouge", "red", "graphite", "charcoal", "titan", "titane", "arctic",
    )
    private val STOP = setOf(
        "appareil", "photo", "photos", "hybride", "reflex", "camera", "cameras", "video", "numerique", "digital", "avec",
        "objectif", "lens", "pack", "neuf", "new", "maroc", "morocco", "prix", "de", "du", "des", "la", "le", "les", "pour",
        "et", "and", "with", "garantie", "an", "ans", "officiel", "original", "version", "edition", "a", "au", "en",
        "sans", "fil", "microphone", "micro", "systeme", "system", "stabilisateur", "gimbal", "cardan",
        "kit", "eos", "alpha", "the", "of", "f", "is", "stm", "usm", "oss", "dg", "dn", "di", "iii-a", "rf", "ef", "fe",
        "rfs", "efs", "z", "nikkor", "lumix", "zuiko", "series", "serie", "360", "disponible", "midox", "shop", "store",
        "next", "level", "pc", "monture", "mount", "compatible", "ttl", "hss", "led", "lampe", "lampes", "flash",
        "eclairage", "portable", "sdxc", "sdhc", "sd", "uhs", "uhs-i", "carte", "memoire", "professionnel", "professionnelle", "compact", "dynamique", "x", "for", "sur",
    )

    /** Words that make a title an accessory, grouped by type: a lens cap and a case for the same camera aren't the same. */
    private val ACCESSORY = mapOf(
        "cap" to listOf("capuchon", "bouchon", "cache objectif", "lens cap", "body cap"),
        "case" to listOf("etui", "housse", "sacoche", "sac", "bag", "case", "valise", "coque", "pochette", "backpack", "sac a dos"),
        "adapter" to listOf("adaptateur", "adapter", "adaptor", "bague"),
        "battery" to listOf("batterie", "batteries", "battery", "accu"),
        "charger" to listOf("chargeur", "charger", "station de charge", "charging hub"),
        "cable" to listOf("cable", "cordon"),
        "cage" to listOf("cage"),
        "mount" to listOf("support", "fixation", "clamp", "pince", "bras", "magic arm", "plateau", "plaque", "quick release", "tete"),
        "filter" to listOf("filtre", "filter", "pop filter", "bonnette", "windscreen", "dead cat", "anti vent"),
        "protect" to listOf("protection", "protecteur", "protector", "film", "verre trempe", "screen protector", "skin"),
        "pole" to listOf("perche", "selfie stick", "baton"),
        "tripod" to listOf("trepied", "tripod", "monopod", "monopode"),
        "remote" to listOf("telecommande", "remote"),
        "hood" to listOf("pare soleil", "pare-soleil", "lens hood"),
        "grip" to listOf("grip", "poignee"),
        "strap" to listOf("dragonne", "strap", "sangle", "courroie"),
        "card" to listOf("carte memoire", "carte sd", "memory card"),
        "drive" to listOf("cle usb", "dual drive", "flash drive", "ultra dual", "ultra flair", "usb drive"),
    )
    private val ACCESSORY_RX = ACCESSORY.mapValues { (_, words) ->
        Regex("(^|[^a-z0-9])(" + words.joinToString("|") { Regex.escape(it) } + ")([^a-z0-9]|$)")
    }

    private val USED = Regex("\\b(occasion|occas|utilise|used|seconde main|reconditionne|refurb\\w*)\\b")
    private val BODY = Regex("\\b(boitier|body|nu|seul|only)\\b")
    private val LENS_RANGE = Regex("(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})\\s*(mm)?")
    private val ROMAN = mapOf("ii" to 2, "iii" to 3, "iv" to 4, "vi" to 6)

    fun normalize(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        t = t.replace('α', 'a').replace('×', 'x').replace("ø", "o").replace('‑', '-').replace('’', '\'')
        return t
    }

    /** A product title without the shop's SEO tail: "Sony A7 V – Appareil photo … | Midox Shop Maroc" -> "Sony A7 V – Appareil photo …". */
    fun displayName(name: String): String = name
        .substringBefore(" | ")
        .replace(Regex("\\s+[-–]\\s+(neuf|new)\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()

    fun key(name: String): ProductKey {
        var s = normalize(displayName(name))
        val used = USED.containsMatchIn(s)
        val body = BODY.containsMatchIn(s)

        // "NT-USB+", "VideoMic Pro+": a glued plus is part of the name
        s = s.replace(Regex("(?<=[a-z0-9])\\+(?=\\s|$)"), " plus")
        val accessoryEarly = ACCESSORY_RX.filterKeys { it == "drive" }.filterValues { it.containsMatchIn(s) }.keys
        // transmitters before the parentheses go: "(2 TX + 1 RX)", "2 émetteurs", "Dual"
        val tx = Regex("\\b(\\d)\\s*(tx|emetteurs?|transmitters?)\\b").find(s)?.groupValues?.get(1)?.toInt()
            ?: when { Regex("\\b(dual|duo)\\b").containsMatchIn(s) -> 2; Regex("\\b(single|solo)\\b").containsMatchIn(s) -> 1; else -> null }
        s = s.replace(Regex("\\b(dual|duo|single|solo)\\b"), " ")
        // "Essentials Kit", "Pack Essentiel", "Bundle": sold with extras
        s = s.replace(Regex("\\b(essentials?|essentiel|bundle|pack complet)\\b"), " bundle ")
        // lens mount, read before "rf"/"ef" are dropped as noise
        val mount = Regex("\\b(rf-?s|ef-?s|ef-?m|rf|ef)(?=[\\s\\d]|$)").find(s)?.groupValues?.get(1)?.replace("-", "")

        // parentheses hold mounts "(C,N,S)", SKUs "(SEL2470GM)", contents "(2TX+1RX)": not part of the identity
        s = s.replace(Regex("\\([^)]*\\)"), " ")

        s = s.replace(Regex("\\bmicro\\s*-?\\s*sd(xc|hc)?\\b"), " microsd ")
        // mount suffixes on codes: "x2t-s", "v1-c", "ad200 for sony" are the same product for each system
        s = s.replace(Regex("\\b([a-z]+\\d+[a-z]*)-(c|n|s|f|o|sony|canon|nikon|fuji)\\b")) { it.groupValues[1] }

        // what's sold with it: "Nikon ZR + SmallRig cage" is a bundle, "Canon R50 + 18-45mm" is a kit
        val plusParts = s.split(Regex("\\s\\+\\s|\\s\\+|\\+\\s"))
        val bundle = plusParts.drop(1).any { !LENS_RANGE.containsMatchIn(it) && !Regex("^\\s*\\d+\\s*mm").containsMatchIn(it) }
        // accessory words count only in the main part, not in "... avec sac" or "+ batterie"
        val head = plusParts.first().split(Regex("\\b(avec|with|inclus|incl)\\b")).first()
        val accessory = ACCESSORY_RX.filterValues { it.containsMatchIn(head) }.keys + accessoryEarly

        // brand: whichever known brand comes first in the title
        val brand = (BRANDS.map { it to it } + BRAND_ALIASES.toList())
            .mapNotNull { (word, real) -> Regex("(^|[^a-z0-9])${Regex.escape(word)}([^a-z0-9]|$)").find(s)?.let { it.range.first to real } }
            .minByOrNull { it.first }?.second

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
        // roman numeral glued to a code: "sl60iid" -> "sl60 mk2 d", "ad200pro ii" handled below
        s = s.replace(Regex("\\b([a-z]+\\d+)(iv|iii|ii)([a-z]{0,2})\\b")) { "${it.groupValues[1]} ${roman(it.groupValues[2])} ${it.groupValues[3]}" }
        // a variant glued to a code: "ad200pro" -> "ad200 pro", "sl60bi" -> "sl60 bi"
        s = s.replace(Regex("\\b([a-z]+\\d+)(pro|max|mini|plus|ultra|lite|bi|d)\\b")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        // a standalone roman numeral is a generation: "go ii", "gm ii", "pro ii", "dg dn ii"
        // after a code it's a mark ("s5 ii" -> "s5 mk2"), after a word it's a number ("go ii" -> "go 2", joined below)
        s = s.replace(Regex("\\b([a-z][a-z0-9]*\\d[a-z0-9]*)\\s+(iv|iii|ii|vi|v)\\b")) { it.groupValues[1] + " " + roman(it.groupValues[2]) }
        s = s.replace(Regex("(?<=[a-z])\\s+(iv|iii|ii|vi)\\b")) { " " + ROMAN.getValue(it.groupValues[1]) }
        // join letter-dash-digit codes: x-t50 -> xt50, zv-e10 -> zve10, np-fw50 -> npfw50
        s = s.replace(Regex("\\b([a-z]{1,3})-([a-z]{0,3}\\d+[a-z]*)\\b")) { it.groupValues[1] + it.groupValues[2] }
        s = s.replace(Regex("\\b([a-z]{1,3})-([a-z]{1,2})\\b")) { it.groupValues[1] + it.groupValues[2] }
        // apertures: "f/2.8", "F4.5-6.3" -> "f2.8" (widest only)
        s = s.replace(Regex("(\\d),(\\d)")) { "${it.groupValues[1]}.${it.groupValues[2]}" }
        var aperture: String? = null
        s = s.replace(Regex("\\bf\\s*/?\\s*(\\d+(\\.\\d+)?)(\\s*-\\s*\\d+(\\.\\d+)?)?")) {
            if (aperture == null) aperture = "f" + it.groupValues[1].removeSuffix(".0")
            " "
        }
        // speeds aren't identity ("200 mb/s"), capacities are ("128 go" -> 128gb)
        s = s.replace(Regex("\\d+\\s*(mb|mo)\\s*/\\s*s"), " ")
        s = s.replace(Regex("\\b(\\d+)\\s*(go|gb)\\b")) { "${it.groupValues[1]}gb" }
        s = s.replace(Regex("\\b(\\d+)\\s*(to|tb)\\b")) { "${it.groupValues[1]}tb" }
        // dimensions "80*120cm", "2x3m" -> one token
        s = s.replace(Regex("\\b(\\d+)\\s*[x*]\\s*(\\d+)\\s*(cm|m)?\\b")) { "${it.groupValues[1]}x${it.groupValues[2]}${it.groupValues[3]}" }
        // other units stay with their number: "150 w" -> "150w"
        s = s.replace(Regex("\\b(\\d+(\\.\\d+)?)\\s+(w|ws|mah|cm|mm|k|m|v|wh|pouces|inch)\\b")) { it.groupValues[1] + it.groupValues[3] }

        val raw = s.split(Regex("[^a-z0-9.]+")).map { it.trim('.') }.filter { it.isNotBlank() }
        // a number right after a name word belongs to it: "hero 13" -> "hero13", "rs 3" -> "rs3", "go 2" -> "go2"
        val tokens = ArrayList<String>()
        for (tk in raw) {
            val prev = tokens.lastOrNull()
            if (tk.matches(Regex("\\d{1,2}")) && prev != null && prev.all { it.isLetter() } && prev.length >= 2 &&
                prev !in STOP && prev !in COLORS && prev !in BRANDS && !BRAND_ALIASES.containsKey(prev)
            ) {
                tokens[tokens.lastIndex] = prev + tk
            } else tokens += tk
        }

        val brandWords = (brand?.split(" ")?.toSet() ?: emptySet()) + BRAND_ALIASES.filterValues { it == brand }.keys
        val models = LinkedHashSet<String>()
        val variants = LinkedHashSet<String>()
        val words = LinkedHashSet<String>()
        val accessoryWords = ACCESSORY.values.flatten().flatMap { it.split(" ") }.toSet()
        for (tk in tokens) {
            if (tk in brandWords || tk in COLORS || tk in STOP) continue
            if (BRAND_ALIASES.containsKey(tk) || tk in BRANDS) continue
            val hasDigit = tk.any { it.isDigit() }
            val hasLetter = tk.any { it.isLetter() }
            when {
                tk.matches(Regex("mk\\d")) -> models += tk
                tk.matches(Regex("\\d+mm")) -> if (lens == null) lens = tk else words += tk
                tk.matches(Regex("\\d+(gb|tb)")) -> models += tk
                tk.matches(Regex("\\d+(\\.\\d+)?k")) -> words += tk
                tk.matches(Regex("\\d+(\\.\\d+)?(mah|w|ws|wh|v|p|fps|m|cm|pouces|inch|mhz|ghz|hz)")) -> words += tk
                hasDigit && hasLetter -> models += tk
                hasDigit -> models += tk
                tk in VARIANTS -> variants += tk
                tk.length > 1 && tk !in accessoryWords -> words += tk
            }
        }
        // nothing with a digit ("Rode PodMic", "Nikon ZR"): the first real word after the brand is the model name
        if (models.isEmpty() && lens == null && brand != null) {
            words.firstOrNull()?.let { models += it; words -= it }
        }
        return ProductKey(brand, models, variants, lens, aperture, mount, tx, body, used, accessory, bundle, words)
    }

    private fun roman(r: String) = "mk" + when (r) { "i" -> 1; "ii" -> 2; "iii" -> 3; "iv" -> 4; "v" -> 5; "vi" -> 6; else -> 0 }

    /** Similarity 0..1, or null if the two keys can't be the same product. */
    fun score(a: ProductKey, b: ProductKey): Double? {
        if (!a.usable || !b.usable) return null
        if (a.brand != null && b.brand != null && a.brand != b.brand) return null
        // without a brand on both sides a bare number ("Support mural de 4" vs "PlayStation 4") means nothing
        if ((a.brand == null || b.brand == null) && !(a.strongCode && b.strongCode)) return null
        if (a.used != b.used) return null
        if (a.accessory != b.accessory) return null
        if (a.bundle != b.bundle) return null
        if (a.models != b.models) return null
        if (a.lens != b.lens) return null
        if (a.aperture != null && b.aperture != null && a.aperture != b.aperture) return null
        if (a.mount != null && b.mount != null && a.mount != b.mount) return null
        if (a.tx != null && b.tx != null && a.tx != b.tx) return null
        val va = a.variants - setOf("kit"); val vb = b.variants - setOf("kit")
        if (va != vb) return null
        // both titles name the product line and share nothing: "VideoMic GO II" vs "Wireless GO II"
        if (a.words.isNotEmpty() && b.words.isNotEmpty() && (a.words intersect b.words).isEmpty()) return null
        val union = (a.words + b.words).size
        val jac = if (union == 0) 1.0 else (a.words intersect b.words).size.toDouble() / union
        return 0.6 + 0.4 * jac
    }
}
