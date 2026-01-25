package com.example.batterynetworkmonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.batterynetworkmonitor.ui.theme.BatteryNetworkMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Spuštění služby
        val serviceIntent = Intent(this, BatteryService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            BatteryNetworkMonitorTheme {
                Scaffold { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).padding(24.dp)) {
                        Text("Monitor sítě", style = MaterialTheme.typography.headlineLarge)
                        Text("Stav: Služba je aktivní na pozadí.", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}