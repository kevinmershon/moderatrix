package com.moderatrix.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moderatrix.app.data.db.ActivityEntryEntity
import com.moderatrix.app.data.db.VitalsEntryEntity
import com.moderatrix.app.data.repo.ModeratrixRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryDay(
    val date: LocalDate,
    val activities: List<ActivityEntryEntity>,
    val vitals: List<VitalsEntryEntity>
)

private const val HISTORY_DAYS = 14

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModeratrixRepo(application)

    private val _activityNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val activityNames: StateFlow<Map<String, String>> = _activityNames.asStateFlow()

    val days: StateFlow<List<HistoryDay>> = run {
        val today = LocalDate.now()
        val dates = (0 until HISTORY_DAYS).map { today.minusDays(it.toLong()) }

        val activityFlows = dates.map { repo.observeActivityEntries(it) }
        val vitalsFlows = dates.map { repo.observeVitalsEntries(it) }

        combine(
            combine(activityFlows) { it },
            combine(vitalsFlows) { it }
        ) { activitiesPerDay, vitalsPerDay ->
            dates.mapIndexed { index, date ->
                HistoryDay(date, activitiesPerDay[index], vitalsPerDay[index])
            }.filter { it.activities.isNotEmpty() || it.vitals.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    init {
        viewModelScope.launch {
            _activityNames.value = repo.getAllActivities().associate { it.id to it.name }
        }
    }
}
