package com.catatuang.app.statement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StatementParserTest {

    private val wib = TimeZone.getTimeZone("Asia/Jakarta")

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0, s: Int = 0) =
        Calendar.getInstance(wib).apply { clear(); set(y, m - 1, d, h, min, s) }.timeInMillis

    /** BRI e-statement style: Tanggal | Uraian | Teller | Debet | Kredit | Saldo, with wrapped descriptions. */
    @Test fun briStyle_debetKreditColumns() {
        val text = """
            LAPORAN TRANSAKSI FINANSIAL
            Periode Transaksi : 01/10/26 - 31/10/26
            Tanggal Transaksi Uraian Transaksi Teller Debet Kredit Saldo
            Saldo Awal 5,000,000.00
            01/10/26 07:12:31 NBMB MUHAMMAD FACHRI TO BUDI SANTOSO 8888015 1,500,000.00 0.00 3,500,000.00
            02/10/26 12:01:02 QRIS KOPI KENANGAN SUNSET 8888015 38,000.00 0.00 3,462,000.00
            DENPASAR ID
            03/10/26 09:00:00 TARIK TUNAI ATM BRI 0 500,000.00 0.00 2,962,000.00
            05/10/26 10:00:00 TRANSFER DARI PT MAJU JAYA GAJI 8888015 0.00 8,000,000.00 10,962,000.00
            06/10/26 11:11:11 TOPUP DANA 0812xxxx 8888015 100,000.00 0.00 10,862,000.00
            Halaman 1 dari 2
            Tanggal Transaksi Uraian Transaksi Teller Debet Kredit Saldo
            31/10/26 23:59:59 BIAYA ADM BULANAN 0 12,500.00 0.00 10,849,500.00
            Saldo Akhir 10,849,500.00
        """.trimIndent()
        val s = StatementParser.parse(text, wib)
        assertEquals(5_000_000L, s.openingBalance)
        assertEquals(6, s.rows.size)
        assertEquals(5, s.debits.size)
        assertEquals(1, s.credits.size)

        val first = s.rows[0]
        assertEquals(1_500_000L, first.amount)
        assertTrue(first.isDebit)
        assertEquals(at(2026, 10, 1, 7, 12, 31), first.timestamp)
        assertEquals("NBMB MUHAMMAD FACHRI TO BUDI SANTOSO", first.description)

        assertEquals("QRIS KOPI KENANGAN SUNSET DENPASAR ID", s.rows[1].description)
        assertEquals(38_000L, s.rows[1].amount)

        val salary = s.rows[3]
        assertFalse(salary.isDebit)
        assertEquals(8_000_000L, salary.amount)

        assertEquals(12_500L, s.rows[5].amount)
        assertEquals(10_849_500L, s.rows[5].balance)
    }

    /** Only one amount column + saldo: direction comes from the balance change. */
    @Test fun amountAndBalance_usesBalanceDelta() {
        val text = """
            Saldo Awal Rp1.000.000,00
            01 Okt 2026 QRIS WARUNG MAKAN 25.000,00 975.000,00
            02 Okt 2026 TRANSFER MASUK DARI ANI 100.000,00 1.075.000,00
            03 Okt 2026 BRIVA 88810 PLN PREPAID 51.500,00 1.023.500,00
        """.trimIndent()
        val s = StatementParser.parse(text, wib)
        assertEquals(3, s.rows.size)
        assertTrue(s.rows[0].isDebit)
        assertFalse(s.rows[1].isDebit)
        assertTrue(s.rows[2].isDebit)
        assertEquals(51_500L, s.rows[2].amount)
        assertEquals(at(2026, 10, 3), s.rows[2].timestamp)
    }

    @Test fun markersAndSigns() {
        val text = """
            01/10/2026 QRIS ALFAMART 25,000.00 DB 975,000.00
            02/10/2026 BUNGA 1,250.00 CR 976,250.00
            03/10/2026 GOPAY TOPUP -50.000,00 926.250,00
        """.trimIndent()
        val s = StatementParser.parse(text, wib)
        assertEquals(listOf(true, false, true), s.rows.map { it.isDebit })
        assertEquals(listOf(25_000L, 1_250L, 50_000L), s.rows.map { it.amount })
    }

    @Test fun amountsOnLineAfterWrappedDescription() {
        val text = """
            Saldo Awal 200,000.00
            04/10/26 08:00:00 NBMB FACHRI TO
            SITI AMINAH 8888015 75,000.00 0.00 125,000.00
        """.trimIndent()
        val s = StatementParser.parse(text, wib)
        assertEquals(1, s.rows.size)
        assertEquals(75_000L, s.rows[0].amount)
        assertEquals("NBMB FACHRI TO SITI AMINAH", s.rows[0].description)
    }

    @Test fun counterpartyGuess() {
        assertEquals("Budi Santoso", StatementParser.guessCounterparty("NBMB MUHAMMAD FACHRI TO BUDI SANTOSO"))
        assertEquals("Kopi Kenangan Sunset Denpasar", StatementParser.guessCounterparty("QRIS KOPI KENANGAN SUNSET DENPASAR ID"))
    }
}
