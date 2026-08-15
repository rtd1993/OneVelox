package com.onevelox.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private val AppBlack = Color(0xFF020202)
private val CardBlack = Color(0xFF101113)
private val RoadGray = Color(0xFF2B2D31)
private val LaneGray = Color(0xFFF6F6F6)
private val AccentRed = Color(0xFFF25F5C)
private val AccentAmber = Color(0xFFFFB703)
private val AccentCyan = Color(0xFF7FDBFF)
private val AccentGreen = Color(0xFF4ADE80)
private val AsphaltRed = Color(0xFF7B1E24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(vm: DriveViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val leftLateral = state.lateralAlerts.filter { it.side == RoadSide.LEFT }.minByOrNull { it.distanceMeters }
    val rightLateral = state.lateralAlerts.filter { it.side == RoadSide.RIGHT }.minByOrNull { it.distanceMeters }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                state = state,
                onMainDistance = vm::setMainDistance,
                onLateralDistance = vm::setLateralDistance,
                onRefreshDatabase = vm::refreshDatabase,
                onSetAlertEnabled = vm::setAlertEnabled,
                onVehicleTypeSelected = vm::setVehicleIconType,
                onVehicleColorSelected = vm::setVehicleColorName,
                onBackToDashboard = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = AppBlack,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppBlack,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    title = {
                        Text("OneVelox", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Apri impostazioni")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBlack)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusRow(state)
                TopRoadInfo(state = state)
                RoadScene(
                    state = state,
                    leftLateral = leftLateral,
                    rightLateral = rightLateral
                )
            }
        }
    }
}

