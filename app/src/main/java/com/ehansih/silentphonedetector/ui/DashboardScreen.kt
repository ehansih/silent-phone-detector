package com.ehansih.silentphonedetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehansih.silentphonedetector.data.db.PermissionEvent
import com.ehansih.silentphonedetector.data.repository.ScanResult
import com.ehansih.silentphonedetector.data.scanner.ImsiAlert
import com.ehansih.silentphonedetector.data.scanner.NetworkAnomaly
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(vm: DetectorViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    when (val s = state) {
        is ScanState.Idle    -> IdleScreen { vm.scan() }
        is ScanState.Scanning -> ScanningScreen()
        is ScanState.Done    -> MainScreen(s.result, vm) { vm.scan() }
        is ScanState.Error   -> ErrorScreen(s.message) { vm.scan() }
    }
}

// ── Idle / Scanning / Error ──────────────────────────────────────────────────

@Composable
private fun IdleScreen(onScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Silent Phone Detector", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Scan for suspicious permissions, network activity, IMSI catchers & data breaches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onScan) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Start Scan")
            }
        }
    }
}

@Composable
private fun ScanningScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("Scanning apps, network & baseband...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Scan failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ── Main tabbed screen ───────────────────────────────────────────────────────

@Composable
private fun MainScreen(result: ScanResult, vm: DetectorViewModel, onRescan: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Permissions", "Network", "Baseband", "Breach Check")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Silent Phone Detector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Tap a tab to explore results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onRescan) { Text("Rescan") }
        }

        // Risk score bar always visible at top
        RiskScoreCard(score = result.riskScore, label = result.riskLabel,
            modifier = Modifier.padding(horizontal = 20.dp))

        Spacer(modifier = Modifier.height(8.dp))

        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> PermissionsTab(result)
            1 -> NetworkTab(result.networkSuspiciousApps)
            2 -> BasebandTab(result.imsiAlert)
            3 -> BreachCheckTab(vm)
        }
    }
}

// ── Tab: Permissions ─────────────────────────────────────────────────────────

@Composable
private fun PermissionsTab(result: ScanResult) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCard(result) }
        if (!result.usageAccessGranted) item { UsageAccessBanner() }
        if (result.events.isNotEmpty()) {
            item { Text("Top Risk Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
            items(result.events) { event -> EventCard(event) }
        } else {
            item {
                Text(
                    "No sensitive permission activity detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Tab: Network Monitor ─────────────────────────────────────────────────────

@Composable
private fun NetworkTab(anomalies: List<NetworkAnomaly>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Network Traffic Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Apps flagged for high data usage since last reboot (>5 MB upload or >30 MB download).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (anomalies.isEmpty()) {
            item {
                Text(
                    "No suspicious network activity detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(anomalies) { anomaly -> NetworkAnomalyCard(anomaly) }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun NetworkAnomalyCard(anomaly: NetworkAnomaly) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(anomaly.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Suspicious", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Text(anomaly.reason, style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Upload: ${"%.1f".format(anomaly.txMb)} MB", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("Download: ${"%.1f".format(anomaly.rxMb)} MB", style = MaterialTheme.typography.bodySmall)
            }
            Text(anomaly.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Tab: Baseband / IMSI Catcher ─────────────────────────────────────────────

@Composable
private fun BasebandTab(alert: ImsiAlert?) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("IMSI Catcher Detection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "IMSI catchers (Stingrays) force phones to downgrade to 2G to intercept calls and SMS messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (alert == null) {
            item { Text("Scan required to check baseband.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item {
                val containerColor = if (alert.detected)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
                Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (alert.detected) "2G Downgrade Detected" else "Network OK",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                alert.networkType,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (alert.detected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(alert.message, style = MaterialTheme.typography.bodySmall)
                        if (alert.detected) {
                            Text(
                                "Recommended: Move to a different location and rescan. Avoid making sensitive calls on 2G.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Tab: Breach Check ────────────────────────────────────────────────────────

@Composable
private fun BreachCheckTab(vm: DetectorViewModel) {
    val breachState by vm.breachState.collectAsState()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Password Breach Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Checks if your password appeared in known data breaches using the Have I Been Pwned k-anonymity API. Only the first 5 characters of a SHA-1 hash are sent — your password never leaves the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    vm.resetBreachState()
                },
                label = { Text("Enter password to check") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (password.isNotBlank()) vm.checkPassword(password) }),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Button(
                onClick = { vm.checkPassword(password) },
                enabled = password.isNotBlank() && breachState !is BreachState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (breachState is BreachState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
                Text("Check Password")
            }
        }

        when (val bs = breachState) {
            is BreachState.Done -> {
                item {
                    val result = bs.result
                    when {
                        result.error != null -> {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Check Failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(result.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        result.isPwned -> {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Password Compromised!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        "This password has appeared ${result.count} time(s) in data breach databases.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Action required: Change this password immediately on all accounts where it is used.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        else -> {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.2f))) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Password Not Found in Breaches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Good news — this password wasn't found in any known data breach. Keep using strong, unique passwords.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun RiskScoreCard(score: Int, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Privacy Risk Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Text("$score/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    score >= 70 -> MaterialTheme.colorScheme.error
                    score >= 40 -> MaterialTheme.colorScheme.tertiary
                    else        -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryCard(result: ScanResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            SummaryRow("Microphone Access", "${result.microphoneApps} apps")
            SummaryRow("Location Access", "${result.locationApps} apps")
            SummaryRow("Background Activity", "${result.backgroundApps} apps")
            SummaryRow("Network Anomalies", "${result.networkAnomalies} apps")
            SummaryRow("IMSI Alert", if (result.imsiAlert?.detected == true) "Yes" else "No")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UsageAccessBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Usage Access Not Granted", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Background detection is limited. Grant Usage Access in Settings → Apps → Special app access → Usage access → Silent Phone Detector.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EventCard(event: PermissionEvent) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.detectedAtMs))
    val bgLabel = if (event.isBackground) " · Background" else ""
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(event.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Risk: ${event.riskScore}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("${event.permissionType}$bgLabel", style = MaterialTheme.typography.bodyMedium)
            Text("Detected at $timeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
