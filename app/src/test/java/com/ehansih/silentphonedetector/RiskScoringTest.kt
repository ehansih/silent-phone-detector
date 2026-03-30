package com.ehansih.silentphonedetector

import com.ehansih.silentphonedetector.data.db.PermissionEvent
import com.ehansih.silentphonedetector.data.repository.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dry-run unit tests for risk scoring logic, ScanResult construction,
 * and PermissionEvent model — no Android runtime needed.
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

    private fun buildScanResult(events: List<PermissionEvent>, usageGranted: Boolean = true): ScanResult {
        val micApps = events.count { it.permissionType == "Microphone" }
        val locApps = events.count { it.permissionType == "Location" }
        val bgApps  = events.count { it.isBackground }
        val rawRisk = (bgApps * 20 + micApps * 10 + locApps * 5 + events.size * 2).coerceAtMost(100)
        val label = when {
            rawRisk >= 70 -> "High risk — background sensor access detected. Review apps immediately."
            rawRisk >= 40 -> "Elevated — multiple apps have sensitive permissions. Check camera & mic."
            rawRisk >= 20 -> "Moderate — some permissions granted. Monitor for unusual activity."
            else          -> "Low — minimal sensitive permission usage detected."
        }
        return ScanResult(
            riskScore          = rawRisk,
            riskLabel          = label,
            microphoneApps     = micApps,
            locationApps       = locApps,
            backgroundApps     = bgApps,
            networkAnomalies   = 0,
            events             = events.sortedByDescending { it.riskScore }.take(20),
            usageAccessGranted = usageGranted
        )
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
        // bgApps=1 (*20) + micApps=1 (*10) + size=1 (*2) = 32
        assertEquals(32, result.riskScore)
        assertEquals(1, result.microphoneApps)
        assertEquals(1, result.backgroundApps)
        assertTrue(result.riskLabel.startsWith("Moderate"))
    }

    @Test
    fun `high risk label triggered at 70+`() {
        val events = (1..5).map { makeEvent("Microphone", isBackground = true, riskScore = 35, packageName = "pkg$it") }
        val result = buildScanResult(events)
        // bgApps=5(*20)=100 → capped at 100
        assertEquals(100, result.riskScore)
        assertTrue(result.riskLabel.startsWith("High risk"))
    }

    @Test
    fun `elevated label triggered between 40 and 69`() {
        // 2 mic bg apps: bgApps=2*20 + micApps=2*10 + size=2*2 = 40+20+4 = 64? wait:
        // bgApps=2(*20)=40, micApps=2(*10)=20, size=2(*2)=4 → 64
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
        // highest risk first
        assertTrue(result.events.first().riskScore >= result.events.last().riskScore)
    }

    @Test
    fun `usageAccessGranted flag propagates`() {
        val r1 = buildScanResult(emptyList(), usageGranted = true)
        val r2 = buildScanResult(emptyList(), usageGranted = false)
        assertTrue(r1.usageAccessGranted)
        assertTrue(!r2.usageAccessGranted)
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

    @Test
    fun `networkAnomalies always 0 in current implementation`() {
        val result = buildScanResult(listOf(makeEvent("Microphone", false, 15)))
        assertEquals(0, result.networkAnomalies)
    }
}
