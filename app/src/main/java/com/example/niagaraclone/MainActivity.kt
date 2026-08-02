package com.niagaraclone

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class TopSection {
    HEADER,
    WIDGETS
}

enum class HeaderItem {
    CLOCK,
    DATE_WEATHER,
    BATTERY
}

class MainActivity : ComponentActivity() {
    private lateinit var appWidgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetHost = AppWidgetHost(applicationContext, 1024)
        appWidgetHost.startListening()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen(appWidgetHost)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    companion object {
        fun openDefaultLauncherSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    context.startActivity(intent)
                    return
                }
            }
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(appWidgetHost: AppWidgetHost) {
    val context = LocalContext.current
    val installedApps = remember { getInstalledApps(context) }
    val alphabet = remember { ('A'..'Z').toList() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val iconPackManager = remember { IconPackManager(context) }
    var selectedAppForCustomization by remember { mutableStateOf<ResolveInfo?>(null) }

    val widgetManager = AppWidgetManager.getInstance(context)
    val widgetProviders = remember { widgetManager.installedProviders }

    // Persistent Preferences
    val prefs = remember { context.getSharedPreferences("layout_prefs", Context.MODE_PRIVATE) }
    var isEditMode by remember { mutableStateOf(false) }
    var isAppDrawerOpen by remember { mutableStateOf(false) }

    // Close full app drawer on system back press
    BackHandler(enabled = isAppDrawerOpen) {
        isAppDrawerOpen = false
    }

    // Pinned Apps
    val pinnedPackages = remember { mutableStateListOf<String>() }

    // Top Section and Header Order
    val topSectionOrder = remember { mutableStateListOf(TopSection.HEADER, TopSection.WIDGETS) }
    val headerItemsOrder = remember { mutableStateListOf(HeaderItem.CLOCK, HeaderItem.DATE_WEATHER, HeaderItem.BATTERY) }

    fun saveLayoutPreferences() {
        val topOrderString = topSectionOrder.joinToString(",") { it.name }
        val headerOrderString = headerItemsOrder.joinToString(",") { it.name }
        val pinnedString = pinnedPackages.joinToString(",")
        prefs.edit()
            .putString("top_section_order", topOrderString)
            .putString("header_items_order", headerOrderString)
            .putString("pinned_packages", pinnedString)
            .apply()
    }

    LaunchedEffect(Unit) {
        val savedTop = prefs.getString("top_section_order", null)
        if (savedTop != null) {
            try {
                topSectionOrder.clear()
                topSectionOrder.addAll(savedTop.split(",").map { TopSection.valueOf(it) })
            } catch (_: Exception) {}
        }

        val savedHeader = prefs.getString("header_items_order", null)
        if (savedHeader != null) {
            try {
                headerItemsOrder.clear()
                headerItemsOrder.addAll(savedHeader.split(",").map { HeaderItem.valueOf(it) })
            } catch (_: Exception) {}
        }

        val savedPinned = prefs.getString("pinned_packages", null)
        if (!savedPinned.isNullOrEmpty()) {
            pinnedPackages.clear()
            pinnedPackages.addAll(savedPinned.split(","))
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Edit Mode Notification Bar
            if (isEditMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Edit Mode: Use ▲ / ▼ to reorder items",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Button(onClick = {
                            isEditMode = false
                            saveLayoutPreferences()
                        }) {
                            Text("Done")
                        }
                    }
                }
            }

            // Top Sections (Header Block & Widget Block)
            topSectionOrder.forEachIndexed { index, section ->
                val elevation by animateDpAsState(if (isEditMode) 4.dp else 0.dp, label = "elevation")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation)
                        .background(
                            if (isEditMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.background
                        )
                ) {
                    Column {
                        if (isEditMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (section == TopSection.HEADER) "Header Block" else "Widget Block",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row {
                                    if (index > 0) {
                                        IconButton(onClick = {
                                            val item = topSectionOrder.removeAt(index)
                                            topSectionOrder.add(index - 1, item)
                                        }) { Text("▲") }
                                    }
                                    if (index < topSectionOrder.size - 1) {
                                        IconButton(onClick = {
                                            val item = topSectionOrder.removeAt(index)
                                            topSectionOrder.add(index + 1, item)
                                        }) { Text("▼") }
                                    }
                                }
                            }
                        }

                        when (section) {
                            TopSection.HEADER -> {
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { isEditMode = true }
                                    )
                                ) {
                                    CustomizableHeader(
                                        itemsOrder = headerItemsOrder,
                                        isEditMode = isEditMode
                                    )
                                }
                            }

                            TopSection.WIDGETS -> {
                                if (widgetProviders.isNotEmpty()) {
                                    val pagerState = rememberPagerState(pageCount = { widgetProviders.size.coerceAtMost(5) })
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { isEditMode = true }
                                            )
                                    ) {
                                        HorizontalPager(state = pagerState) { page ->
                                            val info = widgetProviders[page]
                                            WidgetView(appWidgetHost, info)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Zone: Main Screen Pinned Apps vs Full App Drawer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!isAppDrawerOpen) {
                    val pinnedAppsList = installedApps.filter { pinnedPackages.contains(it.activityInfo.packageName) }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(pinnedAppsList) { _, app ->
                            AppItemRow(
                                app = app,
                                iconPackManager = iconPackManager,
                                context = context,
                                onLongClick = { selectedAppForCustomization = app }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isAppDrawerOpen,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(installedApps) { _, app ->
                            AppItemRow(
                                app = app,
                                iconPackManager = iconPackManager,
                                context = context,
                                onLongClick = { selectedAppForCustomization = app }
                            )
                        }
                    }
                }
            }
        }

        // Alphabet Fast-Scroller Sidebar
        Column(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isAppDrawerOpen = true
                        },
                        onVerticalDrag = { change, _ ->
                            isAppDrawerOpen = true
                            val itemHeight = size.height / alphabet.size
                            val index = (change.position.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                            val targetLetter = alphabet[index]

                            val targetAppIndex = installedApps.indexOfFirst {
                                it.loadLabel(context.packageManager)
                                    .toString()
                                    .startsWith(targetLetter, ignoreCase = true)
                            }

                            if (targetAppIndex != -1) {
                                coroutineScope.launch {
                                    listState.scrollToItem(targetAppIndex)
                                }
                            }
                        }
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    // Modal Dialog for App Options and Icon Picker
    selectedAppForCustomization?.let { app ->
        val pkg = app.activityInfo.packageName
        val isPinned = pinnedPackages.contains(pkg)

        AppOptionsModal(
            app = app,
            isPinned = isPinned,
            onTogglePin = {
                if (isPinned) pinnedPackages.remove(pkg) else pinnedPackages.add(pkg)
                saveLayoutPreferences()
                selectedAppForCustomization = null
            },
            onChangeIcon = {},
            iconPackManager = iconPackManager,
            onDismiss = { selectedAppForCustomization = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItemRow(
    app: ResolveInfo,
    iconPackManager: IconPackManager,
    context: Context,
    onLongClick: () -> Unit
) {
    val iconDrawable = iconPackManager.getAppIcon(app)
    val bitmap = iconDrawable?.toBitmap()?.asImageBitmap()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                    intent?.let { context.startActivity(it) }
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(32.dp).padding(end = 12.dp)
            )
        }
        Text(
            text = app.loadLabel(context.packageManager).toString(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun AppOptionsModal(
    app: ResolveInfo,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onChangeIcon: () -> Unit,
    iconPackManager: IconPackManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showIconPicker by remember { mutableStateOf(false) }

    if (showIconPicker) {
        IconPickerDialog(
            app = app,
            iconPackManager = iconPackManager,
            onDismiss = onDismiss,
            onIconSelected = { drawableName ->
                iconPackManager.setCustomIcon(app.activityInfo.packageName, drawableName)
                onDismiss()
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(app.loadLabel(context.packageManager).toString()) },
            text = {
                Column {
                    TextButton(
                        onClick = onTogglePin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPinned) "Unpin from Main Screen" else "Pin to Main Screen")
                    }
                    TextButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Icon (Lawnicons)")
                    }
                    TextButton(
                        onClick = {
                            MainActivity.openDefaultLauncherSettings(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as Default Launcher")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }
}

@Composable
fun CustomizableHeader(
    itemsOrder: SnapshotStateList<HeaderItem>,
    isEditMode: Boolean
) {
    val context = LocalContext.current

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            delay(1000)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = (level * 100 / scale.toFloat()).toInt()
                    }
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_PLUGGED_AC
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)
    ) {
        itemsOrder.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (item) {
                        HeaderItem.CLOCK -> {
                            Text(
                                text = currentTime,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        HeaderItem.DATE_WEATHER -> {
                            Text(
                                text = currentDate,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        HeaderItem.BATTERY -> {
                            Text(
                                text = "Battery: $batteryLevel%${if (isCharging) " (Charging)" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (isEditMode) {
                    Row {
                        if (index > 0) {
                            IconButton(onClick = {
                                val elem = itemsOrder.removeAt(index)
                                itemsOrder.add(index - 1, elem)
                            }) { Text("▲", fontSize = 12.sp) }
                        }
                        if (index < itemsOrder.size - 1) {
                            IconButton(onClick = {
                                val elem = itemsOrder.removeAt(index)
                                itemsOrder.add(index + 1, elem)
                            }) { Text("▼", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}
