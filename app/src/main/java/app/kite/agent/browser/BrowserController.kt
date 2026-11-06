package app.kite.agent.browser

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * A single visible WebView session driven by the agent.
 * The user can watch every navigation; tools read and act on the live DOM.
 */
object BrowserController {

    private var activity: BrowserActivity? = null
    private var webView: WebView? = null
    private var attached: CompletableDeferred<Unit>? = null
    private var loaded: CompletableDeferred<String>? = null

    fun attach(activity: BrowserActivity, webView: WebView) {
        this.activity = activity
        this.webView = webView
        attached?.complete(Unit)
    }

    fun detach(activity: BrowserActivity) {
        if (this.activity === activity) {
            this.activity = null
            this.webView = null
        }
    }

    fun onPageFinished(url: String) {
        loaded?.complete(url)
    }

    fun onPageError(message: String) {
        loaded?.completeExceptionally(IOException(message))
    }

    fun isOpen(): Boolean = webView != null

    suspend fun open(context: Context, url: String): String = withContext(Dispatchers.Main) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("只支持 http/https 地址")
        }
        val current = webView
        val host = activity
        if (current != null && host != null && !host.isFinishing) {
            loaded = CompletableDeferred()
            current.loadUrl(url)
        } else {
            attached = CompletableDeferred()
            loaded = CompletableDeferred()
            context.startActivity(
                Intent(context, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            withTimeout(15_000) { attached!!.await() }
        }
        withTimeout(30_000) { loaded!!.await() }
        delay(600)
        info()
    }

    suspend fun read(): String = withContext(Dispatchers.Main) {
        val text = evaluate("(document.body ? document.body.innerText : '').slice(0, 16000)")
        if (text.isBlank()) "(页面没有正文)" else text
    }

    suspend fun click(selector: String): String = withContext(Dispatchers.Main) {
        val sel = json(selector)
        val result = evaluate(
            "(function(){var e=document.querySelector($sel);" +
                "if(!e)return 'not_found';" +
                "e.scrollIntoView({block:'center'});e.click();return 'clicked';})()",
        )
        delay(1_200)
        "$result\n\n" + info()
    }

    suspend fun type(selector: String, text: String): String = withContext(Dispatchers.Main) {
        val sel = json(selector)
        val value = json(text)
        val result = evaluate(
            "(function(){var e=document.querySelector($sel);" +
                "if(!e)return 'not_found';" +
                "e.focus();try{var p=(e.tagName==='TEXTAREA')?window.HTMLTextAreaElement.prototype:window.HTMLInputElement.prototype;" +
                "var s=Object.getOwnPropertyDescriptor(p,'value').set;s.call(e,$value);}catch(x){e.value=$value;}" +
                "e.dispatchEvent(new Event('input',{bubbles:true}));" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));return 'typed';})()",
        )
        delay(300)
        result
    }

    suspend fun back(): String = withContext(Dispatchers.Main) {
        val current = webView ?: throw IllegalStateException("浏览器没有打开")
        current.goBack()
        delay(900)
        info()
    }

    fun close() {
        activity?.finish()
        activity = null
        webView = null
    }

    private suspend fun info(): String {
        val title = evaluate("document.title").ifBlank { "(无标题)" }
        val url = evaluate("location.href")
        val text = evaluate("(document.body ? document.body.innerText : '').slice(0, 8000)")
        return "标题：$title\nURL：$url\n\n${text.ifBlank { "(页面没有正文)" }}"
    }

    private suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        val view = webView ?: throw IllegalStateException("浏览器没有打开")
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { result ->
                    if (continuation.isActive) {
                        continuation.resume(decode(result))
                    }
                }
            }
        }
    }

    private fun decode(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return runCatching { JsonParser.parseString(raw).asString }.getOrElse { raw }
    }

    private fun json(value: String): String =
        com.google.gson.Gson().toJson(value)
}
