package com.techmare.pricewatch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.PricePoint
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun dh(v: Double): String =
    NumberFormat.getIntegerInstance(Locale.FRANCE).format(v.roundToLong()).replace(' ', ' ').replace(' ', ' ') + " dh"

fun signedDh(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + dh(abs(v))

fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
}

/** The graphite panel used for the most important thing on each screen. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.panel).padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/**
 * Price ladder: every shop's price on one line, cheapest on the left.
 * Your price is the yellow marker; competitors are dots (hollow when out of stock).
 */
@Composable
fun PriceLadder(comparison: Comparison, onDark: Boolean = true, height: Int = 26) {
    val c = LocalAppColors.current
    val prices = comparison.offers.map { it.price } + comparison.own.price
    val min = prices.min()
    val max = prices.max()
    val track = if (onDark) c.panelTick else c.rule
    val dot = if (onDark) c.panelText else c.ink
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val y = size.height / 2
        fun x(p: Double) = pad + if (max == min) w / 2 else ((p - min) / (max - min)).toFloat() * w
        drawRoundRect(track, Offset(pad, y - 1.5.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        for (o in comparison.offers) {
            val r = 5.dp.toPx()
            if (o.inStock) drawCircle(dot, r, Offset(x(o.price), y))
            else drawCircle(dot, r - 1.dp.toPx(), Offset(x(o.price), y), style = Stroke(1.5.dp.toPx()))
        }
        val ox = x(comparison.own.price)
        drawRoundRect(c.signal, Offset(ox - 3.dp.toPx(), y - 10.dp.toPx()), Size(6.dp.toPx(), 20.dp.toPx()), CornerRadius(3.dp.toPx()))
    }
}

/** Small line chart of price history. */
@Composable
fun HistoryChart(series: List<Pair<Color, List<PricePoint>>>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val all = series.flatMap { it.second }
    if (all.size < 2) return
    val dates = all.map { it.date }.distinct().sorted()
    if (dates.size < 2) return
    val min = all.minOf { it.price }
    val max = all.maxOf { it.price }
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
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
                    drawLine(color, Offset(x(p.date), y(p.price)), Offset(x(pt.date), y(p.price)), 2.5.dp.toPx())
                    drawLine(color, Offset(x(pt.date), y(p.price)), Offset(x(pt.date), y(pt.price)), 2.5.dp.toPx())
                }
                prev = pt
            }
            prev?.let { drawLine(color, Offset(x(it.date), y(it.price)), Offset(x(dates.last()), y(it.price)), 2.5.dp.toPx()) }
        }
    }
}

/** A note with a coloured edge: red for problems, grey for guidance. */
@Composable
fun Note(text: String, warning: Boolean = false) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (warning) c.high else c.muted.copy(alpha = 0.5f)))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) c.ink else c.muted)
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.ink)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

/** Colour for a price difference: red when you're more expensive, green when cheaper. */
@Composable
fun diffColor(diff: Double?, threshold: Int): Color {
    val c = LocalAppColors.current
    return when {
        diff == null -> c.muted
        diff > threshold -> c.high
        diff < -threshold -> c.ok
        else -> c.muted
    }
}

@Composable
fun ProductLine(name: String, sub: String, right: String, rightColor: Color, rightSub: String? = null, rightSubColor: Color? = null) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(right, style = MaterialTheme.typography.titleSmall, color = rightColor)
            if (rightSub != null) Text(rightSub, style = MaterialTheme.typography.bodySmall, color = rightSubColor ?: c.muted)
        }
    }
}
