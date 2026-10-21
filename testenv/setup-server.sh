#!/usr/bin/env bash
# Richtet den lokalen Jellyfin-Testserver per API ein:
# Ersteinrichtung, Benutzer, Bibliotheken, Sammlungen, Wiedergabestatus.
# Kann mehrfach ausgeführt werden (idempotent).
set -euo pipefail
cd "$(dirname "$0")"

URL="${JELLYFIN_URL:-http://localhost:8096}"
USER_NAME="${JELLYFIN_USER:-test}"
USER_PW="${JELLYFIN_PASSWORD:-test}"
SECOND_USER="${JELLYFIN_SECOND_USER:-familie}"
AUTH_BASE='MediaBrowser Client="TestEnvSetup", Device="Script", DeviceId="testenv-setup", Version="1.0.0"'
AUTH="$AUTH_BASE"

uri() { jq -rn --arg s "$1" '$s|@uri'; }

api() { # <METHOD> <pfad> [json]
	local method=$1 path=$2 body=${3:-}
	[[ -n $body ]] || body='{}'
	if [[ $method == GET ]]; then
		curl -sSf "$URL$path" -H "Authorization: $AUTH"
	else
		curl -sSf -X "$method" "$URL$path" -H "Authorization: $AUTH" \
			-H "Content-Type: application/json" -d "$body"
	fi
}

# --- Auf Server warten ---------------------------------------------------------
echo "Warte auf Jellyfin unter $URL ..."
for _ in $(seq 1 120); do
	info=$(curl -sf "$URL/System/Info/Public" 2>/dev/null) && [[ $(jq -r '.Version // empty' <<<"$info" 2>/dev/null) != "" ]] && break
	info=""
	sleep 2
done
[[ -n ${info:-} ]] || { echo "Server nicht erreichbar"; exit 1; }
echo "Jellyfin $(jq -r .Version <<<"$info") läuft."

# --- Ersteinrichtung -----------------------------------------------------------
if [[ $(jq -r .StartupWizardCompleted <<<"$info") != "true" ]]; then
	echo "Führe Ersteinrichtung durch ..."
	sleep 3
	api POST /Startup/Configuration '{"UICulture":"de-DE","MetadataCountryCode":"DE","PreferredMetadataLanguage":"de"}' >/dev/null
	api GET /Startup/User >/dev/null
	api POST /Startup/User "$(jq -nc --arg n "$USER_NAME" --arg p "$USER_PW" '{Name:$n,Password:$p}')" >/dev/null
	api POST /Startup/RemoteAccess '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' >/dev/null
	api POST /Startup/Complete >/dev/null
fi

# --- Anmelden ------------------------------------------------------------------
login=$(curl -sSf -X POST "$URL/Users/AuthenticateByName" -H "Authorization: $AUTH_BASE" \
	-H "Content-Type: application/json" \
	-d "$(jq -nc --arg n "$USER_NAME" --arg p "$USER_PW" '{Username:$n,Pw:$p}')")
TOKEN=$(jq -r .AccessToken <<<"$login")
USER_ID=$(jq -r .User.Id <<<"$login")
AUTH="$AUTH_BASE, Token=\"$TOKEN\""
echo "Angemeldet als $USER_NAME."

# --- Zweiter Benutzer (Profilwechsel testen) ------------------------------------
if ! api GET /Users | jq -e --arg n "$SECOND_USER" 'any(.[]; .Name == $n)' >/dev/null; then
	echo "Lege zweiten Benutzer '$SECOND_USER' (ohne Passwort) an ..."
	api POST /Users/New "$(jq -nc --arg n "$SECOND_USER" '{Name:$n,Password:""}')" >/dev/null
fi

# --- Bibliotheken --------------------------------------------------------------
existing=$(api GET /Library/VirtualFolders | jq -r '.[].Name')
added=0
add_library() { # <name> <collectionType> <pfad im container>
	if grep -qxF "$1" <<<"$existing"; then
		echo "Bibliothek '$1' existiert bereits."
		return
	fi
	echo "Lege Bibliothek '$1' an ..."
	api POST "/Library/VirtualFolders?name=$(uri "$1")&collectionType=$2&paths=$(uri "$3")&refreshLibrary=false" \
		'{"LibraryOptions":{"AutomaticallyAddToCollection":true,"PreferredMetadataLanguage":"de","MetadataCountryCode":"DE","EnableRealtimeMonitor":true}}' >/dev/null
	added=1
}
add_library "Filme" movies /media/movies
add_library "Serien" tvshows /media/shows
add_library "Musik" music /media/music

