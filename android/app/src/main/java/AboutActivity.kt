package com.peerloomllc.satscream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    private lateinit var tvNoWalletMessage: TextView
    private lateinit var tvLightningAddress: TextView
    private lateinit var btnLearnMoreWallets: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
        }

        // Set version info dynamically
        val tvVersionInfo = findViewById<TextView>(R.id.tvVersionInfo)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
            tvVersionInfo.text = "Version $versionName ($versionCode)"
        } catch (e: Exception) {
            tvVersionInfo.text = "Version 1.0.0"
        }

        tvNoWalletMessage = findViewById(R.id.tvNoWalletMessage)
        tvLightningAddress = findViewById(R.id.tvLightningAddress)
        btnLearnMoreWallets = findViewById(R.id.btnLearnMoreWallets)

        // Create styled text with clickable Lightning address
        val message = getString(R.string.lightning_address_message)
        val address = getString(R.string.lightning_address)
        val fullText = message + address

        val spannableString = android.text.SpannableString(fullText)

        // Find the position of the Lightning address in the full text
        val startIndex = message.length
        val endIndex = fullText.length

        // Style the address as a link (blue and underlined)
        val clickableSpan = object : android.text.style.ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                widget.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                copyLightningAddressToClipboard(address)
            }

            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
            }
        }

        spannableString.setSpan(
            clickableSpan,
            startIndex,
            endIndex,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Set the styled text
        tvLightningAddress.text = spannableString
        tvLightningAddress.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // Make links clickable
        tvNoWalletMessage.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        // Learn More button to open local HTML file
        btnLearnMoreWallets.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            openLocalHtmlFile()
        }

        val btnDonate = findViewById<Button>(R.id.btnDonate)
        btnDonate.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            openLightningWallet()
        }
    }

    private fun openLightningWallet() {
        val lightningAddress = getString(R.string.lightning_address)

        try {
            // Create a simple lightning intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("lightning:$lightningAddress")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Check if any app can handle this
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // No Lightning wallet found
                showNoWalletMessages()
            }
        } catch (e: Exception) {
            android.util.Log.e("AboutActivity", "Error opening Lightning wallet", e)
            Toast.makeText(this, "Could not open wallet. Error: ${e.message}", Toast.LENGTH_LONG).show()
            showNoWalletMessages()
        }
    }

    private fun showNoWalletMessages() {
        // Show first message with fade-in animation
        tvNoWalletMessage.visibility = android.view.View.VISIBLE
        tvNoWalletMessage.animate()
            .alpha(1f)
            .setDuration(2000)
            .withEndAction {
                // After first message completes, show learn more button
                btnLearnMoreWallets.visibility = android.view.View.VISIBLE
                btnLearnMoreWallets.animate()
                    .alpha(1f)
                    .setDuration(2000)
                    .withEndAction {
                        // After learn more button completes, show Lightning address
                        tvLightningAddress.visibility = android.view.View.VISIBLE
                        tvLightningAddress.animate()
                            .alpha(1f)
                            .setDuration(2000)
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun openLocalHtmlFile() {
        // Open the local HTML file from assets
        val intent = Intent(this, WebViewActivity::class.java)
        startActivity(intent)
    }

    private fun copyLightningAddressToClipboard(address: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Lightning Address", address)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Lightning address copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun finish() {
        super.finish()
        // Simple finish without custom transitions to avoid conflicts
    }
}