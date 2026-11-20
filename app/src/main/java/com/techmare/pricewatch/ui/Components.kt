package com.techmare.pricewatch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.PricePoint
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** "29 800" with narrow no-break spaces between thousands. */
fun num(v: Double): String =
    NumberFormat.getIntegerInstance(Locale.FRANCE).format(v.roundToLong()).replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

fun dh(v: Double): String = num(v) + " dh"

fun signedDh(v: Double): String = signedNum(v) + " dh"

/** "+11 301", "−1 500", "0". */
fun signedNum(v: Double): String = (if (v > 0.5) "+" else if (v < -0.5) "−" else "") + num(abs(v))

/** "11k", "7.5k", "850": for tight spots like the ticker. */
fun shortNum(v: Double): String {
    val a = abs(v)
    val s = when {
        a >= 10_000 -> "${(a / 1000).roundToLong()}k"
        a >= 1_000 -> "%.1fk".format(Locale.US, a / 1000).replace(".0k", "k")
        else -> num(a)
    }
    return (if (v > 0.5) "+" else if (v < -0.5) "−" else "") + s
}

fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
}

/** A price in the numbers face with a small "dh" after it. */
@Composable
fun Price(v: Double, style: TextStyle = Num.row, color: Color = LocalAppColors.current.ink, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Text(
        buildAnnotatedString {
            append(num(v))
            withStyle(SpanStyle(fontSize = style.fontSize * 0.62f, color = c.muted, fontFamily = Fonts.text, letterSpacing = 0.sp)) { append(" dh") }
        },
        modifier, style = style, color = color, maxLines = 1,
    )
}

/** Raised surface for the most important block on a screen. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel)
            .border(1.dp, c.rule, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/**
 * Price ladder: every shop's price on one line, cheapest on the left.
 * Your price is the yellow bar; competitors are dots (hollow when out of stock).
 */
@Composable
fun PriceLadder(comparison: Comparison, onDark: Boolean = true, height: Int = 22, colors: List<Color> = emptyList()) {
    val c = LocalAppColors.current
    val prices = comparison.offers.map { it.price } + comparison.own.price
    val min = prices.min()
    val max = prices.max()
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val pad = 6.dp.toPx()
        val w = size.width - pad * 2
        val y = size.height / 2
        fun x(p: Double) = pad + if (max == min) w / 2 else ((p - min) / (max - min)).toFloat() * w
        drawLine(c.rule, Offset(pad, y), Offset(pad + w, y), 2.dp.toPx(), StrokeCap.Round)
        // the gap between the cheapest in stock and you, tinted by who's ahead
        comparison.best?.let { b ->
            val from = x(b.price); val to = x(comparison.own.price)
            drawLine((if (to > from) c.high else c.ok).copy(alpha = 0.45f), Offset(minOf(from, to), y), Offset(maxOf(from, to), y), 2.dp.toPx())
        }
        comparison.offers.forEachIndexed { i, o ->
            val r = 4.dp.toPx()
            val col = colors.getOrNull(i) ?: c.ink
            if (o.inStock) drawCircle(col, r, Offset(x(o.price), y))
            else drawCircle(col, r - 0.75.dp.toPx(), Offset(x(o.price), y), style = Stroke(1.5.dp.toPx()))
        }
        val ox = x(comparison.own.price)
        drawRoundRect(c.signal, Offset(ox - 2.dp.toPx(), y - 8.dp.toPx()), Size(4.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** Tiny step chart: the best competitor price over the last weeks, with your price as a yellow guide line. */
@Composable
fun Sparkline(points: List<PricePoint>, yours: Double?, modifier: Modifier = Modifier, color: Color = LocalAppColors.current.muted) {
    val c = LocalAppColors.current
    val pts = points.sortedBy { it.date }
    val dates = pts.map { it.date }.distinct()
    if (dates.size < 2) return
    // scaled to the competitor's own moves; your price only shows when it falls inside that range
    val min = pts.minOf { it.price }; val max = pts.maxOf { it.price }
    val trend = pts.last().price - pts.first().price
    val line = when { trend < 0 -> c.high; trend > 0 -> c.ok; else -> color }
    Canvas(modifier) {
        val w = 1.5.dp.toPx()
        fun x(d: String) = dates.indexOf(d).toFloat() / (dates.size - 1) * size.width
        fun y(p: Double) = if (max == min) size.height / 2 else w + (1 - ((p - min) / (max - min)).toFloat()) * (size.height - 2 * w)
        if (yours != null && yours in min..max) drawLine(c.signal.copy(alpha = 0.55f), Offset(0f, y(yours)), Offset(size.width, y(yours)), 1.dp.toPx())
        // step line: a price holds until the next change
        for (i in 1 until pts.size) {
            val p = pts[i - 1]; val q = pts[i]
            drawLine(line, Offset(x(p.date), y(p.price)), Offset(x(q.date), y(p.price)), w)
            drawLine(line, Offset(x(q.date), y(p.price)), Offset(x(q.date), y(q.price)), w)
        }
        drawCircle(line, 2.dp.toPx(), Offset(x(pts.last().date), y(pts.last().price)))
    }
}

/** Larger price history chart: one step line per shop. */
@Composable
fun HistoryChart(series: List<Pair<Color, List<PricePoint>>>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val all = series.flatMap { it.second }
    if (all.size < 2) return
    val dates = all.map { it.date }.distinct().sorted()
    if (dates.size < 2) return
    val min = all.minOf { it.price }
    val max = all.maxOf { it.price }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val pad = 6.dp.toPx()
            fun x(d: String) = pad + dates.indexOf(d).toFloat() / (dates.size - 1) * (size.width - 2 * pad)
            fun y(p: Double) = if (max == min) size.height / 2 else pad + (1 - ((p - min) / (max - min)).toFloat()) * (size.height - 2 * pad)
            for (i in 0..3) {
                val gy = pad + i * (size.height - 2 * pad) / 3
                drawLine(c.rule, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
            }
            for ((color, pts) in series) {
                if (pts.isEmpty()) continue
                // step line: a price holds until the next change
                var prev: PricePoint? = null
                for (pt in pts.sortedBy { it.date }) {
                    val p = prev
                    if (p != null) {
                        drawLine(color, Offset(x(p.date), y(p.price)), Offset(x(pt.date), y(p.price)), 2.dp.toPx())
                        drawLine(color, Offset(x(pt.date), y(p.price)), Offset(x(pt.date), y(pt.price)), 2.dp.toPx())
                    }
                    prev = pt
                }
                prev?.let { drawLine(color, Offset(x(it.date), y(it.price)), Offset(x(dates.last()), y(it.price)), 2.dp.toPx()) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(shortDate(dates.first()), style = Num.small, color = c.faint)
            Spacer(Modifier.weight(1f))
            Text("${num(min)} – ${num(max)}", style = Num.small, color = c.faint)
            Spacer(Modifier.weight(1f))
            Text(shortDate(dates.last()), style = Num.small, color = c.faint)
        }
    }
}

/** "2026-11-20" -> "20/11" */
fun shortDate(d: String): String = d.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}" else d }

/** A note with a coloured edge: red for problems, grey for guidance. */
@Composable
fun Note(text: String, warning: Boolean = false) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(if (warning) c.high else c.faint))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) c.ink else c.muted)
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.ink)
        if (trailing != null) Text(
            trailing,
            if (onTrailing != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTrailing).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier,
            style = MaterialTheme.typography.labelMedium, color = if (onTrailing != null) c.signal else c.faint,
        )
    }
}

