package com.ehansih.silentphonedetector.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ehansih.silentphonedetector.data.db.AppDatabase
import com.ehansih.silentphonedetector.data.db.PermissionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(
    val riskScore: Int,
    val riskLabel: String,
    val microphoneApps: Int,
    val locationApps: Int,
    val backgroundApps: Int,
    val networkAnomalies: Int,
    val events: List<PermissionEvent>,
    val usageAccessGranted: Boolean
)

class PermissionRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.permissionEventDao()

    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    private val OPS = listOf(
        Triple("Microphone", AppOpsManager.OPSTR_RECORD_AUDIO,  "android.permission.RECORD_AUDIO"),
        Triple("Camera",     AppOpsManager.OPSTR_CAMERA,        "android.permission.CAMERA"),
        Triple("Location",   AppOpsManager.OPSTR_FINE_LOCATION, "android.permission.ACCESS_FINE_LOCATION"),
        Triple("Contacts",   AppOpsManager.OPSTR_READ_CONTACTS, "android.permission.READ_CONTACTS"),
        Triple("SMS",        AppOpsManager.OPSTR_READ_SMS,      "android.permission.READ_SMS")
    )

    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val usageGranted = isUsageAccessGranted()

        // Get user-installed + updated system packages only
        val packages = getAllUserPackages(pm)

        val events = mutableListOf<PermissionEvent>()

        for (pkg in packages) {
            val packageName = pkg.packageName
            val uid = runCatching { pm.getApplicationInfo(packageName, 0).uid }.getOrNull() ?: continue
            val appName = runCatching { pm.getApplicationLabel(pkg.applicationInfo!!).toString() }.getOrDefault(packageName)

            for ((permType, opStr, androidPerm) in OPS) {
                try {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(opStr, uid, packageName)
                    } else {
                        if (pm.checkPermission(androidPerm, packageName) == PackageManager.PERMISSION_GRANTED)
                            AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED
                    }
                    if (mode != AppOpsManager.MODE_ALLOWED) continue

                    // Detect background access via UsageStats if available
                    val isBackground = usageGranted && isAppInBackground(packageName, now)

                    val risk = when {
                        isBackground && permType in listOf("Microphone", "Camera") -> 35
                        isBackground -> 20
                        permType in listOf("Microphone", "Camera") -> 15
                        else -> 5
                    }

                    events.add(PermissionEvent(
                        packageName    = packageName,
                        appName        = appName,
                        permissionType = permType,
                        detectedAtMs   = now,
                        isBackground   = isBackground,
                        riskScore      = risk
                    ))
                } catch (_: Exception) {}
            }
        }

        // Persist to Room (replace previous scan)
        dao.clear()
        if (events.isNotEmpty()) dao.insertAll(events)

        val micApps    = events.count { it.permissionType == "Microphone" }
        val locApps    = events.count { it.permissionType == "Location" }
        val bgApps     = events.count { it.isBackground }

        // Risk score: weighted sum capped at 100
        val rawRisk = (bgApps * 20 + micApps * 10 + locApps * 5 + events.size * 2).coerceAtMost(100)

        val label = when {
            rawRisk >= 70 -> "High risk — background sensor access detected. Review apps immediately."
            rawRisk >= 40 -> "Elevated — multiple apps have sensitive permissions. Check camera & mic."
            rawRisk >= 20 -> "Moderate — some permissions granted. Monitor for unusual activity."
            else          -> "Low — minimal sensitive permission usage detected."
        }

        ScanResult(
            riskScore         = rawRisk,
            riskLabel         = label,
            microphoneApps    = micApps,
            locationApps      = locApps,
            backgroundApps    = bgApps,
            networkAnomalies  = 0,
            events            = events.sortedByDescending { it.riskScore }.take(20),
            usageAccessGranted = usageGranted
        )
    }

    private fun isUsageAccessGranted(): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            !stats.isNullOrEmpty()
        } catch (_: Exception) { false }
    }

    private fun isAppInBackground(packageName: String, now: Long): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24 * 60 * 60 * 1000L,
                now
            )
            val appStats = stats?.firstOrNull { it.packageName == packageName }
            appStats != null && appStats.totalTimeInForeground < 60_000L
        } catch (_: Exception) { false }
    }

    private fun getAllUserPackages(pm: PackageManager): List<android.content.pm.PackageInfo> {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA).toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        }
        return packages.filter { pkg ->
            val ai = pkg.applicationInfo ?: return@filter false
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            !isSystem || isUpdated
        }
    }
}
