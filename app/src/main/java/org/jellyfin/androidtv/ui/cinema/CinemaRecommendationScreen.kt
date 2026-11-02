package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto

data class RecommendationActions(
	val onBack: () -> Unit,
	val onHome: () -> Unit,
	/** `null` means "surprise me". */
	val onSelectMood: (RecommendationMood?) -> Unit,
	val onOpenGenres: () -> Unit,
	val onToggleGenre: (String) -> Unit,
	val onClearGenres: () -> Unit,
	val onSelectCompany: (RecommendationCompany) -> Unit,
	val onSelectDuration: (RecommendationDuration) -> Unit,
	val onSelectEra: (RecommendationEra) -> Unit,
	val onNext: () -> Unit,
	val onPrevious: () -> Unit,
	val onReshuffle: () -> Unit,
	val onRestart: () -> Unit,
	val onOpenItem: (BaseItemDto) -> Unit,
	val onPlayItem: (BaseItemDto) -> Unit,
)

/**
 * "Was soll ich schauen?" — a playful four step quiz (mood, company, duration, era)
 * that ends with three fitting but deliberately different movie suggestions.
 */
@Composable
fun CinemaRecommendationScreen(
	state: RecommendationState,
	actions: RecommendationActions,
	posterUrl: (BaseItemDto) -> String?,
	backdropUrl: (BaseItemDto) -> String?,
	modifier: Modifier = Modifier,
) {
	val reducedMotion = rememberReducedMotion()
	val backdrop = state.results.firstOrNull()
		?.takeIf { state.step == RecommendationStep.Result }
		?.let { backdropUrl(it.item) }

	CinemaBackground(modifier, backdropUrl = backdrop) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = CinemaDimens.Overscan, vertical = 28.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp),
		) {
			QuizTopBar(state = state, actions = actions)

			AnimatedContent(
				targetState = state.step,
				modifier = Modifier.fillMaxWidth().weight(1f),
				transitionSpec = {
					if (reducedMotion) {
						fadeIn(tween(0)) togetherWith fadeOut(tween(0))
					} else {
						val forward = targetState.ordinal > initialState.ordinal
						val direction = if (forward) 1 else -1
						(slideInHorizontally(tween(STEP_ANIMATION_MS)) { it / 4 * direction } + fadeIn(tween(STEP_ANIMATION_MS))) togetherWith
							(slideOutHorizontally(tween(STEP_ANIMATION_MS)) { -it / 4 * direction } + fadeOut(tween(STEP_ANIMATION_MS / 2)))
					}
				},
				label = "recommendationStep",
			) { step ->
				when (step) {
					RecommendationStep.Mood -> MoodStep(state, actions)
					RecommendationStep.Genres -> GenreStep(state, actions)
					RecommendationStep.Company -> CompanyStep(state, actions)
					RecommendationStep.Duration -> DurationStep(state, actions)
					RecommendationStep.Era -> EraStep(state, actions)
					RecommendationStep.Loading -> ShuffleStep()
					RecommendationStep.Result -> ResultStep(state, actions, posterUrl)
				}
			}
		}
	}
}

// region chrome

@Composable
private fun QuizTopBar(state: RecommendationState, actions: RecommendationActions) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		CinemaIconButton(
			icon = painterResource(R.drawable.ic_arrow_back),
			contentDescription = stringResource(R.string.cinema_back),
			onClick = actions.onBack,
			size = NAV_BUTTON_SIZE,
		)
		CinemaIconButton(
			icon = painterResource(R.drawable.ic_house),
			contentDescription = stringResource(R.string.lbl_home),
			onClick = actions.onHome,
			size = NAV_BUTTON_SIZE,
		)

		Spacer(Modifier.weight(1f))

		val question = state.questionNumber
		if (question != null) {
			Text(
				text = stringResource(R.string.reco_question_of, question, QUESTION_COUNT),
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
				fontWeight = FontWeight.SemiBold,
			)
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				repeat(QUESTION_COUNT) { index ->
					val active = index < question
					val width by animateFloatAsState(if (index == question - 1) 36f else 18f, label = "quizDotWidth")
					Box(
						modifier = Modifier
							.width(width.dp)
							.height(8.dp)
							.clip(CinemaDimens.PillShape)
							.background(if (active) CinemaColors.Accent else CinemaColors.Border),
					)
				}
			}
		}
	}
}

@Composable
private fun QuizHeading(emoji: String, title: String, subtitle: String) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			BouncingEmoji(emoji, fontSize = 40)
			CinemaPageTitle(text = title)
		}
		Text(
			text = subtitle,
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.BodySize,
		)
	}
}

