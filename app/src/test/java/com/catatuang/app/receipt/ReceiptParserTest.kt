package com.catatuang.app.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReceiptParserTest {

    private val bali = TimeZone.getTimeZone("Asia/Makassar")

    private fun millis(zone: String, y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { clear(); set(y, m - 1, d, h, min, s) }.timeInMillis

    @Test fun brimoTransfer_rowsJoined() {
        val text = """
            Transaksi Berhasil
            03 Nov 2026, 13:39:12 WIB
            No. Ref 001234567890
            Sumber Dana
            MUHAMMAD FACHRI
            BANK BRI
            0123 **** **** 4567
            Tujuan
            BUDI SANTOSO
            BANK BCA
            1234567890
            Jenis Transaksi Transfer Bank Lain
            Catatan bayar kos november
            Nominal Rp1.500.000
            Biaya Admin Rp2.500
            Total Transaksi Rp1.502.500
        """.trimIndent()
        val r = ReceiptParser.parse(text, bali)
        assertEquals(1_502_500L, r.amount)
        assertEquals("BUDI SANTOSO", r.counterparty)
        assertEquals("001234567890", r.reference)
        assertEquals("bayar kos november", r.note)
        assertEquals("BRImo", r.source)
        assertEquals("Transfer", r.kind)
        assertEquals(millis("Asia/Jakarta", 2026, 11, 3, 13, 39, 12), r.timestamp)
    }

    @Test fun brimo_labelsAndValuesOnSeparateLines() {
        val text = """
            Transaksi Berhasil
            Total Transaksi
            Rp 50.000
            03 November 2026
            13:39 WITA
            Penerima
            Warung Bu Sri
            Biaya Admin
            Rp0
        """.trimIndent()
        val r = ReceiptParser.parse(text, bali)
        assertEquals(50_000L, r.amount)
        assertEquals("Warung Bu Sri", r.counterparty)
        assertEquals(millis("Asia/Makassar", 2026, 11, 3, 13, 39), r.timestamp)
        assertNull(r.note)
    }

    @Test fun qrisPayment() {
        val text = """
            Pembayaran QRIS Berhasil
            Nama Merchant: KOPI KENANGAN SUNSET ROAD
            Total Bayar IDR 38,000.00
            Tanggal 02/11/2026 08:15
            ID Transaksi QR9F82KD02
        """.trimIndent()
        val r = ReceiptParser.parse(text, bali)
        assertEquals(38_000L, r.amount)
        assertEquals("KOPI KENANGAN SUNSET ROAD", r.counterparty)
        assertEquals("QR9F82KD02", r.reference)
        assertEquals("QRIS", r.kind)
        assertEquals(millis("Asia/Makassar", 2026, 11, 2, 8, 15), r.timestamp)
    }

    @Test fun danaWallet_notConfusedBySumberDana() {
        val a = ReceiptParser.parse("Sumber Dana\nBANK BRI\nNominal Rp10.000", bali)
        assertEquals("BRImo", a.source)
        val b = ReceiptParser.parse("DANA\nKirim Uang Berhasil\nTotal Rp25.000", bali)
        assertEquals("DANA", b.source)
        assertEquals(25_000L, b.amount)
    }

    @Test fun fallbackToLargestAmount() {
        val r = ReceiptParser.parse("Berhasil!\nRp 12.000\nBiaya Rp 1.000\nRp 13.000", bali)
        assertEquals(13_000L, r.amount)
    }

    @Test fun rupiahFormats() {
        assertEquals(1_250_000L, ReceiptParser.toRupiah("1.250.000,00"))
        assertEquals(50_000L, ReceiptParser.toRupiah("50,000.00"))
        assertEquals(15_000L, ReceiptParser.toRupiah("15.000"))
        assertEquals(500L, ReceiptParser.toRupiah("500"))
        assertNull(ReceiptParser.toRupiah("0"))
    }

    @Test fun joinRows_mergesTwoColumnLayout() {
        val boxes = listOf(
            OcrBox("Nominal", top = 100, bottom = 130, left = 20),
            OcrBox("Rp50.000", top = 102, bottom = 128, left = 400),
            OcrBox("Biaya Admin", top = 150, bottom = 180, left = 20),
            OcrBox("Rp0", top = 151, bottom = 179, left = 420),
        )
        assertEquals("Nominal Rp50.000\nBiaya Admin Rp0", ReceiptParser.joinRows(boxes))
    }
}
