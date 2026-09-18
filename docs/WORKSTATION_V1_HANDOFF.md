# VS Cartographer — Workstation v1 Handoff

## Status

Workstation v1 is complete.

A post-v1 GUI performance redesign (GUI-P1 through GUI-P10) now adds the
shared PF-1.x data pipeline, 4K raster contract, retained compact map state,
local layer recomposition, and retained-operation reuse. Automated/runtime
acceptance for the final GUI-P10 stack is tracked in
`docs/GUI_PERFORMANCE_VALIDATION.md`.

The original Workstation v1 runtime validation was performed manually by the
reviewer. That historical validation must not be treated as validation of the
new R2048/R4096 and retained-render paths.

Final reviewer validation:

```powershell
.\gradlew.bat test
```

The Workstation implementation is on `master`. The latest handoff-related commit is:

```text
See the current accepted Git ref and GUI_PERFORMANCE_VALIDATION.md evidence.
```

## Delivered scope

- Workstation shell with world/save controls, tool navigation, contextual options and central map viewport.
- Tools: Ores, Surface, Geology and Prospecting.
- Interactive map toolbar: zoom out, zoom in, Fit, Center Player hook and Reset.
- Real Terrain / Surface / Markers layer selection wired into supported render requests.
- Right-side Result Inspector with concise results and collapsible technical diagnostics.
- Collapsible, scrollable left and right sidebars.
- Dark theme, compact world/status bars and final layout polish.
- Determinate stage-local progress where current/total are real; indeterminate fallback elsewhere.

## Main UI structure

```text
CartographerDesktopApp
└── WorkstationController
    └── WorkstationView
        ├── WorldBar / WorldPanel
        ├── ToolNavigationPane
        ├── tool-specific option panes
        ├── LayerPanel
        ├── MapPanel
        │   ├── MapToolbar
        │   └── ScrollPane → StackPane → ImageView
        ├── ResultInspectorPane
        │   └── DiagnosticsPane
        └── WorkstationStatusBar
```

`CartographerDesktopApp` is the JavaFX composition root.
`WorkstationController` owns orchestration and operation routing. Panels
expose semantic APIs and keep their JavaFX controls private.

## Progress architecture

`cartographer.application.ProgressReporter` is the neutral callback contract. The CLI reporter extends it without introducing a UI dependency.

JavaFX render tasks use the private `ProgressTask<T>` bridge in `CartographerDesktopApp`; backend callbacks call wrapper methods, which update Task properties. UI listeners then update `WorkstationView` and `WorkstationStatusBar`.

- ORE: real progress from mapchunk/chunk reads, terrain preparation and image rendering; indeterminate during unmeasured overlay phases.
- Surface: real progress from mapchunk/chunk reads, surface scanning and rendering; indeterminate during object analysis and overlay painting.
- Geology: real progress during chunk loading; indeterminate during rock analysis and geology rendering.
- Prospecting: indeterminate because no reliable denominator is exposed.
- Save discovery: indeterminate because resource/player loading has no single reliable total.

Progress is hidden on both success and failure. No fake time-based or weighted phase percentages are used.

## Intentional limitations

- Center Player remains disabled because no verified player-pixel metadata contract exists.
- Radius overlay and cursor X/Z coordinates are not implemented; coordinate geometry must not be guessed.
- Local recomposition is available only when the current `MapFrame` retains
  all data required by the requested layer set. Missing data requires an
  explicit full Render; there is no hidden source IO.
- Environment/Geology local toggles require previously interpreted map-region
  overlay state; otherwise Render reads the missing map-region data.
- Surface Object discovery remains a distinct selective source operation.
- The current runtime does not claim LOD, atlas-backed rendering, or true
  incremental rendering.
- `.vcdbs` files remain read-only.

## Post-v1 extension — Map / Soil Fertility

- A dedicated Map tool/mode provides base-map rendering without ore overlays.
- Soil Fertility is a real render layer exposed in `LayerPanel` and is off by default.
- Soil Fertility can be enabled independently from Surface.
- `ResultInspectorPane` has a Map-specific result view.
- Map rendering reuses the existing `RenderActualOreMapUseCase` and render pipeline.
- No JavaFX dependency was introduced into application, render, scanner or save code.

## Important boundaries

Do not move JavaFX types into parser, save, scanner, application or render code. Keep DISPLAY and ABSOLUTE coordinates distinct. Preserve the separation between parsing, scanning, analysis and rendering.

Do not add mock analytical data or infer missing save metadata. Any future viewport metadata must come from verified renderer geometry.

## Next work

Complete the acceptance matrix in `docs/GUI_PERFORMANCE_VALIDATION.md` on the
accepted GUI-P10 HEAD. In particular, record real-save R2048/R4096 behavior,
local recomposition smoke results, cache diagnostics, and source-safety
evidence before declaring the performance-oriented GUI redesign DONE.
