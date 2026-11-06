package app.kite.agent.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.agent.AppViewModel
import app.kite.agent.BuildConfig
import app.kite.agent.SystemAccess
import app.kite.agent.ui.theme.Danger
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Mint
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper
import rikka.shizuku.Shizuku

private data class AccessState(
    val grantedRuntime: Int,
    val totalRuntime: Int,
    val allFiles: Boolean,
    val overlay: Boolean,
    val writeSettings: Boolean,
    val usage: Boolean,
    val installUnknown: Boolean,
    val battery: Boolean,
    val root: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
)

private fun runtimePermissions(): List<String> {
    val list = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )
    if (Build.VERSION.SDK_INT >= 33) {
        list += Manifest.permission.POST_NOTIFICATIONS
        list += Manifest.permission.READ_MEDIA_IMAGES
        list += Manifest.permission.READ_MEDIA_VIDEO
        list += Manifest.permission.READ_MEDIA_AUDIO
        list += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    if (Build.VERSION.SDK_INT >= 31) {
        list += Manifest.permission.BLUETOOTH_CONNECT
        list += Manifest.permission.BLUETOOTH_SCAN
    }
    if (Build.VERSION.SDK_INT <= 32) {
        list += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT <= 29) {
        list += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    return list
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun allFilesGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
    Environment.isExternalStorageManager()
} else {
    isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasInstallUnknown(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true

private fun ignoringBattery(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun Context.findActivity(): android.app.Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

private fun openIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "打不开系统设置：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openAllFiles(context: Context) {
    if (Build.VERSION.SDK_INT >= 30) {
        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching {
            context.startActivity(appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                Toast.makeText(context, "打不开所有文件访问设置，请在系统设置里手动开启", Toast.LENGTH_LONG).show()
            }
        }
    } else {
        openIntent(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }
}

private fun openShizuku(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
    runCatching { context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        }
}

@Composable
fun AccessScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var shizukuTest by remember { mutableStateOf<String?>(null) }
    var shizukuTesting by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }

    val permissions = remember { runtimePermissions() }
    val granted = permissions.count { isGranted(context, it) }
    val accessibility = remember(tick) { SystemAccess.isAccessibilityEnabled(context) }
    val state = AccessState(
        grantedRuntime = granted,
        totalRuntime = permissions.size,
        allFiles = allFilesGranted(context),
        overlay = Settings.canDrawOverlays(context),
        writeSettings = Settings.System.canWrite(context),
        usage = hasUsageAccess(context),
        installUnknown = hasInstallUnknown(context),
        battery = ignoringBattery(context),
        root = SystemAccess.isRootAvailable(),
        shizukuRunning = SystemAccess.isShizukuRunning(),
        shizukuGranted = SystemAccess.isShizukuGranted(),
    )

    val deviceStorage by vm.deviceStorage.collectAsStateWithLifecycle()
    val workspace by vm.workspace.collectAsStateWithLifecycle()
    val shellMode by vm.shellMode.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { tick++ }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Paper)
            }
            Text("全部权限", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        ) {
            Text(
                text = if (BuildConfig.PERSONAL) {
                    "个人版专用。点开需要的权限，系统会逐个询问；Play 版不包含这些敏感权限。"
                } else {
                    "当前是 Play 版，不包含个人版的全部权限。"
                },
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))

            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusLine(
                        "运行时权限",
                        "${state.grantedRuntime} / ${state.totalRuntime}",
                        state.grantedRuntime == state.totalRuntime,
                    )
                    StatusLine("所有文件访问", if (state.allFiles) "已开启" else "未开启", state.allFiles)
                    StatusLine("Root", if (state.root) "检测到 su" else "未 root", state.root)
                    StatusLine(
                        "Shizuku",
                        when {
                            state.shizukuGranted -> "已授权（shell UID）"
                            state.shizukuRunning -> "运行中，未授权"
                            else -> "未运行"
                        },
                        state.shizukuGranted,
                    )
                    StatusLine(
                        "无障碍自动化",
                        if (accessibility) "已开启" else "未开启",
                        accessibility,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (accessibility) Mint.copy(alpha = 0.4f) else Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { SystemAccess.openAccessibilitySettings(context) },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("无障碍自动化", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "开启后 Kite 能读屏幕、点其他 App、输入和滚动。Play 版不包含。",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    Text(
                        if (accessibility) "已开启" else "去开启",
                        color = if (accessibility) Mint else Ember,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val missing = permissions.filterNot { isGranted(context, it) }
                    if (missing.isEmpty()) {
                        Toast.makeText(context, "运行时权限已经全部授予", Toast.LENGTH_SHORT).show()
                    } else {
                        launcher.launch(missing.toTypedArray())
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(
                    if (state.grantedRuntime == state.totalRuntime) "运行时权限已全部授予"
                    else "申请运行时权限（还差 ${state.totalRuntime - state.grantedRuntime} 项）",
                    fontWeight = FontWeight.Bold,
                )
            }

            SectionTitle("文件")
            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    AccessRow(
                        title = "所有文件访问",
                        subtitle = if (state.allFiles) "可以读写 /sdcard 下的文件" else "去系统设置里允许 Kite 访问所有文件",
                        granted = state.allFiles,
                        onClick = { openAllFiles(context) },
                    )
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.allFiles) {
                                vm.setDeviceStorage(!deviceStorage)
                                vm.refreshWorkspace()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用手机存储工作区", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.allFiles) "工作区变成 /storage/emulated/0（手机存储根目录）"
                                else "先开启「所有文件访问」",
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                            Text(
                                workspace,
                                color = Muted.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        Switch(
                            checked = deviceStorage && state.allFiles,
                            enabled = state.allFiles,
                            onCheckedChange = {
                                vm.setDeviceStorage(it)
                                vm.refreshWorkspace()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Ember,
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = Panel2,
                                uncheckedBorderColor = Line,
                            ),
                        )
                    }
                }
            }

            SectionTitle("Shell 级别")
            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("普通", shellMode == "normal") { vm.setShellMode("normal") }
                        ModeChip("Root", shellMode == "root", enabled = state.root) { vm.setShellMode("root") }
                        ModeChip("Shizuku", shellMode == "shizuku", enabled = state.shizukuGranted) {
                            vm.setShellMode("shizuku")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (shellMode) {
                            "root" -> if (state.root) "命令以 root 身份执行，危险操作请确认。" else "没有找到 su，当前手机未 root。"
                            "shizuku" -> when {
                                !state.shizukuRunning -> "Shizuku 未运行。先安装并启动 Shizuku，再回来授权。"
                                !state.shizukuGranted -> "Shizuku 正在运行，但还没授权 Kite。"
                                else -> "命令以 shell UID（2000）执行，可用 pm / am / settings / cmd，但不是 root。"
                            }
                            else -> "普通模式：应用沙盒 UID，只能访问工作区和少量系统命令。"
                        },
                        color = if (shellMode == "root" && !state.root || shellMode == "shizuku" && !state.shizukuGranted) Danger else Muted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                    if (!state.shizukuGranted) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (!state.shizukuRunning) openShizuku(context)
                                else SystemAccess.requestShizukuPermission()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Ember),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                        ) {
                            Text(if (state.shizukuRunning) "授权 Shizuku" else "安装 / 启动 Shizuku")
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                shizukuTesting = true
                                shizukuTest = null
                                scope.launch {
                                    shizukuTest = withContext(Dispatchers.IO) {
                                        SystemAccess.runShizukuCommand("id")
                                    }
                                    shizukuTesting = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            enabled = !shizukuTesting,
                        ) {
                            Text(if (shizukuTesting) "测试中…" else "测试 Shizuku 底层（id）", fontWeight = FontWeight.Bold)
                        }
                        shizukuTest?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                result,
                                color = if (result.contains("uid=2000")) Mint else Danger,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            SectionTitle("特殊权限")
            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    AccessRow("悬浮窗", "允许在其他应用上面显示", state.overlay) {
                        openIntent(
                            context,
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                        )
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    AccessRow("修改系统设置", "亮度、音量等系统项", state.writeSettings) {
                        openIntent(
                            context,
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    AccessRow("使用情况访问", "读取应用使用记录", state.usage) {
                        openIntent(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    AccessRow("安装未知应用", "允许 App 拉起安装器", state.installUnknown) {
                        openIntent(
                            context,
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    AccessRow("忽略电池优化", "后台执行任务更稳", state.battery) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure {
                            openIntent(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    AccessRow("应用设置", "查看已授予/拒绝的权限", false, action = "打开") {
                        openIntent(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "说明：Android 不允许 App 静默获取权限。所有危险权限都必须由你在系统弹窗里点同意；Root 需要手机本身已 root；Shizuku 只是 ADB shell 权限，不是 root。",
                color = Muted.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (ok) Mint else Muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Paper, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = if (ok) Mint else Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AccessRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    action: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        Text(
            action ?: if (granted) "已开" else "去开启",
            color = if (granted) Mint else Ember,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Ember.copy(alpha = 0.16f),
            selectedLabelColor = Ember,
            containerColor = Panel2,
            labelColor = Muted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = Line,
            selectedBorderColor = Ember.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 2.dp),
    )
}
