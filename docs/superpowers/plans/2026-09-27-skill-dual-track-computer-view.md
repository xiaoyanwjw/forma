# Skill Dual-Track Computer View — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Ship Computer view strategy chain (`supports`/`project`) + FE `markdown` block, keep picklist settle path green via a transitional legacy strategy.

**Architecture:** Platform `ComputerViewProjector` strategies on a fixed chain: NormalizeView → LegacyPicklistFallback (transition) → NoSkillMarkdown. Skill-emitted `view` is normalized; no-skill turns wrap final text as markdown; current picklist (artifact only) still projects via legacy until Skill dual-track lands.

**Tech Stack:** Java 8 / Spring Boot 2.7 (`lippi-ai-ebus-application`), Vue3 / Vitest (`lippi-ai-ebus-web`)

## Global Constraints

- Computer block whitelist: `markdown` | `note` | `list` | `media` | `section` only.
- Projector strategies must not invent Chinese display copy; kind/tone only.
- Settle still requires persisted usable artifact (AD-5); chain failure on billing without view → no settle.
- Do not rewrite full `streamPicklistRun` orchestration in this plan (CreditHold split = follow-up).
- Spec: `docs/superpowers/specs/2026-09-27-skill-dual-track-computer-contract-design.md`

## File map

| Path | Responsibility |
|------|----------------|
| `.../computer/ComputerViewProjector.java` | Strategy interface |
| `.../computer/ViewProjectContext.java` | Context DTO |
| `.../computer/ViewProjectorChain.java` | Ordered `supports` → `project` |
| `.../computer/NormalizeViewProjector.java` | Sanitize raw view |
| `.../computer/NoSkillMarkdownProjector.java` | No-skill → markdown doc |
| `.../computer/LegacyPicklistFallbackProjector.java` | Transition: DTO → view via existing projector |
| `.../picklist/support/PicklistViewProjector.java` | Keep domain→map helper used by legacy strategy |
| `AgentApplicationService.java` | Build context + call chain for `artifact_ready.view` |
| `computerView.ts` / `ComputerRenderer.vue` (+tests) | Add `markdown` block |
| Skill MD (3 copies) | Dual-track `view`+`artifact` example (doc only) |

---

### Task 1: BE strategy API + Normalize + NoSkillMarkdown (TDD)

**Files:** new under `lippi-ai-ebus-application/.../computer/` + tests

- [x] Write failing tests: Normalize drops unknown block types; NoSkillMarkdown wraps text when `!skillBound`; chain picks first `supports`.
- [x] Implement `ComputerViewProjector`, `ViewProjectContext`, `ViewProjectorChain`, `NormalizeViewProjector`, `NoSkillMarkdownProjector`.
- [x] Run `mvn -pl lippi-ai-ebus-application -am -Dtest=NormalizeViewProjectorTest,NoSkillMarkdownProjectorTest,ViewProjectorChainTest test`.

### Task 2: Legacy picklist fallback + wire AgentApplicationService

**Files:** `LegacyPicklistFallbackProjector.java`, `AgentApplicationService.java`, existing projector/tests

- [x] Write test: skillBound + PicklistArtifactDTO + null rawView → legacy produces list document.
- [x] Implement `LegacyPicklistFallbackProjector` delegating to `PicklistViewProjector`.
- [x] Spring bean: chain order Normalize → LegacyPicklist → NoSkillMarkdown.
- [x] `toArtifactReady`: `view = chain.project(ctx)` with skillBound=true, artifact=dto, rawView=null (until Skill emits view).
- [x] Run `PicklistViewProjectorTest`, `AgentApplicationServiceTest` (picklist happy path).

### Task 3: FE `markdown` block

**Files:** `computerView.ts`, `ComputerRenderer.vue`, `ComputerRenderer.test.ts`

- [x] Add `ComputerMarkdownBlock`; parse + render plain text (escape via Vue text binding).
- [x] Test: document with markdown block appears in DOM; unknown types still skipped.
- [x] `npm test -- --run src/components/business/computer/ComputerRenderer.test.ts`

### Task 4: Skill doc dual-track example

**Files:** three `ecommerce-picklist/SKILL.md` copies

- [x] Add short dual-track shape (`view` + `artifact`) example; keep current flat fields as compatible `artifact` body until parser accepts wrapper (no parser break this PR).
- [x] Note: Runtime still accepts flat JSON via legacy projector.

### Task 5: Spec checklist + verify

- [x] Mark success criteria progress in design spec revision note.
- [x] Commit with message reflecting strategy chain + markdown block.

---

## Out of scope (follow-up)

- Full Skill JSON wrapper parse (`artifact`/`view` envelope) in `PicklistArtifactParser`
- Removing `LegacyPicklistFallbackProjector`
- Splitting `CreditHoldLifecycle` / generic `streamGenerationRun`
