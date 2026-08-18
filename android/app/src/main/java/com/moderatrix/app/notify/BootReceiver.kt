package com.moderatrix.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moderatrix.app.sync.SyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
            ReminderWorker.schedulePeriodic(context)
            SyncWorker.schedulePeriodic(context)
        }
    }
}
