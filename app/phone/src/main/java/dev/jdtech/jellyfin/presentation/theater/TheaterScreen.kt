package dev.jdtech.jellyfin.presentation.theater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.film.components.BaseBadge
import dev.jdtech.jellyfin.presentation.theme.LocalSpacings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheaterScreen(modifier: Modifier = Modifier, viewModel: TheaterViewModel = hiltViewModel()) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val watchedState by viewModel.watchedState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(TheaterTab.SEARCH) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            TheaterTab.DOWNLOADS -> viewModel.startPollingDownloads()
            TheaterTab.WATCHLIST -> {
                viewModel.stopPollingDownloads()
                viewModel.loadWatchlist()
            }
            TheaterTab.WATCHED -> {
                viewModel.stopPollingDownloads()
                viewModel.loadWatched()
            }
            TheaterTab.SEARCH -> viewModel.stopPollingDownloads()
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopPollingDownloads() } }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_theater)) },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                TheaterTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(text = tab.label()) },
                    )
                }
            }

            when (selectedTab) {
                TheaterTab.SEARCH ->
                    SearchSection(
                        state = searchState,
                        innerPadding = innerPadding,
                        onQueryChange = viewModel::onQueryChange,
                        onModeChange = viewModel::onModeChange,
                        onSourceChange = viewModel::onSourceChange,
                        onSearch = viewModel::search,
                        onExpand = viewModel::onResultExpand,
                        onPlayMovie = viewModel::playMovie,
                        onDownloadMovie = viewModel::addMovieTorrent,
                        onDownloadTv = viewModel::addTvTorrent,
                        onWatchlistAdd = viewModel::addToWatchlist,
                        onSimklWatchlist = viewModel::addSimklToWatchlist,
                        onSimklWatched = viewModel::markWatched,
                    )
                TheaterTab.DOWNLOADS ->
                    DownloadsSection(
                        state = downloadsState,
                        innerPadding = innerPadding,
                        onPauseResume = viewModel::pauseOrResume,
                        onDelete = viewModel::delete,
                    )
                TheaterTab.WATCHLIST ->
                    WatchlistSection(
                        state = watchlistState,
                        innerPadding = innerPadding,
                        onKindChange = viewModel::onWatchlistKindChange,
                        onRefresh = viewModel::loadWatchlist,
                        onRemove = viewModel::removeFromWatchlist,
                    )
                TheaterTab.WATCHED ->
                    WatchedSection(
                        state = watchedState,
                        innerPadding = innerPadding,
                        onKindChange = viewModel::onWatchedKindChange,
                        onRefresh = viewModel::loadWatched,
                    )
            }
        }
    }
}

