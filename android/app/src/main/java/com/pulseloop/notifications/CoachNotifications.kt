package com.pulseloop.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.pulseloop.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Ported from CoachNotificationGenerator in the iOS app.
 * Daily AI check-in notifications via WorkManager periodic task.
 */
object CoachNotifications {
    private const val CHANNEL_ID = "coach_checkins"
    private const val DAILY_WORK_NAME = "coach_daily_checkin"
    private const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Coach Check-ins", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily AI-generated health insights based on your ring data"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** Schedule a daily check-in notification. Requires POST_NOTIFICATIONS on Android 13+. */
    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = PeriodicWorkRequestBuilder<CoachNotificationWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
    }

    /** Show an immediate check-in notification. */
    fun showNow(context: Context, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/**
 * WorkManager worker for generating daily coach check-in notifications.
 */
class CoachNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Phase 7: basic notification — full AI-generated content in future iteration
        CoachNotifications.showNow(
            applicationContext,
            "PulseLoop Coach",
            "Good morning! You slept 7h 23m last night. Your recovery looks good — ready for today?",
        )
        return Result.success()
    }
}
