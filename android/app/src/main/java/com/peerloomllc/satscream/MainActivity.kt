package com.peerloomllc.satscream

import android.Manifest
import com.peerloomllc.satscream.R
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var tvPrice: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var btnDarkMode: ImageButton

    // Alert buttons
    private lateinit var btnSetPumpAlert: Button
    private lateinit var tvPumpAlertStatus: TextView

    private lateinit var btnSetDumpAlert: Button
    private lateinit var tvDumpAlertStatus: TextView

    // Single large "alert hit" rocket shown in the center-bottom space
    private lateinit var ivAlertHit: ImageView
    private var hitAnimator: android.animation.AnimatorSet? = null
    // Which alert the rocket is currently showing for ("pump"/"dump"/null), so the
    // blast-off plays once per trigger episode rather than on every price tick.
    private var currentHitKey: String? = null

    private var isBitcoinStandardMode = false
    private var currentPrice: Double? = null

    // Info button
    private lateinit var btnInfo: ImageButton

    // Audio settings button
    private lateinit var btnAudioSettings: ImageButton

    private val dateFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)

    // Event-driven UI refresh: BitcoinService writes the latest price/alert state to
    // SharedPreferences, so we update only when a relevant key actually changes instead of
    // polling once per second. The service writes via apply() on a background thread, so the
    // callback can fire off the main thread — marshal UI work back with runOnUiThread.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Prefs.LAST_PRICE, Prefs.LAST_UPDATE_TIME,
                Prefs.PUMP_TRIGGERED, Prefs.DUMP_TRIGGERED ->
                    runOnUiThread { loadPriceFromPrefs() }
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is first launch
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val welcomeShown = sharedPrefs.getBoolean(Prefs.WELCOME_SHOWN, false)

        if (!welcomeShown) {
            // First launch - show welcome screen
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Load and apply dark mode preference BEFORE setContentView
        val isDarkMode = sharedPrefs.getBoolean(Prefs.DARK_MODE, false)

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
        btnDarkMode = findViewById(R.id.btnDarkMode)

        btnSetPumpAlert = findViewById(R.id.btnSetPumpAlert)
        tvPumpAlertStatus = findViewById(R.id.tvPumpAlertStatus)

        btnSetDumpAlert = findViewById(R.id.btnSetDumpAlert)
        tvDumpAlertStatus = findViewById(R.id.tvDumpAlertStatus)

        ivAlertHit = findViewById(R.id.ivAlertHit)

        btnInfo = findViewById(R.id.btnInfo)
        btnAudioSettings = findViewById(R.id.btnAudioSettings)

        // Load Bitcoin Standard mode preference
        isBitcoinStandardMode = sharedPrefs.getBoolean(Prefs.BITCOIN_STANDARD_MODE, false)

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
        // Live updates are driven by the SharedPreferences listener registered in onResume().
    }

    private fun setupDarkModeToggle(isDarkMode: Boolean) {
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)

        // Icon shows the current theme (moon when dark, sun when light); tap flips it.
        btnDarkMode.setImageResource(if (isDarkMode) R.drawable.ic_moon else R.drawable.ic_sun)

        btnDarkMode.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val newMode = !isDarkMode

            // Save preference
            sharedPrefs.edit { putBoolean(Prefs.DARK_MODE, newMode) }

            // Update widget with new theme
            BitcoinWidget.updateAllWidgets(this)

            // Apply new mode (recreates activity; onCreate re-reads the pref and
            // refreshes the icon).
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
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
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        sharedPrefs.edit {
            putBoolean(Prefs.BITCOIN_STANDARD_MODE, isBitcoinStandardMode)
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

        // Update alert status display (also reflects HIT state / icons consistently)
        updateAlertStatusDisplays()
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
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)

        // Load saved pump alert STATUS only
        val savedPumpPrice = sharedPrefs.getFloat(Prefs.PUMP_TARGET, 0f)
        if (savedPumpPrice > 0f) {
            tvPumpAlertStatus.text = "Pump alert: ${BtcPrice.formatUsd(savedPumpPrice.toDouble())}"
        } else {
            tvPumpAlertStatus.text = "No pump alert set"
        }

        // Load saved dump alert STATUS only
        val savedDumpPrice = sharedPrefs.getFloat(Prefs.DUMP_TARGET, 0f)
        if (savedDumpPrice > 0f) {
            tvDumpAlertStatus.text = "Dump alert: ${BtcPrice.formatUsd(savedDumpPrice.toDouble())}"
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
        // Custom theme rounds + colors the sheet's own surface (see themes.xml), so
        // no secondary background peeks out behind the rounded corners.
        val bottomSheetDialog = BottomSheetDialog(this, R.style.Theme_SatScream_BottomSheetDialog)
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

            val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)

            if (currentInput.isNotEmpty()) {
                val targetPrice = currentInput.toFloatOrNull()
                if (targetPrice != null && targetPrice > 0f) {
                    if (isPump) {
                        sharedPrefs.edit {
                            putFloat(Prefs.PUMP_TARGET, targetPrice)
                                .putBoolean(Prefs.PUMP_TRIGGERED, false)
                                .putBoolean(Prefs.PUMP_IS_BITCOIN_MODE, isBitcoinStandardMode)
                        }

                        // Update display immediately
                        if (isBitcoinStandardMode) {
                            tvPumpAlertStatus.text = "Pump alert: ${targetPrice.toLong()} sats/$"
                        } else {
                            tvPumpAlertStatus.text = "Pump alert: ${BtcPrice.formatUsd(targetPrice.toDouble())}"
                        }
                        Toast.makeText(this, "Pump Alert Set!", Toast.LENGTH_SHORT).show()
                    } else {
                        sharedPrefs.edit {
                            putFloat(Prefs.DUMP_TARGET, targetPrice)
                                .putBoolean(Prefs.DUMP_TRIGGERED, false)
                                .putBoolean(Prefs.DUMP_IS_BITCOIN_MODE, isBitcoinStandardMode)
                        }

                        // Update display immediately
                        if (isBitcoinStandardMode) {
                            tvDumpAlertStatus.text = "Dump alert: ${targetPrice.toLong()} sats/$"
                        } else {
                            tvDumpAlertStatus.text = "Dump alert: ${BtcPrice.formatUsd(targetPrice.toDouble())}"
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
                        remove(Prefs.PUMP_TARGET)
                            .remove(Prefs.PUMP_TRIGGERED)
                    }
                    tvPumpAlertStatus.text = "No pump alert set"
                    Toast.makeText(this, "Pump alert cleared", Toast.LENGTH_SHORT).show()
                } else {
                    sharedPrefs.edit {
                        remove(Prefs.DUMP_TARGET)
                            .remove(Prefs.DUMP_TRIGGERED)
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
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val lastPrice = sharedPrefs.getFloat(Prefs.LAST_PRICE, 0f)
        val lastUpdateTime = sharedPrefs.getString(Prefs.LAST_UPDATE_TIME, null)

        if (lastPrice > 0f) {
            // Capture the previous price before updatePriceDisplay overwrites it, so we
            // only flash on a genuine price change (not on alert-flag writes or resume).
            val previousPrice = currentPrice
            val newPrice = lastPrice.toDouble()

            updatePriceDisplay(newPrice)
            tvLastUpdated.text = lastUpdateTime ?: "Waiting for update..."

            if (previousPrice != null && newPrice != previousPrice) {
                flashPrice(up = newPrice > previousPrice)
            }

            // Update alert status displays based on triggered state
            updateAlertStatusDisplays()
        }
    }

    // Briefly tints the hero price green/red and gives it a small spring pulse on each
    // real tick. Always reflects BTC (USD) direction, even in Bitcoin Standard mode.
    private fun flashPrice(up: Boolean) {
        val flashColor = ContextCompat.getColor(this, if (up) R.color.price_up else R.color.price_down)
        val baseColor = ContextCompat.getColor(this, R.color.text_primary)

        tvPrice.animate().cancel()
        tvPrice.setTextColor(flashColor)
        tvPrice.scaleX = 1f
        tvPrice.scaleY = 1f
        tvPrice.animate()
            .scaleX(1.06f).scaleY(1.06f)
            .setDuration(120)
            .withEndAction {
                tvPrice.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            .start()
        tvPrice.postDelayed({ tvPrice.setTextColor(baseColor) }, 600)
    }

    @SuppressLint("SetTextI18n")
    private fun updatePriceDisplay(price: Double) {
        currentPrice = price

        tvPrice.text = if (isBitcoinStandardMode) {
            // Bitcoin Standard Mode: Show sats per dollar
            BtcPrice.formatSatsPerDollar(price)
        } else {
            // Fiat Mode: Show USD price
            BtcPrice.formatUsd(price)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh once to catch any update that landed while paused, then listen for changes.
        loadPriceFromPrefs()
        getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    @SuppressLint("SetTextI18n")
    private fun updateAlertStatusDisplays() {
        val sharedPrefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val lastPrice = sharedPrefs.getFloat(Prefs.LAST_PRICE, 0f)
        val isDarkMode = sharedPrefs.getBoolean(Prefs.DARK_MODE, false)

        val pumpTriggered = sharedPrefs.getBoolean(Prefs.PUMP_TRIGGERED, false)
        val dumpTriggered = sharedPrefs.getBoolean(Prefs.DUMP_TRIGGERED, false)

        renderAlertStatus(
            label = "Pump",
            target = sharedPrefs.getFloat(Prefs.PUMP_TARGET, 0f),
            triggered = pumpTriggered,
            wasSetInBitcoinMode = sharedPrefs.getBoolean(Prefs.PUMP_IS_BITCOIN_MODE, false),
            lastPrice = lastPrice,
            statusView = tvPumpAlertStatus
        )
        renderAlertStatus(
            label = "Dump",
            target = sharedPrefs.getFloat(Prefs.DUMP_TARGET, 0f),
            triggered = dumpTriggered,
            wasSetInBitcoinMode = sharedPrefs.getBoolean(Prefs.DUMP_IS_BITCOIN_MODE, false),
            lastPrice = lastPrice,
            statusView = tvDumpAlertStatus
        )

        // Rocket "blast off". Pump takes priority if both somehow fire at once.
        // Play the animation once per trigger episode (when the active alert
        // changes), not on every price tick.
        val hitKey = when {
            pumpTriggered -> "pump"
            dumpTriggered -> "dump"
            else -> null
        }
        if (hitKey != currentHitKey) {
            currentHitKey = hitKey
            when (hitKey) {
                "pump" -> playHitAnimation(
                    if (isDarkMode) R.drawable.ic_pump_hit_dark else R.drawable.ic_pump_hit_light,
                    isPump = true
                )
                "dump" -> playHitAnimation(
                    if (isDarkMode) R.drawable.ic_dump_hit_dark else R.drawable.ic_dump_hit_light,
                    isPump = false
                )
                else -> hideHitIcon()
            }
        }
    }

    /**
     * Flies the rocket across the full screen height — bottom→top for pump
     * (nose up), top→bottom for dump (nose down) — twice, with a subtle size
     * pulse, then hides it. The art points up-right, so it's rotated to sit
     * upright/inverted. No back-and-forth rotation.
     */
    private fun playHitAnimation(drawableRes: Int, isPump: Boolean) {
        hitAnimator?.cancel()
        val v = ivAlertHit
        v.setImageResource(drawableRes)
        // Point each rocket the way it travels (pump up, dump down). The two art
        // assets aren't oriented the same, hence the different angles.
        v.rotation = if (isPump) -45f else 45f
        v.scaleX = 1f
        v.scaleY = 1f
        v.visibility = View.VISIBLE

        // Off-screen edges relative to the centered layout position.
        val edge = resources.displayMetrics.heightPixels / 2f + 64f * resources.displayMetrics.density
        val startY = if (isPump) edge else -edge
        val endY = if (isPump) -edge else edge

        val travel = android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, startY, endY).apply {
            duration = 1400
            repeatCount = 1 // plays twice total
            repeatMode = android.animation.ValueAnimator.RESTART
            // Constant speed so the rocket is clearly visible the whole way in
            // both directions. An accelerating curve hid the dump pass (it
            // lingered off-screen at the top, then zipped down too fast).
            interpolator = android.view.animation.LinearInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = hideHitIcon()
            })
        }
        val scaleX = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_X, 0.9f, 1.12f).apply {
            duration = 500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
        }
        val scaleY = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.9f, 1.12f).apply {
            duration = 500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
        }
        hitAnimator = android.animation.AnimatorSet().apply {
            playTogether(travel, scaleX, scaleY)
            start()
        }
    }

    private fun hideHitIcon() {
        hitAnimator?.cancel()
        hitAnimator = null
        ivAlertHit.translationY = 0f
        ivAlertHit.visibility = View.GONE
    }

    /**
     * Renders a single alert's status line and hit icon, handling the Fiat <-> Bitcoin-Standard
     * display conversion. Replaces the former duplicated pump/dump blocks and the separate
     * updateAlertStatusDisplay()/updateAlertStatusDisplays() pair, so the firing and display
     * logic can no longer drift apart.
     */
    @SuppressLint("SetTextI18n")
    private fun renderAlertStatus(
        label: String,
        target: Float,
        triggered: Boolean,
        wasSetInBitcoinMode: Boolean,
        lastPrice: Float,
        statusView: TextView
    ) {
        if (target <= 0f) {
            statusView.text = "No ${label.lowercase(Locale.US)} alert set"
            return
        }

        if (triggered) {
            val priceText = if (isBitcoinStandardMode) {
                "${String.format(Locale.US, "%,d", BtcPrice.satsPerDollar(lastPrice.toDouble()))} sats/$"
            } else {
                BtcPrice.formatUsd(lastPrice.toDouble())
            }
            statusView.text = "${label.uppercase(Locale.US)} HIT: $priceText"
        } else {
            statusView.text = "$label alert: ${formatAlertTarget(target, wasSetInBitcoinMode)}"
        }
    }

    /**
     * Formats a stored alert target for display in the current mode, converting it if the
     * target was originally set in the other mode.
     */
    private fun formatAlertTarget(target: Float, wasSetInBitcoinMode: Boolean): String {
        return if (isBitcoinStandardMode) {
            if (wasSetInBitcoinMode) {
                // Entered as sats/$ — display as-is
                "${target.toLong()} sats/$"
            } else {
                // Entered as USD — convert to sats/$
                "${String.format(Locale.US, "%,d", BtcPrice.satsPerDollar(target.toDouble()))} sats/$"
            }
        } else {
            if (wasSetInBitcoinMode) {
                // Entered as sats/$ — convert to USD
                BtcPrice.formatUsd(BtcPrice.SATS_PER_BTC / target.toDouble())
            } else {
                // Entered as USD — display as-is
                BtcPrice.formatUsd(target.toDouble())
            }
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