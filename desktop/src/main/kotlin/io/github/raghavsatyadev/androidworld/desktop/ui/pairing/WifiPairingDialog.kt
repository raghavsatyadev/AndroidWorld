package io.github.raghavsatyadev.androidworld.desktop.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.wifi.QrPairingIntent
import io.github.raghavsatyadev.androidworld.core.application.wifi.WifiPairStep
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph

@Composable
fun WifiPairingDialogHost(graph: AppGraph) {
    var open by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val pairing = graph.remote.pairing
    val step by pairing.state.collectAsState()

    LaunchedEffect(Unit) {
        graph.deviceBar.pairOverWifiRequests.collect {
            selectedTab = 0
            open = true
            graph.remote.pairing.handle(QrPairingIntent.StartQr)
        }
    }

    if (!open) return

    val close: () -> Unit = {
        if (step !is WifiPairStep.Done) {
            pairing.handle(QrPairingIntent.Cancel)
        }
        open = false
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Pair device over Wi-Fi") },
        confirmButton = {
            TextButton(onClick = close) {
                Text("Close")
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .width(420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            if (selectedTab != 0) {
                                selectedTab = 0
                                if (step !is WifiPairStep.WaitingForScan) {
                                    pairing.handle(QrPairingIntent.StartQr)
                                }
                            }
                        },
                        text = { Text("QR code") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            if (selectedTab != 1) {
                                selectedTab = 1
                                pairing.handle(QrPairingIntent.Cancel)
                            }
                        },
                        text = { Text("Pairing code") },
                    )
                }

                if (selectedTab == 0) {
                    QrTabContent(
                        step = step,
                        onStartQr = { pairing.handle(QrPairingIntent.StartQr) },
                    )
                } else {
                    PairingCodeTabContent(
                        step = step,
                        onPair = { pairingEndpoint, code, connectEndpoint ->
                            pairing.handle(
                                QrPairingIntent.PairWithCode(
                                    pairingEndpoint = pairingEndpoint,
                                    code = code,
                                    connectEndpoint = connectEndpoint,
                                ),
                            )
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun QrTabContent(
    step: WifiPairStep,
    onStartQr: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (step) {
            is WifiPairStep.WaitingForScan -> {
                QrCode(
                    text = step.qrPayload,
                    modifier = Modifier.size(240.dp),
                )
                Text(
                    text =
                        "On the phone: Developer options > Wireless debugging > Pair device with QR code, " +
                            "then scan this code. The phone and this computer must be on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Looking for the phone on the network… (${step.polls} checks)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is WifiPairStep.Pairing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Pairing with ${step.endpoint}…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            WifiPairStep.Connecting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Connecting…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is WifiPairStep.Done -> {
                Text(
                    text = step.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is WifiPairStep.Failed -> {
                Text(
                    text = step.reason,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onStartQr) {
                    Text("Try again")
                }
            }
            WifiPairStep.Idle -> {
                Button(onClick = onStartQr) {
                    Text("Show QR code")
                }
            }
        }
    }
}

@Composable
private fun PairingCodeTabContent(
    step: WifiPairStep,
    onPair: (pairingEndpoint: String, code: String, connectEndpoint: String) -> Unit,
) {
    var pairingEndpoint by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectEndpoint by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = pairingEndpoint,
            onValueChange = { pairingEndpoint = it },
            label = { Text("IP address & port") },
            placeholder = { Text("192.168.1.20:41235") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Wi-Fi pairing code") },
            placeholder = { Text("6 digits") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = connectEndpoint,
            onValueChange = { connectEndpoint = it },
            label = { Text("Connect address") },
            placeholder = { Text("192.168.1.20:37099") },
            supportingText = { Text("Shown on the Wireless debugging screen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val busy = step is WifiPairStep.Pairing || step is WifiPairStep.Connecting
        Button(
            onClick = {
                onPair(pairingEndpoint.trim(), code.trim(), connectEndpoint.trim())
                code = ""
            },
            enabled =
                pairingEndpoint.isNotBlank() &&
                    code.isNotBlank() &&
                    connectEndpoint.isNotBlank() &&
                    !busy,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Pair")
        }

        when (step) {
            is WifiPairStep.Pairing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Pairing with ${step.endpoint}…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            WifiPairStep.Connecting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Connecting…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is WifiPairStep.Done -> {
                Text(
                    text = step.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is WifiPairStep.Failed -> {
                Text(
                    text = step.reason,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> Unit
        }
    }
}
