package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Subscription
import com.example.data.XrayConfig
import com.example.data.XraySettings
import com.example.ui.XrayViewModel
import com.example.ui.theme.*
import libv2ray.Libv2ray

class MainActivity : ComponentActivity() {

    private lateinit var vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private var pendingViewModel: XrayViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize libv2ray native touch
        try {
            Libv2ray.touch()
        } catch (_: Exception) {}

        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pendingViewModel?.onVpnPermissionGranted()
            } else {
                pendingViewModel?.onVpnPermissionDenied()
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainCanvas(
                    onVpnPermissionRequest = { intent, vm ->
                        pendingViewModel = vm
                        vpnPermissionLauncher.launch(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun MainCanvas(
    viewModel: XrayViewModel = viewModel(),
    onVpnPermissionRequest: ((Intent, XrayViewModel) -> Unit)? = null
) {
    val currentTab by viewModel.currentTab.collectAsState("home")
    val syncMessage by viewModel.syncMessage.collectAsState(null)
    val vpnPermissionNeeded by viewModel.vpnPermissionNeeded.collectAsState(null)
    val context = LocalContext.current

    // Handle VPN permission request
    LaunchedEffect(vpnPermissionNeeded) {
        vpnPermissionNeeded?.let { intent ->
            onVpnPermissionRequest?.invoke(intent, viewModel)
            viewModel.clearVpnPermissionRequest()
        }
    }

    // Trigger toast alerts for sync actions
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundCool,
        topBar = {
            NexusTopAppBar()
        },
        bottomBar = {
            NexusBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen rendering with fade transitions
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    "home" -> HomeScreen(viewModel)
                    "subs" -> SubscriptionsScreen(viewModel)
                    "configs" -> ConfigsScreen(viewModel)
                    "settings" -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

// Custom Handdrawn Cellular Signal bars representing elegant telemetry in Slide 1 Top Bar
@Composable
fun CellularSignalIndicator(modifier: Modifier = Modifier, color: Color = PrimaryBlue) {
    Canvas(modifier = modifier) {
        val barGap = 4.dp.toPx()
        val barWidth = 4.dp.toPx()
        val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        // 3 signal bars with adaptive height
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height * 0.6f),
            size = Size(barWidth, size.height * 0.4f),
            cornerRadius = cornerRadius
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(barWidth + barGap, size.height * 0.3f),
            size = Size(barWidth, size.height * 0.7f),
            cornerRadius = cornerRadius
        )

        drawRoundRect(
            color = color,
            topLeft = Offset((barWidth + barGap) * 2f, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = cornerRadius
        )
    }
}

@Composable
fun NexusTopAppBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LocationOn resembles server pins and is 100% present in core
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Nexus logo",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "NEXUS",
                    fontSize = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark,
                    letterSpacing = (-0.02).sp
                )
            }

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassBackground70)
                    .border(1.dp, GlassBorderOutline, CircleShape)
            ) {
                CellularSignalIndicator(
                    modifier = Modifier.size(width = 20.dp, height = 14.dp),
                    color = PrimaryBlue
                )
            }
        }
    }
}

@Composable
fun NexusBottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(68.dp)
                .border(1.dp, GlassBorderSpecular, RoundedCornerShape(999.dp)),
            shape = RoundedCornerShape(999.dp),
            color = GlassBackground85,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Home Tab
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isActive = currentTab == "home",
                    testTag = "home_tab",
                    onClick = { onTabSelected("home") }
                )

                // Subscriptions Tab (Refresh resembles feeds/synchronizing core beautifully)
                BottomNavItem(
                    icon = Icons.Default.Refresh,
                    label = "Subs",
                    isActive = currentTab == "subs",
                    testTag = "subscriptions_tab",
                    onClick = { onTabSelected("subs") }
                )

                // Configs Tab (LocationOn represents configurations endpoints beautifully)
                BottomNavItem(
                    icon = Icons.Default.LocationOn,
                    label = "Configs",
                    isActive = currentTab == "configs",
                    testTag = "configs_tab",
                    onClick = { onTabSelected("configs") }
                )

                // Settings Tab
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isActive = currentTab == "settings",
                    testTag = "settings_tab",
                    onClick = { onTabSelected("settings") }
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val scaleFactor by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "navScale"
    )

    Column(
        modifier = Modifier
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .scale(scaleFactor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) PrimaryBlue else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        if (isActive) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
            )
        }
    }
}

