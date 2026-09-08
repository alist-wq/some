package com.example.navigation.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.navigation.replay.RecordingSessionController
import com.example.navigation.replay.PedestrianSessionMetadata
import com.example.navigation.replay.PedestrianPoseLabel
import com.example.navigation.replay.PedestrianEventLabel
import com.example.navigation.replay.PedestrianRecordingValidator
import com.example.navigation.replay.GnssTrustSessionReportWriter
import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.maps.RegionDiscoveryResult
import com.example.navigation.maps.RegionalMapDownloadState
import com.example.navigation.maps.RegionalMapDownloadPhase
import com.example.navigation.maps.ProductionRegionalMapCatalog
import com.example.navigation.maps.ProductionRegionalMapRuntime
import com.example.navigation.maps.RegionalMapResolver
import com.example.navigation.maps.RegionalMapPackage
import com.example.navigation.routing.GeoPoint
import com.example.navigation.maps.DestinationSearchResult
import com.example.navigation.maps.OfflineDestinationSearchIndex
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.routing.ActiveRouteLifecycle
import com.example.navigation.routing.ActiveRouteState
import com.example.navigation.routing.ConfirmedDestination
import com.example.navigation.routing.ConfirmedRoutePlan
import com.example.navigation.routing.RoutePlanningUseCase
import com.example.navigation.routing.RoutePlanningState
import com.example.navigation.routing.SyntheticDestinationJourneyFixture
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions
import java.nio.file.Files
import java.nio.file.Path

data class PairedObdDevice(val name: String, val address: String)
data class NearbyObdDevice(val name: String, val address: String, val isPaired: Boolean)

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(AppScreen.MAIN)
    private var navigationService by mutableStateOf<NavigationForegroundService?>(null)
    private var serviceBound = false
    private var recording by mutableStateOf(false)
    private var message by mutableStateOf("Select a paired ELM327 adapter before recording")
    private var obdStatus by mutableStateOf("OBD not selected")
    private var pairedDevices by mutableStateOf<List<PairedObdDevice>>(emptyList())
    private var nearbyDevices by mutableStateOf<List<NearbyObdDevice>>(emptyList())
    private var obdDiscoveryStatus by mutableStateOf("Search for a nearby ELM327 adapter")
    private var selectedAddress by mutableStateOf<String?>(null)
    private var savedSessions by mutableStateOf<List<Path>>(emptyList())
    private var selectedSessionPaths by mutableStateOf<Set<Path>>(emptySet())
    private var navigationMapState by mutableStateOf(NavigationMapState())
    private var startAfterPermission = false
    private var discoverAfterPermission = false
    private var pendingPedestrianMetadata: PedestrianSessionMetadata? = null
    private var bluetoothReceiverRegistered = false
    private lateinit var recordingsDirectory: Path
    private lateinit var capabilityReport: SensorCapabilityReport
    private lateinit var capabilityFeatureFlags: CapabilityFeatureFlags
    private val serviceRefreshHandler = Handler(Looper.getMainLooper())
    private val serviceRefresh = object : Runnable {
        override fun run() {
            refreshFromService()
            serviceRefreshHandler.postDelayed(this, 500L)
        }
    }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refreshPairedDevices()
        if (discoverAfterPermission) {
            discoverAfterPermission = false
            if (hasLocationPermission() && hasBluetoothScanPermission()) discoverObdDevices()
            else obdDiscoveryStatus = "Nearby-device permission is required to scan"
        }
        if (startAfterPermission) {
            startAfterPermission = false
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == false || !hasLocationPermission()) {
                pendingPedestrianMetadata = null
                message = "Location permission is required"
            } else startNow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        RenavDiagnostics.event("activity_created")
        MapLibre.getInstance(applicationContext)
        capabilityReport = SensorCapabilityCollector.collect(this)
        capabilityFeatureFlags = CapabilityFeatureFlags.from(capabilityReport)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent.getBooleanExtra(EXTRA_SYNTHETIC_TRUST_SMOKE, false)) {
            val trust = SyntheticGnssTrustSmokeScenario.run()
            navigationMapState = navigationMapState.copy(
                estimatedPosition = trust.navigationPosition,
                gnssTrustState = trust.trustState,
                diagnostics = trust.toDiagnostics()
            )
        }
        recordingsDirectory = filesDir.toPath().resolve("recordings")
        refreshPairedDevices()
        refreshSavedSessions(selectAll = true)
        setContent {
            RenavLaunchGate {
                NavigationAppScreen(
                    screen,
                    ::navigate,
                    navigationService?.recordingController(), navigationMapState, recording, message, obdStatus, pairedDevices, nearbyDevices, obdDiscoveryStatus, selectedAddress,
                    savedSessions, selectedSessionPaths, ::selectObd, ::requestPermissionsOnly, ::requestStart,
                    ::stopRecording, { navigationService?.markEvent() }, ::toggleSessionSelection,
                    ::exportSelectedSessions, ::discoverObdDevices, ::pairOrSelectNearbyObd,
                    ::requestPedestrianStart, { navigationService?.labelPose(it) }, { navigationService?.labelPedestrianEvent(it) }
                )
            }
        }
    }

    private fun navigate(destination: AppScreen) {
        RenavDiagnostics.event("screen_navigation", "from=$screen to=$destination")
        screen = destination
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private fun requestPermissionsOnly() {
        RenavDiagnostics.event("permissions_requested")
        permissions.launch(requiredPermissions())
    }

    private fun requestStart() {
        if (!hasLocationPermission() || !hasBluetoothPermission()) {
            startAfterPermission = true
            permissions.launch(requiredPermissions())
        } else startNow()
    }

    private fun requestPedestrianStart(metadata: PedestrianSessionMetadata) {
        pendingPedestrianMetadata = metadata
        requestStart()
    }

    @SuppressLint("MissingPermission")
    private fun startNow() {
        try {
            RenavDiagnostics.event("recording_start_requested", "obdSelected=${selectedAddress != null}")
            NavigationForegroundService.start(this, selectedAddress, pendingPedestrianMetadata)
            pendingPedestrianMetadata = null
            bindNavigationService()
            recording = true
            message = "Starting foreground navigation recording"
        } catch (error: Throwable) {
            pendingPedestrianMetadata = null
            RenavDiagnostics.event("recording_start_error", error.stackTraceToString())
            recording = false
            message = "Unable to start recording: ${error.message}"
        }
    }

    private fun stopRecording() {
        try {
            RenavDiagnostics.event("recording_stop_requested")
            NavigationForegroundService.stop(this)
            recording = false
            message = "Stopping navigation recording"
            refreshSavedSessions(selectAll = true)
        } catch (error: Throwable) {
            RenavDiagnostics.event("recording_stop_error", error.stackTraceToString())
            message = "Unable to stop recording: ${error.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        pairedDevices = getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
            .map { PairedObdDevice(it.name ?: "Unnamed Bluetooth device", it.address) }
            .sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    private fun discoverObdDevices() {
        if (!hasLocationPermission() || !hasBluetoothScanPermission()) {
            discoverAfterPermission = true
            permissions.launch(requiredPermissions())
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null) {
            obdDiscoveryStatus = "Bluetooth is not available on this device"
            return
        }
        if (!adapter.isEnabled) {
            obdDiscoveryStatus = "Turn on Bluetooth to search for an OBD adapter"
            return
        }
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        nearbyDevices = emptyList()
        obdDiscoveryStatus = if (adapter.startDiscovery()) "Searching for nearby Bluetooth devices…" else "Unable to start Bluetooth discovery"
        RenavDiagnostics.event("obd_discovery_requested")
    }

    @SuppressLint("MissingPermission")
    private fun pairOrSelectNearbyObd(device: NearbyObdDevice) {
        if (device.isPaired) {
            selectObd(device.address)
            return
        }
        if (!hasBluetoothPermission()) {
            requestPermissionsOnly()
            return
        }
        val bluetoothDevice = getSystemService(BluetoothManager::class.java).adapter?.getRemoteDevice(device.address)
        if (bluetoothDevice == null) {
            obdDiscoveryStatus = "The selected device is no longer available"
            return
        }
        obdDiscoveryStatus = if (bluetoothDevice.createBond()) {
            "Pairing request sent for ${device.name}; confirm it in the Android dialog"
        } else {
            "Unable to start pairing with ${device.name}"
        }
        RenavDiagnostics.event("obd_pairing_requested", "address=${device.address}")
    }

    private fun selectObd(address: String) {
        RenavDiagnostics.event("obd_selected", "address=$address")
        selectedAddress = address
        obdStatus = "OBD selected; connection starts with recording"
    }
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private fun hasBluetoothScanPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun refreshSavedSessions(selectAll: Boolean = false) {
        val sessions = listRecordedSessions(recordingsDirectory)
        savedSessions = sessions
        selectedSessionPaths = when {
            selectAll -> sessions.toSet()
            else -> selectedSessionPaths.intersect(sessions.toSet()) + sessions.filterNot(selectedSessionPaths::contains)
        }
    }

    private fun toggleSessionSelection(path: Path) {
        selectedSessionPaths = if (path in selectedSessionPaths) selectedSessionPaths - path else selectedSessionPaths + path
    }

    private fun exportSelectedSessions() {
        val paths = savedSessions.filter { it in selectedSessionPaths }
        if (paths.isEmpty()) return
        val validator = PedestrianRecordingValidator()
        val reports = paths.map(validator::writeReport)
        val trustReports = paths.map(GnssTrustSessionReportWriter()::write)
        val rejected = paths.count { validator.validate(it).disposition.name == "REJECTED" }
        message = if (rejected == 0) "Acceptance reports created before export" else "$rejected recording(s) are incomplete; diagnostic export is still allowed"
        RenavDiagnostics.event("recordings_export_requested", "count=${paths.size}")
        val uris = ArrayList((paths + reports + trustReports).map { path ->
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", path.toFile())
        })
        val clip = ClipData.newUri(contentResolver, "Navigation recordings", uris.first())
        uris.drop(1).forEach { uri -> clip.addItem(ClipData.Item(uri)) }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/octet-stream"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export ${paths.size} recording${if (paths.size == 1) "" else "s"}"))
    }

    override fun onStart() {
        super.onStart()
        serviceRefreshHandler.removeCallbacks(serviceRefresh)
        serviceRefreshHandler.post(serviceRefresh)
        registerBluetoothReceiver()
        bindNavigationService()
    }

    override fun onStop() {
        serviceRefreshHandler.removeCallbacks(serviceRefresh)
        cancelBluetoothDiscovery()
        if (bluetoothReceiverRegistered) unregisterReceiver(bluetoothDiscoveryReceiver)
        bluetoothReceiverRegistered = false
        if (serviceBound) unbindService(serviceConnection)
        serviceBound = false
        navigationService = null
        super.onStop()
    }

    private fun bindNavigationService() {
        if (!serviceBound) serviceBound = bindService(Intent(this, NavigationForegroundService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothDiscoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothDiscoveryReceiver, filter)
        }
        bluetoothReceiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun cancelBluetoothDiscovery() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (hasBluetoothScanPermission() && adapter?.isDiscovering == true) adapter.cancelDiscovery()
    }

    private val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> obdDiscoveryStatus = "Searching for nearby Bluetooth devices…"
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    obdDiscoveryStatus = if (nearbyDevices.isEmpty()) "No nearby Bluetooth devices found" else "Search complete: ${nearbyDevices.size} device(s) found"
                }
                BluetoothDevice.ACTION_FOUND -> foundBluetoothDevice(intent.bluetoothDevice())
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> handleBondStateChange(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun foundBluetoothDevice(device: BluetoothDevice?) {
        device ?: return
        val candidate = NearbyObdDevice(device.name ?: "Unnamed Bluetooth device", device.address, device.bondState == BluetoothDevice.BOND_BONDED)
        nearbyDevices = mergeNearbyObdDevice(nearbyDevices, candidate)
    }

    @SuppressLint("MissingPermission")
    private fun handleBondStateChange(intent: Intent) {
        val device = intent.bluetoothDevice() ?: return
        if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR) == BluetoothDevice.BOND_BONDED) {
            refreshPairedDevices()
            nearbyDevices = nearbyDevices.map { if (it.address == device.address) it.copy(isPaired = true) else it }
            selectObd(device.address)
            obdDiscoveryStatus = "Paired and selected ${device.name ?: "OBD adapter"}"
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            navigationService = (binder as NavigationForegroundService.LocalBinder).service()
            refreshFromService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            navigationService = null
            recording = false
            refreshSavedSessions()
        }
    }

    private fun refreshFromService() {
        navigationService?.currentSnapshot()?.let { state ->
            recording = state.recording
            obdStatus = state.obdStatus
            message = state.message
            state.gnssTrust?.let { trust ->
                navigationMapState = navigationMapState.copy(
                    estimatedPosition = trust.navigationPosition,
                    gnssTrustState = trust.trustState,
                    diagnostics = trust.toDiagnostics(state.externalEvidence)
                )
            }
        }
    }

    private companion object {
        const val EXTRA_SYNTHETIC_TRUST_SMOKE = "syntheticTrustSmoke"
    }
}

