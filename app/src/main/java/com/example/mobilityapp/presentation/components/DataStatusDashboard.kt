package com.example.mobilityapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mobilityapp.R
import com.example.mobilityapp.data.InitializationState
import com.example.mobilityapp.presentation.theme.StatusSuccess
import java.text.DateFormat
import java.util.Date

@Composable
fun DataStatusDashboard(
    initializationState: InitializationState,
    osmLastModified: Long?,
    onScanDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (initializationState) {
        is InitializationState.MissingFiles -> MissingFilesCard(
            missingFiles = initializationState.missingFiles,
            onScanDownloads = onScanDownloads,
            modifier = modifier
        )
        InitializationState.Importing,
        InitializationState.NeedsImport -> ImportingCard(modifier = modifier)
        InitializationState.Ready -> ReadyCard(
            osmLastModified = osmLastModified,
            modifier = modifier
        )
        is InitializationState.Error -> ErrorCard(
            message = initializationState.message,
            modifier = modifier
        )
    }
}

@Composable
private fun MissingFilesCard(
    missingFiles: List<String>,
    onScanDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.data_status_missing_files_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            missingFiles.forEach { fileName ->
                Text(
                    text = stringResource(R.string.data_status_bullet, fileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Button(
                onClick = onScanDownloads,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.data_status_scan_downloads))
            }
        }
    }
}

@Composable
private fun ImportingCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text(
                text = stringResource(R.string.data_status_importing_graph),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ReadyCard(
    osmLastModified: Long?,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val formattedDate = osmLastModified?.let { formatter.format(Date(it)) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.data_status_ready),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            formattedDate?.let { date ->
                Text(
                    text = stringResource(R.string.data_status_osm_date, date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
