package io.appetise.shopper

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.window.OnBackInvokedDispatcher
import android.webkit.WebView

/**
 * The native sheet (D8): a full-height dialog around the warm WebView. The page keeps its own
 * header; back goes to the page as a close request, so a phase that can't be left ignores it.
 */
internal class SheetDialog(
    activity: Activity,
    private val webView: WebView,
    private val onBack: () -> Unit,
) : Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar) {

    private lateinit var cover: FrameLayout
    private var revealed = false

    init {
        setOwnerActivity(activity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)

        val root = FrameLayout(context)
        root.setBackgroundColor(Color.WHITE)
        (webView.parent as? ViewGroup)?.removeView(webView)
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        cover = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            addView(ProgressBar(context), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }
        if (revealed) cover.visibility = android.view.View.GONE
        root.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Keeps the widget clear of the status and navigation bars.
        root.fitsSystemWindows = true
        setContentView(root)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        }
    }

    @Deprecated("Below API 33; above it the OnBackInvokedCallback handles back.")
    override fun onBackPressed() {
        onBack()
    }

    /** The widget has drawn: its first `closable` message arrives as the modal opens. */
    fun reveal() {
        // Remembered, in case the page draws before this dialog has built its views.
        revealed = true
        if (::cover.isInitialized && cover.visibility != android.view.View.GONE) {
            cover.animate().alpha(0f).setDuration(150).withEndAction { cover.visibility = android.view.View.GONE }
        }
    }

    override fun onStop() {
        super.onStop()
        (webView.parent as? ViewGroup)?.removeView(webView)
    }
}
