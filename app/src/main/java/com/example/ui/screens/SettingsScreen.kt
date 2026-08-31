package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.enhance.EnhanceEngineType
import com.example.viewmodel.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CameraViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKeyInput by remember { mutableStateOf(uiState.apiKey) }
    var showSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Gemini API Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.saveApiKey(apiKeyInput)
                    showSnackbar = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Key")
            }
            
            if (showSnackbar) {
                Text(
                    "Settings saved successfully.",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text("AI Enhance Engine", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pilih mesin yang dipakai untuk men-enhance foto di Preview. " +
                    "On-device = offline & gratis. AI Horde = AI generative gratis (butuh internet, tanpa key).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            EnhanceEngineType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = uiState.enhanceEngineType == type,
                            onClick = { viewModel.setEnhanceEngine(type) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.enhanceEngineType == type,
                        onClick = { viewModel.setEnhanceEngine(type) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(type.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            type.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Horde API key (opsional) — kosong = anonim & gratis
            if (uiState.enhanceEngineType == EnhanceEngineType.STABLE_HORDE) {
                Spacer(Modifier.height(8.dp))
                var hordeKeyInput by remember { mutableStateOf(uiState.hordeApiKey) }
                OutlinedTextField(
                    value = hordeKeyInput,
                    onValueChange = { hordeKeyInput = it },
                    label = { Text("AI Horde API Key (opsional)") },
                    supportingText = {
                        Text("Kosongkan untuk memakai akun anonim (gratis). Masukkan key bila punya akun agar prioritas lebih tinggi.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.saveHordeApiKey(hordeKeyInput)
                        showSnackbar = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Horde Key")
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Text("Pro Features Info", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "• AI Background Blur (Portrait)\n" +
                "• Auto Object Removal\n" +
                "• 100x Digital Zoom with Super Res\n" +
                "• Cinematic Log Profiling\n" +
                "\nAll features rely on the Gemini API key.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