@Composable
private fun SearchSection(
    state: TheaterSearchState,
    innerPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onSourceChange: (SearchSource) -> Unit,
    onSearch: () -> Unit,
    onExpand: (String?) -> Unit,
    onPlayMovie: (TheaterMovie) -> Unit,
    onDownloadMovie: (TheaterMovie, TheaterTorrent) -> Unit,
    onDownloadTv: (TheaterTvResult) -> Unit,
    onWatchlistAdd: (TheaterMovie) -> Unit,
    onSimklWatchlist: (TheaterSimklResult) -> Unit,
    onSimklWatched: (TheaterSimklResult) -> Unit,
) {
    val spacings = LocalSpacings.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(spacings.default),
            label = { Text(text = "Search") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacings.default),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = state.source == SearchSource.TORRENTS,
                onClick = { onSourceChange(SearchSource.TORRENTS) },
                label = { Text(text = "Torrents") },
            )
            FilterChip(
                selected = state.source == SearchSource.SIMKL,
                onClick = { onSourceChange(SearchSource.SIMKL) },
                label = { Text(text = "Track") },
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = spacings.default, vertical = spacings.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.mode == SearchMode.MOVIES,
                onClick = { onModeChange(SearchMode.MOVIES) },
                label = { Text(text = "Movies") },
            )
            FilterChip(
                selected = state.mode == SearchMode.TV,
                onClick = { onModeChange(SearchMode.TV) },
                label = { Text(text = "TV") },
            )
            Box(modifier = Modifier.weight(1f))
            TextButton(onClick = onSearch, enabled = state.query.isNotBlank()) {
                Text(text = "Search")
            }
        }

        when {
            state.isLoading -> CenteredLoading()
            state.error != null -> CenteredMessage(text = state.error, isError = true)
            state.source == SearchSource.SIMKL && state.simklResults.isEmpty() ->
                CenteredMessage(
                    text =
                        if (state.hasSearched) "No results."
                        else "Search the SimKL catalogue to track something."
                )
            state.source == SearchSource.SIMKL ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = spacings.default,
                            end = spacings.default,
                            top = spacings.small,
                            bottom = innerPadding.calculateBottomPadding() + spacings.default,
                        ),
                    verticalArrangement = Arrangement.spacedBy(spacings.small),
                ) {
                    items(items = state.simklResults) { result ->
                        SimklResultCard(
                            result = result,
                            onWatchlist = { onSimklWatchlist(result) },
                            onWatched = { onSimklWatched(result) },
                        )
                    }
                }
            state.mode == SearchMode.MOVIES && state.movies.isEmpty() ->
                CenteredMessage(
                    text =
                        if (state.hasSearched) "No results."
                        else "Search for a movie to get started."
                )
            state.mode == SearchMode.TV && state.tvResults.isEmpty() ->
                CenteredMessage(
                    text =
                        if (state.hasSearched) "No results." else "Search for an episode or show."
                )
            state.mode == SearchMode.MOVIES ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = spacings.default,
                            end = spacings.default,
                            top = spacings.small,
                            bottom = innerPadding.calculateBottomPadding() + spacings.default,
                        ),
                    verticalArrangement = Arrangement.spacedBy(spacings.small),
                ) {
                    items(items = state.movies, key = { it.imdb ?: it.title }) { movie ->
                        MovieResultCard(
                            movie = movie,
                            expanded = state.expandedTitle == movie.title,
                            onExpand = { onExpand(movie.title) },
                            onPlay = { onPlayMovie(movie) },
                            onDownload = { torrent -> onDownloadMovie(movie, torrent) },
                            onWatchlistAdd = { onWatchlistAdd(movie) },
                        )
                    }
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = spacings.default,
                            end = spacings.default,
                            top = spacings.small,
                            bottom = innerPadding.calculateBottomPadding() + spacings.default,
                        ),
                    verticalArrangement = Arrangement.spacedBy(spacings.small),
                ) {
                    items(items = state.tvResults) { result ->
                        TvResultRow(result = result, onDownload = { onDownloadTv(result) })
                    }
                }
        }
    }
}

@Composable
private fun SimklResultCard(
    result: TheaterSimklResult,
    onWatchlist: () -> Unit,
    onWatched: () -> Unit,
) {
    val spacings = LocalSpacings.current
    // The tracker endpoints key off TMDB, so a result without one cannot be acted on.
    val actionsEnabled = result.tmdb != null

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
        ) {
            Poster(url = result.poster, modifier = Modifier.width(72.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                                result.year?.toString(),
                                result.rating?.let { String.format(Locale.US, "\u2605 %.1f", it) },
                            )
                            .joinToString(" \u00b7 ")
                            .ifEmpty { "\u2014" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacings.small)) {
                    TextButton(onClick = onWatchlist, enabled = actionsEnabled) {
                        Text(text = "+ Watchlist")
                    }
                    TextButton(onClick = onWatched, enabled = actionsEnabled) {
                        Text(text = "Mark watched")
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieResultCard(
    movie: TheaterMovie,
    expanded: Boolean,
    onExpand: () -> Unit,
    onPlay: () -> Unit,
    onDownload: (TheaterTorrent) -> Unit,
    onWatchlistAdd: () -> Unit,
) {
    val spacings = LocalSpacings.current

    OutlinedCard(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacings.medium)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacings.small)) {
                Poster(url = movie.poster, modifier = Modifier.width(72.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = movie.subtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onWatchlistAdd) { Text(text = "+ Watchlist") }
                }
            }

            // Already in the library: offer playback before any of the download options.
            if (movie.owned) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth().padding(top = spacings.small),
                ) {
                    Text(text = "▶ Play")
                }
            }

            if (expanded) {
                if (movie.torrents.isEmpty()) {
                    Text(
                        text = "No torrents available.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = spacings.small),
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(top = spacings.small),
                        verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
                    ) {
                        movie.torrents.forEach { torrent ->
                            OutlinedButton(
                                onClick = { onDownload(torrent) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = torrent.describe())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvResultRow(result: TheaterTvResult, onDownload: () -> Unit) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                                result.quality,
                                result.size,
                                "${result.seeds} seeds",
                            )
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDownload) { Text(text = "Download") }
        }
    }
}

