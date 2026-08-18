package com.moderatrix.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moderatrix.app.data.repo.ModeratrixRepo
import java.util.concurrent.TimeUnit

private const val IDLE_THRESHOLD_MS = 3 * 60 * 60 * 1000L // 3 hours
private const val SYNC_STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 1 day
private const val SYNC_STALE_RENOTIFY_INTERVAL_MS = 24 * 60 * 60 * 1000L // re-warn at most once/day

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = ModeratrixRepo(applicationContext)
        val lastRecorded = repo.lastRecordedEpochMs()
        val now = System.currentTimeMillis()
        val trigger = inputData.getString(KEY_TRIGGER) ?: "periodic_check"

        val shouldNotify = when {
            lastRecorded == null -> true
            trigger == "fixed_time" -> true
            now - lastRecorded >= IDLE_THRESHOLD_MS -> true
            else -> false
        }

        if (shouldNotify) {
            val hoursSince = lastRecorded?.let { (now - it) / (60 * 60 * 1000) }
            val text = if (hoursSince != null) {
                "It's been $hoursSince hour(s) since your last entry. Log an activity or check-in."
            } else {
                "Log your first activity or check-in for today."
            }
            Notifications.showReminder(applicationContext, "Moderatrix check-in", text)
        }

        checkSyncStaleness(repo, now)

        return Result.success()
    }

    private suspend fun checkSyncStaleness(repo: ModeratrixRepo, now: Long) {
        val lastSuccess = repo.lastSuccessfulSyncEpochMs()
        val settings = repo.settings()
        val sinceLastSuccess = lastSuccess?.let { now - it }

        val isStale = lastSuccess == null || sinceLastSuccess!! >= SYNC_STALE_THRESHOLD_MS
        if (!isStale) return

        val lastWarned = settings.getLastSyncStaleWarningEpochMs()
        val shouldWarnAgain = lastWarned == null || now - lastWarned >= SYNC_STALE_RENOTIFY_INTERVAL_MS
        if (!shouldWarnAgain) return

        val text = if (lastSuccess != null) {
            val hours = sinceLastSuccess!! / (60 * 60 * 1000)
            "Hasn't synced with the server in $hours hour(s). Check that it's reachable on the LAN."
        } else {
            "Hasn't synced with the server yet. Check that it's reachable on the LAN."
        }
        Notifications.showSyncStale(applicationContext, "Moderatrix sync issue", text)
        settings.setLastSyncStaleWarningEpochMs(now)
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        private const val PERIODIC_WORK_NAME = "moderatrix_idle_check"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
