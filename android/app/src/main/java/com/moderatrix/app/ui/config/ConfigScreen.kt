package com.moderatrix.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moderatrix.app.data.db.ActivityDefEntity
import com.moderatrix.app.data.db.CategoryEntity
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface ConfigRow {
    data class Settings(val placeholder: Unit = Unit) : ConfigRow
    data class CategoriesHeader(val placeholder: Unit = Unit) : ConfigRow
    data class CategoryRow(val category: CategoryEntity, val isFirst: Boolean, val isLast: Boolean) : ConfigRow
    data class AddCategory(val placeholder: Unit = Unit) : ConfigRow
    data class ActivitiesHeader(val placeholder: Unit = Unit) : ConfigRow
    data class CategoryActivitiesHeader(val category: CategoryEntity) : ConfigRow
    data class ActivityRow(val activity: ActivityDefEntity) : ConfigRow
    data class AddActivity(val placeholder: Unit = Unit) : ConfigRow
}

private fun ConfigRow.key(): Any = when (this) {
    is ConfigRow.Settings -> "settings"
    is ConfigRow.CategoriesHeader -> "categories_header"
    is ConfigRow.CategoryRow -> "category_${category.id}"
    is ConfigRow.AddCategory -> "add_category"
    is ConfigRow.ActivitiesHeader -> "activities_header"
    is ConfigRow.CategoryActivitiesHeader -> "activities_header_${category.id}"
    is ConfigRow.ActivityRow -> "activity_${activity.id}"
    is ConfigRow.AddActivity -> "add_activity"
}

