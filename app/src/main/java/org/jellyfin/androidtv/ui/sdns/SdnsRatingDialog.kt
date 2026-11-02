package org.jellyfin.androidtv.ui.sdns

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.sdns.SdnsLogic
import org.jellyfin.androidtv.data.sdns.SdnsMovie
import org.jellyfin.androidtv.data.sdns.SdnsMovieQuery
import org.jellyfin.androidtv.data.sdns.SdnsRepository
import org.jellyfin.androidtv.data.sdns.SdnsStatus
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinema.CinemaButton
import org.jellyfin.androidtv.ui.cinema.CinemaColors
import org.jellyfin.androidtv.ui.cinema.CinemaDimens
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.compose.koinInject
import java.util.Locale

private const val DEFAULT_STARS = 3.5
private const val STAR_COUNT = 5
private const val SAVED_CLOSE_DELAY_MS = 1400L

private val SuccessColor = Color(0xFF6FD08C)
private val ErrorColor = Color(0xFFFF7A7A)
private val WatchlistColor = Color(0xFF6AB0FF)

/** Builds the ScreenDiceNSpice lookup data from a Jellyfin item. */
fun BaseItemDto.toSdnsQuery() = SdnsMovieQuery(
	title = name.orEmpty(),
	year = productionYear,
	imdbId = providerIds?.entries?.firstOrNull { it.key.equals("Imdb", ignoreCase = true) }?.value,
)

/**
 * Full screen dialog to rate a movie (0–10 in 0,5 steps) and submit it to ScreenDiceNSpice.
 *
 * @param afterPlayback shown automatically after a movie finished, changes the wording
 */
@Composable
fun SdnsRatingDialog(
	query: SdnsMovieQuery,
	onDismiss: () -> Unit,
	afterPlayback: Boolean = false,
	onSaved: (SdnsMovie) -> Unit = {},
) {
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = true,
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.72f)),
			contentAlignment = Alignment.Center,
		) {
			SdnsRatingPanel(query, onDismiss, afterPlayback, onSaved)
		}
	}
}

private sealed interface PanelState {
	data object Loading : PanelState
	data class Ready(val status: SdnsStatus) : PanelState
	data class LoadFailed(val message: String) : PanelState
	data class Saved(val movie: SdnsMovie) : PanelState
}