@Composable
private fun RenavLaunchGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { context.motionAccessibilityPreference().reducedMotion }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var complete by remember { mutableStateOf(false) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            complete = true
            return@LaunchedEffect
        }
        val started = withFrameNanos { it }
        while (!complete) {
            val elapsed = (withFrameNanos { it } - started) / 1_000_000L
            elapsedMillis = elapsed
            if (elapsed >= RenavSplashMotion.handoffMillis) complete = true
        }
    }
    if (complete) {
        content()
    } else {
        val frame = RenavSplashMotion.frameAt(elapsedMillis)
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(com.example.navigation.app.R.drawable.renav_splash),
                contentDescription = null,
                modifier = Modifier.size(160.dp).graphicsLayer {
                    rotationZ = frame.rotationDegrees
                    alpha = frame.artworkAlpha
                }
            )
            Text("RENAV", fontFamily = FontFamily.SansSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = frame.nameAlpha })
        }
    }
}

enum class AppScreen { MAIN, SETTINGS, DEVELOPER, OFFLINE_MAPS, PEDESTRIAN_RECORDING }

@Composable
private fun NavigationAppScreen(
    screen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    controller: RecordingSessionController?,
    navigationMapState: NavigationMapState,
    recording: Boolean,
    message: String,
    obdStatus: String,
    pairedDevices: List<PairedObdDevice>,
    nearbyDevices: List<NearbyObdDevice>,
    obdDiscoveryStatus: String,
    selectedAddress: String?,
    savedSessions: List<Path>,
    selectedSessionPaths: Set<Path>,
    onSelectObd: (String) -> Unit,
    onPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    onToggleSession: (Path) -> Unit,
    onExport: () -> Unit,
    onDiscoverObd: () -> Unit,
    onPairOrSelectNearbyObd: (NearbyObdDevice) -> Unit,
    onStartPedestrian: (PedestrianSessionMetadata) -> Unit,
    onPose: (PedestrianPoseLabel) -> Unit,
    onPedestrianEvent: (PedestrianEventLabel) -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { context.motionAccessibilityPreference().reducedMotion }
    var regionalDiscovery by remember { mutableStateOf<RegionDiscoveryResult>(RegionDiscoveryResult.CatalogUnavailable) }
    var regionalDownload by remember { mutableStateOf(RegionalMapDownloadState()) }
    val mapRuntime = remember(context) { ProductionRegionalMapRuntime(context) { regionalDownload = it } }
    var activeRegionalPackage by remember { mutableStateOf(mapRuntime.activePackage()) }
    fun refreshRegionalDiscovery() {
        val location = navigationMapState.estimatedPosition?.let { GeoPoint(it.latitude, it.longitude) }
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        regionalDiscovery = RegionalMapResolver.resolve(ProductionRegionalMapCatalog.catalog, permission, location)
    }
    LaunchedEffect(navigationMapState.estimatedPosition) { refreshRegionalDiscovery() }
    BackHandler(enabled = screen != AppScreen.MAIN) { onNavigate(AppScreen.MAIN) }
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState == AppScreen.MAIN) {
                (fadeIn() + slideInHorizontally(initialOffsetX = { -it / 4 })) togetherWith
                    (fadeOut() + slideOutHorizontally(targetOffsetX = { it / 4 }))
            } else {
                (fadeIn() + slideInHorizontally(initialOffsetX = { it / 4 })) togetherWith
                    (fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 4 }))
            }
        },
        label = "app_screen_transition"
    ) { destination ->
    when (destination) {
        AppScreen.MAIN -> RecorderMapScreen(
            developerMode = false, onOpenSettings = { onNavigate(AppScreen.SETTINGS) }, onOpenDeveloper = { onNavigate(AppScreen.DEVELOPER) }, onOpenPedestrianRecording = { onNavigate(AppScreen.PEDESTRIAN_RECORDING) }, onOpenOfflineMaps = { refreshRegionalDiscovery(); onNavigate(AppScreen.OFFLINE_MAPS) },
            controller, navigationMapState, recording, message, obdStatus, pairedDevices, selectedAddress,
            savedSessions, selectedSessionPaths, onSelectObd, onPermissions, onStart, onStop, onMark, onToggleSession, onExport
        )
        AppScreen.SETTINGS -> SettingsScreen(
            obdStatus, pairedDevices, nearbyDevices, obdDiscoveryStatus, selectedAddress, onPermissions, onSelectObd,
            onDiscoverObd, onPairOrSelectNearbyObd, onOpenOfflineMaps = { refreshRegionalDiscovery(); onNavigate(AppScreen.OFFLINE_MAPS) }, onBack = { onNavigate(AppScreen.MAIN) }
        )
        AppScreen.DEVELOPER -> RecorderMapScreen(
            developerMode = true, onOpenSettings = { onNavigate(AppScreen.SETTINGS) }, onOpenDeveloper = {}, onOpenPedestrianRecording = { onNavigate(AppScreen.PEDESTRIAN_RECORDING) }, onOpenOfflineMaps = { refreshRegionalDiscovery(); onNavigate(AppScreen.OFFLINE_MAPS) },
            controller, navigationMapState, recording, message, obdStatus, pairedDevices, selectedAddress,
            savedSessions, selectedSessionPaths, onSelectObd, onPermissions, onStart, onStop, onMark, onToggleSession, onExport,
            onBack = { onNavigate(AppScreen.MAIN) }
        )
        AppScreen.PEDESTRIAN_RECORDING -> PedestrianFieldRecordingScreen(
            recording = recording,
            controller = controller,
            onStart = onStartPedestrian,
            onStop = onStop,
            onPose = onPose,
            onEvent = onPedestrianEvent,
            onExport = onExport,
            onBack = { onNavigate(AppScreen.DEVELOPER) }
        )
        AppScreen.OFFLINE_MAPS -> MapDownloadScreen(
            presentation = RegionalMapDownloadPresentationFactory.create(regionalDiscovery, regionalDownload),
            onRequestPermission = onPermissions,
            manualPackages = ProductionRegionalMapCatalog.catalog.packages,
            onManualRegion = { regionalDiscovery = RegionDiscoveryResult.LocationUnavailable },
            onSelectManualRegion = { regionalDiscovery = RegionDiscoveryResult.Found(it) },
            onDownloadAction = { action ->
                val packageInfo = (regionalDiscovery as? RegionDiscoveryResult.Found)?.packageInfo
                when (action) {
                    RegionalMapPrimaryAction.CONFIRM_DOWNLOAD -> packageInfo?.let { mapRuntime.orchestrator.prepare(it); mapRuntime.orchestrator.start() }
                    RegionalMapPrimaryAction.PAUSE -> mapRuntime.orchestrator.pause()
                    RegionalMapPrimaryAction.RESUME, RegionalMapPrimaryAction.RETRY -> mapRuntime.orchestrator.retry()
                    RegionalMapPrimaryAction.CANCEL -> mapRuntime.orchestrator.cancel()
                    RegionalMapPrimaryAction.MANAGE_DOWNLOADED_MAPS -> Unit
                    else -> Unit
                }
                activeRegionalPackage = mapRuntime.activePackage()
                RenavDiagnostics.event("regional_map_ui_action", "action=$action")
            },
            onBack = { onNavigate(AppScreen.MAIN) }
        )
    }
    }
}

