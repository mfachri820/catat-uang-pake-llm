package com.catatuang.app.receipt

import java.util.Calendar
import java.util.TimeZone

/** What we could read off a transfer / payment receipt. Any field may be missing. */
data class ParsedReceipt(
    val amount: Long?,
    val counterparty: String?,
    val timestamp: Long?,
    val reference: String?,
    val note: String?,
    /** Detected app or bank, e.g. "BRImo". */
    val source: String,
    /** "Transfer", "QRIS", "Top Up", "Pembayaran" or null. */
    val kind: String?,
    val rawText: String,
)

/** A line of OCR text with its position on the screenshot (pixels). */
data class OcrBox(val text: String, val top: Int, val bottom: Int, val left: Int)

/**
 * Turns OCR text from Indonesian banking / e-wallet receipts into structured fields.
 * Tuned for BRImo's "Transaksi Berhasil" receipt, with fallbacks that work on
 * DANA, GoPay, OVO, Livin', BCA mobile and QRIS receipts.
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object ReceiptParser {

    /**
     * Receipts are often two columns ("Nominal ........ Rp50.000"). OCR returns each side
     * as its own line, so we re-join lines that sit on the same row, left to right.
     */
    fun joinRows(boxes: List<OcrBox>): String {
        if (boxes.isEmpty()) return ""
        val sorted = boxes.sortedBy { (it.top + it.bottom) / 2 }
        val rows = mutableListOf<MutableList<OcrBox>>()
        for (box in sorted) {
            val center = (box.top + box.bottom) / 2
            val row = rows.lastOrNull()
            val rowTop = row?.minOf { it.top }
            val rowBottom = row?.maxOf { it.bottom }
            if (row != null && rowTop != null && rowBottom != null && center in rowTop..rowBottom) {
                row.add(box)
            } else {
                rows.add(mutableListOf(box))
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parse(text: String, defaultZone: TimeZone = TimeZone.getDefault()): ParsedReceipt {
        val lines = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
        return ParsedReceipt(
            amount = findAmount(lines),
            counterparty = findCounterparty(lines),
            timestamp = findTimestamp(lines, defaultZone),
            reference = findReference(lines),
            note = findNote(lines),
            source = detectSource(lines),
            kind = detectKind(text),
            rawText = text,
        )
    }

    // ---------------------------------------------------------------- amounts

    private val AMOUNT_LABELS = listOf(
        "total transaksi", "total pembayaran", "total bayar", "total transfer", "total",
        "nominal transfer", "nominal", "jumlah transfer", "jumlah pembayaran", "jumlah", "amount",
    )
    private val FEE_WORDS = listOf("biaya", "admin", "fee", "diskon", "cashback", "saldo")

    private val RP_AMOUNT = Regex(
        "(?i)(?:rp\\.?|idr)\\s*([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)",
    )
    private val BARE_AMOUNT = Regex("(?<![0-9A-Za-z])([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{2})?)(?![0-9])")

    /** "Rp1.250.000,00" -> 1250000, "IDR 50,000.00" -> 50000, "Rp 15.000" -> 15000. */
    fun toRupiah(raw: String): Long? {
        var s = raw.trim()
        if (Regex(".*[.,][0-9]{1,2}$").matches(s)) s = s.substring(0, s.indexOfLast { it == '.' || it == ',' })
        s = s.replace(".", "").replace(",", "")
        return s.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun amountIn(line: String, allowBare: Boolean): Long? {
        RP_AMOUNT.find(line)?.let { return toRupiah(it.groupValues[1]) }
        if (allowBare) BARE_AMOUNT.find(line)?.let { return toRupiah(it.groupValues[1]) }
        return null
    }

    private fun findAmount(lines: List<String>): Long? {
        for (label in AMOUNT_LABELS) {
            for ((i, line) in lines.withIndex()) {
                val lower = line.lowercase()
                if (!lower.startsWith(label)) continue
                if (FEE_WORDS.any { lower.contains(it) }) continue
                // "Total Transaksi Rp52.500" on one row…
                amountIn(line.substring(label.length), allowBare = true)?.let { return it }
                // …or the value on the next row.
                lines.getOrNull(i + 1)?.let { next -> amountIn(next, allowBare = true)?.let { return it } }
            }
        }
        // Fallback: the largest rupiah amount on the receipt (the total is usually the biggest).
        return lines.filterNot { l -> FEE_WORDS.any { l.lowercase().contains(it) } }
            .flatMap { l -> RP_AMOUNT.findAll(l).mapNotNull { toRupiah(it.groupValues[1]) }.toList() }
            .maxOrNull()
    }

    // ---------------------------------------------------------------- labels

    private val COUNTERPARTY_LABELS = listOf(
        "nama penerima", "nama tujuan", "nama merchant", "nama toko", "rekening tujuan",
        "penerima", "tujuan", "merchant", "kepada", "dibayar ke", "to",
    )
    private val NOTE_LABELS = listOf("catatan", "berita", "keterangan", "pesan", "deskripsi", "note")
    private val REFERENCE_LABELS = listOf(
        "nomor referensi", "no. referensi", "no referensi", "no. ref", "no ref", "noref",
        "id transaksi", "no. transaksi", "nomor transaksi", "reference", "ref",
    )

    /** Every label we know about, so we never mistake a label for a value. */
    private val ALL_LABELS = (
        AMOUNT_LABELS + COUNTERPARTY_LABELS + NOTE_LABELS + REFERENCE_LABELS + listOf(
            "sumber dana", "rekening sumber", "biaya admin", "biaya", "status", "berhasil",
            "transaksi berhasil", "jenis transaksi", "waktu", "tanggal", "metode pembayaran",
            "bank", "no. rekening", "nomor rekening",
        )
        ).toSet()

    private fun normalize(line: String) = line.lowercase().trim().trimEnd(':').trim()

    private fun isLabel(line: String) = normalize(line) in ALL_LABELS

    /**
     * If [line] is "<label>" returns "" (value is on a following line);
     * if it is "<label>: value" or "<label> value" returns the value; otherwise null.
     */
    private fun matchLabel(line: String, label: String): String? {
        val n = line.lowercase().trim()
        if (normalize(line) == label) return ""
        if (!n.startsWith(label)) return null
        val rest = line.trim().substring(label.length)
        if (rest.isEmpty() || !(rest[0] == ':' || rest[0] == ' ')) return null
        return rest.trimStart(':', ' ').trim()
    }

    private fun looksLikeName(s: String): Boolean {
        val letters = s.count { it.isLetter() }
        val digits = s.count { it.isDigit() }
        if (letters < 2 || digits > letters) return false
        if (isLabel(s)) return false
        val lower = s.lowercase()
        if (lower.startsWith("bank ") || lower in BANK_NAMES) return false
        if (lower in NOT_NAMES) return false
        if (RP_AMOUNT.containsMatchIn(s)) return false
        return true
    }

    private fun findCounterparty(lines: List<String>): String? {
        for (label in COUNTERPARTY_LABELS) {
            for ((i, line) in lines.withIndex()) {
                val inline = matchLabel(line, label) ?: continue
                if (inline.isNotEmpty() && looksLikeName(inline)) return cleanName(inline)
                // Value block below the label: name, then bank, then account number.
                for (j in i + 1..minOf(i + 3, lines.lastIndex)) {
                    if (isLabel(lines[j])) break
                    if (looksLikeName(lines[j])) return cleanName(lines[j])
                }
            }
        }
        return null
    }

    private fun cleanName(s: String) = s.trim().trim('-', ':').trim()

    private fun findNote(lines: List<String>): String? {
        for (label in NOTE_LABELS) {
            for ((i, line) in lines.withIndex()) {
                val inline = matchLabel(line, label) ?: continue
                val value = inline.ifEmpty {
                    lines.getOrNull(i + 1)?.takeUnless { isLabel(it) }.orEmpty()
                }.trim()
                if (value.isNotEmpty() && value != "-") return value
            }
        }
        return null
    }

    private val REF_TOKEN = Regex("[A-Za-z0-9]{6,}")

    private fun findReference(lines: List<String>): String? {
        for (label in REFERENCE_LABELS) {
            for ((i, line) in lines.withIndex()) {
                val inline = matchLabel(line, label) ?: continue
                val candidate = inline.ifEmpty { lines.getOrNull(i + 1).orEmpty() }
                REF_TOKEN.findAll(candidate).map { it.value }.firstOrNull { tok -> tok.any { it.isDigit() } }
                    ?.let { return it }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- dates

    private val MONTHS = mapOf(
        "jan" to 0, "januari" to 0, "january" to 0,
        "feb" to 1, "februari" to 1, "february" to 1, "pebruari" to 1,
        "mar" to 2, "maret" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "mei" to 4, "may" to 4,
        "jun" to 5, "juni" to 5, "june" to 5,
        "jul" to 6, "juli" to 6, "july" to 6,
        "agu" to 7, "agt" to 7, "ags" to 7, "aug" to 7, "agustus" to 7, "august" to 7,
        "sep" to 8, "sept" to 8, "september" to 8,
        "okt" to 9, "oct" to 9, "oktober" to 9, "october" to 9,
        "nov" to 10, "nopember" to 10, "november" to 10,
        "des" to 11, "dec" to 11, "desember" to 11, "december" to 11,
    )

    private val WORD_DATE = Regex("(?i)\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})\\b")
    private val NUM_DATE = Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b")
    private val TIME = Regex("(?i)\\b(\\d{1,2})[:.](\\d{2})(?:[:.](\\d{2}))?\\s*(WIB|WITA|WIT)?\\b")

    private fun zoneFor(label: String?, fallback: TimeZone): TimeZone = when (label?.uppercase()) {
        "WIB" -> TimeZone.getTimeZone("Asia/Jakarta")
        "WITA" -> TimeZone.getTimeZone("Asia/Makassar")
        "WIT" -> TimeZone.getTimeZone("Asia/Jayapura")
        else -> fallback
    }

    private fun findTimestamp(lines: List<String>, defaultZone: TimeZone): Long? {
        for ((i, line) in lines.withIndex()) {
            var day: Int; var month: Int; var year: Int; var after: String
            val w = WORD_DATE.find(line)
            val n = NUM_DATE.find(line)
            when {
                w != null && MONTHS.containsKey(w.groupValues[2].lowercase()) -> {
                    day = w.groupValues[1].toInt()
                    month = MONTHS.getValue(w.groupValues[2].lowercase())
                    year = w.groupValues[3].toInt()
                    after = line.substring(w.range.last + 1)
                }
                n != null -> {
                    day = n.groupValues[1].toInt()
                    month = n.groupValues[2].toInt() - 1
                    year = n.groupValues[3].toInt()
                    after = line.substring(n.range.last + 1)
                }
                else -> continue
            }
            if (day !in 1..31 || month !in 0..11) continue
            // Time is usually after the date on the same line, sometimes on the next one.
            val t = validTime(after) ?: lines.getOrNull(i + 1)?.let { validTime(it) }
            val zone = zoneFor(t?.groupValues?.get(4)?.takeIf { it.isNotEmpty() }, defaultZone)
            return Calendar.getInstance(zone).apply {
                clear()
                set(year, month, day, t?.groupValues?.get(1)?.toInt() ?: 12, t?.groupValues?.get(2)?.toInt() ?: 0,
                    t?.groupValues?.get(3)?.takeIf { it.isNotEmpty() }?.toInt() ?: 0)
            }.timeInMillis
        }
        return null
    }

    /** A time like 13:39, 13:39:12, or 13.39 WIB (dots only when a zone follows, to avoid amounts). */
    private fun validTime(s: String): MatchResult? = TIME.findAll(s).firstOrNull { m ->
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        val usedColon = m.value.contains(':')
        h in 0..23 && min in 0..59 && (usedColon || m.groupValues[4].isNotEmpty())
    }

    // ---------------------------------------------------------------- source & kind

    private val BANK_NAMES = setOf(
        "bri", "bca", "bni", "mandiri", "btn", "cimb niaga", "permata", "danamon", "bsi", "seabank",
        "jago", "bank jago", "blu", "dana", "gopay", "ovo", "shopeepay", "linkaja",
    )

    private val NOT_NAMES = setOf(
        "transfer", "pembayaran", "transaksi", "berhasil", "sukses", "rekening", "qris", "detail",
        "top up", "transfer berhasil", "pembayaran berhasil", "transaksi berhasil",
    )

    private fun detectSource(lines: List<String>): String {
        val all = lines.joinToString("\n").lowercase()
        val strong = listOf(
            "brimo" to "BRImo", "livin" to "Livin' by Mandiri", "bca mobile" to "BCA mobile",
            "mybca" to "myBCA", "wondr" to "wondr by BNI", "bni mobile" to "BNI Mobile",
            "gopay" to "GoPay", "ovo" to "OVO", "shopeepay" to "ShopeePay", "seabank" to "SeaBank",
            "linkaja" to "LinkAja", "jago" to "Bank Jago",
        )
        strong.firstOrNull { (k, _) -> Regex("\\b$k\\b").containsMatchIn(all) }?.let { return it.second }
        // "Sumber Dana" contains the word DANA, so remove it before looking for the DANA wallet.
        if (Regex("\\bdana\\b").containsMatchIn(all.replace("sumber dana", ""))) return "DANA"
        // The source-of-funds block names the sender's bank.
        val idx = lines.indexOfFirst { normalize(it) in setOf("sumber dana", "rekening sumber", "dari") }
        if (idx >= 0) {
            val block = lines.drop(idx + 1).take(3).joinToString(" ").lowercase()
            if (Regex("\\bbri\\b").containsMatchIn(block)) return "BRImo"
            if (Regex("\\bbca\\b").containsMatchIn(block)) return "BCA mobile"
            if (Regex("\\bmandiri\\b").containsMatchIn(block)) return "Livin' by Mandiri"
            if (Regex("\\bbni\\b").containsMatchIn(block)) return "BNI Mobile"
        }
        if (Regex("\\b(bank )?bri\\b").containsMatchIn(all)) return "BRImo"
        return "Receipt"
    }

    private fun detectKind(text: String): String? {
        val t = text.lowercase()
        return when {
            "qris" in t -> "QRIS"
            "top up" in t || "topup" in t || "isi saldo" in t -> "Top Up"
            "transfer" in t -> "Transfer"
            "pembayaran" in t || "bayar" in t || "payment" in t -> "Pembayaran"
            else -> null
        }
    }
}
