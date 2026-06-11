package com.example.data

import com.example.data.room.ServiceLog
import com.example.data.room.UpcomingMaintenance
import com.example.data.room.Vehicle
import kotlinx.serialization.Serializable

@Serializable
data class AppBackup(
    val vehicles: List<Vehicle>,
    val serviceLogs: List<ServiceLog>,
    val upcomingMaintenance: List<UpcomingMaintenance>
)
