package com.moderatrix.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moderatrix.app.data.repo.ModeratrixRepo
import kotlin.math.roundToInt

class StaleActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = ModeratrixRepo(applicationContext)
        val stale = repo.mostStaleActivity() ?: return Result.success()

        val days = stale.daysSinceLastDone.roundToInt()
        val recency = when (days) {
            0 -> "today"
            1 -> "in 1 day"
            else -> "in $days days"
        }
        val text = "You haven't done \"${stale.activity.name}\" $recency: goal ${stale.activity.targetFreqPerWeek}x/week"
        Notifications.showStaleActivity(applicationContext, "Moderatrix activity nudge", text)

        return Result.success()
    }
}
