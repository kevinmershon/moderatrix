package com.moderatrix.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moderatrix.app.data.db.ActivityEntryEntity
import com.moderatrix.app.data.db.Period
import com.moderatrix.app.data.db.VitalsEntryEntity

private val CornflowerBlue = Color(0xFF6495ED)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val days by viewModel.days.collectAsState()
    val activityNames by viewModel.activityNames.collectAsState()

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

                    Period.values().forEach { period ->
                        val doneActivities = day.activities.filter { it.done && it.period == period }
                        // Multiple vitals saves can happen within one period (each slider tweak
                        // autosaves); only the most recent snapshot is meaningful to show.
                        val latestVitals = day.vitals
                            .filter { it.period == period }
                            .maxByOrNull { it.recordedAtEpochMs }
                        if (doneActivities.isEmpty() && latestVitals == null) return@forEach

                        PeriodSection(
                            period = period,
                            activities = doneActivities,
                            vitals = latestVitals,
                            activityNames = activityNames
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSection(
    period: Period,
    activities: List<ActivityEntryEntity>,
    vitals: VitalsEntryEntity?,
    activityNames: Map<String, String>
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = period.name.lowercase().replaceFirstChar { it.uppercase() },
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(CornflowerBlue, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        if (activities.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                activities.forEach { entry ->
                    AssistChip(
                        onClick = {},
                        label = { Text(activityNames[entry.activityId] ?: entry.activityId) }
                    )
                }
            }
        }

        vitals?.let { v ->
            Text(
                "mood=${v.mood ?: "-"} alertness=${v.alertness ?: "-"} energy=${v.energy ?: "-"} " +
                    "pain=${v.pain ?: "-"} satiety=${v.satiety ?: "-"} hydration=${v.hydration ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
