package com.catatuang.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

/** 1502500 -> "Rp1.502.500" */
fun rupiah(amount: Long): String = "Rp" + groupThousands(amount.toString())

/** Compact form for tight spaces: 1_500_000 -> "Rp1,5 jt", 250_000 -> "Rp250 rb". */
fun rupiahShort(amount: Long): String = when {
    amount >= 1_000_000_000 -> "Rp" + trim1(amount / 1_000_000_000.0) + " M"
    amount >= 1_000_000 -> "Rp" + trim1(amount / 1_000_000.0) + " jt"
    amount >= 10_000 -> "Rp" + (amount / 1000) + " rb"
    else -> rupiah(amount)
}

private fun trim1(v: Double) = String.format(ID_LOCALE, "%.1f", v).removeSuffix(",0")

fun groupThousands(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

fun formatDay(millis: Long): String = SimpleDateFormat("EEEE, d MMM", ID_LOCALE).format(Date(millis))
fun formatDateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", ID_LOCALE).format(Date(millis))
fun formatMonth(millis: Long): String = SimpleDateFormat("MMMM yyyy", ID_LOCALE).format(Date(millis))
fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", ID_LOCALE).format(Date(millis))

/** Shows "1500000" as "1.500.000" while the field keeps raw digits. */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = groupThousands(raw)
        val mapping = object : OffsetMapping {
            // A dot sits before digit i whenever (len - i) is a multiple of 3.
            override fun originalToTransformed(offset: Int): Int =
                offset + (1 until offset).count { (raw.length - it) % 3 == 0 }
            override fun transformedToOriginal(offset: Int): Int =
                out.take(offset).count { it != '.' }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
