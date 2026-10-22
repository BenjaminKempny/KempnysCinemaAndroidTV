package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.contentwarning.ContentWarningCategory
import org.jellyfin.androidtv.data.contentwarning.ContentWarningGroup
import org.jellyfin.androidtv.data.contentwarning.ContentWarningResult
import org.jellyfin.androidtv.data.contentwarning.ContentWarningSeverity
import org.jellyfin.androidtv.data.contentwarning.ContentWarningTopic
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

/**
 * DoesTheDogDie content warnings on the movie detail page.
 *
 * Always shows a compact overview: one tile per category with its severity and the number of
 * reported topics. Selecting a tile opens the details of that category below the overview
 * (selecting it again closes them). Every topic of the opened category is its own lazy item
 * and focus target, so D-Pad navigation scrolls through long lists.
 */
internal fun LazyListScope.contentWarningsSection(
	result: ContentWarningResult,
	selectedCategory: ContentWarningCategory?,
	onSelectCategory: (ContentWarningCategory?) -> Unit,
) {
	val report = (result as? ContentWarningResult.Available)?.report

	item(key = "content-warnings-overview") {
		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			ContentWarningTitle(
				hint = if (report != null && report.groups.isNotEmpty()) stringResource(R.string.content_warnings_hint) else null,
			)

			if (report == null || report.groups.isEmpty()) {
				ContentWarningMessage(result)
			} else {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					report.groups.forEach { group ->
						ContentWarningTile(
							group = group,
							selected = group.category == selectedCategory,
							onClick = {
								onSelectCategory(if (group.category == selectedCategory) null else group.category)
							},
							modifier = Modifier.weight(1f),
						)
					}
				}
			}
		}
	}

	if (report == null) return
	val group = report.groups.firstOrNull { it.category == selectedCategory } ?: return

	item(key = "content-warnings-detail-${group.category}") {
		ContentWarningDetailHeader(group)
	}

	if (group.topics.isEmpty()) {
		item(key = "content-warnings-empty-${group.category}") {
			ContentWarningTopicCard {
				Text(
					text = stringResource(R.string.content_warnings_category_empty),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.MetaSize,
				)
			}
		}
	} else {
		itemsIndexed(group.topics, key = { index, _ -> "content-warnings-topic-${group.category}-$index" }) { _, topic ->
			ContentWarningTopicCard { ContentWarningTopicRow(topic) }
		}
	}

	item(key = "content-warnings-source") {
		Text(
			text = stringResource(R.string.content_warnings_source, report.url),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.MetaSize,
			modifier = Modifier.padding(horizontal = 24.dp),
		)
	}
}

@Composable
private fun ContentWarningTitle(hint: String?) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Icon(
			painter = painterResource(R.drawable.ic_info),
			contentDescription = null,
			tint = CinemaColors.Text,
			modifier = Modifier.size(22.dp),
		)
		Text(
			text = stringResource(R.string.content_warnings_title),
			color = CinemaColors.Text,
			fontSize = CinemaDimens.CardTitleSize,
			fontWeight = FontWeight.SemiBold,
		)
		if (hint != null) {
			Text(
				text = hint,
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
				maxLines = 1,
			)
		}
	}
}

/** Overview tile: severity strip, category, severity and the number of reported topics. */
@Composable
private fun ContentWarningTile(
	group: ContentWarningGroup,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val severityColor = group.severity.color

	Column(
		modifier = modifier
			.clip(CinemaDimens.CardShape)
			.background(
				CinemaColors.Surface.copy(
					alpha = when {
						focused -> 0.9f
						selected -> 0.75f
						else -> 0.5f
					}
				)
			)
			.border(
				width = if (focused || selected) 2.dp else 1.dp,
				color = when {
					focused -> CinemaColors.Focus
					selected -> severityColor
					else -> CinemaColors.Border
				},
				shape = CinemaDimens.CardShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick),
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.height(5.dp)
				.background(severityColor),
		)

		Column(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				text = stringResource(group.category.labelRes),
				color = CinemaColors.Text,
				fontSize = 17.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Box(
					Modifier
						.size(10.dp)
						.clip(CircleShape)
						.background(severityColor),
				)
				Text(
					text = stringResource(group.severity.labelRes),
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.MetaSize,
					fontWeight = FontWeight.Medium,
					maxLines = 1,
				)
			}

			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Text(
					text = if (group.topics.isEmpty()) stringResource(R.string.content_warnings_none_reported)
					else pluralStringResource(R.plurals.content_warnings_topic_count, group.topics.size, group.topics.size),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.MetaSize,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				if (group.topics.isNotEmpty()) {
					Icon(
						painter = painterResource(R.drawable.ic_arrow_forward),
						contentDescription = stringResource(
							if (selected) R.string.content_warnings_collapse else R.string.content_warnings_expand
						),
						tint = CinemaColors.Muted,
						modifier = Modifier
							.size(16.dp)
							.rotate(if (selected) 270f else 90f),
					)
				}
			}
		}
	}
}

