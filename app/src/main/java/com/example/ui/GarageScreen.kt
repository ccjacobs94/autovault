package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.room.UpcomingMaintenance
import com.example.data.room.Vehicle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    isExpanded: Boolean,
    vehicle: Vehicle?,
    activeMaintenance: List<UpcomingMaintenance>,
    isAnalyzing: Boolean,
    onUpdateOdo: (Int) -> Unit,
    onLogService: (String, Int) -> Unit,
    onAddReceipt: (String) -> Unit
) {
    if (vehicle == null) return
    
    var showOdoDialog by remember { mutableStateOf(false) }
    var showLogServiceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        VehicleStatusCard(vehicle, activeMaintenance, onClick = { showOdoDialog = true })
        
        QuickActionsGrid(onAddReceipt, onLogMileageClick = { showOdoDialog = true })
        
        UpcomingMaintenanceSection(activeMaintenance, vehicle)
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { showLogServiceDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Log Service", modifier = Modifier.padding(6.dp), fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLogServiceDialog) {
        var selectedService by remember { mutableStateOf("") }
        var odoStr by remember { mutableStateOf(vehicle.currentOdometer.toString()) }
        
        AlertDialog(
            onDismissRequest = { showLogServiceDialog = false },
            title = { Text("Log a Service") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val serviceOptions = (activeMaintenance.map { it.title } + "Other").distinct()
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedService,
                            onValueChange = { selectedService = it },
                            label = { Text("Service Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        if (expanded) {
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                serviceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedService = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = odoStr,
                        onValueChange = { odoStr = it },
                        label = { Text("Odometer (mi)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val odo = odoStr.toIntOrNull() ?: vehicle.currentOdometer
                        onLogService(selectedService, odo)
                        showLogServiceDialog = false
                    },
                    enabled = selectedService.isNotBlank() && odoStr.isNotBlank()
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogServiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOdoDialog) {
        var odoStr by remember { mutableStateOf(vehicle.currentOdometer.toString()) }
        AlertDialog(
            onDismissRequest = { showOdoDialog = false },
            title = { Text("Update Odometer") },
            text = { OutlinedTextField(value = odoStr, onValueChange = { odoStr = it }) },
            confirmButton = {
                TextButton(onClick = { odoStr.toIntOrNull()?.let { onUpdateOdo(it) }; showOdoDialog = false }) { Text("Update") }
            }
        )
    }
}

@Composable
fun VehicleStatusCard(vehicle: Vehicle, maintenance: List<UpcomingMaintenance>, onClick: () -> Unit) {
    val urgentService = maintenance.firstOrNull { it.expectedOdometer - vehicle.currentOdometer <= 500 }
    val nextService = maintenance.firstOrNull()
    val nextOdo = nextService?.expectedOdometer ?: (vehicle.currentOdometer + 5000)
    val milesToNext = nextOdo - vehicle.currentOdometer
    val progress = if (maintenance.isEmpty()) 1f else (1f - (milesToNext.toFloat() / 5000f)).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("ACTIVE VEHICLE", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    Text("${vehicle.year} ${vehicle.make} ${vehicle.model}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Odometer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%,d".format(vehicle.currentOdometer), fontSize = 32.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("mi", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Next Service", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    val serviceColor = if (urgentService != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Text("${"%,d".format(nextOdo)} mi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = serviceColor)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
fun QuickActionsGrid(onAddReceipt: (String) -> Unit, onLogMileageClick: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onAddReceipt(uri.toString())
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionItem(icon = Icons.Default.ReceiptLong, label = "Scan Receipt", modifier = Modifier.weight(1f)) {
            launcher.launch("image/*")
        }
        ActionItem(icon = Icons.Default.EditRoad, label = "Log Mileage", modifier = Modifier.weight(1f)) {
            onLogMileageClick()
        }
    }
}

@Composable
fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}


@Composable
fun UpcomingMaintenanceSection(maintenance: List<UpcomingMaintenance>, vehicle: Vehicle) {
    val urgent = maintenance.filter { (it.expectedOdometer - vehicle.currentOdometer) <= 500 }
    
    if (urgent.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("You're good!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                Text("No maintenance needed soon.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Upcoming Maintenance",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        )
        urgent.forEach { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${item.expectedOdometer} mi", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val remainingMiles = item.expectedOdometer - vehicle.currentOdometer
                    val progressColor = when {
                        remainingMiles < 0 -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
                        remainingMiles <= 250 -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Amber
                        else -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
                    }
                    val distanceDone = (vehicle.currentOdometer - (item.expectedOdometer - item.recurrenceMiles)).toFloat()
                    val progress = (distanceDone / item.recurrenceMiles.toFloat()).coerceIn(0f, 1f)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (remainingMiles < 0) "Overdue by ${-remainingMiles} mi" else "Due in $remainingMiles mi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (item.isDiyFriendly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (item.isDiyFriendly) "DIY Friendly" else "Mechanic Recommended",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isDiyFriendly) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
