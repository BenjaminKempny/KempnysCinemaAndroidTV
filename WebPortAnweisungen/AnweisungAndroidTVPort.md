# Portierungs-Spezifikation: KempnysCinemaWeb → jellyfin-androidtv Fork

Das Web-Redesign besteht aus **zwei getrennten Schichten**, die im Android-Port unterschiedlich behandelt werden:

| Schicht | Web-Fundort | Rolle | Port-Relevanz |
|---|---|---|---|
| **A — "Cinema" UI** | `src/apps/modern/features/home/*` | Eigenständige, neu gebaute Oberfläche (Home, Hero, Rails, Sidebar, Detail-Hero) | **Primärquelle für den Port** |
| **B — "Apple TV Modern" Theme** | `src/themes/appletvmodern/*` | Glassmorphism-Overlay über die Legacy-Jellyfin-Klassen | Nur Tokens/Motion übernehmen, Struktur ignorieren |

---

## 1. Design-Tokens & Animation-Specs (Web-Quellen → Android TV)

### 1.1 Farben & Transparenzen

Quelle: `src/apps/modern/features/home/_cinemaTokens.scss` (Mixin `cinema-dark-tokens`).
Nur das Dark-Set wird portiert (siehe §3, Light-Appearance ist ausgeschlossen).

**`res/values/colors.xml`**

| Web-Token | Web-Wert | Android Resource | `#AARRGGBB` |
|---|---|---|---|
| `--cinema-page-base` | `#080c12` | `cinema_page_base` | `#FF080C12` |
| `--cinema-page-bg` (Stop 1) | `#243142` | `cinema_page_grad_top` | `#FF243142` |
| `--cinema-page-bg` (Stop 2) | `#111821` | `cinema_page_grad_mid` | `#FF111821` |
| `--cinema-shell-bg` (von) | `rgba(40,49,62,.92)` | `cinema_shell_top` | `#EB28313E` |
| `--cinema-shell-bg` (bis) | `rgba(14,19,27,.96)` | `cinema_shell_bottom` | `#F50E131B` |
| `--cinema-border` | `rgba(229,240,255,.14)` | `cinema_border` | `#24E5F0FF` |
| `--cinema-border-strong` | `rgba(229,240,255,.22)` | `cinema_border_strong` | `#38E5F0FF` |
| `--cinema-text` | `#f6f8fc` | `cinema_text` | `#FFF6F8FC` |
| `--cinema-text-soft` | `#d2ddea` | `cinema_text_soft` | `#FFD2DDEA` |
| `--cinema-muted` | `#b6c0cd` | `cinema_muted` | `#FFB6C0CD` |
| `--cinema-accent` | `#e1edfa` | `cinema_accent` | `#FFE1EDFA` |
| `--cinema-accent-text` | `#142232` | `cinema_accent_text` | `#FF142232` |
| `--cinema-rail-solid-bg` | `#1a222e` | `cinema_rail` | `#FF1A222E` |
| `--cinema-rail-blur-bg` | `rgba(28,37,49,.7)` | `cinema_rail_blur` | `#B31C2531` |
| `--cinema-nav-active-bg` | `rgba(216,234,255,.16)` | `cinema_nav_active` | `#29D8EAFF` |
| `--cinema-nav-active-text` | `#f0f7ff` | `cinema_nav_active_text` | `#FFF0F7FF` |
| `--cinema-button-bg` | `rgba(50,61,76,.88)` | `cinema_button` | `#E0323D4C` |
| `--cinema-hero-bg` | `#172330` | `cinema_hero_bg` | `#FF172330` |
| `--cinema-placeholder-bg` | `#24303e` | `cinema_card_placeholder` | `#FF24303E` |
| `--cinema-input-bg` | `#202a38` | `cinema_input` | `#FF202A38` |
| `--cinema-surface-bg` | `#1b2532` | `cinema_surface` | `#FF1B2532` |
| `--cinema-focus` | `#d4e8ff` | `cinema_focus` | `#FFD4E8FF` |
| `--cinema-focus-ring` | `rgba(169,207,255,.25)` | `cinema_focus_ring` | `#40A9CFFF` |

**Hero-Scrim** (`cinema.scss:318` `.cinemaHero::after`) — zwei gestapelte Gradienten, in Android als zwei `View`s mit `GradientDrawable` bzw. in Compose als zwei `Brush.linearGradient`-Layer über dem Backdrop:

