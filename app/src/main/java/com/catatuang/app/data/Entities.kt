package com.catatuang.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which way money moved. Stored as text in the database. */
object TxnType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val INVEST = "invest"

    val ALL = listOf(EXPENSE, INCOME, INVEST)
    /** Kinds a money-out line can be (spending or moving money into investments). */
    val OUTGOING = listOf(EXPENSE, INVEST)
}

/**
 * A category. [hint] is a plain-language description that is sent to Jev so it knows what
 * belongs here (e.g. "GoFood, GrabFood, warung, kopi"). [kind] is one of [TxnType].
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Key of a drawn icon, see CategoryIcons. (Column kept from v1, where it held an emoji.) */
    @ColumnInfo(name = "emoji") val icon: String,
    val hint: String,
    val kind: String = TxnType.EXPENSE,
)

/** One money movement. Amounts are whole rupiah, always positive; [type] says the direction. */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("timestamp"), Index("counterparty")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val categoryId: Long?,
    /** Who the money went to (or came from): recipient, merchant, sender. */
    val counterparty: String,
    val note: String,
    /** Epoch millis. */
    val timestamp: Long,
    /** e.g. "BRImo", "DANA", "Rekening koran", "Manual". */
    val source: String,
    /** Bank reference number, used to skip duplicate imports of the same receipt. */
    val reference: String? = null,
    /** How this was categorised: "manual", "jev", "history", "rule". */
    val categorisedBy: String = "manual",
    val type: String = TxnType.EXPENSE,
)

data class TxnWithCategory(
    val id: Long,
    val amount: Long,
    val categoryId: Long?,
    val counterparty: String,
    val note: String,
    val timestamp: Long,
    val source: String,
    val reference: String?,
    val categorisedBy: String,
    val type: String,
    val categoryName: String?,
    val categoryIcon: String?,
)

data class CategoryTotal(
    val categoryId: Long?,
    val name: String?,
    val icon: String?,
    val total: Long,
    val count: Int,
)

data class TypeTotal(val type: String, val total: Long)
