# Rubric Walker Review — pi-agent-slim Architecture Spine

**Subject:** `sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md`  
**Parent skim:** `architecture-lippi-ai-ebusiness-2026-09-24` — AD-3, AD-6, AD-10 only  
**Lens:** good-spine checklist (Reviewer Gate)  
**Date:** 2026-09-25  
**Verdict:** **CONCERNS**

---

## Gate verdict (one sentence)

Feature spine fixes the right divergence points (graph keep, HITL-off, MySQL Entry session, three-slot prompt, slim scope) and does not weaken AD-3 / AD-6 / AD-10, but internal table-naming inconsistency plus an underspecified DDL/bootstrap bind leave room for epic teams to diverge.

---

## Checklist scorecard

| Checklist item | Result | Notes |
| --- | --- | --- |
| Divergence points for level below (epics) fixed; none missed | **Pass with notes** | AD-S1–S5 cover the real forks (kernel rewrite, HITL tax, session truth, prompt SPI sprawl, scope creep). Settlement/SSE stay inherited. |
| Every AD Rule enforceable; prevents stated divergence | **Concerns** | AD-S1/S2/S5 clear. AD-S3 mostly strong (idempotency, UNIQUE, Message JSON). Soft edges: AD-S4 “或等价简单结构”; AD-S5 “未引用”. |
| Deferred cannot cause silent divergence | **Pass** | Each Deferred item is locked for this phase by an ADOPTED AD; revisit conditions are present. |
| Named tech verified-current / pinned appropriately | **Pass** | Stack inherits parent Java 8 / Boot 2.7.18 / APP-META MySQL; no invented stack. |
| Ratifies brownfield (does not contradict) | **Pass with notes** | Keeps StateGraph, AgentSession façade, three-slot PromptBuilder — matches intentional slim. WIP rename around `before_agent_start` / context overwrite is outside this file; Rule still names the extension hook. |
| Spec capabilities covered (if spec-driven) | **N/A / Pass** | Build-substrate feature spine; binds parent FRs via inheritance (session ownership, FR12 user bind). |
| Inherited ADs not weakened/contradicted | **Pass** | See inheritance section. |
| Feature-altitude dimensions decided / deferred / open | **Concerns** | Core runtime dimensions covered; ops envelope partially inherited; **DDL script home** and **physical table names** not single-sourced. |

---

## Inheritance (AD-3, AD-6, AD-10)

### AD-3 — Pi vendor-copy + AgentSession

- Parent: copy evolves in-repo; model via `pi-ai`; business entry is `AgentSession`, not internal Agent.
- Child: paradigm + conventions + Inherited row reinforce façade-only injection and `pi-ai` I/O.
- AD-S1’s keep-StateGraph (vs upstream while) is **local evolution under vendor-copy**, not a weakening of AD-3.
- **No conflict.**

### AD-6 — Domain ownership

- Parent: AgentRuntime uniquely writes Agent 会话 / GenerationRun / SSE orchestration; artifacts & credits elsewhere; tools must not write ledger.
- Child: Inherited “AgentRuntime 编排；成果与积分不在 Tool 内写”; AD-S5 keeps settlement/AD-4 finals in ebus application; AD-S3 puts port in `lippi-pi-agent`, MyBatis adapter in infrastructure — consistent with ownership + AD-11 (also listed in Inherited).
- userId bind stays application-side — does not invent tenant multi-write.
- **No conflict / no weakening.**

### AD-10 — Local Docker / APP-META

- Parent: deploy metadata & **bootstrap SQL** live under `APP-META/`; Compose brings starter + MySQL.
- Child: Inherited “Session 表落同一业务 MySQL”; AD-S3 “共享 APP-META / 业务 MySQL”; Stack “MySQL | 继承父 Spine / APP-META”.
- **Preserves “same MySQL / no second primary store.”**
- **Gap (not a contradiction):** parent’s **bootstrap SQL placement** is not restated as an enforceable Rule for the new `PI_SESSION*` tables. “Shared MySQL” alone does not stop one epic from shipping schema only under `infrastructure` resources and another under `APP-META/bootstrap`.

---

## Findings (tiered)

### Critical

_None._

### High

1. **Physical session table names disagree inside the spine**  
   - **Where:** Consistency Conventions (`session` + `session_entry`) vs Structural Seed ERD (`PI_SESSION` / `PI_SESSION_ENTRY`).  
   - **Why it fails the checklist:** Two epics can “follow the spine” and ship incompatible DDL / MyBatis maps — classic silent divergence at feature→epic altitude.  
   - **Suggested disposition:** **autofix** — pick one naming scheme (prefer ERD `PI_*` or a single convention row), update the other site, optionally add one Rule bullet under AD-S3.

