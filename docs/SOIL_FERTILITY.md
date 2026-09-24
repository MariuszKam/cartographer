# Soil Fertility Map Layer

`SOIL_FERTILITY` is an optional Cartographer visualization layer derived from
decoded Vintage Story block evidence. It visualizes nominal fertility
categories represented by block codes.

It is not:

- a simulation of current crop health;
- current N/P/K nutrient state;
- an inference through snow or water; or
- an OreMap/worldgen probability layer.

## Supported fertility tiers

The supported nominal tiers are:

| Tier | Nominal value |
| --- | ---: |
| Bony | 0% |
| Barren | 5% |
| Low | 25% |
| Medium | 50% |
| High | 65% |
| Terra Preta | 80% |

These are nominal tier values used by the visualization. They are not live
farmland nutrient percentages.

## Supported evidence

The classifier supports these exact block-code families:

**Bony soil**

- `bonysoil`
- `bonysoil-1` through `bonysoil-7`

**Forest floor**

- `forestfloor-0` through `forestfloor-7`

**Ordinary soil**

- `soil-verylow-*`
- `soil-low-*`
- `soil-medium-*`
- `soil-high-*`
- `soil-compost-*`

Supported ordinary-soil surface variants are `none`, `verysparse`, `sparse`
and `normal`.

**Farmland**

- `farmland-dry-verylow`
- `farmland-moist-verylow`
- `farmland-dry-low`
- `farmland-moist-low`
- `farmland-dry-medium`
- `farmland-moist-medium`
- `farmland-dry-high`
- `farmland-moist-high`
- `farmland-dry-compost`
- `farmland-moist-compost`

Matching is conservative. Unsupported or malformed block codes remain
unclassified. Explicit non-`game` namespaces are not automatically treated as
having the vanilla semantics described here.

## Farmland semantics

A farmland block code represents the nominal fertility tier used by this
feature. Current farmland N/P/K values are not parsed.

Consequently, `farmland-moist-high` is rendered as the HIGH nominal fertility
tier. It does not mean that the current N, P and K state is literally 65%.
A future live nutrient feature would require verified farmland block-entity
state parsing.

## Surface evidence rules

**CROPS**

Crops are treated as foliage by the surface pipeline. When crop foliage
obscures the RainHeight target, the existing fallback surface scan may continue
down to underlying farmland. `SoilFertilityClassifier` does not classify crops
themselves.

**FOREST FLOOR**

Forest floor is intentionally ground evidence, not foliage.

**SNOW**

Snow is not fertility evidence. The Soil Fertility renderer deliberately does
not infer through snow, so a snow-covered column can have no fertility overlay.

**WATER**

Water is treated as obscuring fertility evidence. A WATER surface cell must
not produce a fertility overlay even if its solid block code happens to look
fertility-bearing. Lake and river-bed soil fertility is not inferred.

**MISSING DATA**

Missing or unclassified evidence remains missing. No neighboring-column
interpolation or guessed substrate is used.

## Data pipeline

1. A render request selects `RenderLayer.SOIL_FERTILITY`.
2. The application surface pipeline requires surface data when either
   `SURFACE` or `SOIL_FERTILITY` is selected.
3. `SURFACE` and `SOIL_FERTILITY` share the same surface data scan.
4. No duplicate fertility-specific full surface scan exists.
5. Exact `SurfaceMap` evidence and the block registry are passed to
   `MapRenderer` when fertility is enabled.
6. `SoilFertilityOverlayRenderer` resolves block identities through the
   registry and performs conservative fertility classification.
7. Markers are rendered after the fertility overlay.

The RainHeight fast path and existing fallback scanning are reused. There is no
separate `SoilSubstrateScanner`.

## Workstation

The Workstation has a dedicated **Map** tool/mode. Map mode can render the base
map without Ore overlays.

Its layer controls include:

- Terrain
- Surface
- Soil Fertility
- Markers

Soil Fertility is off by default. Terrain, Surface and Markers retain their
previous default-on state. Soil Fertility does not automatically enable
Surface and can be selected independently.

Map mode reuses the existing `RenderActualOreMapUseCase` with no ore match and
no ore overlays. It is not a separate backend Map use case.

## Rendering

Soil fertility colors are Cartographer visualization colors; they are not
claimed to be official Vintage Story minimap colors. The legend title is
**Nominal Fertility**.

Fertility renders above the ordinary Surface visualization, and markers render
above fertility. Unsupported columns remain unchanged by the fertility
overlay. The palette implementation is the source of truth for the colors.

## Safety

- `.vcdbs` saves remain strictly read-only.
- Block identities are resolved through the save registry.
- No production fertility block IDs are hard-coded.
- No save data is modified.
- Missing data is not guessed.

## Validation gates

Before merge, the following validation procedure is required:

1. Run the full Gradle test suite.
2. Perform a real-save Workstation smoke test.
3. Test with Soil Fertility ON.
4. Test with Soil Fertility ON while Surface is OFF.
5. Perform an ordinary Map render without Soil Fertility.
6. Visually inspect that fertility appears only where supported evidence exists.
7. Verify PR CI.

These are required gates, not validation results recorded by this document.
The reviewer/controller records the actual evidence separately.
