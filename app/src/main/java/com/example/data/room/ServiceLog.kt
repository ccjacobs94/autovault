package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "service_logs")
@Serializable
data class ServiceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateMillis: Long,
    val odometer: Int,
    val title: String,
    val description: String,
    val cost: Double = 0.0,
    val receiptImageUri: String? = null,
    val isDiy: Boolean = false
)