```
Layer 1 (horizontal, start→end): #D9040A12 → #52040A12 (bei 60%) → #14040A12
Layer 2 (vertikal, bottom→top):  #E00B0B13 → transparent (bei 80%)
```

**Wide-Card-Scrim** (`cinema.scss:578`): vertikal `#00000000 → #E6030910`, Höhe = 66 % der Card.

### 1.2 Geometrie & Typografie

| Element | Web-Wert | Android |
|---|---|---|
| Card-Radius (Poster) | `14px` | `cinema_radius_card = 14dp` (`RoundedCornerShape(14.dp)`) |
| Card-Radius (Theme-Cards) | `12px` (`$card-borderRadius`) | für Leanback `ImageCardView` Corner-Radius `12dp` |
| Hero-Radius | `26px` | `26dp` |
| Shell/Panel-Radius | `32px` / `30px` (< 1100 px) | `30dp` |
| Pill/Button-Radius | `999px` | `RoundedCornerShape(50)` |
| Sidebar-Breite | `64px`, Items `46×46px`, Icons `23px`, Gap `12px` | `cinema_rail_width = 64dp`, Item `46dp`, Icon `23dp` |
| Row-Gap | `18px` | `18dp` |
| Poster-Card-Breite (Rail) | `240px`, Aspect `2/3` | `240dp`, `aspectRatio(2f/3f)` |
| Wide-Card | Aspect `16/10` | `aspectRatio(16f/10f)` |
| Grid | `minmax(155px, 1fr)`, Gap `26px/20px` | `TvLazyVerticalGrid(GridCells.Adaptive(155.dp))` |
| Hero-Mindesthöhe | `450px`, Aspect `2.35` | `aspectRatio(2.35f)`, `heightIn(min = 450.dp)` |
| Hero-Titel | `clamp(2.4rem, 4.8vw, 5rem)`, `w700`, `ls -0.055em` | `56.sp`, `FontWeight.Bold`, `letterSpacing = (-0.055).em` |
| Card-Titel | `0.92rem`, `w600`, 1 Zeile ellipsis | `15.sp`, `SemiBold`, `maxLines = 1`, `TextOverflow.Ellipsis` |
| Card-Subtitle | `0.78rem`, muted, 1 Zeile | `13.sp`, `cinema_muted` |
| Tab-Item | `min-height 42px`, `padding 10/26`, `0.88rem/600` | `42dp` Höhe, `PaddingValues(26.dp, 10.dp)`, `14.sp` |
| Button | `min-height 44px`, `padding 11/22`, Gap `9px`, Icon `21px` | `44dp`, `PaddingValues(22.dp, 11.dp)`, Icon `21dp` |
| Fortschrittsbalken | `3px`, Radius `999px`, Track `#2ED5E8FF`, Progress `cinema_accent` | `LinearProgressIndicator`, `3dp` |

> **10-Foot-Korrektur:** Alle `dp`-Werte oben sind 1:1 aus CSS-px übernommen und für einen Desktop-Betrachtungsabstand dimensioniert. Auf TV **Typografie global um Faktor ~1.3 und Touch-/Fokus-Targets auf min. `48dp` anheben**. Die Web-Codebase macht das für `layout-tv` bereits punktuell (`cinema.scss:968` vergrößert `.cinemaCardMenu` von 36 px auf 44 px) — diese Logik konsequent fortführen.

### 1.3 Animationen & Timing

Alle Easing-Kurven aus dem Web lassen sich exakt als `CubicBezierEasing` bzw. `PathInterpolator` abbilden.

