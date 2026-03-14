package com.scrollguard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.scrollguard.ui.theme.Amber
import com.scrollguard.ui.theme.Coral
import com.scrollguard.ui.theme.Cream
import com.scrollguard.ui.theme.DeepSea
import com.scrollguard.ui.theme.Ink
import com.scrollguard.ui.theme.Mist
import com.scrollguard.ui.theme.Moss
import com.scrollguard.ui.theme.Reef
import com.scrollguard.ui.theme.Sand
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
    val monitoringActive: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasOverlayAccess: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val defaultSessionSeconds: Int = 60,
    val defaultCooldownSeconds: Int = 300,
)

class MainViewModel(private val app: ScrollGuardApp) : ViewModel() {
    private val repository = app.container.repository
    private val sessionManager = app.container.sessionManager
    private val prefs = app.getSharedPreferences("scrollguard_ui", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            defaultSessionSeconds = prefs.getInt(KEY_DEFAULT_SESSION_SECONDS, 60),
            defaultCooldownSeconds = prefs.getInt(KEY_DEFAULT_COOLDOWN_SECONDS, 300),
        )
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
        refreshRuntimeState()
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
                    timeLimitSeconds = timeLimitSeconds.coerceIn(MIN_SESSION_SECONDS, MAX_SESSION_SECONDS),
                    cooldownSeconds = cooldownSeconds.coerceIn(MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS),
                ),
            )
        }
    }

    fun removeLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeLimit(packageName)
        }
    }

    fun updateDefaultPreset(sessionSeconds: Int, cooldownSeconds: Int) {
        prefs.edit()
            .putInt(KEY_DEFAULT_SESSION_SECONDS, sessionSeconds)
            .putInt(KEY_DEFAULT_COOLDOWN_SECONDS, cooldownSeconds)
            .apply()
        _uiState.value = _uiState.value.copy(
            defaultSessionSeconds = sessionSeconds,
            defaultCooldownSeconds = cooldownSeconds,
        )
    }

    fun startMonitoring(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, AppMonitorService::class.java))
        _uiState.value = _uiState.value.copy(monitoringActive = true)
        refreshRuntimeState()
    }

    fun refreshRuntimeState() {
        _uiState.value = _uiState.value.copy(
            monitoringActive = PermissionUtils.isMonitoringActive(app),
            hasUsageAccess = PermissionUtils.hasUsageAccess(app),
            hasOverlayAccess = PermissionUtils.canDrawOverlays(app),
            hasAccessibilityAccess = PermissionUtils.isAccessibilityEnabled(app),
        )
    }

    companion object {
        private const val KEY_DEFAULT_SESSION_SECONDS = "default_session_seconds"
        private const val KEY_DEFAULT_COOLDOWN_SECONDS = "default_cooldown_seconds"
        private const val MIN_SESSION_SECONDS = 15
        private const val MAX_SESSION_SECONDS = 15 * 60
        private const val MIN_COOLDOWN_SECONDS = 60
        private const val MAX_COOLDOWN_SECONDS = 60 * 60

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
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollGuardRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: "home"
    val destinations = listOf(
        BottomDestination("home", "Home", Icons.Rounded.Shield),
        BottomDestination("limits", "Limits", Icons.Rounded.SettingsApplications),
        BottomDestination("stats", "Stats", Icons.Rounded.BarChart),
    )

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRuntimeState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(DeepSea, Reef, Amber))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("ScrollGuard", style = MaterialTheme.typography.titleLarge)
                            Text("Calmer limits for endless feeds", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        containerColor = Sand,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Cream,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        },
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
                    hasUsageAccess = uiState.hasUsageAccess,
                    hasOverlay = uiState.hasOverlayAccess,
                    hasAccessibility = uiState.hasAccessibilityAccess,
                    monitoredCount = uiState.limits.size,
                    stats = uiState.stats,
                    monitoringActive = uiState.monitoringActive,
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
                    monitoredCount = uiState.limits.size,
                    defaultSessionSeconds = uiState.defaultSessionSeconds,
                    defaultCooldownSeconds = uiState.defaultCooldownSeconds,
                    onUpdateDefaultPreset = viewModel::updateDefaultPreset,
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
    monitoringActive: Boolean,
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
            MonitoringStatusCard(monitoringActive = monitoringActive, monitoredCount = monitoredCount)
        }
        item {
            QuickStatsStrip(stats = stats, monitoredCount = monitoredCount)
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(DeepSea, Reef, Amber)))
                .padding(24.dp),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(color = Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.9f, size.height * 0.15f))
                drawCircle(color = Color.White.copy(alpha = 0.04f), radius = size.minDimension * 0.28f, center = Offset(size.width * 0.08f, size.height * 0.85f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Focus protection", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    if (allReady) "Everything is ready. Start a calmer phone session." else "Set up ScrollGuard in three quick steps.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    if (allReady) {
                        "Your chosen apps will get a live timer, doom-scroll warning, and cooldown wall when the session ends."
                    } else {
                        "Enable permissions once, choose the apps to watch, and ScrollGuard will take care of the rest in the background."
                    },
                    color = Color(0xFFF7EFE0),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    progress = { completedSteps / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroInfoPill("Setup", "$completedSteps / 3")
                    HeroInfoPill("Protected apps", monitoredCount.toString())
                }
                Button(
                    onClick = onStartMonitoring,
                    enabled = allReady && monitoredCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = DeepSea,
                        disabledContainerColor = Color.White.copy(alpha = 0.45f),
                        disabledContentColor = DeepSea.copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (monitoredCount > 0) "Start protection" else "Add apps in Limits")
                }
            }
        }
    }
}

