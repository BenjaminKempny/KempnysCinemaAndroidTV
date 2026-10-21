package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.cinema.CinemaSplashFrame
import org.jellyfin.androidtv.ui.cinema.CinemaTheme
import org.koin.compose.koinInject

@Composable
fun SplashScreen() {
	val backgroundService = koinInject<BackgroundService>()

	LaunchedEffect(Unit) {
		// The cinema frame has its own page background.
		backgroundService.clearBackgrounds()
	}

	CinemaSplashFrame()
}

class SplashFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		CinemaTheme {
			SplashScreen()
		}
	}
}
