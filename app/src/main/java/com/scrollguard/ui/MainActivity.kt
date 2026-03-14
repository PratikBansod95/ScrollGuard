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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollGuardRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: "home"
    val destinations = listOf(
        BottomDestination("home", "Home", Icons.Rounded.Security),
        BottomDestination("limits", "Limits", Icons.Rounded.SettingsApplications),
        BottomDestination("stats", "Stats", Icons.Rounded.BarChart),
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("ScrollGuard") }) },
        containerColor = Color(0xFFF8F4EC),
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navController.navigate(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    hasUsageAccess = PermissionUtils.hasUsageAccess(context),
                    hasOverlay = PermissionUtils.canDrawOverlays(context),
                    hasAccessibility = PermissionUtils.isAccessibilityEnabled(context),
                    monitoredCount = uiState.limits.size,
                    stats = uiState.stats,
                    onOpenUsageAccess = { context.startActivity(PermissionUtils.usageAccessIntent()) },
                    onOpenOverlayAccess = { context.startActivity(PermissionUtils.overlayIntent(context)) },
                    onOpenAccessibility = { context.startActivity(PermissionUtils.accessibilityIntent()) },
                    onStartMonitoring = { viewModel.startMonitoring(context) },
                )
            }
            composable("limits") {
                AppSelectionScreen(
                    installedApps = uiState.installedApps,
                    currentLimits = uiState.limits.associateBy { it.packageName },
                    onSaveLimit = viewModel::saveLimit,
                    onRemoveLimit = viewModel::removeLimit,
                )
            }
            composable("stats") {
                DashboardScreen(stats = uiState.stats, monitoredCount = uiState.limits.size)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    monitoredCount: Int,
    stats: DashboardStats,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onStartMonitoring: () -> Unit,
) {
    val completedSteps = listOf(hasUsageAccess, hasOverlay, hasAccessibility).count { it }
    val allReady = completedSteps == 3

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FocusHeroCard(
                monitoredCount = monitoredCount,
                allReady = allReady,
                completedSteps = completedSteps,
                onStartMonitoring = onStartMonitoring,
            )
        }
        item {
            SetupChecklistCard(
                hasUsageAccess = hasUsageAccess,
                hasOverlay = hasOverlay,
                hasAccessibility = hasAccessibility,
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenOverlayAccess = onOpenOverlayAccess,
                onOpenAccessibility = onOpenAccessibility,
            )
        }
        item {
            TodayOverviewCard(stats = stats, monitoredCount = monitoredCount)
        }
        item {
            HowItWorksCard()
        }
    }
}

@Composable
private fun FocusHeroCard(
    monitoredCount: Int,
    allReady: Boolean,
    completedSteps: Int,
    onStartMonitoring: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF17313A), Color(0xFF285363), Color(0xFFD8A04D)),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (allReady) "You are ready to guard your focus" else "Finish setup, then start monitoring",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (allReady) {
                        "ScrollGuard can now show the timer bar, detect rapid scrolling, and block the app when time runs out."
                    } else {
                        "There are only three permissions to enable. Once they are on, the app can watch selected apps and step in when a session goes too far."
                    },
                    color = Color(0xFFF4ECDD),
                )
                LinearProgressIndicator(
                    progress = { completedSteps / 3f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Setup progress: $completedSteps of 3 complete", color = Color.White)
                Text("Apps protected: $monitoredCount", color = Color.White)
                Button(onClick = onStartMonitoring, enabled = allReady && monitoredCount > 0) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(if (monitoredCount > 0) "Start protection" else "Add apps in Limits first")
                }
            }
        }
    }
}

@Composable
private fun SetupChecklistCard(
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Setup checklist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Enable each permission once. After that, ScrollGuard can work in the background without extra steps.")
            PermissionStepRow(
                title = "Usage access",
                subtitle = "Detect which app is on screen.",
                granted = hasUsageAccess,
                onClick = onOpenUsageAccess,
            )
            PermissionStepRow(
                title = "Overlay permission",
                subtitle = "Show the live timer and blocking screen.",
                granted = hasOverlay,
                onClick = onOpenOverlayAccess,
            )
            PermissionStepRow(
                title = "Accessibility service",
                subtitle = "Track repeated scrolling inside monitored apps.",
                granted = hasAccessibility,
                onClick = onOpenAccessibility,
            )
        }
    }
}

@Composable
private fun PermissionStepRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF1A7F37) else Color(0xFFB54708),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) {
            Text(if (granted) "Review" else "Enable")
        }
    }
}

@Composable
private fun TodayOverviewCard(stats: DashboardStats, monitoredCount: Int) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Today at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OverviewRow("Monitored apps", monitoredCount.toString())
            OverviewRow("Blocked sessions", stats.blockedSessionsToday.toString())
            OverviewRow("Doom-scroll warnings", stats.doomScrollDetections.toString())
            OverviewRow("Most recent blocked app", stats.mostUsedApp)
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2DA)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("How protection works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("1. Choose the apps you want to limit in the Limits tab.")
            Text("2. Set a session length and cooldown for each app.")
            Text("3. Start protection once the three permissions are enabled.")
            Text("4. ScrollGuard shows a timer bar, warns on heavy scrolling, and blocks the app when the session ends.")
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5F6))) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Choose the apps to limit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Set a short session timer and a cooldown. Example: 60 seconds of use, then 5 minutes away.")
                }
            }
        }
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
                                if (existing == null) "Not monitored yet" else "Currently limited",
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
                            Text(if (existing == null) "Save limit" else "Update limit")
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