/** The bottom navigation of multi-select steps. */
@Composable
private fun QuizFooter(
	actions: RecommendationActions,
	nextLabel: String,
	showPrevious: Boolean = true,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
	) {
		if (showPrevious) {
			CinemaButton(
				text = stringResource(R.string.cinema_previous),
				icon = painterResource(R.drawable.ic_arrow_back),
				onClick = actions.onPrevious,
			)
		}
		CinemaButton(
			text = nextLabel,
			primary = true,
			onClick = actions.onNext,
		)
	}
}

/** Gives the first option the focus once the step has slid in. */
@Composable
private fun rememberInitialFocus(): FocusRequester {
	val requester = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(STEP_ANIMATION_MS.toLong() / 2)
		runCatching { requester.requestFocus() }
	}
	return requester
}

// endregion

// region steps

/** One answer of a single choice question. */
private class QuizOption(
	val emoji: String,
	val label: String,
	val hint: String?,
	val selected: Boolean,
	val onClick: () -> Unit,
)

/**
 * Shared layout of the single choice questions: picking an option immediately moves on, so
 * there is no "next" button. The previously chosen answer gets the focus when coming back.
 */
@Composable
private fun SingleChoiceStep(
	emoji: String,
	title: String,
	subtitle: String,
	options: List<QuizOption>,
	actions: RecommendationActions,
	compact: Boolean = false,
	showPrevious: Boolean = true,
) {
	val firstFocus = rememberInitialFocus()
	val focusIndex = options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0

	Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp)) {
		QuizHeading(emoji = emoji, title = title, subtitle = subtitle)

		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.verticalScroll(rememberScrollState())
				// Room for the focus ring and the 8 % zoom, otherwise the scroll container clips them.
				.padding(horizontal = 14.dp, vertical = 18.dp),
			horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp),
			verticalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 16.dp),
		) {
			options.forEachIndexed { index, option ->
				QuizOptionCard(
					emoji = option.emoji,
					label = option.label,
					hint = option.hint,
					selected = option.selected,
					onClick = option.onClick,
					compact = compact,
					modifier = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier,
				)
			}
		}

		if (showPrevious) {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				CinemaButton(
					text = stringResource(R.string.cinema_previous),
					icon = painterResource(R.drawable.ic_arrow_back),
					onClick = actions.onPrevious,
				)
			}
		}
	}
}

@Composable
private fun MoodStep(state: RecommendationState, actions: RecommendationActions) {
	if (state.genresLoading) {
		Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
			QuizHeading(
				emoji = "🎭",
				title = stringResource(R.string.reco_mood_title),
				subtitle = stringResource(R.string.reco_mood_subtitle),
			)
			Text(stringResource(R.string.reco_loading_genres), color = CinemaColors.Muted, fontSize = CinemaDimens.BodySize)
		}
		return
	}

	val options = state.availableMoods.map { mood ->
		QuizOption(
			emoji = mood.emoji,
			label = stringResource(mood.label),
			hint = stringResource(mood.hint),
			selected = state.mood == mood,
			onClick = { actions.onSelectMood(mood) },
		)
	} + QuizOption(
		emoji = "✨",
		label = stringResource(R.string.reco_genre_all),
		hint = stringResource(R.string.reco_mood_surprise_hint),
		selected = false,
		onClick = { actions.onSelectMood(null) },
	) + QuizOption(
		emoji = "🎛️",
		label = stringResource(R.string.reco_mood_custom),
		hint = stringResource(R.string.reco_mood_custom_hint),
		selected = state.mood == null && state.selectedGenres.isNotEmpty(),
		onClick = actions.onOpenGenres,
	)

	SingleChoiceStep(
		emoji = "🎭",
		title = stringResource(R.string.reco_mood_title),
		subtitle = stringResource(R.string.reco_mood_subtitle),
		options = options,
		actions = actions,
		compact = true,
		showPrevious = false,
	)
}

@Composable
private fun GenreStep(state: RecommendationState, actions: RecommendationActions) {
	val firstFocus = rememberInitialFocus()

	Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
		QuizHeading(
			emoji = "🎛️",
			title = stringResource(R.string.reco_genre_title),
			subtitle = stringResource(R.string.reco_genre_subtitle),
		)

		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 12.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			QuizChip(
				emoji = "✨",
				label = stringResource(R.string.reco_genre_all),
				selected = state.selectedGenres.isEmpty(),
				onClick = actions.onClearGenres,
				modifier = Modifier.focusRequester(firstFocus),
			)
			state.availableGenres.forEach { genre ->
				QuizChip(
					emoji = genreEmoji(genre),
					label = genre,
					selected = genre in state.selectedGenres,
					onClick = { actions.onToggleGenre(genre) },
				)
			}
		}

		QuizFooter(actions = actions, nextLabel = stringResource(R.string.cinema_next))
	}
}

