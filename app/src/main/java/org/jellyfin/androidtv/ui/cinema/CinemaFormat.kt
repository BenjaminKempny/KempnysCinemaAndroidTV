package org.jellyfin.androidtv.ui.cinema

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TICKS_PER_MINUTE = 600_000_000L
private const val MINUTES_PER_HOUR = 60L

/** `1h 32m` / `45m`. */
fun formatCinemaRuntime(ticks: Long): String {
	val totalMinutes = ticks / TICKS_PER_MINUTE
	val hours = totalMinutes / MINUTES_PER_HOUR
	val minutes = totalMinutes % MINUTES_PER_HOUR
	return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Wall clock time at which the item would end when started now (`HH:mm`). */
fun cinemaEndTime(runTimeTicks: Long?, positionTicks: Long?): String? {
	if (runTimeTicks == null || runTimeTicks <= 0L) return null
	val remaining = runTimeTicks - (positionTicks ?: 0L)
	if (remaining <= 0L) return null

	val end = LocalDateTime.now().plusMinutes(remaining / TICKS_PER_MINUTE)
	return end.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Series show their run (`2011 - 2014`, `2019 -`), everything else the production year. */
val BaseItemDto.cinemaYearLabel: String?
	get() {
		val start = productionYear ?: premiereDate?.year ?: return null
		if (type != BaseItemKind.SERIES) return start.toString()

		val end = endDate?.year
		return when {
			end != null && end != start -> "$start - $end"
			end != null -> start.toString()
			status.equals("Continuing", ignoreCase = true) -> "$start -"
			else -> start.toString()
		}
	}

/** `S1:E2` for episodes, `null` otherwise. */
val BaseItemDto.cinemaEpisodeCode: String?
	get() {
		if (type != BaseItemKind.EPISODE) return null
		val season = parentIndexNumber
		val episode = indexNumber ?: return null
		return if (season != null) "S$season:E$episode" else "E$episode"
	}

/** Distinct, human readable audio languages of the item (e.g. `Deutsch, Englisch`). */
val BaseItemDto.cinemaAudioLanguages: List<String>
	get() = mediaStreams.orEmpty()
		.filter { it.type == MediaStreamType.AUDIO }
		.mapNotNull { stream ->
			val code = stream.language?.takeIf { it.isNotBlank() && it != "und" }
			code?.let(::displayLanguage)
		}
		.distinct()

private val bibliographicCodes = mapOf(
	"ger" to "deu",
	"fre" to "fra",
	"dut" to "nld",
	"chi" to "zho",
	"cze" to "ces",
	"gre" to "ell",
	"per" to "fas",
	"rum" to "ron",
	"slo" to "slk",
	"alb" to "sqi",
	"arm" to "hye",
	"baq" to "eus",
	"bur" to "mya",
	"geo" to "kat",
	"ice" to "isl",
	"mac" to "mkd",
	"mao" to "mri",
	"may" to "msa",
	"tib" to "bod",
	"wel" to "cym",
)

private fun displayLanguage(code: String): String? {
	val normalized = bibliographicCodes[code.lowercase()] ?: code
	val name = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.getDefault())
	return name.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
		?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}


