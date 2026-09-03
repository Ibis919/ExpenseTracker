package com.ibis.expense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ibis.expense.ui.formatAmount

object BudgetNotifier {
    private const val CHANNEL_ID = "budget_alerts"
    private const val ID_NEAR = 1001
    private const val ID_OVER = 1002

    fun notifyNearLimit(context: Context, spentCents: Long, budgetCents: Long) {
        show(
            context,
            id = ID_NEAR,
            title = "预算提醒",
            text = "本月已花 ¥${formatAmount(spentCents)}，达到预算的 80%，注意控制支出"
        )
    }

    fun notifyOverBudget(context: Context, spentCents: Long, budgetCents: Long) {
        show(
            context,
            id = ID_OVER,
            title = "已超支",
            text = "本月已花 ¥${formatAmount(spentCents)}，超出预算 ¥${formatAmount(spentCents - budgetCents)}，接下来少花点"
        )
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
