package com.safepal.agent.agents.movie

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("platform_selection")
data class PlatformSelectionResponse(
    val message: String,
    val platforms: List<StreamingPlatformInfo>
)

@Serializable
@SerialName("streaming_platform")
data class StreamingPlatformInfo(
    val id: Int,
    val name: String
)

@Serializable
@SerialName("movie_recommendations")
data class MovieRecommendationsResponse(
    val message: String,
    val recommendations: List<MovieInfo>
)

@Serializable
@SerialName("movie_details")
data class MovieDetailsResponse(
    val message: String,
    val movie: DetailedMovieInfo,
    val similar: List<MovieInfo>
)

@Serializable
@SerialName("search_results")
data class SearchResultsResponse(
    val message: String,
    val results: List<MovieInfo>
)

@Serializable
@SerialName("movie_info")
data class MovieInfo(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val type: String
)

@Serializable
@SerialName("detailed_movie_info")
data class DetailedMovieInfo(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val type: String,
    val runtime: Int?,
    val genres: List<String>
)

@Serializable
@SerialName("movie_grid_response")
data class MovieGridResponse(
    val message: String,
    val movies: List<MovieInfo>,
    val composeCode: String,
    val uiType: String // "grid", "list", "detailed"
)

@Serializable
@SerialName("error_response")
data class ErrorResponse(
    val message: String,
    val errorType: String? = null
)