@Composable
private fun DownloadsSection(
    state: TheaterDownloadsState,
    innerPadding: PaddingValues,
    onPauseResume: (TheaterDownload) -> Unit,
    onDelete: (TheaterDownload) -> Unit,
) {
    val spacings = LocalSpacings.current
    var pendingDelete by remember { mutableStateOf<TheaterDownload?>(null) }

    pendingDelete?.let { download ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = "Delete download?") },
            text = {
                Text(text = "${download.name} and its files will be removed from the server.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(download)
                        pendingDelete = null
                    }
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(text = "Cancel") }
            },
        )
    }

    when {
        state.isLoading && state.downloads.isEmpty() -> CenteredLoading()
        state.error != null && state.downloads.isEmpty() ->
            CenteredMessage(text = state.error, isError = true)
        state.downloads.isEmpty() -> CenteredMessage(text = "Nothing is downloading right now.")
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = spacings.default,
                        end = spacings.default,
                        top = spacings.small,
                        bottom = innerPadding.calculateBottomPadding() + spacings.default,
                    ),
                verticalArrangement = Arrangement.spacedBy(spacings.small),
            ) {
                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                items(items = state.downloads, key = { it.hash }) { download ->
                    DownloadRow(
                        download = download,
                        onPauseResume = { onPauseResume(download) },
                        onDelete = { pendingDelete = download },
                    )
                }
            }
    }
}

@Composable
private fun DownloadRow(
    download: TheaterDownload,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
        ) {
            Text(
                text = download.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { (download.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = download.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacings.small)) {
                TextButton(onClick = onPauseResume) {
                    Text(text = if (download.isPaused) "Resume" else "Pause")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun WatchlistSection(
    state: TheaterWatchlistState,
    innerPadding: PaddingValues,
    onKindChange: (TitleKind) -> Unit,
    onRefresh: () -> Unit,
    onRemove: (TheaterWatchlistMovie) -> Unit,
) {
    val spacings = LocalSpacings.current

    when {
        state.isLoading && state.movies.isEmpty() -> CenteredLoading()
        state.error != null && state.movies.isEmpty() ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRefresh) { Text(text = "Retry") }
            }
        !state.authorized ->
            CenteredMessage(text = "Not authorized — sign in on the theater server.")
        state.movies.isEmpty() ->
            CenteredMessage(text = "Your watchlist is empty — add titles from Search.")
        else ->
            Column(modifier = Modifier.fillMaxSize()) {
                KindSegmentRow(
                    selected = state.kind,
                    onKindChange = onKindChange,
                    onRefresh = onRefresh,
                )
                if (state.visibleMovies.isEmpty()) {
                    CenteredMessage(
                        text =
                            when (state.kind) {
                                TitleKind.MOVIES -> "No movies on your watchlist."
                                TitleKind.TV -> "No shows on your watchlist."
                            }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = spacings.default,
                                end = spacings.default,
                                top = spacings.small,
                                bottom = innerPadding.calculateBottomPadding() + spacings.default,
                            ),
                        verticalArrangement = Arrangement.spacedBy(spacings.small),
                    ) {
                        items(
                            items = state.visibleMovies,
                            key = { it.tmdb ?: it.title.hashCode() },
                        ) { movie ->
                            WatchlistRow(movie = movie, onRemove = { onRemove(movie) })
                        }
                    }
                }
            }
    }
}

/** Movies/TV segment picker shared by the watchlist and watched tabs. */
@Composable
private fun KindSegmentRow(
    selected: TitleKind,
    onKindChange: (TitleKind) -> Unit,
    onRefresh: () -> Unit,
) {
    val spacings = LocalSpacings.current

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = spacings.default, vertical = spacings.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == TitleKind.MOVIES,
            onClick = { onKindChange(TitleKind.MOVIES) },
            label = { Text(text = "Movies") },
        )
        FilterChip(
            selected = selected == TitleKind.TV,
            onClick = { onKindChange(TitleKind.TV) },
            label = { Text(text = "TV") },
        )
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onRefresh) { Text(text = "Refresh") }
    }
}

