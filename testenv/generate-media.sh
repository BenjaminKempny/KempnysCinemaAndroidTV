#!/usr/bin/env bash
# Erzeugt eine Test-Mediathek (Filme, Serien, Musik) mit ffmpeg.
# Die Videos sind synthetische Testbilder (mit Zeitstempel), die Ordnernamen
# entsprechen echten Titeln, damit Jellyfin Poster/Hintergründe/Beschreibungen
# von TMDB laden kann.
#
# Umgebungsvariablen:
#   DURATION   Länge der Videos in Sekunden (Standard: 300)
#   MEDIA_DIR  Zielordner (Standard: ./media)
set -euo pipefail
cd "$(dirname "$0")"

MEDIA_DIR="${MEDIA_DIR:-./media}"
DURATION="${DURATION:-300}"
BASE="$MEDIA_DIR/.base"
FF=(ffmpeg -hide_banner -loglevel error -nostdin -y)

command -v ffmpeg >/dev/null || { echo "ffmpeg fehlt (brew install ffmpeg)"; exit 1; }
mkdir -p "$BASE" "$MEDIA_DIR/movies" "$MEDIA_DIR/shows" "$MEDIA_DIR/music"

ts() { printf '%02d:%02d:%02d,000' $(($1 / 3600)) $(($1 % 3600 / 60)) $(($1 % 60)); }

write_srt() { # <datei> <text>
	local f=$1 text=$2 i=0 n=1
	: >"$f"
	while ((i < DURATION)); do
		printf '%d\n%s --> %s\n%s %d\n\n' "$n" "$(ts $i)" "$(ts $((i + 4)))" "$text" "$n" >>"$f"
		i=$((i + 10))
		n=$((n + 1))
	done
}

# --- Basis-Clips (werden nur einmal kodiert und danach geklont) -------------
if [[ ! -f "$BASE/h264.mkv" || ! -f "$BASE/hevc.mkv" || ! -f "$BASE/h264.mp4" ]]; then
	echo "Erzeuge Basis-Clips (${DURATION}s) ..."
	write_srt "$BASE/de.srt" "Deutscher Untertitel"
	write_srt "$BASE/en.srt" "English subtitle"

	# H.264 + 2 Audiospuren (DE/EN) + 2 Untertitelspuren -> Direct Play, Spurwahl
	"${FF[@]}" \
		-f lavfi -i "testsrc2=size=1280x720:rate=25:duration=$DURATION" \
		-f lavfi -i "sine=frequency=440:sample_rate=48000:duration=$DURATION" \
		-f lavfi -i "sine=frequency=660:sample_rate=48000:duration=$DURATION" \
		-i "$BASE/de.srt" -i "$BASE/en.srt" \
		-map 0:v -map 1:a -map 2:a -map 3 -map 4 \
		-c:v libx264 -preset ultrafast -crf 30 -pix_fmt yuv420p -g 50 \
		-c:a aac -b:a 96k -ac 2 -c:s srt \
		-metadata:s:a:0 language=ger -metadata:s:a:0 title="Deutsch" \
		-metadata:s:a:1 language=eng -metadata:s:a:1 title="English" \
		-metadata:s:s:0 language=ger -metadata:s:s:0 title="Deutsch" \
		-metadata:s:s:1 language=eng -metadata:s:s:1 title="English" \
		"$BASE/h264.mkv"

	# HEVC 10 Bit + AC3 5.1 -> testet ggf. Transcoding
	if ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libx265; then
		"${FF[@]}" \
			-f lavfi -i "testsrc2=size=1920x1080:rate=25:duration=$DURATION" \
			-f lavfi -i "sine=frequency=330:sample_rate=48000:duration=$DURATION" \
			-c:v libx265 -preset ultrafast -crf 32 -pix_fmt yuv420p10le -x265-params log-level=error \
			-c:a ac3 -b:a 384k -ac 6 -metadata:s:a:0 language=eng \
			"$BASE/hevc.mkv"
	else
		echo "libx265 nicht verfügbar - verwende H.264 statt HEVC"
		cp "$BASE/h264.mkv" "$BASE/hevc.mkv"
	fi

	# Einfaches MP4 (H.264/AAC)
	"${FF[@]}" \
		-f lavfi -i "testsrc2=size=1280x720:rate=30:duration=$DURATION" \
		-f lavfi -i "sine=frequency=550:sample_rate=48000:duration=$DURATION" \
		-c:v libx264 -preset ultrafast -crf 30 -pix_fmt yuv420p -g 60 \
		-c:a aac -b:a 96k -ac 2 -movflags +faststart \
		"$BASE/h264.mp4"
fi

