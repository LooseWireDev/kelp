package com.loosewire.kelp.server

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.AuthState
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startLogin()
    }

    /**
     * Two chained sign-ins on one WebView:
     * 1. Developer-app PKCE (catalog v2 API) → redirects to `kelp://auth`.
     * 2. First-party PKCE (full-length playback) → redirects to
     *    `https://tidal.com/android/login/auth`.
     * WebView cookies carry the TIDAL session into step two.
     */
    private fun startLogin() {
        TideRuntime.initialize(applicationContext)
        val loginUri = nextLoginUri()
        if (loginUri == null) {
            finish()
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
                val uri = request.url
                return handleRedirect(uri, view) || !uri.scheme.equals("https", ignoreCase = true)
            }
        }
        webView.loadUrl(loginUri.toString())
    }

    private fun nextLoginUri(): Uri? = when {
        !TideRuntime.developerLoggedIn() -> TideRuntime.loginUri()
        TideRuntime.streamingNeedsLogin() -> TideRuntime.streamingLoginUri()
        else -> null
    }

    private fun handleRedirect(uri: Uri, view: WebView): Boolean {
        when {
            TideRuntime.isStreamingRedirectUri(uri) -> lifecycleScope.launch {
                if (TideRuntime.finalizeStreamingLogin(uri)) {
                    continueSignIn(view)
                } else {
                    showError(authStateMessage(TideRuntime.currentAuthSnapshot()))
                }
            }

            TideRuntime.isRedirectUri(uri) -> lifecycleScope.launch {
                if (TideRuntime.finalizeLogin(uri)) {
                    continueSignIn(view)
                } else {
                    showError(authStateMessage(TideRuntime.currentAuthSnapshot()))
                }
            }

            else -> return false
        }
        return true
    }

    private fun continueSignIn(view: WebView) {
        val next = nextLoginUri()
        if (next == null) {
            if (!TideRuntime.developerLoggedIn()) {
                showError(authStateMessage(TideRuntime.currentAuthSnapshot()))
            } else {
                finish()
            }
            return
        }
        if (view === webView) view.loadUrl(next.toString()) else startLogin()
    }

    private fun showError(message: String) {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            webView = null
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                text = message
                gravity = android.view.Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "Retry"
                setOnClickListener { startLogin() }
            })
            addView(Button(context).apply {
                text = "Back"
                setOnClickListener { finish() }
            })
        })
    }

    private fun authStateMessage(snapshot: AuthSnapshot): String = when (snapshot.state) {
        AuthState.MissingConfiguration ->
            "The TIDAL client ID is missing. Add it to local.properties and rebuild Kelp."
        AuthState.Error -> snapshot.errorMessage ?: "TIDAL sign in failed."
        else -> "Could not start TIDAL sign in."
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