@Composable
private fun SdnsRatingPanel(
	query: SdnsMovieQuery,
	onDismiss: () -> Unit,
	afterPlayback: Boolean,
	onSaved: (SdnsMovie) -> Unit,
) {
	val repository = koinInject<SdnsRepository>()
	val scope = rememberCoroutineScope()
	val starsFocus = remember { FocusRequester() }

	var stars by remember { mutableDoubleStateOf(DEFAULT_STARS) }
	var state by remember { mutableStateOf<PanelState>(PanelState.Loading) }
	var saving by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(query) {
		state = runCatching { repository.find(query) }.fold(
			onSuccess = { movie ->
				movie?.rating?.let { stars = SdnsLogic.clampStars(SdnsLogic.ratingToStars(it)) }
				PanelState.Ready(SdnsLogic.status(movie))
			},
			onFailure = { PanelState.LoadFailed(it.message ?: it.javaClass.simpleName) },
		)
	}

	LaunchedEffect(Unit) { runCatching { starsFocus.requestFocus() } }

	fun submit(starValue: Double?) {
		if (saving) return
		saving = true
		error = null
		scope.launch {
			runCatching { repository.submit(query, starValue?.let(SdnsLogic::starsToRating)) }
				.onSuccess { movie ->
					state = PanelState.Saved(movie)
					onSaved(movie)
					delay(SAVED_CLOSE_DELAY_MS)
					onDismiss()
				}
				.onFailure { error = it.message ?: it.javaClass.simpleName }
			saving = false
		}
	}

	Column(
		modifier = Modifier
			.width(640.dp)
			.clip(CinemaDimens.PanelShape)
			.background(CinemaColors.Surface)
			.border(1.dp, CinemaColors.BorderStrong, CinemaDimens.PanelShape)
			.padding(horizontal = 44.dp, vertical = 36.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(18.dp),
	) {
		Text(
			text = stringResource(if (afterPlayback) R.string.sdns_rating_after_playback else R.string.sdns_rating_title).uppercase(),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.EyebrowSize,
			fontWeight = FontWeight.SemiBold,
			letterSpacing = 2.sp,
		)

		Text(
			text = buildString {
				append(query.title)
				query.year?.let { append(" ($it)") }
			},
			color = CinemaColors.Text,
			fontSize = CinemaDimens.PageTitleSize,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)

		StatusChip(state)

		val current = state
		if (current is PanelState.Saved) {
			SavedContent(current.movie)
			return@Column
		}

		AnimatedContent(
			targetState = stars,
			transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
			label = "sdnsRating",
		) { value ->
			Row(verticalAlignment = Alignment.Bottom) {
				Text(
					text = formatStars(value),
					color = CinemaColors.Text,
					fontSize = 72.sp,
					fontWeight = FontWeight.Black,
				)
				Text(
					text = " / 5",
					color = CinemaColors.Muted,
					fontSize = 24.sp,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(bottom = 14.dp),
				)
			}
		}

		StarBar(
			stars = stars,
			onChange = { stars = it },
			onConfirm = { submit(stars) },
			modifier = Modifier.focusRequester(starsFocus),
		)

		Text(
			text = stringResource(R.string.sdns_rating_hint),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.MetaSize,
		)

		error?.let {
			Text(
				text = it,
				color = ErrorColor,
				fontSize = CinemaDimens.MetaSize,
				textAlign = TextAlign.Center,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Row(
			modifier = Modifier.padding(top = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(14.dp),
		) {
			CinemaButton(
				text = stringResource(if (saving) R.string.sdns_rating_saving else R.string.sdns_rating_save),
				icon = painterResource(R.drawable.ic_check),
				primary = true,
				enabled = !saving,
				onClick = { submit(stars) },
			)
			CinemaButton(
				text = stringResource(R.string.sdns_rating_only_seen),
				icon = painterResource(R.drawable.ic_watch),
				enabled = !saving,
				onClick = { submit(null) },
			)
			CinemaButton(
				text = stringResource(if (afterPlayback) R.string.sdns_rating_later else R.string.lbl_cancel),
				onClick = onDismiss,
			)
		}
	}
}

@Composable
private fun StatusChip(state: PanelState) {
	val (text, color) = when (state) {
		PanelState.Loading -> stringResource(R.string.sdns_status_loading) to CinemaColors.Muted
		is PanelState.LoadFailed -> stringResource(R.string.sdns_status_error, state.message) to ErrorColor
		is PanelState.Saved -> stringResource(R.string.sdns_status_saved) to SuccessColor
		is PanelState.Ready -> when (state.status) {
			SdnsStatus.NOT_FOUND -> stringResource(R.string.sdns_status_not_found) to SuccessColor
			SdnsStatus.WATCHLIST -> stringResource(R.string.sdns_status_watchlist) to WatchlistColor
			SdnsStatus.SEEN -> stringResource(R.string.sdns_status_seen) to CinemaColors.TextSoft
			SdnsStatus.RATED -> stringResource(R.string.sdns_status_rated) to CinemaColors.Star
		}
	}

	Text(
		text = text,
		color = color,
		fontSize = CinemaDimens.TagTextSize,
		fontWeight = FontWeight.SemiBold,
		maxLines = 2,
		textAlign = TextAlign.Center,
		overflow = TextOverflow.Ellipsis,
		modifier = Modifier
			.clip(CinemaDimens.PillShape)
			.background(color.copy(alpha = 0.14f))
			.border(1.dp, color.copy(alpha = 0.35f), CinemaDimens.PillShape)
			.padding(horizontal = 16.dp, vertical = 6.dp),
	)
}

@Composable
private fun SavedContent(movie: SdnsMovie) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.padding(vertical = 18.dp),
	) {
		Icon(
			painter = painterResource(R.drawable.ic_check),
			contentDescription = null,
			tint = SuccessColor,
			modifier = Modifier.size(64.dp),
		)
		Text(
			text = movie.rating?.let { stringResource(R.string.sdns_saved_rated, formatStars(SdnsLogic.ratingToStars(it))) }
				?: stringResource(R.string.sdns_saved_seen),
			color = CinemaColors.Text,
			fontSize = CinemaDimens.SectionTitleSize,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
		)
	}
}

/** Five stars, adjusted with D-Pad left / right in 0,5 steps (0,5–5). Center confirms. */
@Composable
private fun StarBar(
	stars: Double,
	onChange: (Double) -> Unit,
	onConfirm: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "sdnsStarScale")

	Row(
		modifier = modifier
			.onFocusChanged { focused = it.isFocused }
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				when (event.key) {
					Key.DirectionLeft -> {
						onChange(SdnsLogic.clampStars(stars - SdnsLogic.STAR_STEP)); true
					}

					Key.DirectionRight -> {
						onChange(SdnsLogic.clampStars(stars + SdnsLogic.STAR_STEP)); true
					}

					Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
						onConfirm(); true
					}

					else -> false
				}
			}
			.focusable()
			.scale(scale)
			.clip(CinemaDimens.PillShape)
			.background(if (focused) CinemaColors.ButtonFocused.copy(alpha = 0.35f) else Color.Transparent)
			.border(2.dp, if (focused) CinemaColors.FocusRing else Color.Transparent, CinemaDimens.PillShape)
			.padding(horizontal = 18.dp, vertical = 12.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		for (index in 0 until STAR_COUNT) {
			val fill = (stars - index).coerceIn(0.0, 1.0).toFloat()
			Box(Modifier.size(56.dp)) {
				Icon(
					painter = painterResource(R.drawable.ic_star),
					contentDescription = null,
					tint = CinemaColors.Text.copy(alpha = 0.18f),
					modifier = Modifier.fillMaxSize(),
				)
				if (fill > 0f) {
					Icon(
						painter = painterResource(R.drawable.ic_star),
						contentDescription = null,
						tint = CinemaColors.Star,
						modifier = Modifier
							.fillMaxSize()
							.drawWithContent {
								clipRect(right = size.width * fill) { this@drawWithContent.drawContent() }
							},
					)
				}
			}
		}
	}
}

private fun formatStars(value: Double) = String.format(Locale.GERMANY, "%.1f", value)


