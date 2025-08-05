package com.safepal.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import com.safepal.agent.ui.MovieRecommendationViewModel
import com.safepal.agent.ui.components.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieRecommendationScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDetail: (Int, String) -> Unit = { _, _ -> },
    viewModel: MovieRecommendationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración"
                        )
                    }
                    TextButton(
                        onClick = { viewModel.restartChat() }
                    ) {
                        Text("Reiniciar")
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.platformSelectionActive) {
                ChatInputSection(
                    inputText = uiState.inputText,
                    isInputEnabled = uiState.isInputEnabled && !uiState.isLoading,
                    onInputTextChange = viewModel::updateInputText,
                    onSendMessage = { viewModel.sendMessage() }
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageItem(
                        message = message,
                        onMovieClick = { movieId, type -> 
                            viewModel.onMovieClick(movieId, type, onNavigateToDetail)
                        }
                    )
                }

                if (uiState.platformSelectionActive) {
                    item {
                        PlatformSelectionCard(
                            platforms = uiState.availablePlatforms,
                            selectedPlatforms = uiState.selectedPlatforms,
                            onPlatformToggle = viewModel::togglePlatform,
                            onConfirm = viewModel::confirmPlatformSelection
                        )
                    }
                }


                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}