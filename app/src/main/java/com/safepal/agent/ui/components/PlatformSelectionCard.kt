package com.safepal.agent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safepal.agent.ui.StreamingPlatform

@Composable
fun PlatformSelectionCard(
    platforms: List<StreamingPlatform>,
    selectedPlatforms: List<Int>,
    onPlatformToggle: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Selecciona tus plataformas de streaming:",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            platforms.forEach { platform ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedPlatforms.contains(platform.id),
                        onCheckedChange = { onPlatformToggle(platform.id) }
                    )
                    Text(
                        text = platform.name,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConfirm,
                enabled = selectedPlatforms.isNotEmpty(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Confirmar Selección")
            }
        }
    }
}