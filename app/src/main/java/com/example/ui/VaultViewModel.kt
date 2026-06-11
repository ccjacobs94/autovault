package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.data.room.ServiceLog
import com.example.data.room.UpcomingMaintenance
import com.example.data.room.VaultRepository
import com.example.data.UserPreferencesRepository
import com.example.data.room.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull

class VaultViewModel(
    private val repository: VaultRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {
    
    private val _milesPerWeek = MutableStateFlow(prefsRepo.getMilesPerWeek())
    val milesPerWeek: StateFlow<Int> = _milesPerWeek

    private val _notificationTime = MutableStateFlow(prefsRepo.getNotificationTime())
    val notificationTime: StateFlow<Pair<Int, Int>> = _notificationTime

    fun setMilesPerWeek(miles: Int) {
        prefsRepo.setMilesPerWeek(miles)
        _milesPerWeek.value = miles
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        prefsRepo.setNotificationTime(hour, minute)
        _notificationTime.value = Pair(hour, minute)
    }
    
    val allVehicles: StateFlow<List<Vehicle>?> = repository.allVehicles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _activeVehicleId = MutableStateFlow<Long?>(null)
    val activeVehicleId: StateFlow<Long?> = _activeVehicleId

    val activeVehicle = _activeVehicleId.flatMapLatest { id ->
        if (id != null) repository.getVehicleById(id) else kotlinx.coroutines.flow.flowOf(null)
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    val activeServiceLogs = _activeVehicleId.flatMapLatest { id ->
        if (id != null) repository.getServiceLogsForVehicle(id) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val activeMaintenance = _activeVehicleId.flatMapLatest { id ->
        if (id != null) repository.getUpcomingMaintenanceForVehicle(id) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val _expertResponse = MutableStateFlow<String?>(null)
    val expertResponse: StateFlow<String?> = _expertResponse

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    fun setActiveVehicle(id: Long) {
        _activeVehicleId.value = id
    }

    fun insertVehicle(make: String, model: String, year: Int, odometer: Int, autoSchedule: Boolean = false, onProgress: (String) -> Unit = {}, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            onProgress("Saving vehicle details...")
            val vId = repository.insertVehicle(Vehicle(make = make, model = model, year = year, currentOdometer = odometer))
            _activeVehicleId.value = vId
            if (autoSchedule) {
                generateAIbasedMaintenanceSchedule(vId, make, model, year, odometer, onProgress)
            } else {
                onProgress("Applying standard maintenance schedule...")
                generateStaticMaintenanceSchedule(vId, make, model, odometer)
            }
            onComplete()
        }
    }

    private suspend fun generateAIbasedMaintenanceSchedule(vehicleId: Long, make: String, model: String, year: Int, currentOdometer: Int, onProgress: (String) -> Unit) {
        onProgress("Searching for $year $make $model maintenance schedule...")
        val prompt = "Look up the manufacturer's recommended maintenance schedule for a $year $make $model. The current odometer is $currentOdometer miles. \n\nOutput ONLY a JSON array with nothing else. Each element should be an object with the following fields:\n" +
            "- 'title': (String) The name of the maintenance item (e.g. 'Oil Change', 'Spark Plugs')\n" +
            "- 'expectedOdometer': (Number) The EXACT odometer reading when this item is NEXT due, calculated based on the current odometer ($currentOdometer) and the interval.\n" +
            "- 'description': (String) A brief description of what is done.\n" +
            "- 'isDiyFriendly': (Boolean) True if a typical person can do it themselves, false otherwise."
        
        val systemInstruction = Content(role = "system", parts = listOf(Part(text = "You are a car mechanic expert. Always respond with pure JSON only.")))
        val tools = listOf(
            buildJsonObject {
                putJsonObject("googleSearch") {}
            }
        )
        
        val response = analyzeWithGeminiChat(
            history = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemInstruction,
            tools = tools
        )
        
        onProgress("Compiling information...")

        try {
            val element = Json { ignoreUnknownKeys = true }.parseToJsonElement(response.replace("```json", "").replace("```", "").trim())
            if (element is kotlinx.serialization.json.JsonArray) {
                onProgress("Applying maintenance plan to vehicle profile...")
                element.forEach { item ->
                    val json = item.jsonObject
                    val title = json["title"]?.jsonPrimitive?.content ?: "Unknown"
                    val expectedOdo = json["expectedOdometer"]?.jsonPrimitive?.content?.toIntOrNull() ?: (currentOdometer + 5000)
                    val description = json["description"]?.jsonPrimitive?.content ?: ""
                    val isDiy = json["isDiyFriendly"]?.jsonPrimitive?.booleanOrNull ?: false
                    
                    repository.insertUpcomingMaintenance(UpcomingMaintenance(
                        vehicleId = vehicleId,
                        title = title,
                        expectedOdometer = expectedOdo,
                        description = description,
                        isDiyFriendly = isDiy
                    ))
                }
            } else {
                generateStaticMaintenanceSchedule(vehicleId, make, model, currentOdometer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to static on failure
            generateStaticMaintenanceSchedule(vehicleId, make, model, currentOdometer)
        }
    }

    private suspend fun generateStaticMaintenanceSchedule(vehicleId: Long, make: String, model: String, currentOdometer: Int) {
        val modelLower = model.lowercase()
        val makeLower = make.lowercase()
        val isEv = makeLower in listOf("tesla", "rivian", "lucid", "polestar") || 
                   modelLower.contains("ev") || modelLower.contains("leaf") || 
                   modelLower.contains("bolt") || modelLower.contains("mach-e") || modelLower.contains("ioniq")

        if (!isEv) {
            repository.insertUpcomingMaintenance(UpcomingMaintenance(vehicleId = vehicleId, expectedOdometer = currentOdometer + 5000, title = "Oil Change", description = "Synthetic oil and filter", isDiyFriendly = true))
        }
        repository.insertUpcomingMaintenance(UpcomingMaintenance(vehicleId = vehicleId, expectedOdometer = currentOdometer + 15000, title = "Tire Rotation", description = "Rotate all 4 tires", isDiyFriendly = true))
        repository.insertUpcomingMaintenance(UpcomingMaintenance(vehicleId = vehicleId, expectedOdometer = currentOdometer + 45000, title = "Brake Pads Inspection", description = "Check front and rear pads", isDiyFriendly = false))
    }

    fun updateOdometer(odometer: Int) {
        val id = _activeVehicleId.value ?: return
        viewModelScope.launch {
            repository.updateVehicleOdometer(id, odometer)
        }
    }

    fun deleteVehicle(id: Long) {
        viewModelScope.launch {
            repository.deleteVehicle(id)
            if (_activeVehicleId.value == id) {
                _activeVehicleId.value = null
            }
        }
    }

    fun addServiceLog(title: String, desc: String, cost: Double, isDiy: Boolean, receiptImageUri: String? = null) {
        val id = _activeVehicleId.value ?: return
        val currentOdo = activeVehicle.value?.currentOdometer ?: 0
        viewModelScope.launch {
            repository.insertServiceLog(
                ServiceLog(
                    vehicleId = id,
                    dateMillis = System.currentTimeMillis(),
                    odometer = currentOdo,
                    title = title,
                    description = desc,
                    cost = cost,
                    receiptImageUri = receiptImageUri,
                    isDiy = isDiy
                )
            )
        }
    }

    fun updateServiceLog(log: ServiceLog) {
        viewModelScope.launch {
            repository.insertServiceLog(log)
        }
    }

    fun deleteServiceLog(logId: Long) {
        viewModelScope.launch {
            repository.deleteServiceLog(logId)
        }
    }

    fun handleLogService(serviceType: String, odometer: Int) {
        val id = _activeVehicleId.value ?: return
        
        viewModelScope.launch {
            repository.insertServiceLog(
                ServiceLog(
                    vehicleId = id,
                    dateMillis = System.currentTimeMillis(),
                    odometer = odometer,
                    title = serviceType,
                    description = "Logged $serviceType at $odometer miles",
                    cost = 0.0,
                    isDiy = true
                )
            )
            
            val currentVehicleInfo = activeVehicle.value
            if (currentVehicleInfo != null && currentVehicleInfo.currentOdometer < odometer) {
                repository.updateVehicleOdometer(id, odometer)
            } else if (currentVehicleInfo == null) {
                val vInfo = repository.getVehicleById(id).firstOrNull()
                if (vInfo != null && vInfo.currentOdometer < odometer) {
                    repository.updateVehicleOdometer(id, odometer)
                }
            }
            
            val upcoming = activeMaintenance.value.firstOrNull { it.title.equals(serviceType, ignoreCase = true) }
            val defaultRecurrence = when (serviceType.lowercase()) {
                "oil change" -> 5000
                "tire rotation" -> 10000
                "brake pad replacement" -> 30000
                "air filter" -> 15000
                "battery replacement" -> 50000
                else -> 10000
            }
            if (upcoming != null) {
                repository.insertUpcomingMaintenance(
                    upcoming.copy(expectedOdometer = odometer + upcoming.recurrenceMiles)
                )
            } else {
                repository.insertUpcomingMaintenance(
                   UpcomingMaintenance(
                       vehicleId = id,
                       expectedOdometer = odometer + defaultRecurrence,
                       title = serviceType,
                       description = "Next $serviceType",
                       isDiyFriendly = true,
                       recurrenceMiles = defaultRecurrence
                   )
                )
            }
        }
    }

    fun addCustomMaintenance(title: String, desc: String, recurrenceMiles: Int, lastDoneOdometer: Int, targetVehicleId: Long? = null) {
        val id = targetVehicleId ?: _activeVehicleId.value ?: return
        viewModelScope.launch {
            val existingList = repository.getUpcomingMaintenanceForVehicle(id).first()
            val duplicate = existingList.find { it.title.equals(title, ignoreCase = true) }
            if (duplicate != null) {
                // If it already exists, ignore to prevent duplicates
                return@launch
            }
            repository.insertUpcomingMaintenance(
                UpcomingMaintenance(
                    vehicleId = id,
                    expectedOdometer = lastDoneOdometer + recurrenceMiles,
                    title = title,
                    description = desc,
                    isDiyFriendly = true,
                    recurrenceMiles = recurrenceMiles
                )
            )
        }
    }

    fun updateOdometerForVehicle(targetVehicleId: Long, odometer: Int) {
        viewModelScope.launch {
            repository.updateVehicleOdometer(targetVehicleId, odometer)
        }
    }

    fun updateCustomMaintenance(id: Long, title: String, desc: String, recurrenceMiles: Int, lastDoneOdometer: Int) {
        val vehicleId = _activeVehicleId.value ?: return
        viewModelScope.launch {
            val existingList = repository.getUpcomingMaintenanceForVehicle(vehicleId).first()
            val duplicate = existingList.find { it.id != id && it.title.equals(title, ignoreCase = true) }
            if (duplicate != null) {
                return@launch
            }
            repository.insertUpcomingMaintenance(
                UpcomingMaintenance(
                    id = id,
                    vehicleId = vehicleId,
                    expectedOdometer = lastDoneOdometer + recurrenceMiles,
                    title = title,
                    description = desc,
                    isDiyFriendly = true,
                    recurrenceMiles = recurrenceMiles
                )
            )
        }
    }

    fun deleteMaintenance(id: Long) {
        viewModelScope.launch {
            repository.deleteUpcomingMaintenance(id)
        }
    }

    private val _chatMessages = MutableStateFlow<List<Content>>(emptyList())
    val chatMessages: StateFlow<List<Content>> = _chatMessages

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun sendChatMessage(message: String) {
        val allVs = allVehicles.value ?: emptyList()
        val allVehiclesContext = allVs.joinToString("\n") {
            "ID: ${it.id} - ${it.year} ${it.make} ${it.model} (Odo: ${it.currentOdometer} miles)"
        }
        val vehicle = activeVehicle.value
        val defaultVehicleId = vehicle?.id?.toString() ?: "None"
        
        val maintenance = activeMaintenance.value
        val maintenanceContext = if (maintenance.isEmpty()) {
            "No maintenance currently tracked."
        } else {
            maintenance.joinToString("\n") { 
                "- ${it.title} (due at ${it.expectedOdometer} mi)" 
            }
        }

        val currentContext = """
            You are an AI Car Expert. The user has the following vehicles:
            $allVehiclesContext
            
            Currently active vehicle ID is: $defaultVehicleId
            Currently tracked maintenance for active vehicle:
            $maintenanceContext
            
            You can help the user add tracked maintenance schedules, update their vehicle's odometer, or log a completed service (such as tire rotation, oil change, etc.) which adds it to their service history.
            If the user wants to add maintenance, update odometer, or log a completed service but it's ambiguous which vehicle they mean, ask them to clarify context before proceeding.
            A maintenance schedule requires a title, recurrence in miles, and last done odometer reading. 
            If the user wants to add maintenance but fields are missing, you should infer the missing fields based on standard recommendations for their vehicle, propose them (including all missing fields), and ask the user if it looks good.
            Once all fields and descriptions are confirmed by the user, output the following JSON block inside XML tags in your response to perform the action (you can add multiple items as a JSON array):
            <ADD_MAINTENANCE>
            [
              {"vehicleId": 1, "title": "...", "recurrenceMiles": 5000, "lastDoneOdometer": 40000, "description": "..."}
            ]
            </ADD_MAINTENANCE>
            
            If the user wants to update the odometer, output:
            <UPDATE_ODOMETER>
            [
              {"vehicleId": 1, "odometer": 45000}
            ]
            </UPDATE_ODOMETER>

            If the user has completed a service/maintenance item (e.g. they rotated their tires, changed the oil, or performed other service), you should log it in their service history. Output the following XML block to log a completed service (this adds a service log in their history, updates their vehicle's current odometer, and reschedules the next service automatically):
            <LOG_SERVICE>
            [
              {"vehicleId": 1, "serviceType": "...", "odometer": 45000}
            ]
            </LOG_SERVICE>
            
            Reply normally to the user confirming the actions taken.
            Your replies should be concise: keep them to no more than a 20-second read (around 60-80 words). If the user's question is complicated, you may provide a longer answer up to a 45-second read.
        """.trimIndent()
        
        val userContent = Content(role = "user", parts = listOf(Part(text = message)))
        _chatMessages.value = _chatMessages.value + userContent
        _isChatLoading.value = true
        
        viewModelScope.launch {
            val systemInstruction = Content(role = "system", parts = listOf(Part(text = currentContext)))
            val responseText = analyzeWithGeminiChat(_chatMessages.value, systemInstruction)
            
            var displayResponse = responseText
            
            fun extractJsonFromTags(tagStart: String, tagEnd: String): String? {
                if (responseText.contains(tagStart) && responseText.contains(tagEnd)) {
                    return responseText.substringAfter(tagStart).substringBefore(tagEnd).trim()
                }
                return null
            }
            
            val addMaintStr = extractJsonFromTags("<ADD_MAINTENANCE>", "</ADD_MAINTENANCE>")
            if (addMaintStr != null) {
                try {
                    val element = Json { ignoreUnknownKeys = true }.parseToJsonElement(addMaintStr)
                    val jsonArray = if (element is JsonArray) {
                        element
                    } else if (element is kotlinx.serialization.json.JsonObject) {
                        JsonArray(listOf(element))
                    } else {
                        JsonArray(emptyList())
                    }
                    
                    jsonArray.forEach { item ->
                        val json = item.jsonObject
                        val title = json["title"]?.jsonPrimitive?.content ?: ""
                        val recurrenceMiles = json["recurrenceMiles"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val lastDoneOdometer = json["lastDoneOdometer"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val description = json["description"]?.jsonPrimitive?.content ?: ""
                        val targetVehicleId = json["vehicleId"]?.jsonPrimitive?.content?.toLongOrNull()
                        
                        if (title.isNotEmpty()) {
                            addCustomMaintenance(title, description, recurrenceMiles, lastDoneOdometer, targetVehicleId)
                        }
                    }
                    displayResponse = displayResponse.replace("<ADD_MAINTENANCE>.*</ADD_MAINTENANCE>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val updateOdoStr = extractJsonFromTags("<UPDATE_ODOMETER>", "</UPDATE_ODOMETER>")
            if (updateOdoStr != null) {
                try {
                    val element = Json { ignoreUnknownKeys = true }.parseToJsonElement(updateOdoStr)
                    val jsonArray = if (element is JsonArray) element else if (element is kotlinx.serialization.json.JsonObject) JsonArray(listOf(element)) else JsonArray(emptyList())
                    jsonArray.forEach { item ->
                        val json = item.jsonObject
                        val vId = json["vehicleId"]?.jsonPrimitive?.content?.toLongOrNull()
                        val odometer = json["odometer"]?.jsonPrimitive?.content?.toIntOrNull()
                        if (vId != null && odometer != null) {
                            updateOdometerForVehicle(vId, odometer)
                        }
                    }
                    displayResponse = displayResponse.replace("<UPDATE_ODOMETER>.*</UPDATE_ODOMETER>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val logServiceStr = extractJsonFromTags("<LOG_SERVICE>", "</LOG_SERVICE>")
            if (logServiceStr != null) {
                try {
                    val element = Json { ignoreUnknownKeys = true }.parseToJsonElement(logServiceStr)
                    val jsonArray = if (element is JsonArray) {
                        element
                    } else if (element is kotlinx.serialization.json.JsonObject) {
                        JsonArray(listOf(element))
                    } else {
                        JsonArray(emptyList())
                    }
                    
                    jsonArray.forEach { item ->
                        val json = item.jsonObject
                        val serviceType = json["serviceType"]?.jsonPrimitive?.content ?: ""
                        val odo = json["odometer"]?.jsonPrimitive?.content?.toIntOrNull()
                        val targetVehicleId = json["vehicleId"]?.jsonPrimitive?.content?.toLongOrNull()
                        val activeId = targetVehicleId ?: _activeVehicleId.value
                        if (serviceType.isNotEmpty() && odo != null && activeId != null) {
                            if (activeId != _activeVehicleId.value) {
                                setActiveVehicle(activeId)
                            }
                            handleLogService(serviceType, odo)
                        }
                    }
                    displayResponse = displayResponse.replace("<LOG_SERVICE>.*</LOG_SERVICE>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val modelContent = Content(role = "model", parts = listOf(Part(text = displayResponse)))
            _chatMessages.value = _chatMessages.value + modelContent
            _isChatLoading.value = false
        }
    }

    private suspend fun analyzeWithGeminiChat(history: List<Content>, systemInstruction: Content, tools: List<kotlinx.serialization.json.JsonObject>? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext "API Key missing."
        
        val request = GenerateContentRequest(
            contents = history,
            systemInstruction = systemInstruction,
            tools = tools
        )
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response available."
        } catch (e: Exception) {
            "Error analyzing: ${e.message}"
        }
    }

    private suspend fun analyzeWithGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext "API Key missing."
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No insight available."
        } catch (e: Exception) {
            "Error analyzing: ${e.message}"
        }
    }
}

class VaultViewModelFactory(
    private val repository: VaultRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VaultViewModel(repository, prefsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
