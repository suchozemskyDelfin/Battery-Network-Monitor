package com.example.batterynetworkmonitor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.batterynetworkmonitor.ui.theme.BatteryNetworkMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BatteryNetworkMonitorTheme {
                // Sledujeme stav služby pro ovládání tlačítek
                var isServiceRunning by remember { mutableStateOf(false) }

                // Dynamický text stavu
                val statusText = if (isServiceRunning) "Služba je aktivní" else "Služba je zastavena"

                // Automatický start při prvním spuštění aplikace
                LaunchedEffect(Unit) {
                    startBatteryService()
                    isServiceRunning = true
                }

                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(24.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = "Monitor sítě",
                            style = MaterialTheme.typography.headlineLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Stav: $statusText",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Tlačítko ZAPNOUT - aktivní jen když služba neběží
                        Button(
                            onClick = {
                                startBatteryService()
                                isServiceRunning = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isServiceRunning
                        ) {
                            Text("Zapnout monitorování")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tlačítko VYPNOUT - aktivní jen když služba běží
                        OutlinedButton(
                            onClick = {
                                stopBatteryService()
                                isServiceRunning = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isServiceRunning
                        ) {
                            Text("Vypnout monitorování")
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "Aplikace nyní naslouchá na portu 8888 pro dotazy z PC klienta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }

    private fun startBatteryService() {
        val intent = Intent(this, BatteryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBatteryService() {
        val intent = Intent(this, BatteryService::class.java)
        stopService(intent)
    }
}