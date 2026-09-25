# Architecture Principles

## Purpose

This document is the durable architectural constitution for VS Cartographer production code.

It defines how to reason about boundaries, ownership, dependency direction, data authority, and composition. It intentionally does not describe the current package tree, class inventory, dependency graph, feature set, milestone, or implementation status.

Current topology must be discovered from the repository and its automated guards.

These principles should survive ordinary package renames, class moves, feature additions, refactors, releases, and implementation rewrites.

## 1. Keep the production dependency graph acyclic

Production dependencies must not form cycles.

A cycle is a design problem because it makes responsibilities mutually dependent and prevents clear ownership.

Do not hide a conceptual cycle through indirection. Reflection, string-based lookup, service locators, global registries, forwarding wrappers, or duplicated models do not make a cyclic design healthy merely because a static import disappears.

When a cycle appears, identify the competing responsibilities and restore a one-way boundary.

## 2. Let responsibility determine dependency direction

Dependency direction follows architectural responsibility, not convenience.

Higher-level orchestration may coordinate capabilities needed to perform a use case. Lower-level mechanisms must not depend back on the orchestration that invokes them.

Infrastructure provides mechanisms and implementations. It must not decide product policy merely because it has access to lower-level data.

Where dependency inversion is useful, contracts belong with the responsibility that semantically owns the capability, not automatically with the concrete implementation.

The goal is one-way knowledge: each side should know only what its responsibility legitimately requires.

## 3. Keep presentation at the outside

Presentation adapts system behavior for a user or external consumer.

Lower-level domain semantics, parsing, persistence, indexing, caching, and infrastructure must not depend on presentation concerns.

Presentation may translate user intent into application operations and translate results into view models or rendered output. That permission does not justify leaking presentation types inward.

## 4. Place concepts by semantic ownership

The primary placement question is:

> Who owns the meaning of this concept?

Do not use:

> Where is this concept currently used?

A type, contract, result model, planner, policy, or abstraction belongs with the responsibility that defines its semantics.

The first consumer of a type does not automatically own it.

If multiple consumers need the same concept, first identify whether the concept is genuinely shared or whether an adapter should translate between distinct representations.

## 5. Use explicit adapters at boundaries

Different responsibilities may need different representations of similar information.

Translate explicitly at the boundary where knowledge of both sides is legitimate.

Do not contaminate a lower-level model with higher-level semantics merely to avoid mapping.

Do not duplicate models solely to disguise an unhealthy dependency. Separate models are justified when they express genuinely different meanings or lifecycle boundaries.

## 6. Compose at the outer edge

Concrete object-graph assembly belongs at an outer composition boundary.

Components should receive collaborators instead of constructing unrelated infrastructure internally.

Domain and capability classes must not become hidden composition roots.

Construction of a local value object or an implementation-private helper is different from choosing unrelated application infrastructure. The principle targets hidden ownership of external dependencies and system wiring, not ordinary object creation.

## 7. Keep data authority explicit

Source truth, interpreted data, and derived data are different architectural categories.

Authoritative inputs must remain identifiable as authoritative.

Caches, snapshots, indexes, projections, render artifacts, and other derived representations are replaceable unless the product explicitly defines otherwise.

Derived data must not silently become the source of truth merely because it is convenient or faster to access.

When information is inferred, estimated, cached, transformed, or incomplete, preserve that semantic status across boundaries.

## 8. Prefer explicit boundaries over hidden coupling

Architectural boundaries must be visible in normal code structure and contracts.

Do not satisfy a guard while preserving the forbidden relationship through:

- reflection used to avoid a dependency;
- string class names or dynamic lookup used as a dependency tunnel;
- service locators;
- mutable global registries;
- unrelated static global state;
- compatibility forwarding layers created only to preserve a bad dependency;
- duplicated models created only to hide coupling.

Hidden coupling is still coupling.

## 9. Do not create generic ownership to solve dependency errors

A package or module named as a generic shared area is not automatically neutral.

Do not move a concept into a generic common, shared, util, misc, or equivalent dumping ground merely to make dependency arrows compile.

A neutral abstraction is valid only when its meaning is genuinely neutral and its ownership can be explained without referring to a specific dependency violation.

Prefer a precise semantic home over a convenient shared bucket.

## 10. Introduce abstractions only for real seams

Interfaces and abstractions should represent a real reason for substitution or isolation, such as:

- an external boundary;
- independently testable policy;
- lifecycle ownership;
- multiple meaningful implementations;
- inversion of a dependency that protects higher-level policy.

Do not introduce speculative interfaces, factories, layers, or extension points because they might be useful someday.

Architecture should remove accidental coupling, not manufacture accidental complexity.

## 11. Separate mechanism, interpretation, orchestration, and presentation

Keep distinct responsibilities conceptually separate:

- acquiring or decoding data;
- storing or retrieving data;
- interpreting domain meaning;
- coordinating a use case;
- presenting a result.

A component may collaborate across a boundary through a suitable contract, but should not absorb unrelated responsibilities merely because the data is already available there.

Repeated pressure to cross a boundary is a signal to reconsider ownership or introduce an adapter, not a reason to erase the boundary.

## 12. Preserve important domain distinctions

Architecture must protect domain truth rather than flatten it.

Do not collapse semantically different states merely to simplify APIs.

Examples of distinctions that should remain explicit when relevant include:

- different coordinate spaces;
- authoritative observations versus inferred signals;
- absent data versus corrupt data versus unsupported data;
- source data versus derived or cached data.

The specific types that express these distinctions may change. The requirement to preserve the distinctions does not.

## 13. Treat architecture roles as a reasoning model, not a directory template

Useful architectural roles include presentation, orchestration/application policy, domain capabilities, and infrastructure/adapters.

These are conceptual responsibilities, not mandatory package names or a required source-tree shape.

A subsystem may contain more than one internal role. Package names may change. Roles may be split or combined when responsibility remains clear.

Do not update this document merely because the physical layout changes.

## 14. Treat automated guards as enforcement, not as the constitution

Automated architecture checks enforce the repository's current structural realization of these principles.

The checks may evolve as the implementation evolves.

A guard rule is evidence of an intended boundary, but the existence of a guard is not itself the architectural rationale.

Do not design around a guard's parser or loopholes. Preserve the principle the guard is intended to protect.

If a valid architectural evolution requires changing a guard, change the implementation and guard deliberately while keeping the underlying principle explicit.

## 15. Make fundamental architecture changes deliberate

Ordinary implementation work should preserve these principles.

If a task genuinely requires changing a fundamental principle, treat that as an explicit architectural decision with its own rationale and review.

Do not silently weaken an architectural rule because a local implementation is difficult.

## Maintenance rule

This document is not a snapshot of the repository.

Do not add:

- current package or class inventories;
- current dependency edges or cycle counts;
- current architecture diagrams that mirror the source tree;
- current feature or milestone status;
- issue, pull-request, commit, or branch status;
- current test or performance metrics;
- current build or CI commands;
- implementation-specific rules that exist only because today's classes have particular names.

Current implementation truth belongs in code, tests, build configuration, CI, generated reports, focused technical documentation, and task-specific issues or pull requests.

Only update this document when the project's fundamental architectural principles change.
