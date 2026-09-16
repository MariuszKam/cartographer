# PF-1.1 Decode Memory Model

**Status:** `VALIDATED`

**Branch:** `perf/p1.1-decode-memory-model`

**Historical baseline:** `2149cf2781fec75c67d417d69a1c378d4cc94f6b`

**Validated candidate before documentation closure:** `4fdce9918b07ed4eccef7dac7bd7e8b2029d0c30`

**Validation date:** `2026-09-16`

`VALIDATED` means that the PF-1.1 implementation and the defined reviewer evidence gates passed. It does not mean that the work has been merged to `master` or that product/release acceptance has been granted.

## Objective and memory model

PF-1.1 establishes a safe decode memory model by removing unnecessary decoder copy/allocation amplification, making decoded-layer ownership explicit, preserving immutable published chunk data, and reusing bounded decoder scratch safely. It also preserves corruption diagnostics, keeps `not decoded != empty`, retains read-only `.vcdbs` semantics, and prepares the decoder lifecycle for later streaming stages.

The intended lifecycle is:

```text
decode
  -> reusable bounded workspace
  -> one owned published decoded layer
  -> workspace reused only after decode consumer ownership is independent
```

Published decoded arrays never alias reusable workspace scratch. The public array-facing APIs remain defensive even though the trusted parser path transfers ownership of immutable decoded layers.

## Checkpoint history

The following checkpoint commits are taken from the actual history between the historical baseline and the candidate.

| Checkpoint | Commit | Outcome |
|---|---|---|
| A — Decode Contract & Layer Semantics | `586c599` — `perf: define unavailable liquid layer semantics` | Distinguished unavailable liquids from decoded-empty liquids; unavailable access fails explicitly, preserving `not decoded != empty`. |
| B — Safe Ownership Transfer | `2f4f1a3` — `perf: transfer decoded layer ownership safely` | Added immutable `DecodedChunkLayer` ownership transfer without the redundant final parser copy; public array construction and accessors remain defensive. |
| C — Decode Algorithm Cleanup | `7d3b0f5` — `perf: remove chunk decode intermediates` | Removed compressed palette/bit-plane source copies and production `int[][]` bit-plane materialization; decoded bit planes are extracted directly. |
| D — ChunkDecodeWorkspace | `b5817c5` — `perf: reuse bounded chunk decode workspace` | Added reusable bounded scratch, lazy reusable `ZstdDecompressCtx`, worker-scoped workspace pooling, exclusive borrowing, and deterministic native-resource close. |
| E — Decoder-specific JMH | `4fdce99` — `perf: add chunk decoder jmh benchmarks` | Added deterministic raw/compressed 19-entry fixtures and public, fresh-owned, and reused-owned decoder benchmarks. |

PF-1.1-D also included narrow corrective commits for parser diagnostics, pool ownership, compile compatibility, and workspace-aware reader test doubles: `04ccea2`, `73ce15a`, and `7eedf9b`.

## Verification gates

The reviewer/user supplied the following successful build evidence:

```text
.\\gradlew.bat clean test
BUILD SUCCESSFUL

.\\gradlew.bat jmhClasses
BUILD SUCCESSFUL
```

An earlier `671 tests completed, 20 failed` result was traced to historical test doubles bypassing workspace-aware parser overloads. The test doubles were corrected; it was not a remaining failure.

### JMH environment and methodology

- Host: `MARIUSZ-PC`
- JDK: Temurin/OpenJDK `25.0.4.1`
- JMH: `1.37`
- Threads: `1`
- Forks: `2`
- Warmup: `5 x 1 second`
- Measurement: `5 x 1 second`
- Mode: `AverageTime`
- Unit: `us/op`
- Profiler: `gc`

The deterministic fixtures were `RAW_PALETTE_19` and `COMPRESSED_PALETTE_19`. Both represent identical semantic voxel data. Fixture construction and correctness checks occurred during setup, outside measured operations.

### Public API cross-SHA evidence

The public benchmark called only `decode(byte[], int)`, allowing the same benchmark source and fixture to be used against the historical decoder and the candidate.

| Fixture | Historical time | Candidate public time | Historical B/op | Candidate public B/op |
|---|---:|---:|---:|---:|
| `RAW_PALETTE_19` | 134.733 us/op | 83.275 us/op | 173104.942 | 295344.762 |
| `COMPRESSED_PALETTE_19` | 119.903 us/op | 84.398 us/op | 173392.840 | 295504.716 |

The candidate public API was faster in these microbenchmarks, while allocating more per invocation. That increase is documented rather than hidden: `decode(byte[], int)` creates an owned result and returns a defensive `toArray()` compatibility copy. The internal owned production path avoids that compatibility copy. The historical RAW timing was materially noisy (`134.733 ± 19.984 us/op`), so its timing percentage should not be treated as high-precision evidence.

### Current owned-path evidence

| Fixture | Fresh-owned time | Reused-owned time | Fresh-owned B/op | Reused-owned B/op |
|---|---:|---:|---:|---:|
| `RAW_PALETTE_19` | 74.268 us/op | 71.562 us/op | 164256.524 | 131120.506 |
| `COMPRESSED_PALETTE_19` | 73.572 us/op | 70.293 us/op | 164424.520 | 131144.497 |

Workspace reuse reduced transient allocation by approximately `33136 B/op` for RAW and `33280 B/op` for compressed palettes compared with fresh-workspace owned decoding. Reused-owned decoding is close to the required final `int[32768]` payload size (`32768 * 4 = 131072` bytes), but this is not zero allocation and is not claimed as an exact theoretical minimum.