# Datei klonen (APFS-Clone belegt keinen zusätzlichen Speicher)
place() { # <typ: h264|hevc|mp4> <ziel ohne endung>
	local src ext
	case $1 in
	h264) src="$BASE/h264.mkv" ext=mkv ;;
	hevc) src="$BASE/hevc.mkv" ext=mkv ;;
	mp4) src="$BASE/h264.mp4" ext=mp4 ;;
	esac
	local dest="$2.$ext"
	[[ -f $dest ]] && return 0
	mkdir -p "$(dirname "$dest")"
	cp -c "$src" "$dest" 2>/dev/null || cp "$src" "$dest"
}

# --- Filme -------------------------------------------------------------------
MOVIES=(
	# Blender Open Movies
	"Big Buck Bunny (2008)|h264"
	"Sintel (2010)|hevc"
	"Tears of Steel (2012)|h264"
	"Elephants Dream (2006)|mp4"
	"Cosmos Laundromat (2015)|h264"
	"Spring (2019)|mp4"
	"Coffee Run (2020)|h264"
	"Charge (2022)|hevc"
	# Klassiker
	"Nosferatu (1922)|mp4"
	"Metropolis (1927)|h264"
	"The General (1926)|mp4"
	"Night of the Living Dead (1968)|hevc"
	"Plan 9 from Outer Space (1957)|h264"
	"Charade (1963)|mp4"
	# Bekannte Filme (viele Metadaten, automatische TMDB-Sammlungen)
	"The Matrix (1999)|h264"
	"The Matrix Reloaded (2003)|hevc"
	"The Matrix Revolutions (2003)|mp4"
	"Inception (2010)|h264"
	"Interstellar (2014)|hevc"
	"Batman Begins (2005)|mp4"
	"The Dark Knight (2008)|h264"
	"Back to the Future (1985)|h264"
	"Back to the Future Part II (1989)|mp4"
	"Back to the Future Part III (1990)|hevc"
)

echo "Erzeuge Filme ..."
for entry in "${MOVIES[@]}"; do
	title=${entry%%|*}
	type=${entry##*|}
	place "$type" "$MEDIA_DIR/movies/$title/$title"
	# Externe Untertitel für MP4-Filme
	if [[ $type == mp4 ]]; then
		[[ -f "$MEDIA_DIR/movies/$title/$title.de.srt" ]] || cp "$BASE/de.srt" "$MEDIA_DIR/movies/$title/$title.de.srt"
	fi
done

# --- Serien ------------------------------------------------------------------
SHOWS=(
	"Dark (2017)|1:5,2:3"
	"Breaking Bad (2008)|1:4,2:3"
	"The Mandalorian (2019)|1:4"
	"Stranger Things (2016)|1:4,2:2"
)

echo "Erzeuge Serien ..."
for entry in "${SHOWS[@]}"; do
	show=${entry%%|*}
	name=${show% (*}
	IFS=',' read -r -a seasons <<<"${entry##*|}"
	for s in "${seasons[@]}"; do
		season=${s%%:*}
		count=${s##*:}
		for ((e = 1; e <= count; e++)); do
			code=$(printf 'S%02dE%02d' "$season" "$e")
			type=h264
			((e % 2 == 0)) && type=mp4
			place "$type" "$MEDIA_DIR/shows/$show/Season $(printf '%02d' "$season")/$name $code"
		done
	done
done

# --- Musik -------------------------------------------------------------------
echo "Erzeuge Musik ..."
make_album() { # <künstler> <album> <jahr> <basisfrequenz>
	local artist=$1 album=$2 year=$3 freq=$4
	local dir="$MEDIA_DIR/music/$artist/$album ($year)"
	mkdir -p "$dir"
	[[ -f "$dir/cover.jpg" ]] || "${FF[@]}" -f lavfi -i "mandelbrot=size=600x600:start_scale=$((freq / 100))" -frames:v 1 "$dir/cover.jpg"
	for t in 1 2 3 4 5; do
		local f
		f="$dir/$(printf '%02d' $t) - Track $t.mp3"
		[[ -f $f ]] && continue
		"${FF[@]}" -f lavfi -i "sine=frequency=$((freq + t * 55)):sample_rate=44100:duration=30" \
			-c:a libmp3lame -b:a 128k \
			-metadata title="Track $t" -metadata artist="$artist" -metadata album_artist="$artist" \
			-metadata album="$album" -metadata date="$year" -metadata track="$t/5" -metadata genre="Electronic" \
			"$f"
	done
}
make_album "Testband" "Sinustöne" 2024 220
make_album "Testband" "Mehr Sinustöne" 2025 330
make_album "Die Generatoren" "Rauschen" 2023 440

echo "Fertig: $(find "$MEDIA_DIR" -type f ! -path '*/.base/*' | wc -l | tr -d ' ') Dateien in $MEDIA_DIR"

