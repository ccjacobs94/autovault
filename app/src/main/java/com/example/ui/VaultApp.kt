package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.room.ServiceLog
import com.example.data.room.UpcomingMaintenance
import com.example.data.room.Vehicle
import kotlinx.coroutines.launch
import com.example.data.AutoBackupManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultApp(viewModel: VaultViewModel, autoBackupManager: AutoBackupManager? = null) {
    val vehicles by viewModel.allVehicles.collectAsStateWithLifecycle()
    val activeVehicle by viewModel.activeVehicle.collectAsStateWithLifecycle()
    val activeLogs by viewModel.activeServiceLogs.collectAsStateWithLifecycle()
    val activeMaintenance by viewModel.activeMaintenance.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var showAddMaintenanceDialog by remember { mutableStateOf(false) }

    var showManageMaintenancePage by remember { mutableStateOf(false) }

    // Auto select first vehicle
    LaunchedEffect(vehicles) {
        val currentVehicles = vehicles
        if (currentVehicles != null) {
            if (currentVehicles.isNotEmpty() && activeVehicle == null) {
                viewModel.setActiveVehicle(currentVehicles.first().id)
            }
        }
    }

    var wasInitiallyEmpty by remember { mutableStateOf<Boolean?>(null) }
    var hideOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(vehicles) {
        if (wasInitiallyEmpty == null && vehicles != null) {
            wasInitiallyEmpty = vehicles!!.isEmpty()
        }
    }

    if (wasInitiallyEmpty == true && !hideOnboarding) {
        OnboardingScreen(onAdd = { mk, md, y, o, auto, onProgress, onComplete -> 
            viewModel.insertVehicle(mk, md, y, o, auto, onProgress) {
                onComplete()
            }
        }, onFinish = {
            hideOnboarding = true
        })
    } else if (showManageMaintenancePage) {
        MaintenanceScreen(
            maintenanceList = activeMaintenance,
            onBack = { showManageMaintenancePage = false },
            onAdd = { title, desc, rec, last -> viewModel.addCustomMaintenance(title, desc, rec, last) },
            onUpdate = { id, title, desc, rec, last -> viewModel.updateCustomMaintenance(id, title, desc, rec, last) },
            onDelete = { id -> viewModel.deleteMaintenance(id) }
        )
    } else {
        Scaffold(
            topBar = { 
                VaultTopBar(
                    vehicles = vehicles ?: emptyList(),
                    activeVehicle = activeVehicle,
                    onVehicleSelected = { viewModel.setActiveVehicle(it) },
                    onAddVehicle = { showAddVehicleDialog = true }
                ) 
            },
            bottomBar = { VaultBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                val isExpanded = maxWidth > 600.dp
                
                if (activeVehicle == null && !showAddVehicleDialog) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active vehicle. Please add one.")
                    }
                } else {
                    when (selectedTab) {
                        0 -> GarageScreen(
                            isExpanded = isExpanded,
                            vehicle = activeVehicle,
                            activeMaintenance = activeMaintenance,
                            isAnalyzing = isAnalyzing,
                            onUpdateOdo = { viewModel.updateOdometer(it) },
                            onLogService = { service, odo -> viewModel.handleLogService(service, odo) },
                            onAddReceipt = { uri -> 
                                viewModel.addServiceLog(title = "Receipt Upload", desc = "Scanned Receipt", cost = 0.0, isDiy = true, receiptImageUri = uri)
                            }
                        )
                        1 -> HistoryScreen(
                            logs = activeLogs,
                            onUpdate = { viewModel.updateServiceLog(it) },
                            onDelete = { viewModel.deleteServiceLog(it) }
                        )
                        2 -> AiExpertScreen(
                            messages = chatMessages,
                            isLoading = isChatLoading,
                            onSendMessage = { viewModel.sendChatMessage(it) },
                            onClearChat = { viewModel.clearChat() }
                        )
                        3 -> SettingsScreen(
                            logs = activeLogs,
                            autoBackupManager = autoBackupManager,
                            milesPerWeek = viewModel.milesPerWeek.collectAsStateWithLifecycle().value,
                            onMilesPerWeekChange = { viewModel.setMilesPerWeek(it) },
                            notificationTime = viewModel.notificationTime.collectAsStateWithLifecycle().value,
                            onNotificationTimeChange = { h, m -> viewModel.setNotificationTime(h, m) },
                            onManageMaintenance = { showManageMaintenancePage = true },
                            onRemoveVehicle = {
                                activeVehicle?.id?.let {
                                    viewModel.deleteVehicle(it)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddVehicleDialog) {
        AddVehicleDialog(
            onDismiss = { if (vehicles?.isNotEmpty() == true) showAddVehicleDialog = false },
            onAdd = { mk, md, y, o, auto, onProgress, onComplete -> 
                viewModel.insertVehicle(mk, md, y, o, auto, onProgress) {
                    onComplete()
                }
            },
            onFinish = {
                showAddVehicleDialog = false
            }
        )
    }

    if (showAddMaintenanceDialog) {
        AddMaintenanceDialog(
            onDismiss = { showAddMaintenanceDialog = false },
            onAdd = { title, desc, recurrence, lastDone ->
                viewModel.addCustomMaintenance(title, desc, recurrence, lastDone)
                showAddMaintenanceDialog = false
            }
        )
    }
}

@Composable
fun VaultTopBar(
    vehicles: List<Vehicle>, 
    activeVehicle: Vehicle?, 
    onVehicleSelected: (Long) -> Unit, 
    onAddVehicle: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar, 
                    contentDescription = "Car Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = true }) {
                    Text(
                        text = activeVehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: "AutoVault", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Vehicle", tint = MaterialTheme.colorScheme.onBackground)
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text("${vehicle.year} ${vehicle.make} ${vehicle.model}") },
                                onClick = { onVehicleSelected(vehicle.id); expanded = false }
                            )
                        }
                        if (vehicles.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Add Vehicle", color = MaterialTheme.colorScheme.primary) },
                            onClick = { onAddVehicle(); expanded = false }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone, 
                        contentDescription = "Cloud Synced", 
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Local Sync", 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun VaultBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Garage") },
            label = { Text("Garage") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Analytics, contentDescription = "AI Expert") },
            label = { Text("AI Expert") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
