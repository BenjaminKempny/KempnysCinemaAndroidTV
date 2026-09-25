package org.jellyfin.androidtv.ui.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.androidtv.R
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * Every cinema screen loads its artwork through this composable.
 *
 * `coil3.compose.AsyncImage` falls back to Coil's *singleton* image loader when no loader
 * is passed explicitly. The app never installs one (`SingletonImageLoader.Factory` is not
 * implemented), so that fallback is a stock loader with a stock OkHttp client — it knows
 * nothing about the server's certificate trust settings, the custom timeouts or the
 * user agent configured in the app's Koin module, and it opens a *second*
 * memory and disk cache next to the app's one.
 *
 * The visible symptom was artwork that silently stayed blank for a large part of the
 * library. Routing every request through the injected [ImageLoader] fixes that and lets
 * the cinema UI share the cache with the rest of the app.
 */
@Composable
fun CinemaAsyncImage(
	model: String?,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Crop,
	alignment: Alignment = Alignment.Center,
) = CinemaAsyncImage(
	model = remember(model) { CinemaArtwork(listOfNotNull(model)) },
	contentDescription = contentDescription,
	modifier = modifier,
	contentScale = contentScale,
	alignment = alignment,
)

@Composable
fun CinemaAsyncImage(
	model: CinemaArtwork?,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Crop,
	alignment: Alignment = Alignment.Center,
) {
	val imageLoader = koinInject<ImageLoader>()
	val context = LocalContext.current
	var candidateIndex by remember(model) { mutableIntStateOf(0) }
	val url = model?.urls?.getOrNull(candidateIndex)
	val request = remember(context, url) {
		ImageRequest.Builder(context)
			.data(url)
			.crossfade(CinemaMotion.ImageCrossfadeDuration)
			.build()
	}

	AsyncImage(
		model = request,
		imageLoader = imageLoader,
		contentDescription = contentDescription,
		contentScale = contentScale,
		alignment = alignment,
		modifier = modifier,
		error = painterResource(R.drawable.ic_clapperboard),
		onError = {
			// Ignore callbacks from a request replaced by recycling or a new candidate.
			if (model != null && it.result.request.data == model.urls.getOrNull(candidateIndex)) {
				Timber.w("Cinema image candidate %d failed (%s)", candidateIndex, it.result.throwable.javaClass.simpleName)
				if (candidateIndex < model.urls.lastIndex) candidateIndex++
			}
		},
	)
}