@Composable
fun ConfigScreen(viewModel: ConfigViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    var newActivityName by remember { mutableStateOf("") }
    var newActivityFreq by remember { mutableStateOf("3") }
    var selectedCategoryId by remember { mutableStateOf(state.categories.firstOrNull()?.id ?: "") }
    var newCategoryName by remember { mutableStateOf("") }
    var serverUrlField by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityDefEntity?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    // Local working order of activities, per category, so a drag feels immediate; committed to
    // the ViewModel (and thus the server) once the drag ends. Rebuilt whenever the underlying
    // data changes for reasons other than our own optimistic reorder (e.g. initial load, remote pull).
    var localActivityOrder by remember { mutableStateOf(state.activities) }
    LaunchedEffect(state.activities.map { it.id to it.categoryId to it.sortOrder }) {
        localActivityOrder = state.activities
    }

    editingActivity?.let { activity ->
        EditActivityDialog(
            activity = activity,
            categories = state.categories,
            onDismiss = { editingActivity = null },
            onSave = { updated ->
                viewModel.saveActivity(updated)
                editingActivity = null
            },
            onRemove = {
                viewModel.archiveActivity(activity)
                editingActivity = null
            }
        )
    }

    editingCategory?.let { category ->
        EditCategoryDialog(
            category = category,
            onDismiss = { editingCategory = null },
            onSave = { newName ->
                viewModel.renameCategory(category, newName)
                editingCategory = null
            }
        )
    }

    val rows = buildList {
        add(ConfigRow.Settings())
        add(ConfigRow.CategoriesHeader())
        state.categories.forEachIndexed { index, category ->
            add(ConfigRow.CategoryRow(category, isFirst = index == 0, isLast = index == state.categories.lastIndex))
        }
        add(ConfigRow.AddCategory())
        add(ConfigRow.ActivitiesHeader())
        state.categories.forEach { category ->
            val activitiesInCategory = localActivityOrder.filter { !it.archived && it.categoryId == category.id }
            if (activitiesInCategory.isEmpty()) return@forEach
            add(ConfigRow.CategoryActivitiesHeader(category))
            activitiesInCategory.forEach { add(ConfigRow.ActivityRow(it)) }
        }
        add(ConfigRow.AddActivity())
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromRow = rows.getOrNull(from.index) as? ConfigRow.ActivityRow ?: return@rememberReorderableLazyListState
        val toRow = rows.getOrNull(to.index) as? ConfigRow.ActivityRow ?: return@rememberReorderableLazyListState
        if (fromRow.activity.categoryId != toRow.activity.categoryId) return@rememberReorderableLazyListState

        val categoryId = fromRow.activity.categoryId
        val withinCategory = localActivityOrder.filter { !it.archived && it.categoryId == categoryId }
        val fromIndex = withinCategory.indexOfFirst { it.id == fromRow.activity.id }
        val toIndex = withinCategory.indexOfFirst { it.id == toRow.activity.id }
        if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState

        val reordered = withinCategory.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val others = localActivityOrder.filterNot { !it.archived && it.categoryId == categoryId }
        localActivityOrder = others + reordered
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        items(rows, key = { it.key() }) { row ->
            when (row) {
                is ConfigRow.Settings -> {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = serverUrlField,
                                onValueChange = { serverUrlField = it },
                                label = { Text("Server base URL") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { viewModel.setServerUrl(serverUrlField) }) {
                                    Text("Save URL")
                                }
                                OutlinedButton(onClick = { viewModel.syncNow() }) {
                                    Text("Sync now")
                                }
                            }
                        }
                    }
                }

                is ConfigRow.CategoriesHeader -> {
                    Text(
                        "Categories (tap to rename, arrows to reorder)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is ConfigRow.CategoryRow -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            row.category.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editingCategory = row.category }
                        )
                        IconButton(
                            enabled = !row.isFirst,
                            onClick = { viewModel.moveCategory(row.category, -1) }
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = !row.isLast,
                            onClick = { viewModel.moveCategory(row.category, 1) }
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }

                is ConfigRow.AddCategory -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("New category") },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (newCategoryName.isNotBlank()) {
                                viewModel.addCategory(newCategoryName)
                                newCategoryName = ""
                            }
                        }) { Text("Add") }
                    }
                }

                is ConfigRow.ActivitiesHeader -> {
                    Text(
                        "Activities (tap to edit, drag handle to reorder within a category)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is ConfigRow.CategoryActivitiesHeader -> {
                    Text(
                        row.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is ConfigRow.ActivityRow -> {
                    ReorderableItem(reorderableState, key = row.key()) { _ ->
                        val activity = row.activity
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { editingActivity = activity }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Drag to reorder",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .draggableHandle(
                                            onDragStopped = {
                                                val categoryId = activity.categoryId
                                                val newOrder = localActivityOrder.filter {
                                                    !it.archived && it.categoryId == categoryId
                                                }
                                                viewModel.reorderActivities(newOrder)
                                            }
                                        )
                                )
                                Column {
                                    Text(activity.name)
                                    Text(
                                        "${activity.targetFreqPerWeek}x/wk · " + periodSummary(activity),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                is ConfigRow.AddActivity -> {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Add activity", style = MaterialTheme.typography.titleSmall)

                            OutlinedTextField(
                                value = newActivityName,
                                onValueChange = { newActivityName = it },
                                label = { Text("Activity name") },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )

                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                OutlinedButton(onClick = { categoryMenuExpanded = true }) {
                                    val label = state.categories.find { it.id == selectedCategoryId }?.name
                                        ?: "Category"
                                    Text(label)
                                }
                                DropdownMenu(
                                    expanded = categoryMenuExpanded,
                                    onDismissRequest = { categoryMenuExpanded = false }
                                ) {
                                    state.categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.name) },
                                            onClick = {
                                                selectedCategoryId = category.id
                                                categoryMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = newActivityFreq,
                                onValueChange = { newActivityFreq = it.filter { c -> c.isDigit() } },
                                label = { Text("Target freq / week") },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )

                            Button(
                                onClick = {
                                    val freq = newActivityFreq.toIntOrNull() ?: 1
                                    if (newActivityName.isNotBlank() && selectedCategoryId.isNotBlank()) {
                                        viewModel.createActivity(selectedCategoryId, newActivityName, freq)
                                        newActivityName = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Add activity")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun periodSummary(activity: ActivityDefEntity): String {
    val parts = mutableListOf<String>()
    if (activity.availableMorning) parts.add("AM")
    if (activity.availableNoon) parts.add("Noon")
    if (activity.availableNight) parts.add("Night")
    return if (parts.size == 3) "All day" else parts.joinToString("/")
}

@Composable
private fun EditCategoryDialog(
    category: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(category.id) { mutableStateOf(category.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditActivityDialog(
    activity: ActivityDefEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (ActivityDefEntity) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember(activity.id) { mutableStateOf(activity.name) }
    var freq by remember(activity.id) { mutableStateOf(activity.targetFreqPerWeek.toString()) }
    var categoryId by remember(activity.id) { mutableStateOf(activity.categoryId) }
    var morning by remember(activity.id) { mutableStateOf(activity.availableMorning) }
    var noon by remember(activity.id) { mutableStateOf(activity.availableNoon) }
    var night by remember(activity.id) { mutableStateOf(activity.availableNight) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit activity") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { categoryMenuExpanded = true }) {
                        val label = categories.find { it.id == categoryId }?.name ?: "Category"
                        Text(label)
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = freq,
                    onValueChange = { freq = it.filter { c -> c.isDigit() } },
                    label = { Text("Target freq / week") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Text(
                    "Available during",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = morning, onCheckedChange = { morning = it })
                    Text("Morning")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noon, onCheckedChange = { noon = it })
                    Text("Noon")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = night, onCheckedChange = { night = it })
                    Text("Night")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    activity.copy(
                        name = name,
                        categoryId = categoryId,
                        targetFreqPerWeek = freq.toIntOrNull() ?: activity.targetFreqPerWeek,
                        availableMorning = morning,
                        availableNoon = noon,
                        availableNight = night
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