@Composable
private fun WatchedSection(
    state: TheaterWatchedState,
    innerPadding: PaddingValues,
    onKindChange: (TitleKind) -> Unit,
    onRefresh: () -> Unit,
) {
    val spacings = LocalSpacings.current

    when {
        state.isLoading && state.isEmpty -> CenteredLoading()
        state.error != null && state.isEmpty ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRefresh) { Text(text = "Retry") }
            }
        !state.authorized ->
            CenteredMessage(text = "Not authorized — sign in on the theater server.")
        state.isEmpty -> CenteredMessage(text = "Nothing watched yet.")
        else ->
            Column(modifier = Modifier.fillMaxSize()) {
                KindSegmentRow(
                    selected = state.kind,
                    onKindChange = onKindChange,
                    onRefresh = onRefresh,
                )
                val contentPadding =
                    PaddingValues(
                        start = spacings.default,
                        end = spacings.default,
                        top = spacings.small,
                        bottom = innerPadding.calculateBottomPadding() + spacings.default,
                    )
                when (state.kind) {
                    TitleKind.MOVIES ->
                        if (state.movies.isEmpty()) {
                            CenteredMessage(text = "No watched movies.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(spacings.small),
                            ) {
                                items(
                                    items = state.movies,
                                    key = { it.tmdb ?: it.title.hashCode() },
                                ) { movie ->
                                    WatchedMovieRow(movie = movie)
                                }
                            }
                        }
                    TitleKind.TV ->
                        if (state.episodes.isEmpty() && state.shows.isEmpty()) {
                            CenteredMessage(text = "No watched shows.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(spacings.small),
                            ) {
                                if (state.episodes.isNotEmpty()) {
                                    item { SectionHeader(text = "Recent episodes") }
                                    items(items = state.episodes) { episode ->
                                        WatchedEpisodeRow(episode = episode)
                                    }
                                }
                                if (state.shows.isNotEmpty()) {
                                    item { SectionHeader(text = "Completed shows") }
                                    items(
                                        items = state.shows,
                                        key = { it.tmdb ?: it.title.hashCode() },
                                    ) { show ->
                                        WatchedShowRow(show = show)
                                    }
                                }
                            }
                        }
                }
            }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val spacings = LocalSpacings.current

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = spacings.extraSmall),
    )
}

@Composable
private fun WatchedMovieRow(movie: TheaterWatchedMovie) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(url = movie.poster, modifier = Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(movie.year?.toString(), formatWatchedAt(movie.watchedAt))
                            .joinToString(" · ")
                            .ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WatchedEpisodeRow(episode: TheaterWatchedEpisode) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
        ) {
            Text(
                text = episode.showTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                            episode.episodeCode(),
                            episode.title,
                            formatWatchedAt(episode.watchedAt),
                        )
                        .joinToString(" · ")
                        .ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchedShowRow(show: TheaterWatchedShow) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                show.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistRow(movie: TheaterWatchlistMovie, onRemove: () -> Unit) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(url = movie.poster, modifier = Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = movie.title, style = MaterialTheme.typography.titleSmall)
                movie.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (movie.owned) {
                BaseBadge {
                    Text(
                        text = "In library",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier.align(Alignment.Center).padding(horizontal = spacings.small),
                    )
                }
            }
            TextButton(onClick = onRemove, enabled = movie.tmdb != null) {
                Text(text = "Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Poster(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(0.66f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    val spacings = LocalSpacings.current

    Box(
        modifier = Modifier.fillMaxSize().padding(spacings.default),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun TheaterTab.label(): String =
    when (this) {
        TheaterTab.SEARCH -> "Search"
        TheaterTab.DOWNLOADS -> "Downloads"
        TheaterTab.WATCHLIST -> "Watchlist"
        TheaterTab.WATCHED -> "Watched"
    }

private fun TheaterMovie.subtitle(): String =
    listOfNotNull(
            year?.toString(),
            rating?.let { String.format(Locale.US, "★ %.1f", it) },
            "In library".takeIf { owned },
        )
        .joinToString(" · ")
        .ifEmpty { "—" }

/** Formats a unix epoch second timestamp as a short local date, e.g. "Sep 9, 2026". */
private fun formatWatchedAt(watchedAt: Double?): String? {
    if (watchedAt == null || watchedAt <= 0.0) return null
    val date = Date((watchedAt * 1000).toLong())
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
}

private fun TheaterWatchedEpisode.episodeCode(): String? {
    if (season == null || episode == null) return null
    return String.format(Locale.US, "S%02dE%02d", season, episode)
}

private fun TheaterTorrent.describe(): String =
    listOfNotNull(quality, type, size, "${seeds} seeds").joinToString(" · ")

private fun TheaterDownload.statusLine(): String {
    val percent = String.format(Locale.US, "%.1f%%", progress)
    val speed = String.format(Locale.US, "%.2f MB/s", dlspeed / 1_000_000.0)
    return listOf(percent, state, speed, "$seeds seeds", formatEta(eta)).joinToString(" · ")
}

private fun formatEta(seconds: Long): String {
    if (seconds <= 0L || seconds >= TheaterDownload.ETA_UNKNOWN) return "ETA —"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "ETA ${hours}h ${minutes}m"
        minutes > 0 -> "ETA ${minutes}m"
        else -> "ETA ${seconds}s"
    }
}
