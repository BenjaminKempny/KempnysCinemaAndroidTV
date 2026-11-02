package org.jellyfin.androidtv.ui.settings.screen.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.sdns.SdnsProfile
import org.jellyfin.androidtv.data.sdns.SdnsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

private sealed interface ProfilesState {
	data object Loading : ProfilesState
	data class Loaded(val profiles: List<SdnsProfile>) : ProfilesState
	data class Failed(val message: String) : ProfilesState
}

@Composable
fun SettingsPlaybackSdnsScreen() {
	val userPreferences = koinInject<UserPreferences>()
	val repository = koinInject<SdnsRepository>()

	var submitRatings by rememberPreference(userPreferences, UserPreferences.sdnsSubmitRatings)
	var profileId by rememberPreference(userPreferences, UserPreferences.sdnsProfileId)
	var profileName by rememberPreference(userPreferences, UserPreferences.sdnsProfileName)
	var profilesState by remember { mutableStateOf<ProfilesState>(ProfilesState.Loading) }

	LaunchedEffect(Unit) {
		profilesState = if (!repository.hasConnection) {
			ProfilesState.Failed("")
		} else {
			runCatching { repository.getProfiles() }.fold(
				onSuccess = { ProfilesState.Loaded(it) },
				onFailure = { ProfilesState.Failed(it.message ?: it.javaClass.simpleName) },
			)
		}
	}

	val effectiveProfileId = profileId.ifBlank { BuildConfig.SDNS_PROFILE_ID }.trim()
	// PROFILE_ID may be a UUID, an exact profile name or a unique name prefix
	val selectedProfileId = (profilesState as? ProfilesState.Loaded)?.profiles?.let { profiles ->
		profiles.firstOrNull { it.id == effectiveProfileId }?.id
			?: profiles.firstOrNull { it.name.equals(effectiveProfileId, ignoreCase = true) }?.id
			?: profiles.filter { effectiveProfileId.isNotEmpty() && it.name.startsWith(effectiveProfileId, ignoreCase = true) }
				.singleOrNull()?.id
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback).uppercase()) },
				headingContent = { Text(stringResource(R.string.sdns_pref_section)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.sdns_pref_submit_ratings)) },
				captionContent = { Text(stringResource(R.string.sdns_pref_submit_ratings_description)) },
				trailingContent = { Checkbox(checked = submitRatings) },
				onClick = { submitRatings = !submitRatings },
				modifier = Modifier.focusKey("sdns_submit_ratings"),
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.sdns_pref_profile)) },
				captionContent = {
					Text(
						when {
							profileName.isNotBlank() && profileId.isNotBlank() -> profileName
							BuildConfig.SDNS_PROFILE_ID.isNotBlank() -> stringResource(R.string.sdns_pref_profile_env, BuildConfig.SDNS_PROFILE_ID)
							else -> stringResource(R.string.sdns_pref_profile_none)
						}
					)
				},
			)
		}

		when (val state = profilesState) {
			ProfilesState.Loading -> item {
				ListButton(
					headingContent = { Text(stringResource(R.string.sdns_pref_profiles_loading)) },
					onClick = {},
					enabled = false,
				)
			}

			is ProfilesState.Failed -> item {
				ListButton(
					headingContent = {
						Text(
							if (state.message.isBlank()) stringResource(R.string.sdns_pref_not_configured)
							else stringResource(R.string.sdns_status_error, state.message)
						)
					},
					onClick = {},
					enabled = false,
				)
			}

			is ProfilesState.Loaded -> state.profiles.forEach { profile ->
				item {
					ListButton(
						headingContent = { Text(profile.name.ifBlank { profile.id }) },
						trailingContent = { RadioButton(checked = profile.id == selectedProfileId) },
						onClick = {
							profileId = profile.id
							profileName = profile.name
						},
						modifier = Modifier.focusKey("sdns_profile_${profile.id}"),
					)
				}
			}
		}
	}
}

