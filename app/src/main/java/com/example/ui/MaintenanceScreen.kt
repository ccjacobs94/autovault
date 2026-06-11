package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.room.UpcomingMaintenance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    maintenanceList: List<UpcomingMaintenance>,
    onBack: () -> Unit,
    onAdd: (title: String, desc: String, interval: Int, lastOdo: Int) -> Unit,
    onUpdate: (id: Long, title: String, desc: String, interval: Int, lastOdo: Int) -> Unit,
    onDelete: (id: Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<UpcomingMaintenance?>(null) }
    var itemToDelete by remember { mutableStateOf<UpcomingMaintenance?>(null) }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Tracked Maintenance?") },
            text = { Text("Are you sure you want to delete this tracked maintenance? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(itemToDelete!!.id)
                    itemToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracked Maintenance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        if (maintenanceList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No maintenance tracked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                items(maintenanceList) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { editItem = item }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { itemToDelete = item }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Next due at: ${item.expectedOdometer} mi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMaintenanceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { t, d, r, l -> 
                onAdd(t, d, r, l)
                showAddDialog = false
            }
        )
    }

    if (editItem != null) {
        AddMaintenanceDialog(
            initialTitle = editItem!!.title,
            initialDesc = editItem!!.description,
            initialRecurrence = editItem!!.recurrenceMiles,
            initialExpectedOdo = editItem!!.expectedOdometer,
            onDismiss = { editItem = null },
            onAdd = { t, d, r, l ->
                onUpdate(editItem!!.id, t, d, r, l)
                editItem = null
            }
        )
    }
}
