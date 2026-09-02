package me.kafuuneko.rpclient.feature.developer.logviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiIntent
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiState
import me.kafuuneko.rpclient.libs.debug.AppLogEntry
import me.kafuuneko.rpclient.libs.debug.AppLogLevel
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppLogViewerLayout(
    uiState: AppLogViewerUiState,
    emit: AppLogViewerUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is AppLogViewerUiState.Normal) {
        AppLogViewerUiIntent.Back.emit()
    }

    when (uiState) {
        AppLogViewerUiState.None -> Unit
        is AppLogViewerUiState.Finished -> Unit
        is AppLogViewerUiState.Normal -> AppLogViewerNormal(uiState, emit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppLogViewerNormal(
    state: AppLogViewerUiState.Normal,
    emit: AppLogViewerUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.app_logs),
            onBack = { AppLogViewerUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { AppLogViewerUiIntent.ExportLogs.emit() }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.export_logs)
                    )
                }
                IconButton(onClick = { AppLogViewerUiIntent.RequestClearLogs.emit() }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.clear_logs_title)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { AppLogViewerUiIntent.ChangeSearchQuery(it).emit() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.app_log_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { AppLogViewerUiIntent.ChangeSearchQuery("").emit() }) {
                            Icon(Icons.Rounded.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedLevel == null,
                    onClick = { AppLogViewerUiIntent.SelectLevelFilter(null).emit() },
                    label = { Text("ALL (${state.allLogs.size})") },
                    shape = RoundedCornerShape(10.dp)
                )
                AppLogLevel.entries.forEach { level ->
                    val count = state.allLogs.count { it.level == level }
                    FilterChip(
                        selected = state.selectedLevel == level,
                        onClick = { AppLogViewerUiIntent.SelectLevelFilter(level).emit() },
                        label = { Text("${level.name} ($count)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Log List or Empty State
            if (state.filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    RpEmptyState(
                        title = stringResource(R.string.no_logs_title),
                        subtitle = stringResource(R.string.no_logs_subtitle)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filteredLogs, key = { it.id }) { entry ->
                        AppLogItemCard(entry)
                    }
                }
            }
        }
    }

    if (state.isClearDialogOpen) {
        AppDangerDialog(
            onDismissRequest = { AppLogViewerUiIntent.DismissClearDialog.emit() },
            title = stringResource(R.string.clear_logs_title),
            message = stringResource(R.string.clear_logs_confirm_desc),
            confirmText = stringResource(R.string.delete),
            onConfirm = { AppLogViewerUiIntent.ConfirmClearLogs.emit() }
        )
    }
}

@Composable
private fun AppLogItemCard(entry: AppLogEntry) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = remember(entry.timestamp) { timeFormatter.format(Date(entry.timestamp)) }
    var expanded by remember { mutableStateOf(false) }

    val (levelColor, levelBgColor) = when (entry.level) {
        AppLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
        AppLogLevel.INFO -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        // 固定的浅色琥珀在深色主题下不可读，改用会随主题反转的 tertiary 色对。
        AppLogLevel.WARN -> MaterialTheme.colorScheme.tertiary to
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        AppLogLevel.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !entry.throwableSummary.isNullOrBlank()) {
                expanded = !expanded
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Level Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = levelBgColor
                ) {
                    Text(
                        text = entry.level.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                }

                // Module Tag
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = entry.module,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Time
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }

            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Throwable summary if present
            if (!entry.throwableSummary.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.throwableSummary,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
