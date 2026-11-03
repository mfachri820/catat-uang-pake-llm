package com.catatuang.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.catatuang.app.R

/**
 * Publishes a "sharing shortcut" so Catat Uang can appear in the top Direct Share row of the
 * Android share sheet (Android 10+), not only in the app list further down.
 * Ranking is up to Android; using it from the share sheet makes it climb.
 */
object ShareShortcut {
    private const val ID = "catat_receipt"
    const val CATEGORY = "com.catatuang.app.category.RECEIPT"

    fun publish(context: Context) {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, ID)
                .setShortLabel("Catat Uang")
                .setLongLabel("Catat pengeluaran")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setCategories(setOf(CATEGORY))
                .setLongLived(true)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}
