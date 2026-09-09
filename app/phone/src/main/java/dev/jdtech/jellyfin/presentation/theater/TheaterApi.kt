package dev.jdtech.jellyfin.presentation.theater

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

const val THEATER_BASE = "http://100.99.195.85:8181"

/** A single downloadable torrent belonging to a movie search result. */
@Serializable
data class TheaterTorrent(
    val quality: String? = null,
    val type: String? = null,
    val seeds: Int = 0,
    val size: String? = null,
    val hash: String = "",
)

@Serializable
data class TheaterMovie(
    val title: String = "",
    val year: Int? = null,
    val rating: Double? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
    // Older theater servers omit the ownership fields entirely.
    val owned: Boolean = false,
    val folder: String? = null,
    val poster: String? = null,
    val torrents: List<TheaterTorrent> = emptyList(),
)

@Serializable private data class MovieSearchResponse(val results: List<TheaterMovie> = emptyList())

@Serializable
data class TheaterTvResult(
    val title: String = "",
    val seeds: Int = 0,
    val size: String? = null,
    val hash: String = "",
    val quality: String? = null,
)

@Serializable
private data class TvSearchResponse(val results: List<TheaterTvResult> = emptyList())

/** A show folder the theater server already has on disk. */
@Serializable
data class TheaterTvSeries(
    val name: String = "",
    val folder: String = "",
    // Episode files on disk, and how many season folders they span.
    val count: Int = 0,
    val seasons: Int = 0,
    val poster: String? = null,
    // Null when the server could not resolve the folder name to a TMDB show.
    val tmdb: Int? = null,
)

@Serializable private data class TvLibraryResponse(val series: List<TheaterTvSeries> = emptyList())

/** SimKL watch progress for a show. Absent when the show is not on any list. */
@Serializable
data class TheaterSimklProgress(
    val status: String? = null,
    @SerialName("watched_count") val watchedCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("last_watched") val lastWatched: String? = null,
    @SerialName("next_to_watch") val nextToWatch: String? = null,
)

@Serializable
data class TheaterEpisode(
    val season: Int? = null,
    val episode: Int? = null,
    val name: String? = null,
    // ISO date, e.g. "2022-02-04".
    @SerialName("air_date") val airDate: String? = null,
    val watched: Boolean = false,
)

@Serializable
data class TheaterShowDetails(
    val tmdb: Int? = null,
    val title: String = "",
    val overview: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("tmdb_rating") val tmdbRating: Double? = null,
    @SerialName("tmdb_votes") val tmdbVotes: Int? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val poster: String? = null,
    val simkl: TheaterSimklProgress? = null,
    /** Downloaded in the local library. */
    val owned: Boolean = false,
    val episodes: List<TheaterEpisode> = emptyList(),
)

@Serializable
data class TheaterDownload(
    val hash: String = "",
    val name: String = "",
    val state: String = "",
    val progress: Double = 0.0,
    val dlspeed: Long = 0,
    val seeds: Int = 0,
    val eta: Long = ETA_UNKNOWN,
) {
    val isPaused: Boolean
        get() = state.startsWith("stopped", ignoreCase = true) || state.startsWith("paused", true)

    companion object {
        const val ETA_UNKNOWN = 8640000L
    }
}

@Serializable
private data class DownloadsResponse(val downloads: List<TheaterDownload> = emptyList())

@Serializable
data class TheaterWatchlistMovie(
    val title: String = "",
    val year: Int? = null,
    val tmdb: Int? = null,
    val owned: Boolean = false,
    val folder: String? = null,
    val poster: String? = null,
    // Older theater servers list movies only and omit the kind entirely.
    val kind: String? = "movie",
) {
    val isShow: Boolean
        get() = kind.equals("tv", ignoreCase = true)
}

@Serializable
data class TheaterWatchlist(
    val authorized: Boolean = false,
    val movies: List<TheaterWatchlistMovie> = emptyList(),
)

@Serializable
data class TheaterWatchedMovie(
    val title: String = "",
    val tmdb: Int? = null,
    val year: Int? = null,
    // Unix epoch seconds. Rows imported before the server tracked timestamps have none.
    @SerialName("watched_at") val watchedAt: Double? = null,
    val poster: String? = null,
)

@Serializable
data class TheaterWatchedShow(
    val title: String = "",
    val year: Int? = null,
    val tmdb: Int? = null,
)

@Serializable
data class TheaterWatchedEpisode(
    @SerialName("show_title") val showTitle: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    @SerialName("watched_at") val watchedAt: Double? = null,
)

@Serializable
data class TheaterWatched(
    val authorized: Boolean = false,
    val movies: List<TheaterWatchedMovie> = emptyList(),
    val shows: List<TheaterWatchedShow> = emptyList(),
    val episodes: List<TheaterWatchedEpisode> = emptyList(),
)

@Serializable
data class TheaterSimklResult(
    val title: String = "",
    val year: Int? = null,
    val tmdb: Int? = null,
    @SerialName("simkl_id") val simklId: Int? = null,
    val imdb: String? = null,
    val poster: String? = null,
    val rating: Double? = null,
    val kind: String? = null,
)

@Serializable
private data class SimklSearchResponse(val results: List<TheaterSimklResult> = emptyList())

@Serializable
private data class OkResponse(
    val ok: Boolean = false,
    @SerialName("error") val error: String? = null,
)

/** Thin client for the theater server's JSON API. */
@Singleton
class TheaterApi @Inject constructor() {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun searchMovies(query: String): List<TheaterMovie> {
        val body = get("/api/search?q=${query.urlEncoded()}")
        return json.decodeFromString<MovieSearchResponse>(body).results
    }