| # | Web-Animation | Fundort | Dauer / Easing | Android-TV-Umsetzung |
|---|---|---|---|---|
| **A1** | **Nav-Pill Slide** — der aktive Tab-/Sidebar-Highlight gleitet an die neue Position statt neu zu zeichnen. `transform`, `width`, `height` werden animiert; `opacity` separat. | `cinema.scss:148-162`, Logik in `NavPill.tsx` (`useNavPill`) | `360ms cubic-bezier(0.22, 1, 0.36, 1)` (easeOutQuint), Opacity `200ms ease` | `animateDpAsState` / `animateRectAsState` auf Offset+Size des Pill-`Box` hinter den Nav-Items. Easing: `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`, `tween(360)`. **Auslöser wechselt von Klick zu `onFocusChanged`** — der Pill folgt auf TV dem D-Pad-Fokus, nicht nur der Selektion. |
| **A2** | **Card Focus-Glow** — im TV-Layout ersetzt ein weicher Ring die Hover-Verschiebung. `transform: none`, nur `box-shadow: 0 0 0 6px rgba(169,207,255,0.25)` | `cinema.scss:958-966` (`.layout-tv`) | `120ms ease` auf `box-shadow` | `Modifier.border`/`drawBehind` mit `animateColorAsState(tween(120, easing = FastOutSlowInEasing))`, Ring `6dp` in `cinema_focus_ring`. |
| **A3** | **Card Focus-Zoom** (Theme-Schicht, Apple-TV-Effekt) — `scale(1.05)`, Hintergrund `rgba(255,255,255,0.24)`, Glow `0 0 3em rgba(255,255,255,0.24)` + Drop-Shadow `0 1.2em 2.6em rgba(0,0,0,0.78)` | `themes/appletvmodern/theme.scss:536-547` | `350ms cubic-bezier(0.25, 0.46, 0.45, 0.94)` (easeOutQuad) auf `transform`, `background-color`, `box-shadow` | **Kernanimation des Ports.** Compose: `animateFloatAsState(if (focused) 1.05f else 1f, tween(350, easing = CubicBezierEasing(0.25f,0.46f,0.45f,0.94f)))` → `Modifier.graphicsLayer { scaleX = s; scaleY = s }`. Leanback: `FocusHighlightHelper.setupBrowseItemFocusHighlight(..., ZOOM_FACTOR_SMALL, false)` reicht nicht (1.1x, falsches Timing) — stattdessen eigener `OnFocusChangeListener` mit `ObjectAnimator.ofPropertyValuesHolder(SCALE_X 1.05f, SCALE_Y 1.05f)`, `duration = 350`, `interpolator = PathInterpolator(0.25f, 0.46f, 0.45f, 0.94f)`. Elevation parallel via `ObjectAnimator.ofFloat(view, "translationZ", 0f, 8f)`. **`clipChildren=false` + `clipToPadding=false`** auf der Row setzen, sonst wird der Zoom beschnitten. |
| **A4** | **Tab Focus-Scale** | `theme.scss:434-444` | `300ms` easeOutQuad, `scale 1.05` | identisch zu A3, Faktor aus `--jf-btn-scale = 1.05`. |
| **A5** | **Button-Hover/Focus** — Hintergrund `rgba(229,240,255,.14) → .49`, Border-Inset `.14 → .28` | `theme.scss:400-444`, `cinema.scss:390-411` | `160ms`–`300ms ease` auf `background-color`, `box-shadow`, `transform` | `animateColorAsState(tween(160))` am `state_focused`-Selector; für Views ein `ColorStateList` + `StateListAnimator`. |
| **A6** | **Glass-Blur der Rail/Shell** | `cinema.scss:991-999`, `theme.scss:274` | `backdrop-filter: blur(24px) saturate(135%)`, Transition `400ms` easeOutQuad | Android hat kein echtes Backdrop-Blur vor API 31. **≥ API 31:** `RenderEffect.createBlurEffect(24f, 24f, CLAMP)` auf einem Snapshot-Layer. **< API 31 / generell auf TV (GPU-Budget!):** durch die *statische* Fallback-Farbe `cinema_rail` `#FF1A222E` ersetzen — das Web macht per `@supports` exakt diesen Fallback. **Empfehlung: auf TV grundsätzlich den Fallback nutzen.** |
| **A7** | **Skeleton-Shimmer** | `cinema.scss:762, 1239` | `cinemaShimmer 2s ease-in-out infinite alternate`, `background-position 100% → 0`, Gradient `#1c2735 → #2b394a → #1c2735` | `rememberInfiniteTransition` + `animateFloat(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)` → `Brush.linearGradient(start = offset)`. |
| **A8** | **Reduced Motion** | `cinema.scss:1259-1267` | alle Animationen/Transitions deaktiviert | `Settings.Global.ANIMATOR_DURATION_SCALE == 0` abfragen und Animationen überspringen. |
| **A9** | **Smooth-Scroll-to-Top** beim Fokussieren von Brand/Tabs | `CinemaHome.tsx:63` (`onHeaderFocus`) | `behavior: 'smooth'` | `TvLazyColumn`-State: `coroutineScope.launch { listState.animateScrollToItem(0) }` im `onFocusChanged` der Top-Bar. |

