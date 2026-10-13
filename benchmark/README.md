# Cinema-Performance prüfen

## Umgesetzt

- Geladene Home-Tabs werden innerhalb des Home-ViewModels wiederverwendet, auch bei leeren Ergebnissen. Sortieren lädt nur den Katalog neu; Spotlight und „Weiterschauen“ bleiben erhalten.
- Medienwechsel verwirft den bisherigen Home-Zustand. Explizites Aktualisieren invalidiert auch die Sammlungszugehörigkeitsprüfung. Es gibt keinen globalen, benutzerübergreifenden Home-Cache.
- Abgebrochene Tab-/Seitenladevorgänge hinterlassen keinen dauerhaft aktiven Ladeindikator.
- Die Genre-Stichprobe fordert nur Genre-Metadaten ohne Bilder an. Höchstens vier Genre-Abfragen laufen gleichzeitig.
- Poster-Raster verteilen die verfügbare Breite gleichmäßig auf sieben Spalten. Platzhalter für unbesetzte Spalten halten auch die letzte Reihe gleich groß. Die rechte Karte wird nicht mehr durch zuvor vergebene feste Breiten gestaucht.

### Optionaler Modus mit Designabstrichen

Unter **Einstellungen → Anpassung → Cinema-Performance-Modus**, standardmäßig aus.
Die Einstellung gilt für Startseite, Details, Suche und Profil.

- Keine Bildüberblendungen und keine automatische Spotlight-Rotation; manuelle Bedienung bleibt verfügbar.
- Bestehende Reduced-Motion-Pfade schalten unter anderem Shimmer und Fokus-/Tab-Animationen ab.
- Dekorative Hintergrundverläufe, Panel-Transparenz und Panel-Rahmen werden reduziert.
- Posterauflösung, Texte und sichtbare Fokusrahmen bleiben erhalten.

## Funktionstests

Vom Repository-Stamm aus:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.androidtv.ui.cinema.CinemaGridTest
```

Der Gerätetest benötigt keinen Jellyfin-Server. Er misst elf Karten in einer absichtlich schmalen Ansicht und prüft D-Pad-Navigation zur rechten Karte sowie zur letzten Karte der unvollständigen zweiten Reihe.

## Messung auf dem Google TV Streamer

1. Streamer per ADB verbinden. Für vergleichbare Messungen keine anderen ADB-Geräte verbunden lassen.
2. Release-nahe Test-App installieren: `./gradlew :app:installBenchmark`.
3. **Jellyfin TV (Benchmark)** öffnen, mit einem Testkonto anmelden und eine Film-Bibliothek mit mehreren Rasterreihen laden. Die App hat eigene Daten und überschreibt die normale Installation nicht.
4. Home-Tab **Alle/All** auswählen. Einmal durch den Katalog navigieren, damit der Bildcache für den Vergleich warm ist.
5. Zuerst mit ausgeschaltetem, dann mit eingeschaltetem Performance-Modus messen:

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.androidtv.benchmark.CinemaScrollBenchmark
```

Ergebnisse vor dem zweiten Lauf aus `benchmark/build/outputs/` und `benchmark/build/reports/` sichern. Fünf Wiederholungen pro Lauf; gleiche Bibliothek, Auflösung, Bildwiederholrate und thermische Bedingungen verwenden. Der Benchmark misst D-Pad-Navigation, aktiviert aber keine Wiedergabe oder sonstigen Aktionen. Er bricht bei fehlendem Login/Filmkatalog ab, statt versehentlich den Anmeldebildschirm zu vermessen.

`FrameTimingMetric` liefert insbesondere Perzentile der Frame-Dauer und, auf unterstützten Android-Versionen, des überschrittenen Frame-Budgets. Bei 60 Hz stehen ungefähr 16,7 ms pro Frame zur Verfügung. Der Scroll-Benchmark verwendet Vollkompilierung, um Änderungen der Darstellung möglichst unabhängig vom JIT-Aufwärmen zu vergleichen. Ein Prozess-Kaltstart leert nicht automatisch den Bild-Diskcache.

Emulatorwerte eignen sich für Funktionstests, nicht als Leistungsversprechen für den Streamer. Eine Beschleunigungsquote wurde bisher nicht auf dem physischen Gerät gemessen.

## Baseline Profile

Die App- und Instrumentierungs-APKs besitzen unterschiedliche Paketnamen. Die Benchmark-App ist minifiziert, nicht debuggable und nur in dieser Variante für Shell-Profiling freigegeben.

```sh
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.androidtv.benchmark.BaselineProfileGenerator
```

Auf einem für Profilgenerierung unterstützten Gerät ausführen (z. B. Android 13 oder neuer). Der Generator wartet sprachunabhängig auf den geladenen Filmkatalog und zeichnet Start und D-Pad-Navigation auf. Das erzeugte Textprofil aus den zusätzlichen Instrumentierungsausgaben unter `benchmark/build/outputs/` prüfen und als `app/src/main/baseline-prof.txt` übernehmen; danach App neu bauen und die Paketierung des Profils prüfen. Erst mit einem tatsächlich erzeugten und integrierten Cinema-Profil ist der Startup-Test mit Baseline Profile als Prüfung dieser Optimierung aussagekräftig.

## Noch messungsabhängig

Ein app-eigenes Profil wurde hier nicht erzeugt. Weitere Schritte aus dem Performance-Plan — gezieltes Bild-Prefetching, Cache-Speicherbudgets, schrittweise sichtbare Genre-/Sammlungsreihen und Messpfade für Detailöffnung/Suche — sollten anhand der Streamer-Traces priorisiert werden. Video-Decoding und serverseitiges Transcoding sind von diesen Oberflächenänderungen nicht betroffen.
