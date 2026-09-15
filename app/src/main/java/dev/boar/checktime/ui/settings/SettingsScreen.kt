package dev.boar.checktime.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.data.Settings
import dev.boar.checktime.scheduler.PendingNotification
import dev.boar.checktime.ui.common.formatTime

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onOpenCategories: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(Permissions.status(context)) }

    // Возврат из системных настроек — перечитать статусы.
    LifecycleResumeEffect(Unit) {
        permissions = Permissions.status(context)
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = Permissions.status(context)
    }

    SettingsScreen(
        state = state,
        permissions = permissions,
        onSetInterval = { viewModel.setInterval(it) },
        onSetSnooze = { viewModel.setSnooze(it) },
        onSetStep = { viewModel.setStep(it) },
        onStartTracking = { viewModel.startTracking() },
        onAllocateNow = { context.startActivity(PendingNotification.allocationIntent(context)) },
        onOpenCategories = onOpenCategories,
        onFixOverlay = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        onFixNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onFixExactAlarms = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(Permissions.exactAlarmSettingsIntent(context))
        },
        onFixFullScreen = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) context.startActivity(Permissions.fullScreenIntentSettingsIntent(context))
        },
        onFixBattery = { context.startActivity(Permissions.batterySettingsIntent(context)) },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState?,
    permissions: PermissionStatus,
    onSetInterval: (Int) -> Unit,
    onSetSnooze: (Int) -> Unit,
    onSetStep: (Int) -> Unit,
    onStartTracking: () -> Unit,
    onAllocateNow: () -> Unit,
    onOpenCategories: () -> Unit,
    onFixOverlay: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixBattery: () -> Unit,
) {
    if (state == null) return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_tracking), style = MaterialTheme.typography.titleMedium)
        if (state.tracking == null) {
            Button(onClick = onStartTracking, enabled = permissions.overlay, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_start_tracking))
            }
            if (!permissions.overlay) {
                Text(stringResource(R.string.settings_overlay_required), color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text(stringResource(R.string.settings_tracking_since, formatTime(state.tracking.trackingStart)))
            OutlinedButton(onClick = onAllocateNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_allocate_now))
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_intervals), style = MaterialTheme.typography.titleMedium)
        MinutesField(stringResource(R.string.settings_interval), state.settings.intervalMinutes, onSetInterval)
        MinutesField(stringResource(R.string.settings_snooze), state.settings.snoozeMinutes, onSetSnooze)
        MinutesField(stringResource(R.string.settings_step), state.settings.stepMinutes, onSetStep)

        HorizontalDivider()
        OutlinedButton(onClick = onOpenCategories, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_categories))
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleMedium)
        PermissionRow(stringResource(R.string.perm_overlay), permissions.overlay, onFixOverlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(stringResource(R.string.perm_notifications), permissions.notifications, onFixNotifications)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(stringResource(R.string.perm_exact_alarms), permissions.exactAlarms, onFixExactAlarms)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PermissionRow(stringResource(R.string.perm_full_screen), permissions.fullScreenIntent, onFixFullScreen)
        }
        PermissionRow(stringResource(R.string.perm_battery), permissions.batteryUnrestricted, onFixBattery)
    }
}

/** Числовое поле; коммитит каждое валидное значение в диапазоне Settings.MIN_MINUTES..MAX_MINUTES. */
@Composable
private fun MinutesField(label: String, value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.let { if (it in Settings.MIN_MINUTES..Settings.MAX_MINUTES) onCommit(it) }
        },
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.unit_minutes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp))
        if (!granted) {
            TextButton(onClick = onFix) { Text(stringResource(R.string.perm_fix)) }
        }
    }
}