### 1.4 Fokus-Ring (global)

Quelle `src/scripts/keyboardNavigation.scss` — greift, sobald D-Pad-Navigation aktiv ist:

```
outline:    3px solid #d4e8ff, offset 3px
box-shadow: 0 0 0 6px rgba(10,20,35,0.8),   ← dunkler Separator
            0 0 24px rgba(169,207,255,0.5)  ← Glow
```

Android: eigenes `FocusRingDrawable` bzw. Compose-`Modifier`:
`3dp` Stroke `#FFD4E8FF`, `3dp` Abstand, darunter `6dp` Ring `#CC0A1423`, plus Glow (`24dp` Blur-Radius, `#80A9CFFF`) via `Paint.setShadowLayer` mit `setLayerType(LAYER_TYPE_SOFTWARE)`.

> Der Ring muss **zusätzlich** zum Zoom (A3) existieren — auf dem TV ist nur die Skalierung als Fokusindikator zu schwach.

---

## 2. Screen- & Komponenten-Portierungsplan

### 2.1 Cinema Home (Hauptscreen)

- **Relevante Web-Code-Dateien:**
  `src/apps/modern/features/home/CinemaHome.tsx` (Shell + Routing), `HomeViews.tsx` (Spotlight / ContinueWatching / Catalog / Collections / Genres), `cinema.scss`, `_cinemaTokens.scss`, `api.ts` (Queries), `navigation.ts` (URL-State), `librarySource.ts`.

- **Visuelles & Verhaltens-Konzept:**
  Vertikaler Aufbau innerhalb einer abgerundeten "Shell" (`.cinemaShell`, Radius 32 dp, Gradient + optionaler Blur), links davor eine **fixierte, vertikal zentrierte Icon-Rail** (`.cinemaSidebar`, 64 dp, Pill-Form) mit Search / Movies / Shows / Profile / Settings. Oben im Shell: App-Icon + Segmented-Control `All | Collections | Genres` mit gleitendem Pill (A1).
  Content-Reihenfolge im `all`-View: **Spotlight-Hero → Continue Watching (Wide-Cards) → Katalog-Grid mit Infinite Scroll**.
  Medientyp (`movies`/`series`) und View liegen als Query-Parameter im Router (`navigation.ts`), sind also echter Navigations-State und nicht nur lokaler UI-State.

- **Android TV Umsetzungs-Vorgabe:**
  - Root: `Scaffold` mit `Row { CinemaRail(); Column { Header(); Content() } }` — die Rail als `Modifier.focusRestorer()`-Container, damit Rücksprung aus dem Content den zuletzt aktiven Rail-Eintrag wiederherstellt.
  - **Rail-Verhalten:** Im Web ist sie permanent 64 dp breit und icon-only. Auf TV zur **expandierenden Nav-Rail** ausbauen: collapsed 64 dp (Icons), bei Fokus-Eintritt auf ~240 dp animiert mit Labels (`animateDpAsState`, `tween(360)`, Easing A1). Das ist das etablierte TV-Muster und entspricht dem Pill-Timing des Webs.
  - **Content:** `TvLazyColumn` mit Items in genau der Web-Reihenfolge. Jede Rail = `TvLazyRow` mit `contentPadding = PaddingValues(horizontal = 48.dp)` (Overscan) und `clipToPadding = false`.
  - **Segmented-Control:** `TabRow` (tv-material3) mit `TabRowDefaults.PillIndicator` — bildet A1 nativ ab.
  - **D-Pad-Vertrag:** `DPAD_LEFT` am linken Rail-Rand springt in die Sidebar; `DPAD_UP` aus der ersten Content-Row in die Tabs; `DPAD_BACK` im Detail-View zurück auf die Listenebene (im Web: `.cinemaButton` mit `ArrowBackRounded`, `CinemaHome.tsx:99`).

### 2.2 Spotlight-Hero

- **Relevante Web-Code-Dateien:** `HomeViews.tsx` → `HeroContent` / `Spotlight`, `cinema.scss:305-388`, `api.ts` → `useCinemaHero`.

