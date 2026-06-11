package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.UserPreferencesRepository
import com.example.data.room.AppDatabase
import kotlinx.coroutines.flow.first

class MaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.vaultDao()
        val prefs = UserPreferencesRepository(applicationContext)
        
        val milesPerWeek = prefs.getMilesPerWeek()
        val dailyMiles = milesPerWeek / 7

        // Update odometers
        val vehicles = dao.getAllVehicles().first()
        for (vehicle in vehicles) {
            val newOdometer = vehicle.currentOdometer + dailyMiles
            if (newOdometer > vehicle.currentOdometer) {
                 dao.updateVehicleOdometer(vehicle.id, newOdometer)
            }
            
            // Check for upcoming maintenance
            val upcoming = dao.getUpcomingMaintenanceForVehicle(vehicle.id).first()
            for (maintenance in upcoming) {
                if (maintenance.expectedOdometer - newOdometer <= 250) {
                    sendNotification(
                        applicationContext,
                        id = maintenance.id.toInt(),
                        title = "Service Due Soon: ${vehicle.make} ${vehicle.model}",
                        summary = "${maintenance.title} is due in ${maintenance.expectedOdometer - newOdometer} mi"
                    )
                }
            }
        }
        
        return Result.success()
    }

    private fun sendNotification(context: Context, id: Int, title: String, summary: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "maintenance_channel",
                "Maintenance Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "maintenance_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // fallback icon
            .setContentTitle(title)
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        nm.notify(id, builder.build())
    }
}
