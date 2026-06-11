package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.example.data.room.AppDatabase
import com.example.data.room.VaultRepository
import com.example.ui.VaultApp
import com.example.ui.VaultViewModel
import com.example.ui.VaultViewModelFactory
import com.example.ui.theme.MyApplicationTheme

import com.example.data.UserPreferencesRepository
import com.example.data.AutoBackupManager
import com.example.worker.WorkScheduler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private lateinit var autoBackupManager: AutoBackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        
        val db = AppDatabase.getDatabase(this)
        val repo = VaultRepository(db.vaultDao())
        val prefsRepo = UserPreferencesRepository(this)
        val factory = VaultViewModelFactory(repo, prefsRepo)
        val viewModel = ViewModelProvider(this, factory)[VaultViewModel::class.java]

        autoBackupManager = AutoBackupManager(this, repo)

        val time = prefsRepo.getNotificationTime()
        WorkScheduler.scheduleMaintenanceWork(this, time.first, time.second)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VaultApp(viewModel = viewModel, autoBackupManager = autoBackupManager)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save the backup when app goes to background
        GlobalScope.launch(Dispatchers.IO) {
            autoBackupManager.performBackup()
        }
    }
}