/** Colour for a price difference (your price minus theirs): red when you're more expensive, green when cheaper. */
@Composable
fun diffColor(diff: Double?, threshold: Int): Color {
    val c = LocalAppColors.current
    return when {
        diff == null -> c.faint
        diff > threshold -> c.high
        diff < -threshold -> c.ok
        else -> c.muted
    }
}

/**
 * The list row used everywhere: name and a detail line on the left, figures on the right,
 * and an optional thin bar on the left edge saying where you stand.
 */
@Composable
fun QuoteRow(
    name: String,
    sub: String,
    modifier: Modifier = Modifier,
    edge: Color? = null,
    dim: Boolean = false,
    right: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        if (edge != null) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(edge))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = if (dim) c.muted else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) { right() }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.rule))
}

/** Compact search field on a raised surface. */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(c.panel)
            .border(1.dp, c.rule, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = c.muted)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.faint, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = SolidColor(c.signal), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Outlined.Close, "Clear search",
            Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }.padding(5.dp), tint = c.muted,
        )
    }
}

/** Text tabs with counts and a yellow underline on the selected one. Scrolls sideways when they don't fit. */
@Composable
fun <T> TextTabs(items: List<T>, selected: T, label: (T) -> String, count: ((T) -> Int)? = null, onSelect: (T) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { t ->
            val on = t == selected
            Column(
                Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(8.dp)).clickable { onSelect(t) }.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label(t), style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.muted)
                    if (count != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(count(t).toString(), style = Num.small, color = if (on) c.signal else c.faint)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.height(2.dp).fillMaxWidth(if (on) 1f else 0f).background(c.signal))
            }
        }
    }
}

/** Small on/off chip for options that combine (in stock only, camera gear only). */
@Composable
fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Text(
        label,
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (on) c.panelHigh else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) c.faint else c.rule), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.muted,
    )
}

/** Choice chips where exactly one is selected (sort order, threshold). */
@Composable
fun <T> ChoiceRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, c.rule, RoundedCornerShape(9.dp)).padding(2.dp),
    ) {
        items.forEach { t ->
            val on = t == selected
            Text(
                label(t),
                Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) c.panelHigh else Color.Transparent)
                    .clickable { onSelect(t) }.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.muted,
            )
        }
    }
}

/** One item on the ticker tape. */
data class Tick(val text: String, val delta: String, val down: Boolean)

/**
 * The ticker tape: today's competitor price moves scrolling past, like a quote board.
 * It's the only thing in the app that moves by itself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickerTape(ticks: List<Tick>, onClick: () -> Unit) {
    val c = LocalAppColors.current
    if (ticks.isEmpty()) return
    val text: AnnotatedString = buildAnnotatedString {
        ticks.forEach { t ->
            withStyle(SpanStyle(color = if (t.down) c.high else c.ok, fontFamily = Fonts.numbers, fontSize = 12.sp)) {
                append(if (t.down) "▼ " else "▲ ")
            }
            withStyle(SpanStyle(color = c.ink)) { append(t.text) }
            withStyle(SpanStyle(color = if (t.down) c.high else c.ok, fontFamily = Fonts.numbers, fontSize = 13.sp)) { append("  " + t.delta) }
            append("        ")
        }
    }
    Box(
        Modifier.fillMaxWidth().background(c.panel).clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text, Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp, initialDelayMillis = 800, repeatDelayMillis = 0),
            style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
        // soft edges so items slide in and out instead of being cut
        Box(Modifier.align(Alignment.CenterStart).width(20.dp).height(20.dp).background(Brush.horizontalGradient(listOf(c.panel, Color.Transparent))))
        Box(Modifier.align(Alignment.CenterEnd).width(20.dp).height(20.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, c.panel))))
    }
}

/** Status dot + text, used for how fresh the data is. */
@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

val ContentPadding = PaddingValues(horizontal = 16.dp)
