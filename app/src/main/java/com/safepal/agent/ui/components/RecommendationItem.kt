package com.safepal.agent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.safepal.agent.ui.ContentRecommendation
import com.safepal.agent.ui.extractYear
import java.util.Locale

@Composable
fun RecommendationItem(
    recommendation: ContentRecommendation,
    onMovieClick: ((Int, String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMovieClick?.invoke(recommendation.id, recommendation.type) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp)
        ) {
            // Poster image
            AsyncImage(
                model = recommendation.posterUrl,
                contentDescription = recommendation.title,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title with year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = recommendation.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    extractYear(recommendation.releaseDate)?.let { year ->
                        Text(
                            text = "($year)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rating and type
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ ${String.format(Locale.getDefault(), "%.1f", recommendation.voteAverage)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = if (recommendation.type == "tv") "Serie" else "Película",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Platforms
                if (recommendation.platforms.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📺 ${recommendation.platforms.joinToString(", ") { it.name }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = recommendation.overview,
                    fontSize = 12.sp,
                    maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}