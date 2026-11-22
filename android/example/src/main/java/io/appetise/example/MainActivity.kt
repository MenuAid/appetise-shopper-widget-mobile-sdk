package io.appetise.example

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.appetise.shopper.Appetise
import io.appetise.shopper.AppetiseConfiguration
import io.appetise.shopper.AppetiseIngredient
import io.appetise.shopper.AppetiseRecipe
import io.appetise.shopper.AppetiseSubscription

/**
 * A recipe screen with a Shop button gated on readiness, as a partner would build it. For local
 * development pass `hostedPage`, `apiBase` and `autoOpen` as launch extras (see README).
 */
class MainActivity : Activity() {
    private val subscriptions = mutableListOf<AppetiseSubscription>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Appetise.configure(this, AppetiseConfiguration(
            partner = intent.getStringExtra("partner") ?: "local-dev",
            countryCode = "NZ",
            apiBase = intent.getStringExtra("apiBase"),
            hostedPage = intent.getStringExtra("hostedPage") ?: "https://embed.appetise.io/m/v1/index.html",
            debug = true,
        ))

        val status = TextView(this).apply { text = "Checking…"; textSize = 16f }
        val shop = Button(this).apply {
            text = "Shop ingredients"
            isEnabled = false
            setOnClickListener { openList() }
        }
        val recipe = Button(this).apply {
            text = "Shop as a recipe"
            isEnabled = false
            setOnClickListener {
                Appetise.openRecipe(this@MainActivity, AppetiseRecipe.Params(
                    name = "Creamy mushroom pasta",
                    ingredients = listOf(AppetiseIngredient.Raw("400g spaghetti"), AppetiseIngredient.Raw("300ml cream")),
                    externalRef = "example-recipe-1",
                ))
            }
        }
        val events = TextView(this).apply { textSize = 12f }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 160, 48, 48)
            addView(TextView(context).apply { text = "Creamy mushroom pasta"; textSize = 26f })
            listOf("400g spaghetti", "50g butter", "250g chestnut mushrooms, sliced", "300ml cream").forEach {
                addView(TextView(context).apply { text = it; textSize = 18f; setPadding(0, 16, 0, 16) })
            }
            addView(status)
            addView(shop, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(recipe, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(events)
        })

        subscriptions += Appetise.onReadinessChange { ready ->
            shop.isEnabled = ready
            recipe.isEnabled = ready
            status.text = if (ready) "Ready" else "Not ready: ${Appetise.notReadyReason?.wire}"
            // Dev only: lets a script open the sheet on an emulator without tapping.
            if (ready && intent.getBooleanExtra("autoOpen", false)) shop.postDelayed({ openList() }, 1000)
        }
        listOf("embed_opened", "embed_error", "embed_basket_viewed").forEach { name ->
            subscriptions += Appetise.on(name) { events.append("$name\n") }
        }
    }

    private fun openList() {
        Appetise.open(this, listOf(
            AppetiseIngredient.Raw("400g spaghetti"),
            AppetiseIngredient.Raw("50g butter"),
            AppetiseIngredient.Raw("300ml cream"),
        ))
    }

    override fun onDestroy() {
        subscriptions.forEach { it.cancel() }
        super.onDestroy()
    }
}
