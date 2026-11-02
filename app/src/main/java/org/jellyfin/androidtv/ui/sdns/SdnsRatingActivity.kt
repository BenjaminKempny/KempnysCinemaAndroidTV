package org.jellyfin.androidtv.ui.sdns

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.data.sdns.SdnsMovieQuery
import org.jellyfin.androidtv.data.sdns.SdnsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.cinema.CinemaTheme
import org.jellyfin.androidtv.ui.cinema.CinemaThemeState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Transparent activity that shows the [SdnsRatingDialog] on top of whatever screen the user
 * returns to after a movie finished.
 */
class SdnsRatingActivity : FragmentActivity() {
	private val userPreferences by inject<UserPreferences>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val query = SdnsMovieQuery(
			title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
			year = intent.getIntExtra(EXTRA_YEAR, NO_YEAR).takeIf { it != NO_YEAR },
			imdbId = intent.getStringExtra(EXTRA_IMDB),
		)
		if (query.title.isBlank()) {
			finish()
			return
		}

		CinemaThemeState.isLight = userPreferences[UserPreferences.cinemaLightThemeEnabled]

		setContent {
			CinemaTheme {
				SdnsRatingDialog(
					query = query,
					afterPlayback = true,
					onDismiss = ::finish,
				)
			}
		}
	}

	override fun finish() {
		super.finish()
		@Suppress("DEPRECATION")
		overridePendingTransition(0, android.R.anim.fade_out)
	}

	companion object {
		private const val EXTRA_TITLE = "sdns_title"
		private const val EXTRA_YEAR = "sdns_year"
		private const val EXTRA_IMDB = "sdns_imdb"
		private const val NO_YEAR = -1

		/**
		 * Shows the rating dialog for a finished movie when "Submit movie ratings" is enabled.
		 */
		@JvmStatic
		fun promptIfEnabled(
			context: Context,
			userPreferences: UserPreferences,
			repository: SdnsRepository,
			item: BaseItemDto?,
		) {
			if (item == null || item.type != BaseItemKind.MOVIE) return
			if (!userPreferences[UserPreferences.sdnsSubmitRatings]) return
			if (!repository.isConfigured) {
				Timber.w("ScreenDiceNSpice rating prompt skipped, not configured")
				return
			}

			val query = item.toSdnsQuery()
			val intent = Intent(context, SdnsRatingActivity::class.java)
				.putExtra(EXTRA_TITLE, query.title)
				.putExtra(EXTRA_YEAR, query.year ?: NO_YEAR)
				.putExtra(EXTRA_IMDB, query.imdbId)
			if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			context.startActivity(intent)
		}
	}
}

