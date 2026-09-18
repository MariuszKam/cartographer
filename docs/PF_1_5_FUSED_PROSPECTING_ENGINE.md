# PF-1.5 Fused Prospecting Engine

Status: IMPLEMENTED — validation pending.

## Motivation

The previous production path rendered the upper-rock map and then asked the
saved ore provider to traverse the same chunk area once for each requested ore
resource. That made physical work grow with `resources × chunks`.

## Data flow

`FusedProspectingEngine` loads metadata and the block registry once, builds one
`ProspectingScanPlan`, and calls the PF-1.2 coverage-aware selective reader
once. Its callback immediately passes each terminal visit to the PF-1.3
`RockStreamingSession` and to the compiled ore classifier. Decoded chunks are
not retained after the callback returns.

The reader receives one union of natural-rock IDs and all requested ore IDs.
Palette rejection therefore happens once per candidate chunk. A chunk with a
matching palette is fully decoded at most once for the operation.

## Classifier and memory model

Registry interpretation and ore-code normalization occur during plan
construction. The hot path uses a sorted primitive block-ID array and compact
offset/membership arrays. This supports arbitrary sparse IDs and does not
impose a 64-resource semantic limit. The operation retains only the plan,
classifier, primitive result flags, PF-1.3 compact rock state, and PF-1.2's
bounded in-flight decode state:

```text
O(plan + classifier + result aggregates + bounded decode working set)
```

No decoded-chunk list, per-resource chunk map, or independent executor is
introduced.

## Correctness and determinism

The PF-1.3 session remains authoritative for upper-rock scanning and coverage
classification. It receives every visit, including missing, failed, and
palette-rejected visits. Ore observations preserve the existing semantics:
matching blocks produce `OBSERVED`; a failed/missing requested visit produces
`UNAVAILABLE` when no match was observed; otherwise the result is
`NOT_OBSERVED`. Resource output order remains the existing evaluator order.

The fused accumulator stores only boolean observation state, so decode
completion order cannot change the result. Rock finalization remains the
existing completion-order-independent PF-1.3 implementation.

## Migration and compatibility

`ActualOreObservationProvider` remains the single-resource convenience
contract. `SavedOreObservationProvider` additionally implements
`FusedProspectingObservationProvider`; the production prospecting use case
uses the fused capability when present. Other providers remain usable for
characterization and reference tests.

## Validation gates

Required legacy/reference parity coverage, targeted fused architectural tests,
full tests, bounded-concurrency checks, real-save checksum validation, and
JFR/macro measurements remain reviewer gates. A focused fused traversal test
was added; the test suite and runtime gates were not run by Codex in this
change.

## Known limitations

The existing public request is still a single prospecting operation; batching
is internal to that operation. The former single-resource provider behavior is
covered by the convenience delegation and remains available through injected
non-fused providers for characterization. Runtime full-decode/pass counters
and JFR integration are supplied by the existing PF-1.2 telemetry but were
not executed here.
