package com.scrollguard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scrollguard.ScrollGuardApp
import com.scrollguard.data.AppLimitEntity
import com.scrollguard.logic.DashboardStats
import com.scrollguard.services.AppMonitorService
import com.scrollguard.ui.theme.ScrollGuardTheme
import com.scrollguard.utils.AppUtils
import com.scrollguard.utils.InstalledApp
import com.scrollguard.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ScrollGuardApp
        setContent {
            ScrollGuardTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(app))
                ScrollGuardRoot(viewModel = viewModel)
            }
        }
    }
}

data class MainUiState(
    val limits: List<AppLimitEntity> = emptyList(),
    val stats: DashboardStats = DashboardStats(),
    val installedApps: List<InstalledApp> = emptyList(),
)

class MainViewModel(private val app: ScrollGuardApp) : ViewModel() {
    private val repository = app.container.repository
    private val sessionManager = app.container.sessionManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLimits().collect { limits ->
                _uiState.value = _uiState.value.copy(limits = limits)
            }
        }
        viewModelScope.launch {
            sessionManager.dashboardStats.collect { stats ->
                _uiState.value = _uiState.value.copy(stats = stats)
            }
        }
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(installedApps = AppUtils.getLaunchableApps(app))
        }
    }

    fun saveLimit(appItem: InstalledApp, timeLimitSeconds: Int, cooldownSeconds: Int) {
        viewModelScope.launch {
            repository.saveLimit(
                AppLimitEntity(
                    packageName = appItem.packageName,
                    appName = appItem.appName,
                    timeLimitSeconds = timeLimitSeconds,
                    cooldownSeconds = cooldownSeconds,
                ),
            )
        }
    }

    fun removeLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeLimit(packageName)
        }
    }

    fun startMonitoring(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, AppMonitorService::class.java))
    }

    companion object {
        fun factory(app: ScrollGuardApp): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollGuardRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: "main"

    Scaffold(
        topBar = { TopAppBar(title = { Text("ScrollGuard") }) },
        containerColor = Color(0xFFF7F2E8),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF203A43))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                NavChip("main", currentRoute, Icons.Rounded.Security) { navController.navigate("main") }
                NavChip("apps", currentRoute, Icons.Rounded.SettingsApplications) { navController.navigate("apps") }
                NavChip("dashboard", currentRoute, Icons.Rounded.BarChart) { navController.navigate("dashboard") }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("main") {
                MainScreen(
                    hasUsageAccess = PermissionUtils.hasUsageAccess(context),
                    hasOverlay = PermissionUtils.canDrawOverlays(context),
                    hasAccessibility = PermissionUtils.isAccessibilityEnabled(context),
                    monitoredCount = uiState.limits.size,
                    onOpenUsageAccess = { context.startActivity(PermissionUtils.usageAccessIntent()) },
                    onOpenOverlayAccess = { context.startActivity(PermissionUtils.overlayIntent(context)) },
                    onOpenAccessibility = { context.startActivity(PermissionUtils.accessibilityIntent()) },
                    onStartMonitoring = { viewModel.startMonitoring(context) },
                )
            }
            composable("apps") {
                AppSelectionScreen(
                    installedApps = uiState.installedApps,
                    currentLimits = uiState.limits.associateBy { it.packageName },
                    onSaveLimit = viewModel::saveLimit,
                    onRemoveLimit = viewModel::removeLimit,
                )
            }
            composable("dashboard") {
                DashboardScreen(stats = uiState.stats, monitoredCount = uiState.limits.size)
            }
        }
    }
}