if [[ $added == 1 ]]; then
	echo "Starte Bibliotheks-Scan (lädt Metadaten von TMDB, kann ein paar Minuten dauern) ..."
	api POST /Library/Refresh >/dev/null
	sleep 5
	for _ in $(seq 1 400); do
		task=$(api GET /ScheduledTasks | jq -c '.[] | select(.Key=="RefreshLibrary")')
		[[ $(jq -r .State <<<"$task") == Running ]] || break
		printf '\r  Scan: %3.0f%%' "$(jq -r '.CurrentProgressPercentage // 0' <<<"$task")"
		sleep 3
	done
	echo -e "\r  Scan abgeschlossen.   "
fi

# --- Sammlungen ----------------------------------------------------------------
movies=$(api GET "/Items?userId=$USER_ID&Recursive=true&IncludeItemTypes=Movie&Fields=Path")
id_for_movie() { jq -r --arg f "/$1/" '[.Items[] | select(.Path | contains($f)) | .Id][0] // empty' <<<"$movies"; }

create_collection() { # <name> <ordner...>
	local name=$1
	shift
	if api GET "/Items?userId=$USER_ID&Recursive=true&IncludeItemTypes=BoxSet" | jq -e --arg n "$name" 'any(.Items[]; .Name == $n)' >/dev/null; then
		echo "Sammlung '$name' existiert bereits."
		return
	fi
	local ids=() id folder
	for folder in "$@"; do
		id=$(id_for_movie "$folder")
		[[ -n $id ]] && ids+=("$id")
	done
	if [[ ${#ids[@]} -eq 0 ]]; then
		echo "Keine Filme für Sammlung '$name' gefunden."
		return
	fi
	echo "Lege Sammlung '$name' an (${#ids[@]} Filme) ..."
	api POST "/Collections?name=$(uri "$name")&ids=$(IFS=,; echo "${ids[*]}")" >/dev/null
}

create_collection "Blender Open Movies" \
	"Big Buck Bunny (2008)" "Sintel (2010)" "Tears of Steel (2012)" "Elephants Dream (2006)" \
	"Cosmos Laundromat (2015)" "Spring (2019)" "Coffee Run (2020)" "Charge (2022)"
create_collection "Stummfilme & Klassiker" \
	"Nosferatu (1922)" "Metropolis (1927)" "The General (1926)" "Night of the Living Dead (1968)" \
	"Plan 9 from Outer Space (1957)" "Charade (1963)"
create_collection "Christopher Nolan" \
	"Batman Begins (2005)" "The Dark Knight (2008)" "Inception (2010)" "Interstellar (2014)"

# --- Wiedergabestatus (Weiterschauen / Als Nächstes / Favoriten) ---------------
episodes=$(api GET "/Items?userId=$USER_ID&Recursive=true&IncludeItemTypes=Episode&Fields=Path")
id_for_episode() { jq -r --arg f "/$1." '[.Items[] | select(.Path | contains($f)) | .Id][0] // empty' <<<"$episodes"; }

userdata() { # <id> <json>
	[[ -n $1 ]] && api POST "/UserItems/$1/UserData?userId=$USER_ID" "$2" >/dev/null 2>&1 || true
}
favorite() { [[ -n $1 ]] && api POST "/UserFavoriteItems/$1?userId=$USER_ID" >/dev/null 2>&1 || true; }

NOW=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
PLAYED="{\"Played\":true,\"LastPlayedDate\":\"$NOW\"}"
resume() { echo "{\"Played\":false,\"PlaybackPositionTicks\":$(($1 * 10000000)),\"LastPlayedDate\":\"$NOW\"}"; }

echo "Setze Wiedergabestatus und Favoriten ..."
userdata "$(id_for_episode "Dark S01E01")" "$PLAYED"
userdata "$(id_for_episode "Dark S01E02")" "$PLAYED"
userdata "$(id_for_episode "Dark S01E03")" "$(resume 90)"
userdata "$(id_for_episode "Breaking Bad S01E01")" "$PLAYED"
userdata "$(id_for_movie "Inception (2010)")" "$(resume 120)"
userdata "$(id_for_movie "Big Buck Bunny (2008)")" "$(resume 45)"
userdata "$(id_for_movie "The Matrix (1999)")" "$PLAYED"
favorite "$(id_for_movie "Interstellar (2014)")"
favorite "$(id_for_movie "Sintel (2010)")"
favorite "$(jq -r '[.Items[] | select(.Path | contains("/Dark (2017)/")) | .SeriesId][0] // empty' <<<"$episodes")"

cat <<EOF

==========================================================
 Testserver bereit
   Browser:          $URL
   Android-Emulator: http://10.0.2.2:8096
   Benutzer:         $USER_NAME / $USER_PW
   Zweitbenutzer:    $SECOND_USER (ohne Passwort)
==========================================================
EOF



