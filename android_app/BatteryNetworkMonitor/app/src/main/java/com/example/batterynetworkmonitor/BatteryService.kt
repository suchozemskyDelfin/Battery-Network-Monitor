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

    // Konstanta pro akci vypnutí
    companion object {
        const val ACTION_STOP = "STOP_BATTERY_SERVICE"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Pokud uživatel klikl na "Vypnout" v notifikaci
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, createNotification())
        startUdpServer()
        return START_STICKY
    }

    private fun startUdpServer() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryMonitor:CpuLock").apply {
            acquire()
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("BatteryLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        serviceScope.launch {
            try {
                // Používáme reuseAddress, aby se port rychle uvolnil pro další start
                socket = DatagramSocket(8888).apply { reuseAddress = true }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket?.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        if (message == "BATTERY_QUERY") {
                            forceWakeScreen()
                            sendBatteryStatus(packet.address, packet.port)
                        }
                    } catch (e: Exception) {
                        // Socket closed vyvolá výjimku při ukončení, to je v pořádku
                        if (isActive) Log.e("BatteryService", "Receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BatteryService", "UDP Server error: ${e.message}")
            }
        }
    }

    private fun forceWakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val screenLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "BatteryMonitor:ForceWake"
        )
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
            manager?.createNotificationChannel(channel)
        }

        // Vytvoření úmyslu pro tlačítko "Vypnout"
        val stopIntent = Intent(this, BatteryService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sledování baterie aktivní")
            .setContentText("PC se může dotazovat na stav")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Vypnout", stopPendingIntent) // Tlačítko v notifikaci
            .build()
    }

    override fun onDestroy() {
        Log.d("BatteryService", "Ukončování služby...")
        serviceScope.cancel() // Zastaví všechny coroutiny (včetně UDP smyčky)
        socket?.close()

        if (multicastLock?.isHeld == true) multicastLock?.release()
        if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}