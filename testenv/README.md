# Lokale Jellyfin-Testumgebung

Ein lokaler Jellyfin-Server in Docker mit fertig befüllter Mediathek. Damit musst du dich beim
Testen im Android-TV-Emulator nicht mit deinem echten Server verbinden.

## Inhalt

| Bereich     | Inhalt                                                                                     |
|-------------|--------------------------------------------------------------------------------------------|
| Filme       | 24 Filme mit Postern/Hintergründen/Beschreibungen (von TMDB)                               |
| Serien      | Dark, Breaking Bad, The Mandalorian, Stranger Things (mehrere Staffeln, 25 Episoden)       |
| Musik       | 3 Alben mit Cover (15 Titel)                                                               |
| Sammlungen  | Blender Open Movies, Stummfilme & Klassiker, Christopher Nolan + automatische Filmreihen (Matrix, Zurück in die Zukunft, The Dark Knight) |
| Benutzer    | `test` / `test` und `familie` (ohne Passwort, für den Profilwechsel)                        |
| Status      | Weiterschauen (Inception, Big Buck Bunny, Dark S01E03), „Als Nächstes“, Favoriten          |

Die Videos sind synthetische Testbilder mit eingeblendetem Zeitstempel und Sinuston (5 Minuten).
So lassen sich der Player, das Spulen und der Fortschritt gut testen:

- **`.mkv` H.264** – 2 Audiospuren (Deutsch/Englisch) + 2 eingebettete Untertitel → Direct Play, Spurwahl
- **`.mkv` HEVC 10 Bit 1080p + AC3 5.1** – z. B. Sintel, Interstellar → testet ggf. Transcoding
- **`.mp4` H.264** + externe deutsche `.srt`-Untertitel

Nur die Metadaten (Titel, Poster usw.) kommen von den echten Filmen – der Server braucht beim
ersten Scan also Internetzugang.

## Voraussetzungen

```bash
brew install ffmpeg jq      # falls nicht vorhanden
# Docker Desktop muss laufen
```

## Benutzung

```bash
cd testenv
./testenv.sh start      # Medien erzeugen, Server starten, alles einrichten (idempotent)
./testenv.sh stop       # Server stoppen (Daten bleiben erhalten)
./testenv.sh reset      # Serverdaten löschen (frische Einrichtung beim nächsten start)
./testenv.sh reset-all  # zusätzlich die erzeugten Medien löschen
./testenv.sh logs       # Server-Logs anzeigen
./testenv.sh adb        # adb reverse: Server auf dem Gerät unter localhost:8096 erreichbar
```

Optionen über Umgebungsvariablen, z. B. `DURATION=60 ./testenv.sh start` (kürzere Videos, nur bei
neu erzeugten Medien) oder `JELLYFIN_USER=ich JELLYFIN_PASSWORD=geheim ./testenv.sh start`.

Web-Oberfläche zum Kontrollieren: <http://localhost:8096>

## Verbindung aus dem Android-TV-Emulator

1. App im Emulator starten → **Server hinzufügen**
2. Adresse: **`http://10.0.2.2:8096`** (`10.0.2.2` ist im Emulator der Mac selbst)
3. Anmelden mit **`test`** / **`test`**

Alternativ `./testenv.sh adb` ausführen und `http://localhost:8096` eingeben – das funktioniert
auch für ein echtes Gerät per USB.

Die App speichert Server und Anmeldung. Solange du die App nur neu baust/installierst (ohne
Deinstallation oder „Daten löschen“), bleibst du angemeldet. Deinen echten Server kannst du
parallel in der App behalten und über die Serverauswahl wechseln.

## Alternative ohne Docker

Der offizielle Demo-Server: `https://demo.jellyfin.org/stable`, Benutzer `demo`, ohne Passwort.
Allerdings mit wenig Inhalt, eingeschränkten Rechten und ohne Kontrolle über die Daten.