@Composable
private fun PedestrianFieldRecordingScreen(
    recording: Boolean,
    controller: RecordingSessionController?,
    onStart: (PedestrianSessionMetadata) -> Unit,
    onStop: () -> Unit,
    onPose: (PedestrianPoseLabel) -> Unit,
    onEvent: (PedestrianEventLabel) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    var setup by remember { mutableStateOf(PedestrianRecordingSetupState()) }
    var presentation by remember { mutableStateOf(PedestrianRecordingPresentation(recording, setup.initialPose)) }
    var durationTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { while (recording) { delay(1_000); durationTick++ } }
    val availability = controller?.availability()
    val duration = (controller?.status()?.elapsedNanos?.div(1_000_000_000L) ?: 0L) + durationTick * 0L
    BackHandler(enabled = recording) { presentation = presentation.copy(confirmation = "Stop and save before leaving this recording") }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PEDESTRIAN FIELD RECORDING", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Mode: PEDESTRIAN · OBD is not required")
        if (!recording) {
            Text("INITIAL PHONE POSE")
            PedestrianPoseLabel.entries.forEach { pose -> Button(onClick = { setup = setup.copy(initialPose = pose) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Select initial pose ${pose.name}" }) { Text(if (setup.initialPose == pose) "SELECTED: ${pose.name}" else pose.name) } }
            TextField(setup.routeReference, { setup = setup.copy(routeReference = it) }, label = { Text("Route identifier or geometry reference (required)") }, modifier = Modifier.fillMaxWidth())
            TextField(setup.intendedStart, { setup = setup.copy(intendedStart = it) }, label = { Text("Intended start (optional)") }, modifier = Modifier.fillMaxWidth())
            TextField(setup.intendedDestination, { setup = setup.copy(intendedDestination = it) }, label = { Text("Intended destination (optional)") }, modifier = Modifier.fillMaxWidth())
            TextField(setup.notes, { setup = setup.copy(notes = it) }, label = { Text("Carry notes / known anomalies (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(setup.consentConfirmed, { setup = setup.copy(consentConfirmed = it) }); Text("I consent to local recording and confirm anonymization status") }
            Button(enabled = setup.canStart, onClick = {
                presentation = PedestrianRecordingPresentation(recording = true, currentPose = setup.initialPose)
                onStart(PedestrianSessionMetadata(NavigationMode.PEDESTRIAN, setup.initialPose,
                    setup.intendedStart.ifBlank { null }, setup.intendedDestination.ifBlank { null },
                    routeIdentifier = setup.routeReference, deviceModel = Build.MODEL,
                    carryConditionNotes = setup.notes.ifBlank { null }, consentConfirmed = true,
                    anonymized = setup.anonymized, wallClockStartedEpochMillis = System.currentTimeMillis()))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "Start pedestrian recording without OBD" }) { Text("START PEDESTRIAN SESSION") }
        } else {
            Text("Recording: ${duration}s · Current pose: ${presentation.currentPose}")
            Text("Sensors — GNSS ${availability?.gnss == true}, gyro ${availability?.gyroscope == true}, accelerometer ${availability?.accelerometer == true}, rotation ${availability?.gameRotationVector == true}")
            if (availability != null && (!availability.gnss || !availability.gyroscope || !availability.accelerometer || !availability.gameRotationVector)) Text("Required sensor stream unavailable; this session may be rejected", color = Color(0xFFB71C1C))
            Text("PHONE POSE")
            PedestrianPoseLabel.entries.forEach { pose -> Button(onClick = { onPose(pose); presentation = PedestrianRecordingReducer.pose(presentation, pose) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Record pose ${pose.name}" }) { Text(pose.name) } }
            Text("EVENT")
            PedestrianEventLabel.entries.forEach { event -> Button(onClick = { onEvent(event); presentation = PedestrianRecordingReducer.event(presentation, event) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Record event ${event.name}" }) { Text(event.name) } }
            presentation.confirmation?.let { Text(it, color = Color(0xFF2E7D32)) }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("STOP AND SAVE") }
        }
        Button(enabled = !recording, onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("EXPORT SELECTED SESSIONS") }
        Button(enabled = !recording, onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("BACK") }
    }
}

@Composable
private fun MapDownloadScreen(
    presentation: RegionalMapDownloadPresentation,
    manualPackages: List<RegionalMapPackage>,
    onRequestPermission: () -> Unit,
    onManualRegion: () -> Unit,
    onSelectManualRegion: (RegionalMapPackage) -> Unit,
    onDownloadAction: (RegionalMapPrimaryAction) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { context.motionAccessibilityPreference().reducedMotion }
    val runAction: (RegionalMapPrimaryAction) -> Unit = { action ->
        when (action) {
            RegionalMapPrimaryAction.REQUEST_LOCATION_PERMISSION -> onRequestPermission()
            RegionalMapPrimaryAction.SELECT_REGION_MANUALLY -> onManualRegion()
            RegionalMapPrimaryAction.NONE -> Unit
            else -> onDownloadAction(action)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
            .semantics { contentDescription = "Offline maps: ${presentation.title}; ${presentation.detail}" },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedContent(
            targetState = presentation.title,
            transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn() togetherWith fadeOut() },
            label = "regional_map_title"
        ) { Text(it, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
        Text(presentation.detail)
        if (presentation.primaryAction == RegionalMapPrimaryAction.SELECT_REGION_MANUALLY ||
            presentation.secondaryAction == RegionalMapPrimaryAction.SELECT_REGION_MANUALLY) {
            manualPackages.forEach { packageInfo ->
                Button(onClick = { onSelectManualRegion(packageInfo) }, modifier = Modifier.semantics { contentDescription = "Select ${packageInfo.displayName} offline map" }) {
                    Text(packageInfo.displayName)
                }
            }
        }
        presentation.packageInfo?.let { packageInfo ->
            Text("REGION: ${packageInfo.displayName}")
            Text("VERSION: ${packageInfo.mapVersion}")
            Text("SIZE ESTIMATE: ${"%.1f".format(java.util.Locale.ROOT, packageInfo.estimatedSizeBytes / 1_000_000.0)} MB")
        }
        Text("OFFLINE STATUS: ${presentation.availabilityLabel}", fontWeight = FontWeight.Bold)
        presentation.progressLabel?.let { Text(it) }
        if (presentation.primaryAction != RegionalMapPrimaryAction.NONE) {
            Button(
                onClick = { runAction(presentation.primaryAction) },
                modifier = Modifier.semantics { contentDescription = "${actionLabel(presentation.primaryAction)} for offline map" }
            ) { Text(actionLabel(presentation.primaryAction)) }
        }
        if (presentation.secondaryAction != RegionalMapPrimaryAction.NONE) {
            Button(
                onClick = { runAction(presentation.secondaryAction) },
                modifier = Modifier.semantics { contentDescription = "${actionLabel(presentation.secondaryAction)} for offline map" }
            ) { Text(actionLabel(presentation.secondaryAction)) }
        }
        if (presentation.ready) Text("OFFLINE NAVIGATION READY", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
        Button(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to navigation" }) { Text("BACK TO NAVIGATION") }
    }
}

private fun actionLabel(action: RegionalMapPrimaryAction): String = when (action) {
    RegionalMapPrimaryAction.REQUEST_LOCATION_PERMISSION -> "ALLOW LOCATION"
    RegionalMapPrimaryAction.SELECT_REGION_MANUALLY -> "CHOOSE REGION MANUALLY"
    RegionalMapPrimaryAction.CONFIRM_DOWNLOAD -> "DOWNLOAD MAP"
    RegionalMapPrimaryAction.PAUSE -> "PAUSE DOWNLOAD"
    RegionalMapPrimaryAction.RESUME -> "RESUME DOWNLOAD"
    RegionalMapPrimaryAction.CANCEL -> "CANCEL DOWNLOAD"
    RegionalMapPrimaryAction.RETRY -> "RETRY DOWNLOAD"
    RegionalMapPrimaryAction.MANAGE_DOWNLOADED_MAPS -> "MANAGE DOWNLOADED MAPS"
    RegionalMapPrimaryAction.NONE -> ""
}

@Composable
private fun SettingsScreen(
    obdStatus: String,
    pairedDevices: List<PairedObdDevice>,
    nearbyDevices: List<NearbyObdDevice>,
    obdDiscoveryStatus: String,
    selectedAddress: String?,
    onPermissions: () -> Unit,
    onSelectObd: (String) -> Unit,
    onDiscoverObd: () -> Unit,
    onPairOrSelectNearbyObd: (NearbyObdDevice) -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS")
        Text("Permissions and vehicle connection")
        Button(onClick = onPermissions) { Text("GRANT / REFRESH PERMISSIONS") }
        Button(onClick = onOpenOfflineMaps) { Text("OFFLINE MAPS") }
        Text(obdStatus)
        Text("OBD ADAPTER")
        pairedDevices.forEach { device ->
            Button(onClick = { onSelectObd(device.address) }) {
                Text(if (device.address == selectedAddress) "SELECTED: ${device.name}" else "USE OBD: ${device.name}")
            }
        }
        if (pairedDevices.isEmpty()) Text("No paired Bluetooth devices listed.")
        Text("ADD OR DISCOVER OBD ADAPTER")
        Button(onClick = onDiscoverObd) { Text("SEARCH NEARBY DEVICES") }
        Text(obdDiscoveryStatus)
        nearbyDevices.forEach { device ->
            Button(onClick = { onPairOrSelectNearbyObd(device) }) {
                Text("${if (device.isPaired) "USE" else "PAIR"}: ${device.name}")
            }
        }
        Button(onClick = onBack) { Text("BACK TO NAVIGATION") }
    }
}

@Composable
private fun RecorderMapScreen(
    developerMode: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenPedestrianRecording: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    controller: RecordingSessionController?,
    navigationMapState: NavigationMapState,
    recording: Boolean,
    message: String,
    obdStatus: String,
    pairedDevices: List<PairedObdDevice>,
    selectedAddress: String?,
    savedSessions: List<Path>,
    selectedSessionPaths: Set<Path>,
    onSelectObd: (String) -> Unit,
    onPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    onToggleSession: (Path) -> Unit,
    onExport: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { context.motionAccessibilityPreference().reducedMotion }
    var tick by remember { mutableLongStateOf(0L) }
    var activeStyle by remember { mutableStateOf<Style?>(null) }
    var mapLoadState by remember { mutableStateOf(MapLoadState.LOADING) }
    var mapRetryCount by remember { mutableIntStateOf(0) }
    val mapLoading = mapLoadState == MapLoadState.LOADING
    var displayedPosition by remember { mutableStateOf(navigationMapState.estimatedPosition) }
    var displayedHeading by remember { mutableStateOf(navigationMapState.movementHeadingDegrees) }
    var cameraCentered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = menuOpen) {
        menuOpen = false
        RenavDiagnostics.event("navigation_menu_toggled", "open=false source=back")
    }
    val manualOriginTracker = remember { ManualOriginTracker() }
    var manualTrackingState by remember { mutableStateOf(ManualOriginTrackingState()) }
    // The synthetic package exists only in debug builds so the full destination journey can be
    // exercised before a real regional package is available. It is never a production fallback.
    val syntheticJourneyFixture = remember {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            SyntheticDestinationJourneyFixture()
        } else {
            null
        }
    }
    val destinationIndex: OfflineDestinationSearchIndex? = syntheticJourneyFixture?.index
    val routePlanner = remember { RoutePlanningUseCase() }
    val activeRouteLifecycle = remember { ActiveRouteLifecycle() }
    var syntheticObservationIndex by remember { mutableIntStateOf(0) }
    var destinationSearch by remember { mutableStateOf(DestinationSearchSelectionState()) }
    var routePlanningState by remember { mutableStateOf(RoutePlanningState()) }
    var activeRouteState by remember { mutableStateOf(ActiveRouteState()) }
    LaunchedEffect(recording) { while (recording) { delay(1_000); tick++ } }
    LaunchedEffect(navigationMapState.estimatedPosition, navigationMapState.movementHeadingDegrees, reducedMotion) {
        val targetPosition = navigationMapState.estimatedPosition
        val targetHeading = navigationMapState.movementHeadingDegrees
        val canAnimate = !reducedMotion && targetPosition != null &&
            targetPosition.confidence !in setOf(PositionConfidence.LOW, PositionConfidence.LOST) && displayedPosition != null
        if (!canAnimate) {
            displayedPosition = targetPosition
            displayedHeading = targetHeading
            return@LaunchedEffect
        }
        val fromPosition = displayedPosition ?: return@LaunchedEffect
        val fromHeading = displayedHeading
        val startedAt = withFrameNanos { it }
        var elapsedMillis: Long
        do {
            elapsedMillis = (withFrameNanos { it } - startedAt) / 1_000_000L
            val fraction = (elapsedMillis.toFloat() / NavigationMotionPerformance.mapInterpolationDurationMillis).coerceIn(0f, 1f)
            val point = MapMotionInterpolator.position(
                com.example.navigation.routing.GeoPoint(fromPosition.latitude, fromPosition.longitude),
                com.example.navigation.routing.GeoPoint(targetPosition.latitude, targetPosition.longitude),
                fraction
            )
            displayedPosition = targetPosition.copy(latitude = point.latitude, longitude = point.longitude)
            displayedHeading = if (fromHeading != null && targetHeading != null) {
                MapMotionInterpolator.heading(fromHeading, targetHeading, fraction)
            } else targetHeading
        } while (elapsedMillis < NavigationMotionPerformance.mapInterpolationDurationMillis)
    }
    // Texture mode keeps this Android view in the normal Compose z-order so map controls receive touches.
    val mapView = remember { MapView(context, MapLibreMapOptions().textureMode(true)) }
    fun loadMapStyle() {
        mapLoadState = MapLoadState.LOADING
        activeStyle = null
        mapView.getMapAsync { map ->
            map.setStyle(ProductionRegionalMapCatalog.OPEN_FREE_MAP_STYLE) { style ->
                RenavDiagnostics.event("map_style_loaded")
                activeStyle = style
                mapLoadState = MapLoadState.READY
            }
        }
    }
    DisposableEffect(mapView) {
        RenavDiagnostics.event("map_initialization_started")
        mapView.onStart()
        loadMapStyle()
        onDispose { mapView.onStop(); mapView.onDestroy() }
    }
    LaunchedEffect(mapRetryCount) { if (mapRetryCount > 0) loadMapStyle() }
    LaunchedEffect(mapLoadState) {
        if (mapLoadState == MapLoadState.LOADING) {
            delay(10_000)
            if (mapLoadState == MapLoadState.LOADING) mapLoadState = MapLoadState.UNAVAILABLE
        }
    }
    val activeCandidate = activeRouteState.confirmedPlan?.candidate
    val displayedMapState = navigationMapState.copy(
        estimatedPosition = displayedPosition,
        movementHeadingDegrees = displayedHeading,
        route = activeCandidate?.route ?: navigationMapState.route,
        destination = activeCandidate?.destination?.point
            ?: destinationSearch.proposedDestination?.coordinate
            ?: navigationMapState.destination
    )
    val routePreview = RoutePreviewPresentationFactory.create(routePlanningState)
    val activeRouteGuidance = ActiveRouteGuidancePresentationFactory.create(activeRouteState)
    LaunchedEffect(activeStyle, displayedPosition, displayedHeading, displayedMapState.route, displayedMapState.destination, routePreview.routeGeometry, navigationMapState.gnssTrustState, manualTrackingState.origin) {
        activeStyle?.let { style ->
            MapLibreNavigationRenderer.render(style, displayedMapState)
            MapLibreNavigationRenderer.renderCandidateRoute(style, routePreview.routeGeometry)
            MapLibreNavigationRenderer.renderManualOrigin(style, manualTrackingState.origin?.point)
        }
    }
    LaunchedEffect(navigationMapState.estimatedPosition, manualTrackingState.active) {
        val estimate = navigationMapState.estimatedPosition
        val current = manualTrackingState.position
        if (manualTrackingState.active && estimate != null && estimate.source != com.example.navigation.navigation.PositionSource.MANUAL_START &&
            current != null && estimate.timestamp > current.timestamp) {
            manualTrackingState = manualOriginTracker.observe(ManualTrackingObservation.Position(estimate))
        }
    }
    LaunchedEffect(navigationMapState.estimatedPosition) {
        val position = navigationMapState.estimatedPosition
        if (position == null) {
            cameraCentered = false
        } else if (!cameraCentered) {
            mapView.getMapAsync { map ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), 16.0))
            }
            cameraCentered = true
        }
    }
    val status = controller?.status()
    val navigationStatus = NavigationStatusPresentationFactory.create(
        navigationMapState.estimatedPosition,
        navigationMapState.gnssTrustState
    )
    val guidance = NavigationGuidancePresentationFactory.create(
        routeAvailable = navigationMapState.route != null,
        state = navigationMapState.guidance
    )
    val displayedRouteProgress by animateFloatAsState(
        targetValue = guidance.routeProgress ?: 0f,
        animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.spring(),
        label = "route_progress"
    )
    val usedBytes = status?.outputPath?.takeIf(Files::exists)?.let(Files::size) ?: 0L
    val trustColor by animateColorAsState(
        targetValue = when (navigationStatus.trustTone) {
            TrustVisualTone.NORMAL -> Color(0xFF2E7D32)
            TrustVisualTone.CAUTION -> Color(0xFFF57F17)
            TrustVisualTone.DANGER -> Color(0xFFB71C1C)
            TrustVisualTone.RECOVERY -> Color(0xFF1565C0)
            TrustVisualTone.UNAVAILABLE -> Color(0xFF616161)
        },
        animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.spring(),
        label = "gnss_trust_color"
    )
    val uncertaintySize by animateDpAsState(
        targetValue = navigationMapState.estimatedPosition?.estimatedAccuracyMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { (it * 1.5).coerceIn(32.0, 180.0).dp } ?: 32.dp,
        animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.spring(),
        label = "position_uncertainty_size"
    )
    var showDiagnostics by remember { mutableStateOf(false) }
    var manualStartSelection by remember { mutableStateOf(ManualStartSelectionState()) }
    var markerSaved by remember { mutableStateOf(false) }
    var exportPreparing by remember { mutableStateOf(false) }
    LaunchedEffect(markerSaved) {
        if (markerSaved) {
            delay(1_800)
            markerSaved = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Button(
            onClick = {
                menuOpen = true
                RenavDiagnostics.event("navigation_menu_toggled", "open=true source=compose_button")
            },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).sizeIn(minWidth = 56.dp, minHeight = 56.dp).zIndex(100f)
                .semantics { contentDescription = "Open navigation menu" }
        ) { Text("☰", fontSize = 24.sp) }
        if (developerMode) Button(
            onClick = onOpenPedestrianRecording,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).heightIn(min = 56.dp)
                .semantics { contentDescription = "Open pedestrian field recording setup" }
        ) { Text("PEDESTRIAN FIELD RECORDING") }
        if (navigationMapState.estimatedPosition?.confidence in setOf(PositionConfidence.LOW, PositionConfidence.LOST)) {
            Box(Modifier.align(Alignment.Center).size(uncertaintySize)
                .background(Color(0x331565C0), CircleShape))
        } else if (navigationMapState.estimatedPosition != null) {
            Box(Modifier.align(Alignment.Center).size(18.dp).background(Color(0xFF1565C0), CircleShape))
        }
        AnimatedVisibility(
            visible = manualStartSelection.phase in setOf(ManualStartSelectionPhase.SELECTING, ManualStartSelectionPhase.SELECTED),
            modifier = Modifier.align(Alignment.Center),
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut()
        ) {
            Text("＋", color = Color(0xFF8E24AA), fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Manual start-point selection target" })
        }
        AnimatedVisibility(
            visible = manualStartSelection.phase != ManualStartSelectionPhase.IDLE,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.96f)).padding(16.dp)
                    .semantics { contentDescription = "Manual start-point selection" },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (manualStartSelection.phase == ManualStartSelectionPhase.CONFIRMED) "START POINT CONFIRMED" else "SELECT START POINT",
                    fontWeight = FontWeight.Bold
                )
                if (manualStartSelection.phase != ManualStartSelectionPhase.CONFIRMED) {
                    Text("Pan and zoom the map, then select the map centre.")
                }
                manualStartSelection.selectedStart?.let { start ->
                    Text("MANUAL POINT: ${"%.5f".format(java.util.Locale.US, start.point.latitude)}, ${"%.5f".format(java.util.Locale.US, start.point.longitude)}")
                    Text("Source: manually selected — not a live measured position", color = Color(0xFF6A1B9A))
                }
                if (manualStartSelection.phase != ManualStartSelectionPhase.CONFIRMED) {
                    Button(onClick = {
                        mapView.getMapAsync { map ->
                            map.cameraPosition.target?.let { target ->
                                manualStartSelection = ManualStartSelectionReducer.select(
                                    if (manualStartSelection.phase == ManualStartSelectionPhase.SELECTED) manualStartSelection
                                    else ManualStartSelectionReducer.enter(manualStartSelection),
                                    com.example.navigation.routing.GeoPoint(target.latitude, target.longitude)
                                )
                                RenavDiagnostics.event("manual_start_point_selected")
                            }
                        }
                    }, modifier = Modifier.semantics { contentDescription = "Select map centre as start point" }) { Text("SELECT MAP CENTRE") }
                    Button(
                        enabled = manualStartSelection.phase == ManualStartSelectionPhase.SELECTED,
                        onClick = {
                            manualStartSelection = ManualStartSelectionReducer.confirm(manualStartSelection)
                            manualTrackingState = manualOriginTracker.start(
                                manualStartSelection, MonotonicTimestamp(android.os.SystemClock.elapsedRealtimeNanos())
                            )
                            manualStartSelection = ManualStartSelectionReducer.cancel()
                            RenavDiagnostics.event("manual_start_point_confirmed")
                        },
                        modifier = Modifier.semantics { contentDescription = "Confirm manual start point" }
                    ) { Text("CONFIRM START POINT") }
                }
                if (manualStartSelection.phase in setOf(ManualStartSelectionPhase.SELECTED, ManualStartSelectionPhase.CONFIRMED)) {
                    Button(onClick = { manualStartSelection = ManualStartSelectionReducer.reselect(manualStartSelection) }) { Text("RESELECT") }
                }
                Button(onClick = {
                    manualStartSelection = ManualStartSelectionReducer.cancel()
                    RenavDiagnostics.event("manual_start_point_cancelled")
                }, modifier = Modifier.semantics { contentDescription = "Cancel manual start-point selection" }) { Text("CANCEL") }
            }
        }
        AnimatedVisibility(
            visible = destinationSearch.phase != DestinationSearchPhase.IDLE,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
        ) {
            DestinationSearchPanel(
                state = destinationSearch,
                onQuery = { query -> destinationSearch = DestinationSearchSelectionReducer.query(destinationSearch, destinationIndex, query) },
                onSelect = { record ->
                    destinationSearch = DestinationSearchSelectionReducer.select(destinationSearch, record)
                    val proposedDestination = destinationSearch.proposedDestination
                    routePlanningState = if (syntheticJourneyFixture != null && proposedDestination != null) {
                        routePlanner.plan(
                            origin = syntheticJourneyFixture.origin,
                            destination = ConfirmedDestination(
                                proposedDestination.stableId,
                                proposedDestination.label,
                                proposedDestination.coordinate,
                                proposedDestination.sourcePackage
                            ),
                            mode = NavigationMode.VEHICLE,
                            verifiedGraph = syntheticJourneyFixture.verifiedGraph,
                            activeRoute = activeRouteState.confirmedPlan?.candidate?.route
                        )
                    } else {
                        RoutePlanningState(
                            phase = com.example.navigation.routing.RoutePlanningPhase.GRAPH_UNAVAILABLE,
                            reason = "A compatible regional routing graph is required before route preview."
                        )
                    }
                    RenavDiagnostics.event("destination_proposed", "id=${record.stableId}")
                },
                onReplace = { destinationSearch = DestinationSearchSelectionReducer.open(destinationSearch) },
                onCancel = {
                    destinationSearch = DestinationSearchSelectionReducer.cancel(destinationSearch)
                    RenavDiagnostics.event("destination_selection_cancelled")
                }
            )
        }
        AnimatedVisibility(
            visible = routePreview.visible,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
        ) {
            RoutePreviewPanel(
                presentation = routePreview,
                onStart = {
                    routePlanningState.candidate?.let { candidate ->
                        if (activeRouteLifecycle.state.phase != com.example.navigation.routing.ActiveRoutePhase.STOPPED) {
                            activeRouteLifecycle.stop()
                        }
                        activeRouteState = activeRouteLifecycle.start(
                            ConfirmedRoutePlan("debug-${candidate.destination.stableId}", candidate)
                        )
                        syntheticObservationIndex = 0
                        routePlanningState = RoutePlanningState()
                        RenavDiagnostics.event("route_start_confirmed")
                    }
                },
                onCancel = { routePlanningState = RoutePlanningState(); RenavDiagnostics.event("route_preview_cancelled") },
                onChangeDestination = {
                    routePlanningState = RoutePlanningState()
                    destinationSearch = DestinationSearchSelectionReducer.open(destinationSearch)
                    RenavDiagnostics.event("route_destination_change_requested")
                }
            )
        }
        AnimatedVisibility(
            visible = activeRouteGuidance.visible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut()
        ) {
            ActiveRouteGuidancePanel(
                presentation = activeRouteGuidance,
                showSyntheticAdvance = syntheticJourneyFixture != null &&
                    activeRouteState.phase !in setOf(
                        com.example.navigation.routing.ActiveRoutePhase.ARRIVED,
                        com.example.navigation.routing.ActiveRoutePhase.STOPPED
                    ),
                onAdvanceSynthetic = {
                    syntheticJourneyFixture?.observations()?.getOrNull(syntheticObservationIndex)?.let { observation ->
                        activeRouteState = activeRouteLifecycle.observe(observation)
                        syntheticObservationIndex++
                        RenavDiagnostics.event("synthetic_route_advanced", "step=$syntheticObservationIndex phase=${activeRouteState.phase}")
                    }
                },
                onStop = {
                    activeRouteState = activeRouteLifecycle.stop()
                    RenavDiagnostics.event("active_route_stop_requested")
                },
                onNewDestination = {
                    activeRouteState = activeRouteLifecycle.stop()
                    destinationSearch = DestinationSearchSelectionReducer.open(destinationSearch)
                    RenavDiagnostics.event("active_route_new_destination_requested")
                }
            )
        }
        val manualTrackingPresentation = ManualOriginTrackingPresentationFactory.create(
            manualTrackingState, navigationMapState.gnssTrustState
        )
        AnimatedVisibility(
            visible = manualTrackingPresentation.visible && manualStartSelection.phase == ManualStartSelectionPhase.IDLE,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
        ) {
            Column(
                Modifier.widthIn(max = 320.dp).background(Color.White.copy(alpha = 0.96f)).padding(14.dp)
                    .semantics { contentDescription = "Manual-origin tracking: ${manualTrackingPresentation.statusLabel}; ${manualTrackingPresentation.detail}" },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(manualTrackingPresentation.statusLabel, color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
                Text(manualTrackingPresentation.detail)
                Text("POSITION SOURCE: ${manualTrackingPresentation.sourceLabel}")
                Text("CONFIDENCE: ${manualTrackingPresentation.confidenceLabel}")
                Text("ESTIMATED ACCURACY: ${manualTrackingPresentation.accuracyLabel}")
                manualTrackingPresentation.warning?.let { Text(it, color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold) }
                Button(onClick = {
                    manualTrackingState = manualOriginTracker.stop()
                    RenavDiagnostics.event("manual_origin_tracking_stopped")
                }, enabled = manualTrackingState.active, modifier = Modifier.semantics { contentDescription = "Stop manual-origin tracking" }) { Text("STOP TRACKING") }
                Button(onClick = {
                    manualTrackingState = manualOriginTracker.stop()
                    manualStartSelection = ManualStartSelectionReducer.enter(ManualStartSelectionState())
                    RenavDiagnostics.event("manual_origin_tracking_start_over")
                }, modifier = Modifier.semantics { contentDescription = "Start tracking from a new manual origin" }) { Text("START OVER") }
            }
        }
        AnimatedVisibility(
            visible = guidance.visible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            enter = if (reducedMotion) EnterTransition.None else slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.94f)).padding(16.dp)
                    .semantics { contentDescription = "Navigation guidance: ${guidance.stateLabel}" },
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AnimatedContent(
                    targetState = guidance.stateLabel,
                    transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn() togetherWith fadeOut() },
                    label = "guidance_state"
                ) { stateLabel -> Text(stateLabel, color = Color(0xFF123047), fontWeight = FontWeight.Bold) }
                guidance.maneuverLabel?.let { maneuver ->
                    Text(maneuver, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                guidance.distanceLabel?.let { distance ->
                    AnimatedContent(
                        targetState = distance,
                        transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn() togetherWith fadeOut() },
                        label = "turn_distance"
                    ) { distanceLabel -> Text("In $distanceLabel", fontWeight = FontWeight.Bold) }
                }
                guidance.turnPreview?.let { Text(it, color = Color(0xFF415A69)) }
                guidance.routeProgress?.let {
                    LinearProgressIndicator(progress = { displayedRouteProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        AnimatedVisibility(
            visible = mapLoadState != MapLoadState.READY,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut()
        ) {
            Text(mapLoadFeedback(mapLoadState).label, color = if (mapLoading) Color(0xFF123047) else Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.94f)).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        AnimatedVisibility(
            visible = developerMode && recording,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
        ) {
            Text("● RECORDING", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier.align(Alignment.TopStart).zIndex(99f),
            enter = if (reducedMotion) EnterTransition.None else slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = 390.dp).padding(12.dp)
                .background(Color.White.copy(alpha = 0.94f)).padding(12.dp)
                .animateContentSize(animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.spring())
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (developerMode) "DEVELOPER MODE" else "OFFLINE NAVIGATION")
                Button(
                    onClick = {
                        menuOpen = false
                        RenavDiagnostics.event("navigation_menu_toggled", "open=false source=menu_close")
                    },
                    modifier = Modifier.semantics { contentDescription = "Close navigation menu" }
                ) { Text("CLOSE") }
            }
            if (developerMode) {
                Text(if (recording) "RECORDING" else "READY")
                Text("Duration: ${(status?.elapsedNanos ?: 0L) / 1_000_000_000} s | session: ${usedBytes / 1_000} KB")
                Text(message)
                Text("Required sensors: GNSS, raw GNSS, gyro, accelerometer, rotation vector, OBD speed")
                Text(obdStatus)
            }
            if (message != "Select a paired ELM327 adapter before recording") Text(message)
            if (mapLoadState != MapLoadState.READY) {
                Text(if (mapLoading) "Map is loading. Navigation status remains available while the map initializes." else "Map could not be loaded. Navigation status remains available.")
                Button(onClick = {
                    mapRetryCount++
                    RenavDiagnostics.event("map_retry_requested", "attempt=$mapRetryCount")
                }) { Text(mapLoadFeedback(mapLoadState).actionLabel ?: "RETRY MAP") }
            }
            Text("POSITION SOURCE")
            Text(navigationStatus.sourceLabel)
            AnimatedContent(
                targetState = navigationStatus.confidenceLabel,
                transitionSpec = { if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn() togetherWith fadeOut() },
                label = "gnss_trust_state"
            ) { label ->
                Text(
                    "${navigationStatus.trustIcon} $label",
                    color = trustColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "GNSS trust state: $label" }
                )
            }
            Text("ESTIMATED ACCURACY: ${navigationStatus.accuracyLabel}")
            navigationStatus.warning?.let { warning -> Text(warning, color = Color(0xFFB71C1C)) }
            Button(onClick = { showDiagnostics = !showDiagnostics }) {
                Text(if (showDiagnostics) "HIDE DEVELOPER DIAGNOSTICS" else "SHOW DEVELOPER DIAGNOSTICS")
            }
            if (showDiagnostics) NavigationDiagnosticsPanel(
                navigationMapState.diagnostics.copy(
                    gnssTrustState = navigationMapState.diagnostics.gnssTrustState ?: navigationMapState.gnssTrustState,
                    positionUncertaintyMeters = navigationMapState.diagnostics.positionUncertaintyMeters
                        ?: navigationMapState.estimatedPosition?.estimatedAccuracyMeters
                )
            )
            if (developerMode) {
                Button(onClick = onOpenSettings) { Text("OPEN SETTINGS") }
                Button(onClick = if (recording) onStop else onStart) {
                    Text(if (recording) "STOP SESSION" else "START SESSION")
                }
                Button(enabled = recording, onClick = {
                    onMark()
                    markerSaved = true
                    RenavDiagnostics.event("recording_marker_saved")
                }) { Text("MARK EVENT") }
                AnimatedVisibility(
                    visible = markerSaved,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut()
                ) {
                    Text("MARKER SAVED", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
                Text("Saved recordings: ${savedSessions.size}")
                savedSessions.forEach { path ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = path in selectedSessionPaths, enabled = !recording, onCheckedChange = {
                            onToggleSession(path)
                            RenavDiagnostics.event("recording_selection_changed", "selected=${!it}")
                        })
                        Text(path.fileName.toString())
                    }
                }
                Text("Selected sessions: ${selectedSessionPaths.size}")
                Button(enabled = !recording && selectedSessionPaths.isNotEmpty(), onClick = {
                    exportPreparing = true
                    RenavDiagnostics.event("recordings_export_preparing", "count=${selectedSessionPaths.size}")
                    onExport()
                }) {
                    Text(if (exportPreparing) "PREPARING EXPORT…" else "EXPORT SELECTED (${selectedSessionPaths.size})")
                }
                LaunchedEffect(exportPreparing) {
                    if (exportPreparing) {
                        delay(700)
                        exportPreparing = false
                    }
                }
                onBack?.let { Button(onClick = it) { Text("EXIT DEVELOPER MODE") } }
            } else {
                Button(onClick = {
                    destinationSearch = DestinationSearchSelectionReducer.open(destinationSearch)
                    menuOpen = false
                    RenavDiagnostics.event("destination_search_opened")
                }, modifier = Modifier.semantics { contentDescription = "Search for a destination" }) { Text("SEARCH DESTINATION") }
                Button(onClick = {
                    manualStartSelection = ManualStartSelectionReducer.enter(manualStartSelection)
                    menuOpen = false
                    RenavDiagnostics.event("manual_start_point_selection_started")
                }, modifier = Modifier.semantics { contentDescription = "Select manual navigation start point" }) { Text("SELECT START POINT") }
                Button(onClick = {
                    menuOpen = false
                    onOpenSettings()
                }) { Text("SETTINGS") }
                Button(onClick = {
                    menuOpen = false
                    onOpenOfflineMaps()
                }) { Text("OFFLINE MAPS") }
                Button(onClick = {
                    menuOpen = false
                    onOpenDeveloper()
                }) { Text("DEVELOPER MODE") }
            }
        }
        }
    }
}

@Composable
private fun DestinationSearchPanel(
    state: DestinationSearchSelectionState,
    onQuery: (String) -> Unit,
    onSelect: (com.example.navigation.maps.DestinationIndexRecord) -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.96f)).padding(16.dp)
            .semantics { contentDescription = "Destination search and selection" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.phase == DestinationSearchPhase.SELECTED) {
            val proposed = state.proposedDestination ?: return@Column
            Text("PROPOSED DESTINATION", fontWeight = FontWeight.Bold)
            Text(proposed.label)
            Text("${"%.5f".format(java.util.Locale.US, proposed.coordinate.latitude)}, ${"%.5f".format(java.util.Locale.US, proposed.coordinate.longitude)}")
            Text("Proposed destination only — route planning has not started", color = Color(0xFF415A69))
            Button(onClick = onReplace, modifier = Modifier.semantics { contentDescription = "Replace proposed destination" }) { Text("CHANGE DESTINATION") }
            Button(onClick = onCancel, modifier = Modifier.semantics { contentDescription = "Cancel proposed destination" }) { Text("CANCEL") }
            return@Column
        }
        Text("SEARCH DESTINATION", fontWeight = FontWeight.Bold)
        TextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text("Address or place") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Destination address or place query" }
        )
        Button(onClick = { focusManager.clearFocus() }, modifier = Modifier.semantics { contentDescription = "Hide destination search keyboard" }) { Text("HIDE KEYBOARD") }
        when (val result = state.searchResult) {
            null -> Text("Search the downloaded map for an address or place.")
            DestinationSearchResult.NoRegionInstalled -> Text("No downloaded regional search index is available. Download a compatible map to search offline.", color = Color(0xFFB71C1C))
            is DestinationSearchResult.IndexUnavailable -> Text(result.reason, color = Color(0xFFB71C1C))
            DestinationSearchResult.QueryTooShort -> Text("Enter at least ${OfflineDestinationSearchIndex.MINIMUM_QUERY_LENGTH} characters.")
            DestinationSearchResult.NoMatch -> Text("No address or place matches this query.")
            is DestinationSearchResult.Results -> result.matches.forEach { record ->
                Button(onClick = { onSelect(record) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Select destination ${record.displayLabel}" }) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(record.displayLabel)
                        Text(record.sourceType.name.lowercase().replaceFirstChar(Char::uppercaseChar))
                    }
                }
            }
        }
        Button(onClick = onCancel, modifier = Modifier.semantics { contentDescription = "Cancel destination search" }) { Text("CANCEL") }
    }
}

