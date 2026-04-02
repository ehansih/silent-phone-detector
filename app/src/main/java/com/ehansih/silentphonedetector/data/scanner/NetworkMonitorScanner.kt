package com.ehansih.silentphonedetector.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build

data class NetworkAnomaly(
    val packageName: String,
    val appName: String,
    val txMb: Double,
    val rxMb: Double,
    val reason: String
)

class NetworkMonitorScanner(private val context: Context) {

    // Thresholds since last device reboot
    private val TX_THRESHOLD = 5L * 1024 * 1024   // 5 MB upload
    private val RX_THRESHOLD = 30L * 1024 * 1024  // 30 MB download

    fun scan(): List<NetworkAnomaly> {
        val pm = context.packageManager
        val anomalies = mutableListOf<NetworkAnomaly>()

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }.filter {
            val ai = it.applicationInfo ?: return@filter false
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            !isSystem || isUpdated
        }

        for (pkg in packages) {
            val uid = runCatching { pm.getApplicationInfo(pkg.packageName, 0).uid }.getOrNull() ?: continue
            val tx = TrafficStats.getUidTxBytes(uid)
            val rx = TrafficStats.getUidRxBytes(uid)
            if (tx < 0 || rx < 0) continue  // UNSUPPORTED

            if (tx < TX_THRESHOLD && rx < RX_THRESHOLD) continue

            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg.packageName, 0)).toString()
            }.getOrDefault(pkg.packageName)

            val txMb = tx / 1_048_576.0
            val rxMb = rx / 1_048_576.0
            val reasons = mutableListOf<String>()
            if (tx >= TX_THRESHOLD) reasons.add("Upload ${"%.1f".format(txMb)} MB")
            if (rx >= RX_THRESHOLD) reasons.add("Download ${"%.1f".format(rxMb)} MB")

            anomalies.add(
                NetworkAnomaly(
                    packageName = pkg.packageName,
                    appName = appName,
                    txMb = txMb,
                    rxMb = rxMb,
                    reason = reasons.joinToString(", ")
                )
            )
        }

        return anomalies.sortedByDescending { it.txMb + it.rxMb }
    }
}
