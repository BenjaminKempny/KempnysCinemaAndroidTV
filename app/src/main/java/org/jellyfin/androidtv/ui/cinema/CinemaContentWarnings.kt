package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
 * Collapsible DoesTheDogDie content warning row on the movie detail page. The header is a
 * single focus target that toggles the section; in the expanded state every category becomes
 * its own lazy item (and focus target) so D-Pad navigation scrolls through long lists.
 */
internal fun LazyListScope.contentWarningsSection(
	result: ContentWarningResult,
	expanded: Boolean,
	onToggle: () -> Unit,
) {
	item(key = "content-warnings-header") {
		ContentWarningHeader(result = result, expanded = expanded, onToggle = onToggle)
	}

	if (!expanded) return

	val report = (result as? ContentWarningResult.Available)?.report
	if (report == null) {
		item(key = "content-warnings-message") { ContentWarningMessage(result) }
		return
	}

	items(report.groups, key = { "content-warnings-${it.category}" }) { group ->
		ContentWarningGroupPanel(group)
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
private fun ContentWarningHeader(
	result: ContentWarningResult,
	expanded: Boolean,
	onToggle: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.clip(CinemaDimens.PanelShape)
			.background(CinemaColors.Surface.copy(alpha = if (focused) 0.85f else 0.55f))
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) CinemaColors.Focus else CinemaColors.Border,
				shape = CinemaDimens.PanelShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onToggle),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp),
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

			Box(Modifier.weight(1f)) { ContentWarningSummary(result) }

			Icon(
				painter = painterResource(R.drawable.ic_arrow_forward),
				contentDescription = stringResource(
					if (expanded) R.string.content_warnings_collapse else R.string.content_warnings_expand
				),
				tint = CinemaColors.Muted,
				modifier = Modifier
					.size(22.dp)
					.rotate(if (expanded) 270f else 90f),
			)
		}
	}
}

/** Compact one-line summary in the collapsed header. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentWarningSummary(result: ContentWarningResult) {
	when (result) {
		is ContentWarningResult.Available -> {
			val flagged = result.report.groups.filter { it.severity != ContentWarningSeverity.NONE }
			if (flagged.isEmpty()) {
				SummaryText(stringResource(R.string.content_warnings_none_reported))
			} else {
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					flagged.forEach { group ->
						SeverityChip(
							text = stringResource(group.category.labelRes),
							severity = group.severity,
						)
					}
				}
			}
		}

		else -> SummaryText(stringResource(result.messageRes))
	}
}

@Composable
private fun SummaryText(text: String) = Text(
	text = text,
	color = CinemaColors.Muted,
	fontSize = CinemaDimens.MetaSize,
	maxLines = 1,
)

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

@Composable
private fun ContentWarningGroupPanel(group: ContentWarningGroup) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 24.dp)
			.clip(CinemaDimens.CardShape)
			.background(CinemaColors.Surface.copy(alpha = 0.45f))
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) CinemaColors.Focus else CinemaColors.Border,
				shape = CinemaDimens.CardShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.focusable(),
	) {
		Column(
			modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Text(
					text = stringResource(group.category.labelRes),
					color = CinemaColors.Text,
					fontSize = CinemaDimens.CardTitleSize,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.weight(1f),
				)
				SeverityChip(
					text = stringResource(group.severity.labelRes),
					severity = group.severity,
				)
			}

			if (group.topics.isEmpty()) {
				Text(
					text = stringResource(R.string.content_warnings_category_empty),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.MetaSize,
				)
			}

			group.topics.forEach { topic -> ContentWarningTopicRow(topic) }
		}
	}
}

@Composable
private fun ContentWarningTopicRow(topic: ContentWarningTopic) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Text(
				text = "• ${topic.name}",
				color = CinemaColors.Text,
				fontSize = CinemaDimens.MetaSize,
				fontWeight = FontWeight.Medium,
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



