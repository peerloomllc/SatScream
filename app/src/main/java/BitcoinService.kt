package com.example.satscream

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class BitcoinService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)

    // Store last successful price to retain on failure
    private var lastSuccessfulPrice: Double? = null

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class BitcoinPrice(
        val bitcoin: BitcoinData
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class BitcoinData(
        val usd: Double
    )

    // Coinbase API response structure
    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class CoinbaseResponse(
        val data: CoinbaseData
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class CoinbaseData(
        val amount: String
    )

    companion object {
        private const val CHANNEL_ID = "BitcoinPriceChannel"
        private const val ALERT_CHANNEL_ID = "BitcoinAlertChannel_Silent"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        private const val API_URL_PRIMARY = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
        private const val API_URL_SECONDARY = "https://api.coinbase.com/v2/prices/BTC-USD/spot"

        // Shared Preference Keys
        const val PREFS_NAME = "BitcoinPrefs"
        const val KEY_PUMP_TARGET = "TARGET_PRICE_PUMP"
        const val KEY_DUMP_TARGET = "TARGET_PRICE_DUMP"
        const val KEY_PUMP_TRIGGERED = "PUMP_ALERT_TRIGGERED"
        const val KEY_DUMP_TRIGGERED = "DUMP_ALERT_TRIGGERED"
        const val KEY_LAST_PRICE = "LAST_PRICE"
        const val KEY_LAST_UPDATE_TIME = "LAST_UPDATE_TIME"
    }

    // Helper function to format price as whole number with commas
    // Example: 96500.50 -> "$96,500"
    private fun formatPrice(price: Double): String {
        val wholePrice = price.toLong()
        return String.format(Locale.US, "$%,d", wholePrice)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Starting Bitcoin price updates..."))

        scope.launch {
            while (isActive) {
                try {
                    val price = fetchBitcoinPrice()

                    // Access setup preferences
                    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

                    // 1. Get Targets
                    val pumpTarget = prefs.getFloat(KEY_PUMP_TARGET, 0f)
                    val dumpTarget = prefs.getFloat(KEY_DUMP_TARGET, 0f)

                    // 2. Get Trigger States
                    val pumpTriggered = prefs.getBoolean(KEY_PUMP_TRIGGERED, false)
                    val dumpTriggered = prefs.getBoolean(KEY_DUMP_TRIGGERED, false)

                    if (price != null) {
                        // Success - store and update
                        lastSuccessfulPrice = price

                        // Save price and timestamp to SharedPreferences for MainActivity
                        val currentTime = "Last updated: ${dateFormat.format(Date())}"
                        prefs.edit()
                            .putFloat(KEY_LAST_PRICE, price.toFloat())
                            .putString(KEY_LAST_UPDATE_TIME, currentTime)
                            .apply()

                        // Update widget with new price
                        BitcoinWidget.updateAllWidgets(this@BitcoinService)

                        // Update the silent ongoing notification with current price
                        val notification = createNotification("BTC: ${formatPrice(price)}")
                        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)

                        // Check if Bitcoin Standard Mode is enabled
                        val isBitcoinStandardMode = prefs.getBoolean("BITCOIN_STANDARD_MODE", false)

                        if (isBitcoinStandardMode) {
                            // ===========================
                            // BITCOIN STANDARD MODE LOGIC
                            // ===========================
                            val satsPerDollar = (100_000_000.0 / price)

                            // PUMP ALERT: sats per dollar LESS THAN OR EQUAL TO target (BTC price going UP)
                            if (pumpTarget > 0 && satsPerDollar <= pumpTarget && !pumpTriggered) {
                                // Mark as triggered
                                prefs.edit().putBoolean(KEY_PUMP_TRIGGERED, true).apply()

                                // Play Pump Sound
                                playAlertSound(isPump = true)

                                // Show Notification
                                val satsPerDollarLong = satsPerDollar.toLong()
                                val alertNotification = createAlertNotification(
                                    "PUMPING ALERT! ${String.format(Locale.US, "%,d", satsPerDollarLong)} sats/$",
                                    isPump = true
                                )
                                manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
                            }

                            // DUMP ALERT: sats per dollar GREATER THAN OR EQUAL TO target (BTC price going DOWN)
                            if (dumpTarget > 0 && satsPerDollar >= dumpTarget && !dumpTriggered) {
                                // Mark as triggered
                                prefs.edit().putBoolean(KEY_DUMP_TRIGGERED, true).apply()

                                // Play Dump Sound
                                playAlertSound(isPump = false)

                                // Show Notification
                                val satsPerDollarLong = satsPerDollar.toLong()
                                val alertNotification = createAlertNotification(
                                    "DUMPING ALERT! ${String.format(Locale.US, "%,d", satsPerDollarLong)} sats/$",
                                    isPump = false
                                )
                                manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
                            }

                            // Reset triggers when price moves away from thresholds
                            if (pumpTarget > 0 && satsPerDollar > pumpTarget * 1.01 && pumpTriggered) {
                                prefs.edit().putBoolean(KEY_PUMP_TRIGGERED, false).apply()
                            }
                            if (dumpTarget > 0 && satsPerDollar < dumpTarget * 0.99 && dumpTriggered) {
                                prefs.edit().putBoolean(KEY_DUMP_TRIGGERED, false).apply()
                            }

                        } else {
                            // ===================
                            // FIAT MODE LOGIC
                            // ===================

                            // PUMP ALERT: price GREATER THAN OR EQUAL TO target
                            if (pumpTarget > 0 && price >= pumpTarget && !pumpTriggered) {
                                // Mark as triggered
                                prefs.edit().putBoolean(KEY_PUMP_TRIGGERED, true).apply()

                                // Play Pump Sound
                                playAlertSound(isPump = true)

                                // Show Notification
                                val alertNotification = createAlertNotification(
                                    "PUMPING ALERT! BTC: ${formatPrice(price)}",
                                    isPump = true
                                )
                                manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
                            }

                            // DUMP ALERT: price LESS THAN OR EQUAL TO target
                            if (dumpTarget > 0 && price <= dumpTarget && !dumpTriggered) {
                                // Mark as triggered
                                prefs.edit().putBoolean(KEY_DUMP_TRIGGERED, true).apply()

                                // Play Dump Sound
                                playAlertSound(isPump = false)

                                // Show Notification
                                val alertNotification = createAlertNotification(
                                    "DUMPING ALERT! BTC: ${formatPrice(price)}",
                                    isPump = false
                                )
                                manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
                            }

                            // Reset triggers when price moves away from thresholds
                            if (pumpTarget > 0 && price < pumpTarget * 0.99 && pumpTriggered) {
                                prefs.edit().putBoolean(KEY_PUMP_TRIGGERED, false).apply()
                            }
                            if (dumpTarget > 0 && price > dumpTarget * 1.01 && dumpTriggered) {
                                prefs.edit().putBoolean(KEY_DUMP_TRIGGERED, false).apply()
                            }
                        }
                    } else {
                        // Price fetch failed - retain previous price in notification
                        if (lastSuccessfulPrice != null) {
                            val notification = createNotification("BTC: ${formatPrice(lastSuccessfulPrice!!)} (cached)")
                            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, notification)
                        }
                        // Note: We don't update SharedPreferences on failure to retain last good data
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(60000) // Update every 60 seconds
            }
        }
        return START_STICKY
    }

    suspend fun fetchBitcoinPrice(): Double? = suspendCancellableCoroutine { continuation ->
        // Try primary API first
        tryPrimaryApi(continuation)
    }

    private fun tryPrimaryApi(continuation: kotlinx.coroutines.CancellableContinuation<Double?>) {
        val request = Request.Builder().url(API_URL_PRIMARY).build()
        val call = client.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                // Primary failed, try secondary
                trySecondaryApi(continuation)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return@use

                    if (!it.isSuccessful) {
                        // Primary failed, try secondary
                        trySecondaryApi(continuation)
                        return@use
                    }

                    try {
                        val body = it.body?.string()
                        if (body == null) {
                            trySecondaryApi(continuation)
                            return@use
                        }

                        // Decode the JSON payload
                        val priceData: BitcoinPrice = json.decodeFromString(body)
                        continuation.resume(priceData.bitcoin.usd)
                    } catch (e: Exception) {
                        // Primary parsing failed, try secondary
                        trySecondaryApi(continuation)
                    }
                }
            }
        })
    }

    private fun trySecondaryApi(continuation: kotlinx.coroutines.CancellableContinuation<Double?>) {
        if (!continuation.isActive) return

        val request = Request.Builder().url(API_URL_SECONDARY).build()
        val call = client.newCall(request)

        // Don't register another cancellation handler - already registered in tryPrimaryApi

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return@use

                    if (!it.isSuccessful) {
                        continuation.resume(null)
                        return@use
                    }

                    try {
                        val body = it.body?.string()
                        if (body == null) {
                            continuation.resume(null)
                            return@use
                        }

                        // Decode Coinbase JSON
                        val priceData: CoinbaseResponse = json.decodeFromString(body)
                        val price = priceData.data.amount.toDoubleOrNull()
                        continuation.resume(price)
                    } catch (e: Exception) {
                        continuation.resume(null)
                    }
                }
            }
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Silent channel for ongoing price updates
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bitcoin Price Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current Bitcoin price"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)

            // Alert channel - HIGH importance with vibration but NO SOUND
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for Pumps and Dumps"

                // Disable strict notification sound so we can play custom sound via MediaPlayer
                setSound(null, null)

                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setBypassDnd(true) // Try to bypass Do Not Disturb
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(priceText: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bitcoin Price")
            .setContentText(priceText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createAlertNotification(alertText: String, isPump: Boolean): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if(isPump) "PUMP ALERT!" else "DUMP ALERT!"

        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(alertText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
    }

    private var mediaPlayer: MediaPlayer? = null

    private fun playAlertSound(isPump: Boolean) {
        try {
            // Release any existing MediaPlayer
            mediaPlayer?.release()
            mediaPlayer = null

            val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
            val customAudioKey = if (isPump) "CUSTOM_PUMP_AUDIO_PATH" else "CUSTOM_DUMP_AUDIO_PATH"
            val customAudioPath = sharedPrefs.getString(customAudioKey, null)

            var useCustomAudio = false

            // Check if custom audio exists and is valid
            if (customAudioPath != null && customAudioPath.isNotEmpty()) {
                val customFile = java.io.File(customAudioPath)
                if (customFile.exists() && customFile.canRead() && customFile.length() > 0) {
                    useCustomAudio = true
                } else {
                    // Custom file doesn't exist or can't be read - clean up the preference
                    android.util.Log.w("BitcoinService", "Custom audio file not found or unreadable, removing preference")
                    sharedPrefs.edit().remove(customAudioKey).apply()
                }
            }

            if (useCustomAudio && customAudioPath != null) {
                // Use custom audio file
                try {
                    mediaPlayer = MediaPlayer()
                    mediaPlayer?.setDataSource(customAudioPath)
                    mediaPlayer?.prepare()
                    mediaPlayer?.start()

                    return
                } catch (e: Exception) {
                    android.util.Log.e("BitcoinService", "Error playing custom audio, falling back to default", e)
                    // Fall through to default audio
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }

            // Use default audio from assets
            try {
                val assetFileName = if (isPump) "pump.wav" else "dump.wav"


                val afd = assets.openFd(assetFileName)

                mediaPlayer = MediaPlayer()
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                mediaPlayer?.prepare()
                mediaPlayer?.start()

            } catch (e: Exception) {
                android.util.Log.e("BitcoinService", "Error playing default audio from assets", e)
                mediaPlayer?.release()
                mediaPlayer = null
                throw e
            }

        } catch (e: Exception) {
            android.util.Log.e("BitcoinService", "Error in playAlertSound", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        client.dispatcher.executorService.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}