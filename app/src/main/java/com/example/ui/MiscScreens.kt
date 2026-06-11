package com.example.ui

import android.net.Uri
import com.example.data.AutoBackupManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.room.ServiceLog
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch

@Composable
fun DocumentsScreen(logs: List<ServiceLog>) {
    val logsWithReceipts = logs.filter { it.receiptImageUri != null }
    if (logsWithReceipts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Digital Invoices & Receipts", style = MaterialTheme.typography.titleLarge)
                Text("Any receipts linked to service logs will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logsWithReceipts) { log ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = "Receipt")
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(log.title, style = MaterialTheme.typography.titleMedium)
                            Text("URI: ${log.receiptImageUri}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    logs: List<ServiceLog>,
    autoBackupManager: AutoBackupManager? = null,
    milesPerWeek: Int = 250,
    onMilesPerWeekChange: (Int) -> Unit = {},
    notificationTime: Pair<Int, Int> = Pair(8, 0),
    onNotificationTimeChange: (hour: Int, minute: Int) -> Unit = {_,_->},
    onManageMaintenance: () -> Unit,
    onRemoveVehicle: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showRemoveDialog by remember { mutableStateOf(false) }

    var milesText by remember { mutableStateOf(milesPerWeek.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream)
                        writer.write("{ \"logsCount\": ${logs.size}, \"exportedAt\": \"${System.currentTimeMillis()}\" }")
                        writer.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val autoBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { 
            autoBackupManager?.setBackupFolder(it)
        }
    }

    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onNotificationTimeChange(hourOfDay, minute)
                com.example.worker.WorkScheduler.scheduleMaintenanceWork(context, hourOfDay, minute)
            },
            notificationTime.first,
            notificationTime.second,
            false // 12 hour format
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        LazyColumn(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
            
            item {
                OutlinedTextField(
                    value = milesText,
                    onValueChange = { 
                        milesText = it
                        it.toIntOrNull()?.let { miles -> onMilesPerWeekChange(miles) }
                    },
                    label = { Text("Average Miles Driven Per Week") },
                    singleLine = true
                )
            }
            
            item {
                val timeStr = String.format("%02d:%02d", notificationTime.first, notificationTime.second)
                Button(onClick = { timePickerDialog.show() }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Alert Time: $timeStr")
                }
            }

            item {
                Button(onClick = onManageMaintenance, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Manage Tracked Maintenance")
                }
            }
            item {
                Button(onClick = { exportLauncher.launch("autovault_backup.json") }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Export Data Manually")
                }
            }
            item {
                Button(onClick = { autoBackupLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Set Auto-Backup Folder")
                }
            }
            item {
                Button(
                    onClick = { showRemoveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("Remove Current Vehicle")
                }
            }
            item {
                Text("Select a folder to enable auto-backup on close.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Vehicle") },
            text = { Text("Are you sure you want to remove this vehicle? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemoveVehicle()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddVehicleDialog(onDismiss: () -> Unit, onAdd: (make: String, model: String, year: Int, odometer: Int, autoSchedule: Boolean, onProgress: (String) -> Unit, onComplete: () -> Unit) -> Unit, onFinish: () -> Unit = {}) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Add Vehicle", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Text("X", fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    com.example.ui.OnboardingChat(onAdd = onAdd, onFinish = onFinish)
                }
            }
        }
    }
}

val CAR_MAKES = listOf("Acura", "Alfa Romeo", "Aston Martin", "Audi", "BMW", "Bentley", "Buick", "Cadillac", "Chevrolet", "Chrysler", "Dodge", "Ferrari", "FIAT", "Ford", "GMC", "Genesis", "Honda", "Hyundai", "INFINITI", "Jaguar", "Jeep", "Kia", "Lamborghini", "Land Rover", "Lexus", "Lincoln", "Lucid", "Maserati", "Mazda", "McLaren", "Mercedes-Benz", "MINI", "Mitsubishi", "Nissan", "Polestar", "Porsche", "Ram", "Rivian", "Rolls-Royce", "Subaru", "Tesla", "Toyota", "Volkswagen", "Volvo")

val CAR_YEARS = (1990..2026).map { it.toString() }.reversed()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        
        val filteredOptions = options.filter { it.contains(value, ignoreCase = true) }
        
        if (filteredOptions.isNotEmpty() && expanded) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredOptions.take(15).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onAdd: (make: String, model: String, year: Int, odometer: Int, autoSchedule: Boolean, onProgress: (String) -> Unit, onComplete: () -> Unit) -> Unit, onFinish: () -> Unit = {}) {
    com.example.ui.OnboardingChat(onAdd = onAdd, onFinish = onFinish)
}

@Composable
fun AddMaintenanceDialog(
    initialTitle: String = "",
    initialDesc: String = "",
    initialRecurrence: Int? = null,
    initialExpectedOdo: Int? = null,
    onDismiss: () -> Unit,
    onAdd: (title: String, desc: String, recurrenceMiles: Int, lastDoneOdo: Int) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var desc by remember { mutableStateOf(initialDesc) }
    var recurrenceStr by remember { mutableStateOf(initialRecurrence?.toString() ?: "") }
    var lastDoneStr by remember { mutableStateOf(if (initialExpectedOdo != null && initialRecurrence != null) (initialExpectedOdo - initialRecurrence).toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) "Track Maintenance" else "Edit Tracked Maintenance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Service Name (e.g. Brake Flush)") })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                OutlinedTextField(value = recurrenceStr, onValueChange = { recurrenceStr = it }, label = { Text("Recurrence (Miles)") })
                OutlinedTextField(value = lastDoneStr, onValueChange = { lastDoneStr = it }, label = { Text("Last Done at (Odometer)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val recurrence = recurrenceStr.toIntOrNull() ?: 5000
                val lastDone = lastDoneStr.toIntOrNull() ?: 0
                onAdd(title, desc, recurrence, lastDone)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

