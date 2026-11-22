package io.appetise.shopper

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * The Appetise shopper widget, as a native sheet. Names follow the web SDK's `window.Appetise`
 * (mobile-sdk D11): `configure`, `open` for `render`, `openRecipe` for `renderRecipe`, `close`
 * for `destroy`, and `isReady` for `ready()`. Call from the main thread.
 */
object Appetise {
    private var app: Application? = null
    private var configuration: AppetiseConfiguration? = null
    private var host: WebViewHost? = null
    private var sheet: SheetDialog? = null
    private var pendingOpen: org.json.JSONObject? = null
    private var answered = false
    private val main = Handler(Looper.getMainLooper())
    private val readinessListeners = mutableMapOf<UUID, (Boolean) -> Unit>()
    private val eventListeners = mutableMapOf<String, MutableMap<UUID, (Map<String, Any?>) -> Unit>>()

    /** True only when [open] right now would draw. Gate the button on it. */
    var isReady: Boolean = false
        private set

    /** Why not, once the page has answered. */
    var notReadyReason: AppetiseNotReadyReason? = null
        private set

    /** Cheap: no WebView yet. Call once, early. A new configuration drops any warm page. */
    fun configure(context: Context, configuration: AppetiseConfiguration) {
        val application = context.applicationContext as Application
        if (app == null) watch(application)
        app = application
        if (configuration == this.configuration) return
        this.configuration = configuration
        if (sheet == null) tearDown()
    }

    /** Warms the page and asks the server whether the widget may draw. Subscribing does this too. */
    fun preload() {
        warmHost()?.loadIfNeeded()
    }

    fun onReadinessChange(listener: (Boolean) -> Unit): AppetiseSubscription {
        val id = UUID.randomUUID()
        readinessListeners[id] = listener
        preload()
        return AppetiseSubscription { readinessListeners.remove(id) }
    }

    /** The web SDK's public events, e.g. `embed_opened`, with their payloads. */
    fun on(event: String, listener: (Map<String, Any?>) -> Unit): AppetiseSubscription {
        val id = UUID.randomUUID()
        eventListeners.getOrPut(event) { mutableMapOf() }[id] = listener
        return AppetiseSubscription { eventListeners[event]?.remove(id) }
    }

    /** [externalRef] is the partner's own reference for this list, returned unchanged on its session. */
    fun open(activity: Activity, items: List<AppetiseIngredient>, externalRef: String? = null) =
        open(activity, Bridge.open(items, externalRef))

    fun openRecipe(activity: Activity, recipe: AppetiseRecipe) = open(activity, Bridge.openRecipe(recipe))

    /** Ends the shop and dismisses the sheet. */
    fun close() {
        host?.send(Bridge.close())
        dismissSheet()
    }

    private fun open(activity: Activity, message: org.json.JSONObject) {
        val host = warmHost() ?: run {
            log("open() before configure(). Nothing will draw.")
            return
        }

        // D9: a refusal does nothing; the page has already emitted its reason.
        if (answered && !isReady) {
            log("open() ignored: ${notReadyReason?.wire ?: "not ready"}")
            return
        }

        host.loadIfNeeded()
        present(activity, host)

        if (answered) host.send(message) else pendingOpen = message
    }

    private fun warmHost(): WebViewHost? {
        host?.let { return it }
        val app = app ?: return null
        val configuration = configuration ?: return null
        return WebViewHost(
            app,
            configuration,
            onMessage = { message -> main.post { handle(message) } },
            onTerminated = { main.post { terminated() } },
        ).also { host = it }
    }

    private fun present(activity: Activity, host: WebViewHost) {
        if (sheet != null) return
        host.attach(activity)
        sheet = SheetDialog(activity, host.webView) { host.send(Bridge.closeRequested("back")) }.also { it.show() }
    }

    private fun handle(message: Bridge.PageMessage) {
        when (message) {
            is Bridge.PageMessage.Readiness -> {
                val changed = message.isReady != isReady || !answered
                answered = true
                isReady = message.isReady
                notReadyReason = if (message.isReady) null else message.reason
                if (changed) readinessListeners.values.toList().forEach { it(message.isReady) }
                flushPendingOpen()
            }
            is Bridge.PageMessage.Closable -> if (message.closable) sheet?.reveal()
            Bridge.PageMessage.Closed -> dismissSheet()
            is Bridge.PageMessage.LinkHint -> host?.rememberAppUrl(message.appUrl, message.url)
            is Bridge.PageMessage.Event -> eventListeners[message.name]?.values?.toList()?.forEach { it(message.payload) }
        }
    }

    private fun flushPendingOpen() {
        val message = pendingOpen ?: return
        pendingOpen = null
        if (isReady) host?.send(message) else dismissSheet()
    }

    private fun dismissSheet() {
        sheet?.dismiss()
        sheet = null
        app?.let { host?.detach(it) }
    }

    private fun terminated() {
        dismissSheet()
        tearDown()
    }

    private fun tearDown() {
        host?.destroy()
        host = null
        answered = false
        pendingOpen = null
    }

    /** Foreground re-asks /config after a failure (D9); low memory drops an idle page. */
    private fun watch(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) host?.send(Bridge.foreground())
            }
            override fun onActivityStopped(activity: Activity) { started = maxOf(0, started - 1) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (sheet?.ownerActivity == activity) dismissSheet()
            }
        })
        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && sheet == null) tearDown()
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Superseded by onTrimMemory") override fun onLowMemory() {}
        })
    }

    private fun log(message: String) {
        if (configuration?.debug == true) Log.i("Appetise", message)
    }
}
