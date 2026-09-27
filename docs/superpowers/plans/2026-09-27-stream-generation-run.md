# Generic streamGenerationRun — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Generation Run pipeline for all Agent SSE interactions; picklist/empty become profiles + thin HTTP aliases.

**Architecture:** `GenerationRunContext` + `SkillRunProfile` (skillId, settle, persistAs). `streamGenerationRun` owns prompt → (persist plugin?) → `ComputerViewResolver` → settle?/release. `ArtifactPersistPlugin` for `persistAs=picklist`. FE calls `POST /api/v1/agent/runs`; `/runs/empty` and `/runs/picklist` stay as aliases.

**Tech Stack:** Java 8 / Spring Boot 2.7, Vue3

## Global Constraints

- AD-5: settle only after usable persist + resolved view (when settleEnabled).
- Dry / empty: never settle; emit `run_failed` with `emptyRun=true`.
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

- [ ] Add types + Picklist plugin (parse → persist → rawView + DTO).
- [ ] Unit test plugin happy path with fixtures.

### Task 2: streamGenerationRun

- [ ] Implement unified stream; dry branch = current empty behavior; billed = current picklist order.
- [ ] `streamEmptyRun` / `streamPicklistRun` delegate.
- [ ] Green `AgentApplicationServiceTest`.

### Task 3: HTTP + FE

- [ ] `POST /api/v1/agent/runs` body: sceneId/sceneCode, sessionId, text, skillId, dryRun.
- [ ] FE `streamAgentRun`; picklist/empty call it (or keep old URLs as BE aliases only).
- [ ] Workspace still works via picklist alias or new path.

### Task 4: Docs + commit

- [ ] Update dual-track revision note.
- [ ] Commit rename leftovers + this feature.

## Out of scope

- SKILL.md front-matter `output.billing` parser
- sku persist plugin
- Deleting `/runs/empty` and `/runs/picklist`
