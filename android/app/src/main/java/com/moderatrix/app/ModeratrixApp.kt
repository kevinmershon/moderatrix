package com.moderatrix.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.moderatrix.app.data.repo.ModeratrixRepo
import com.moderatrix.app.notify.Notifications
import com.moderatrix.app.notify.ReminderScheduler
import com.moderatrix.app.notify.ReminderWorker
import com.moderatrix.app.notify.StaleActivityScheduler
import com.moderatrix.app.sync.SyncWorker
import kotlinx.coroutines.launch

class ModeratrixApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Notifications.ensureChannel(this)
        ReminderScheduler.scheduleAll(this)
        ReminderWorker.schedulePeriodic(this)
        StaleActivityScheduler.scheduleForToday(this)
        SyncWorker.schedulePeriodic(this)

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            ModeratrixRepo(this@ModeratrixApp).ensureSeeded()
        }
    }
}
