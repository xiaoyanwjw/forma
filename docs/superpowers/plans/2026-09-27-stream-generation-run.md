# Generic streamGenerationRun — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** One Generation Run pipeline for all Agent SSE interactions; picklist/empty become profiles + thin HTTP aliases.

**Architecture:** `GenerationRunContext` + `SkillRunProfile` (dry / noSkill / billedPicklist). `streamGenerationRun` owns prompt → (persist plugin?) → `ComputerViewResolver` → settle?/release. `ArtifactPersistPlugin` for `persistAs=picklist`. FE calls `POST /api/v1/agent/runs`; `/runs/empty` and `/runs/picklist` stay as aliases.

**Tech Stack:** Java 8 / Spring Boot 2.7, Vue3

## Global Constraints

- AD-5: settle only after usable persist + resolved view (when settleEnabled).
- Dry / empty: never settle; emit `run_failed` with `emptyRun=true`.
- No-skill: never settle; emit `artifact_ready(view)` then release; mark FAILED in DB; **no** `run_failed` on success.
- Computer render field remains `view`; transitional picklist fields OK on `artifact_ready`.
- Do not parse full Skill YAML `output:` block yet — registry maps known skill ids.

## File map

| Path | Responsibility |
|------|----------------|
| `agent/dto/GenerationRunContext.java` | Unified run context |
| `agent/support/SkillRunProfile.java` | dry vs billed-picklist profiles |
| `agent/support/ArtifactPersistPlugin.java` | persistAs SPI |
| `agent/support/PicklistArtifactPersistPlugin.java` | wraps parser + PicklistApplicationService |
| `AgentApplicationService.java` | `prepareGenerationRun` + `streamGenerationRun`; empty/picklist adapters |
| `AgentController.java` | `POST /runs` + aliases |
| `agent.ts` / composables | `streamAgentRun`; wrappers for dry/picklist |

---

### Task 1: Profile + persist plugin + GenerationRunContext

- [x] Add types + Picklist plugin (parse → persist → rawView + DTO).
- [x] Unit test plugin happy path with fixtures.

### Task 2: streamGenerationRun

- [x] Implement unified stream; dry branch = current empty behavior; billed = current picklist order.
- [x] `streamEmptyRun` / `streamPicklistRun` delegate.
- [x] Green `AgentApplicationServiceTest`.

### Task 3: HTTP + FE

- [x] `POST /api/v1/agent/runs` body: sceneId/sceneCode, sessionId, text, skillId, dryRun.
- [x] FE `streamAgentRun`; picklist/empty call it (or keep old URLs as BE aliases only).
- [x] Workspace still works via picklist alias or new path.

### Task 4: Docs + commit

- [x] Update dual-track revision note.
- [x] Commit rename leftovers + this feature.

### Task 5: No-skill path

- [x] `SkillRunProfile.noSkill()` + blank `skillId` resolve (non-dry).
- [x] `streamNoSkillRun`: prompt(null skill) → NoSkillMarkdown → `artifact_ready(view)` → release; no settle / no success `run_failed`.
- [x] Tests: prepare rejects blank text; stream emits markdown view without settle.

## Out of scope

- SKILL.md front-matter `output.billing` parser
- sku persist plugin
- Deleting `/runs/empty` and `/runs/picklist`
