package com.ehansih.silentphonedetector.data.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class ImsiAlert(
    val detected: Boolean,
    val networkType: String,
    val message: String
)

class ImsiCatcherScanner(private val context: Context) {

    // 2G network type constants that indicate possible IMSI catcher downgrade
    private val TYPES_2G = setOf(
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM
    )

    fun scan(): ImsiAlert {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ImsiAlert(
                detected = false,
                networkType = "Unknown",
                message = "READ_PHONE_STATE permission not granted — cannot check network type."
            )
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val rawType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            val typeName = networkTypeName(rawType)
            val is2g = rawType in TYPES_2G

            ImsiAlert(
                detected = is2g,
                networkType = typeName,
                message = if (is2g) {
                    "Warning: Device downgraded to $typeName. IMSI catchers force 2G to intercept calls/SMS. Review surroundings."
                } else {
                    "Network OK: $typeName detected — no 2G downgrade found."
                }
            )
        } catch (e: Exception) {
            ImsiAlert(detected = false, networkType = "Unknown", message = "Could not read network type: ${e.message}")
        }
    }

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS   -> "GPRS (2G)"
        TelephonyManager.NETWORK_TYPE_EDGE   -> "EDGE (2G)"
        TelephonyManager.NETWORK_TYPE_CDMA   -> "CDMA (2G)"
        TelephonyManager.NETWORK_TYPE_1xRTT  -> "1xRTT (2G)"
        TelephonyManager.NETWORK_TYPE_IDEN   -> "iDEN (2G)"
        TelephonyManager.NETWORK_TYPE_GSM    -> "GSM (2G)"
        TelephonyManager.NETWORK_TYPE_UMTS   -> "UMTS (3G)"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO rev.0 (3G)"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO rev.A (3G)"
        TelephonyManager.NETWORK_TYPE_HSDPA  -> "HSDPA (3G)"
        TelephonyManager.NETWORK_TYPE_HSUPA  -> "HSUPA (3G)"
        TelephonyManager.NETWORK_TYPE_HSPA   -> "HSPA (3G)"
        TelephonyManager.NETWORK_TYPE_HSPAP  -> "HSPA+ (3G)"
        TelephonyManager.NETWORK_TYPE_LTE    -> "LTE (4G)"
        TelephonyManager.NETWORK_TYPE_EHRPD  -> "eHRPD (3G)"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
        361 /* NETWORK_TYPE_NR */             -> "NR (5G)"
        else                                  -> "Type $type"
    }
}