    suspend fun searchTv(query: String): List<TheaterTvResult> {
        val body = get("/api/search/tv?q=${query.urlEncoded()}")
        return json.decodeFromString<TvSearchResponse>(body).results
    }

    /** Shows already downloaded to the library, newest folder scan first. */
    suspend fun tvLibrary(): List<TheaterTvSeries> {
        val body = get("/api/tv")
        return json.decodeFromString<TvLibraryResponse>(body).series
    }

    suspend fun showDetails(tmdb: Int): TheaterShowDetails {
        val body = get("/api/show/details?tmdb=$tmdb")
        return json.decodeFromString<TheaterShowDetails>(body)
    }

    suspend fun downloads(): List<TheaterDownload> {
        val body = get("/api/downloads")
        return json.decodeFromString<DownloadsResponse>(body).downloads
    }

    suspend fun watchlist(): TheaterWatchlist {
        val body = get("/api/watchlist")
        return json.decodeFromString<TheaterWatchlist>(body)
    }

    suspend fun watched(): TheaterWatched {
        val body = get("/api/watched")
        return json.decodeFromString<TheaterWatched>(body)
    }

    suspend fun addMovieTorrent(hash: String, name: String) {
        postForm("/api/torrent/add", "hash" to hash, "name" to name)
    }

    suspend fun addTvTorrent(hash: String, name: String) {
        postForm("/api/torrent/add/tv", "hash" to hash, "name" to name)
    }

    /** Starts playback of an already downloaded movie on the theater TV. */
    suspend fun playMovie(folder: String) {
        postForm("/api/play", "folder" to folder)
    }

    suspend fun torrentAction(action: String, hash: String) {
        postForm("/api/torrent/action", "a" to action, "hash" to hash)
    }

    suspend fun addToWatchlist(imdb: String, title: String) {
        postForm("/api/watchlist/add", "imdb" to imdb, "title" to title)
    }

    suspend fun simklSearch(query: String, kind: String): List<TheaterSimklResult> {
        val body = get("/api/simkl/search?q=${query.urlEncoded()}&kind=$kind")
        return json.decodeFromString<SimklSearchResponse>(body).results
    }

    /** Adds a SimKL search result to the watchlist. Shows also carry their SimKL id. */
    suspend fun addToWatchlist(result: TheaterSimklResult, isShow: Boolean) {
        val fields = mutableListOf<Pair<String, String>>()
        if (isShow) {
            fields.add("kind" to "tv")
        }
        result.tmdb?.let { fields.add("tmdb" to it.toString()) }
        if (isShow) {
            result.simklId?.let { fields.add("simkl_id" to it.toString()) }
        }
        fields.add("title" to result.title)
        result.year?.let { fields.add("year" to it.toString()) }
        postForm("/api/watchlist/add", *fields.toTypedArray())
    }

    suspend fun markWatched(result: TheaterSimklResult, isShow: Boolean) {
        val fields = mutableListOf<Pair<String, String>>()
        if (isShow) {
            fields.add("kind" to "tv")
        }
        result.tmdb?.let { fields.add("tmdb" to it.toString()) }
        if (isShow) {
            result.simklId?.let { fields.add("simkl_id" to it.toString()) }
        }
        fields.add("title" to result.title)
        postForm("/api/watched/add", *fields.toTypedArray())
    }

    /**
     * Marks a watchlist entry watched. The server also drops it from the watchlist, so callers
     * should reload the watchlist afterwards.
     */
    suspend fun markWatchlistWatched(entry: TheaterWatchlistMovie) {
        val fields = mutableListOf<Pair<String, String>>()
        fields.add("kind" to if (entry.isShow) "tv" else "movie")
        entry.tmdb?.let { fields.add("tmdb" to it.toString()) }
        fields.add("title" to entry.title)
        postForm("/api/watched/add", *fields.toTypedArray())
    }

    /**
     * Un-marks something watched. Pass [season] and [episode] with `kind=episode` to reverse a
     * single episode; `kind=tv` or `kind=movie` clear the whole title. The server tombstones the
     * row so a later SimKL sync cannot resurrect it, and queues the SimKL history removal.
     */
    suspend fun unmarkWatched(
        kind: String,
        tmdb: Int,
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
    ) {
        val fields = mutableListOf<Pair<String, String>>()
        fields.add("kind" to kind)
        fields.add("tmdb" to tmdb.toString())
        season?.let { fields.add("season" to it.toString()) }
        episode?.let { fields.add("episode" to it.toString()) }
        fields.add("title" to title.orEmpty())
        postForm("/api/watched/remove", *fields.toTypedArray())
    }

    suspend fun removeFromWatchlist(tmdb: Int) {
        postForm("/api/watchlist/remove", "tmdb" to tmdb.toString())
    }

    private suspend fun get(path: String): String =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(THEATER_BASE + path).get().build())
        }

    private suspend fun postForm(path: String, vararg fields: Pair<String, String>) {
        val body: RequestBody =
            FormBody.Builder()
                .apply { fields.forEach { (name, value) -> add(name, value) } }
                .build()
        val response =
            withContext(Dispatchers.IO) {
                execute(Request.Builder().url(THEATER_BASE + path).post(body).build())
            }
        val result = json.decodeFromString<OkResponse>(response)
        if (!result.ok) {
            throw IOException(result.error ?: "Request failed")
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message =
                    runCatching { json.decodeFromString<OkResponse>(body).error }.getOrNull()
                throw IOException(message ?: "HTTP ${response.code}")
            }
            return body
        }
    }
}

private fun String.urlEncoded(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