@Composable
private fun NavChip(
    route: String,
    currentRoute: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = currentRoute == route,
        onClick = onClick,
        label = { Text(route.replaceFirstChar { it.uppercase() }) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
fun MainScreen(
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    monitoredCount: Int,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onStartMonitoring: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(monitoredCount = monitoredCount, onStartMonitoring = onStartMonitoring)
        }
        item {
            PermissionCard(
                title = "Usage Access",
                granted = hasUsageAccess,
                description = "Lets ScrollGuard detect when monitored apps move into the foreground.",
                buttonText = "Enable usage access",
                onClick = onOpenUsageAccess,
            )
        }
        item {
            PermissionCard(
                title = "Overlay Permission",
                granted = hasOverlay,
                description = "Needed for the timer bar, doom-scroll warning, and full-screen blocking overlay.",
                buttonText = "Enable overlays",
                onClick = onOpenOverlayAccess,
            )
        }
        item {
            PermissionCard(
                title = "Accessibility Service",
                granted = hasAccessibility,
                description = "Listens for TYPE_VIEW_SCROLLED events so ScrollGuard can spot continuous scrolling.",
                buttonText = "Enable accessibility",
                onClick = onOpenAccessibility,
            )
        }
        if (!(hasUsageAccess && hasOverlay && hasAccessibility)) {
            item { WarningCard() }
        }
    }
}

@Composable
private fun HeroCard(monitoredCount: Int, onStartMonitoring: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF203A43), Color(0xFF2C5364), Color(0xFFDBA858)),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Break the scroll loop",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Track social apps, show a live session bar, detect frantic scrolling, and intervene before the session disappears.",
                    color = Color(0xFFF2ECE2),
                )
                Text("Monitored apps: $monitoredCount", color = Color.White)
                Button(onClick = onStartMonitoring) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Start monitoring")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(if (granted) "Enabled" else "Needs attention", color = if (granted) Color(0xFF1A7F37) else Color(0xFFB54708))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0D5)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Monitoring can be bypassed if permissions are turned off.", fontWeight = FontWeight.Bold)
            Text("Re-enable any missing permission before relying on ScrollGuard to enforce breaks.")
        }
    }
}

@Composable
fun AppSelectionScreen(
    installedApps: List<InstalledApp>,
    currentLimits: Map<String, AppLimitEntity>,
    onSaveLimit: (InstalledApp, Int, Int) -> Unit,
    onRemoveLimit: (String) -> Unit,
) {
    val timeInputs = remember { mutableStateMapOf<String, String>() }
    val cooldownInputs = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(installedApps, key = { it.packageName }) { app ->
            val existing = currentLimits[app.packageName]
            if (!timeInputs.containsKey(app.packageName)) {
                timeInputs[app.packageName] = existing?.timeLimitSeconds?.toString() ?: "60"
            }
            if (!cooldownInputs.containsKey(app.packageName)) {
                cooldownInputs[app.packageName] = existing?.cooldownSeconds?.toString() ?: "300"
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app = app)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = timeInputs[app.packageName].orEmpty(),
                            onValueChange = { timeInputs[app.packageName] = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Session sec") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = cooldownInputs[app.packageName].orEmpty(),
                            onValueChange = { cooldownInputs[app.packageName] = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Cooldown sec") },
                            singleLine = true,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                onSaveLimit(
                                    app,
                                    timeInputs[app.packageName]?.toIntOrNull() ?: 60,
                                    cooldownInputs[app.packageName]?.toIntOrNull() ?: 300,
                                )
                            },
                        ) {
                            Text(if (existing == null) "Monitor app" else "Update")
                        }
                        if (existing != null) {
                            OutlinedButton(onClick = { onRemoveLimit(app.packageName) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFECE7DF)) {
        AndroidView(
            modifier = Modifier.height(56.dp),
            factory = { context ->
                ImageView(context).apply {
                    setPadding(12, 12, 12, 12)
                    setImageDrawable(app.icon)
                }
            },
            update = { imageView -> imageView.setImageDrawable(app.icon) },
        )
    }
}

@Composable
fun DashboardScreen(stats: DashboardStats, monitoredCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatCard(title = "Blocked sessions today", value = stats.blockedSessionsToday.toString())
        StatCard(title = "Doom scroll detections", value = stats.doomScrollDetections.toString())
        StatCard(title = "Most recent blocked app", value = stats.mostUsedApp)
        StatCard(title = "Monitored apps", value = monitoredCount.toString())
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
