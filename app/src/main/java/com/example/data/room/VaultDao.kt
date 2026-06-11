package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun getVehicleById(id: Long): Flow<Vehicle?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: Long)

    @Query("UPDATE vehicles SET currentOdometer = :odometer WHERE id = :id")
    suspend fun updateVehicleOdometer(id: Long, odometer: Int)

    @Query("SELECT * FROM service_logs WHERE vehicleId = :vehicleId ORDER BY dateMillis DESC")
    fun getServiceLogsForVehicle(vehicleId: Long): Flow<List<ServiceLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLog(log: ServiceLog)

    @Query("DELETE FROM service_logs WHERE id = :id")
    suspend fun deleteServiceLog(id: Long)

    @Query("SELECT * FROM upcoming_maintenance WHERE vehicleId = :vehicleId ORDER BY expectedOdometer ASC")
    fun getUpcomingMaintenanceForVehicle(vehicleId: Long): Flow<List<UpcomingMaintenance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpcomingMaintenance(maintenance: UpcomingMaintenance)

    @Query("DELETE FROM upcoming_maintenance WHERE id = :id")
    suspend fun deleteUpcomingMaintenance(id: Long)

    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    suspend fun getAllVehiclesSnapshot(): List<Vehicle>

    @Query("SELECT * FROM service_logs ORDER BY dateMillis DESC")
    suspend fun getAllServiceLogsSnapshot(): List<ServiceLog>

    @Query("SELECT * FROM upcoming_maintenance ORDER BY expectedOdometer ASC")
    suspend fun getAllMaintenanceSnapshot(): List<UpcomingMaintenance>
}