// --- Home Screen View ---
@Composable
fun HomeScreen(viewModel: XrayViewModel) {
    val isConnected by viewModel.isConnected.collectAsState(false)
    val isConnecting by viewModel.isConnecting.collectAsState(false)
    val connectionStatus by viewModel.connectionStatus.collectAsState("Not Protected")
    val allConfigs by viewModel.allConfigs.collectAsState(emptyList())
    val selectedId by viewModel.selectedConfigId.collectAsState(null)
    val downloadSpeed by viewModel.downloadSpeed.collectAsState("--")
    val uploadSpeed by viewModel.uploadSpeed.collectAsState("--")

    val activeConfig = allConfigs.find { it.id == selectedId }

    // Server Location + Flag Indicator declarations scoped correctly at top
    val serverEmoji = when (activeConfig?.protocol) {
        "VLESS" -> "🇩🇪"
        "VMESS" -> "🇳🇱"
        "TROJAN" -> "🇺🇸"
        "REALITY" -> "🇸🇬"
        else -> "🌐"
    }

    val serverLocationName = when (activeConfig?.protocol) {
        "VLESS" -> "Frankfurt, Germany"
        "VMESS" -> "Amsterdam, Netherlands"
        "TROJAN" -> "New York, USA"
        "REALITY" -> "Singapore"
        else -> "Optimal Routing"
    }

    // Circular Halos scaling animations
    val infiniteTransition = rememberInfiniteTransition(label = "haloTransition")
    val haloPulse1 by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnected) 1200 else 2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    val haloPulse2 by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnected) 1400 else 2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Orb Core System
        Box(
            modifier = Modifier
                .size(300.dp)
                .padding(top = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Halo Backing Ring 2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = haloPulse2
                        scaleY = haloPulse2
                    }
                    .border(
                        width = 1.dp,
                        color = if (isConnected) PrimaryBlue.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.04f),
                        shape = CircleShape
                    )
            )

            // Halo Backing Ring 1
            Box(
                modifier = Modifier
                    .fillMaxSize(0.8f)
                    .graphicsLayer {
                        scaleX = haloPulse1
                        scaleY = haloPulse1
                    }
                    .border(
                        width = 1.dp,
                        color = if (isConnected) PrimaryBlue.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isConnected) PrimaryBlue.copy(alpha = 0.01f) else Color.Transparent,
                        shape = CircleShape
                    )
            )

            // Concentric Glow Halo 0
            Box(
                modifier = Modifier
                    .fillMaxSize(0.6f)
                    .border(
                        width = 1.dp,
                        color = if (isConnected) PrimaryBlue.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isConnected) PrimaryBlue.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.01f),
                        shape = CircleShape
                    )
            )

            // Dynamic Power Button (Glass FAB)
            Surface(
                onClick = { viewModel.toggleConnection() },
                modifier = Modifier
                    .size(116.dp)
                    .testTag("connect_button"),
                shape = CircleShape,
                color = GlassBackground85,
                tonalElevation = 8.dp,
                shadowElevation = if (isConnected) 24.dp else 12.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom Handcrafted Canvas Power Button representing Slide 1 visual
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(54.dp)
                        )
                    } else {
                        val powerColor = if (isConnected) PrimaryBlue else OnSurfaceVariantMuted
                        Canvas(modifier = Modifier.size(44.dp)) {
                            val strokeWidth = 3.dp.toPx()
                            // Circle Arc with top gap
                            drawArc(
                                color = powerColor,
                                startAngle = -240f,
                                sweepAngle = 300f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            // Clean central tick
                            drawLine(
                                color = powerColor,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height * 0.45f),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Connection State Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = connectionStatus,
                fontSize = 28.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                color = if (isConnected) PrimaryBlue else OnSurfaceDark
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location marker",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$serverLocationName $serverEmoji",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Server Selection Ribbon Card
        Surface(
            onClick = { viewModel.selectTab("configs") },
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = GlassBackground70,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Flag circle Container
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainerBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = serverEmoji,
                            fontSize = 20.sp
                        )
                    }

                    // Labels
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = activeConfig?.name ?: "No Config Selected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDark
                        )
                        Text(
                            text = "Smart Routing Active",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                // Latency Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) GreenSuccess else OrangeWarning)
                    )
                    Text(
                        text = if (activeConfig?.ping != null) "${activeConfig.ping} ms" else "Offline",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) GreenSuccess else OrangeWarning,
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Configs navigation",
                        tint = OnSurfaceVariantMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Speed Metrics dashboard dashboard widgets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Download speed Widget
            Surface(
                modifier = Modifier
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = GlassBackground70,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Download telemetry index",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Download",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = downloadSpeed,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = OnSurfaceDark
                        )
                        Text(
                            text = "Mbps",
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            // Upload speed Widget (Arrow Up is KeyboardArrowUp)
            Surface(
                modifier = Modifier
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = GlassBackground70,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Upload telemetry index",
                            tint = OrangeWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Upload",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uploadSpeed,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = OnSurfaceDark
                        )
                        Text(
                            text = "Mbps",
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Subscriptions Screen View (Manage sources) ---
@Composable
fun SubscriptionsScreen(viewModel: XrayViewModel) {
    val subscriptions by viewModel.allSubscriptions.collectAsState(emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState(false)
    
    var showAddDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var inputUrl by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Subscription Source") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Source Name") },
                        placeholder = { Text("e.g. Frankfurt Feed") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Feed URL") },
                        placeholder = { Text("https://example.com/sub") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputName.isNotBlank() && inputUrl.isNotBlank()) {
                            viewModel.addSubscription(inputName, inputUrl)
                            showAddDialog = false
                            inputName = ""
                            inputUrl = ""
                        }
                    }
                ) {
                    Text("Add Feed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feed titles layout group
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Subscriptions",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
                Text(
                    text = "Manage sources",
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("add_subscription_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add feed option icon", modifier = Modifier.size(16.dp))
                    Text("Add Source")
                }
            }
        }

        // Fast Sync Trigger Action card
        Surface(
            onClick = { viewModel.syncSubscriptions() },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorderOutline, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = GlassBackground70
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync indices trigger",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSyncing) "Syncing..." else "Sync Feeds & Discover Servers",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceDark
                    )
                }

                if (isSyncing) {
                    CircularProgressIndicator(
                        color = PrimaryBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Fast synchronize action clicker",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Empty Feed visual indicator or item rows
        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Database is bare",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No alternative sources",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Add custom sources to discover secure endpoints.",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(subscriptions) { sub: Subscription ->
                    SubscriptionItemRow(
                        subscription = sub,
                        onDelete = { viewModel.removeSubscription(sub) }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionItemRow(
    subscription: Subscription,
    onDelete: () -> Unit
) {
    val isActive = subscription.status == "Active"
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassBackground70,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscription.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isActive) GreenSuccessContainer else RedErrorContainer)
                        .border(
                            1.dp,
                            if (isActive) GreenSuccess.copy(alpha = 0.2f) else RedError.copy(alpha = 0.2f),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = subscription.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) GreenSuccess else RedError
                    )
                }
            }

            // URL Code Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundCool)
                    .border(1.dp, GlassBorderOutline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = subscription.url,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceVariantMuted,
                    maxLines = 1
                )
            }

            // Info and delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Active timestamp indicator",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Updated recently",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove subscription item feed",
                        tint = RedError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- Configs Screen View (Chips search and server items) ---
