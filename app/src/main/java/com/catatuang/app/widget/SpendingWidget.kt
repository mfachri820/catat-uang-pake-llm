package com.catatuang.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.color.ColorProvider
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.catatuang.app.CatatUangApp
import com.catatuang.app.R
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.TxnType
import com.catatuang.app.data.TypeTotal
import com.catatuang.app.data.monthRange
import com.catatuang.app.ui.CategoryIcons
import com.catatuang.app.ui.MainActivity
import com.catatuang.app.ui.formatMonth
import com.catatuang.app.ui.rupiah
import com.catatuang.app.ui.rupiahShort
import java.util.Calendar

/**
 * Home screen widget. Shows what you've spent this month against your budget, what that leaves
 * per day, money in and invested, and the top categories. Tapping it opens the details dashboard.
 */
class SpendingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CatatUangApp
        val dao = app.repository.txns
        val (from, to) = monthRange()
        // Load once up front so the first frame has data…
        val initialTypes = dao.typeTotals(from, to)
        val initialTop = dao.totalsBetween(from, to, TxnType.EXPENSE)
        val budget = app.settings.monthlyBudget
        val monthLabel = formatMonth(from)

        provideContent {
            // …then keep following the database while the widget session is alive.
            val types by remember { dao.observeTypeTotals(from, to) }.collectAsState(initialTypes)
            val top by remember { dao.observeTotalsBetween(from, to, TxnType.EXPENSE) }.collectAsState(initialTop)
            GlanceTheme { Content(monthLabel, types, top, budget) }
        }
    }

    @Composable
    private fun Content(month: String, types: List<TypeTotal>, top: List<CategoryTotal>, budget: Long) {
        val context = LocalContext.current
        val size = LocalSize.current
        val byType = types.associate { it.type to it.total }
        val spent = byType[TxnType.EXPENSE] ?: 0
        val income = byType[TxnType.INCOME] ?: 0
        val invested = byType[TxnType.INVEST] ?: 0
        val compact = size.width < MEDIUM.width || size.height < MEDIUM.height

        val details = activity(context, MainActivity.ACTION_DETAILS)
        val add = activity(context, MainActivity.ACTION_ADD)
        val c = GlanceTheme.colors
        val incomeColor = ColorProvider(day = Color(0xFF0B6E4F), night = Color(0xFF7FD6B4))
        val investColor = ColorProvider(day = Color(0xFF3A4FB8), night = Color(0xFFA9B8FF))
        val muted = c.onSurfaceVariant

        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(c.surface).padding(14.dp).clickable(details),
        ) {
            // Header: app mark, month, quick add
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(R.drawable.ic_cat_bills), contentDescription = null,
                    modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(c.primary),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (compact) "Spent" else "Spent in $month",
                    style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    GlanceModifier.size(30.dp).cornerRadius(15.dp).background(c.primary).clickable(add),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = c.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }

            Text(
                if (compact) rupiahShort(spent) else rupiah(spent),
                style = TextStyle(color = c.onSurface, fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )

            if (budget > 0) {
                val left = budget - spent
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (spent.toFloat() / budget).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                    color = if (left < 0) c.error else c.primary,
                    backgroundColor = c.secondaryContainer,
                )
                Spacer(GlanceModifier.height(4.dp))
                val daysLeft = daysLeftInMonth()
                Text(
                    when {
                        left < 0 -> "${rupiahShort(-left)} over budget"
                        compact -> "${rupiahShort(left)} left"
                        else -> "${rupiahShort(left)} left · ≈ ${rupiahShort(left / daysLeft)}/day for $daysLeft days"
                    },
                    style = TextStyle(color = if (left < 0) c.error else muted, fontSize = 12.sp),
                    maxLines = 1,
                )
            }

            if (!compact) {
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("In", "+" + rupiahShort(income), incomeColor, R.drawable.ic_cat_income, GlanceModifier.defaultWeight())
                    Stat("Invested", rupiahShort(invested), investColor, R.drawable.ic_cat_growth, GlanceModifier.defaultWeight())
                }
            }

            if (size.height >= LARGE.height && top.isNotEmpty()) {
                Spacer(GlanceModifier.height(10.dp))
                Text("Top spending", style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                val max = top.first().total.coerceAtLeast(1)
                top.take(3).forEach { t ->
                    Column(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                ImageProvider(CategoryIcons.res(t.icon)), contentDescription = null,
                                modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(c.onSurface),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                t.name ?: "Uncategorised",
                                style = TextStyle(color = c.onSurface, fontSize = 12.sp),
                                maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                            )
                            Text(rupiahShort(t.total), style = TextStyle(color = c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        }
                        Spacer(GlanceModifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = t.total.toFloat() / max,
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                            color = c.tertiary,
                            backgroundColor = c.secondaryContainer,
                        )
                    }
                }
            }

            if (!compact) {
                Spacer(GlanceModifier.defaultWeight())
                Text("Tap for details ›", style = TextStyle(color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String, color: androidx.glance.unit.ColorProvider, icon: Int, modifier: GlanceModifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(color))
            Spacer(GlanceModifier.width(4.dp))
            Column {
                Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                Text(value, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }

    private fun activity(context: Context, action: String): Action =
        // Distinct actions so the PendingIntents don't collapse into one.
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

    private fun daysLeftInMonth(): Int {
        val cal = Calendar.getInstance()
        return (cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
    }

    companion object {
        private val SMALL = DpSize(110.dp, 70.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE = DpSize(180.dp, 200.dp)

        suspend fun refresh(context: Context) = SpendingWidget().updateAll(context)
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
