package com.safepal.agent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safepal.agent.ui.ContentRecommendation

@Composable
fun RecommendationsCard(
    recommendations: List<ContentRecommendation>,
    onMovieClick: ((Int, String) -> Unit)? = null
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
                text = "Recomendaciones:",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (recommendations.isEmpty()) {
                Text(
                    text = "No se encontraron recomendaciones que coincidan con tus criterios.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                recommendations.forEach { recommendation ->
                    RecommendationItem(
                        recommendation = recommendation,
                        onMovieClick = onMovieClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}