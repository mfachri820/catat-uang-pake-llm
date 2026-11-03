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

    @Query("SELECT * FROM categories WHERE kind IN (:kinds) ORDER BY name")
    suspend fun getByKinds(kinds: List<String>): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun byName(name: String): Category?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

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
           t.reference, t.categorisedBy, t.type, c.name AS categoryName, c.emoji AS categoryIcon
    FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
"""

private const val CATEGORY_TOTALS = """
    SELECT t.categoryId AS categoryId, c.name AS name, c.emoji AS icon,
           SUM(t.amount) AS total, COUNT(*) AS count
    FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId
    WHERE t.timestamp >= :from AND t.timestamp < :to AND t.type = :type
    GROUP BY t.categoryId ORDER BY total DESC
"""

@Dao
interface TxnDao {
    @Query("$TXN_WITH_CATEGORY WHERE t.timestamp >= :from AND t.timestamp < :to ORDER BY t.timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TxnWithCategory>>

    /** Per-category totals for one [type] (expense, income or invest). */
    @Query(CATEGORY_TOTALS)
    fun observeTotalsBetween(from: Long, to: Long, type: String): Flow<List<CategoryTotal>>

    @Query(CATEGORY_TOTALS)
    suspend fun totalsBetween(from: Long, to: Long, type: String): List<CategoryTotal>

    @Query("SELECT type, SUM(amount) AS total FROM transactions WHERE timestamp >= :from AND timestamp < :to GROUP BY type")
    fun observeTypeTotals(from: Long, to: Long): Flow<List<TypeTotal>>

    @Query("SELECT type, SUM(amount) AS total FROM transactions WHERE timestamp >= :from AND timestamp < :to GROUP BY type")
    suspend fun typeTotals(from: Long, to: Long): List<TypeTotal>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= :from AND timestamp < :to AND type = :type")
    fun observeSum(from: Long, to: Long, type: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE timestamp >= :from AND timestamp < :to AND type = :type")
    suspend fun sum(from: Long, to: Long, type: String): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): Txn?

    /** Most recent category used with this person/merchant in one of [types] — lets us skip the AI. */
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE LOWER(counterparty) = LOWER(:counterparty) AND categoryId IS NOT NULL AND type IN (:types)
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    suspend fun lastCategoryFor(counterparty: String, types: List<String>): Long?

    /** An existing entry with the same amount and direction around the same time — likely the same payment. */
    @Query("SELECT * FROM transactions WHERE amount = :amount AND type IN (:types) AND timestamp BETWEEN :from AND :to LIMIT 1")
    suspend fun findSimilar(amount: Long, types: List<String>, from: Long, to: Long): Txn?

    @Insert
    suspend fun insertAll(txns: List<Txn>)

    @Insert
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Txn>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
