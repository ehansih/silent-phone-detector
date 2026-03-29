package com.ehansih.silentphonedetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen() {
    val summary = DemoData.summary

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection()
        }
        item {
            RiskScoreCard(score = summary.riskScore, label = summary.riskLabel)
        }
        item {
            SummaryCard(summary)
        }
        item {
            SectionTitle(title = "Recent Behavior")
        }
        items(summary.events) { event ->
            EventCard(event)
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Silent Phone Detector",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Today\'s Activity Snapshot",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RiskScoreCard(score: Int, label: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Privacy Risk Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$score/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: ActivitySummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            SummaryRow(label = "Microphone Access", value = summary.microphoneAccess)
            SummaryRow(label = "Location Pings", value = summary.locationAccess)
            SummaryRow(label = "Background Wakeups", value = summary.backgroundWakeups)
            SummaryRow(label = "Network Anomalies", value = summary.networkAnomalies)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun EventCard(event: ActivityEvent) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = event.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = event.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = event.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ActivitySummary(
    val riskScore: Int,
    val riskLabel: String,
    val microphoneAccess: String,
    val locationAccess: String,
    val backgroundWakeups: String,
    val networkAnomalies: String,
    val events: List<ActivityEvent>
)

private data class ActivityEvent(
    val appName: String,
    val description: String,
    val timeLabel: String
)

private object DemoData {
    val summary = ActivitySummary(
        riskScore = 62,
        riskLabel = "Elevated background activity detected. Review microphone-heavy apps.",
        microphoneAccess = "5 sessions",
        locationAccess = "18 pings",
        backgroundWakeups = "12 wakeups",
        networkAnomalies = "2 spikes",
        events = listOf(
            ActivityEvent(
                appName = "Instagram",
                description = "Microphone accessed 3 times in the background today.",
                timeLabel = "Last seen 15 min ago"
            ),
            ActivityEvent(
                appName = "Maps",
                description = "Location tracked every 12 minutes while screen was off.",
                timeLabel = "Last seen 1 hr ago"
            ),
            ActivityEvent(
                appName = "Work Chat",
                description = "Unexpected network spike while in background.",
                timeLabel = "Last seen 2 hrs ago"
            )
        )
    )
}