@Composable
private fun SettingsDrawer(
    state: DriveUiState,
    onMainDistance: (Int) -> Unit,
    onLateralDistance: (Int) -> Unit,
    onRefreshDatabase: () -> Unit,
    onSetAlertEnabled: (DangerType, Boolean) -> Unit,
    onVehicleTypeSelected: (String) -> Unit,
    onVehicleColorSelected: (String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    var sectionDistanceOpen by remember { mutableStateOf(false) }
    var sectionAlertsOpen by remember { mutableStateOf(false) }
    var sectionVehicleOpen by remember { mutableStateOf(false) }
    var sectionDbOpen by remember { mutableStateOf(false) }

    val vehicleTypes = listOf("AUTO", "MOTO", "CAMION")
    val vehicleColors = VehicleColorNames

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0B0B0C),
        drawerContentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Impostazioni", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Dashboard, contentDescription = null)
                Text("Torna alla dashboard")
            }
            Text("Modalita: GPS reale", color = Color(0xFF9CA3AF))

            CollapsibleSection(
                title = "Distanze avvisi",
                expanded = sectionDistanceOpen,
                onToggle = { sectionDistanceOpen = !sectionDistanceOpen }
            ) {
                Text("Distanza strada principale: ${state.settings.mainRoadAlertDistanceMeters}m")
                Slider(
                    value = state.settings.mainRoadAlertDistanceMeters.toFloat(),
                    onValueChange = { onMainDistance(it.toInt()) },
                    valueRange = 300f..2000f
                )
                Text("Distanza strade vicine: ${state.settings.lateralRoadAlertDistanceMeters}m")
                Slider(
                    value = state.settings.lateralRoadAlertDistanceMeters.toFloat(),
                    onValueChange = { onLateralDistance(it.toInt()) },
                    valueRange = 50f..500f
                )
            }

            CollapsibleSection(
                title = "Avvisi attivi",
                expanded = sectionAlertsOpen,
                onToggle = { sectionAlertsOpen = !sectionAlertsOpen }
            ) {
                AlertToggleRow("Autovelox", state.settings.autoveloxEnabled) { onSetAlertEnabled(DangerType.SPEED_CAMERA, it) }
                AlertToggleRow("VeloBox", state.settings.veloboxEnabled) { onSetAlertEnabled(DangerType.VELOBOX, it) }
                AlertToggleRow("VeloOK", state.settings.velookEnabled) { onSetAlertEnabled(DangerType.VELOOK, it) }
                AlertToggleRow("Tutor", state.settings.tutorEnabled) { onSetAlertEnabled(DangerType.TUTOR, it) }
                AlertToggleRow("T-Red", state.settings.tRedEnabled) { onSetAlertEnabled(DangerType.T_RED, it) }
                AlertToggleRow("ZTL", state.settings.ztlEnabled) { onSetAlertEnabled(DangerType.ZTL, it) }
                AlertToggleRow("Area controllata", state.settings.zoneAreaEnabled) { onSetAlertEnabled(DangerType.ZONE_AREA, it) }
                AlertToggleRow("Sorpassometro", state.settings.surveillanceEnabled) { onSetAlertEnabled(DangerType.SURVEILLANCE, it) }
                AlertToggleRow("Corsia preferenziale", state.settings.buswayEnabled) { onSetAlertEnabled(DangerType.BUSWAY, it) }
                AlertToggleRow("Pericoli", state.settings.hazardEnabled) { onSetAlertEnabled(DangerType.HAZARD, it) }
            }

            CollapsibleSection(
                title = "Veicolo dashboard",
                expanded = sectionVehicleOpen,
                onToggle = { sectionVehicleOpen = !sectionVehicleOpen }
            ) {
                DropdownSelector(
                    label = "Tipo icona",
                    currentValue = state.settings.vehicleIconType,
                    values = vehicleTypes,
                    onSelected = onVehicleTypeSelected
                )
                DropdownSelector(
                    label = "Colore icona",
                    currentValue = state.settings.vehicleColorName,
                    values = vehicleColors,
                    onSelected = onVehicleColorSelected
                )
            }

            CollapsibleSection(
                title = "Database POI",
                expanded = sectionDbOpen,
                onToggle = { sectionDbOpen = !sectionDbOpen }
            ) {
                FilledTonalButton(
                    onClick = onRefreshDatabase,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.dbSyncInProgress
                ) {
                    Text(if (state.dbSyncInProgress) "Aggiornamento DB in corso..." else "Aggiorna DB")
                }

                if (state.dbSyncStatus.isNotBlank()) {
                    Text(
                        "Feed DB: ${state.dbSyncStatus}",
                        color = dbStatusColor(state),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.dbSyncInProgress) {
                    LinearProgressIndicator(
                        progress = { state.dbSyncProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentAmber,
                        trackColor = Color(0xFF2A2F36)
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121318))) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    currentValue: String,
    values: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.labelMedium)
        Box {
            FilledTonalButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(currentValue)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE5E7EB))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(state: DriveUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusChip(if (state.moving) "GPS MOV" else "GPS", state.gpsOk, Icons.Default.GpsFixed, Modifier.weight(1f))
        DbStatusChip(label = "POI: ${state.loadedPoiCount}", state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TopRoadInfo(state: DriveUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBlack)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Strada corrente", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.labelMedium)
            Text(
                state.currentRoadName.ifEmpty { "In attesa posizione" },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            state.mainAlert?.let { alert ->
                MainAlertCard(alert = alert)
            }
            if (state.uncertainJunctionMode) {
                Text(
                    "Incrocio con direzione incerta: pericoli mostrati fino a 1 km in tutte le direzioni",
                    color = AccentAmber,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                state.allDirectionsAlerts.take(3).forEach { alert ->
                    val sideLabel = when (alert.side) {
                        RoadSide.LEFT -> "Sinistra"
                        RoadSide.RIGHT -> "Destra"
                        RoadSide.MAIN -> "Frontale"
                    }
                    Text(
                        "$sideLabel • ${alert.type.label} • ${alert.distanceMeters} m",
                        color = Color(0xFFE5E7EB),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (state.dataSourceLabel.isNotBlank()) {
                Text(
                    "Fonte dati: ${state.dataSourceLabel}",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            state.activeTutorSegment?.let { tutor ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13232D))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Tutor attivo: ${tutor.label}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Velocita media attuale: ${tutor.currentAverageSpeedKmh} km/h", color = Color(0xFFBFE7FF))
                        Text("Limite media tratto: ${tutor.maxAverageSpeedKmh} km/h", color = Color(0xFFBFE7FF))
                        Text("Fine tratto tra ${tutor.remainingMeters} m", color = AccentAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            state.tutorSegmentResultAlert?.let { result ->
                val bg = if (result.compliant) Color(0xFF11381F) else Color(0xFF3A1212)
                val fg = if (result.compliant) AccentGreen else AccentRed
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = bg)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Fine tratto Tutor: ${result.label}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Media finale: ${result.averageSpeedKmh} km/h (limite ${result.maxAverageSpeedKmh} km/h)",
                            color = fg,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAlertCard(alert: com.onevelox.app.model.MainRoadAlert) {
    val danger = alert.danger
    val accent = if (alert.overspeed) AccentRed else AccentAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171B))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(dangerIcon(danger.type), contentDescription = null, tint = accent)
                BannerFitText(
                    text = danger.type.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                SpeedLimitSign(limitKmh = danger.allowedSpeedKmh)
            }
            RestrictionScheduleNotice(danger = danger)
            BannerFitText(
                text = "Distanza ${danger.distanceMeters} m",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                maxFontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (ok) AccentGreen else AccentRed, modifier = Modifier.size(16.dp))
            BannerFitText(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxFontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DbStatusChip(label: String, state: DriveUiState, modifier: Modifier = Modifier) {
    val tint = dbStatusColor(state)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            BannerFitText(
                text = label,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxFontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun dbStatusColor(state: DriveUiState): Color {
    if (state.dbSyncInProgress) return AccentAmber
    if (state.loadedPoiCount < 20) return AccentRed
    if (state.dbUpdateAvailable) return AccentAmber
    if (state.dbSyncErrorType != null) return AccentRed
    return AccentGreen
}

@Composable
private fun RoadScene(state: DriveUiState, leftLateral: DangerPoint?, rightLateral: DangerPoint?) {
    val sceneMainAlert = state.mainAlert
    val sceneLeftLateral = leftLateral
    val sceneRightLateral = rightLateral
    val speedDuration = (2400 - state.speedKmh * 12).coerceIn(350, 2400)
    val transition = rememberInfiniteTransition(label = "road")
    val laneOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (state.speedKmh <= 0) 0f else 72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laneOffset"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0D), Color(0xFF000000))))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val roadWidth = size.width * 0.36f
                val roadLeft = (size.width - roadWidth) / 2f
                drawRoundRect(
                    color = RoadGray,
                    topLeft = androidx.compose.ui.geometry.Offset(roadLeft, 0f),
                    size = androidx.compose.ui.geometry.Size(roadWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f, 48f)
                )
                val leftLineX = roadLeft + 18f
                val rightLineX = roadLeft + roadWidth - 18f
                drawMovingDashedVerticalLine(x = leftLineX, laneOffset = laneOffset, color = LaneGray)
                drawMovingDashedVerticalLine(x = rightLineX, laneOffset = laneOffset, color = LaneGray)

                sceneLeftLateral?.takeIf { it.type == DangerType.BUSWAY }?.let {
                    drawRestrictedSideLane(
                        side = RoadSide.LEFT,
                        defaultBoundaryLineX = leftLineX,
                        laneOffset = laneOffset,
                        pulse = pulse
                    )
                }
                sceneRightLateral?.takeIf { it.type == DangerType.BUSWAY }?.let {
                    drawRestrictedSideLane(
                        side = RoadSide.RIGHT,
                        defaultBoundaryLineX = rightLineX,
                        laneOffset = laneOffset,
                        pulse = pulse
                    )
                }

                sceneLeftLateral?.takeIf { it.type != DangerType.BUSWAY }?.let {
                    drawSideBranchRoad(
                        side = RoadSide.LEFT,
                        roadLeft = roadLeft,
                        roadWidth = roadWidth,
                        dangerType = it.type
                    )
                }

                sceneRightLateral?.takeIf { it.type != DangerType.BUSWAY }?.let {
                    drawSideBranchRoad(
                        side = RoadSide.RIGHT,
                        roadLeft = roadLeft,
                        roadWidth = roadWidth,
                        dangerType = it.type
                    )
                }
            }

            sceneMainAlert?.let { alert ->
                DangerPill(
                    label = alert.danger.type.label,
                    distanceMeters = alert.danger.distanceMeters,
                    icon = dangerIcon(alert.danger.type),
                    accent = if (alert.overspeed) AccentRed else AccentAmber,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .fillMaxWidth(0.72f)
                )
            }
            sceneLeftLateral?.let { danger ->
                LateralAlertBanner(
                    danger = danger,
                    pulse = pulse,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 20.dp)
                        .fillMaxWidth(0.42f)
                )
            }
            sceneRightLateral?.let { danger ->
                LateralAlertBanner(
                    danger = danger,
                    pulse = pulse,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 20.dp)
                        .fillMaxWidth(0.42f)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VehicleTopGlyph(
                    vehicleType = state.settings.vehicleIconType,
                    colorName = state.settings.vehicleColorName,
                    modifier = Modifier.size(88.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.speedKmh}",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("km/h", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.titleMedium)
                    val tutorAverage = state.activeTutorSegment?.currentAverageSpeedKmh
                        ?: state.recentTutorAverage?.averageSpeedKmh
                    val tutorLimit = state.activeTutorSegment?.maxAverageSpeedKmh
                        ?: state.recentTutorAverage?.maxAverageSpeedKmh
                    tutorAverage?.let { avg ->
                        TutorAverageChip(averageKmh = avg, limitKmh = tutorLimit)
                    }
                }
            }

            if (state.turnSlowdownDetected) {
                Text(
                    "Rallentamento svolta rilevato",
                    color = AccentAmber,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TutorAverageChip(averageKmh: Int, limitKmh: Int?) {
    val overLimit = limitKmh != null && averageKmh > limitKmh
    val background = if (overLimit) Color(0xFF3A1212) else Color(0xFF11381F)
    val foreground = if (overLimit) AccentRed else AccentGreen
    val label = if (limitKmh != null) {
        "Media $averageKmh km/h  •  limite $limitKmh"
    } else {
        "Media $averageKmh km/h"
    }
    Card(colors = CardDefaults.cardColors(containerColor = background)) {
        Text(
            text = label,
            color = foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMovingDashedVerticalLine(
    x: Float,
    laneOffset: Float,
    color: Color,
    strokeWidth: Float = 7f,
    dashHeight: Float = 34f,
    dashGap: Float = 38f
) {
    var y = -dashHeight + laneOffset
    while (y < size.height + dashHeight) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(x, y),
            end = androidx.compose.ui.geometry.Offset(x, y + dashHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        y += dashHeight + dashGap
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRestrictedSideLane(
    side: RoadSide,
    defaultBoundaryLineX: Float,
    laneOffset: Float,
    pulse: Float
) {
    val desiredWidth = 126f
    val offsetToMargin = if (side == RoadSide.LEFT) {
        desiredWidth.coerceAtMost(defaultBoundaryLineX)
    } else {
        desiredWidth.coerceAtMost((size.width - defaultBoundaryLineX).coerceAtLeast(0f))
    }
    val newDashedLineX = if (side == RoadSide.LEFT) {
        defaultBoundaryLineX - offsetToMargin
    } else {
        defaultBoundaryLineX + offsetToMargin
    }
    val fillStartX = minOf(defaultBoundaryLineX, newDashedLineX)
    val fillWidth = kotlin.math.abs(defaultBoundaryLineX - newDashedLineX)
    drawRoundRect(
        color = AsphaltRed.copy(alpha = 0.92f),
        topLeft = androidx.compose.ui.geometry.Offset(fillStartX, 0f),
        size = androidx.compose.ui.geometry.Size(fillWidth, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = Color(0xFF5C1218).copy(alpha = 0.45f),
        topLeft = androidx.compose.ui.geometry.Offset(fillStartX + 4f, 0f),
        size = androidx.compose.ui.geometry.Size((fillWidth * 0.55f).coerceAtLeast(8f), size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
    )
    drawMovingDashedVerticalLine(
        x = newDashedLineX,
        laneOffset = laneOffset,
        color = Color(0xFFFFE0E0).copy(alpha = pulse),
        strokeWidth = 6f
    )
    drawMovingDashedVerticalLine(
        x = (defaultBoundaryLineX + newDashedLineX) / 2f,
        laneOffset = laneOffset,
        color = Color(0xFFFFB4B4).copy(alpha = 0.35f * pulse),
        strokeWidth = 4f,
        dashHeight = 22f,
        dashGap = 30f
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFixedDashedHorizontalLine(
    y: Float,
    startX: Float,
    endX: Float,
    color: Color,
    strokeWidth: Float = 7f,
    dashWidth: Float = 34f,
    dashGap: Float = 38f
) {
    val left = minOf(startX, endX)
    val right = maxOf(startX, endX)
    var x = left
    while (x < right) {
        val dashEnd = (x + dashWidth).coerceAtMost(right)
        if (dashEnd - x > 4f) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(dashEnd, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        x += dashWidth + dashGap
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSideBranchRoad(
    side: RoadSide,
    roadLeft: Float,
    roadWidth: Float,
    dangerType: DangerType
) {
    val isRestrictedAccess = dangerType == DangerType.ZTL || dangerType == DangerType.ZONE_AREA
    val branchHeight = (roadWidth * 0.40f).coerceIn(120f, 190f)
    val joinOverlap = 10f
    val branchLength = size.width * 0.28f + joinOverlap
    val centerY = if (isRestrictedAccess) size.height * 0.52f else size.height * 0.30f
    val top = centerY - branchHeight / 2f
    val left = if (side == RoadSide.LEFT) {
        roadLeft - branchLength + joinOverlap
    } else {
        roadLeft + roadWidth - joinOverlap
    }
    val asphalt = if (isRestrictedAccess) AsphaltRed.copy(alpha = 0.92f) else RoadGray
    drawRoundRect(
        color = asphalt,
        topLeft = androidx.compose.ui.geometry.Offset(left, top),
        size = androidx.compose.ui.geometry.Size(branchLength, branchHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f)
    )
    if (isRestrictedAccess) {
        drawRoundRect(
            color = Color(0xFF5C1218).copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(left + 4f, top + 4f),
            size = androidx.compose.ui.geometry.Size(
                (branchLength - 8f).coerceAtLeast(8f),
                (branchHeight - 8f).coerceAtLeast(8f)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
        )
    }
    val lineInset = 18f
    val lineColor = if (isRestrictedAccess) Color.White else LaneGray
    val lineStartX = left + 8f
    val lineEndX = left + branchLength - 8f
    drawFixedDashedHorizontalLine(
        y = top + lineInset,
        startX = lineStartX,
        endX = lineEndX,
        color = lineColor
    )
    drawFixedDashedHorizontalLine(
        y = top + branchHeight - lineInset,
        startX = lineStartX,
        endX = lineEndX,
        color = lineColor
    )
}

@Composable
private fun SpeedLimitSign(limitKmh: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = limitKmh.coerceIn(10, 130).toString(),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun BannerFitText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    maxFontSize: TextUnit = 13.sp,
    minFontSize: TextUnit = 8.sp
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        val fontSize = remember(text, maxWidthPx, fontWeight, maxFontSize.value, minFontSize.value) {
            if (maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
                return@remember maxFontSize
            }
            var low = minFontSize.value
            var high = maxFontSize.value
            var best = minFontSize.value
            repeat(12) {
                val mid = (low + high) / 2f
                val layout = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = mid.sp,
                        fontWeight = fontWeight,
                        textAlign = textAlign
                    ),
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidthPx)
                )
                if (!layout.hasVisualOverflow && layout.size.width <= maxWidthPx) {
                    best = mid
                    low = mid
                } else {
                    high = mid
                }
            }
            best.sp
        }
        Text(
            text = text,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BannerLine(
    text: String,
    color: Color,
    textAlign: TextAlign,
    fontWeight: FontWeight = FontWeight.Normal,
    maxFontSize: TextUnit = 12.sp
) {
    BannerFitText(
        text = text,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxFontSize = maxFontSize
    )
}

@Composable
private fun DangerPill(
    label: String,
    distanceMeters: Int,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC15171A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                BannerFitText(text = label, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 14.sp)
                BannerFitText(text = "$distanceMeters m", color = Color(0xFFCBD5E1), maxFontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LateralAlertBanner(
    danger: DangerPoint,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    if (danger.type.isMainPreviewType()) {
        DangerPill(
            label = danger.type.label,
            distanceMeters = danger.distanceMeters,
            icon = dangerIcon(danger.type),
            accent = AccentAmber,
            modifier = modifier
        )
    } else {
        SideDangerBadge(
            danger = danger,
            pulse = pulse,
            modifier = modifier
        )
    }
}

@Composable
private fun SideBadgeLeadIcon(danger: DangerPoint, pulse: Float) {
    if (danger.type == DangerType.ZTL || danger.type == DangerType.ZONE_AREA) {
        BlockedDirectionIcon(side = danger.side, pulse = pulse)
    } else {
        Icon(sideMarkerIcon(danger.type), contentDescription = null, tint = AccentAmber.copy(alpha = pulse))
    }
}

@Composable
private fun SideDangerBadge(
    danger: DangerPoint,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val isRight = danger.side == RoadSide.RIGHT
    val sideLabel = when (danger.side) {
        RoadSide.LEFT -> "Sinistra"
        RoadSide.RIGHT -> "Destra"
        RoadSide.MAIN -> "Centro"
    }
    val textAlign = if (isRight) TextAlign.End else TextAlign.Start
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xDD16181D))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (isRight) Alignment.End else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isRight) {
                    SideBadgeLeadIcon(danger = danger, pulse = pulse)
                }
                BannerFitText(
                    text = "$sideLabel • ${danger.type.label}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign,
                    maxFontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isRight) {
                    SideBadgeLeadIcon(danger = danger, pulse = pulse)
                }
            }
            when (danger.type) {
                DangerType.BUSWAY -> {
                    BannerLine("Corsia ${if (danger.side == RoadSide.RIGHT) "a destra" else "a sinistra"}", AccentRed, textAlign, FontWeight.SemiBold)
                    BannerLine("X divieto di transito sulla corsia", AccentRed, textAlign)
                }
                DangerType.ZTL -> {
                    BannerLine("Divieto di accesso ZTL", AccentRed, textAlign, FontWeight.SemiBold)
                    BannerLine("Varco controllato con freccia sbarrata", AccentAmber, textAlign)
                }
                DangerType.ZONE_AREA -> {
                    BannerLine("Area controllata", AccentRed, textAlign, FontWeight.SemiBold)
                    BannerLine("Varco area con freccia sbarrata", AccentAmber, textAlign)
                }
                DangerType.SURVEILLANCE -> {
                    BannerLine("Divieto di sorpasso", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("Fine tratta tra ${danger.distanceMeters} m", Color(0xFF94A3B8), textAlign)
                }
                DangerType.T_RED -> {
                    BannerLine("Semaforo controllato", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("T-Red attivo", Color(0xFF94A3B8), textAlign)
                }
                DangerType.TUTOR -> {
                    BannerLine("Tutor sul tratto", AccentCyan, textAlign, FontWeight.SemiBold)
                    BannerLine("Fine tratta tra ${danger.distanceMeters} m", Color(0xFF94A3B8), textAlign)
                }
                DangerType.VELOBOX -> {
                    BannerLine("VeloBox temporaneo", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("Velocita max ${danger.allowedSpeedKmh} km/h", Color(0xFF94A3B8), textAlign)
                }
                DangerType.VELOOK -> {
                    BannerLine("VeloOK / telecamera controllo", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("Sorveglianza elettronica", Color(0xFF94A3B8), textAlign)
                }
                DangerType.SPEED_CAMERA -> {
                    BannerLine("Autovelox fisso", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("Velocita max ${danger.allowedSpeedKmh} km/h", Color(0xFF94A3B8), textAlign)
                }
                DangerType.HAZARD -> {
                    BannerLine("Pericolo laterale", AccentAmber, textAlign, FontWeight.SemiBold)
                    BannerLine("${danger.distanceMeters} m", Color(0xFF94A3B8), textAlign)
                }
            }
            RestrictionScheduleNotice(danger = danger, textAlign = textAlign)
            BannerLine(
                text = danger.branchRoadName ?: "${danger.distanceMeters} m",
                color = Color(0xFFE5E7EB),
                textAlign = textAlign,
                maxFontSize = 11.sp
            )
        }
    }
}

private fun dangerIcon(type: DangerType): ImageVector = when (type) {
    DangerType.SPEED_CAMERA -> Icons.Default.CameraAlt
    DangerType.VELOBOX -> Icons.Default.Close
    DangerType.VELOOK -> Icons.Default.ReportProblem
    DangerType.TUTOR -> Icons.Default.Speed
    DangerType.T_RED -> Icons.Default.Traffic
    DangerType.ZTL -> Icons.Default.Block
    DangerType.ZONE_AREA -> Icons.Default.Block
    DangerType.SURVEILLANCE -> Icons.Default.ReportProblem
    DangerType.BUSWAY -> Icons.Default.Block
    DangerType.HAZARD -> Icons.Default.ReportProblem
}

private fun sideMarkerIcon(type: DangerType): ImageVector = when (type) {
    DangerType.BUSWAY -> Icons.Default.Close
    DangerType.ZTL -> Icons.Default.Block
    DangerType.ZONE_AREA -> Icons.Default.Block
    DangerType.SURVEILLANCE -> Icons.Default.ReportProblem
    DangerType.T_RED -> Icons.Default.Traffic
    DangerType.TUTOR -> Icons.Default.Speed
    DangerType.SPEED_CAMERA -> Icons.Default.CameraAlt
    DangerType.VELOBOX -> Icons.Default.Close
    DangerType.VELOOK -> Icons.Default.ReportProblem
    DangerType.HAZARD -> Icons.Default.ReportProblem
}

private fun DangerType.isMainPreviewType(): Boolean = when (this) {
    DangerType.SPEED_CAMERA,
    DangerType.VELOBOX,
    DangerType.VELOOK,
    DangerType.TUTOR,
    DangerType.T_RED,
    DangerType.SURVEILLANCE -> true
    DangerType.ZTL,
    DangerType.ZONE_AREA,
    DangerType.BUSWAY,
    DangerType.HAZARD -> false
}

private fun lateralBranchColor(type: DangerType): Color = when (type) {
    DangerType.BUSWAY, DangerType.ZTL, DangerType.ZONE_AREA -> AccentRed
    else -> AccentAmber
}

@Composable
private fun BlockedDirectionIcon(side: RoadSide, pulse: Float) {
    val arrow = when (side) {
        RoadSide.LEFT -> Icons.Default.ArrowBack
        RoadSide.RIGHT -> Icons.Default.ArrowForward
        RoadSide.MAIN -> Icons.Default.ArrowUpward
    }
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(arrow, contentDescription = null, tint = AccentAmber.copy(alpha = pulse))
        Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RestrictionScheduleNotice(danger: DangerPoint, textAlign: TextAlign = TextAlign.Start) {
    val notice = evaluateRestrictionNotice(danger)
    if (notice != null) {
        BannerFitText(
            text = notice.first,
            color = if (notice.second) AccentRed else AccentAmber,
            fontWeight = if (notice.second) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = textAlign,
            maxFontSize = 11.sp
        )
    }
}

private fun evaluateRestrictionNotice(danger: DangerPoint): Pair<String, Boolean>? {
    val schedule = danger.restrictionSchedule?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = schedule.substringAfter('(').substringBeforeLast(')', schedule).ifBlank { schedule }
    val active = isScheduleActiveNow(normalized)
    return when (active) {
        true -> "Varco attivo ora: rischio multa" to true
        false -> "Fuori fascia attiva: $normalized" to false
        null -> "Orari OSM: $schedule" to false
    }
}

private fun isScheduleActiveNow(schedule: String): Boolean? {
    val normalized = schedule.replace(";", " ").replace("  ", " ")
    if (normalized.contains("24/7", ignoreCase = true)) return true
    val calendar = Calendar.getInstance()
    val dayIndex = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 0
    }
    val hhmm = calendar.get(Calendar.HOUR_OF_DAY) * 100 + calendar.get(Calendar.MINUTE)
    val dayRanges = Regex("(Mo|Tu|We|Th|Fr|Sa|Su)(?:-(Mo|Tu|We|Th|Fr|Sa|Su))?")
        .findAll(normalized)
        .mapNotNull { match ->
            val start = weekdayIndex(match.groupValues[1])
            val end = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(::weekdayIndex) ?: start
            if (start == null || end == null) null else start..end
        }
        .toList()
    val dayMatches = if (dayRanges.isEmpty()) true else dayRanges.any { dayIndex in it }
    val timeRanges = Regex("(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})")
        .findAll(normalized)
        .mapNotNull { match ->
            val start = parseHhMm(match.groupValues[1])
            val end = parseHhMm(match.groupValues[2])
            if (start == null || end == null) null else start to end
        }
        .toList()
    val timeMatches = if (timeRanges.isEmpty()) dayRanges.isNotEmpty() else timeRanges.any { (start, end) -> hhmm in start..end }
    return if (!dayMatches) false else if (timeRanges.isEmpty() && dayRanges.isEmpty()) null else timeMatches
}

private fun weekdayIndex(token: String): Int? = when (token.lowercase(Locale.ROOT)) {
    "mo" -> 1
    "tu" -> 2
    "we" -> 3
    "th" -> 4
    "fr" -> 5
    "sa" -> 6
    "su" -> 7
    else -> null
}

private fun parseHhMm(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return (hours * 100 + minutes).takeIf { hours in 0..23 && minutes in 0..59 }
}
