package com.lechixy.kick

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.AuthRepository
import com.lechixy.kick.data.account.SessionStatus
import com.lechixy.kick.data.account.userMessage
import com.lechixy.kick.ui.components.account.createKickLoginWebViewContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Login directly on Kick; only the verified session is retained in encrypted device storage. */
class KickWebLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var verifying by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var attemptedCookies: String? = null
    private var generation = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        generation = AccountGraph.auth.startLogin()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies { flush(); ready = true }
        }
        WebStorage.getInstance().deleteAllData()
        lifecycleScope.launch {
            AccountGraph.auth.session.collect {
                if (it.generation != generation) finish()
            }
        }
        // Kick can complete login through client-side routing without another
        // onPageFinished callback. Only inspect this WebView's own cookie jar.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (ready) captureSession()
                    delay(1000)
                }
            }
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kick ile giriş yap", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { finish() }) { Text("Kapat") }
                        }
                        Text("Kick hesabınızla giriş yapın.", Modifier.padding(horizontal = 12.dp))
                        if (verifying) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Oturum doğrulanıyor…", Modifier.padding(8.dp))
                        }
                        error?.let {
                            Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
                            Row {
                                TextButton(onClick = { attemptedCookies = null; captureSession() }, enabled = !verifying) { Text("Doğrulamayı yeniden dene") }
                                TextButton(onClick = { restartLogin() }, enabled = !verifying) { Text("Başka hesapla giriş yap") }
                            }
                        }
                        if (ready) AndroidView(factory = { context ->
                            createKickLoginWebViewContainer(context,
                                onCreated = { view ->
                                    webView = view
                                    view.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            if (!request.isForMainFrame) return false
                                            val uri = request.url
                                            val host = uri.host.orEmpty()
                                            val allowed = uri.scheme == "https" && (host == "kick.com" || host.endsWith(".kick.com"))
                                            if (!allowed) error = "Bu giriş ekranında Kick e-posta girişini kullanın; dış site yönlendirmesi açılmadı."
                                            return !allowed
                                        }
                                        override fun onPageFinished(view: WebView, url: String?) {
                                            if (url?.startsWith("https://kick.com/") == true) captureSession()
                                        }
                                    }
                                },
                                onSized = { it.loadUrl("https://kick.com/login") }
                            )
                        }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }

    private fun captureSession() {
        if (verifying || isFinishing) return
        val view = webView ?: return
        val manager = CookieManager.getInstance()
        val kickCookies = manager.getCookie("https://kick.com").orEmpty()
        if (kickCookies == attemptedCookies || AuthRepository.cookie(kickCookies, "session_token") == null) return
        val webCookies = manager.getCookie("https://web.kick.com").orEmpty()
        attemptedCookies = kickCookies
        verifying = true; error = null
        lifecycleScope.launch {
            try {
                if (generation != AccountGraph.auth.session.value.generation) { finish(); return@launch }
                AccountGraph.auth.connect(kickCookies, webCookies, view.settings.userAgentString, generation)
                finish()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = if (failure is com.lechixy.kick.data.account.AccountApiException)
                    "Kick oturumunu doğrulayamadı (HTTP ${failure.status}). Yeniden deneyin."
                    else failure.userMessage()
            } finally { verifying = false }
        }
    }

    private fun restartLogin() {
        attemptedCookies = null; error = null
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if (!isFinishing) webView?.loadUrl("https://kick.com/login")
        }
    }

    override fun onDestroy() {
        webView?.apply { stopLoading(); clearHistory(); clearCache(true); destroy() }
        webView = null
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        if (AccountGraph.auth.session.value.status == SessionStatus.Connecting &&
            AccountGraph.auth.session.value.generation == generation) AccountGraph.auth.cancelLogin()
        super.onDestroy()
    }
}
