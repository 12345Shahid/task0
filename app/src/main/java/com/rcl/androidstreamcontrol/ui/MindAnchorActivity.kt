package com.rcl.androidstreamcontrol.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MindAnchorActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        supportActionBar?.title = "Mind Anchor"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // Required for LocalStorage
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)

        // Expose an Android interface so the web page can save mood to native SharedPreferences
        webView.addJavascriptInterface(AndroidBridge(this), "Android")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("file:///android_asset/mind-anchor/index.html")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** JavaScript interface to allow the WebView to write to SharedPreferences */
    inner class AndroidBridge(private val context: Context) {
        @JavascriptInterface
        fun savePreference(key: String, value: String) {
            context.getSharedPreferences("MindAnchorPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .apply()
        }

        @JavascriptInterface
        fun getPreference(key: String, defaultValue: String): String {
            return context.getSharedPreferences("MindAnchorPrefs", Context.MODE_PRIVATE)
                .getString(key, defaultValue) ?: defaultValue
        }
    }
}
