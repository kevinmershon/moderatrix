package com.moderatrix.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class StaleActivityAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<StaleActivityWorker>().build()
        WorkManager.getInstance(context).enqueue(request)

        // Roll a fresh random pair for tomorrow once both of today's slots have had their chance
        // to fire; scheduling from each firing (rather than a single midnight job) keeps this
        // self-healing if the app was killed and missed a midnight trigger.
        StaleActivityScheduler.scheduleForTomorrow(context)
    }
}
