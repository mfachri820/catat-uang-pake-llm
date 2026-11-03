package com.catatuang.app.statement

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/** One line of a bank statement (rekening koran / mutasi). */
data class StatementRow(
    val timestamp: Long,
    val description: String,
    val amount: Long,
    /** true = money out (debet), false = money in (kredit). */
    val isDebit: Boolean,
    val balance: Long?,
)

data class ParsedStatement(
    val rows: List<StatementRow>,
    val openingBalance: Long?,
) {
    val debits get() = rows.filter { it.isDebit }
    val credits get() = rows.filterNot { it.isDebit }
}

/**
 * Parses the text of an Indonesian bank statement: BRI e-statement / rekening koran, BRImo
 * mutasi, and similar layouts from other banks.
 *
 * It doesn't rely on exact column positions. A transaction is a line that starts with a date
 * and carries money amounts (with decimals, or prefixed by Rp). The last amount is the running
 * balance, and the change in balance between rows tells money out from money in, which works
 * whether the statement has separate Debet/Kredit columns, DB/CR markers, or signs.
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object StatementParser {

    private val NUM_DATE = Regex("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b")
    private val WORD_DATE = Regex("^(\\d{1,2})[\\s\\-]([A-Za-z]{3,9})[\\s\\-](\\d{2,4})\\b")
    private val TIME = Regex("^\\s*(\\d{1,2})[:.](\\d{2})(?:[:.](\\d{2}))?")

    /**
     * Money: "1,500,000.00", "25.000,00", "38000.00", or "Rp50.000"; optional sign and DB/CR marker.
     * Numbers without decimals and without Rp (teller IDs, account numbers) are ignored.
     */
    private val MONEY = Regex(
        "(?<![\\w.,])([+-])?\\s?(?:(?:Rp\\.?|IDR)\\s?)?" +
            "(\\d{1,3}(?:[.,]\\d{3})+[.,]\\d{2}|\\d+[.,]\\d{2}|(?<=Rp|Rp\\.|Rp |IDR |IDR)\\d{1,3}(?:\\.\\d{3})+)" +
            "(?![\\d])(?:\\s?(DB|CR|Db|Cr|D|K)\\b)?",
    )

    private val MONTHS = mapOf(
        "jan" to 0, "feb" to 1, "peb" to 1, "mar" to 2, "apr" to 3, "mei" to 4, "may" to 4, "jun" to 5,
        "jul" to 6, "agu" to 7, "agt" to 7, "ags" to 7, "aug" to 7, "sep" to 8, "okt" to 9, "oct" to 9,
        "nov" to 10, "nop" to 10, "des" to 11, "dec" to 11,
    )

    private val NOISE = listOf(
        "tanggal transaksi", "uraian", "halaman", "page ", "saldo awal", "saldo akhir", "opening balance",
        "closing balance", "total transaksi", "total mutasi", "mutasi debet", "mutasi kredit", "no. rekening",
        "nomor rekening", "periode", "laporan", "terbilang", "keterangan", "biaya materai", "disclaimer",
        "dicetak", "printed", "account statement", "tanggal :",
    )

    private data class Money(val value: Long, val signed: Int?, val marker: String?, val start: Int)

    /** "1,500,000.00" -> 1500000, "25.000,00" -> 25000, "0.00" -> 0. */
    fun parseMoney(raw: String): Long? {
        var s = raw.replace(Regex("(?i)rp\\.?|idr|\\s"), "")
        if (Regex(".*[.,]\\d{2}$").matches(s)) s = s.dropLast(3)
        return s.replace(".", "").replace(",", "").toLongOrNull()
    }

    private fun moneyIn(line: String): List<Money> = MONEY.findAll(line).mapNotNull { m ->
        val v = parseMoney(m.groupValues[2]) ?: return@mapNotNull null
        Money(
            value = v,
            signed = when (m.groupValues[1]) { "-" -> -1; "+" -> 1; else -> null },
            marker = m.groupValues[3].uppercase().ifEmpty { null },
            start = m.range.first,
        )
    }.toList()

    private fun isNoise(line: String): Boolean {
        val l = line.lowercase()
        return NOISE.any { l.contains(it) }
    }

    /** Returns (millis, rest-of-line) if the line starts with a date. */
    private fun leadingDate(line: String, zone: TimeZone): Pair<Long, String>? {
        val (d, m, y, end) = NUM_DATE.find(line)?.let {
            listOf(it.groupValues[1].toInt(), it.groupValues[2].toInt() - 1, it.groupValues[3].toInt(), it.range.last + 1)
        } ?: WORD_DATE.find(line)?.let {
            val month = MONTHS[it.groupValues[2].lowercase().take(3)] ?: return null
            listOf(it.groupValues[1].toInt(), month, it.groupValues[3].toInt(), it.range.last + 1)
        } ?: return null
        if (d !in 1..31 || m !in 0..11) return null
        val year = if (y < 100) 2000 + y else y
        var rest = line.substring(end)
        var h = 0; var min = 0; var sec = 0
        TIME.find(rest)?.let { t ->
            val hh = t.groupValues[1].toInt(); val mm = t.groupValues[2].toInt()
            if (hh in 0..23 && mm in 0..59) {
                h = hh; min = mm; sec = t.groupValues[3].toIntOrNull() ?: 0
                rest = rest.substring(t.range.last + 1)
            }
        }
        val millis = Calendar.getInstance(zone).apply { clear(); set(year, m, d, h, min, sec) }.timeInMillis
        return millis to rest.trim()
    }

    private class Draft(val timestamp: Long, var description: String, var money: List<Money>)

    fun parse(text: String, zone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")): ParsedStatement {
        val lines = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }

        val opening = lines.firstOrNull { l ->
            val low = l.lowercase(); low.contains("saldo awal") || low.contains("opening balance")
        }?.let { moneyIn(it).lastOrNull()?.value }

        val drafts = mutableListOf<Draft>()
        var current: Draft? = null
        var continuation = 0
        for (line in lines) {
            val dated = leadingDate(line, TimeZone.getTimeZone(zone.id))
            if (dated != null && !isNoise(line)) {
                val (ts, rest) = dated
                val money = moneyIn(rest)
                val desc = if (money.isNotEmpty()) rest.substring(0, money.first().start) else rest
                current = Draft(ts, stripTeller(desc), money).also { drafts.add(it) }
                continuation = 0
                continue
            }
            val d = current ?: continue
            if (isNoise(line)) { current = null; continue }
            val money = moneyIn(line)
            if (d.money.isEmpty() && money.isNotEmpty()) {
                // Description wrapped and the amounts landed on the next line.
                d.description = (d.description + " " + stripTeller(line.substring(0, money.first().start))).trim()
                d.money = money
            } else if (money.isEmpty() && continuation < 3) {
                d.description = (d.description + " " + line).trim()
                continuation++
            }
        }

        val rows = mutableListOf<StatementRow>()
        var prevBalance = opening
        for (d in drafts) {
            if (d.money.isEmpty()) continue
            val row = resolve(d, prevBalance) ?: continue
            rows += row
            prevBalance = row.balance ?: prevBalance
        }
        return ParsedStatement(rows, opening)
    }

    private fun resolve(d: Draft, prevBalance: Long?): StatementRow? {
        val m = d.money
        val balance = if (m.size >= 2) m.last().value else null
        val delta = if (balance != null && prevBalance != null) balance - prevBalance else null
        val desc = cleanDescription(d.description)

        // Explicit markers win: "25,000.00 DB", "-25.000,00", "50.000 CR".
        m.dropLast(if (balance != null) 1 else 0).firstOrNull { it.marker != null || it.signed != null }?.let { x ->
            val debit = x.marker in setOf("DB", "D") || x.signed == -1
            return StatementRow(d.timestamp, desc, x.value, debit, balance)
        }

        val amounts = if (balance != null) m.dropLast(1) else m
        val nonZero = amounts.filter { it.value > 0 }
        val amount: Long
        val debit: Boolean
        when {
            // Separate Debet and Kredit columns: "... 38,000.00 0.00 3,462,000.00".
            amounts.size >= 2 && nonZero.size == 1 -> {
                amount = nonZero[0].value
                val debitColumn = amounts[amounts.size - 2]
                debit = when {
                    delta != null && abs(delta) == amount -> delta < 0
                    else -> nonZero[0] === debitColumn
                }
            }
            nonZero.isNotEmpty() -> {
                amount = nonZero[0].value
                debit = if (delta != null && abs(delta) == amount) delta < 0 else true
            }
            else -> return null
        }
        return StatementRow(d.timestamp, desc, amount, debit, balance)
    }

    private fun cleanDescription(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /** Drops the Teller column (a bare 5–8 digit ID, or "0") that sits just before the amounts. */
    private fun stripTeller(s: String): String = s.trim().replace(Regex("(^|\\s)(\\d{5,8}|0)$"), "").trim()

    // ---------------------------------------------------------------- helpers for categorising

    private val CODES = setOf(
        "NBMB", "ESB", "IBNK", "IBIZ", "MB", "TRF", "TRANSFER", "TRFDN", "KE", "DARI", "BRIVA", "VA",
        "PEMBAYARAN", "PEMBELIAN", "BI-FAST", "BIFAST", "FAST", "ONLINE", "SWITCHING", "DB", "CR",
        "QR", "QRIS", "PAYMENT", "DEBIT", "KARTU", "POS", "EDC", "IDR",
    )

    /**
     * Best guess at the other party from a statement description: who got paid for money out,
     * who sent it for money in ("NBMB ANI TO FACHRI" is from Ani).
     */
    fun guessCounterparty(description: String, incoming: Boolean = false): String {
        val upper = description.uppercase()
        val base = if (incoming) {
            Regex("\\bDARI\\s+(.+)$").find(upper)?.groupValues?.get(1)
                ?: Regex("^(.+?)\\s+TO\\s+").find(upper)?.groupValues?.get(1)
                ?: upper
        } else {
            Regex("\\b(?:TO|KE)\\s+(.+)$").find(upper)?.groupValues?.get(1) ?: upper
        }
        val words = base.split(' ', '/', ':', '-', '|')
            .filter { w -> w.isNotBlank() && w !in CODES && w.count { it.isDigit() } < 3 && w.any { it.isLetter() } }
            .take(4)
        val name = words.joinToString(" ").ifBlank { description.take(40) }
        return name.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }
}
