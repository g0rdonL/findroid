package dev.jdtech.jellyfin.presentation.theater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TheaterTab {
    SEARCH,
    DOWNLOADS,
    WATCHLIST,
}

enum class SearchMode {
    MOVIES,
    TV,
}

/** Where search results come from: the torrent indexers or the SimKL catalogue. */
enum class SearchSource {
    TORRENTS,
    SIMKL,
}

data class TheaterSearchState(
    val query: String = "",
    val mode: SearchMode = SearchMode.MOVIES,
    val source: SearchSource = SearchSource.TORRENTS,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val movies: List<TheaterMovie> = emptyList(),
    val tvResults: List<TheaterTvResult> = emptyList(),
    val simklResults: List<TheaterSimklResult> = emptyList(),
    val expandedTitle: String? = null,
    val error: String? = null,
)

data class TheaterDownloadsState(
    val isLoading: Boolean = false,
    val downloads: List<TheaterDownload> = emptyList(),
    val error: String? = null,
)

data class TheaterWatchlistState(
    val isLoading: Boolean = false,
    val authorized: Boolean = true,
    val movies: List<TheaterWatchlistMovie> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TheaterViewModel @Inject constructor(private val api: TheaterApi) : ViewModel() {
    private val _searchState = MutableStateFlow(TheaterSearchState())
    val searchState = _searchState.asStateFlow()

    private val _downloadsState = MutableStateFlow(TheaterDownloadsState())
    val downloadsState = _downloadsState.asStateFlow()

    private val _watchlistState = MutableStateFlow(TheaterWatchlistState())
    val watchlistState = _watchlistState.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages = _messages.asStateFlow()

    private var pollJob: Job? = null

    fun onQueryChange(query: String) {
        _searchState.update { it.copy(query = query) }
    }

    fun onModeChange(mode: SearchMode) {
        _searchState.update { it.copy(mode = mode).cleared() }
    }

    fun onSourceChange(source: SearchSource) {
        _searchState.update { it.copy(source = source).cleared() }
    }

    private fun TheaterSearchState.cleared() =
        copy(
            movies = emptyList(),
            tvResults = emptyList(),
            simklResults = emptyList(),
            expandedTitle = null,
            hasSearched = false,
            error = null,
        )

    fun onResultExpand(title: String?) {
        _searchState.update {
            it.copy(expandedTitle = if (it.expandedTitle == title) null else title)
        }
    }

    fun search() {
        val state = _searchState.value
        if (state.query.isBlank()) return

        viewModelScope.launch {
            _searchState.update { it.copy(isLoading = true, error = null) }
            try {
                if (state.source == SearchSource.SIMKL) {
                    val kind = if (state.mode == SearchMode.TV) "tv" else "movies"
                    val results = api.simklSearch(state.query, kind)
                    _searchState.update {
                        it.copy(isLoading = false, hasSearched = true, simklResults = results)
                    }
                    return@launch
                }

                when (state.mode) {
                    SearchMode.MOVIES -> {
                        val results = api.searchMovies(state.query)
                        _searchState.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                movies = results,
                                tvResults = emptyList(),
                            )
                        }
                    }
                    SearchMode.TV -> {
                        val results = api.searchTv(state.query)
                        _searchState.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                tvResults = results,
                                movies = emptyList(),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _searchState.update {
                    it.copy(isLoading = false, hasSearched = true, error = e.errorText())
                }
            }
        }
    }

    fun addMovieTorrent(movie: TheaterMovie, torrent: TheaterTorrent) {
        val name =
            buildString {
                append(movie.title)
                movie.year?.let { append(" ($it)") }
                torrent.quality?.let { append(" [$it]") }
            }
        runAction(successMessage = "Added $name") { api.addMovieTorrent(torrent.hash, name) }
    }

    fun addTvTorrent(result: TheaterTvResult) {
        runAction(successMessage = "Added ${result.title}") {
            api.addTvTorrent(result.hash, result.title)
        }
    }

    fun addToWatchlist(movie: TheaterMovie) {
        val imdb = movie.imdb
        if (imdb.isNullOrBlank()) {
            _messages.value = "No IMDb id for ${movie.title}"
            return
        }
        runAction(successMessage = "${movie.title} added to watchlist") {
            api.addToWatchlist(imdb, movie.title)
        }
    }

    fun addSimklToWatchlist(result: TheaterSimklResult) {
        val isShow = _searchState.value.mode == SearchMode.TV
        runAction(successMessage = "Added to watchlist") {
            api.addToWatchlist(result, isShow = isShow)
        }
    }

    fun markWatched(result: TheaterSimklResult) {
        val isShow = _searchState.value.mode == SearchMode.TV
        runAction(successMessage = "Marked watched") { api.markWatched(result, isShow = isShow) }
    }

    fun removeFromWatchlist(movie: TheaterWatchlistMovie) {
        val tmdb = movie.tmdb
        if (tmdb == null) {
            _messages.value = "No TMDB id for ${movie.title}"
            return
        }
        runAction(successMessage = "Removed ${movie.title}") {
            api.removeFromWatchlist(tmdb)
            loadWatchlist()
        }
    }

    fun pauseOrResume(download: TheaterDownload) {
        val action = if (download.isPaused) "resume" else "pause"
        runAction(successMessage = null) {
            api.torrentAction(action, download.hash)
            refreshDownloads()
        }
    }

    fun delete(download: TheaterDownload) {
        runAction(successMessage = "Deleted ${download.name}") {
            api.torrentAction("delete", download.hash)
            refreshDownloads()
        }
    }

    /** Polls the downloads endpoint every 3 seconds for as long as the tab is visible. */
    fun startPollingDownloads() {
        if (pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
                _downloadsState.update { it.copy(isLoading = it.downloads.isEmpty()) }
                while (isActive) {
                    refreshDownloads()
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    fun stopPollingDownloads() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refreshDownloads() {
        try {
            val downloads = api.downloads()
            _downloadsState.update {
                it.copy(isLoading = false, downloads = downloads, error = null)
            }
        } catch (e: Exception) {
            _downloadsState.update { it.copy(isLoading = false, error = e.errorText()) }
        }
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            _watchlistState.update { it.copy(isLoading = true, error = null) }
            try {
                val watchlist = api.watchlist()
                _watchlistState.update {
                    it.copy(
                        isLoading = false,
                        authorized = watchlist.authorized,
                        movies = watchlist.movies,
                    )
                }
            } catch (e: Exception) {
                _watchlistState.update { it.copy(isLoading = false, error = e.errorText()) }
            }
        }
    }

    fun onMessageShown() {
        _messages.value = null
    }

    private fun runAction(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                successMessage?.let { _messages.value = it }
            } catch (e: Exception) {
                _messages.value = e.errorText()
            }
        }
    }

    private fun Exception.errorText(): String = message ?: this::class.simpleName ?: "Unknown error"

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
