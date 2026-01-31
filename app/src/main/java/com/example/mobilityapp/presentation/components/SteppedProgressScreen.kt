package com.example.mobilityapp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mobilityapp.R

data class LoadingStep(
    val label: String,
    val isCompleted: Boolean
)

@Composable
fun SteppedProgressScreen(
    steps: List<LoadingStep>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.loading_step_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Find the first incomplete step to show as "current"
                val currentStepIndex = steps.indexOfFirst { !it.isCompleted }
                
                steps.forEachIndexed { index, step ->
                    StepItem(
                        label = step.label,
                        isCompleted = step.isCompleted,
                        isCurrent = index == currentStepIndex
                    )
                }
                
                // Show overall progress only if there are steps
                if (steps.isNotEmpty()) {
                    val completedCount = steps.count { it.isCompleted }
                    val totalCount = steps.size
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / totalCount.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    
                    Text(
                        text = stringResource(R.string.loading_steps_completed, completedCount, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    label: String,
    isCompleted: Boolean,
    isCurrent: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCompleted -> {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isCurrent -> {
                    // Animated loading indicator for current step only
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    // Pending step - show empty checkbox
                    Text(
                        text = "[ ]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                isCurrent -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}
