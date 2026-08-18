package com.moderatrix.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fixed daily check-in times. If the user hasn't recorded anything within the last
 * 3 hours at one of these times, a reminder fires. These also act as a floor: even with
 * no fixed-time miss, [ReminderWorker] independently checks the "3h since last entry" rule
 * on a rolling basis via periodic WorkManager execution.
 */
object ReminderScheduler {
    val FIXED_TIMES: List<LocalTime> = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(13, 0),
        LocalTime.of(17, 0),
        LocalTime.of(20, 0)
    )

    private const val REQUEST_CODE_BASE = 4200

    fun scheduleAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        FIXED_TIMES.forEachIndexed { index, time ->
            var next = LocalDateTime.of(LocalDate.now(), time)
            if (next.isBefore(now)) {
                next = next.plusDays(1)
            }
            scheduleAt(context, alarmManager, next, REQUEST_CODE_BASE + index)
        }
    }

    private fun scheduleAt(
        context: Context,
        alarmManager: AlarmManager,
        dateTime: LocalDateTime,
        requestCode: Int
    ) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /** Called from the receiver after firing, to schedule the same slot 24h later. */
    fun rescheduleNextDay(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val index = requestCode - REQUEST_CODE_BASE
        if (index !in FIXED_TIMES.indices) return
        val next = LocalDateTime.of(LocalDate.now().plusDays(1), FIXED_TIMES[index])
        scheduleAt(context, alarmManager, next, requestCode)
    }
}
