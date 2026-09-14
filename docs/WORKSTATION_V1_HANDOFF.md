# VS Cartographer — Workstation v1 Handoff

## Status

Workstation v1 is complete.

Runtime validation was performed manually by the reviewer.

Final reviewer validation:

```powershell
.\gradlew.bat test
```

The Workstation implementation is on `master`. The latest handoff-related commit is:

```text
17c7818 fix(test): update reader doubles for progress overloads
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
└── WorkstationView
    ├── WorldBar / WorldPanel
    ├── ToolNavigationPane
    ├── SearchPanel
    ├── LayerPanel
    ├── MapPanel
    │   ├── MapToolbar
    │   └── ScrollPane → StackPane → ImageView
    ├── ResultInspectorPane
    │   └── DiagnosticsPane
    └── WorkstationStatusBar
```

`CartographerDesktopApp` remains the composition root and orchestration layer. Panels expose semantic APIs and keep their JavaFX controls private.

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
- No instant layer compositing, layer cache, zoom persistence or additional map analysis features.
- No Layers sidebar beyond the real Terrain / Surface / Markers controls.
- `.vcdbs` files remain read-only.

## Important boundaries

Do not move JavaFX types into parser, save, scanner, application or render code. Keep DISPLAY and ABSOLUTE coordinates distinct. Preserve the separation between parsing, scanning, analysis and rendering.

Do not add mock analytical data or infer missing save metadata. Any future viewport metadata must come from verified renderer geometry.

## Next work

Per `AGENTS.md`, the next implementation target is **1.0a Explored World Coverage**:

```text
coverage inspect <save.vcdbs>
coverage render <save.vcdbs> --out <coverage.png>
```

This is separate from the completed Workstation v1 UI work and requires unit tests, real-save CLI validation and PNG inspection before sign-off.
