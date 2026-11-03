package com.catatuang.app.statement

import com.catatuang.app.data.TxnType

/** A category the rules can assign; created on first use if the user doesn't have it. */
data class RuleCategory(val name: String, val icon: String, val hint: String, val kind: String)

/**
 * Statement lines whose category is certain from the bank's own wording. These skip Jev entirely.
 * Everything else goes to payee history, then Jev.
 */
object StatementRules {
    // ---- money out: spending
    val CASH = RuleCategory("Tarik Tunai", "cash", "Cash withdrawn from an ATM or teller. What the cash was spent on isn't known.", TxnType.EXPENSE)
    val BANK_FEES = RuleCategory("Biaya Bank", "bank", "Bank charges: monthly admin fee, card fee, transfer fee, tax on interest, stamp duty", TxnType.EXPENSE)
    val TOP_UP = RuleCategory("Top Up E-Wallet", "wallet", "Moving money into your own e-wallet (DANA, GoPay, OVO, ShopeePay, LinkAja). Spending happens later from the wallet.", TxnType.EXPENSE)

    // ---- money out: investing (names match the default investment categories)
    val FUNDS = RuleCategory("Reksa Dana", "fund", "Mutual funds and bonds: Bibit, Bareksa, Tanamduit, reksa dana, SBN, ORI, SR, sukuk", TxnType.INVEST)
    val STOCKS = RuleCategory("Saham", "stock", "Stocks: Stockbit, Ajaib, IPOT, Mirae, BIONS, top-up to RDN (rekening dana nasabah)", TxnType.INVEST)
    val GOLD = RuleCategory("Emas", "gold", "Gold: Pegadaian tabungan emas, Pluang emas, Tring, Antam, Treasury, buying gold", TxnType.INVEST)
    val CRYPTO = RuleCategory("Crypto", "crypto", "Crypto: Pintu, Indodax, Tokocrypto, Reku, Bitcoin, Ethereum", TxnType.INVEST)

    // ---- money in
    val SALARY = RuleCategory("Gaji", "income", "Salary from an employer: payroll, gaji, THR, bonus, tunjangan", TxnType.INCOME)
    val INTEREST = RuleCategory("Bunga & Cashback", "interest", "Bank interest (bunga, jasa giro), cashback, refunds, promos, rewards", TxnType.INCOME)
    val RETURNS = RuleCategory("Hasil Investasi", "growth", "Money back from investments: selling stocks or funds, dividends, redemptions, matured deposito", TxnType.INCOME)

    private val cash = Regex("\\b(TARIK ?TUNAI|TARIKAN ATM|TRK ?TUNAI|PENARIKAN|CASH ?WITHDRAWAL|TTN)\\b")
    private val fees = Regex("\\b(BIAYA ?ADM\\w*|ADM BULANAN|BIAYA ?KARTU|BIAYA ?TRANSFER|BIAYA ?TRX|PAJAK|BEA ?METERAI|ADMIN FEE|BIAYA)\\b")
    private val wallet = Regex("\\b(DANA|GOPAY|GO-PAY|OVO|SHOPEEPAY|SHOPEE ?PAY|LINKAJA|ISAKU|DOKU)\\b")
    private val topUp = Regex("\\b(TOP ?UP|TOPUP|ISI ?SALDO|DOMPET)\\b")

    private val crypto = Regex("\\b(PINTU|INDODAX|TOKOCRYPTO|REKU|TRIV|ZIPMEX|CRYPTO|KRIPTO)\\b")
    private val gold = Regex("\\b(TABUNGAN EMAS|TAB EMAS|EMAS|GOLD|TRING|TREASURY|ANTAM|LAKUEMAS)\\b")
    private val funds = Regex("\\b(BIBIT|BAREKSA|TANAMDUIT|REKSA ?DANA|SBN|ORI0\\d+|SR0\\d+|SUKUK)\\b")
    private val stocks = Regex("\\b(STOCKBIT|AJAIB|IPOT|INDO ?PREMIER|MIRAE|BIONS|RDN|SEKURITAS|KSEI|SAHAM)\\b")

    private val salary = Regex("\\b(GAJI|PAYROLL|SALARY|THR|TUNJANGAN)\\b")
    private val interest = Regex("\\b(BUNGA|INTEREST|JASA ?GIRO|CASHBACK|REFUND|PENGEMBALIAN|REVERSAL|KOREKSI)\\b")
    private val dividend = Regex("\\b(DIVIDEN|DIVIDEND|REDEMPTION)\\b")

    private fun investment(d: String): RuleCategory? = when {
        crypto.containsMatchIn(d) -> CRYPTO
        funds.containsMatchIn(d) -> FUNDS
        stocks.containsMatchIn(d) -> STOCKS
        gold.containsMatchIn(d) -> GOLD
        else -> null
    }

    /** Money out: spending rules first (cash, fees, top-ups), then investment platforms. */
    fun matchOutgoing(description: String): RuleCategory? {
        val d = description.uppercase()
        return when {
            cash.containsMatchIn(d) -> CASH
            fees.containsMatchIn(d) && d.length < 60 -> BANK_FEES
            wallet.containsMatchIn(d) && (topUp.containsMatchIn(d) || d.startsWith("BRIVA")) -> TOP_UP
            else -> investment(d)
        }
    }

    /** Money in: salary, interest/cashback, or money coming back from an investment platform. */
    fun matchIncoming(description: String): RuleCategory? {
        val d = description.uppercase()
        return when {
            salary.containsMatchIn(d) -> SALARY
            dividend.containsMatchIn(d) || investment(d) != null -> RETURNS
            interest.containsMatchIn(d) -> INTEREST
            else -> null
        }
    }
}