### Medium

2. **AD-10 bootstrap/DDL home not bound for new session tables**  
   - **Where:** Inherited AD-10 row + AD-S3 production default; Deferred silent on migrations.  
   - **Why:** Operational envelope for this feature’s new entities is half-decided (DB instance) and half-silent (who owns SQL scripts / compose init). Parent AD-10 already answers “APP-META/bootstrap,” but feature builders may miss it.  
   - **Suggested disposition:** **autofix** — one AD-S3 or Conventions line: “DDL in `APP-META/bootstrap` (shared MySQL), not a second datastore and not only classpath-optional.” Or **defer** explicitly with “DDL path = parent AD-10” if intentional.

3. **AD-S3 still leans on external “Part 2 / packages/agent” for completeness**  
   - **Where:** AD-S3 Rule opener; frontmatter `sources` absolute path `/Users/echo/workspace/pi/packages/agent`.  
   - **Why:** Entry fields, write path, and constraints in-body are largely self-contained now (improvement), but “Part 2 会话树子集” remains a label only meaningful with that tree; absolute machine path is non-portable for other builders/CI. Residual ambiguity: which non-`message` entry types are in-scope this phase.  
   - **Suggested disposition:** **autofix** — replace absolute source with repo-relative or “upstream pi packages/agent (reference)”; add “本阶段 entry_type 仅 `message`（+ 可选 compaction）” or list allowed types; keep Part 3 in Deferred (already present).

4. **Frontmatter `binds` under-lists inherited ADs**  
   - **Where:** `binds: [AD-3, AD-4, AD-5, lippi-pi-agent]` vs Inherited table (AD-1, AD-6, AD-7, AD-10, AD-11, AD-12).  
   - **Why:** Not a Rule conflict, but weakens machine/human discovery of binding constraints at finalize handoff.  
   - **Suggested disposition:** **autofix** — expand `binds` to match Inherited (or document that Inherited is authoritative and `binds` is scope-only).

### Low (tail)

5. **AD-S4 soft edge (“或等价简单结构”)** — slightly dilutes “纯字符串” inject; still forbids map SPI. Prefer one concrete allowed shape if ContextOverwrite-style structs are in flight.  
6. **AD-S5 “未引用配置”** — subjective cleanup gate; acceptable for scope AD but expect PR debate.  
7. **No Open Questions section** — residual items (entry_type enum, DDL home) are findings rather than named opens; feature altitude would be cleaner if leftovers were explicit opens or Deferred bullets.

---

## What works well (credit)

- Paradigm is short and decision-shaped; mermaid binds AgentSession / SSE / CreditLedger correctly to parent AD-4/AD-5.
- AD-S1 cleanly prevents dual rewrite; Deferred 1A has a revisit condition.
- AD-S2 default-off HITL with ports retained is enforceable and avoids LIMS-capability deletion vs Adam thickness.
- AD-S3 (current) is the right altitude for session: MySQL + Entry subset, ≠ Checkpointer, idempotency/`UNIQUE(session_id,seq)` spelled out, Message JSON pinned to `lippi-pi-ai`, AD-11-compatible adapter placement.
- Deferred list does **not** leave “pick anything” gaps for this phase; ADOPTED ADs lock the choices.
- Inherited AD-6 / AD-10 / AD-11 / AD-12 rows show conscious parent binding after MySQL override.

---

## Disposition summary for Finalize owner

| Finding | Severity | Action |
| --- | --- | --- |
| Table name `session*` vs `PI_SESSION*` | High | autofix |
| DDL/bootstrap bind vs AD-10 | Medium | autofix or explicit defer-to-parent |
| Part 2 / absolute source / entry_type | Medium | autofix |
| frontmatter `binds` vs Inherited | Medium | autofix |
| AD-S4/S5 soft wording; missing opens | Low | optional polish |

**Do not FAIL** the spine: no inherited AD is contradicted; core divergence points are fixed. **Do not PASS** until High #1 is reconciled (and ideally Medium #2–3).

---

## Rubric walker compact return

- **Verdict:** CONCERNS  
- **Top findings:** High table-name split; Medium DDL/AD-10 silence; Medium Part-2/source portability; Medium binds vs Inherited  
- **File:** `sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/reviews/review-rubric-walker.md`