@Composable
private fun MonitoringStatusCard(monitoringActive: Boolean, monitoredCount: Int) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (monitoringActive) Moss.copy(alpha = 0.16f) else Amber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (monitoringActive) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (monitoringActive) Moss else Coral,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (monitoringActive) "Monitoring is active" else "Monitoring has not been started yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (monitoringActive) {
                        if (monitoredCount > 0) "$monitoredCount apps are ready for protection right now." else "Turn on app limits so protection has something to guard."
                    } else {
                        "After setup, tap Start protection on Home to begin watching your selected apps."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(active = monitoringActive)
        }
    }
}

@Composable
private fun HeroInfoPill(label: String, value: String) {
    Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = Color(0xFFEEDFC6), style = MaterialTheme.typography.bodyMedium)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickStatsStrip(stats: DashboardStats, monitoredCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        QuickStatCard("Apps", monitoredCount.toString(), Icons.Rounded.SettingsApplications, Reef, Modifier.weight(1f))
        QuickStatCard("Warnings", stats.doomScrollDetections.toString(), Icons.Rounded.Warning, Coral, Modifier.weight(1f))
        QuickStatCard("Blocked", stats.blockedSessionsToday.toString(), Icons.Rounded.AccessTime, Moss, Modifier.weight(1f))
    }
}

@Composable
private fun QuickStatCard(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = tint.copy(alpha = 0.14f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(10.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Setup checklist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Turn on these permissions once so ScrollGuard can watch the foreground app, show overlays, and detect repeated scrolling.")
            PermissionStepRow("Usage access", "Detect which app is currently on screen.", hasUsageAccess, onOpenUsageAccess)
            PermissionStepRow("Overlay permission", "Show the timer bar, warning modal, and cooldown wall.", hasOverlay, onOpenOverlayAccess)
            PermissionStepRow("Accessibility service", "Watch heavy scrolling patterns inside feeds.", hasAccessibility, onOpenAccessibility)
        }
    }
}

@Composable
private fun PermissionStepRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (granted) Color(0xFFF0F8F2) else Color(0xFFFFF5E8),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (granted) Moss.copy(alpha = 0.16f) else Amber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (granted) Moss else Coral,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
                Text(if (granted) "Review" else "Enable")
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2DA))) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How a protected session works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TimelineRow("Pick the apps you tend to lose time in.")
            TimelineRow("Set a short session and a cooldown in the Limits tab.")
            TimelineRow("Start protection once the setup checklist is complete.")
            TimelineRow("ScrollGuard warns you when scrolling gets intense and blocks the app when time is up.")
        }
    }
}

