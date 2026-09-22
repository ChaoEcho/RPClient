package me.kafuuneko.rpclient.feature.recovery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryUiIntent
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryUiState

/** 使用应用既有 Material 主题，明确强调数据保留和唯一安全的重试操作。 */
@Composable
fun RecoveryLayout(state: RecoveryUiState, emit: (RecoveryUiIntent) -> Unit) {
    BackHandler { emit(RecoveryUiIntent.Close) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (state == RecoveryUiState.Failed) R.string.recovery_failed else R.string.recovery_description),
                modifier = Modifier.widthIn(max = 560.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state == RecoveryUiState.Working) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { emit(RecoveryUiIntent.Retry) }) { Text(stringResource(R.string.recovery_retry)) }
                TextButton(onClick = { emit(RecoveryUiIntent.Close) }) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryPreview() {
    AppTheme(dynamicColor = false) { RecoveryLayout(RecoveryUiState.Idle) {} }
}
