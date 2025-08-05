package com.safepal.agent.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TMDBClient(private val apiKey: String) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val baseUrl = "https://api.themoviedb.org/3"

    suspend fun getPopularMovies(page: Int = 1): MovieResponse {
        return client.get("$baseUrl/movie/popular") {
            parameter("api_key", apiKey)
            parameter("page", page)
        }.body()
    }

    suspend fun getPopularTVShows(page: Int = 1): TVResponse {
        return client.get("$baseUrl/tv/popular") {
            parameter("api_key", apiKey)
            parameter("page", page)
        }.body()
    }

    suspend fun discoverMovies(
        withWatchProviders: String? = null,
        watchRegion: String = "US",
        sortBy: String = "popularity.desc",
        withGenres: String? = null,
        page: Int = 1
    ): MovieResponse {
        return client.get("$baseUrl/discover/movie") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            withWatchProviders?.let { parameter("with_watch_providers", it) }
            withGenres?.let { parameter("with_genres", it) }
            parameter("watch_region", watchRegion)
        }.body()
    }

    suspend fun discoverTVShows(
        withWatchProviders: String? = null,
        watchRegion: String = "US",
        sortBy: String = "popularity.desc",
        withGenres: String? = null,
        page: Int = 1
    ): TVResponse {
        return client.get("$baseUrl/discover/tv") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            withWatchProviders?.let { parameter("with_watch_providers", it) }
            withGenres?.let { parameter("with_genres", it) }
            parameter("watch_region", watchRegion)
        }.body()
    }

    suspend fun getWatchProviders(type: String = "movie", movieOrTvId: Int): WatchProvidersResponse {
        return client.get("$baseUrl/$type/$movieOrTvId/watch/providers") {
            parameter("api_key", apiKey)
        }.body()
    }

    suspend fun discoverMoviesWithRegion(
        withWatchProviders: String? = null,
        watchRegion: String = "US",
        language: String = "en-US",
        sortBy: String = "popularity.desc",
        withGenres: String? = null,
        page: Int = 1
    ): MovieResponse {
        return client.get("$baseUrl/discover/movie") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            parameter("language", language)
            withWatchProviders?.let { parameter("with_watch_providers", it) }
            withGenres?.let { parameter("with_genres", it) }
            parameter("watch_region", watchRegion)
        }.body()
    }

    suspend fun discoverTVShowsWithRegion(
        withWatchProviders: String? = null,
        watchRegion: String = "US",
        language: String = "en-US", 
        sortBy: String = "popularity.desc",
        withGenres: String? = null,
        page: Int = 1
    ): TVResponse {
        return client.get("$baseUrl/discover/tv") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            parameter("language", language)
            withWatchProviders?.let { parameter("with_watch_providers", it) }
            withGenres?.let { parameter("with_genres", it) }
            parameter("watch_region", watchRegion)
        }.body()
    }

    suspend fun getMovieRecommendations(movieId: Int, page: Int = 1): MovieResponse {
        return client.get("$baseUrl/movie/$movieId/recommendations") {
            parameter("api_key", apiKey)
            parameter("page", page)
        }.body()
    }

    suspend fun getTVRecommendations(tvId: Int, page: Int = 1): TVResponse {
        return client.get("$baseUrl/tv/$tvId/recommendations") {
            parameter("api_key", apiKey)
            parameter("page", page)
        }.body()
    }

    suspend fun searchMovies(query: String, page: Int = 1): MovieResponse {
        return client.get("$baseUrl/search/movie") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
        }.body()
    }

    suspend fun searchTVShows(query: String, page: Int = 1): TVResponse {
        return client.get("$baseUrl/search/tv") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
        }.body()
    }

    suspend fun getMovieDetails(movieId: Int, language: String = "en-US"): MovieDetails {
        return client.get("$baseUrl/movie/$movieId") {
            parameter("api_key", apiKey)
            parameter("language", language)
        }.body()
    }

    suspend fun getTVDetails(tvId: Int, language: String = "en-US"): TVDetails {
        return client.get("$baseUrl/tv/$tvId") {
            parameter("api_key", apiKey)
            parameter("language", language)
        }.body()
    }

    suspend fun getMovieGenres(language: String = "en-US"): GenreResponse {
        return client.get("$baseUrl/genre/movie/list") {
            parameter("api_key", apiKey)
            parameter("language", language)
        }.body()
    }

    suspend fun getTVGenres(language: String = "en-US"): GenreResponse {
        return client.get("$baseUrl/genre/tv/list") {
            parameter("api_key", apiKey)
            parameter("language", language)
        }.body()
    }
}

