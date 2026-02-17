package com.peerloomllc.satscream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val webView = findViewById<WebView>(R.id.webView)
        val btnBack = findViewById<ImageButton>(R.id.btnBackWeb)

        // Configure WebView
        webView.settings.javaScriptEnabled = false

        // Custom WebViewClient to handle link clicks
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // If it's a file:// URL (our local HTML), load it in WebView
                if (url?.startsWith("file://") == true) {
                    return false
                }

                // For all other URLs (https://, http://), open in external browser
                url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    startActivity(intent)
                }
                return true
            }
        }

        // Load the local HTML file from assets
        webView.loadUrl("file:///android_asset/use-lightning-network-modified.html")

        // Back button
        btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overrideActivityTransition(
            OVERRIDE_TRANSITION_CLOSE,
            android.R.anim.slide_in_left,
            android.R.anim.slide_out_right
        )
    }
}