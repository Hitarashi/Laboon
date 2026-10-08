# Laboon visual extensions, contract 1

Laboon themes are separately installed Android packages. They contain a manifest, a declarative
visual definition, and optional local assets. Laboon reads those resources and interprets the
definition with `:theme-renderer`; it never loads extension Kotlin, Compose classes, or application
code into the player process.

The first contract release supports one active extension. The built-in option is Laboon's Material 3
Expressive appearance. An extension can override selected screens and theme tokens; any omitted
screen uses the built-in presentation. The playback engine, services, credentials, permissions, and
playback decisions stay inside Laboon.

## Author a theme package

Add an activity with the discovery action, and put the manifest resource id on the `<application>`
element:

```xml
<application ...>
    <activity android:name=".ThemeInfoActivity" android:exported="true">
        <intent-filter>
            <action android:name="org.shilpo.laboon.action.VISUAL_THEME" />
            <category android:name="android.intent.category.DEFAULT" />
        </intent-filter>
    </activity>
    <meta-data
        android:name="org.shilpo.laboon.visualTheme"
        android:resource="@raw/visual_theme_manifest" />
</application>
```

The manifest is JSON in `res/raw/visual_theme_manifest.json`:

```json
{
  "id": "theme.example.midnight",
  "name": "Midnight",
  "author": "Example",
  "version": "1.0.0",
  "contractVersion": 1,
  "minimumAndroidApi": 29,
  "definitionAsset": "laboon/theme.json"
}
```

Place `theme.json` at `assets/laboon/theme.json`. Validate it
with [visual-theme.schema.json](visual-theme.schema.json) and validate the manifest
with [visual-theme-manifest.schema.json](visual-theme-manifest.schema.json). [Hitarashi](../../Laboon%20Themes/Hitarashi/app/src/main/assets/laboon/theme.json)
is the reference package, including its API 33 AGSL shader and companion activity.

## Definition capabilities

The renderer supports boxes and overlays, weighted rows and columns, vertical and horizontal
scrolling, lazy columns and rows, adaptive lazy grids, text, host-owned text fields, metadata, icon
glyphs, artwork, packaged images, surfaces, controls, progress, clipping, transforms, gradients,
Material color roles, Material shapes, and effect attachment. A lazy collection uses
`attributes.itemsBinding`; its first child is a template rendered once for each row. Use a direct
`binding` attribute (for example, `"binding": "track.title"`) for a single value, or `{{key}}` in
text for interpolation. `minScreenWidthDp` and `maxScreenWidthDp` let a node appear only at matching
window widths. Use `weight` inside a row or column to allocate remaining space.

Attach `gestureAction`, `gestureAxis` (`horizontal` or `vertical`), and an optional
`gestureProgressBinding` to a container to recognize a drag. `gestureDirection` can be `positive` or
`negative`; progress ranges from -1 to 1 and is included in the semantic action parameters when the
drag crosses its threshold. Bind `morphProgressBinding` with `morphStartDp` and `morphEndDp` on a
clipped node to morph its corner radius as host presentation progress changes. Transform bindings
include `alphaBinding`, `scaleBinding`, `rotationBinding`, `translationXBinding`, and
`translationYBinding`.

Set `parameter.<name>` on a button or icon button to pass a fixed or binding-resolved value with its
semantic action. For example, `parameter.search.filter: "albums"` asks the host search action to use
the albums filter. The host validates the value and owns the operation.

Laboon currently provides presentation values for the selected screen and tab, loading/error state,
display name, current track metadata and artwork, playback progress/state, audio quality, lyric
state, spectrum values, queue state, search query/filter/loading/error, and theme options. Home
definitions can bind `tracks`, `artists`, `albums`, and `queue` collections. Queue rows also receive
`queue.entryId`, `queue.index`, and `queue.origin`. Player definitions can bind `spectrum.bands`,
`playback.lyrics`, and `playback.qualityVariants`; selecting a quality variant uses the
`selectAudioQuality` action with a `quality.format` parameter. The `visualizer` screen receives the
`rip.tasks` collection; its `cancelRipTask` action accepts the row's `rip.taskId`. Search
definitions can bind `search.tracks`, `search.albums`, `search.artists`, `search.playlists`,
`search.stations`, and `search.topResults`. Album screens receive `album.title`, `album.artist`,
`album.artworkUrl`, `album.releaseDate`, `album.trackCount`, `album.loading`, `album.hasError`, and
`album.error`, plus the album's `tracks` and alternate-version `albums` collections. Artist screens
receive the artist's name/artwork and its `tracks`, `albums`, and similar-artist `artists`
collections. Record-label screens receive label name/artwork and its release `albums` and `artists`.
A node's `visibleBinding` can hide loading and error content until its boolean binding becomes true.
Definitions receive strings and image references, never credentials, repositories, mutable playback
objects, or service clients.

