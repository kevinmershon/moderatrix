package com.moderatrix.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moderatrix.app.data.db.ActivityDefEntity

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

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = serverUrlField,
                        onValueChange = { serverUrlField = it },
                        label = { Text("Server base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        item {
            Text("Categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(state.categories) { category ->
            Text("• ${category.name}", modifier = Modifier.padding(vertical = 2.dp))
        }
        item {
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

        item {
            Text(
                "Activities (tap to edit)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(state.activities.filter { !it.archived }) { activity ->
            val categoryName = state.categories.find { it.id == activity.categoryId }?.name ?: activity.categoryId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { editingActivity = activity },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(activity.name)
                    Text(
                        "$categoryName · ${activity.targetFreqPerWeek}x/wk · " + periodSummary(activity),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
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
                            val label = state.categories.find { it.id == selectedCategoryId }?.name ?: "Category"
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

private fun periodSummary(activity: ActivityDefEntity): String {
    val parts = mutableListOf<String>()
    if (activity.availableMorning) parts.add("AM")
    if (activity.availableNoon) parts.add("Noon")
    if (activity.availableNight) parts.add("Night")
    return if (parts.size == 3) "All day" else parts.joinToString("/")
}

@Composable
private fun EditActivityDialog(
    activity: ActivityDefEntity,
    categories: List<com.moderatrix.app.data.db.CategoryEntity>,
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

                Text("Available during", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = morning, onCheckedChange = { morning = it })
                    Text("Morning")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = noon, onCheckedChange = { noon = it })
                    Text("Noon")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
