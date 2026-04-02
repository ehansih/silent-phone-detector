package com.ehansih.silentphonedetector.data.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.MessageDigest

data class BreachResult(
    val isPwned: Boolean,
    val count: Int,
    val error: String? = null
)

/**
 * Uses the Have I Been Pwned k-anonymity API to check if a password
 * has appeared in known data breaches.
 *
 * Only the first 5 characters of the SHA-1 hash are sent to the server.
 * The full password never leaves the device.
 */
class BreachScanner {

    suspend fun checkPassword(password: String): BreachResult = withContext(Dispatchers.IO) {
        if (password.isBlank()) return@withContext BreachResult(isPwned = false, count = 0, error = "Password is empty")
        try {
            val sha1 = sha1Hex(password)
            val prefix = sha1.take(5)
            val suffix = sha1.drop(5)

            val conn = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection()
            conn.setRequestProperty("User-Agent", "SilentPhoneDetector/1.0")
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000

            val lines = BufferedReader(InputStreamReader(conn.getInputStream())).readLines()
            val match = lines.firstOrNull { it.startsWith(suffix, ignoreCase = true) }

            if (match != null) {
                val count = match.split(":").getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                BreachResult(isPwned = true, count = count)
            } else {
                BreachResult(isPwned = false, count = 0)
            }
        } catch (e: Exception) {
            BreachResult(isPwned = false, count = 0, error = e.message ?: "Network error")
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
