package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

enum class OnboardingStep {
    ASK_MAKE,
    WAIT_MAKE,
    ASK_MODEL,
    WAIT_MODEL,
    ASK_YEAR,
    WAIT_YEAR,
    ASK_ODOMETER,
    WAIT_ODOMETER,
    ASK_AUTO_SCHEDULE,
    WAIT_AUTO_SCHEDULE,
    SAVING,
    SUCCESS,
    FINISHED
}

data class OnboardingMessage(val text: String, val isUser: Boolean)

@Composable
fun OnboardingChat(onAdd: (make: String, model: String, year: Int, odometer: Int, autoSchedule: Boolean, onProgress: (String) -> Unit, onComplete: () -> Unit) -> Unit, onFinish: () -> Unit = {}) {
    var step by remember { mutableStateOf(OnboardingStep.ASK_MAKE) }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(0) }
    var odometer by remember { mutableStateOf(0) }
    var autoSchedule by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<OnboardingMessage>() }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var loadingText by remember { mutableStateOf("") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(step) {
        when (step) {
            OnboardingStep.ASK_MAKE -> {
                messages.add(OnboardingMessage("Let's add your vehicle. What make is it? (e.g. Toyota, Ford)", isUser = false))
                step = OnboardingStep.WAIT_MAKE
            }
            OnboardingStep.ASK_MODEL -> {
                messages.add(OnboardingMessage("Great. What model is your $make? (e.g. Camry, F-150)", isUser = false))
                step = OnboardingStep.WAIT_MODEL
            }
            OnboardingStep.ASK_YEAR -> {
                messages.add(OnboardingMessage("What year is it?", isUser = false))
                step = OnboardingStep.WAIT_YEAR
            }
            OnboardingStep.ASK_ODOMETER -> {
                messages.add(OnboardingMessage("Almost done! What's the current odometer reading in miles?", isUser = false))
                step = OnboardingStep.WAIT_ODOMETER
            }
            OnboardingStep.ASK_AUTO_SCHEDULE -> {
                messages.add(OnboardingMessage("Would you like me to look up the manufacturer's maintenance schedule for your $year $make $model and automatically add it for you? (Yes/No)", isUser = false))
                step = OnboardingStep.WAIT_AUTO_SCHEDULE
            }
            OnboardingStep.SAVING -> {
                keyboardController?.hide()
                onAdd(make, model, year, odometer, autoSchedule, { progress ->
                    loadingText = progress
                }, {
                    step = OnboardingStep.SUCCESS
                })
            }
            OnboardingStep.SUCCESS -> {
                messages.add(OnboardingMessage("Success! Your car is ready.", isUser = false))
            }
            OnboardingStep.FINISHED -> {
                onFinish()
            }
            else -> {} // Waiting states
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { message ->
                val isUser = message.isUser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = if (isUser) 24.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 24.dp
                                )
                            )
                            .background(
                                if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == OnboardingStep.SAVING) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (step == OnboardingStep.SUCCESS) {
                    Button(
                        onClick = { step = OnboardingStep.FINISHED },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OK")
                    }
                } else if (step == OnboardingStep.WAIT_AUTO_SCHEDULE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                keyboardController?.hide()
                                messages.add(OnboardingMessage("No", isUser = true))
                                autoSchedule = false
                                step = OnboardingStep.SAVING
                            },
                            modifier = Modifier.weight(1.5f).height(48.dp)
                        ) {
                            Text("No, standard schedule")
                        }
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                messages.add(OnboardingMessage("Yes", isUser = true))
                                autoSchedule = true
                                step = OnboardingStep.SAVING
                            },
                            modifier = Modifier.weight(1.5f).height(48.dp)
                        ) {
                            Text("Yes, use AI lookup")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type your answer...") },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            singleLine = true,
                            enabled = step != OnboardingStep.FINISHED
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val input = textInput.trim()
                                if (input.isNotBlank()) {
                                    messages.add(OnboardingMessage(input, isUser = true))
                                    textInput = ""
                                    when (step) {
                                        OnboardingStep.WAIT_MAKE -> {
                                            make = input
                                            step = OnboardingStep.ASK_MODEL
                                        }
                                        OnboardingStep.WAIT_MODEL -> {
                                            model = input
                                            step = OnboardingStep.ASK_YEAR
                                        }
                                        OnboardingStep.WAIT_YEAR -> {
                                            val parsedYear = input.toIntOrNull()
                                            if (parsedYear != null && parsedYear in 1900..2100) {
                                                year = parsedYear
                                                step = OnboardingStep.ASK_ODOMETER
                                            } else {
                                                messages.add(OnboardingMessage("Please enter a valid year (e.g. 2018).", isUser = false))
                                            }
                                        }
                                        OnboardingStep.WAIT_ODOMETER -> {
                                            val parsedOdo = input.replace(",", "").toIntOrNull()
                                            if (parsedOdo != null && parsedOdo >= 0) {
                                                odometer = parsedOdo
                                                step = OnboardingStep.ASK_AUTO_SCHEDULE
                                            } else {
                                                messages.add(OnboardingMessage("Please enter a valid number for odometer.", isUser = false))
                                            }
                                        }
                                        OnboardingStep.WAIT_AUTO_SCHEDULE -> {
                                            autoSchedule = input.trim().lowercase().startsWith("y")
                                            step = OnboardingStep.SAVING
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (step != OnboardingStep.FINISHED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            enabled = step != OnboardingStep.FINISHED
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
