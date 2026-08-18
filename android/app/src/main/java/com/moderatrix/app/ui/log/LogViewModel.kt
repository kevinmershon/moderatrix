package com.moderatrix.app.ui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moderatrix.app.data.db.ActivityDefEntity
import com.moderatrix.app.data.db.ActivityEntryEntity
import com.moderatrix.app.data.db.CategoryEntity
import com.moderatrix.app.data.db.Period
import com.moderatrix.app.data.db.VitalsEntryEntity
import com.moderatrix.app.data.repo.ModeratrixRepo
import com.moderatrix.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

fun currentPeriod(time: LocalTime = LocalTime.now()): Period = when {
    time.isBefore(LocalTime.of(11, 0)) -> Period.MORNING
    time.isBefore(LocalTime.of(17, 0)) -> Period.NOON
    else -> Period.NIGHT
}

data class LogUiState(
    val date: LocalDate = LocalDate.now(),
    val categories: List<CategoryEntity> = emptyList(),
    val activities: List<ActivityDefEntity> = emptyList(),
    val entriesToday: List<ActivityEntryEntity> = emptyList(),
    val vitalsToday: List<VitalsEntryEntity> = emptyList()
)

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModeratrixRepo(application)

    private val date = LocalDate.now()

    val uiState: StateFlow<LogUiState> = combine(
        repo.observeCategories(),
        repo.observeActivities(),
        repo.observeActivityEntries(date),
        repo.observeVitalsEntries(date)
    ) { categories, activities, entries, vitals ->
        LogUiState(date, categories, activities, entries, vitals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogUiState(date))

    fun selectedPeriod(): Period = currentPeriod()

    fun toggleActivity(activityId: String, period: Period, done: Boolean) {
        viewModelScope.launch {
            repo.recordActivity(date, period, activityId, done)
            SyncWorker.triggerOneOff(getApplication())
        }
    }

    fun recordVitals(
        period: Period,
        mood: Int?,
        alertness: Int?,
        energy: Int?,
        pain: Int?,
        satiety: Int?,
        hydration: Int?,
        notes: String?
    ) {
        viewModelScope.launch {
            repo.recordVitals(date, period, mood, alertness, energy, pain, satiety, hydration, notes)
            SyncWorker.triggerOneOff(getApplication())
        }
    }

    fun activitiesForCategory(categoryId: String, period: Period, state: LogUiState): List<ActivityDefEntity> =
        state.activities.filter { it.categoryId == categoryId && it.isAvailableFor(period) }

    fun isDoneForPeriod(activityId: String, period: Period, state: LogUiState): Boolean =
        state.entriesToday.any { it.activityId == activityId && it.period == period && it.done }
}