- **Visuelles & Verhaltens-Konzept:**
  Backdrop (`maxWidth 1600`, `object-position: center 35%`) mit doppeltem Scrim (§1.1). Content links, max. `min(70%, 740px)`: Eyebrow "Spotlight" → Genre-Tags (max. 3) + Jahr + Altersfreigabe → Titel → Overview (3 Zeilen geklemmt) → Aktionen `Play/Resume` (primär) + `Details`. Rechts zwei Pfeil-Buttons für das Durchblättern der Spotlight-Rotation (`index % items.length`).
  Zusätzlich rendert `HeroContent` ein `<img className='cinemaAmbient'>` — ein großflächig unscharfes Ambient-Light hinter dem Hero.

- **Android TV Umsetzungs-Vorgabe:**
  - `Box` mit `AsyncImage` (Coil, `ContentScale.Crop`, `alignment = BiasAlignment(0f, -0.3f)` ≙ `center 35%`) + zwei Scrim-Overlays.
  - **Pfeil-Buttons entfallen** (Maus-Affordanz). Stattdessen: Auto-Rotation alle ~8 s mit `Crossfade(tween(400))`, pausiert sobald der Hero Fokus hat; manuelles Blättern über `DPAD_LEFT/RIGHT`, während `Play` fokussiert ist.
  - **Initialer Fokus** liegt auf dem `Play`-Button (`Modifier.focusRequester`, `LaunchedEffect { requestFocus() }`).
  - Ambient-Light: `RenderEffect`-Blur auf einer herunterskalierten Kopie des Backdrops, oder — günstiger — `Palette.from(bitmap)` → dominante Farbe als Radial-Gradient hinter dem Screen.
  - Overview: `maxLines = 3`, `TextOverflow.Ellipsis`.

### 2.3 Continue Watching / Media-Rails

- **Relevante Web-Code-Dateien:** `HomeViews.tsx` → `ContinueWatching`, `MediaCard.tsx`, `cinema.scss:469-619`. Genre-Rails zusätzlich in `src/components/homesections/sections/genreRows.ts`.

- **Visuelles & Verhaltens-Konzept:**
  Horizontale Rail, Gap 18 dp. Zwei Card-Varianten:
  - **Poster-Card** (Standard): `2/3`, Radius 14 dp, Titel + Jahr **unterhalb** des Bildes.
  - **Wide-Card** (`cinemaCard-wide`, für Continue Watching): `16/10`, Text **im Bild** auf Gradient-Scrim, Play-Icon-Badge unten rechts (36 dp Kreis), Fortschrittsbalken (3 dp) am unteren Rand, Episoden-Untertitel im Format `S{n} E{n} / {Titel}`.
  - Wide-Cards sind `playOnSelect` — die gesamte Card startet die Wiedergabe (`useCinemaPlayback`, `startPositionTicks` aus `UserData`), Poster-Cards navigieren zur Detailseite.
  - Genre-Rails: max. **12 Genres**, je **20 Items**, sortiert nach `PremiereDate desc` (`genreRows.ts:24-25`).

- **Android TV Umsetzungs-Vorgabe:**
  - `TvLazyRow` mit `items(key = { it.id })`. Card = `androidx.tv.material3.Card` mit `CardDefaults.colors()` und eigenem `scale`/`border`-Focus-Verhalten nach A2 + A3.
  - **Zwei Composables:** `PosterCard(240.dp, 2f/3f)` und `WideCard(16f/10f)` — Text-über-Bild-Variante mit `Brush.verticalGradient(0f to Transparent, 1f to Color(0xE6030910))`.
  - **Fortschritt:** Web legt ihn im TV-Layout aus der Overlay-Position in den normalen Fluss (`cinema.scss:977-981`) — auf Android den 3-dp-`LinearProgressIndicator` unterhalb des Bildes rendern, damit er nicht vom Fokus-Ring verdeckt wird.
  - **Select-Verhalten 1:1 übernehmen:** `DPAD_CENTER` auf Continue-Watching-Card → `PlaybackLauncher` mit Resume-Position; auf Poster-Card → Detail-Screen. `KEYCODE_MENU` / Long-Press → Kontextmenü (im Web: `.cinemaCardMenu`).
  - Scroll-Snap (`scroll-snap-type: x proximity`) wird auf TV **deaktiviert** — genau wie im Web (`cinema.scss:952-955`), weil es mit fokusgetriebenem Scrolling kollidiert. Stattdessen `bringIntoViewRequester` bzw. `pivotOffsets = PivotOffsets(0.1f)` für konstanten Fokus-Ankerpunkt.

