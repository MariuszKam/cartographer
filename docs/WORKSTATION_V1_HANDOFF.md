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

## Post-v1 extension — GUI-P13 final Workstation shell

GUI-P13 is a presentation/interaction redesign on top of the accepted P1-P12
performance architecture. It does not introduce a new source reader, cache,
session lifecycle, renderer authority, or worker pool.

The final shell is map-first:

```text
WorkstationView
├── WorkstationWorldBar
│   └── compact WorldPanel / save chooser
├── ToolNavigationPane (persistent Tool Rail)
├── workspace
│   ├── collapsible Context Dock
│   │   └── SearchPanel / source controls
│   ├── MapPanel (primary expanding region)
│   │   └── floating MapToolbar
│   └── collapsible ResultInspectorPane
│       ├── Inspect
│       ├── Results
│       ├── Layers
│       └── Diagnostics
└── WorkstationStatusBar
    ├── operation / progress / cancel
    └── viewport telemetry
```

The Layer panel is physically located in the Inspector dock because those
controls are retained/local view state. Collapsing either dock only changes the
JavaFX layout; it does not clear `MapFrame`, reopen the save, re-render the
raster, or reset viewport zoom/pan.

The source-control dock remains scoped by the P12 foreground/discovery rules.
The map, inspector and retained local interactions remain available according
to the existing responsive-operation contract.

## Important boundaries

Do not move JavaFX types into parser, save, scanner, application or render code. Keep DISPLAY and ABSOLUTE coordinates distinct. Preserve the separation between parsing, scanning, analysis and rendering.

Do not add mock analytical data or infer missing save metadata. Any future viewport metadata must come from verified renderer geometry.

## PF-2.7 Prepare World extension

The World Bar exposes an explicit `Prepare world` action for the selected
save revision. Preparation is separate from Render and runs in the existing
`FOREGROUND` scope, using the same Cancel and stale-result protection from
P12.

The badge shows revision-scoped `Not prepared / Partial / Ready` state and
compact Terrain/Surface/mapregion/ROCK/resource coverage. Status inspection is
cache-only and does not open the source game database.

Prepare World reports six monotonic phases: Header, Terrain, Surface,
Map regions, Geology and Resources. Verified phase state is checkpointed so a
cancelled operation can resume from already-derived artifacts. Refreshing an
already READY immutable revision preserves valid later-phase evidence until it
is actually revalidated.

Normal Render never implicitly starts full-world preparation. Compatible warm
renders continue to use PF-2.6 snapshot-backed paths; incomplete or unsupported
coverage keeps the authoritative source fallback.

PF-2.7 does not alter Surface fallback ordering/scanning semantics and does not
introduce top-down early-stop fallback.

## Final P13/P14 state

GUI-P13 implements the final map-first Workstation shell while preserving the
P1-P12 backend, retained-state and scoped-operation contracts.

GUI-P14 implements the executable acceptance workflow described in
`docs/GUI_PERFORMANCE_VALIDATION.md`.

Implementation is complete, but reviewer runtime evidence is intentionally not
invented here. The final factual status remains:

```text
IMPLEMENTATION COMPLETE
VALIDATION PENDING
```

Run the GUI-P14 workflow on the exact accepted candidate SHA. Only a PASS from
`guiReleaseGate` after the manual real-save campaign changes the redesign
status to validated/done.
