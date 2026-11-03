package com.catatuang.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spending category. [hint] is a plain-language description that is sent to Jev
 * so it knows what belongs here (e.g. "GoFood, GrabFood, warung, kopi").
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val hint: String,
)

/** One expense. Amounts are whole rupiah. */
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
    /** Who the money went to: recipient name or merchant. */
    val counterparty: String,
    val note: String,
    /** Epoch millis. */
    val timestamp: Long,
    /** e.g. "BRImo", "DANA", "Cash". */
    val source: String,
    /** Bank reference number, used to skip duplicate imports of the same receipt. */
    val reference: String? = null,
    /** How this was categorised: "manual", "jev", "history". */
    val categorisedBy: String = "manual",
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
    val categoryName: String?,
    val categoryEmoji: String?,
)

data class CategoryTotal(
    val categoryId: Long?,
    val name: String?,
    val emoji: String?,
    val total: Long,
)
