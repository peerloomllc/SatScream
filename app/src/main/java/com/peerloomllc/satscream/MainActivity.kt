package com.peerloomllc.satscream

import android.Manifest
import com.peerloomllc.satscream.R
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var tvPrice: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var switchDarkMode: SwitchMaterial

    // Alert buttons
    private lateinit var btnSetPumpAlert: Button
    private lateinit var tvPumpAlertStatus: TextView
    private lateinit var ivPumpIcon: ImageView

    private lateinit var btnSetDumpAlert: Button
    private lateinit var tvDumpAlertStatus: TextView
    private lateinit var ivDumpIcon: ImageView

    private var isBitcoinStandardMode = false
    private val PREF_BITCOIN_STANDARD_MODE = "BITCOIN_STANDARD_MODE"
    private var currentPrice: Double? = null

    // Info button
    private lateinit var btnInfo: ImageButton

    // Audio settings button
    private lateinit var btnAudioSettings: ImageButton

    private val dateFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)

    companion object {
        private const val PREF_DARK_MODE = "DARK_MODE"
        private const val PREF_LAST_PRICE = "LAST_PRICE"
        private const val PREF_LAST_UPDATE_TIME = "LAST_UPDATE_TIME"
    }

    // Helper function to format price as whole number with commas
    private fun formatPrice(price: Double): String {
        val wholePrice = price.toLong()
        return String.format(Locale.US, "$%,d", wholePrice)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is first launch
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        val welcomeShown = sharedPrefs.getBoolean("WELCOME_SHOWN", false)

        if (!welcomeShown) {
            // First launch - show welcome screen
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Load and apply dark mode preference BEFORE setContentView
        val isDarkMode = sharedPrefs.getBoolean(PREF_DARK_MODE, false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        // Request notification permission for Android 13+ (CRITICAL FOR NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // Initialize all views
        tvPrice = findViewById(R.id.tvPrice)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        switchDarkMode = findViewById(R.id.switchDarkMode)

        btnSetPumpAlert = findViewById(R.id.btnSetPumpAlert)
        tvPumpAlertStatus = findViewById(R.id.tvPumpAlertStatus)
        ivPumpIcon = findViewById(R.id.ivPumpIcon)

        btnSetDumpAlert = findViewById(R.id.btnSetDumpAlert)
        tvDumpAlertStatus = findViewById(R.id.tvDumpAlertStatus)
        ivDumpIcon = findViewById(R.id.ivDumpIcon)

        btnInfo = findViewById(R.id.btnInfo)
        btnAudioSettings = findViewById(R.id.btnAudioSettings)

        // Load Bitcoin Standard mode preference
        isBitcoinStandardMode = sharedPrefs.getBoolean(PREF_BITCOIN_STANDARD_MODE, false)

        // Setup Bitcoin Standard mode toggle on tvPrice click
        tvPrice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            toggleBitcoinStandardMode()
        }

        // Setup Dark Mode toggle (after views are initialized)
        setupDarkModeToggle(isDarkMode)

        // Setup info button
        setupInfoButton()

        // Setup audio settings button
        setupAudioSettingsButton()

        // Start background service
        startBitcoinService()

        // Setup UI
        setupAlertUI()

        // Load initial price from SharedPreferences (set by BitcoinService)
        loadPriceFromPrefs()

        // Start monitoring for price updates from service
        startPriceMonitoring()
    }

    private fun setupDarkModeToggle(isDarkMode: Boolean) {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)

        // Set initial state WITHOUT triggering listener
        switchDarkMode.setOnCheckedChangeListener(null)
        switchDarkMode.isChecked = isDarkMode

        // Now set the listener
        switchDarkMode.setOnCheckedChangeListener { view, isChecked ->
            // Only proceed if the value actually changed (prevents recreation loop)
            if (isChecked != isDarkMode) {
                // Haptic feedback
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                // Save preference
                sharedPrefs.edit { putBoolean(PREF_DARK_MODE, isChecked) }

                // Update widget with new theme
                BitcoinWidget.updateAllWidgets(this)

                // Apply new mode (recreates activity)
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }
    }

    private fun setupInfoButton() {
        btnInfo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val intent = Intent(this, AboutActivity::class.java)

            // Use ActivityOptions for slide animation (modern approach)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            startActivity(intent, options.toBundle())
        }
    }

    private fun setupAudioSettingsButton() {
        btnAudioSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val intent = Intent(this, AudioSettingsActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            startActivity(intent, options.toBundle())
        }
    }

    private fun toggleBitcoinStandardMode() {
        isBitcoinStandardMode = !isBitcoinStandardMode

        // Save preference
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        sharedPrefs.edit {
            putBoolean(PREF_BITCOIN_STANDARD_MODE, isBitcoinStandardMode)
        }


        // Update widget with new mode
        BitcoinWidget.updateAllWidgets(this)

        // Show toast
        val message = if (isBitcoinStandardMode) {
            "Bitcoin Standard Mode engaged"
        } else {
            "Fiat Mode engaged"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Update display with current price
        currentPrice?.let { price ->
            updatePriceDisplay(price)
        }

        // Update alert status display
        updateAlertStatusDisplay()
    }

    private fun startBitcoinService() {
        val serviceIntent = Intent(this, BitcoinService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("SetTextI18n")
    private fun setupAlertUI() {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)

        // Load saved pump alert STATUS only
        val savedPumpPrice = sharedPrefs.getFloat("TARGET_PRICE_PUMP", 0f)
        if (savedPumpPrice > 0f) {
            tvPumpAlertStatus.text = "Pump alert: ${formatPrice(savedPumpPrice.toDouble())}"
        } else {
            tvPumpAlertStatus.text = "No pump alert set"
        }

        // Load saved dump alert STATUS only
        val savedDumpPrice = sharedPrefs.getFloat("TARGET_PRICE_DUMP", 0f)
        if (savedDumpPrice > 0f) {
            tvDumpAlertStatus.text = "Dump alert: ${formatPrice(savedDumpPrice.toDouble())}"
        } else {
            tvDumpAlertStatus.text = "No dump alert set"
        }

        // Pump alert button - show bottom sheet
        btnSetPumpAlert.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            showPriceInputBottomSheet(isPump = true)
        }

        // Dump alert button - show bottom sheet
        btnSetDumpAlert.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            showPriceInputBottomSheet(isPump = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("SetTextI18n", "InflateParams")
    private fun showPriceInputBottomSheet(isPump: Boolean) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_price_input, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Set title based on alert type
        val tvTitle = bottomSheetView.findViewById<TextView>(R.id.tvBottomSheetTitle)
        tvTitle.text = if (isPump) "Set Pump Alert" else "Set Dump Alert"

        // Get views
        val tvPriceInput = bottomSheetView.findViewById<TextView>(R.id.tvPriceInput)
        val btnCreateAlert = bottomSheetView.findViewById<Button>(R.id.btnCreateAlert)
        val btnCancel = bottomSheetView.findViewById<Button>(R.id.btnCancel)

        // Number buttons
        val numberButtons = listOf(
            bottomSheetView.findViewById<Button>(R.id.btn0),
            bottomSheetView.findViewById<Button>(R.id.btn1),
            bottomSheetView.findViewById<Button>(R.id.btn2),
            bottomSheetView.findViewById<Button>(R.id.btn3),
            bottomSheetView.findViewById<Button>(R.id.btn4),
            bottomSheetView.findViewById<Button>(R.id.btn5),
            bottomSheetView.findViewById<Button>(R.id.btn6),
            bottomSheetView.findViewById<Button>(R.id.btn7),
            bottomSheetView.findViewById<Button>(R.id.btn8),
            bottomSheetView.findViewById<Button>(R.id.btn9)
        )

        val btnBackspace = bottomSheetView.findViewById<Button>(R.id.btnBackspace)
        val btnClear = bottomSheetView.findViewById<Button>(R.id.btnClear)

        // Track current input
        var currentInput = ""

        // Update display
        fun updateDisplay() {
            if (isBitcoinStandardMode) {
                // Bitcoin Standard Mode: Show number with "/$" suffix
                tvPriceInput.text = if (currentInput.isEmpty()) {
                    "0/$"
                } else {
                    val number = currentInput.toLongOrNull() ?: 0L
                    String.format(Locale.US, "%,d/$", number)
                }
            } else {
                // Fiat Mode: Show "$" prefix
                tvPriceInput.text = if (currentInput.isEmpty()) {
                    "$0"
                } else {
                    val number = currentInput.toLongOrNull() ?: 0L
                    String.format(Locale.US, "$%,d", number)
                }
            }
        }

        // Initialize display when bottom sheet loads
        updateDisplay()

        // Number button clicks
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                // Limit to 10 digits
                if (currentInput.length < 10) {
                    currentInput += index.toString()
                    updateDisplay()
                }
            }
        }

        // Backspace
        btnBackspace.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (currentInput.isNotEmpty()) {
                currentInput = currentInput.dropLast(1)
                updateDisplay()
            }
        }

        // Clear
        btnClear.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentInput = ""
            updateDisplay()
        }

        // Cancel
        btnCancel.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            bottomSheetDialog.dismiss()
        }

        // Create Alert
        btnCreateAlert.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

            val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)

            if (currentInput.isNotEmpty()) {
                val targetPrice = currentInput.toFloatOrNull()
                if (targetPrice != null && targetPrice > 0f) {
                    if (isPump) {
                        sharedPrefs.edit {
                            putFloat("TARGET_PRICE_PUMP", targetPrice)
                                .putBoolean("PUMP_ALERT_TRIGGERED", false)
                                .putBoolean("PUMP_ALERT_IS_BITCOIN_MODE", isBitcoinStandardMode)
                        }

                        // Update display immediately
                        if (isBitcoinStandardMode) {
                            tvPumpAlertStatus.text = "Pump alert: ${targetPrice.toLong()} sats/$"
                        } else {
                            tvPumpAlertStatus.text = "Pump alert: ${formatPrice(targetPrice.toDouble())}"
                        }
                        Toast.makeText(this, "Pump Alert Set!", Toast.LENGTH_SHORT).show()
                    } else {
                        sharedPrefs.edit {
                            putFloat("TARGET_PRICE_DUMP", targetPrice)
                                .putBoolean("DUMP_ALERT_TRIGGERED", false)
                                .putBoolean("DUMP_ALERT_IS_BITCOIN_MODE", isBitcoinStandardMode)
                        }

                        // Update display immediately
                        if (isBitcoinStandardMode) {
                            tvDumpAlertStatus.text = "Dump alert: ${targetPrice.toLong()} sats/$"
                        } else {
                            tvDumpAlertStatus.text = "Dump alert: ${formatPrice(targetPrice.toDouble())}"
                        }
                        Toast.makeText(this, "Dump Alert Set!", Toast.LENGTH_SHORT).show()
                    }
                    bottomSheetDialog.dismiss()
                } else {
                    Toast.makeText(this, "Please enter a valid price > $0", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Empty input means clear the alert
                if (isPump) {
                    sharedPrefs.edit {
                        remove("TARGET_PRICE_PUMP")
                            .remove("PUMP_ALERT_TRIGGERED")
                    }
                    tvPumpAlertStatus.text = "No pump alert set"
                    Toast.makeText(this, "Pump alert cleared", Toast.LENGTH_SHORT).show()
                } else {
                    sharedPrefs.edit {
                        remove("TARGET_PRICE_DUMP")
                            .remove("DUMP_ALERT_TRIGGERED")
                    }
                    tvDumpAlertStatus.text = "No dump alert set"
                    Toast.makeText(this, "Dump alert cleared", Toast.LENGTH_SHORT).show()
                }
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.show()
    }

    private fun loadPriceFromPrefs() {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        val lastPrice = sharedPrefs.getFloat(PREF_LAST_PRICE, 0f)
        val lastUpdateTime = sharedPrefs.getString(PREF_LAST_UPDATE_TIME, null)

        if (lastPrice > 0f) {
            currentPrice = lastPrice.toDouble()
            updatePriceDisplay(currentPrice!!)
            tvLastUpdated.text = lastUpdateTime ?: "Waiting for update..."

            // Update alert status displays based on triggered state
            updateAlertStatusDisplays()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePriceDisplay(price: Double) {
        currentPrice = price

        if (isBitcoinStandardMode) {
            // Bitcoin Standard Mode: Show sats per dollar
            val satsPerDollar = (100_000_000.0 / price).toLong()
            tvPrice.text = "${String.format(Locale.US, "%,d", satsPerDollar)}/$"
        } else {
            // Fiat Mode: Show USD price
            tvPrice.text = formatPrice(price)
        }
    }

    private fun startPriceMonitoring() {
        lifecycleScope.launch {
            while (isActive) {
                // Just read the price that BitcoinService already fetched
                loadPriceFromPrefs()
                delay(1000) // Check every 1 second for faster UI updates
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAlertStatusDisplay() {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        val pumpTarget = sharedPrefs.getFloat("TARGET_PRICE_PUMP", 0f)
        val dumpTarget = sharedPrefs.getFloat("TARGET_PRICE_DUMP", 0f)
        val pumpWasSetInBitcoinMode = sharedPrefs.getBoolean("PUMP_ALERT_IS_BITCOIN_MODE", false)
        val dumpWasSetInBitcoinMode = sharedPrefs.getBoolean("DUMP_ALERT_IS_BITCOIN_MODE", false)

        // PUMP ALERT DISPLAY
        if (pumpTarget > 0) {
            if (isBitcoinStandardMode) {
                // Currently in Bitcoin Standard Mode
                if (pumpWasSetInBitcoinMode) {
                    // Was set in Bitcoin mode, display as-is
                    tvPumpAlertStatus.text = "Pump alert: ${pumpTarget.toLong()} sats/$"
                } else {
                    // Was set in Fiat mode, convert USD to sats/$
                    val satsPerDollar = (100_000_000.0 / pumpTarget.toDouble()).toLong()
                    tvPumpAlertStatus.text = "Pump alert: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                }
            } else {
                // Currently in Fiat Mode
                if (pumpWasSetInBitcoinMode) {
                    // Was set in Bitcoin mode, convert sats/$ to USD
                    val usdPrice = 100_000_000.0 / pumpTarget.toDouble()
                    tvPumpAlertStatus.text = "Pump alert: ${formatPrice(usdPrice)}"
                } else {
                    // Was set in Fiat mode, display as-is
                    tvPumpAlertStatus.text = "Pump alert: ${formatPrice(pumpTarget.toDouble())}"
                }
            }
        } else {
            tvPumpAlertStatus.text = "No pump alert set"
        }

        // DUMP ALERT DISPLAY
        if (dumpTarget > 0) {
            if (isBitcoinStandardMode) {
                // Currently in Bitcoin Standard Mode
                if (dumpWasSetInBitcoinMode) {
                    // Was set in Bitcoin mode, display as-is
                    tvDumpAlertStatus.text = "Dump alert: ${dumpTarget.toLong()} sats/$"
                } else {
                    // Was set in Fiat mode, convert USD to sats/$
                    val satsPerDollar = (100_000_000.0 / dumpTarget.toDouble()).toLong()
                    tvDumpAlertStatus.text = "Dump alert: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                }
            } else {
                // Currently in Fiat Mode
                if (dumpWasSetInBitcoinMode) {
                    // Was set in Bitcoin mode, convert sats/$ to USD
                    val usdPrice = 100_000_000.0 / dumpTarget.toDouble()
                    tvDumpAlertStatus.text = "Dump alert: ${formatPrice(usdPrice)}"
                } else {
                    // Was set in Fiat mode, display as-is
                    tvDumpAlertStatus.text = "Dump alert: ${formatPrice(dumpTarget.toDouble())}"
                }
            }
        } else {
            tvDumpAlertStatus.text = "No dump alert set"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAlertStatusDisplays() {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        val lastPrice = sharedPrefs.getFloat(PREF_LAST_PRICE, 0f)

        // Determine which icon set to use based on current theme
        val isDarkMode = sharedPrefs.getBoolean(PREF_DARK_MODE, false)
        val pumpIcon = if (isDarkMode) R.drawable.ic_pump_hit_dark else R.drawable.ic_pump_hit_light
        val dumpIcon = if (isDarkMode) R.drawable.ic_dump_hit_dark else R.drawable.ic_dump_hit_light

        // Update PUMP alert display
        val pumpTarget = sharedPrefs.getFloat("TARGET_PRICE_PUMP", 0f)
        val pumpTriggered = sharedPrefs.getBoolean("PUMP_ALERT_TRIGGERED", false)
        val pumpWasSetInBitcoinMode = sharedPrefs.getBoolean("PUMP_ALERT_IS_BITCOIN_MODE", false)

        if (pumpTarget > 0f) {
            if (pumpTriggered) {
                if (isBitcoinStandardMode) {
                    val satsPerDollar = (100_000_000.0 / lastPrice.toDouble()).toLong()
                    tvPumpAlertStatus.text = "PUMP HIT: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                } else {
                    tvPumpAlertStatus.text = "PUMP HIT: ${formatPrice(lastPrice.toDouble())}"
                }
                // Show the pump icon
                ivPumpIcon.setImageResource(pumpIcon)
                ivPumpIcon.visibility = View.VISIBLE
            } else {
                // Not triggered - use conversion logic from updateAlertStatusDisplay
                if (isBitcoinStandardMode) {
                    if (pumpWasSetInBitcoinMode) {
                        tvPumpAlertStatus.text = "Pump alert: ${pumpTarget.toLong()} sats/$"
                    } else {
                        val satsPerDollar = (100_000_000.0 / pumpTarget.toDouble()).toLong()
                        tvPumpAlertStatus.text = "Pump alert: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                    }
                } else {
                    if (pumpWasSetInBitcoinMode) {
                        val usdPrice = 100_000_000.0 / pumpTarget.toDouble()
                        tvPumpAlertStatus.text = "Pump alert: ${formatPrice(usdPrice)}"
                    } else {
                        tvPumpAlertStatus.text = "Pump alert: ${formatPrice(pumpTarget.toDouble())}"
                    }
                }
                // Hide the pump icon
                ivPumpIcon.visibility = View.INVISIBLE
            }
        } else {
            tvPumpAlertStatus.text = "No pump alert set"
            // Hide the pump icon
            ivPumpIcon.visibility = View.INVISIBLE
        }

        // Update DUMP alert display
        val dumpTarget = sharedPrefs.getFloat("TARGET_PRICE_DUMP", 0f)
        val dumpTriggered = sharedPrefs.getBoolean("DUMP_ALERT_TRIGGERED", false)
        val dumpWasSetInBitcoinMode = sharedPrefs.getBoolean("DUMP_ALERT_IS_BITCOIN_MODE", false)

        if (dumpTarget > 0f) {
            if (dumpTriggered) {
                if (isBitcoinStandardMode) {
                    val satsPerDollar = (100_000_000.0 / lastPrice.toDouble()).toLong()
                    tvDumpAlertStatus.text = "DUMP HIT: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                } else {
                    tvDumpAlertStatus.text = "DUMP HIT: ${formatPrice(lastPrice.toDouble())}"
                }
                // Show the dump icon
                ivDumpIcon.setImageResource(dumpIcon)
                ivDumpIcon.visibility = View.VISIBLE
            } else {
                // Not triggered - use conversion logic from updateAlertStatusDisplay
                if (isBitcoinStandardMode) {
                    if (dumpWasSetInBitcoinMode) {
                        tvDumpAlertStatus.text = "Dump alert: ${dumpTarget.toLong()} sats/$"
                    } else {
                        val satsPerDollar = (100_000_000.0 / dumpTarget.toDouble()).toLong()
                        tvDumpAlertStatus.text = "Dump alert: ${String.format(Locale.US, "%,d", satsPerDollar)} sats/$"
                    }
                } else {
                    if (dumpWasSetInBitcoinMode) {
                        val usdPrice = 100_000_000.0 / dumpTarget.toDouble()
                        tvDumpAlertStatus.text = "Dump alert: ${formatPrice(usdPrice)}"
                    } else {
                        tvDumpAlertStatus.text = "Dump alert: ${formatPrice(dumpTarget.toDouble())}"
                    }
                }
                // Hide the dump icon
                ivDumpIcon.visibility = View.INVISIBLE
            }
        } else {
            tvDumpAlertStatus.text = "No dump alert set"
            // Hide the dump icon
            ivDumpIcon.visibility = View.INVISIBLE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SAVED_PRICE", tvPrice.text.toString())
        outState.putString("SAVED_LAST_UPDATED", tvLastUpdated.text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("SAVED_PRICE")?.let { savedPrice ->
            if (savedPrice != getString(R.string.loading)) {
                tvPrice.text = savedPrice
            }
        }
        savedInstanceState.getString("SAVED_LAST_UPDATED")?.let { savedTime ->
            tvLastUpdated.text = savedTime
        }
    }
}