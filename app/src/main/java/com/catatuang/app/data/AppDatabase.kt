package com.catatuang.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Category::class, Txn::class], version = 1, exportSchema = true)
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
                ).build().also { instance = it }
            }
    }
}

/** Starter categories. Hints are what Jev reads to decide where a payment belongs. */
val DEFAULT_CATEGORIES = listOf(
    Category(name = "Makan & Minum", emoji = "🍜", hint = "Food and drinks: restaurants, warung, cafes, kopi, GoFood, GrabFood, ShopeeFood, groceries for meals"),
    Category(name = "Transportasi", emoji = "🛵", hint = "Transport: Gojek, Grab rides, bensin/fuel, parking, toll, KRL, TransJakarta, tickets"),
    Category(name = "Belanja", emoji = "🛍️", hint = "Shopping: Tokopedia, Shopee, Lazada, clothes, electronics, household items, Indomaret, Alfamart"),
    Category(name = "Tagihan", emoji = "💡", hint = "Bills and utilities: PLN listrik, token, PDAM water, internet, IndiHome, pulsa, paket data, BPJS, rent/kos, subscriptions"),
    Category(name = "Kirim ke Keluarga/Teman", emoji = "👨‍👩‍👧", hint = "Money sent to a person: transfer to family, friends, splitting bills, arisan, gifts of money"),
    Category(name = "Kesehatan", emoji = "💊", hint = "Health: apotek, pharmacy, clinic, doctor, hospital, Halodoc"),
    Category(name = "Hiburan", emoji = "🎬", hint = "Entertainment: movies, games, top-up game, streaming, concerts, hobbies, travel"),
    Category(name = "Pendidikan", emoji = "📚", hint = "Education: school fees, SPP, courses, books, campus payments"),
    Category(name = "Lainnya", emoji = "📦", hint = "Anything that does not clearly fit the other categories"),
)
