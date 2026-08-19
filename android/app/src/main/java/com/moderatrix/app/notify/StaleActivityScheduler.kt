package com.moderatrix.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Up to two reminders a day, at random times between 8am and 8pm at least 4 hours apart,
 * nudging about whichever non-zero-frequency activity is most overdue relative to its weekly
 * target. Independent of the fixed-time/idle check-in reminders in [ReminderScheduler].
 */
object StaleActivityScheduler {
    private val WINDOW_START = LocalTime.of(8, 0)
    private val WINDOW_END = LocalTime.of(20, 0)
    private const val MIN_GAP_MINUTES = 4 * 60L
    private const val REQUEST_CODE_SLOT_1 = 4300
    private const val REQUEST_CODE_SLOT_2 = 4301
    private const val PREFS_NAME = "stale_activity_scheduler"
    private const val KEY_LAST_SCHEDULED_DATE = "last_scheduled_date"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Rolls today's two random times and schedules alarms for them, but only the first time this
     * is called on a given calendar day — safe to call from multiple entry points (app launch,
     * boot) without re-rolling already-armed times. If a picked time has already passed (e.g. the
     * app wasn't opened until evening), that slot fires shortly instead of being skipped outright,
     * as long as we're still within the window — otherwise a whole day could pass with zero
     * nudges. If shifting one slot to "fire soon" would leave less than the minimum gap before
     * the other slot's effective time, the later slot is dropped for today rather than firing
     * both close together.
     */
    fun scheduleForToday(context: Context) {
        val today = LocalDate.now()
        val prefs = prefs(context)
        val lastScheduledDate = prefs.getString(KEY_LAST_SCHEDULED_DATE, null)
        if (lastScheduledDate == today.toString()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val windowEnd = LocalDateTime.of(today, WINDOW_END)

        val (time1, time2) = pickTwoTimes()
        val rolls = listOf(REQUEST_CODE_SLOT_1 to time1, REQUEST_CODE_SLOT_2 to time2)
            .sortedBy { it.second }

        val effectiveTimes = mutableListOf<LocalDateTime>()
        for ((requestCode, time) in rolls) {
            val dateTime = LocalDateTime.of(today, time)
            val effectiveDateTime = when {
                dateTime.isAfter(now) -> dateTime
                now.isBefore(windowEnd) -> now.plusMinutes(2)
                else -> null // both the roll and now are past the window; nothing to do today
            } ?: continue

            val tooCloseToPrevious = effectiveTimes.lastOrNull()?.let {
                java.time.Duration.between(it, effectiveDateTime).toMinutes() < MIN_GAP_MINUTES
            } ?: false
            if (tooCloseToPrevious) continue

            effectiveTimes.add(effectiveDateTime)
            scheduleAt(context, alarmManager, effectiveDateTime, requestCode)
        }

        prefs.edit().putString(KEY_LAST_SCHEDULED_DATE, today.toString()).apply()
    }

    /** Called after a slot fires, to roll tomorrow's pair fresh (avoids the same time every day). */
    fun scheduleForTomorrow(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tomorrow = LocalDate.now().plusDays(1)

        val (time1, time2) = pickTwoTimes()
        scheduleAt(context, alarmManager, LocalDateTime.of(tomorrow, time1), REQUEST_CODE_SLOT_1)
        scheduleAt(context, alarmManager, LocalDateTime.of(tomorrow, time2), REQUEST_CODE_SLOT_2)

        prefs(context).edit().putString(KEY_LAST_SCHEDULED_DATE, tomorrow.toString()).apply()
    }

    private fun pickTwoTimes(): Pair<LocalTime, LocalTime> {
        val windowStartMinutes = WINDOW_START.toSecondOfDay() / 60
        val windowEndMinutes = WINDOW_END.toSecondOfDay() / 60

        val first = Random.nextLong(windowStartMinutes.toLong(), windowEndMinutes.toLong())

        // Restrict the second pick to the sub-range that's actually >= MIN_GAP_MINUTES away from
        // the first, on whichever side has room; if neither side has room (very unlikely given
        // the 12h window vs 4h gap), just place it at the window edge farthest from the first.
        val earlySideEnd = first - MIN_GAP_MINUTES
        val lateSideStart = first + MIN_GAP_MINUTES

        val second = when {
            earlySideEnd >= windowStartMinutes && lateSideStart <= windowEndMinutes -> {
                if (Random.nextBoolean()) {
                    Random.nextLong(windowStartMinutes.toLong(), earlySideEnd + 1)
                } else {
                    Random.nextLong(lateSideStart, windowEndMinutes.toLong() + 1)
                }
            }
            earlySideEnd >= windowStartMinutes -> Random.nextLong(windowStartMinutes.toLong(), earlySideEnd + 1)
            lateSideStart <= windowEndMinutes -> Random.nextLong(lateSideStart, windowEndMinutes.toLong() + 1)
            else -> if (first - windowStartMinutes > windowEndMinutes - first) windowStartMinutes.toLong() else windowEndMinutes.toLong()
        }

        return LocalTime.ofSecondOfDay(first * 60) to LocalTime.ofSecondOfDay(second * 60)
    }

    private fun scheduleAt(
        context: Context,
        alarmManager: AlarmManager,
        dateTime: LocalDateTime,
        requestCode: Int
    ) {
        val intent = Intent(context, StaleActivityAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REQUEST_CODE, requestCode)
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

    const val EXTRA_REQUEST_CODE = "stale_request_code"
}