Controls request a fixed semantic action by its documented id. Laboon passes only actions supported
on that screen to the renderer and performs the existing app action. Current ids include `back`,
`continue`, `openTelegram`, `connectLastFm`, `connectListenBrainz`, `requestPermission`,
`openSettings`, `openSettingsCategory`, `openHome`, `openSearch`, `openLibrary`, `openPlayer`,
`playPause`, `playTrack`, `playNext`, `addToQueue`, `previousTrack`, `nextTrack`, `seek`,
`openQueue`, `openAlbum`, `openArtist`, `ripTrack`, `toggleShuffle`, `cycleRepeat`, `refresh`,
`retry`, `dismissPlayer`, `openThemePicker`, `signOut`, `selectAudioQuality`, `search`,
`openVisualizer`, `cancelRipTask`, `playQueueEntry`, `removeQueueEntry`, `moveQueueEntry`,
`clearQueue`, `promoteAutoplay`, and `retryDiscovery`. `moveQueueEntry` accepts `queue.index` and
`queue.direction` (`up` or `down`); the other queue actions use the row's `queue.entryId` or
`queue.index`. `openSettingsCategory` accepts a `settings.category` value from the
`settingsCategories` collection (`audio_pipeline`, `lyrics_engine`, `appearance`, `services`,
`storage`, or `about`). Settings category screens may be overridden with names such as
`settings.appearance`; absent category screens use the stock settings content. On the `search`
screen, a text field with `field: "search.query"` can be paired with a `search` button; optional
`parameter.search.filter` accepts `top-results`, `artists`, `albums`, `songs`, `playlists`,
`stations`, or `music-videos`. Laboon returns result collections and executes searches through its
existing repository. Unknown action ids reject the definition. An unavailable action renders
disabled.

Partial definitions are encouraged. A theme can supply `splash`, `welcome`, `permissions`,
`connect`, `lastFm`, `listenBrainz`, `home`, `search`, `library`, `settings`, `player`, `queue`,
`album`, `artist`, `label`, `albumRelated`, or `visualizer` screen trees; unsupported or absent
entries fall back to Laboon's stock screen. Authentication and permission actions remain host-owned.
Sensitive text-field values are transient host input and are not substituted into text bindings or
exposed as presentation data. A persistent host recovery control remains available while a custom
screen is active.

## Options, motion, and effects

Theme options are declared with `type: "switch"`, `type: "choice"`, or `type: "slider"`. Values are
bounded and stored separately for each extension. A numeric option whose id matches an effect
uniform can tune that uniform. Options are presentation-only and cannot alter playback rules.

The renderer uses `durationScale`, `springDampingRatio`, and `springStiffness` for extension screen
transitions. The host continues to handle input, accessibility semantics, action availability, and
reduced-motion settings. Definitions should keep content readable under large font scales, preserve
TalkBack labels, and avoid relying on motion to communicate state.

Effects name a packaged shader asset. A general effect receives the rendered child through the AGSL
`content` input and may declare a `resolution` float2. The built-in `glassSurface` effect also
receives Laboon's captured backdrop for refraction. Contract 1 executes AGSL only on Android 13 or
later; any theme using AGSL must declare `minimumAndroidApi: 33` or greater. Laboon compiles shaders
before activation, caches packaged assets and per-surface effects, and falls back to the working
appearance if an installed theme becomes incompatible or invalid. It does not down-convert a shader
for older Android versions.

Each definition, manifest, and asset is size-limited. Trees have bounded depth and node count;
options, screens, colors, effects, and typography entries are capped. Asset paths cannot escape the
extension package. Invalid packages are skipped, and an invalid update/uninstall clears the active
selection to stock M3 Expressive.

## Test and release

Test the package on Android 10/API 29, Android 12/API 31, Android 13/API 33, and the current Laboon
target. Also test TalkBack, font scaling, rotation, process recreation, reduced motion, large window
resizing, invalid shader compilation, malformed JSON, missing assets, package update, and uninstall.
The host validates package compatibility before preview/apply and rechecks installed extensions when
it resumes.

## Contract 1 motion, drawing and typography

Named `motion.keyframes` define `durationMs` and 2–64 `{fraction, progress}` frames. Times must
increase from `(0, 0)` to `(1, 1)`; bounded overshoot is supported. Attach a name using
`alphaAnimation`, `scaleAnimation`, `rotationAnimation`, `translationXAnimation`,
`translationYAnimation`, or `morphAnimation`. `spring` selects the global spring. Optional
`<property>From` attributes define entry values. Every animation respects the host motion scale;
hidden screen trees stop being composed after their exit transition.

