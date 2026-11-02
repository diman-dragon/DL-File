package dev.filedeck.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

private const val ZWSP = '\u200B'

/** Наименьший масштаб шрифта, до которого подписи сжимаются, лишь бы слово не рвалось посреди. */
private const val MIN_SHRINK = 0.6f

/** Символы, после которых имя файла можно перенести: «IMG_2024.05_x.jpg» рвётся по «_» и «.», а не по буквам. */
private const val SOFT_BREAK_AFTER = "_./\\-+,()[]"

/** Вставляет невидимые точки переноса после разделителей в именах и путях. Только для показа, исходная строка не меняется. */
internal fun withSoftBreaks(s: String): String {
    var need = false
    for (i in 0 until s.length - 1) if (s[i] in SOFT_BREAK_AFTER && !s[i + 1].isWhitespace() && s[i + 1] != ZWSP) { need = true; break }
    if (!need) return s
    val sb = StringBuilder(s.length + 8)
    for (i in s.indices) {
        val c = s[i]
        sb.append(c)
        if (c in SOFT_BREAK_AFTER && i + 1 < s.length && !s[i + 1].isWhitespace() && s[i + 1] != ZWSP) sb.append(ZWSP)
    }
    return sb.toString()
}

/** true, если какая-то строка оборвана посреди слова (не на пробеле, не на разделителе, не на дефисе). */
internal fun brokeMidWord(text: String, r: TextLayoutResult): Boolean {
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i, visibleEnd = false)
        if (end <= 0 || end >= text.length) continue
        val prev = text[end - 1]
        val next = text[end]
        if (prev.isWhitespace() || next.isWhitespace()) continue
        if (prev == ZWSP || next == ZWSP) continue
        if (prev == '-' || prev == '\u2013' || prev == '\u2014') continue
        return true
    }
    return false
}

/**
 * Единственный текст приложения. Слова никогда не рвутся посреди: если слово не помещается в строку,
 * шрифт плавно уменьшается (до [MIN_SHRINK]), а перенос идёт только по пробелам и разделителям.
 * Все экраны используют его вместо material3.Text.
 */
@Composable
fun FdText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    fontFamily: FontFamily? = null,
) {
    val ambient = LocalTextStyle.current
    val base = when {
        fontSize.isSpecified -> fontSize
        ambient.fontSize.isSpecified -> ambient.fontSize
        else -> 14.sp
    }
    val baseLine = when {
        lineHeight.isSpecified -> lineHeight
        ambient.lineHeight.isSpecified -> ambient.lineHeight
        else -> TextUnit.Unspecified
    }
    var k by remember(text, base) { mutableFloatStateOf(1f) }
    var width by remember(text, base) { mutableIntStateOf(0) }
    M3Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = base * k,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        lineHeight = if (baseLine.isSpecified) baseLine * k else TextUnit.Unspecified,
        maxLines = maxLines,
        onTextLayout = { r ->
            val w = r.layoutInput.constraints.maxWidth
            if (width != 0 && w > width && k < 1f) {
                width = w
                k = 1f
            } else {
                width = w
                if (k > MIN_SHRINK && brokeMidWord(text, r)) k = (k * 0.92f).coerceAtLeast(MIN_SHRINK)
            }
        },
    )
}

/**
 * Единые текстовые атомы. Любое имя (файла, папки, архива, пути) переносится на новые строки целиком:
 * без многоточия, без обрезки и без сокращений; перенос идёт по разделителям (_ . - /), а слишком длинное
 * слово без разделителей сначала уменьшает шрифт и только потом переносится по символам.
 */
@Composable
fun NameText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = fd.text,
    weight: FontWeight? = null,
    center: Boolean = false,
) {
    FdText(
        withSoftBreaks(text),
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        fontWeight = weight,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, size: Int = 12, center: Boolean = false) {
    NameText(text, modifier, size, fd.muted, center = center)
}

/** Карточка плитки: значок или миниатюра сверху, название и подпись под ней — одинаково везде. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    square: Boolean = true,
    lead: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) fd.manila.copy(alpha = 0.18f) else fd.surface2)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().then(if (square) Modifier.aspectRatio(1f) else Modifier.height(40.dp)),
            contentAlignment = Alignment.Center,
        ) {
            lead()
            if (selected) Box(Modifier.align(Alignment.TopEnd)) { SelectCheck(true) }
        }
        NameText(title, Modifier.padding(top = 6.dp), size = 13, center = true)
        if (subtitle != null) MetaText(subtitle, size = 11, center = true)
    }
}
