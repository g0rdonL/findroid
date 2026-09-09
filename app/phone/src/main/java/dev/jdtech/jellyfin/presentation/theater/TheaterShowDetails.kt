package dev.jdtech.jellyfin.presentation.theater

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.film.components.BaseBadge
import dev.jdtech.jellyfin.presentation.theme.LocalSpacings
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Grid of shows the theater server already has downloaded. */
@Composable
internal fun TvLibrarySection(
    state: TheaterLibraryState,
    innerPadding: PaddingValues,
    onSeriesClick: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val spacings = LocalSpacings.current

    when {
        state.isLoading && state.series.isEmpty() -> CenteredLoading()
        state.error != null && state.series.isEmpty() ->
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
        state.series.isEmpty() -> CenteredMessage(text = "No shows in the library yet.")
        else ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = spacings.default,
                        end = spacings.default,
                        top = spacings.small,
                        bottom = innerPadding.calculateBottomPadding() + spacings.default,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacings.small),
                verticalArrangement = Arrangement.spacedBy(spacings.medium),
            ) {
                items(items = state.series, key = { it.folder }) { series ->
                    TvSeriesCard(series = series, onClick = { onSeriesClick(it) })
                }
            }
    }
}

/** Folders the server could not resolve to a TMDB show have no details to open. */
@Composable
private fun TvSeriesCard(series: TheaterTvSeries, onClick: (Int) -> Unit) {
    val spacings = LocalSpacings.current
    val tmdb = series.tmdb

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = tmdb != null) { tmdb?.let(onClick) },
        verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
    ) {
        Poster(url = series.poster, modifier = Modifier.fillMaxWidth())
        Text(
            text = series.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = series.subtitle(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShowDetailsSection(state: TheaterDetailsState, innerPadding: PaddingValues) {
    val spacings = LocalSpacings.current
    val show = state.selectedShow

    when {
        state.isLoading -> CenteredLoading()
        state.error != null -> CenteredMessage(text = state.error, isError = true)
        show == null -> CenteredMessage(text = "No details for this show.")
        else -> {
            // Seasons start expanded; a long-running show is easier to skim collapsed.
            var collapsedSeasons by remember(show.tmdb) { mutableStateOf(emptySet<Int>()) }
            val seasons = show.episodes.groupBy { it.season ?: 0 }.toSortedMap()

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
                item { ShowHeader(show = show) }

                if (show.imdbRating != null || show.tmdbRating != null) {
                    item { RatingsRow(show = show) }
                }

                show.simkl?.let { progress -> item { SimklProgressCard(progress = progress) } }

                show.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    item {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (seasons.isNotEmpty()) {
                    item { SectionHeader(text = "Episodes") }
                    seasons.forEach { (season, episodes) ->
                        val collapsed = season in collapsedSeasons
                        item(key = "season-$season") {
                            SeasonHeader(
                                season = season,
                                episodeCount = episodes.size,
                                watchedCount = episodes.count { it.watched },
                                collapsed = collapsed,
                                onToggle = {
                                    collapsedSeasons =
                                        if (collapsed) collapsedSeasons - season
                                        else collapsedSeasons + season
                                },
                            )
                        }
                        if (!collapsed) {
                            episodes.forEach { episode ->
                                item(key = "e-$season-${episode.episode ?: episode.name}") {
                                    EpisodeRow(episode = episode)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShowHeader(show: TheaterShowDetails) {
    val spacings = LocalSpacings.current

    Row(horizontalArrangement = Arrangement.spacedBy(spacings.medium)) {
        Poster(url = show.poster, modifier = Modifier.width(112.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
        ) {
            Text(text = show.title, style = MaterialTheme.typography.titleLarge)
            show.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacings.extraSmall)) {
                show.status?.takeIf { it.isNotBlank() }?.let { InfoChip(text = it) }
                show.genres.forEach { genre -> InfoChip(text = genre) }
            }
            if (show.owned) {
                BaseBadge(modifier = Modifier.padding(top = spacings.extraSmall)) {
                    Text(
                        text = "In library",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier.align(Alignment.Center).padding(horizontal = spacings.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingsRow(show: TheaterShowDetails) {
    val spacings = LocalSpacings.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacings.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        show.imdbRating?.let { rating ->
            Text(
                text = String.format(Locale.US, "IMDb %.1f", rating),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        show.tmdbRating?.let { rating ->
            Text(
                text =
                    buildString {
                        append(String.format(Locale.US, "TMDB %.1f", rating))
                        show.tmdbVotes?.let { append(" ($it votes)") }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SimklProgressCard(progress: TheaterSimklProgress) {
    val spacings = LocalSpacings.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            verticalArrangement = Arrangement.spacedBy(spacings.extraSmall),
        ) {
            Text(
                text = progress.statusLabel(),
                style = MaterialTheme.typography.titleSmall,
            )
            if (progress.totalCount > 0) {
                LinearProgressIndicator(
                    progress = {
                        (progress.watchedCount.toFloat() / progress.totalCount).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text =
                    listOfNotNull(
                            "${progress.watchedCount}/${progress.totalCount} episodes".takeIf {
                                progress.totalCount > 0
                            },
                            progress.nextToWatch?.let { "Next: $it" },
                        )
                        .joinToString(" · ")
                        .ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeasonHeader(
    season: Int,
    episodeCount: Int,
    watchedCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val spacings = LocalSpacings.current

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(vertical = spacings.small),
        horizontalArrangement = Arrangement.spacedBy(spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (collapsed) "▸" else "▾",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (season == 0) "Specials" else "Season $season",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$watchedCount/$episodeCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EpisodeRow(episode: TheaterEpisode) {
    val spacings = LocalSpacings.current
    // Unwatched episodes stay muted so the watched run reads at a glance.
    val contentColor =
        if (episode.watched) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (episode.watched) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = episode.describe(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small non-interactive chip used for the show status and genres. */
@Composable
private fun InfoChip(text: String) {
    val spacings = LocalSpacings.current

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier.padding(top = spacings.extraSmall)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = spacings.small, vertical = spacings.extraSmall),
    )
}

private fun TheaterTvSeries.subtitle(): String =
    listOfNotNull(
            "$seasons season".takeIf { seasons == 1 },
            "$seasons seasons".takeIf { seasons > 1 },
            "$count episodes".takeIf { count > 0 },
        )
        .joinToString(" · ")
        .ifEmpty { "—" }

private fun TheaterSimklProgress.statusLabel(): String =
    when (status?.lowercase(Locale.US)) {
        "watching" -> "Watching"
        "plantowatch" -> "Plan to watch"
        "completed" -> "Completed"
        "hold" -> "On hold"
        "dropped" -> "Dropped"
        else -> status?.replaceFirstChar { it.uppercase() } ?: "Tracked"
    }

/** e.g. "S01E01 · Welcome to Margrave · Feb 4, 2022". */
private fun TheaterEpisode.describe(): String =
    listOfNotNull(episodeCode(), name, formatAirDate(airDate))
        .joinToString(" · ")
        .ifEmpty { "—" }

private fun TheaterEpisode.episodeCode(): String? {
    if (season == null || episode == null) return null
    return String.format(Locale.US, "S%02dE%02d", season, episode)
}

/** Reformats the server's ISO air date to a short local date, leaving it alone if unparseable. */
private fun formatAirDate(airDate: String?): String? {
    if (airDate.isNullOrBlank()) return null
    val parsed =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(airDate) }.getOrNull()
            ?: return airDate
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(parsed)
}
