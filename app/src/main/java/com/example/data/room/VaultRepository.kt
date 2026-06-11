package com.example.data.room

import kotlinx.coroutines.flow.Flow

class VaultRepository(private val dao: VaultDao) {
    val allVehicles: Flow<List<Vehicle>> = dao.getAllVehicles()

    fun getVehicleById(id: Long) = dao.getVehicleById(id)

    suspend fun insertVehicle(vehicle: Vehicle): Long = dao.insertVehicle(vehicle)

    suspend fun deleteVehicle(id: Long) = dao.deleteVehicle(id)

    suspend fun updateVehicleOdometer(id: Long, odometer: Int) {
        dao.updateVehicleOdometer(id, odometer)
    }

    fun getServiceLogsForVehicle(vehicleId: Long) = dao.getServiceLogsForVehicle(vehicleId)

    suspend fun insertServiceLog(log: ServiceLog) = dao.insertServiceLog(log)

    suspend fun deleteServiceLog(id: Long) = dao.deleteServiceLog(id)

    fun getUpcomingMaintenanceForVehicle(vehicleId: Long) = dao.getUpcomingMaintenanceForVehicle(vehicleId)

    suspend fun insertUpcomingMaintenance(maintenance: UpcomingMaintenance) = dao.insertUpcomingMaintenance(maintenance)

    suspend fun deleteUpcomingMaintenance(id: Long) = dao.deleteUpcomingMaintenance(id)

    suspend fun getAllVehiclesSnapshot() = dao.getAllVehiclesSnapshot()
    suspend fun getAllServiceLogsSnapshot() = dao.getAllServiceLogsSnapshot()
    suspend fun getAllMaintenanceSnapshot() = dao.getAllMaintenanceSnapshot()
}