@Composable
private fun TimelineRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(Amber),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable
fun AppSelectionScreen(
    installedApps: List<InstalledApp>,
    currentLimits: Map<String, AppLimitEntity>,
    monitoredCount: Int,
    defaultSessionSeconds: Int,
    defaultCooldownSeconds: Int,
    onUpdateDefaultPreset: (Int, Int) -> Unit,
    onSaveLimit: (InstalledApp, Int, Int) -> Unit,
    onRemoveLimit: (String) -> Unit,
) {
    val timeInputs = remember { mutableStateMapOf<String, String>() }
    val cooldownInputs = remember { mutableStateMapOf<String, String>() }
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyActive by remember { mutableStateOf(false) }
    var showSuggestedOnly by remember { mutableStateOf(false) }
    var selectedDefaultSession by remember(defaultSessionSeconds) { mutableStateOf(defaultSessionSeconds) }
    var selectedDefaultCooldown by remember(defaultCooldownSeconds) { mutableStateOf(defaultCooldownSeconds) }
    val suggestedCount = installedApps.count { it.isSuggested }

    val filteredApps = installedApps
        .filter { app ->
            val matchesQuery = searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = !showOnlyActive || currentLimits.containsKey(app.packageName)
            val matchesSuggested = !showSuggestedOnly || app.isSuggested
            matchesQuery && matchesFilter && matchesSuggested
        }
        .sortedWith(
            compareByDescending<InstalledApp> { currentLimits.containsKey(it.packageName) }
                .thenByDescending { it.isSuggested }
                .thenBy { it.appName.lowercase() },
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5F6))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose the apps to limit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Start with the apps that usually pull you into feeds. Suggested apps are surfaced first, and presets keep setup fast.")
                    Text("$monitoredCount apps currently protected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (suggestedCount > 0) {
                        StatusHint("Suggested first", "$suggestedCount likely doom-scroll apps were detected on this phone.")
                    }
                    PresetSection(
                        title = "Default session preset",
                        values = listOf(45, 60, 120),
                        formatter = { "${it}s" },
                        selectedValue = selectedDefaultSession,
                        onSelect = { selectedDefaultSession = it },
                    )
                    PresetSection(
                        title = "Default cooldown preset",
                        values = listOf(300, 600, 900),
                        formatter = { formatPresetDuration(it) },
                        selectedValue = selectedDefaultCooldown,
                        onSelect = { selectedDefaultCooldown = it },
                    )
                    Button(
                        onClick = { onUpdateDefaultPreset(selectedDefaultSession, selectedDefaultCooldown) },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Save default pair")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !showOnlyActive,
                    onClick = { showOnlyActive = false },
                    label = { Text("All apps") },
                )
                FilterChip(
                    selected = showOnlyActive,
                    onClick = { showOnlyActive = true },
                    label = { Text("Protected only") },
                )
                FilterChip(
                    selected = showSuggestedOnly,
                    onClick = { showSuggestedOnly = !showSuggestedOnly },
                    label = { Text("Suggested") },
                )
            }
        }
        if (filteredApps.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (searchQuery.isBlank()) "No apps match this view" else "No apps found",
                    body = if (searchQuery.isBlank()) "Try turning off Protected only or Suggested to see more apps." else "Try a different app name or clear the search field.",
                )
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val existing = currentLimits[app.packageName]
                if (!timeInputs.containsKey(app.packageName)) {
                    timeInputs[app.packageName] = existing?.timeLimitSeconds?.toString() ?: "60"
                }
                if (!cooldownInputs.containsKey(app.packageName)) {
                    cooldownInputs[app.packageName] = existing?.cooldownSeconds?.toString() ?: "300"
                }

                val isExpanded = expandedCards[app.packageName] ?: false
                val sessionValue = timeInputs[app.packageName]?.toIntOrNull()
                val cooldownValue = cooldownInputs[app.packageName]?.toIntOrNull()
                val sessionError = sessionValidationError(sessionValue)
                val cooldownError = cooldownValidationError(cooldownValue)
                val canSave = sessionError == null && cooldownError == null

                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedCards[app.packageName] = !isExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(app = app)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (existing == null) {
                                        if (app.isSuggested) "Suggested for protection" else "Not monitored yet"
                                    } else {
                                        "${formatDuration(sessionValue ?: 60)} session • ${formatDuration(cooldownValue ?: 300)} cooldown"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            StatusPill(active = existing != null)
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = timeInputs[app.packageName].orEmpty(),
                                    onValueChange = { timeInputs[app.packageName] = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Session (sec)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    isError = sessionError != null,
                                    supportingText = {
                                        Text(sessionError ?: "Recommended 45-120 seconds for quick interruption.")
                                    },
                                )
                                OutlinedTextField(
                                    value = cooldownInputs[app.packageName].orEmpty(),
                                    onValueChange = { cooldownInputs[app.packageName] = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Cooldown (sec)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    isError = cooldownError != null,
                                    supportingText = {
                                        Text(cooldownError ?: "Recommended 5-15 minutes to break the loop.")
                                    },
                                )
                            }
                            StatusHint(
                                "Current setup",
                                "${formatDuration(sessionValue ?: defaultSessionSeconds)} inside the app, then ${formatDuration(cooldownValue ?: defaultCooldownSeconds)} away from it.",
                            )
                            PresetSection(
                                title = "Quick session presets",
                                values = listOf(45, 60, 120),
                                formatter = { "${it}s" },
                                selectedValue = timeInputs[app.packageName]?.toIntOrNull(),
                                onSelect = { timeInputs[app.packageName] = it.toString() },
                            )
                            PresetSection(
                                title = "Quick cooldown presets",
                                values = listOf(300, 600, 900),
                                formatter = { formatPresetDuration(it) },
                                selectedValue = cooldownInputs[app.packageName]?.toIntOrNull(),
                                onSelect = { cooldownInputs[app.packageName] = it.toString() },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        timeInputs[app.packageName] = defaultSessionSeconds.toString()
                                        cooldownInputs[app.packageName] = defaultCooldownSeconds.toString()
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text("Use defaults")
                                }
                                Button(
                                    onClick = {
                                        onSaveLimit(
                                            app,
                                            sessionValue ?: 60,
                                            cooldownValue ?: 300,
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    enabled = canSave,
                                ) {
                                    Text(if (existing == null) "Save limit" else "Update limit")
                                }
                                if (existing != null) {
                                    OutlinedButton(onClick = { onRemoveLimit(app.packageName) }, shape = RoundedCornerShape(18.dp)) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusHint(title: String, body: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Mist) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    Surface(shape = RoundedCornerShape(999.dp), color = if (active) Moss.copy(alpha = 0.14f) else Mist) {
        Text(
            if (active) "Active" else "Idle",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (active) Moss else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    Surface(shape = RoundedCornerShape(20.dp), color = Mist) {
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Reef, DeepSea)))
                        .padding(22.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your progress today", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text("A quick pulse on how often ScrollGuard had to step in.", color = Color(0xFFE8EEF1))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardHighlightCard("Blocked sessions", stats.blockedSessionsToday.toString(), Icons.Rounded.AccessTime, Amber, Modifier.weight(1f))
                DashboardHighlightCard("Warnings", stats.doomScrollDetections.toString(), Icons.Rounded.Warning, Coral, Modifier.weight(1f))
            }
        }
        item {
            DashboardWideCard(
                title = "Most blocked app today",
                value = stats.topBlockedApp,
                subtitle = "The app that hit the limit most often today.",
                icon = Icons.Rounded.SettingsApplications,
                tint = Reef,
            )
        }
        item {
            DashboardWideCard(
                title = "Monitored apps",
                value = monitoredCount.toString(),
                subtitle = "Apps currently covered by ScrollGuard limits.",
                icon = Icons.Rounded.Shield,
                tint = Moss,
            )
        }
    }
}

@Composable
private fun DashboardHighlightCard(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = tint.copy(alpha = 0.14f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(10.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardWideCard(title: String, value: String, subtitle: String, icon: ImageVector, tint: Color) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Cream)) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}















@Composable
private fun PresetSection(
    title: String,
    values: List<Int>,
    formatter: (Int) -> String,
    selectedValue: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = selectedValue == value,
                    onClick = { onSelect(value) },
                    label = { Text(formatter(value)) },
                )
            }
        }
    }
}

private fun formatPresetDuration(seconds: Int): String {
    return if (seconds % 60 == 0) {
        "${seconds / 60}m"
    } else {
        "${seconds}s"
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return when {
        minutes > 0 && remainder > 0 -> "${minutes}m ${remainder}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun sessionValidationError(value: Int?): String? {
    return when {
        value == null -> "Enter a session length."
        value < 15 -> "Use at least 15 seconds."
        value > 15 * 60 -> "Keep sessions below 15 minutes."
        else -> null
    }
}

private fun cooldownValidationError(value: Int?): String? {
    return when {
        value == null -> "Enter a cooldown length."
        value < 60 -> "Use at least 1 minute."
        value > 60 * 60 -> "Keep cooldown below 60 minutes."
        else -> null
    }
}














