package com.critterfarm.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.critterfarm.data.local.CritterMood
import com.critterfarm.health.HealthConnectAvailability
import com.critterfarm.health.HealthConnectManager
import com.critterfarm.ui.farm.CritterCanvas
import com.critterfarm.ui.model.GameZone

/**
 * The very first thing a new player sees: meet Sprout, connect Health Connect (or don't —
 * every path leads to a playable farm, never a dead end).
 */
@Composable
fun OnboardingScreen(
    healthConnectManager: HealthConnectManager,
    onContinueToFarm: () -> Unit,
) {
    val context = LocalContext.current

    var availability by remember { mutableStateOf(healthConnectManager.getAvailability()) }
    var permissionResult by remember { mutableStateOf<Set<String>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted -> permissionResult = granted }

    // In case the player already granted permissions in a previous session (e.g. they came
    // back from Settings), reflect that without requiring another tap.
    LaunchedEffect(Unit) {
        val alreadyGranted = healthConnectManager.getGrantedPermissions()
        if (alreadyGranted.isNotEmpty()) {
            permissionResult = alreadyGranted
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            CritterCanvas(mood = CritterMood.BOUNCING_HAPPY, speciesKey = "blob", stage = 0)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Meet Sprout the Blob!",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sprout grows alongside you — roaming the pasture, splashing in the " +
                    "pond, and celebrating every healthy choice you make.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))

            when (val currentAvailability = availability) {
                HealthConnectAvailability.Available -> {
                    Button(
                        onClick = {
                            permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect My Health", style = MaterialTheme.typography.labelLarge)
                    }
                }
                HealthConnectAvailability.NotInstalled -> UnavailableCard(
                    title = "Health Connect isn't installed yet",
                    body = "Install it to unlock every zone on the farm — or skip for now, " +
                        "Sprout is happy to wait.",
                    primaryLabel = "Get Health Connect",
                    onPrimary = { openHealthConnectInstallPage(context) },
                    onRecheck = { availability = healthConnectManager.getAvailability() },
                )
                HealthConnectAvailability.UpdateRequired -> UnavailableCard(
                    title = "Health Connect needs an update",
                    body = "A quick update unlocks every zone on the farm — or skip for now.",
                    primaryLabel = "Update Health Connect",
                    onPrimary = { openHealthConnectInstallPage(context) },
                    onRecheck = { availability = healthConnectManager.getAvailability() },
                )
                HealthConnectAvailability.Unavailable -> Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "This device can't run Health Connect",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "No worries — Sprout still wants to hang out! Every zone will " +
                                "just stay in Dormant Zone mode.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            permissionResult?.let { granted ->
                Spacer(modifier = Modifier.height(16.dp))
                PermissionOutcomeCard(granted)
            }

            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onContinueToFarm, modifier = Modifier.fillMaxWidth()) {
                Text(continueLabel(permissionResult), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun continueLabel(permissionResult: Set<String>?): String =
    if (permissionResult != null && permissionResult.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
        "Let's Go!"
    } else {
        "Continue to Farm"
    }

@Composable
private fun UnavailableCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onRecheck: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(onClick = onPrimary) { Text(primaryLabel) }
                OutlinedButton(onClick = onRecheck, modifier = Modifier.padding(start = 8.dp)) {
                    Text("I've done that")
                }
            }
        }
    }
}

@Composable
private fun PermissionOutcomeCard(granted: Set<String>) {
    val allGranted = granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
    val dormantZones = GameZone.entries.filter { it.isDormant(granted) }
    val headline = when {
        allGranted -> "All zones are live! Sprout is thrilled."
        granted.isEmpty() -> "No worries — Sprout still wants to hang out."
        else -> "Nice start! A few zones are live already."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            if (dormantZones.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                dormantZones.forEach { zone ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("${zone.emoji} ")
                        Text(zone.dormantMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You can link the rest anytime from the farm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun openHealthConnectInstallPage(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=${HealthConnectManager.PROVIDER_PACKAGE_NAME}"),
    ).apply { setPackage("com.android.vending") }
    try {
        context.startActivity(marketIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // No Play Store app — fall through to the web listing below.
    }
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=${HealthConnectManager.PROVIDER_PACKAGE_NAME}"),
    )
    try {
        context.startActivity(webIntent)
    } catch (e: ActivityNotFoundException) {
        // No browser either. The "I've done that" recheck button remains the fallback path,
        // and the farm stays fully playable regardless.
    }
}
