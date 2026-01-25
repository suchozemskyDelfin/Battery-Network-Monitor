package com.example.batterynetworkmonitor

import android.app.*
import android.content.*
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class BatteryService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Okamžité vytvoření notifikace pro Foreground Service
        startForeground(1, createNotification())
        startUdpServer()
        return START_STICKY
    }

    private fun startUdpServer() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // 1. CPU WAKE LOCK - udržuje procesor naživu na pozadí
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryMonitor:CpuLock").apply {
            acquire()
        }

        // 2. MULTICAST LOCK - nezbytné pro příjem UDP broadcastů na Xiaomi
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("BatteryLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        serviceScope.launch {
            try {
                socket = DatagramSocket(8888)
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet) // Zde služba čeká na paket z PC

                    val message = String(packet.data, 0, packet.length)
                    if (message == "BATTERY_QUERY") {
                        // Jakmile přijde dotaz, zkusíme "nakopnout" displej
                        forceWakeScreen()
                        sendBatteryStatus(packet.address, packet.port)
                    }
                }
            } catch (e: Exception) {
                Log.e("BatteryService", "UDP Error: ${e.message}")
            }
        }
    }

    private fun forceWakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // FULL_WAKE_LOCK s ACQUIRE_CAUSES_WAKEUP by měl rozsvítit displej i na zamčeném Xiaomi
        @Suppress("DEPRECATION")
        val screenLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "BatteryMonitor:ForceWake"
        )

        // Rozsvítíme na 3 vteřiny, což stačí na odeslání síťové odpovědi
        if (!screenLock.isHeld) {
            screenLock.acquire(3000)
        }
    }

    private fun sendBatteryStatus(address: InetAddress, port: Int) {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val responseStr = "${android.os.Build.MODEL}|$pct|$isCharging"
        val response = responseStr.toByteArray()

        serviceScope.launch {
            try {
                socket?.send(DatagramPacket(response, response.size, address, port))
            } catch (e: Exception) {
                Log.e("BatteryService", "Send Error: ${e.message}")
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "battery_service"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monitor Baterie", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Služba sledování sítě")
            .setContentText("Aplikace naslouchá na portu 8888")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        socket?.close()
        if (multicastLock?.isHeld == true) multicastLock?.release()
        if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}