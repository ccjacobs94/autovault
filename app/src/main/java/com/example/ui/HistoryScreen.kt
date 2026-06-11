package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.room.ServiceLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    logs: List<ServiceLog>,
    onUpdate: (ServiceLog) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No service history found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        var editingLog by remember { mutableStateOf<ServiceLog?>(null) }
        
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(logs) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingLog = log }
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(log.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        val dateString = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(log.dateMillis))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(dateString, style = MaterialTheme.typography.labelSmall)
                            Text("${log.odometer} mi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        
        if (editingLog != null) {
            EditServiceLogDialog(
                log = editingLog!!,
                onDismiss = { editingLog = null },
                onUpdate = { log ->
                    onUpdate(log)
                    editingLog = null
                },
                onDelete = {
                    onDelete(editingLog!!.id)
                    editingLog = null
                }
            )
        }
    }
}

@Composable
fun EditServiceLogDialog(
    log: ServiceLog,
    onDismiss: () -> Unit,
    onUpdate: (ServiceLog) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(log.title) }
    var description by remember { mutableStateOf(log.description) }
    var odometerStr by remember { mutableStateOf(log.odometer.toString()) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Service?") },
            text = { Text("Are you sure you want to delete this service log? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Service", modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.offset(x = 12.dp, y = (-12).dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = odometerStr,
                    onValueChange = { odometerStr = it },
                    label = { Text("Odometer (mi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val odo = odometerStr.toIntOrNull() ?: log.odometer
                    onUpdate(log.copy(title = title, description = description, odometer = odo))
                },
                enabled = title.isNotBlank() && odometerStr.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