@Composable
private fun ContentWarningDetailHeader(group: ContentWarningGroup) {
	Row(
		modifier = Modifier.padding(start = 24.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Text(
			text = stringResource(group.category.labelRes),
			color = CinemaColors.Text,
			fontSize = CinemaDimens.CardTitleSize,
			fontWeight = FontWeight.SemiBold,
		)
		SeverityChip(
			text = stringResource(group.severity.labelRes),
			severity = group.severity,
		)
	}
}

@Composable
private fun ContentWarningMessage(result: ContentWarningResult) {
	CinemaPanel(Modifier.fillMaxWidth()) {
		Text(
			text = stringResource(result.messageRes),
			color = CinemaColors.TextSoft,
			fontSize = CinemaDimens.BodySize,
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
		)
	}
}

/** Focusable card around a single topic of the opened category. */
@Composable
private fun ContentWarningTopicCard(content: @Composable () -> Unit) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 24.dp)
			.clip(CinemaDimens.CardShape)
			.background(CinemaColors.Surface.copy(alpha = if (focused) 0.7f else 0.45f))
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) CinemaColors.Focus else CinemaColors.Border,
				shape = CinemaDimens.CardShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.focusable(),
	) {
		Box(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) { content() }
	}
}

@Composable
private fun ContentWarningTopicRow(topic: ContentWarningTopic) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Text(
				text = topic.name,
				color = CinemaColors.Text,
				fontSize = CinemaDimens.MetaSize,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.weight(1f),
			)
			Text(
				text = stringResource(R.string.content_warnings_votes, topic.yesVotes, topic.noVotes),
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
			)
		}

		topic.scenes.forEach { scene ->
			Column(
				modifier = Modifier.padding(start = 18.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				if (scene.timestamps.isNotEmpty()) {
					Text(
						text = pluralStringResource(
							R.plurals.content_warnings_timestamps,
							scene.timestamps.size,
							scene.timestamps.joinToString(", "),
						),
						color = CinemaColors.Accent,
						fontSize = CinemaDimens.MetaSize,
						fontWeight = FontWeight.SemiBold,
					)
				}
				Text(
					text = if (scene.spoiler) stringResource(R.string.content_warnings_spoiler, scene.description)
					else scene.description,
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.MetaSize,
					lineHeight = 21.sp,
				)
			}
		}
	}
}

@Composable
private fun SeverityChip(text: String, severity: ContentWarningSeverity) {
	Row(
		modifier = Modifier
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.Tag)
			.border(1.dp, CinemaColors.TagBorder, CinemaDimens.PillShape)
			.padding(horizontal = 12.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			Modifier
				.size(10.dp)
				.clip(CircleShape)
				.background(severity.color),
		)
		Text(
			text = text,
			color = CinemaColors.TextSoft,
			fontSize = CinemaDimens.TagTextSize,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
		)
	}
}

private val ContentWarningSeverity.color: Color
	get() = when (this) {
		ContentWarningSeverity.NONE -> Color(0xFF4CAF50)
		ContentWarningSeverity.MILD -> Color(0xFFFFC107)
		ContentWarningSeverity.MODERATE -> Color(0xFFFF9800)
		ContentWarningSeverity.SEVERE -> Color(0xFFF44336)
	}

private val ContentWarningSeverity.labelRes: Int
	get() = when (this) {
		ContentWarningSeverity.NONE -> R.string.content_warnings_severity_none
		ContentWarningSeverity.MILD -> R.string.content_warnings_severity_mild
		ContentWarningSeverity.MODERATE -> R.string.content_warnings_severity_moderate
		ContentWarningSeverity.SEVERE -> R.string.content_warnings_severity_severe
	}

private val ContentWarningCategory.labelRes: Int
	get() = when (this) {
		ContentWarningCategory.SEX_NUDITY -> R.string.content_warnings_category_sex
		ContentWarningCategory.VIOLENCE -> R.string.content_warnings_category_violence
		ContentWarningCategory.LANGUAGE -> R.string.content_warnings_category_language
		ContentWarningCategory.DRUGS -> R.string.content_warnings_category_drugs
		ContentWarningCategory.FRIGHTENING -> R.string.content_warnings_category_frightening
		ContentWarningCategory.OTHER -> R.string.content_warnings_category_other
	}

private val ContentWarningResult.messageRes: Int
	get() = when (this) {
		ContentWarningResult.Loading -> R.string.content_warnings_loading
		ContentWarningResult.NotFound -> R.string.content_warnings_not_found
		ContentWarningResult.MissingIds -> R.string.content_warnings_missing_ids
		ContentWarningResult.NotConfigured -> R.string.content_warnings_not_configured
		ContentWarningResult.Error -> R.string.content_warnings_error
		is ContentWarningResult.Available -> R.string.content_warnings_none_reported
	}

