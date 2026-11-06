package app.kite.agent.agent

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

data class ApprovalRequest(
    val id: Long,
    val tool: String,
    val detail: String,
)

/**
 * Suspends a tool until the chat UI allows or denies it. A missing UI
 * (background/subagent) times out and denies, which is the safe default.
 */
object ApprovalGate {

    private val ids = AtomicLong(0)
    private val _request = MutableStateFlow<ApprovalRequest?>(null)
    val request = _request.asStateFlow()
    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun ask(tool: String, detail: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        _request.value = ApprovalRequest(ids.incrementAndGet(), tool, detail.take(600))
        return try {
            withTimeout(120_000) { deferred.await() }
        } catch (_: Throwable) {
            false
        } finally {
            _request.value = null
            pending = null
        }
    }

    fun resolve(allow: Boolean) {
        pending?.complete(allow)
    }
}
