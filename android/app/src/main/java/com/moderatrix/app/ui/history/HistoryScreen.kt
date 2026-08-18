package com.moderatrix.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val days by viewModel.days.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("History", style = MaterialTheme.typography.headlineSmall)
        }
        if (days.isEmpty()) {
            item {
                Text(
                    "No entries yet in the last 14 days.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        items(days) { day ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(day.date.toString(), style = MaterialTheme.typography.titleMedium)
                    val doneActivities = day.activities.filter { it.done }
                    if (doneActivities.isNotEmpty()) {
                        Text(
                            "Activities: " + doneActivities.joinToString { "${it.activityId} (${it.period.name.lowercase()})" }
                        )
                    }
                    day.vitals.forEach { v ->
                        Text(
                            "${v.period.name.lowercase()}: mood=${v.mood ?: "-"} alertness=${v.alertness ?: "-"} " +
                                "energy=${v.energy ?: "-"} pain=${v.pain ?: "-"} satiety=${v.satiety ?: "-"} " +
                                "hydration=${v.hydration ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
