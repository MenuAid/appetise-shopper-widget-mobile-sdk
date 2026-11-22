package io.appetise.shopper

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Owns the one warm WebView (D9): loads the hosted page, carries the bridge, and keeps every
 * non-Appetise navigation out of it (D3, D7).
 */
@SuppressLint("SetJavaScriptEnabled")
internal class WebViewHost(
    appContext: Context,
    private val configuration: AppetiseConfiguration,
    private val onMessage: (Bridge.PageMessage) -> Unit,
    private val onTerminated: () -> Unit,
) {
    /** Swapped to the presenting Activity while shown, and back to the app context after. */
    private val context = MutableContextWrapper(appContext)
    val webView: WebView = WebView(context)

    private val allowedHosts = buildSet {
        Uri.parse(configuration.hostedPage).host?.lowercase()?.let(::add)
        configuration.apiBase?.let { Uri.parse(it).host?.lowercase() }?.let(::add)
    }
    private val appUrlHints = mutableMapOf<String, String>()
    private var loaded = false

    init {
        WebView.setWebContentsDebuggingEnabled(configuration.debug)
        webView.setBackgroundColor(Color.WHITE)
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            // D13: the store selection lives in localStorage.
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        installBridge()
    }

    fun attach(activity: Context) {
        context.baseContext = activity
    }

    fun detach(appContext: Context) {
        context.baseContext = appContext
    }

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        val tablet = (app.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
            Configuration.SCREENLAYOUT_SIZE_LARGE
        webView.loadUrl(Bridge.pageUrl(configuration, app.packageName, info?.versionName ?: "0", tablet))
    }

    fun send(message: org.json.JSONObject) {
        webView.evaluateJavascript(Bridge.script(message), null)
    }

    fun rememberAppUrl(appUrl: String, url: String) {
        appUrlHints[url] = appUrl
    }

    /** D7: the supermarket's app first, then a Custom Tab, which shares Chrome's sign-in. */
    private fun openExternally(url: String, from: Context) {
        val deepLink = appUrlHints[url]
        if (deepLink != null) {
            try {
                from.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).addNewTaskIfNeeded(from))
                return
            } catch (_: ActivityNotFoundException) {
                // The app isn't installed: the browser it is.
            }
        }
        try {
            CustomTabsIntent.Builder().build().launchUrl(from, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            from.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addNewTaskIfNeeded(from))
        }
    }

    fun destroy() {
        webView.destroy()
    }

    /**
     * `window.appetiseHost.postMessage(string)`, injected for the hosted page's origin only. No
     * `@JavascriptInterface`: nothing on the page can reach native code except this one channel.
     */
    private fun installBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            log { "This WebView has no WebMessageListener; the widget can't talk to the app." }
            return
        }
        val origin = Uri.parse(configuration.hostedPage).let { uri ->
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        }
        WebViewCompat.addWebMessageListener(webView, Bridge.HANDLER_NAME, setOf(origin)) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame) return@addWebMessageListener
            val parsed = message.data?.let(Bridge::read)
            if (parsed == null) {
                log { "Ignored an off-protocol message: ${message.data}" }
            } else {
                log { "Bridge message: $parsed" }
                onMessage(parsed)
            }
        }
    }

    private fun isAppetise(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "about" || scheme == "data" || scheme == "blob") return true
        val host = uri.host?.lowercase() ?: return false
        return host in allowedHosts || host.endsWith(".appetise.io")
    }

    /** True when the URL stays in the WebView; anything else opens outside it (D7). */
    private fun route(url: Uri, from: Context): Boolean {
        if (isAppetise(url)) return true
        openExternally(url.toString(), from)
        return false
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !route(request.url, view.context)

        /** The OS killed the renderer: drop this WebView; the next preload or open builds another. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            onTerminated()
            return true
        }
    }

    /**
     * `target="_blank"` and `window.open()`, including the checkout frame's "view cart" link: a
     * throwaway window catches the first real URL, routes it, and is destroyed.
     */
    private inner class Chrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Our context wrapper, not the Activity behind it, and the app context for the catcher.
            val from = view.context
            val catcher = WebView(from.applicationContext)
            catcher.webViewClient = object : WebViewClient() {
                private var caught = false

                private fun catch(window: WebView, url: Uri?) {
                    // `window.open()` with no URL starts at about:blank, then navigates.
                    if (caught || url == null || url.scheme == "about") return
                    caught = true
                    route(url, from)
                    window.post { window.destroy() }
                }

                override fun shouldOverrideUrlLoading(window: WebView, request: WebResourceRequest): Boolean {
                    catch(window, request.url)
                    return true
                }

                // A new window's first load can skip shouldOverrideUrlLoading.
                override fun onPageStarted(window: WebView, url: String?, favicon: Bitmap?) {
                    catch(window, url?.let(Uri::parse))
                }
            }
            catcher.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) = window.destroy()
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = catcher
            resultMsg.sendToTarget()
            return true
        }
    }

    private inline fun log(message: () -> String) {
        if (configuration.debug) Log.i("Appetise", message())
    }
}

private fun Intent.addNewTaskIfNeeded(from: Context): Intent =
    if (from is android.app.Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