### 2.4 Katalog-Grid (All / Genre / Collection-Inhalt)

- **Relevante Web-Code-Dateien:** `HomeViews.tsx` → `Catalog`, `InfiniteScroll.tsx`, `api.ts` → `useCinemaItems`, `cinema.scss:490-495`.

- **Visuelles & Verhaltens-Konzept:**
  Responsives Grid (`auto-fill, minmax(155px, 1fr)`), Toolbar mit Titel + Sortier-Select (`name` / `added` / `year`), Nachladen über einen `IntersectionObserver`-Sentinel.

- **Android TV Umsetzungs-Vorgabe:**
  - `TvLazyVerticalGrid(columns = GridCells.Adaptive(minSize = 155.dp))`, `verticalArrangement = 26.dp`, `horizontalArrangement = 20.dp`.
  - **Infinite Scroll:** `snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }` → bei Annäherung ans Ende `loadNextPage()`. Paging-3 (`PagingSource` auf die Jellyfin-API) ist die saubere Variante.
  - **Sortierung:** kein `<select>` — stattdessen Leanback-`GuidedStepFragment` oder ein `ModalBottomSheet`/`DropdownMenu` aus tv-material3 mit D-Pad-Liste.
  - Beim Verlassen des Grids muss die Fokus-Position erhalten bleiben → `Modifier.focusRestorer()` + `rememberSaveable` des letzten Index (Web-Äquivalent: `useCinemaFocus`, siehe 2.7).

### 2.5 Detailseite (Cinema-Hero-Kapsel)

- **Relevante Web-Code-Dateien:** `src/apps/legacy/features/itemDetails/cinemaLayout.ts`, `src/apps/legacy/controllers/itemDetails/index.js` + `index.html`, `themes/appletvmodern/theme.scss:1040-1240`.

- **Visuelles & Verhaltens-Konzept:**
  Der Legacy-DOM wird zur Laufzeit umgebaut (`prepareCinemaLayout`): Poster und Info-Ribbon wandern in einen neu erzeugten `.cinemaDetailHero`, Beschreibung + Track-Auswahl in `.cinemaDetailSummary`. Ergebnis ist eine **Kapsel** (Radius 32 dp, Gradient `#EB28313E → #F50E131B`, Shadow `0 1.5em 5em rgba(0,0,0,0.4)`, Blur 24 dp) mit **CSS-Grid `18em / 1fr`**: Poster links (Radius 18 dp), Titel/Meta/Buttons rechts, darunter volle Breite für den Rest. Hinter Zeile 1 liegt ein Pseudo-Element mit dem Artwork (`--detail-artwork`) unter einem horizontalen Gradienten `rgba(14,19,27,.78) → .92`.

- **Android TV Umsetzungs-Vorgabe:**
  - **Nicht den DOM-Umbau portieren** — das ist ein reiner Web-Workaround. Direkt die Zielstruktur bauen.
  - Leanback: `DetailsSupportFragment` mit `FullWidthDetailsOverviewRowPresenter` (`STATE_HALF`) + `DetailsOverviewLogoPresenter` für das Poster. Compose-TV: `Box` mit Backdrop + Scrim, darüber `Row { Poster(width = 288.dp, 2f/3f, RoundedCornerShape(18.dp)); Column { Title; MetaTags; ActionRow } }`, darunter `TvLazyColumn` mit Beschreibung, Staffeln/Episoden, Cast, Ähnliches.
  - **Action-Buttons** (`Play`, `Resume`, `Trailer`, `Watched`, `Favorite`) als Pill-Row, Styling nach A5 + Fokus-Ring §1.4. Erster Button erhält initialen Fokus.
  - Meta-Chips = `cinemaTag`: Radius 999, `#B81A232F`, Border `#38E4EFFF`, Text `13.sp`.

### 2.6 Suche

- **Relevante Web-Code-Dateien:** `src/apps/modern/routes/search.tsx`, `src/apps/modern/features/search/search.scss` + `api.ts`.

- **Visuelles & Verhaltens-Konzept:**
  Intro-Panel (Radius 26 dp) mit Überschrift, Eingabefeld (Radius 18 dp, Höhe 64 dp, `:focus-within` → Accent-Border + 3 px Ring) und Filter-Pills; darunter die Ergebnisse als Grid.

