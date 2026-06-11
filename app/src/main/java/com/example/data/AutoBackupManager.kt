package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.room.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter

class AutoBackupManager(private val context: Context, private val repository: VaultRepository) {
    private val prefs = context.getSharedPreferences("autobackup_prefs", Context.MODE_PRIVATE)

    fun setBackupFolder(uri: Uri) {
        prefs.edit().putString("backup_folder_uri", uri.toString()).apply()
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    fun getBackupFolder(): Uri? {
        return prefs.getString("backup_folder_uri", null)?.let { Uri.parse(it) }
    }

    suspend fun performBackup() {
        val uri = getBackupFolder() ?: return
        withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, uri) ?: return@withContext
                var file = folder.findFile("autovault_backup.json")
                if (file == null) {
                    file = folder.createFile("application/json", "autovault_backup.json")
                }
                
                file?.uri?.let { fileUri ->
                    val snapshot = AppBackup(
                        vehicles = repository.getAllVehiclesSnapshot(),
                        serviceLogs = repository.getAllServiceLogsSnapshot(),
                        upcomingMaintenance = repository.getAllMaintenanceSnapshot()
                    )
                    val jsonStr = Json.encodeToString(snapshot)
                    
                    context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream)
                        writer.write(jsonStr)
                        writer.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