@Composable
private fun CompanyStep(state: RecommendationState, actions: RecommendationActions) {
	SingleChoiceStep(
		emoji = "🛋️",
		title = stringResource(R.string.reco_company_title),
		subtitle = stringResource(R.string.reco_company_subtitle),
		options = RecommendationCompany.entries.map { company ->
			QuizOption(
				emoji = company.emoji,
				label = stringResource(company.label),
				hint = stringResource(company.hint),
				selected = state.company == company,
				onClick = { actions.onSelectCompany(company) },
			)
		},
		actions = actions,
	)
}

@Composable
private fun DurationStep(state: RecommendationState, actions: RecommendationActions) {
	SingleChoiceStep(
		emoji = "⏱️",
		title = stringResource(R.string.reco_duration_title),
		subtitle = stringResource(R.string.reco_duration_subtitle),
		options = RecommendationDuration.entries.map { duration ->
			QuizOption(
				emoji = duration.emoji,
				label = stringResource(duration.label),
				hint = stringResource(duration.hint),
				selected = state.duration == duration,
				onClick = { actions.onSelectDuration(duration) },
			)
		},
		actions = actions,
	)
}

@Composable
private fun EraStep(state: RecommendationState, actions: RecommendationActions) {
	SingleChoiceStep(
		emoji = "📅",
		title = stringResource(R.string.reco_era_title),
		subtitle = stringResource(R.string.reco_era_subtitle),
		options = RecommendationEra.entries.map { era ->
			QuizOption(
				emoji = era.emoji,
				label = stringResource(era.label),
				hint = eraHint(era),
				selected = state.era == era,
				onClick = { actions.onSelectEra(era) },
			)
		},
		actions = actions,
	)
}

@Composable
private fun eraHint(era: RecommendationEra): String = when (era) {
	RecommendationEra.New, RecommendationEra.Modern ->
		stringResource(R.string.reco_era_since, era.years!!.first.toString())
	RecommendationEra.Classic -> stringResource(R.string.reco_era_before, (era.years!!.last + 1).toString())
	RecommendationEra.Whatever -> stringResource(R.string.reco_any_hint)
}


/** Short "shuffling the film reels" interlude between the quiz and the result. */
@Composable
private fun ShuffleStep() {
	val reducedMotion = rememberReducedMotion()
	val transition = rememberInfiniteTransition(label = "shuffle")
	val rotation by transition.animateFloat(
		initialValue = 0f,
		targetValue = if (reducedMotion) 0f else 360f,
		animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
		label = "shuffleRotation",
	)
	val pulse by transition.animateFloat(
		initialValue = 0.9f,
		targetValue = if (reducedMotion) 0.9f else 1.1f,
		animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
		label = "shufflePulse",
	)

	Column(
		modifier = Modifier.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
	) {
		Text(
			text = "🎞️",
			fontSize = 96.sp,
			modifier = Modifier.graphicsLayer {
				rotationZ = rotation
				scaleX = pulse
				scaleY = pulse
			},
		)
		CinemaPageTitle(text = stringResource(R.string.reco_shuffling))
		Text(
			text = stringResource(R.string.reco_shuffling_hint),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.BodySize,
		)
	}
}

