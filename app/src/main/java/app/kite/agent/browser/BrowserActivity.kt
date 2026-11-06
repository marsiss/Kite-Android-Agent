package app.kite.agent.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat

@SuppressLint("SetJavaScriptEnabled")
class BrowserActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#080A0E")
        window.navigationBarColor = Color.parseColor("#080A0E")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080A0E"))
            fitsSystemWindows = true
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#11151B"))
        }
        val close = TextView(this).apply {
            text = "×"
            setTextColor(Color.parseColor("#F4EFE6"))
            textSize = 24f
            setPadding(8, 0, 24, 0)
            setOnClickListener { finish() }
        }
        titleView = TextView(this).apply {
            text = "Kite Browser"
            setTextColor(Color.parseColor("#8A8F99"))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        bar.addView(close)
        bar.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setBackgroundColor(Color.parseColor("#080A0E"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    titleView.text = view?.title ?: url.orEmpty()
                    BrowserController.onPageFinished(url.orEmpty())
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        BrowserController.onPageError(error?.description?.toString() ?: "页面加载失败")
                    }
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                runCatching {
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle(name)
                        .setMimeType(mimeType)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
                    getSystemService(DownloadManager::class.java).enqueue(request)
                    Toast.makeText(this@BrowserActivity, "开始下载：$name", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this@BrowserActivity, "下载失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        root.addView(bar)
        root.addView(webView)
        setContentView(root)
        BrowserController.attach(this, webView)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isNotBlank()) webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }?.let { webView.loadUrl(it) }
    }

    override fun onDestroy() {
        BrowserController.detach(this)
        runCatching { webView.destroy() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