@Serializable
data class MovieResponse(
    val page: Int,
    val results: List<Movie>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int
)

@Serializable
data class TVResponse(
    val page: Int,
    val results: List<TVShow>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int
)

@Serializable
data class Movie(
    val id: Int,
    val title: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("release_date") val releaseDate: String?,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList()
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

@Serializable
data class TVShow(
    val id: Int,
    val name: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("first_air_date") val firstAirDate: String?,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList()
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

@Serializable
data class WatchProvidersResponse(
    val id: Int,
    val results: Map<String, WatchProviderRegion> = emptyMap()
)

@Serializable
data class WatchProviderRegion(
    val link: String? = null,
    val flatrate: List<WatchProvider>? = null,
    val rent: List<WatchProvider>? = null,
    val buy: List<WatchProvider>? = null
)

@Serializable
data class WatchProvider(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String?,
    @SerialName("display_priority") val displayPriority: Int
) {
    fun getLogoUrl(): String? = logoPath?.let { "https://image.tmdb.org/t/p/w92$it" }
}

data class StreamingPlatform(
    val id: Int,
    val name: String,
    val logoUrl: String?
) {
    companion object {
        val POPULAR_PLATFORMS = listOf(
            StreamingPlatform(8, "Netflix", null),
            StreamingPlatform(9, "Amazon Prime Video", null),
            StreamingPlatform(337, "Disney Plus", null),
            StreamingPlatform(384, "HBO Max", null),
            StreamingPlatform(15, "Hulu", null)
        )
    }
}

// Data classes for detailed movie/TV information
@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class ProductionCompany(
    val id: Int,
    val name: String,
    @SerialName("logo_path") val logoPath: String?,
    @SerialName("origin_country") val originCountry: String
)

@Serializable
data class ProductionCountry(
    @SerialName("iso_3166_1") val iso: String,
    val name: String
)

@Serializable
data class SpokenLanguage(
    @SerialName("english_name") val englishName: String,
    @SerialName("iso_639_1") val iso: String,
    val name: String
)

@Serializable
data class BelongsToCollection(
    val id: Int,
    val name: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?
)

@Serializable
data class MovieDetails(
    val adult: Boolean = false,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("belongs_to_collection") val belongsToCollection: BelongsToCollection?,
    val budget: Long = 0,
    val genres: List<Genre> = emptyList(),
    val homepage: String?,
    val id: Int,
    @SerialName("imdb_id") val imdbId: String?,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String,
    @SerialName("original_title") val originalTitle: String,
    val overview: String,
    val popularity: Double,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("production_companies") val productionCompanies: List<ProductionCompany> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<ProductionCountry> = emptyList(),
    @SerialName("release_date") val releaseDate: String?,
    val revenue: Long = 0,
    val runtime: Int?,
    @SerialName("spoken_languages") val spokenLanguages: List<SpokenLanguage> = emptyList(),
    val status: String,
    val tagline: String?,
    val title: String,
    val video: Boolean = false,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("vote_count") val voteCount: Int
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    fun getGenreNames(): List<String> = genres.map { it.name }
}

@Serializable
data class TVDetails(
    val adult: Boolean = false,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("created_by") val createdBy: List<Creator> = emptyList(),
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("first_air_date") val firstAirDate: String?,
    val genres: List<Genre> = emptyList(),
    val homepage: String?,
    val id: Int,
    @SerialName("in_production") val inProduction: Boolean = false,
    val languages: List<String> = emptyList(),
    @SerialName("last_air_date") val lastAirDate: String?,
    val name: String,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int,
    @SerialName("number_of_seasons") val numberOfSeasons: Int,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String,
    @SerialName("original_name") val originalName: String,
    val overview: String,
    val popularity: Double,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("production_companies") val productionCompanies: List<ProductionCompany> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<ProductionCountry> = emptyList(),
    @SerialName("spoken_languages") val spokenLanguages: List<SpokenLanguage> = emptyList(),
    val status: String,
    val tagline: String?,
    val type: String,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("vote_count") val voteCount: Int
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    fun getGenreNames(): List<String> = genres.map { it.name }
}

@Serializable
data class Creator(
    val id: Int,
    @SerialName("credit_id") val creditId: String,
    val name: String,
    val gender: Int,
    @SerialName("profile_path") val profilePath: String?
)

@Serializable
data class GenreResponse(
    val genres: List<Genre>
)
