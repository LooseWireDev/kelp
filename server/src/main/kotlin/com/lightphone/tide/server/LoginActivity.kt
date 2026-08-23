package com.lightphone.tide.server

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lightphone.tide.protocol.AuthSnapshot
import com.lightphone.tide.protocol.AuthState
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TideRuntime.initialize(applicationContext)
        val loginUri = TideRuntime.loginUri()
        if (loginUri == null) {
            showError(authStateMessage(TideRuntime.currentAuthSnapshot()))
            return
        }

        val webView = WebView(this).also { this.webView = it }
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleRedirect(request.url)
            }
        }
        webView.loadUrl(loginUri.toString())
    }

    private fun handleRedirect(uri: Uri): Boolean {
        if (!TideRuntime.isRedirectUri(uri)) return false
        lifecycleScope.launch {
            if (TideRuntime.finalizeLogin(uri)) {
                finish()
            } else {
                showError(authStateMessage(TideRuntime.currentAuthSnapshot()))
            }
        }
        return true
    }

    private fun showError(message: String) {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            webView = null
        }
        setContentView(TextView(this).apply {
            text = message
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
        })
    }

    private fun authStateMessage(snapshot: AuthSnapshot): String = when (snapshot.state) {
        AuthState.MissingConfiguration ->
            "TIDAL credentials are missing. Add them to local.properties and rebuild Tide."
        AuthState.Error -> snapshot.errorMessage ?: "TIDAL sign in failed."
        else -> "Could not start TIDAL sign in."
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
