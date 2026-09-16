package dev.boar.checktime.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val overlay: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
)

object Permissions {
    fun status(context: Context): PermissionStatus {
        val am = context.getSystemService(AlarmManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        return PermissionStatus(
            overlay = Settings.canDrawOverlays(context),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms(),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    fun overlaySettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    fun exactAlarmSettingsIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))

    fun batterySettingsIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
}
