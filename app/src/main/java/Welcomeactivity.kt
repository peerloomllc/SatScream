package com.peerloomllc.satscream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var tvWelcomeTitle: TextView
    private lateinit var tvWelcomeMessage: TextView
    private lateinit var llButtons: LinearLayout
    private lateinit var btnLearnMore: Button
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle)
        tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage)
        llButtons = findViewById(R.id.llButtons)
        btnLearnMore = findViewById(R.id.btnLearnMore)
        btnContinue = findViewById(R.id.btnContinue)

        // Start the fade sequence
        startWelcomeSequence()

        // Button click listeners
        btnLearnMore.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            openBitcoinCrashCourse()
        }

        btnContinue.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            finishWelcome()
        }
    }

    private fun startWelcomeSequence() {
        // Step 1: Fade in "Welcome, Pleb" (2s fade in)
        tvWelcomeTitle.visibility = android.view.View.VISIBLE
        tvWelcomeTitle.animate()
            .alpha(1f)
            .setDuration(2000)
            .withEndAction {
                // Step 2: Wait, then fade out (2s fade out)
                tvWelcomeTitle.animate()
                    .alpha(0f)
                    .setDuration(2000)
                    .withEndAction {
                        // Step 3: Hide title and show message (2s fade in)
                        tvWelcomeTitle.visibility = android.view.View.GONE
                        tvWelcomeMessage.visibility = android.view.View.VISIBLE
                        tvWelcomeMessage.animate()
                            .alpha(1f)
                            .setDuration(2000)
                            .withEndAction {
                                // Step 4: Show buttons (2s fade in)
                                llButtons.visibility = android.view.View.VISIBLE
                                llButtons.animate()
                                    .alpha(1f)
                                    .setDuration(2000)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun openBitcoinCrashCourse() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nakamotoinstitute.org/crash-course/"))
        startActivity(intent)
    }

    private fun finishWelcome() {
        // Mark welcome as shown
        val prefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("WELCOME_SHOWN", true).apply()

        // Navigate to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}