@Composable
private fun ResultStep(
	state: RecommendationState,
	actions: RecommendationActions,
	posterUrl: (BaseItemDto) -> String?,
) {
	val firstFocus = rememberInitialFocus()

	Column(
		modifier = Modifier.fillMaxSize(),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		// Heading and the round actions share one row so the cards fit without scrolling.
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
					BouncingEmoji("🍿", fontSize = 36)
					CinemaPageTitle(text = stringResource(R.string.reco_result_title))
				}
				if (state.relaxed.isEmpty()) {
					Text(
						text = stringResource(R.string.reco_result_subtitle),
						color = CinemaColors.TextSoft,
						fontSize = CinemaDimens.BodySize,
					)
				} else {
					RelaxedNotice(
						text = stringResource(
							R.string.reco_result_relaxed,
							state.relaxed.map { stringResource(it.label) }.joinToString(", "),
						),
					)
				}
			}

			if (state.results.isNotEmpty()) {
				CinemaButton(
					text = stringResource(R.string.reco_reshuffle),
					icon = painterResource(R.drawable.ic_shuffle),
					primary = true,
					onClick = actions.onReshuffle,
				)
				CinemaButton(
					text = stringResource(R.string.reco_restart),
					onClick = actions.onRestart,
				)
			}
		}

		if (state.results.isEmpty()) {
			Text(
				text = stringResource(R.string.reco_result_empty),
				color = CinemaColors.Text,
				fontSize = CinemaDimens.BodySize,
			)
			CinemaButton(
				text = stringResource(R.string.reco_restart),
				icon = painterResource(R.drawable.ic_shuffle),
				primary = true,
				onClick = actions.onRestart,
				modifier = Modifier.focusRequester(firstFocus),
			)
			return@Column
		}

		Row(
			// Room for the focus ring and zoom of the buttons inside the cards.
			modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(20.dp),
		) {
			state.results.forEachIndexed { index, recommendation ->
				RecommendationCard(
					recommendation = recommendation,
					posterUrl = posterUrl(recommendation.item),
					onOpen = { actions.onOpenItem(recommendation.item) },
					onPlay = { actions.onPlayItem(recommendation.item) },
					detailsModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

/** Solid pill so the hint stays readable on top of the (often bright) blurred backdrop. */
@Composable
private fun RelaxedNotice(text: String) {
	Row(
		modifier = Modifier
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.Surface)
			.border(1.dp, CinemaColors.BorderStrong, CinemaDimens.PillShape)
			.padding(horizontal = 14.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(text = "💡", fontSize = 16.sp)
		Text(
			text = text,
			color = CinemaColors.Text,
			fontSize = CinemaDimens.MetaSize,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

// endregion

// region building blocks

@Composable
private fun RecommendationCard(
	recommendation: Recommendation,
	posterUrl: String?,
	onOpen: () -> Unit,
	onPlay: () -> Unit,
	detailsModifier: Modifier,
	modifier: Modifier = Modifier,
) {
	val item = recommendation.item
	val meta = listOfNotNull(
		item.productionYear?.toString(),
		item.runTimeTicks?.takeIf { it > 0L }?.let(::formatCinemaRuntime),
	).joinToString(" · ")
	val rating = item.communityRating?.let { "★ %.1f".format(it) }
	val reasons = recommendation.matchedGenres.take(2)

	CinemaPanel(modifier) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Box(
					modifier = Modifier
						.width(RESULT_POSTER_WIDTH)
						.height(RESULT_POSTER_WIDTH * 1.5f)
						.clip(RoundedCornerShape(CinemaDimens.PosterRadius))
						.background(CinemaColors.CardPlaceholder),
				) {
					if (posterUrl != null) {
						CinemaAsyncImage(
							model = posterUrl,
							contentDescription = item.name,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize(),
						)
					}
				}

				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = item.name.orEmpty(),
						color = CinemaColors.Text,
						fontSize = CinemaDimens.CardTitleSize,
						fontWeight = FontWeight.Bold,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					if (meta.isNotBlank() || rating != null) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							if (meta.isNotBlank()) {
								Text(text = meta, color = CinemaColors.TextSoft, fontSize = CinemaDimens.MetaSize, maxLines = 1)
							}
							if (rating != null) {
								Text(
									text = rating,
									color = CinemaColors.Star,
									fontSize = CinemaDimens.MetaSize,
									fontWeight = FontWeight.Bold,
									maxLines = 1,
									softWrap = false,
								)
							}
						}
					}
					item.overview?.takeIf { it.isNotBlank() }?.let {
						Text(
							text = it,
							color = CinemaColors.TextSoft,
							fontSize = 14.sp,
							lineHeight = 18.sp,
							maxLines = 3,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}

			if (reasons.isNotEmpty()) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					reasons.forEach { CinemaTag(text = it) }
				}
			}

			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				CinemaButton(
					text = stringResource(R.string.lbl_play),
					icon = painterResource(R.drawable.ic_play),
					primary = true,
					onClick = onPlay,
					modifier = detailsModifier,
				)
				CinemaButton(
					text = stringResource(R.string.cinema_details),
					onClick = onOpen,
				)
			}
		}
	}
}

/** Large single choice tile: emoji, label and a short hint. */
@Composable
private fun QuizOptionCard(
	emoji: String,
	label: String,
	hint: String?,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	compact: Boolean = false,
	width: Dp = if (compact) OPTION_CARD_WIDTH_COMPACT else OPTION_CARD_WIDTH,
) {
	QuizSelectable(selected = selected, onClick = onClick, shape = RoundedCornerShape(OPTION_RADIUS), radius = OPTION_RADIUS, modifier = modifier.width(width)) { contentColor ->
		Column(
			modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 14.dp else 22.dp, horizontal = 12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
		) {
			Text(text = emoji, fontSize = if (compact) 34.sp else 44.sp)
			Text(
				text = label,
				color = contentColor,
				fontSize = CinemaDimens.CardTitleSize,
				fontWeight = FontWeight.Bold,
				textAlign = TextAlign.Center,
				maxLines = 1,
			)
			if (hint != null) {
				Text(
					text = hint,
					color = contentColor.copy(alpha = 0.75f),
					fontSize = CinemaDimens.MetaSize,
					textAlign = TextAlign.Center,
					maxLines = 1,
				)
			}
		}
	}
}

