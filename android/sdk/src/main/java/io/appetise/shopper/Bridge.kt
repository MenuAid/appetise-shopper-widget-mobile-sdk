package io.appetise.shopper

import android.net.Uri
import org.json.JSONObject

/**
 * The native half of the bridge protocol (mobile-sdk D3, D14). Messages are checked against the
 * schema the widget publishes at `m/v1/bridge-protocol.v1.schema.json`.
 */
internal object Bridge {
    const val PROTOCOL_VERSION = 1
    const val HANDLER_NAME = "appetiseHost"
    const val SDK_VERSION = "0.1.0"

    sealed class PageMessage {
        data class Readiness(val isReady: Boolean, val reason: AppetiseNotReadyReason?) : PageMessage()
        data class Closable(val closable: Boolean) : PageMessage()
        data object Closed : PageMessage()
        data class LinkHint(val url: String, val appUrl: String) : PageMessage()
        data class Event(val name: String, val payload: Map<String, Any?>) : PageMessage()
    }

    /** What `appetiseHost.postMessage` delivers, as a JSON string. Anything off-protocol is dropped. */
    fun read(raw: String): PageMessage? {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (message.optInt("v", -1) != PROTOCOL_VERSION) return null

        return when (message.optString("type")) {
            "readiness" -> if (message.has("isReady")) {
                PageMessage.Readiness(
                    message.getBoolean("isReady"),
                    AppetiseNotReadyReason.from(message.optString("notReadyReason").ifEmpty { null }),
                )
            } else null
            "closable" -> if (message.has("closable")) PageMessage.Closable(message.getBoolean("closable")) else null
            "closed" -> PageMessage.Closed
            "link_hint" -> {
                val url = webUrl(message.optString("url"))
                val appUrl = message.optString("appUrl").ifEmpty { null }
                if (url != null && appUrl != null) PageMessage.LinkHint(url, appUrl) else null
            }
            "event" -> message.optString("event").ifEmpty { null }?.let { name ->
                val payload = message.optJSONObject("payload")
                PageMessage.Event(name, payload?.keys()?.asSequence()?.associateWith { payload.opt(it) } ?: emptyMap())
            }
            else -> null
        }
    }

    fun open(items: List<AppetiseIngredient>, externalRef: String?): JSONObject =
        envelope("open").put("items", org.json.JSONArray(items.map { it.toJson() }))
            .apply { externalRef?.let { put("externalRef", it) } }

    fun openRecipe(recipe: AppetiseRecipe): JSONObject = envelope("open_recipe").put("recipe", recipe.toJson())

    fun closeRequested(via: String): JSONObject = envelope("close_requested").put("via", via)

    fun close(): JSONObject = envelope("close")

    fun foreground(): JSONObject = envelope("foreground")

    fun script(message: JSONObject): String =
        "window.AppetiseNativeHost && window.AppetiseNativeHost.receive($message);"

    /** `hostedPage#context=<URI-encoded JSON>`: everything the page needs before it boots. */
    fun pageUrl(configuration: AppetiseConfiguration, appId: String, appVersion: String, tablet: Boolean): String {
        val context = JSONObject()
            .put("protocol", PROTOCOL_VERSION)
            .put("partner", configuration.partner)
            .put("appId", appId)
            .put("appVersion", appVersion)
            .put("sdkPlatform", "android")
            .put("sdkFramework", configuration.framework)
            .put("sdkVersion", SDK_VERSION)
            .put("deviceType", if (tablet) "tablet" else "mobile")
        configuration.countryCode?.let { context.put("countryCode", it) }
        configuration.apiBase?.let { context.put("apiBase", it) }
        if (configuration.debug) context.put("debug", true)

        val base = configuration.hostedPage.substringBefore('#')
        return "$base#context=${Uri.encode(context.toString())}"
    }

    private fun envelope(type: String): JSONObject = JSONObject().put("v", PROTOCOL_VERSION).put("type", type)

    private fun webUrl(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val scheme = value.substringBefore(':').lowercase()
        return if (scheme == "https" || scheme == "http") value else null
    }
}
