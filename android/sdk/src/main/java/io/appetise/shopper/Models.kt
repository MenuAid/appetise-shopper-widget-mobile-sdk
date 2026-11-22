package io.appetise.shopper

import org.json.JSONArray
import org.json.JSONObject

/** How the partner's app sets the widget up. Only [partner] is required. */
data class AppetiseConfiguration(
    /** The partner ID from Appetise Insights. */
    val partner: String,
    /** `NZ` or `AU`. Worked out from the device's timezone when null. */
    val countryCode: String? = null,
    /** Overrides the API origin baked into the hosted page. For staging and local development. */
    val apiBase: String? = null,
    /** Overrides the hosted page. For staging and local development. */
    val hostedPage: String = "https://embed.appetise.io/m/v1/index.html",
    /** Writes lifecycle to Logcat and makes the WebView debuggable. */
    val debug: Boolean = false,
    /** Set by the React Native and Flutter wrappers; leave as `native` otherwise. */
    val framework: String = "native",
)

/** One line of a shopping list: as the shopper would write it, or already split up. */
sealed class AppetiseIngredient {
    data class Raw(val raw: String) : AppetiseIngredient()
    data class Named(val name: String, val quantity: Double? = null, val unit: String? = null) : AppetiseIngredient()

    internal fun toJson(): JSONObject = when (this) {
        is Raw -> JSONObject().put("raw", raw)
        is Named -> JSONObject().put("name", name).apply {
            quantity?.let { put("quantity", it) }
            unit?.let { put("unit", it) }
        }
    }
}

/** A recipe to shop. [externalRef] is required: in an app it is the recipe's only identity. */
sealed class AppetiseRecipe {
    abstract val externalRef: String

    data class Params(
        val name: String,
        val ingredients: List<AppetiseIngredient>,
        override val externalRef: String,
        val servings: Int? = null,
        val imageUrl: String? = null,
    ) : AppetiseRecipe()

    data class Text(
        val text: String,
        override val externalRef: String,
        val name: String? = null,
    ) : AppetiseRecipe()

    internal fun toJson(): JSONObject = when (this) {
        is Params -> JSONObject()
            .put("name", name)
            .put("ingredients", JSONArray(ingredients.map { it.toJson() }))
            .put("externalRef", externalRef)
            .apply {
                servings?.let { put("servings", it) }
                imageUrl?.let { put("imageUrl", it) }
            }
        is Text -> JSONObject().put("text", text).put("externalRef", externalRef).apply {
            name?.let { put("name", it) }
        }
    }
}

/** Why `open` would not draw right now. The same values as the web SDK's `ready()`. */
enum class AppetiseNotReadyReason(val wire: String) {
    CONFIG_UNRESOLVED("config_unresolved"),
    NO_COUNTRY("no_country"),
    DISABLED("disabled"),
    PARTNER_UNCONFIRMED("partner_unconfirmed"),
    NO_STORES("no_stores");

    companion object {
        fun from(wire: String?): AppetiseNotReadyReason? = entries.firstOrNull { it.wire == wire }
    }
}

/** Returned by `on` and `onReadinessChange`. The listener stays until [cancel]. */
class AppetiseSubscription internal constructor(private var onCancel: (() -> Unit)?) {
    fun cancel() {
        onCancel?.invoke()
        onCancel = null
    }
}
