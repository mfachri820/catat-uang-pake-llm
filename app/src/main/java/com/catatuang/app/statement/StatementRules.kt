package com.catatuang.app.statement

/** A category the rules can assign; created on first use if the user doesn't have it. */
data class RuleCategory(val name: String, val emoji: String, val hint: String)

/**
 * Statement lines whose category is certain from the bank's own wording. These skip Jev entirely.
 * Everything else goes to payee history, then Jev.
 */
object StatementRules {
    val CASH = RuleCategory(
        "Tarik Tunai", "💵",
        "Cash withdrawn from an ATM or teller. What the cash was spent on isn't known.",
    )
    val BANK_FEES = RuleCategory(
        "Biaya Bank", "🏦",
        "Bank charges: monthly admin fee, card fee, transfer fee, tax on interest, stamp duty",
    )
    val TOP_UP = RuleCategory(
        "Top Up E-Wallet", "👛",
        "Moving money into your own e-wallet (DANA, GoPay, OVO, ShopeePay, LinkAja). Spending happens later from the wallet.",
    )

    private val cash = Regex("\\b(TARIK ?TUNAI|TARIKAN ATM|TRK ?TUNAI|PENARIKAN|CASH ?WITHDRAWAL|TTN)\\b")
    private val fees = Regex(
        "\\b(BIAYA ?ADM\\w*|ADM BULANAN|BIAYA ?KARTU|BIAYA ?TRANSFER|BIAYA ?TRX|PAJAK|BEA ?METERAI|ADMIN FEE|BIAYA)\\b",
    )
    private val wallet = Regex("\\b(DANA|GOPAY|GO-PAY|OVO|SHOPEEPAY|SHOPEE ?PAY|LINKAJA|ISAKU|DOKU)\\b")
    private val topUp = Regex("\\b(TOP ?UP|TOPUP|ISI ?SALDO|DOMPET)\\b")

    fun match(description: String): RuleCategory? {
        val d = description.uppercase()
        return when {
            cash.containsMatchIn(d) -> CASH
            fees.containsMatchIn(d) && d.length < 60 -> BANK_FEES
            wallet.containsMatchIn(d) && (topUp.containsMatchIn(d) || d.startsWith("BRIVA")) -> TOP_UP
            else -> null
        }
    }
}
