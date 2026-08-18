package com.moderatrix.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moderatrix.app.data.db.ActivityEntryEntity
import com.moderatrix.app.data.db.VitalsEntryEntity
import com.moderatrix.app.data.repo.ModeratrixRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryDay(
    val date: LocalDate,
    val activities: List<ActivityEntryEntity>,
    val vitals: List<VitalsEntryEntity>
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModeratrixRepo(application)

    private val _days = MutableStateFlow<List<HistoryDay>>(emptyList())
    val days: StateFlow<List<HistoryDay>> = _days.asStateFlow()

    init {
        loadLastDays(14)
    }

    private fun loadLastDays(count: Int) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val out = mutableListOf<HistoryDay>()
            for (offset in 0 until count) {
                val date = today.minusDays(offset.toLong())
                val activities = repo.getActivityEntries(date)
                val vitals = repo.getVitalsEntries(date)
                if (activities.isNotEmpty() || vitals.isNotEmpty()) {
                    out.add(HistoryDay(date, activities, vitals))
                }
            }
            _days.value = out
        }
    }
}