Matching `sharedElementKey` attributes connect artwork across host screen transitions. Drag progress
can drive transforms and corner-radius morphs. For compatible polygon morphs, provide
`polygonPoints` and `morphPolygonPoints` as semicolon-separated `x,y` pairs in normalized
coordinates. Both polygons must have the same 3–64 vertices; bind `morphProgressBinding` and attach
`morphAnimation`. Hitarashi demonstrates a circle-to-cookie polygon morph through this public
interface.

`drawing` supplies host-rendered `circle`, `line`, or rounded-rectangle layers through
`drawingKind`, `color`/`gradient`, `strokeDp`, and `radiusDp`. `flowRow` wraps controls under
resizing and font scaling. Package a font and reference it from `typography.<style>.fontAsset`; font
sizes, weights and letter spacing remain bounded. `colorSource: "artwork"` requests a host-generated
palette from the current track artwork; omitted/system uses wallpaper colors on Android 12+ and a
fixed Material palette on Android 10–11. Color-role overrides are applied afterward.

General AGSL effects require `uniform shader content`. The public backdrop effect `glassSurface`
requires `uniform shader img`, `resolution`/`center`/`size` float2 uniforms, `radius` float4,
`thickness`, `refract_index`, `refract_intensity` floats and `foreground_color_premultiplied`
float4. The host owns backdrop capture and geometry. A shader interface or compilation failure
rejects apply and preview; no Android 10 graphics fallback is synthesized.

## Additional surfaces and actions

`settings.audio_pipeline`, `settings.lyrics_engine`, `settings.services`, `settings.storage` and
`settings.about` can override category content. Categories receive `settings.title`,
`settings.subtitle` and `settings.features`; these preserve existing planned-feature information.
About receives `about.version`, `about.releaseTag`, `about.loading`, `about.changelog` and
`about.contributors`. `openProjectLink` accepts `project.linkId` (`repository`/`issues`),
`openContributorProfile` accepts a host-listed `contributor.login`, and `copyBuildInfo` copies the
app version.

`openLyrics` and `openAudioInfo` open `lyrics` and `audioInfo`. Lyrics receive `lyrics.lines` with
index, text, timing, active state and romanization. `seek` with `lyrics.index` selects an existing
line; `openLyricsShare` opens `lyricsShare`. Sharing receives `share.lines`,
selected-count/busy/mode/romanization/translation values. `toggleLyricLine` accepts `lyrics.index`;
the host enforces 1–6 selected lines. `setLyricsShareOption` accepts `share.option` (`mode`,
`romanization`, `translation`) and `share.value` (`card`/`text` for mode). `copyLyrics` and
`shareLyrics` use Laboon's existing clipboard and sharing operations. Android's external share
chooser remains platform-owned. `audioInfo` exposes codec, container, bit depth, sample rate,
bitrate, channels, decoder, output engine/device/protocol and latency.

`themePicker` receives `themes.installed` and `themes.selectedId`; `previewTheme` accepts a listed
`theme.id` and `restoreDefault` restores stock presentation. A `hostControl` with
`id: "themeOptions"` places the Laboon-managed option controls in an extension's layout. Give that
control an explicit height. Preview/apply approval and the independent recovery dialog remain
host-managed. The recovery control is always available; a main-thread presentation crash clears the
selected theme for the next launch and records a recovery notice.

## Validate and build

Install the authoring dependency with
`python3 -m pip install -r tools/requirements-theme-validation.txt`, then run:

```sh
python3 tools/validate_visual_theme.py /path/to/res/raw/visual_theme_manifest.json /path/to/assets --android-api 33
```

The authoring validator checks JSON Schema, semantic actions, keyframes, polygon compatibility,
nested scrolling bounds, paths, assets and budgets. Android additionally decodes images/fonts and
compiles AGSL before activation. Limits are 512 KiB per file, 8 MiB total referenced assets, 128
assets, 2,000 tree nodes, depth 24 and approximately 16 million decoded pixels total after image
sampling. No parsing or package reads belong in a frame callback.

Build the SDK artifacts with
`./gradlew :theme-contract:assembleRelease :theme-renderer:assembleRelease`; the AARs are under each
module's `build/outputs/aar`. Theme authors only need the manifest/schema and packaged assets;
including executable SDK classes in a theme package does not make Laboon load them.

Runtime release validation is tracked in [visual-theme-validation.md](visual-theme-validation.md).
Build/schema/unit-test success does not establish frame-time performance, accessibility or visual
parity on a device.

A `backdrop` node captures one visual background per screen for `glassSurface`. Place it first in an
overlay, with foreground content and effects as siblings. Captured descendants cannot contain
effects or another backdrop, preventing recursive layer rendering. Hitarashi supplies a
palette-driven gradient through this public capture primitive. Host graphics execution stays behind
API 33 guards.
