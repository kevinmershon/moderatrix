package com.moderatrix.app.ui.overdue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moderatrix.app.data.db.CategoryEntity
import com.moderatrix.app.data.repo.ModeratrixRepo
import com.moderatrix.app.data.repo.OverdueActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverdueUiState(
    val loading: Boolean = true,
    val categoriesById: Map<String, CategoryEntity> = emptyMap(),
    val overdueActivities: List<OverdueActivity> = emptyList()
)

class OverdueViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModeratrixRepo(application)

    private val categoriesById = MutableStateFlow<Map<String, CategoryEntity>?>(null)

    val uiState: StateFlow<OverdueUiState> = combine(
        categoriesById,
        repo.observeTopOverdueActivities(limit = 10)
    ) { categories, overdue ->
        if (categories == null) {
            OverdueUiState(loading = true)
        } else {
            OverdueUiState(loading = false, categoriesById = categories, overdueActivities = overdue)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverdueUiState())

    init {
        viewModelScope.launch {
            categoriesById.value = repo.getAllCategories().associateBy { it.id }
        }
    }
}
