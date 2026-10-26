package com.techmare.pricewatch

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmare.pricewatch.core.Compare
import com.techmare.pricewatch.core.Position
import com.techmare.pricewatch.data.Prefs
import com.techmare.pricewatch.data.Repository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Each morning: download the new results and say what changed for your products. */
class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val prefs = Prefs(applicationContext)
        when (repo.refresh()) {
            Repository.Fetch.OFFLINE -> return Result.retry()
            Repository.Fetch.NOT_READY -> return Result.success()
            Repository.Fetch.OK -> {}
        }
        val snap = repo.snapshot() ?: return Result.success()
        if (snap.generated == prefs.lastNotified) return Result.success()

        val comps = Compare.run(snap, prefs.overrides, prefs.excluded, prefs.threshold.toDouble())
        val matched = comps.flatMap { c -> c.offers.map { it.url } }.toSet()
        val changes = repo.changes().filter { it.url in matched }
        val drops = changes.count { it.type == "drop" }
        val outs = changes.count { it.type == "out" }
        val cheaper = comps.count { it.position == Position.CHEAPER_ELSEWHERE }

        val parts = buildList {
            if (drops > 0) add("$drops price drop${if (drops > 1) "s" else ""} at competitors")
            if (outs > 0) add("$outs out of stock elsewhere")
        }
        if (parts.isNotEmpty() || cheaper > 0) {
            notify(
                applicationContext,
                title = if (cheaper > 0) "$cheaper of your products are cheaper elsewhere" else "Competitor prices changed",
                text = parts.joinToString(", ").ifEmpty { "Open Price Watch to see where." },
            )
        }
        prefs.lastNotified = snap.generated
        return Result.success()
    }

    companion object {
        private const val WORK = "morning-prices"
        private const val CHANNEL = "prices"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(WORK); return }
            // first run at 9:00 local time, then daily
            val now = LocalDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<PriceWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        @android.annotation.SuppressLint("MissingPermission")
        fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Price changes", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, n)
        }
    }
}
