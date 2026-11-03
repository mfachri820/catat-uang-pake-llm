package com.catatuang.app.data

import android.content.Context
import com.catatuang.app.widget.SpendingWidget
import java.util.Calendar

class Repository(private val context: Context, db: AppDatabase) {
    val categories = db.categoryDao()
    val txns = db.txnDao()

    suspend fun seedIfEmpty() {
        if (categories.count() == 0) categories.insertAll(DEFAULT_CATEGORIES)
    }

    suspend fun save(txn: Txn): Long {
        val id = if (txn.id == 0L) txns.insert(txn) else txn.id.also { txns.update(txn) }
        refreshWidget()
        return id
    }

    suspend fun saveAll(list: List<Txn>) {
        txns.insertAll(list)
        refreshWidget()
    }

    /** Find a category by name, creating it if needed. Returns its id. */
    suspend fun ensureCategory(name: String, emoji: String, hint: String): Long =
        categories.byName(name)?.id ?: categories.insert(Category(name = name, emoji = emoji, hint = hint))

    suspend fun delete(id: Long) {
        txns.delete(id)
        refreshWidget()
    }

    suspend fun refreshWidget() {
        runCatching { SpendingWidget.refresh(context) }
    }
}

/** [start, end) of the month containing [millis], plus an offset in months. */
fun monthRange(millis: Long = System.currentTimeMillis(), offset: Int = 0): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offset)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}

fun dayRange(millis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    return start to cal.timeInMillis
}
