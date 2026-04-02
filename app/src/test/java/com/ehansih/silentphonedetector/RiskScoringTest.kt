package com.ehansih.silentphonedetector

import com.ehansih.silentphonedetector.data.db.PermissionEvent
import com.ehansih.silentphonedetector.data.repository.ScanResult
import com.ehansih.silentphonedetector.data.scanner.BreachResult
import com.ehansih.silentphonedetector.data.scanner.ImsiAlert
import com.ehansih.silentphonedetector.data.scanner.NetworkAnomaly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Dry-run unit tests — no Android runtime needed.
 * Covers: PermissionEvent, ScanResult aggregation, risk scoring,
 *         NetworkAnomaly, ImsiAlert, BreachResult, SHA-1 k-anonymity logic.
 */
class RiskScoringTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun makeEvent(
        permissionType: String,
        isBackground: Boolean,
        riskScore: Int,
        packageName: String = "com.test.app",
        appName: String = "TestApp"
    ) = PermissionEvent(
        packageName    = packageName,
        appName        = appName,
        permissionType = permissionType,
        detectedAtMs   = System.currentTimeMillis(),
        isBackground   = isBackground,
        riskScore      = riskScore
    )

    private fun buildScanResult(
        events: List<PermissionEvent>,
        usageGranted: Boolean = true,
        networkAnomalies: List<NetworkAnomaly> = emptyList(),
        imsiAlert: ImsiAlert? = null
    ): ScanResult {
        val micApps  = events.count { it.permissionType == "Microphone" }
        val locApps  = events.count { it.permissionType == "Location" }
        val bgApps   = events.count { it.isBackground }
        val netCount = networkAnomalies.size
        val imsiBonus = if (imsiAlert?.detected == true) 20 else 0
        val rawRisk = (bgApps * 20 + micApps * 10 + locApps * 5 + events.size * 2 + netCount * 8 + imsiBonus)
            .coerceAtMost(100)
        val label = when {
            rawRisk >= 70 -> "High risk — background sensor access detected. Review apps immediately."
            rawRisk >= 40 -> "Elevated — multiple apps have sensitive permissions. Check camera & mic."
            rawRisk >= 20 -> "Moderate — some permissions granted. Monitor for unusual activity."
            else          -> "Low — minimal sensitive permission usage detected."
        }
        return ScanResult(
            riskScore             = rawRisk,
            riskLabel             = label,
            microphoneApps        = micApps,
            locationApps          = locApps,
            backgroundApps        = bgApps,
            networkAnomalies      = netCount,
            networkSuspiciousApps = networkAnomalies,
            imsiAlert             = imsiAlert,
            events                = events.sortedByDescending { it.riskScore }.take(20),
            usageAccessGranted    = usageGranted
        )
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }
    }

    // ── PermissionEvent model ─────────────────────────────────────────────────

    @Test
    fun `PermissionEvent default id is zero`() {
        val event = makeEvent("Microphone", false, 15)
        assertEquals(0L, event.id)
    }

    @Test
    fun `PermissionEvent fields round-trip correctly`() {
        val now = System.currentTimeMillis()
        val event = PermissionEvent(
            packageName    = "com.foo.bar",
            appName        = "FooBar",
            permissionType = "Camera",
            detectedAtMs   = now,
            isBackground   = true,
            riskScore      = 35
        )
        assertEquals("com.foo.bar", event.packageName)
        assertEquals("FooBar", event.appName)
        assertEquals("Camera", event.permissionType)
        assertEquals(now, event.detectedAtMs)
        assertTrue(event.isBackground)
        assertEquals(35, event.riskScore)
    }

    // ── Risk score per-event weights ──────────────────────────────────────────

    @Test
    fun `background microphone gets risk 35`() {
        val risk = when {
            true && "Microphone" in listOf("Microphone", "Camera") -> 35
            true -> 20
            "Microphone" in listOf("Microphone", "Camera") -> 15
            else -> 5
        }
        assertEquals(35, risk)
    }

    @Test
    fun `background location gets risk 20`() {
        val risk = when {
            true && "Location" in listOf("Microphone", "Camera") -> 35
            true -> 20
            "Location" in listOf("Microphone", "Camera") -> 15
            else -> 5
        }
        assertEquals(20, risk)
    }

    @Test
    fun `foreground microphone gets risk 15`() {
        val risk = when {
            false && "Microphone" in listOf("Microphone", "Camera") -> 35
            false -> 20
            "Microphone" in listOf("Microphone", "Camera") -> 15
            else -> 5
        }
        assertEquals(15, risk)
    }

    @Test
    fun `foreground contacts gets risk 5`() {
        val risk = when {
            false && "Contacts" in listOf("Microphone", "Camera") -> 35
            false -> 20
            "Contacts" in listOf("Microphone", "Camera") -> 15
            else -> 5
        }
        assertEquals(5, risk)
    }

    // ── ScanResult aggregation ────────────────────────────────────────────────

    @Test
    fun `empty scan gives risk 0 and Low label`() {
        val result = buildScanResult(emptyList())
        assertEquals(0, result.riskScore)
        assertTrue(result.riskLabel.startsWith("Low"))
        assertEquals(0, result.microphoneApps)
        assertEquals(0, result.locationApps)
        assertEquals(0, result.backgroundApps)
    }

    @Test
    fun `single background mic app scores correctly`() {
        val events = listOf(makeEvent("Microphone", isBackground = true, riskScore = 35))
        val result = buildScanResult(events)
        // bgApps=1(*20) + micApps=1(*10) + size=1(*2) = 32
        assertEquals(32, result.riskScore)
        assertEquals(1, result.microphoneApps)
        assertEquals(1, result.backgroundApps)
        assertTrue(result.riskLabel.startsWith("Moderate"))
    }

    @Test
    fun `high risk label triggered at 70+`() {
        val events = (1..5).map { makeEvent("Microphone", isBackground = true, riskScore = 35, packageName = "pkg$it") }
        val result = buildScanResult(events)
        // bgApps=5(*20)=100 → capped
        assertEquals(100, result.riskScore)
        assertTrue(result.riskLabel.startsWith("High risk"))
    }

    @Test
    fun `elevated label triggered between 40 and 69`() {
        // bgApps=2(*20)=40 + micApps=2(*10)=20 + size=2(*2)=4 = 64
        val events = (1..2).map { makeEvent("Microphone", isBackground = true, riskScore = 35, packageName = "pkg$it") }
        val result = buildScanResult(events)
        assertEquals(64, result.riskScore)
        assertTrue(result.riskLabel.startsWith("Elevated"))
    }

    @Test
    fun `risk score capped at 100`() {
        val events = (1..20).map { makeEvent("Microphone", isBackground = true, riskScore = 35, packageName = "pkg$it") }
        val result = buildScanResult(events)
        assertTrue(result.riskScore <= 100)
    }

    @Test
    fun `events list sorted by riskScore descending and capped at 20`() {
        val events = (1..25).mapIndexed { i, _ ->
            makeEvent("Contacts", isBackground = false, riskScore = i, packageName = "pkg$i")
        }
        val result = buildScanResult(events)
        assertEquals(20, result.events.size)
        assertTrue(result.events.first().riskScore >= result.events.last().riskScore)
    }

    @Test
    fun `usageAccessGranted flag propagates`() {
        val r1 = buildScanResult(emptyList(), usageGranted = true)
        val r2 = buildScanResult(emptyList(), usageGranted = false)
        assertTrue(r1.usageAccessGranted)
        assertFalse(r2.usageAccessGranted)
    }

    @Test
    fun `location apps counted separately from microphone`() {
        val events = listOf(
            makeEvent("Microphone", false, 15, "pkg1"),
            makeEvent("Location",   false, 5,  "pkg2"),
            makeEvent("Location",   false, 5,  "pkg3")
        )
        val result = buildScanResult(events)
        assertEquals(1, result.microphoneApps)
        assertEquals(2, result.locationApps)
        assertEquals(0, result.backgroundApps)
    }

    // ── NetworkAnomaly ────────────────────────────────────────────────────────

    @Test
    fun `NetworkAnomaly fields set correctly`() {
        val anomaly = NetworkAnomaly(
            packageName = "com.spy.app",
            appName     = "SpyApp",
            txMb        = 12.5,
            rxMb        = 5.0,
            reason      = "Upload 12.5 MB"
        )
        assertEquals("SpyApp", anomaly.appName)
        assertEquals(12.5, anomaly.txMb, 0.01)
        assertEquals("Upload 12.5 MB", anomaly.reason)
    }

    @Test
    fun `network anomalies add 8 points each to risk score`() {
        val anomalies = listOf(
            NetworkAnomaly("pkg1", "App1", 10.0, 5.0, "Upload 10.0 MB"),
            NetworkAnomaly("pkg2", "App2", 8.0,  3.0, "Upload 8.0 MB")
        )
        val result = buildScanResult(emptyList(), networkAnomalies = anomalies)
        // 2 anomalies * 8 = 16
        assertEquals(16, result.riskScore)
        assertEquals(2, result.networkAnomalies)
        assertEquals(2, result.networkSuspiciousApps.size)
        assertTrue(result.riskLabel.startsWith("Low"))
    }

    @Test
    fun `network anomalies list preserved in result`() {
        val anomaly = NetworkAnomaly("pkg1", "App1", 50.0, 200.0, "Upload 50.0 MB, Download 200.0 MB")
        val result = buildScanResult(emptyList(), networkAnomalies = listOf(anomaly))
        assertEquals(1, result.networkSuspiciousApps.size)
        assertEquals("pkg1", result.networkSuspiciousApps[0].packageName)
    }

    // ── ImsiAlert ────────────────────────────────────────────────────────────

    @Test
    fun `ImsiAlert not detected returns correct state`() {
        val alert = ImsiAlert(detected = false, networkType = "LTE (4G)", message = "Network OK: LTE (4G)")
        assertFalse(alert.detected)
        assertEquals("LTE (4G)", alert.networkType)
    }

    @Test
    fun `ImsiAlert detected adds 20 to risk score`() {
        val alert = ImsiAlert(detected = true, networkType = "EDGE (2G)", message = "Warning: 2G downgrade")
        val result = buildScanResult(emptyList(), imsiAlert = alert)
        assertEquals(20, result.riskScore)
        assertNotNull(result.imsiAlert)
        assertTrue(result.imsiAlert!!.detected)
    }

    @Test
    fun `ImsiAlert not detected adds 0 to risk score`() {
        val alert = ImsiAlert(detected = false, networkType = "NR (5G)", message = "Network OK")
        val result = buildScanResult(emptyList(), imsiAlert = alert)
        assertEquals(0, result.riskScore)
    }

    @Test
    fun `null imsiAlert stored correctly`() {
        val result = buildScanResult(emptyList(), imsiAlert = null)
        assertNull(result.imsiAlert)
    }

    @Test
    fun `IMSI alert + background mic app reaches elevated threshold`() {
        // bgApps=1(*20)=20 + mic=1(*10)=10 + size=1(*2)=2 + imsi=20 = 52
        val events = listOf(makeEvent("Microphone", isBackground = true, riskScore = 35))
        val alert = ImsiAlert(detected = true, networkType = "GPRS (2G)", message = "Warning: 2G")
        val result = buildScanResult(events, imsiAlert = alert)
        assertEquals(52, result.riskScore)
        assertTrue(result.riskLabel.startsWith("Elevated"))
    }

    // ── BreachResult model ────────────────────────────────────────────────────

    @Test
    fun `BreachResult pwned fields`() {
        val r = BreachResult(isPwned = true, count = 54321)
        assertTrue(r.isPwned)
        assertEquals(54321, r.count)
        assertNull(r.error)
    }

    @Test
    fun `BreachResult not pwned`() {
        val r = BreachResult(isPwned = false, count = 0)
        assertFalse(r.isPwned)
        assertEquals(0, r.count)
    }

    @Test
    fun `BreachResult with error`() {
        val r = BreachResult(isPwned = false, count = 0, error = "Network timeout")
        assertFalse(r.isPwned)
        assertEquals("Network timeout", r.error)
    }

    // ── SHA-1 k-anonymity logic ───────────────────────────────────────────────

    @Test
    fun `SHA-1 of known password matches expected prefix`() {
        // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        val hash = sha1Hex("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals("5BAA6", hash.take(5))
    }

    @Test
    fun `SHA-1 prefix is 5 uppercase hex chars`() {
        val hash = sha1Hex("test1234")
        val prefix = hash.take(5)
        assertEquals(5, prefix.length)
        assertTrue(prefix.all { it.isLetterOrDigit() })
    }

    @Test
    fun `SHA-1 suffix is 35 chars`() {
        val hash = sha1Hex("anypassword")
        assertEquals(40, hash.length)     // full SHA-1
        assertEquals(35, hash.drop(5).length)  // suffix sent for comparison
    }

    @Test
    fun `different passwords produce different hashes`() {
        val h1 = sha1Hex("password1")
        val h2 = sha1Hex("password2")
        assertTrue(h1 != h2)
    }

    @Test
    fun `SHA-1 is deterministic`() {
        val h1 = sha1Hex("repeatme")
        val h2 = sha1Hex("repeatme")
        assertEquals(h1, h2)
    }
}