The reused-owned benchmark is the closer microbenchmark representation of production. `VcdbsReader` borrows a `ChunkDecodeWorkspace`, passes it through `ChunkParser`, uses internal owned decoding, and transfers the resulting `DecodedChunkLayer` into `ParsedChunk`. The production path does not perform the public compatibility `toArray()` copy. These are decoder microbenchmarks, not end-to-end evidence.

## Real-save macro validation — PF-1.1-F1

### Environment and save identity

- Host: `MARIUSZ-PC`
- OS: Windows 11
- Java: Temurin/OpenJDK `25.0.4.1`
- Save size: `359395328` bytes
- Save SHA-256: `544518BF82874F735D66326467FD8E37B839C91387ED2B23B9EC9518AE39B8D6`
- WAL before validation: absent
- SHM before validation: absent
- Execution mode: `JVM_WARM`
- Warmups: `2`
- Measured iterations: `5`
- OS filesystem cache: uncontrolled

Historical macro SHA was `2149cf2781fec75c67d417d69a1c378d4cc94f6b`; candidate macro SHA was `4fdce9918b07ed4eccef7dac7bd7e8b2029d0c30`.

### R256 sanity

Candidate `ROCK_UPPER_R256` completed successfully. Its result fingerprint was:

```text
419287550ee45d264701857a73d59b42ae66f9f420a7ce3fdd2d3e0cf9b69735
```

Samples were `141225100`, `134785600`, `131300000`, `159398300`, and `122348200 ns`.

| Metric | Result |
|---|---:|
| Min | 122348200 ns |
| P50 | 134785600 ns |
| P95 | 159398300 ns |
| Max | 159398300 ns |

R256 is sanity evidence only, not the primary ROCK conclusion.

### Canonical R1024 comparison

Both historical and candidate runs used `ROCK_UPPER_R1024`, `JVM_WARM`, two warmups, five measured iterations, the same environment, and the same save fingerprint.

The historical and candidate result fingerprint was exactly:

```text
aeb5d5f58f36728f2b3428770b64f91860a1967fd2c6e9446c8bd9ae329ea2ff
```

This equality was the correctness gate.

Historical samples: `2525231700`, `2358341700`, `2387436200`, `2483652300`, `2377832900 ns`.

| Historical metric | Result |
|---|---:|
| Min | 2358341700 ns |
| P50 | 2387436200 ns |
| P95 | 2525231700 ns |
| Max | 2525231700 ns |

Candidate samples: `1981601000`, `1994138100`, `1988169400`, `1979896600`, `1979442900 ns`.

| Candidate metric | Result |
|---|---:|
| Min | 1979442900 ns |
| P50 | 1981601000 ns |
| P95 | 1994138100 ns |
| Max | 1994138100 ns |

Candidate relative deltas, calculated as `(candidate - baseline) / baseline * 100`, were:

| Metric | Delta |
|---|---:|
| Min | -16.066323% |
| P50 | -16.998787% |
| P95 | -21.031480% |
| Max | -21.031480% |

Negative values mean faster.

### R2048 scalability

Candidate `ROCK_UPPER_R2048` completed successfully with fingerprint:

```text
5a35d0c199a085b40058e17e7dcdf72e4aab5c4ca1dadb8caddd71cb5c282ffb
```

| Metric | Result |
|---|---:|
| Min | 5246026900 ns |
| P50 | 5278940300 ns |
| P95 | 5433835300 ns |
| Max | 5433835300 ns |

Historical R2048 was recorded by the earlier PF-1.0 evidence as OOM and was not rerun during F1. No historical R2048 timing or fingerprint is fabricated here. Candidate R2048 success is scalability evidence, not an apples-to-apples timing comparison.

### R4096 stretch result

Candidate `ROCK_UPPER_R4096` failed with:

```text
java.lang.OutOfMemoryError: Java heap space
```

The first useful application frames were:

```text
RockMap.<init>(RockMap.java:18)
RockColumnScanner.scan(RockColumnScanner.java:109)
RenderRockMapUseCase.execute(RenderRockMapUseCase.java:120)
```

No macro report was generated. R4096 remains beyond PF-1.1's comfortable memory envelope. This is not hidden or reclassified as support; it is a stretch-workload result for later streaming and ROCK architecture stages.

### Save safety

Across the entire F1 experiment, the save SHA-256 and length remained unchanged, and WAL/SHM remained absent. The official safety gate reported:

```text
Real-save validation: PASS
Violations: none
```

## JFR status

**JFR: NOT RUN for PF-1.1 closure.**

PF-1.1-E supplied focused decoder allocation/time evidence with JMH `-prof gc`; PF-1.1-F supplied real-save macro and save-safety evidence. No unexplained R1024 correctness or performance anomaly required a focused JFR investigation during closure. JFR remains the primary profiler for future profiling investigations under the broader performance contract.

## Remaining limits and handoff to PF-1.2/PF-1.3

- R4096 still OOMs.
- PF-1.1 optimizes decoder ownership and scratch lifecycle; it does not solve whole-operation retention in ROCK.
- PF-1.2 Streaming Processing Engine remains the next stage.
- PF-1.3 Streaming ROCK Engine remains responsible for eliminating ROCK's whole-region decoded/derived retention bottleneck.
- Decoder-local success must not be conflated with a complete bounded-memory backend architecture.

PF-1.1's targeted decode-memory objective is supported by the unit-test/build evidence supplied by the reviewer, deterministic JMH time/allocation evidence, canonical real-save R1024 correctness/performance evidence, R2048 scalability evidence, and save-safety validation. Later performance stages remain necessary.

This record does not claim zero allocation, R4096 support, complete backend optimization, global performance completion, JFR validation, merge to `master`, or production release acceptance.
