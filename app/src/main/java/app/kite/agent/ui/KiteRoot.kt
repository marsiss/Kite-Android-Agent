package app.kite.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.agent.AppViewModel
import app.kite.agent.ui.theme.Ink

private const val HOME = "home"
private const val CHAT = "chat"

@Composable
fun KiteRoot(vm: AppViewModel) {
    var route by rememberSaveable { mutableStateOf(HOME) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var accessOpen by rememberSaveable { mutableStateOf(false) }
    var mcpOpen by rememberSaveable { mutableStateOf(false) }
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    var skillsOpen by rememberSaveable { mutableStateOf(false) }
    var scheduledOpen by rememberSaveable { mutableStateOf(false) }

    val current by vm.current.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val provider by vm.provider.collectAsStateWithLifecycle()
    val runningTool by vm.runningTool.collectAsStateWithLifecycle()
    val contextTokens by vm.contextTokens.collectAsStateWithLifecycle()
    val sharedText by ShareBus.text.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(sharedText) {
        val text = sharedText
        if (!text.isNullOrBlank()) {
            ShareBus.clear()
            vm.startNew()
            vm.send(text)
            route = CHAT
        }
    }

    BackHandler(enabled = scheduledOpen || skillsOpen || licensesOpen || mcpOpen || accessOpen || settingsOpen || route != HOME) {
        when {
            scheduledOpen -> scheduledOpen = false
            skillsOpen -> skillsOpen = false
            licensesOpen -> licensesOpen = false
            mcpOpen -> mcpOpen = false
            accessOpen -> accessOpen = false
            settingsOpen -> settingsOpen = false
            else -> route = HOME
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        when {
            scheduledOpen -> stateHolder.SaveableStateProvider("scheduled") {
                ScheduledScreen(onBack = { scheduledOpen = false })
            }

            skillsOpen -> stateHolder.SaveableStateProvider("skills") {
                SkillsScreen(
                    workspace = vm.workspace.value,
                    onBack = { skillsOpen = false },
                )
            }

            licensesOpen -> stateHolder.SaveableStateProvider("licenses") {
                LicensesScreen(onBack = { licensesOpen = false })
            }

            mcpOpen -> stateHolder.SaveableStateProvider("mcp") {
                McpScreen(vm = vm, onBack = { mcpOpen = false })
            }

            accessOpen -> stateHolder.SaveableStateProvider("access") {
                AccessScreen(vm = vm, onBack = { accessOpen = false })
            }

            settingsOpen -> stateHolder.SaveableStateProvider("settings") {
                SettingsScreen(
                    vm = vm,
                    onBack = { settingsOpen = false },
                    onAccess = { accessOpen = true },
                    onMcp = { mcpOpen = true },
                    onLicenses = { licensesOpen = true },
                    onSkills = { skillsOpen = true },
                    onScheduled = { scheduledOpen = true },
                )
            }

            route == CHAT && current != null -> stateHolder.SaveableStateProvider("chat") {
                ChatScreen(
                    session = current!!,
                    running = running,
                    runningTool = runningTool,
                    modelName = provider.model,
                    contextTokens = contextTokens,
                    onSend = { text, images, files -> vm.send(text, images, files) },
                    onStop = vm::stop,
                    onBack = { route = HOME },
                    onSettings = { settingsOpen = true },
                )
            }

            else -> stateHolder.SaveableStateProvider("home") {
                HomeScreen(
                    sessions = sessions,
                    modelName = provider.model,
                    configured = provider.apiKey.isNotBlank(),
                    onOpen = { id ->
                        if (vm.open(id) != null) route = CHAT
                    },
                    onSubmit = { text ->
                        vm.startNew()
                        vm.send(text)
                        route = CHAT
                    },
                    onSettings = { settingsOpen = true },
                    onDelete = vm::delete,
                    onRename = vm::rename,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
