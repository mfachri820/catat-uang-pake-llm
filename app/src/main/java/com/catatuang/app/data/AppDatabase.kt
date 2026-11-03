package com.catatuang.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Txn::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "catat-uang.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * v1 → v2: income & investing. Adds categories.kind and transactions.type (existing rows are
         * expenses), swaps emojis for drawn icon keys, and adds the new income/investment categories.
         * Done with ALTER/UPDATE only, so no table is rebuilt and no data is touched otherwise.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'expense'")
                val byName = LEGACY_ICONS.entries.joinToString(" ") { (name, icon) ->
                    "WHEN '${name.replace("'", "''")}' THEN '$icon'"
                }
                db.execSQL("UPDATE categories SET emoji = CASE name $byName ELSE 'tag' END")
                DEFAULT_CATEGORIES.filter { it.kind != TxnType.EXPENSE }.forEach { c ->
                    db.insert(
                        "categories", SQLiteDatabase.CONFLICT_IGNORE,
                        ContentValues().apply {
                            put("name", c.name); put("emoji", c.icon); put("hint", c.hint); put("kind", c.kind)
                        },
                    )
                }
            }
        }

        /** v1 category names (defaults and rule-created ones) → their new icons. */
        private val LEGACY_ICONS = mapOf(
            "Makan & Minum" to "food", "Transportasi" to "transport", "Belanja" to "shopping",
            "Tagihan" to "bills", "Kirim ke Keluarga/Teman" to "family", "Kesehatan" to "health",
            "Hiburan" to "fun", "Pendidikan" to "education", "Lainnya" to "tag",
            "Tarik Tunai" to "cash", "Biaya Bank" to "bank", "Top Up E-Wallet" to "wallet",
        )
    }
}

/** Starter categories. Hints are what Jev reads to decide where a transaction belongs. */
val DEFAULT_CATEGORIES = listOf(
    // Spending
    Category(name = "Makan & Minum", icon = "food", hint = "Food and drinks: restaurants, warung, cafes, kopi, GoFood, GrabFood, ShopeeFood, groceries for meals"),
    Category(name = "Transportasi", icon = "transport", hint = "Transport: Gojek, Grab rides, bensin/fuel, parking, toll, KRL, TransJakarta, tickets"),
    Category(name = "Belanja", icon = "shopping", hint = "Shopping: Tokopedia, Shopee, Lazada, clothes, electronics, household items, Indomaret, Alfamart"),
    Category(name = "Tagihan", icon = "bills", hint = "Bills and utilities: PLN listrik, token, PDAM water, internet, IndiHome, pulsa, paket data, BPJS, rent/kos, subscriptions"),
    Category(name = "Kirim ke Keluarga/Teman", icon = "family", hint = "Money sent to a person: transfer to family, friends, splitting bills, arisan, gifts of money"),
    Category(name = "Kesehatan", icon = "health", hint = "Health: apotek, pharmacy, clinic, doctor, hospital, Halodoc"),
    Category(name = "Hiburan", icon = "fun", hint = "Entertainment: movies, games, top-up game, streaming, concerts, hobbies, travel"),
    Category(name = "Pendidikan", icon = "education", hint = "Education: school fees, SPP, courses, books, campus payments"),
    Category(name = "Lainnya", icon = "tag", hint = "Spending that does not clearly fit the other categories"),
    // Income
    Category(name = "Gaji", icon = "income", kind = TxnType.INCOME, hint = "Salary from an employer: payroll, gaji, THR, bonus, tunjangan"),
    Category(name = "Usaha & Freelance", icon = "business", kind = TxnType.INCOME, hint = "Business or freelance income: sales, client payments, projects, side jobs, online shop payouts"),
    Category(name = "Kiriman", icon = "gift", kind = TxnType.INCOME, hint = "Money received from family or friends: allowance, gifts, angpao, someone paying you back"),
    Category(name = "Bunga & Cashback", icon = "interest", kind = TxnType.INCOME, hint = "Bank interest (bunga, jasa giro), cashback, refunds, promos, rewards"),
    Category(name = "Hasil Investasi", icon = "growth", kind = TxnType.INCOME, hint = "Money back from investments: selling stocks or funds, dividends, redemptions, matured deposito"),
    Category(name = "Pemasukan Lain", icon = "tag", kind = TxnType.INCOME, hint = "Incoming money that does not fit the other income categories"),
    // Investing
    Category(name = "Reksa Dana", icon = "fund", kind = TxnType.INVEST, hint = "Mutual funds and bonds: Bibit, Bareksa, Tanamduit, reksa dana, SBN, ORI, SR, sukuk"),
    Category(name = "Saham", icon = "stock", kind = TxnType.INVEST, hint = "Stocks: Stockbit, Ajaib, IPOT, Mirae, BIONS, top-up to RDN (rekening dana nasabah)"),
    Category(name = "Emas", icon = "gold", kind = TxnType.INVEST, hint = "Gold: Pegadaian tabungan emas, Pluang emas, Tring, Antam, Treasury, buying gold"),
    Category(name = "Crypto", icon = "crypto", kind = TxnType.INVEST, hint = "Crypto: Pintu, Indodax, Tokocrypto, Reku, Bitcoin, Ethereum"),
    Category(name = "Tabungan & Deposito", icon = "deposit", kind = TxnType.INVEST, hint = "Putting money aside: deposito, savings goals, moving money to your own savings account"),
)
