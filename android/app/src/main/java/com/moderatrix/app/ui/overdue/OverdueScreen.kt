package com.moderatrix.app.ui.overdue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moderatrix.app.data.repo.OverdueActivity
import kotlin.math.roundToInt

@Composable
fun OverdueScreen(viewModel: OverdueViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("Overdue", style = MaterialTheme.typography.headlineSmall)
        }

        if (!state.loading && state.overdueActivities.isEmpty()) {
            item {
                Text(
                    "Nothing overdue right now.",
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        val grouped = state.overdueActivities
            .groupBy { it.activity.categoryId }
            .toList()
            .sortedBy { (_, activities) -> -(activities.maxOf { it.staleness }) }

        items(grouped) { (categoryId, activities) ->
            val categoryName = state.categoriesById[categoryId]?.name ?: "Uncategorized"

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(categoryName, style = MaterialTheme.typography.titleMedium)
                    activities.forEach { overdue ->
                        OverdueRow(overdue)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueRow(overdue: OverdueActivity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${overdue.activity.name} (${overdue.activity.targetFreqPerWeek}x/wk)")
        Text(
            "${overdue.daysSinceLastDone.roundToInt()}d overdue",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
