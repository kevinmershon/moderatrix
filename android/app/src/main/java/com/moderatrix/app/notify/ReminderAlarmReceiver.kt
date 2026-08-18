package com.moderatrix.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_TRIGGER to "fixed_time"))
            .build()
        WorkManager.getInstance(context).enqueue(request)

        if (requestCode != -1) {
            ReminderScheduler.rescheduleNextDay(context, requestCode)
        }
    }

    companion object {
        const val EXTRA_REQUEST_CODE = "request_code"
    }
}
