package io.appetise.shopper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeTest {
    @Test
    fun readsEveryPageMessage() {
        assertEquals(
            Bridge.PageMessage.Readiness(false, AppetiseNotReadyReason.NO_STORES),
            Bridge.read("""{"v":1,"type":"readiness","isReady":false,"notReadyReason":"no_stores"}"""),
        )
        assertEquals(Bridge.PageMessage.Closable(true), Bridge.read("""{"v":1,"type":"closable","closable":true}"""))
        assertEquals(Bridge.PageMessage.Closed, Bridge.read("""{"v":1,"type":"closed"}"""))
        assertEquals(
            Bridge.PageMessage.LinkHint("https://woolworths.co.nz/cart", "nz.woolworths.shop://cart"),
            Bridge.read("""{"v":1,"type":"link_hint","url":"https://woolworths.co.nz/cart","appUrl":"nz.woolworths.shop://cart"}"""),
        )
        assertEquals(
            Bridge.PageMessage.Event("embed_opened", mapOf("itemCount" to 3)),
            Bridge.read("""{"v":1,"type":"event","event":"embed_opened","payload":{"itemCount":3}}"""),
        )
    }

    @Test
    fun dropsOffProtocolMessages() {
        assertNull(Bridge.read("""{"v":2,"type":"closed"}"""))
        assertNull(Bridge.read("""{"v":1,"type":"eval","code":"x"}"""))
        assertNull(Bridge.read("""{"v":1,"type":"closable"}"""))
        assertNull(Bridge.read("""{"v":1,"type":"link_hint","url":"javascript:alert(1)","appUrl":"x://cart"}"""))
        assertNull(Bridge.read("not json"))
    }

    @Test
    fun encodesNativeMessages() {
        assertEquals("""{"v":1,"type":"close_requested","via":"back"}""", Bridge.closeRequested("back").toString())
        assertEquals("post-9", Bridge.open(emptyList(), "post-9").getString("externalRef"))
        assertFalse(Bridge.open(emptyList(), null).has("externalRef"))
        val recipe = Bridge.openRecipe(AppetiseRecipe.Text("2 eggs", externalRef = "r1"))
        assertEquals("r1", recipe.getJSONObject("recipe").getString("externalRef"))
    }
}
