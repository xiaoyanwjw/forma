# Billed Picklist Run Pipeline Layering — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make `streamPicklistRun` follow the dual-track Runtime pipeline: parse → persist → **project (gate)** → settle → emit `{view, artifactRef}` (+ transitional business fields for FE).

**Architecture:** Extract `CreditHoldLifecycle` (release/settle logging boundary) and `ComputerViewGate` (chain + fail-closed). Reorder `streamPicklistRun` so projection cannot run after settle. FE accepts view-first `artifact_ready` (items optional).

**Tech Stack:** Java 8 / Spring (`lippi-ai-ebus-application`), Vue3 (`lippi-ai-ebus-web`)

## Global Constraints

- AD-5: settle only after usable persist **and** successful view projection.
- Projector strategies invent no Chinese display copy.
- Keep LegacyPicklist on chain until Skill dual-track is reliable in prod.
- Do not delete empty-run path; share release helper only.

## File map

| Path | Responsibility |
|------|----------------|
| `agent/support/CreditHoldLifecycle.java` | `tryRelease` / wrap settle call site logging |
| `agent/support/ComputerViewGate.java` | `requireProjectedView(ctx)` → map or BusinessException |
| `AgentApplicationService.java` | Ordered pipeline steps as named private methods |
| `AgentApplicationServiceTest.java` | Gate before settle; view-first ready payload |
| `useAgentPicklistRun.ts` (+ types/tests) | Parse ready when `view` present even without `items` |

---

### Task 1: ComputerViewGate + failing tests

- [x] Test: missing projection → exception message; present rawView → Normalize result.
- [x] Implement `ComputerViewGate`.
- [x] Test: `streamPicklistRun` when chain would return empty → release, **never** settle.

### Task 2: Reorder streamPicklistRun + CreditHoldLifecycle

- [x] Extract `CreditHoldLifecycle.tryRelease`.
- [x] Pipeline order: parse → persist → `requireProjectedView` → settle → emit.
- [x] `toArtifactReady(artifact, view)` — view already computed; no second project call.
- [x] Green `AgentApplicationServiceTest`.

### Task 3: FE view-first

- [x] `toPicklistArtifact`: accept payload with valid `view` and empty/missing `items`.
- [x] Update workspace tests if they assume items-only.

### Task 4: Spec/plan checkbox + commit

- [x] Note pipeline order fix in dual-track design revision.
- [x] Commit.

## Out of scope

- Full generic `streamGenerationRun` for all skills
- Removing LegacyPicklistFallbackProjector
- Dropping `items` from SSE entirely (keep transitional until history API owns business payload)