- **Android TV Umsetzungs-Vorgabe:**
  - Vorhandene `SearchFragment`/`SearchActivity` des Upstream-ATV beibehalten (Sprach-/Leanback-Suche), **nur neu skinnen**: `SearchOrbView`-Farbe auf `cinema_accent`, Ergebnis-Rows mit `PosterCard` aus 2.3.
  - Das Web-Freitextfeld nicht 1:1 übernehmen — auf TV übernimmt `SpeechRecognizer` + Leanback-Keyboard die Eingabe.
  - Filter-Pills → horizontale `TabRow` über den Ergebnissen.

### 2.7 Fokus-Engine & D-Pad-Navigation (Querschnitt — wichtigster Logik-Port)

- **Relevante Web-Code-Dateien:**
  `src/components/focusManager.js`, `src/scripts/keyboardNavigation.js`, `src/scripts/keyboardUtils.js`, `src/scripts/inputManager.js`, `src/components/scrollManager.js`, `src/apps/modern/features/home/useCinemaFocus.ts`.

- **Verhaltens-Konzept (das im Fork Erarbeitete):**
  1. **Mittelpunkt- statt Kantenvergleich.** Upstream-Jellyfin wählt das nächste Ziel über Rechteck-Kanten; der Fork schaltet im `directional`-Modus auf Mittelpunkte um (`focusManager.js`, `midX`/`midY`) — dadurch springt der Fokus bei unterschiedlich großen Cards nicht mehr in die falsche Spalte.
  2. **Containment-Regel.** Elemente, die vollständig im Rechteck des aktuell fokussierten Elements liegen (`contained`), werden bei horizontaler Navigation übersprungen — verhindert, dass der Fokus in Badges/Overlays *innerhalb* einer Card fällt.
  3. **Dialog-Scoping.** `getDefaultScope()` liefert den obersten sichtbaren Dialog statt `document.body`; `back` innerhalb eines Dialogs navigiert nie die Seite dahinter (`inputManager.js`).
  4. **Handled-Kontrakt.** `handleCommand` gibt jetzt `boolean` zurück; nur ein tatsächlich verarbeitetes Kommando konsumiert das Event.
  5. **Fokus-Gedächtnis.** `useCinemaFocus` merkt sich pro `location.key` den zuletzt fokussierten Knoten (über `data-focus-key` bzw. Index, LRU mit 30 Einträgen) und stellt ihn bei Rück-Navigation (`POP`) wieder her; ein `MutationObserver` re-fokussiert, wenn asynchron geladene Inhalte den Fokus verlieren.
  6. **Gerichtete Sprünge.** `ArrowDown` aus den Tabs überspringt den (zufälligen) Spotlight und landet direkt auf Continue Watching (`CinemaHome.tsx:67`).
  7. **Scroll-on-Focus** mit `block: 'nearest', inline: 'nearest'`.

- **Android TV Umsetzungs-Vorgabe:**
  | Web-Mechanismus | Android-Äquivalent |
  |---|---|
  | Mittelpunkt-Navigation | Android's `FocusFinder` arbeitet bereits mittelpunktnah → **kein Port nötig**. Reste durch explizite `nextFocusUp/Down/Left/RightId` bzw. `Modifier.focusProperties { }` absichern. |
  | Containment-Regel | Innere Elemente einer Card als `focusable = false` markieren; die Card ist ein einziges Fokus-Ziel. Aktionen laufen über Long-Press/`KEYCODE_MENU`. |
  | Dialog-Scoping | `Dialog`/`DialogFragment` fängt Fokus systemseitig; zusätzlich `Modifier.focusGroup()` + `BackHandler(enabled = dialogOpen)`. |
  | Handled-Kontrakt | `onKeyEvent { … ; true/false }` konsistent zurückgeben, nie pauschal `true`. |
  | Fokus-Gedächtnis (`useCinemaFocus`) | `Modifier.focusRestorer()` pro Row/Grid **plus** `rememberSaveable` des Index, damit es über Navigation hinweg überlebt. Leanback: `setSelectedPosition()` im `onResume` aus dem ViewModel. |
  | Gerichtete Sprünge | `Modifier.focusProperties { down = continueWatchingFocusRequester }` an der `TabRow`. |
  | Scroll-on-Focus | `bringIntoViewRequester.bringIntoView()` im `onFocusEvent`, bzw. `pivotOffsets` auf der `TvLazyRow`. |
