package com.catatuang.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAll(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

private const val TXN_WITH_CATEGORY = """
    SELECT t.id, t.amount, t.categoryId, t.counterparty, t.note, t.timestamp, t.source,
           t.reference, t.categorisedBy, c.name AS categoryName, c.emoji AS categoryEmoji
    FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
"""

@Dao
interface TxnDao {
    @Query("$TXN_WITH_CATEGORY WHERE t.timestamp >= :from AND t.timestamp < :to ORDER BY t.timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TxnWithCategory>>

    @Query(
        """
        SELECT t.categoryId AS categoryId, c.name AS name, c.emoji AS emoji, SUM(t.amount) AS total
        FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.timestamp >= :from AND t.timestamp < :to
        GROUP BY t.categoryId ORDER BY total DESC
        """,
    )
    fun observeTotalsBetween(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT t.categoryId AS categoryId, c.name AS name, c.emoji AS emoji, SUM(t.amount) AS total
        FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.timestamp >= :from AND t.timestamp < :to
        GROUP BY t.categoryId ORDER BY total DESC
        """,
    )
    suspend fun totalsBetween(from: Long, to: Long): List<CategoryTotal>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= :from AND timestamp < :to")
    suspend fun sumBetween(from: Long, to: Long): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): Txn?

    /** Most recent category used for this recipient — lets us skip the AI for repeat payees. */
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE LOWER(counterparty) = LOWER(:counterparty) AND categoryId IS NOT NULL
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    suspend fun lastCategoryFor(counterparty: String): Long?

    @Insert
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
}