/** Pill shaped multi-select chip with an emoji. */
@Composable
private fun QuizChip(
	emoji: String,
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	QuizSelectable(selected = selected, onClick = onClick, shape = CinemaDimens.PillShape, radius = CHIP_HEIGHT / 2, modifier = modifier.height(CHIP_HEIGHT)) { contentColor ->
		Row(
			modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(text = emoji, fontSize = 20.sp)
			Text(
				text = label,
				color = contentColor,
				fontSize = CinemaDimens.ButtonTextSize,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
			)
			if (selected) Text(text = "✓", color = contentColor, fontSize = CinemaDimens.ButtonTextSize, fontWeight = FontWeight.Bold)
		}
	}
}

/** Shared focus / selection treatment of the quiz options: bouncy zoom, focus ring, accent fill. */
@Composable
private fun QuizSelectable(
	selected: Boolean,
	onClick: () -> Unit,
	shape: androidx.compose.ui.graphics.Shape,
	radius: Dp,
	modifier: Modifier = Modifier,
	content: @Composable (Color) -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }
	val reducedMotion = rememberReducedMotion()

	val scale by animateFloatAsState(
		targetValue = when {
			focused -> 1.08f
			selected -> 1.02f
			else -> 1f
		},
		animationSpec = if (reducedMotion) tween(0) else androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f),
		label = "quizScale",
	)
	val background by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocused
			selected -> CinemaColors.Accent
			else -> CinemaColors.Button
		},
		label = "quizBackground",
	)
	val contentColor by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocusedText
			selected -> CinemaColors.AccentText
			else -> CinemaColors.Text
		},
		label = "quizContent",
	)

	Box(
		modifier = modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.cinemaFocusRing(focused, radius)
			.clip(shape)
			.background(background)
			.border(1.dp, if (focused || selected) Color.Transparent else CinemaColors.Border, shape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		content(contentColor)
	}
}

@Composable
private fun BouncingEmoji(emoji: String, fontSize: Int) {
	val reducedMotion = rememberReducedMotion()
	val transition = rememberInfiniteTransition(label = "emoji")
	val offset by transition.animateFloat(
		initialValue = 0f,
		targetValue = if (reducedMotion) 0f else -6f,
		animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
		label = "emojiBounce",
	)
	Text(
		text = emoji,
		fontSize = fontSize.sp,
		modifier = Modifier.graphicsLayer { translationY = offset * density },
	)
}

/** A little bit of fun for the genre chips; unknown genres get a film clapper. */
private fun genreEmoji(genre: String): String {
	val name = genre.lowercase()
	return GENRE_EMOJIS.entries.firstOrNull { (keys, _) -> keys.any { it in name } }?.value ?: "🎬"
}

private val GENRE_EMOJIS: Map<List<String>, String> = linkedMapOf(
	listOf("anim", "zeichentrick") to "🧸",
	listOf("kom", "comed") to "😂",
	listOf("horror") to "👻",
	listOf("thrill") to "😱",
	listOf("action") to "💥",
	listOf("abenteuer", "advent") to "🗺️",
	listOf("science", "sci-fi") to "🚀",
	listOf("fantasy") to "🐉",
	listOf("roman", "liebe") to "💘",
	listOf("drama") to "🎭",
	listOf("krimi", "crime") to "🕵️",
	listOf("myster") to "🔍",
	listOf("famil") to "👨‍👩‍👧",
	listOf("dokument", "documentary") to "📚",
	listOf("musik", "music") to "🎵",
	listOf("krieg", "war") to "🪖",
	listOf("western") to "🤠",
	listOf("histor") to "🏛️",
	listOf("sport") to "⚽",
	listOf("kinder", "kids") to "🎈",
)

// endregion

private const val QUESTION_COUNT = 4
private const val STEP_ANIMATION_MS = 380
private val NAV_BUTTON_SIZE = 44.dp
private val OPTION_CARD_WIDTH = 190.dp
private val OPTION_CARD_WIDTH_COMPACT = 176.dp
private val OPTION_RADIUS = 22.dp
private val CHIP_HEIGHT = 52.dp
private val RESULT_POSTER_WIDTH = 100.dp

