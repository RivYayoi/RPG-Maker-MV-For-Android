# RPG Maker MV Editor for Android — Native Java Port

**[简体中文](README.md)** | **[English](README_EN.md)**

> A native-Java Android port of the **RPG Maker MV editor** (the authoring tool),
> reproducing the PC editor's Main Window and its RPG Maker MV project format.

**Download:** [Release v1.0.0](https://github.com/RivYayoi/RPG-Maker-MV-For-Android/releases/tag/v1.0.0) ·
`RPGMakerMVEditor-Android-1.0.0.apk` (10.71 MB)
SHA-256 `d2b1eacbe853b0f22f5a237af5ff59fc2acf43d1313208a8e79caf1fbf628e72`

```bash
adb install -r -t RPGMakerMVEditor-Android-1.0.0.apk
```

| | |
|---|---|
| Language | **Pure Java** (no Kotlin, no JavaScript) |
| UI | **Native Android Views** (`View` / `Canvas` / `RecyclerView` / `PopupMenu` / `AlertDialog`) |
| Container | **None** — no WebView, no Capacitor, no HTML loaded |
| Storage | `java.io.File` + `org.json`, app-private directory |
| Dependencies | appcompat / recyclerview / constraintlayout / material (AndroidX) only |
| minSdk / targetSdk | 23 / 35 |
| Orientation | Portrait + landscape, layout adapts (see below) |

**Verified**: the APK's `assets/` contains only `template/` — **no `assets/public`** — and the
application code has **zero** references to `android.webkit`.

---

## Quick start (build from source)

RPG Maker MV's engine and art assets are © Kadokawa and are **not redistributed here**.
Copy the project scaffold out of your own RPG Maker MV installation first, then build:

```bash
# 1) fetch the MV scaffold (defaults to "C:\Program Files (x86)\KADOKAWA\RPGMV")
node scripts/fetch-template.mjs
# or point it somewhere else:
MV_INSTALL_DIR="D:\Games\RPGMV" node scripts/fetch-template.mjs

# 2) build the debug APK
cd app-java
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

You need the Android SDK (`platforms;android-35`, `build-tools;35.0.0`, `platform-tools`)
and JDK 17+. `app-java/local.properties` must point at your SDK:

```properties
sdk.dir=C\:\\path\\to\\android-sdk
```

---

## Why not a web shell: the Phase 1 finding

RPG Maker MV's **editor is not an NW.js app — it is a native Qt5/QML desktop program**:

| Whole-tree scan (204 JS files) | Hits | Files | **In the editor's own UI code** |
|---|---|---|---|
| Node.js core modules | 29 | 2 | **0** |
| NW.js desktop APIs | 5 | 2 | **0** |
| Native path assumptions | 41 | 5 | 1 (compiled into the exe, no JS surface) |
| IPC / renderer split | **0** | **0** | **0** |

`RPGMV.exe` contains `QtQuick` ×451 and `QQmlApplicationEngine` ×6; the binary inside
`nwjs-win/` is called `Game.exe` and its `www/` holds nothing but an `icon/` directory —
that is the **game player**, not the editor.

So there was no JavaScript to shim. The portable boundary is the **RPG Maker MV project
format**, which this port re-implements in Java. See
[`android-incompatibility.json`](android-incompatibility.json) for the full evidence.

---

## PC page mapping

The page inventory comes from the editor's **own manual** — `Help/page/01_03.html`
("Basic Editor Controls", the six Main Window components) and `01_04.html` (the menu bar).

### Main Window — all six components ported

The layout follows the manual's own screenshot `Help/inc/img/01_03_img01.png`:

| PC component | Native implementation |
|---|---|
| (1) Menu Bar | **Horizontal row** (File Edit Mode Draw Scale Tools Game Help); each title opens a dropdown `PopupMenu`, exactly like the desktop |
| (2) Toolbar | **Horizontal icon row**, 17 hand-drawn vector icons (new/open/save/undo/redo/zoom/mode/grid/new map/properties/database/plugins/resources…) |
| (3) Tile Palette | `TilePaletteView`: **vertical grid** in the left column, with the A / B / R tabs below (as on the PC) |
| (4) Map List | `MapListPanel`: **tree** (project → MAP001), collapsible, selection highlight, long-press to rename/delete |
| (5) Map View | `MapCanvasView` (hardware-accelerated `Canvas`), fills the right side |
| (6) Status Bar | Bottom **panelled** readout: `001:MAP001 (20x15) │ Map │ 100% │ Tileset 1 · Overworld` |

### Landscape / portrait adaptation

One layout, driven by resource qualifiers — the same behaviour as the desktop editor:

| Resource qualifier | Left column | Tile cell | Menu text | Tool icon |
|---|---|---|---|---|
| `values/` (portrait) | 150dp | 32dp | 13sp | 30dp |
| `values-land/` (landscape) | **230dp** | 40dp | 13sp | 32dp |
| `values-sw600dp/` (tablet) | **270dp** | 44dp | 14sp | 36dp |

Measured: 633px left column in landscape (5 tile columns), 413px in portrait (4 columns).

**Key detail**: `configChanges` deliberately **omits** `orientation|screenSize` so the
Activity is recreated on rotation — otherwise `values-land` is never re-resolved (this
was found by measurement: the column stayed 413px after rotating). The open map id is
preserved across the recreation via `onSaveInstanceState`.

### System-bar adaptation

`WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsCompat`:

- Status bar / navigation bar / display cutout / IME insets are all taken from
  `systemBars() | displayCutout()`
- The root layout is padded by those insets ⇒ the **PC menu bar is always below the
  system status bar**, and the **PC status bar is always above the navigation bar**
- The window background is set to `@color/bg`, so in landscape the strip beside the
  navigation bar shows the app's own colour instead of a black frame
- The IME inset is folded in for keyboard pop-ups (`adjustResize`, max of nav-bar and IME)

### Menu bar (item-for-item against 01_04.html)

```
[File]  Save Project / Close Project / Map Properties / Deployment / Exit
[Edit]  Undo / Redo / Copy / Paste / Delete
[Mode]  Map / Event / Region          ← Region is the manual's R tab
[Scale] Zoom In / Zoom Out / Actual Size / Fit to Map
[Tools] Database / Plugin Manager / Resource Manager
        Sound Test / Event Searcher / Character Generator
[Game]  Playtest / Open Folder
[Help]  About / Contents
```

### Standalone pages

| Page | Status |
|---|---|
| **Database** (01_08, 16 entity pages) | ✅ Master-detail: entity picker + record list + a form **generated from each record's JSON keys**. Actors/Classes/Skills/Items/Weapons/Armors/Enemies/Troops/States/Animations/Tilesets/CommonEvents/System/Types/Terms |
| **Plugin Manager** (01_05) | ✅ Parses the real `js/plugins.js`; enable switches, name/author/version/description editing, rewrites the file preserving the original wrapper text |
| **Resource Manager** (01_05) | ✅ Tree browser over 21 category roots, image preview (read-only) |
| **Map Properties** (01_07_03) | ✅ Parallax, encounter step, BGM, scroll type, tileset, map-list name |
| Map Design / Map Data Controls (01_07_01/02) | ⚠️ Drawing ✅; the rectangular selection frame exists but has no UI |
| Event Commands (01_10, 16 categories) | ⚠️ Event properties editable; the full command editor is not implemented |
| Sound Test / Event Searcher / Character Generator | ❌ Not implemented (the menu items are there and report it) |
| Deployment / Playtest | ❌ Not implemented (the desktop side depends on the NW.js runtime) |

---

## Format fidelity (verified across languages)

The Java implementation maps one-to-one onto the verified MV contract:

```
NewData/js/rpg_core.js:5273-5302    tileId constants and the kind/shape algebra
NewData/js/rpg_core.js:5040-5126    autotile bx/by and composition
NewData/js/rpg_core.js:5388-5424    FLOOR / WALL_AUTOTILE_TABLE
NewData/js/rpg_objects.js:5929      data[(z*height + y)*width + x]
```

End-to-end on a real Android 14 emulator (`analysis/native-e2e.mjs`, **19/19 pass**):

| Assertion | Result |
|---|---|
| Tile written by painting | `2816` (= `TILE_ID_A2`, the default ground) |
| `data[]` length | `1200` = 20 × 15 × 4 |
| **Schema vs the editor's own `Map001.json`** | **missing=[] extra=[]** |
| Reopen after restart | Pass |
| All 8 PC menu titles reachable (incl. scrolling) | Pass |
| Tools → Database dropdown → Database page | Pass (4 real Actors records) |
| Crash scan | CLEAN |

Landscape/portrait adaptation (`analysis/native-orientation.mjs`, **20/20 pass**):

| Assertion | Portrait | Landscape |
|---|---|---|
| All six PC components | ✅ | ✅ |
| All 8 menu titles | ✅ | ✅ |
| Menu bar below the system status bar | ✅ `menu.y1=66` | ✅ |
| Palette / map tree left of the map view | ✅ | ✅ |
| Left column width changes with orientation | 413px | **633px** |
| PC status bar at the bottom | ✅ | ✅ |
| Crash scan | CLEAN | CLEAN |

---

## Known boundaries (nothing guessed)

1. **Non-auto tiles (B/C/D/E/A5) are not offered.** Their set-number → source-image
   binding is compiled into `RPGMV.exe`'s Qt resources, and the Ghidra/Hopper REA
   providers are not installed on this host, so it cannot be read. The A1–A4 auto-tile
   origins *were* verified line by line and the block is resolved by probing. The
   tileIds written to disk are unaffected — the desktop editor renders them with its
   own correct mapping.
2. **Auto-tile shapes are not auto-derived**; the user-selected shape is written
   (default `0`).
3. **The event command editor** is not implemented.
4. **Only 4 tileset sheets are bundled** (the default tileset). The full `img/` is
   218 MB and `audio/` is 162 MB.

---

## Layout

```
RPG-Maker-MV-For-Android/
├── README.md                    Chinese version (the default landing page)
├── README_EN.md                 ← this file (English)
├── LICENSE
├── android-incompatibility.json REA boundary map
├── app-java/                    pure-Java Gradle project (the product)
│   ├── app/src/main/java/com/rmmv/editor/
│   │   ├── MainActivity.java    project list ([File] entry point)
│   │   ├── core/                TileId / MvMap / MapInfos / Tileset /
│   │   │                        Project / ProjectStore / Session
│   │   └── ui/                  MapEditorActivity / MapCanvasView /
│   │                            TilePaletteView / MapListPanel /
│   │                            MapPropertiesActivity / DatabaseActivity /
│   │                            PluginManagerActivity / ResourceManagerActivity
│   │                            + db/ plugin/ resource/ helpers
│   ├── app/src/main/res/
│   │   ├── layout/              activity_map_editor.xml (the PC Main Window)
│   │   ├── values/      dimens.xml       portrait sizes
│   │   ├── values-land/ dimens.xml       landscape sizes (PC proportions)
│   │   ├── values-sw600dp/dimens.xml     tablet sizes
│   │   └── drawable/            17 hand-drawn vector icons + button backgrounds
│   └── app/src/main/assets/template/  MV scaffold (generated by fetch-template.mjs)
├── scripts/fetch-template.mjs   copy the scaffold from a local RPG Maker MV install
├── scripts/publish-release.mjs   create the GitHub Release and upload the APK
├── analysis/                    evidence scripts (boundary scan, device acceptance)
├── evidence/                    device screenshots
├── docs/                        feature matrix / compatibility plan / logcat notes
└── dist/                        build output (the APK is not tracked, see .gitignore)
```

### Relationship to the previous (WebView) build

The `app/` directory holds the earlier Capacitor + WebView implementation for
comparison and is **not tracked** (superseded by `app-java/`, and it would duplicate
7 MB of scaffold plus a second Gradle project).

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/功能支持矩阵.md`](docs/功能支持矩阵.md) | ✅/⚠️/❌ support matrix per feature module and the known boundaries (Chinese) |
| [`docs/compatibility-layer-plan.md`](docs/compatibility-layer-plan.md) | Compatibility-layer design: each Phase-1 boundary mapped to its implementation (Chinese) |
| [`docs/logcat-findings.md`](docs/logcat-findings.md) | Key logcat excerpts from the device and the open-issues list (Chinese) |
| [`android-incompatibility.json`](android-incompatibility.json) | The REA boundary map (machine-readable) |

---

## License

The **code** in this repository is released under the MIT License (see [`LICENSE`](LICENSE)).

RPG Maker MV's engine and art assets are **not included** — they belong to Kadokawa and
are copied from your own RPG Maker MV installation by `scripts/fetch-template.mjs`.
RPG Maker MV itself is subject to its own terms; please refer to them.