@Composable
private fun RoutePreviewPanel(
    presentation: RoutePreviewPresentation,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onChangeDestination: () -> Unit
) {
    Column(
        Modifier.widthIn(max = 340.dp).background(Color.White.copy(alpha = 0.96f)).padding(16.dp)
            .semantics { contentDescription = "Route preview: ${presentation.label}; ${presentation.detail}" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(presentation.label, fontWeight = FontWeight.Bold)
        Text(presentation.detail)
        if (presentation.activeRoutePreserved) Text("Current active route remains unchanged until confirmation.")
        if (presentation.canStartNavigation) {
            Button(onClick = onStart, modifier = Modifier.semantics { contentDescription = "Start navigation on proposed route" }) { Text("START NAVIGATION") }
        }
        Button(onClick = onChangeDestination, modifier = Modifier.semantics { contentDescription = "Change route destination" }) { Text("CHANGE DESTINATION") }
        Button(onClick = onCancel, modifier = Modifier.semantics { contentDescription = "Cancel route preview" }) { Text("CANCEL") }
    }
}

@Composable
private fun ActiveRouteGuidancePanel(
    presentation: ActiveRouteGuidancePresentation,
    showSyntheticAdvance: Boolean,
    onAdvanceSynthetic: () -> Unit,
    onStop: () -> Unit,
    onNewDestination: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.96f)).padding(16.dp)
            .semantics { contentDescription = "Active route guidance: ${presentation.label}; ${presentation.detail}" },
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(presentation.label, fontWeight = FontWeight.Bold)
        Text(presentation.detail)
        presentation.maneuver?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        presentation.remainingDistanceLabel?.let { Text("Remaining: $it") }
        presentation.progress?.let { progress -> LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
        presentation.sourceLabel?.let { Text("ORIGIN SOURCE: $it") }
        presentation.confidenceLabel?.let { Text("CONFIDENCE: $it") }
        presentation.accuracyLabel?.let { Text("ESTIMATED ACCURACY: $it") }
        if (showSyntheticAdvance) {
            Text("DEBUG SYNTHETIC JOURNEY", color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
            Button(
                onClick = onAdvanceSynthetic,
                modifier = Modifier.semantics { contentDescription = "Advance synthetic route observation" }
            ) { Text("ADVANCE SYNTHETIC ROUTE") }
        }
        if (presentation.canStop) Button(onClick = onStop, modifier = Modifier.semantics { contentDescription = "Stop navigation" }) { Text("STOP NAVIGATION") }
        if (presentation.canChooseNewDestination) Button(onClick = onNewDestination, modifier = Modifier.semantics { contentDescription = "Choose a new destination" }) { Text("NEW DESTINATION") }
    }
}
