package com.catatuang.app.statement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatementRulesTest {
    @Test fun outgoing() {
        assertEquals(StatementRules.CASH, StatementRules.matchOutgoing("TARIK TUNAI ATM BRI"))
        assertEquals(StatementRules.BANK_FEES, StatementRules.matchOutgoing("BIAYA ADM BULANAN"))
        assertEquals(StatementRules.TOP_UP, StatementRules.matchOutgoing("TOPUP DANA 0812xxxx"))
        assertEquals(StatementRules.FUNDS, StatementRules.matchOutgoing("BRIVA 12345 BIBIT TUMBUH BERSAMA"))
        assertEquals(StatementRules.STOCKS, StatementRules.matchOutgoing("NBMB FACHRI TO RDN STOCKBIT SEKURITAS"))
        assertEquals(StatementRules.CRYPTO, StatementRules.matchOutgoing("BRIVA 9988 PINTU KEMANA SAJA"))
        assertEquals(StatementRules.GOLD, StatementRules.matchOutgoing("PEGADAIAN TABUNGAN EMAS"))
        assertNull(StatementRules.matchOutgoing("QRIS KOPI KENANGAN"))
        assertNull(StatementRules.matchOutgoing("NBMB FACHRI TO GOLDEN BAKERY"))
    }

    @Test fun incoming() {
        assertEquals(StatementRules.SALARY, StatementRules.matchIncoming("TRANSFER PAYROLL PT MAJU JAYA"))
        assertEquals(StatementRules.INTEREST, StatementRules.matchIncoming("BUNGA"))
        assertEquals(StatementRules.RETURNS, StatementRules.matchIncoming("BIFAST DARI BIBIT TUMBUH BERSAMA"))
        assertNull(StatementRules.matchIncoming("NBMB ANI LESTARI TO MUHAMMAD FACHRI"))
    }
}
