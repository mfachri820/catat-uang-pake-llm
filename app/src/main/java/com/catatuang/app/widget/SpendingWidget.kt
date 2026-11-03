package com.catatuang.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.catatuang.app.CatatUangApp
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.dayRange
import com.catatuang.app.data.monthRange
import com.catatuang.app.ui.MainActivity
import com.catatuang.app.ui.formatMonth
import com.catatuang.app.ui.rupiah
import com.catatuang.app.ui.rupiahShort
import kotlinx.coroutines.flow.map

/** Home screen widget: this month's total, budget bar, today's spend and top categories. */
class SpendingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CatatUangApp
        val dao = app.repository.txns
        val (from, to) = monthRange()
        val (dFrom, dTo) = dayRange()
        // Load once up front so the first frame has data…
        val initialTotals = dao.totalsBetween(from, to)
        val initialToday = dao.sumBetween(dFrom, dTo)
        val budget = app.settings.monthlyBudget
        val monthLabel = formatMonth(from)

        provideContent {
            // …then keep following the database while the widget session is alive.
            val totals by remember { dao.observeTotalsBetween(from, to) }.collectAsState(initialTotals)
            val today by remember {
                dao.observeBetween(dFrom, dTo).map { list -> list.sumOf { it.amount } }
            }.collectAsState(initialToday)
            GlanceTheme { Content(monthLabel, totals, today, budget) }
        }
    }

    @Composable
    private fun Content(month: String, totals: List<CategoryTotal>, today: Long, budget: Long) {
        val context = LocalContext.current
        val size = LocalSize.current
        val total = totals.sumOf { it.total }
        // Distinct actions so the two PendingIntents don't collapse into one.
        val open = actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN))
        val add = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_ADD)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        val c = GlanceTheme.colors

        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(c.primaryContainer)
                .padding(14.dp).clickable(open),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month,
                    style = TextStyle(color = c.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    GlanceModifier.size(30.dp).cornerRadius(15.dp).background(c.primary).clickable(add),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = c.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
            Text(
                if (size.width < MEDIUM.width) rupiahShort(total) else rupiah(total),
                style = TextStyle(color = c.onPrimaryContainer, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (budget > 0) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (total.toFloat() / budget).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = if (total > budget) c.error else c.primary,
                    backgroundColor = c.surface,
                )
                Spacer(GlanceModifier.height(4.dp))
                val left = budget - total
                Text(
                    if (left >= 0) "${rupiahShort(left)} left" else "${rupiahShort(-left)} over",
                    style = TextStyle(color = c.onPrimaryContainer, fontSize = 11.sp),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text("Today ${rupiah(today)}", style = TextStyle(color = c.onPrimaryContainer, fontSize = 12.sp))

            if (size.height >= LARGE.height && totals.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                totals.take(3).forEach { t ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            "${t.emoji ?: "❔"} ${t.name ?: "Uncategorised"}",
                            style = TextStyle(color = c.onPrimaryContainer, fontSize = 12.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(rupiahShort(t.total), style = TextStyle(color = c.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 70.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE = DpSize(180.dp, 180.dp)

        suspend fun refresh(context: Context) = SpendingWidget().updateAll(context)
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
