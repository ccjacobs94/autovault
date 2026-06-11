package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "upcoming_maintenance")
@Serializable
data class UpcomingMaintenance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val expectedOdometer: Int,
    val title: String,
    val description: String,
    val isDiyFriendly: Boolean,
    val recurrenceMiles: Int = 10000
)
