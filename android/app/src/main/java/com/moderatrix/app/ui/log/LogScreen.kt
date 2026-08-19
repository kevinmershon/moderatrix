package com.moderatrix.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moderatrix.app.data.db.Period
import kotlinx.coroutines.delay

@Composable
fun LogScreen(viewModel: LogViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedPeriod by remember { mutableStateOf(viewModel.selectedPeriod()) }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("Moderatrix — ${state.date}", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Period.values().forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        items(state.categories) { category ->
            val activitiesForPeriod = viewModel.activitiesForCategory(category.id, selectedPeriod, state)
            if (activitiesForPeriod.isEmpty()) return@items

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(category.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    activitiesForPeriod.forEach { activity ->
                        val done = viewModel.isDoneForPeriod(activity.id, selectedPeriod, state)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val label = if (activity.targetFreqPerWeek > 0) {
                                "${activity.name} (${activity.targetFreqPerWeek}x/wk)"
                            } else {
                                activity.name
                            }
                            Text(label)
                            Checkbox(
                                checked = done,
                                onCheckedChange = { checked ->
                                    viewModel.toggleActivity(activity.id, selectedPeriod, checked)
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            val latestVitals = viewModel.latestVitalsForPeriod(selectedPeriod, state)
            VitalsCard(selectedPeriod = selectedPeriod, latestVitals = latestVitals, viewModel = viewModel)
        }
    }
}

@Composable
private fun VitalsCard(
    selectedPeriod: Period,
    latestVitals: com.moderatrix.app.data.db.VitalsEntryEntity?,
    viewModel: LogViewModel
) {
    var mood by remember { mutableIntStateOf(3) }
    var alertness by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var pain by remember { mutableIntStateOf(0) }
    var satiety by remember { mutableIntStateOf(3) }
    var hydration by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }

    // Reload saved values whenever the selected period changes or a newer snapshot for it
    // arrives (e.g. after sync), instead of always starting from hardcoded defaults.
    LaunchedEffect(selectedPeriod, latestVitals?.id) {
        mood = latestVitals?.mood ?: 3
        alertness = latestVitals?.alertness ?: 3
        energy = latestVitals?.energy ?: 3
        pain = latestVitals?.pain ?: 0
        satiety = latestVitals?.satiety ?: 3
        hydration = latestVitals?.hydration ?: 3
        notes = latestVitals?.notes ?: ""
    }

    fun save() {
        viewModel.recordVitals(
            selectedPeriod, mood, alertness, energy, pain, satiety, hydration,
            notes.ifBlank { null }
        )
    }

    // Debounce free-text notes so we don't write on every keystroke. Skipped right after the
    // reload effect above sets `notes` from storage, so that doesn't re-trigger a save.
    var notesDirty by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPeriod, latestVitals?.id) {
        notesDirty = false
    }
    LaunchedEffect(notes) {
        if (!notesDirty) return@LaunchedEffect
        delay(800)
        save()
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("How are you doing?", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

            LabeledSlider("Mood", mood, 1, 5, onChange = { mood = it }, onSettled = ::save)
            LabeledSlider("Alertness", alertness, 1, 5, onChange = { alertness = it }, onSettled = ::save)
            LabeledSlider("Energy", energy, 1, 5, onChange = { energy = it }, onSettled = ::save)
            LabeledSlider("Pain", pain, 0, 5, onChange = { pain = it }, onSettled = ::save)
            LabeledSlider("Satiety (1=hungry, 5=full)", satiety, 1, 5, onChange = { satiety = it }, onSettled = ::save)
            LabeledSlider("Hydration", hydration, 1, 5, onChange = { hydration = it }, onSettled = ::save)

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    notesDirty = true
                },
                label = { Text("Notes (optional, autosaves)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    onSettled: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: $value")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onSettled,
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0)
        )
    }
}
