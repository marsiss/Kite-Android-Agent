package app.kite.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kite.agent.agent.AgentEngine
import app.kite.agent.agent.AgentEvent
import app.kite.agent.agent.ModelCatalog
import app.kite.agent.data.Message
import app.kite.agent.data.McpServerConfig
import app.kite.agent.data.McpToolInfo
import app.kite.agent.data.ProviderConfig
import app.kite.agent.data.Role
import app.kite.agent.data.Session
import app.kite.agent.data.SessionStore
import app.kite.agent.data.SessionSummary
import app.kite.agent.data.Settings
import app.kite.agent.data.ToolCall
import app.kite.agent.mcp.McpManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val settings = Settings(application)
    private val mcpManager = McpManager(application)

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _current = MutableStateFlow<Session?>(null)
    val current = _current.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val _provider = MutableStateFlow(settings.provider())
    val provider = _provider.asStateFlow()

    private val _shellEnabled = MutableStateFlow(settings.shellEnabled())
    val shellEnabled = _shellEnabled.asStateFlow()

    private val _shellMode = MutableStateFlow(settings.shellMode())
    val shellMode = _shellMode.asStateFlow()

    private val _approvalMode = MutableStateFlow(settings.approvalMode())
    val approvalMode = _approvalMode.asStateFlow()

    private val _deviceStorage = MutableStateFlow(settings.deviceStorage())
    val deviceStorage = _deviceStorage.asStateFlow()

    private val _workspace = MutableStateFlow(settings.workspace())
    val workspace = _workspace.asStateFlow()

    private val _runningTool = MutableStateFlow<ToolCall?>(null)
    val runningTool = _runningTool.asStateFlow()

    private val _contextTokens = MutableStateFlow(0)
    val contextTokens = _contextTokens.asStateFlow()

    private val _mcpServers = MutableStateFlow(mcpManager.servers())
    val mcpServers = _mcpServers.asStateFlow()

    private val _mcpStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val mcpStatus = _mcpStatus.asStateFlow()

    private val _mcpTools = MutableStateFlow<List<McpToolInfo>>(emptyList())
    val mcpTools = _mcpTools.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading = _modelsLoading.asStateFlow()

    private val _localTree = MutableStateFlow(settings.localTree())
    val localTree = _localTree.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var engine: AgentEngine? = null
    private var job: Job? = null
    private var streamingId: String? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    init {
        refreshSessions()
        refreshMcp()
    }

    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _sessions.value = store.list()
        }
    }

    fun open(id: String): Session? {
        val session = store.load(id) ?: return null
        _current.value = session
        return session
    }

    fun startNew(): Session {
        val session = store.newSession()
        store.save(session)
        _current.value = session
        refreshSessions()
        return session
    }

    fun delete(id: String) {
        store.delete(id)
        if (_current.value?.id == id) _current.value = null
        refreshSessions()
    }

    fun rename(id: String, title: String) {
        val updated = store.rename(id, title) ?: return
        if (_current.value?.id == id) _current.value = updated
        refreshSessions()
    }

    fun saveProvider(config: ProviderConfig) {
        settings.saveProvider(config)
        _provider.value = config
        _messages.tryEmit("模型设置已保存")
    }

    fun fetchModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank()) {
            _messages.tryEmit("接口地址为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _modelsLoading.value = true
            _models.value = emptyList()
            runCatching { ModelCatalog.list(baseUrl, apiKey) }
                .onSuccess { list ->
                    _models.value = list
                    _messages.tryEmit(
                        if (list.isEmpty()) "没有发现模型" else "发现 ${list.size} 个模型",
                    )
                }
                .onFailure { error ->
                    _messages.tryEmit("获取模型失败：${error.message ?: error.javaClass.simpleName}")
                }
            _modelsLoading.value = false
        }
    }

    fun setShellEnabled(enabled: Boolean) {
        settings.setShellEnabled(enabled)
        _shellEnabled.value = enabled
    }

    fun refreshMcp() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = mcpManager.refresh()
            _mcpStatus.value = status
            _mcpTools.value = mcpManager.currentTools()
            _mcpServers.value = mcpManager.servers()
        }
    }

    fun saveMcpServer(config: McpServerConfig) {
        val list = mcpManager.servers().toMutableList()
        val index = list.indexOfFirst { it.id == config.id }
        if (index >= 0) list[index] = config else list += config
        mcpManager.saveServers(list)
        _mcpServers.value = list
        refreshMcp()
    }

    fun deleteMcpServer(id: String) {
        val list = mcpManager.servers().filterNot { it.id == id }
        mcpManager.saveServers(list)
        _mcpServers.value = list
        refreshMcp()
    }

    fun testMcpServer(config: McpServerConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _messages.tryEmit(mcpManager.test(config))
        }
    }

    fun setShellMode(mode: String) {
        settings.setShellMode(mode)
        _shellMode.value = mode
    }

    fun setApprovalMode(enabled: Boolean) {
        settings.setApprovalMode(enabled)
        _approvalMode.value = enabled
    }

    fun setDeviceStorage(enabled: Boolean) {
        settings.setDeviceStorage(enabled)
        _deviceStorage.value = enabled
        _workspace.value = settings.workspace()
    }

    fun setLocalTree(uri: String) {
        settings.setLocalTree(uri)
        _localTree.value = uri
    }

    fun refreshWorkspace() {
        _workspace.value = settings.workspace()
    }

    fun send(
        text: String,
        attachments: List<app.kite.agent.data.ImageAttachment> = emptyList(),
        files: List<app.kite.agent.data.FileAttachment> = emptyList(),
    ) {
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && attachments.isEmpty() && files.isEmpty()) || _running.value) return
        val config = _provider.value
        if (config.apiKey.isBlank()) {
            val base = _current.value ?: startNew()
            _current.value = base.copy(
                messages = base.messages + Message(
                    role = Role.ASSISTANT,
                    content = "还没有配置 API Key。打开右上角设置，填上模型服务地址、模型名和 Key 就能开始。",
                    error = true,
                ),
                updatedAt = System.currentTimeMillis(),
            )
            _messages.tryEmit("请先配置 API Key")
            return
        }

        var session = _current.value ?: startNew()
        val isFirstUserMessage = session.messages.none { it.role == Role.USER }
        val content = trimmed.ifBlank { if (attachments.isNotEmpty()) "看看这张图" else "看看这些文件" }
        val userMessage = Message(
            role = Role.USER,
            content = content,
            attachments = attachments,
            files = files,
        )
        session = session.copy(
            title = if (isFirstUserMessage) {
                trimmed.lineSequence().firstOrNull()?.take(26)?.ifBlank {
                    when {
                        attachments.isNotEmpty() -> "图片任务"
                        files.isNotEmpty() -> files.first().name.take(26)
                        else -> "新任务"
                    }
                } ?: "新任务"
            } else {
                session.title
            },
            messages = session.messages + userMessage,
            updatedAt = System.currentTimeMillis(),
        )
        _current.value = session
        persist(session)
        streamingId = null
        _running.value = true
        _runningTool.value = null
        AgentService.start(getApplication())

        val engine = AgentEngine(
            context = getApplication(),
            config = config,
            workspace = File(_workspace.value),
            shellEnabled = _shellEnabled.value,
            allowExternal = settings.allowExternalPaths(),
            shellMode = _shellMode.value,
            mcp = mcpManager,
            approval = _approvalMode.value,
        )
        this.engine = engine
        val baseMessages = session.messages

        job = viewModelScope.launch(Dispatchers.IO) {
            engine.run(baseMessages).collect { event ->
                val snapshot = _current.value ?: session
                val updated = reduce(snapshot, event)
                _current.value = updated
                when (event) {
                    is AgentEvent.AssistantMessage, is AgentEvent.ToolMessage, is AgentEvent.PlanUpdated -> persist(updated)
                    is AgentEvent.Done, is AgentEvent.Failure -> {
                        _running.value = false
                        _runningTool.value = null
                        AgentService.stop(getApplication())
                        persist(updated)
                        refreshSessions()
                    }

                    is AgentEvent.ToolStarted -> _runningTool.value = event.call
                    is AgentEvent.ToolFinished -> _runningTool.value = null
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        engine?.cancel()
        job?.cancel()
        _running.value = false
        _runningTool.value = null
        streamingId = null
        AgentService.stop(getApplication())
        _current.value?.let { persist(it) }
    }

    private fun reduce(session: Session, event: AgentEvent): Session {
        val now = System.currentTimeMillis()
        return when (event) {
            is AgentEvent.Delta -> {
                val list = session.messages.toMutableList()
                val id = streamingId
                if (id == null) {
                    val message = Message(role = Role.ASSISTANT, content = event.text)
                    streamingId = message.id
                    list += message
                } else {
                    val index = list.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        list[index] = list[index].copy(content = list[index].content + event.text)
                    } else {
                        val message = Message(role = Role.ASSISTANT, content = event.text)
                        streamingId = message.id
                        list += message
                    }
                }
                session.copy(messages = list, updatedAt = now)
            }

            is AgentEvent.ReasoningDelta -> {
                val list = session.messages.toMutableList()
                val id = streamingId
                if (id == null) {
                    val message = Message(role = Role.ASSISTANT, reasoning = event.text)
                    streamingId = message.id
                    list += message
                } else {
                    val index = list.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        val old = list[index]
                        list[index] = old.copy(reasoning = (old.reasoning ?: "") + event.text)
                    }
                }
                session.copy(messages = list, updatedAt = now)
            }

            is AgentEvent.AssistantMessage -> {
                val list = session.messages.toMutableList()
                val id = streamingId
                if (id != null) {
                    val index = list.indexOfFirst { it.id == id }
                    if (index >= 0) list[index] = event.message else list += event.message
                } else {
                    list += event.message
                }
                streamingId = null
                session.copy(messages = list, updatedAt = now)
            }

            is AgentEvent.ToolMessage -> session.copy(
                messages = session.messages + event.message,
                updatedAt = now,
            )

            is AgentEvent.PlanUpdated -> session.copy(
                todos = event.items,
                updatedAt = now,
            )

            is AgentEvent.ContextStats -> {
                _contextTokens.value = event.tokens
                session
            }

            is AgentEvent.Failure -> session.copy(
                messages = session.messages + Message(
                    role = Role.ASSISTANT,
                    content = event.message,
                    error = true,
                ),
                updatedAt = now,
            )

            else -> session
        }
    }

    private fun persist(session: Session) {
        viewModelScope.launch(Dispatchers.IO) { store.save(session) }
    }

    fun testConnection() {
        val config = _provider.value
        if (config.baseUrl.isBlank()) {
            _messages.tryEmit("接口地址为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val base = config.baseUrl.trim().trimEnd('/')
            val endpoint = if (base.endsWith("/chat/completions")) {
                base.removeSuffix("/chat/completions") + "/models"
            } else {
                "$base/models"
            }
            val request = Request.Builder()
                .url(endpoint)
                .apply {
                    if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
                }
                .build()
            val message = runCatching {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        "连接成功（HTTP ${response.code}）"
                    } else if (response.code == 401 || response.code == 403) {
                        "Key 无效或没有权限（HTTP ${response.code}）"
                    } else {
                        "服务返回 HTTP ${response.code}"
                    }
                }
            }.getOrElse { error ->
                "连不上：${error.message ?: error.javaClass.simpleName}"
            }
            _messages.tryEmit(message)
        }
    }

    override fun onCleared() {
        engine?.cancel()
        job?.cancel()
        mcpManager.close()
        AgentService.stop(getApplication())
        super.onCleared()
    }
}