@Composable
fun ConfigsScreen(viewModel: XrayViewModel) {
    val allConfigs by viewModel.allConfigs.collectAsState(emptyList())
    val selectedId by viewModel.selectedConfigId.collectAsState(null)
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocolFilter by remember { mutableStateOf("All") }
    val protocols = listOf("All", "VLESS", "VMess", "Trojan", "Reality")

    var showAddConfigDialog by remember { mutableStateOf(false) }
    var inputConfigString by remember { mutableStateOf("") }

    if (showAddConfigDialog) {
        AlertDialog(
            onDismissRequest = { showAddConfigDialog = false },
            title = { Text("Paste Xray VLESS/VMess/Trojan URI") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paste a raw protocol connection string to discover and map server endpoints.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = inputConfigString,
                        onValueChange = { inputConfigString = it },
                        label = { Text("URI Connection String") },
                        placeholder = { Text("vless://uuid@host:port... or vmess://...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputConfigString.isNotBlank()) {
                            val success = viewModel.addConfigFromString(inputConfigString)
                            if (success) {
                                showAddConfigDialog = false
                                inputConfigString = ""
                            }
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core titles layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Configurations",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
                Text(
                    text = "Find and test low latency endpoints",
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Optimal TCPing trigger for finding lowest pings possible immediately
                IconButton(
                    onClick = { viewModel.testAllPingsAndOptimize() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassBackground70)
                        .border(1.dp, GlassBorderOutline, CircleShape)
                        .testTag("test_all_pings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, // Standard refresh represents re-testing beautifully
                        contentDescription = "Test layout latencies",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showAddConfigDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassBackground70)
                        .border(1.dp, GlassBorderOutline, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import configuration",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search text-field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search configs...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Lookup config filter") },
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("config_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue.copy(alpha = 0.4f),
                unfocusedBorderColor = GlassBorderOutline,
                focusedContainerColor = GlassBackground70,
                unfocusedContainerColor = GlassBackground70
            )
        )

        // Horizontally scrollable protocol chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            protocols.forEach { proto ->
                val isActive = selectedProtocolFilter.equals(proto, ignoreCase = true)
                Surface(
                    onClick = { selectedProtocolFilter = proto },
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)),
                    shape = RoundedCornerShape(999.dp),
                    color = if (isActive) PrimaryBlue else GlassBackground70,
                    contentColor = if (isActive) Color.White else OnSurfaceVariantMuted,
                    tonalElevation = if (isActive) 4.dp else 0.dp
                ) {
                    Text(
                        text = proto,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Listing configs
        val listItems = allConfigs.filter { config ->
            val matchQuery = config.name.contains(searchQuery, ignoreCase = true) || 
                             config.address.contains(searchQuery, ignoreCase = true)
            val matchProto = selectedProtocolFilter.equals("All", ignoreCase = true) || 
                             config.protocol.equals(selectedProtocolFilter, ignoreCase = true)
            matchQuery && matchProto
        }

        if (listItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No configurations found in database",
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listItems) { config: XrayConfig ->
                    val isSelected = config.id == selectedId
                    ConfigItemRow(
                        config = config,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectConfig(config.id) },
                        onPingTest = { viewModel.testNodePing(config) },
                        onStarToggle = { viewModel.toggleStarred(config) },
                        onDelete = { viewModel.deleteConfig(config) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigItemRow(
    config: XrayConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPingTest: () -> Unit,
    onStarToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val serverEmoji = when (config.protocol) {
        "VLESS" -> "🇩🇪"
        "VMESS" -> "🇳🇱"
        "TROJAN" -> "🇺🇸"
        "REALITY" -> "🇸🇬"
        else -> "🌐"
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) PrimaryBlue else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        color = GlassBackground70,
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flag Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainerBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = serverEmoji,
                    fontSize = 18.sp
                )
            }

            // Central details box
            Column(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = config.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    
                    IconButton(
                        onClick = onStarToggle,
                        modifier = Modifier.size(20.dp)
                    ) {
                        // Unstarred opacity matches extended visual beautifully on a single core Star icon
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Toggle bookmark configuration",
                            tint = if (config.isStarred) OrangeWarning else TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(if (config.isStarred) 1f else 0.4f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = config.protocol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = config.address,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        maxLines = 1
                    )
                    
                    // Transport channel tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BackgroundCool)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = config.streamTransport,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariantMuted
                        )
                    }
                }
            }

            // Latency metrics and delete actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Ping Tester Badge Button
                Surface(
                    onClick = onPingTest,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)),
                    shape = RoundedCornerShape(99.dp),
                    color = when {
                        config.ping == null -> BackgroundCool
                        config.ping <= 80 -> GreenSuccessContainer
                        config.ping <= 160 -> OrangeWarningContainer
                        else -> RedErrorContainer
                    },
                    contentColor = when {
                        config.ping == null -> TextMuted
                        config.ping <= 80 -> GreenSuccess
                        config.ping <= 160 -> OrangeWarning
                        else -> RedError
                    }
                ) {
                    Text(
                        text = if (config.ping != null) "${config.ping}ms" else "Test",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove config from client list",
                        tint = RedError.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- Settings Screen View ---
@Composable
fun SettingsScreen(viewModel: XrayViewModel) {
    val settingsState by viewModel.settings.collectAsState(XraySettings())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Text(
                text = "Configure network parameters and application behavior.",
                fontSize = 14.sp,
                color = TextMuted
            )
        }

        // Section 1: DNS Settings group
        SettingsCategorySection(title = "DNS") {
            SettingsToggleRow(
                title = "Local DNS",
                description = null,
                checked = settingsState.localDns,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(localDns = it)) }
            )

            SettingsInputRow(
                title = "Primary DNS",
                value = settingsState.primaryDns,
                onValueChange = { viewModel.updateSettings(settingsState.copy(primaryDns = it)) }
            )

            SettingsInputRow(
                title = "Secondary DNS",
                value = settingsState.secondaryDns,
                onValueChange = { viewModel.updateSettings(settingsState.copy(secondaryDns = it)) }
            )

            SettingsToggleRow(
                title = "Fake DNS",
                description = "Resolve IP via fake pool",
                checked = settingsState.fakeDns,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(fakeDns = it)) }
            )

            SettingsStrategyDropdownRow(
                title = "Strategy",
                selectedOption = settingsState.dnsStrategy,
                options = listOf("prefer_ipv4", "prefer_ipv6", "ipv4_only", "ipv6_only"),
                onSelectOption = { viewModel.updateSettings(settingsState.copy(dnsStrategy = it)) }
            )
        }

        // Section 2: ADVANCED PROTOCOL Settings group
        SettingsCategorySection(title = "ADVANCED PROTOCOL") {
            SettingsToggleRow(
                title = "Mux",
                description = "Allows multiple TCP/UDP streams over single connection",
                checked = settingsState.muxEnabled,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(muxEnabled = it)) }
            )

            SettingsToggleRow(
                title = "Fragment",
                description = "Splits packets to bypass DPI rules",
                checked = settingsState.fragmentEnabled,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(fragmentEnabled = it)) }
            )
        }

        // Section 3: GENERAL Settings group
        SettingsCategorySection(title = "GENERAL & ENGINE") {
            SettingsToggleRow(
                title = "Start on Boot",
                description = null,
                checked = settingsState.startOnBoot,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(startOnBoot = it)) }
            )

            SettingsToggleRow(
                title = "Allow Insecure",
                description = "Skip TLS certificates validation rules",
                checked = settingsState.allowInsecure,
                onCheckedChange = { viewModel.updateSettings(settingsState.copy(allowInsecure = it)) }
            )

            // Xray Core version display supporting xHTTP streams
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Xray Core Version",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Supports xhttp streams (v26.4.5) for secure low-latency relays.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(PrimaryContainerBlue)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "v26.4.5-xhttp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategorySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 14.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = GlassBackground70,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceDark
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BackgroundCool
            )
        )
    }
}

@Composable
fun SettingsInputRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceDark,
            modifier = Modifier.weight(1f)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = OnSurfaceDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .width(145.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BackgroundCool)
                .border(1.dp, GlassBorderOutline, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SettingsStrategyDropdownRow(
    title: String,
    selectedOption: String,
    options: List<String>,
    onSelectOption: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceDark
        )

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedOption.uppercase().replace("_", " "),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Strategy dropdown selection list arrow",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.uppercase().replace("_", " ")) },
                        onClick = {
                            onSelectOption(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
