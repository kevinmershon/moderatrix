package com.moderatrix.app.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moderatrix.app.data.db.ActivityDefEntity
import com.moderatrix.app.data.db.CategoryEntity
import com.moderatrix.app.data.repo.ModeratrixRepo
import com.moderatrix.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ConfigUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val activities: List<ActivityDefEntity> = emptyList(),
    val serverUrl: String = ""
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModeratrixRepo(application)
    private val settings = repo.settings()

    val uiState: StateFlow<ConfigUiState> = combine(
        repo.observeCategories(),
        repo.observeActivities(),
        settings.serverBaseUrl
    ) { categories, activities, serverUrl ->
        ConfigUiState(categories, activities, serverUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConfigUiState())

    fun createActivity(
        categoryId: String,
        name: String,
        targetFreqPerWeek: Int
    ) {
        viewModelScope.launch {
            val activityId = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank {
                UUID.randomUUID().toString()
            }
            val nextSortOrder = (uiState.value.activities.filter { it.categoryId == categoryId && !it.archived }
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            repo.upsertActivityDef(
                ActivityDefEntity(
                    activityId, categoryId, name, targetFreqPerWeek, archived = false,
                    sortOrder = nextSortOrder
                )
            )
            pushConfigInBackground()
        }
    }

    /** Persists a full drag-reorder of one category's activities. */
    fun reorderActivities(orderedActivities: List<ActivityDefEntity>) {
        viewModelScope.launch {
            repo.reorderActivities(orderedActivities)
        }
    }

    /** Persists a full drag-reorder of categories. */
    fun reorderCategories(orderedCategories: List<CategoryEntity>) {
        viewModelScope.launch {
            repo.reorderCategories(orderedCategories)
        }
    }

    /** Swaps [category] with its neighbor [delta] positions away (-1 = up, +1 = down). */
    fun moveCategory(category: CategoryEntity, delta: Int) {
        viewModelScope.launch {
            val ordered = uiState.value.categories
            val index = ordered.indexOfFirst { it.id == category.id }
            val targetIndex = index + delta
            if (index == -1 || targetIndex !in ordered.indices) return@launch

            val reordered = ordered.toMutableList().apply {
                add(targetIndex, removeAt(index))
            }
            repo.reorderCategories(reordered)
        }
    }

    /** Full in-place edit: name, category, freq, and morning/noon/night applicability. */
    fun saveActivity(activity: ActivityDefEntity) {
        viewModelScope.launch {
            repo.upsertActivityDef(activity)
            pushConfigInBackground()
        }
    }

    fun archiveActivity(activity: ActivityDefEntity) {
        viewModelScope.launch {
            repo.upsertActivityDef(activity.copy(archived = true))
            pushConfigInBackground()
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val nextSortOrder = (uiState.value.categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repo.upsertCategory(CategoryEntity(id, name, nextSortOrder))
            pushConfigInBackground()
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settings.setServerBaseUrl(url)
        }
    }

    fun syncNow() {
        SyncWorker.triggerOneOff(getApplication())
    }

    private fun pushConfigInBackground() {
        viewModelScope.launch {
            repo.pushConfig()
        }
    }
}
