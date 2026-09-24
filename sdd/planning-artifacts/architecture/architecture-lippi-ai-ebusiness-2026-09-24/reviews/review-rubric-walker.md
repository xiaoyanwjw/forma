# Rubric Walker Review — Architecture Spine

**Target:** `ARCHITECTURE-SPINE.md`  
**Altitude:** initiative (build-substrate)  
**Inputs checked:** PRD `prd-lippi-ai-ebusiness-2026-09-23` (FR-1..12, NFR-1..5, UJ-1..3); spine memlog  
**Date:** 2026-09-24  
**Verdict:** **pass-with-fixes**

Paradigm, credit/SSE/ownership invariants, and FR/UJ coverage are strong enough for feature-level builders. Fix the silent-divergence gaps below before `status: final` — none require paradigm rethink.

---

## Checklist scorecard

| Criterion | Result | Notes |
| --- | --- | --- |
| Fixes real divergence points for level below; misses none critical | **Partial** | Core seams fixed (client/API, modular monolith, Pi vendor-copy, SSE vs poll, CreditLedger sole writer, domain ownership, usable-artifact settle). Misses: PK strategy, FR-11 feedback write-owner, NFR-5 rate-limit home, CI/CD envelope silence. |
| Every AD Rule enforceable and prevents stated divergence | **Mostly** | AD-1..9, AD-11 solid. AD-7 softens with「或等价可导出主图方案」— two units can disagree on “usable Listing.” AD-10 vague on which deps must be compose-local vs cloud-reachable (OSS/model). |
| Nothing under Deferred could let two units diverge silently | **Fail → fix** | Several Deferred items OK (payment, OAuth, Boot upgrade, cloud, model vendor via pi-ai). **Risks:** SSE JSON schema deferred without a binding event-type enum; history 30d vs 50 left open without single owner+pin story; Conventions leave 雪花/UUID as「实现时二选一」*outside* Deferred — looks settled, isn’t. |
| Named tech verified-current | **Partial** | Boot 2.7.18 correctly pinned + EOL Deferred. Java 8 intentional LIMS align. Vue/Vite/TS scaffold-lock OK; spine wisely says勿假定 Vite 6. **Flags:** MyBatis starter pinned **2.3.1** (Boot-2.7 line latest is **2.3.2** / MyBatis **3.5.14**); jjwt **0.11.5** LIMS-aligned but current line is **0.12.x/0.13.0**; Aliyun OSS SDK **unpinned** (“脚手架时查”). |
| Covers driving PRD FR-1..12 / NFR | **Partial** | Capability map covers FR-1..12, UJ Agent shell, NFR-2, NFR-5 (thin). **NFR-1** (quality floor) and **NFR-4** (compliance/disclaimer) absent from map and ADs. NFR-3 via error convention. FR-11 retry settled; **feedback persistence** not owned. |
| Every altitude-owned dimension decided / deferred / open — esp. ops | **Partial** | Deploy: AD-10 local compose + Deferred cloud/multi-env/observability. Infra: MySQL, Aliyun OSS, model vendor Deferred. **Silent:** CI/CD / release artifact; where rate limits live; local OSS/model stub strategy under AD-10. |
| No template comments or placeholders | **Pass** | No `<!-- -->`, TODO/TBD/`[ASSUMPTION]`/brace placeholders in spine body. Frontmatter `status: draft` expected pre-finalize. |

---

## What works (do not reopen)

- **Paradigm** SPA + Spring modular monolith + vendor-copied Pi + SSE for billable gen is a coherent substrate; mermaid ownership graph matches AD-6.
- **AD-5 + AD-7** correctly couple hold → usable persist → settle; tools forbidden from credit writes — prevents the highest-cost divergence (double-spend / fail-charge).
- **AD-3 + AD-4** prevent dual LLM stacks and sync-vs-poll protocol forks; fetch+ReadableStream rule is enforceable.
- **AD-2 / AD-6 / AD-11** give package ownership and dependency direction feature agents can cite.
- **AD-8 / AD-9** correctly keep SMS/WeChat/payment off the critical path without leaving upgrade undefined (manual ledger).
- **Deferred** items for payment, OAuth, mini-program, Boot 3, cloud, model vendor generally carry revisit conditions and don’t reopen settled ADs.
- Seed tree + ER sketch are minimal and aligned; no template residue.

---

## Findings (severity-ordered)

### 1. HIGH — Primary-key strategy left as open choice inside Conventions

**Checklist:** Deferred silent-divergence; altitude dimension incomplete.

**Where:** Consistency Conventions → `ID`：「雪花/UUID 字符串主键（实现时二选一并全库一致）」

**Why it fails the rubric:** This is a cross-module invariant for initiative altitude. Placing「二选一」in Conventions *implies* settled consistency while leaving the choice unbound. Identity / CreditLedger / PicklistArtifact / ListingArtifact built in parallel can ship incompatible ID types before anyone notices. Not listed under Deferred with a single pin owner/story.

**Fix (autofix):** Either (a) **decide now** (prefer one: UUID string *or* snowflake string) as Convention or thin AD, **or** (b) move to Deferred:「首个持久化故事由 Identity/infrastructure 选定雪花或 UUID 字符串，全库强制；禁止混用」.

---

### 2. HIGH — FR-11 quality-feedback write ownership missing

**Checklist:** Divergence miss; PRD coverage gap.

**Where:** Capability map FR-11 →「application 编排 + CreditLedger」; AD-6 ownership table has no Feedback / QualityReport writer.

**Why:** Retry+recharge is covered by AD-5/AD-7. 「质量差」反馈 is a durable write with no unique owner. Two epics can invent `Feedback` under AgentRuntime, PicklistArtifact, ListingArtifact, or an ad-hoc table in application — silent schema/API fork.

**Fix (autofix):** Add owner to AD-6 (e.g. `QualityFeedback` or fold under HistoryQuery/Identity with explicit write scope) and bind FR-11 in the capability map to that owner + AD-6. Soft-delete/retention can stay Deferred.

---

### 3. HIGH — NFR-5 rate-limit placement neither decided nor Deferred

**Checklist:** PRD NFR coverage; altitude ops/security dimension silent.

**Where:** Capability map NFR-5：「Identity + 频率限制（实现）」— no AD Rule, no Deferred revisit condition.

**Why:** “实现” is not an architecture call. Feature builders can put limits in gateway, Spring filter, Identity service, or AgentRuntime start — incompatible stacks and bypass paths (SSE vs REST).

**Fix (autofix):** Decide thin rule under AD-8 or new AD-12: e.g.「计费生成入口（REST start + SSE）统一经 application/Identity 侧频率限制；禁止仅前端节流」; **or** Deferred with pin at first AgentRuntime story and named owner.

---

### 4. MEDIUM — Deferred SSE event JSON schema can fork web ↔ AgentRuntime

**Checklist:** Deferred silent-divergence.

**Where:** Deferred：「SSE 事件 JSON schema 细表（实现第一个 Agent 故事时钉死）」; Conventions already require「稳定字符串」but no enum.

**Why:** Pin-at-first-story helps, but initiative builders of `apps/web` Agent shell and `AgentRuntime` can invent different type names (`text_delta` vs `message.delta`) before that story. AD-4 lists payload *kinds* in prose only.

**Fix (discuss / light autofix):** Promote a **closed event-type name list** into AD-4 or Conventions (even if payload fields stay Deferred): e.g. `text_delta | tool_start | tool_end | artifact_ready | error | done`. Keep JSON field schema Deferred to first Agent story.

---

### 5. MEDIUM — Ops envelope incomplete: CI/CD silent; AD-10 local-deps ambiguity

**Checklist:** Altitude-owned ops/deploy/infra.

**Where:** AD-10 + Deferred cloud/multi-env/observability. No mention of CI, image build, or whether OSS/LLM must be real vs stubbed in compose.

**Why:** Initiative spine need not design K8s, but “how two units produce a runnable system” includes whether CI builds `apps/api`+`apps/web` the same way and whether MediaStore/AgentRuntime assume live Aliyun/model endpoints locally. Left silent → diverge on README/compose contracts.

**Fix (autofix):** Add Deferred bullets with revisit conditions, e.g.「CI：首个可运行里程碑前钉 compose 构建/健康检查；本地 OSS/模型：compose 允许 mock 端口，契约与 MediaStore/pi-ai 一致」. Optionally tighten AD-10 Rule one sentence on required compose services vs external-only.

---

### 6. MEDIUM — Named tech currency flags (non-blocking if intentional)

**Checklist:** Named tech verified-current.

| Named | Spine | Verified 2026-09-24 | Action |
| --- | --- | --- | --- |
| Spring Boot 2.7.18 | Pinned; EOL Deferred | Correct final OSS 2.7.x; OSS EOL; commercial support extended | Keep + keep Deferred upgrade |
| Java 8 | Locked to LIMS copy | Valid for Boot 2.7; not current LTS | Keep; already Deferred upgrade |
| mybatis-spring-boot-starter 2.3.1 / MyBatis 3.5.13 | LIMS align | Boot-2.7 line latest **2.3.2** (MyBatis **3.5.14**) | Prefer bump to 2.3.2 unless LIMS POM forces 2.3.1 — document “LIMS pin” if kept |
| jjwt 0.11.5 | LIMS align | Latest Maven Central **0.13.0** (0.12+ API break) | OK to keep with explicit “LIMS-aligned, not latest” note |
| Aliyun OSS Java SDK | Unpinned | Must pin at scaffold | Pin concrete version in Stack at finalize or Deferred「MediaStore 脚手架故事钉死」 |
| Vue 3.5.x / Vite / TS via create-vue | lockfile | create-vue current; Vite line must not be assumed 6 | Keep spine wording; ignore memlog’s Vite 6 assumption |
| MySQL 8.0.x | compose 钉补丁 | OK as seed | Pin patch in compose when written |
| Docker Compose V2 | Named | Current | OK |

---

### 7. MEDIUM — NFR-1 / NFR-4 not on capability map

**Checklist:** PRD NFR coverage.

**Where:** Capability → Architecture Map omits NFR-1 (quality floor) and NFR-4 (compliance / human-review disclaimer).

**Why:** May be mostly product/content, but builders will still place: prompt/guardrail hooks, ToS acceptance at register, export watermark/disclaimer. Without even Deferred ownership, Identity vs web vs AgentRuntime will fork.

**Fix (autofix):** Map NFR-1 → AgentRuntime/prompt+tool policy (Deferred detail OK); NFR-4 → Identity/web registration + export surfaces (Deferred copy OK). One sentence each in map is enough.

---

### 8. LOW — AD-7 “等价可导出主图方案” softens usable-Listing rule

**Checklist:** Rule enforceability.

**Why:** Prevents stated divergence (「模型说完了」vs 可上架) only if “usable” is binary. Equivalence clause invites ListingArtifact vs MediaStore disagreement (URL present vs “scheme JSON only”).

**Fix (discuss):** Prefer harden to「至少一条可下载主图 OSS URL」for v1; keep “方案-only” as Deferred product exception if ever needed.

---

### 9. LOW — History retention Deferred without single pin owner

**Checklist:** Deferred silent-divergence (mild).

**Where:** Deferred 30 天 vs 50 条; HistoryQuery is read-only owner.

**Fix:** Append「由 HistoryQuery 在首个 FR-12 故事选定；清理任务与查询过滤器必须同一策略」.

---

## AD-by-AD enforceability notes

| AD | Prevents claim | Rule enforceable? | Gap |
| --- | --- | --- | --- |
| AD-1 | FE model/credits/artifact writes | Yes | — |
| AD-2 | v1 microservice split | Yes | — |
| AD-3 | Dual Pi / Maven-on-LIMS | Yes | Copy path assumed; no checksum/source commit rule (OK for initiative) |
| AD-4 | Sync vs poll dual protocol | Yes | Event *names* not closed set → Finding 4 |
| AD-5 | Direct balance mutate / fail-charge / double-spend | Yes | — |
| AD-6 | Shared writers | Yes for listed owners | Feedback missing → Finding 2 |
| AD-7 | Stream-complete ≠ billable success | Mostly | Soft clause → Finding 8 |
| AD-8 | Anon billable gen; SMS/WeChat block | Yes | Rate limit not in Rule → Finding 3 |
| AD-9 | Blobs in MySQL; pay gateway block | Yes | — |
| AD-10 | Irreproducible local installs | Partial | Dep list fuzzy → Finding 5 |
| AD-11 | Cycles / interfaces→domain leak | Yes (diagram + prose) | — |

---

## PRD capability coverage matrix

| Cap | Covered? | Governed by | Gap |
| --- | --- | --- | --- |
| FR-1 | Yes | AD-8, AD-1 | — |
| FR-2 | Yes | AD-5, AD-6 | — |
| FR-3..5 | Yes | AD-5, AD-6 | — |
| FR-6 | Yes | AD-9 (manual) | — |
| FR-7..8 | Yes | AD-4,6,7 | — |
| FR-9..10 | Yes | AD-4,6,7,9 | Export UX detail seed-level OK |
| FR-11 | Partial | AD-5,7 | Feedback owner missing |
| FR-12 | Yes | AD-6 | Retention Deferred (OK with pin owner) |
| UJ-1..3 | Yes | AD-4, AD-9, UX refs | — |
| NFR-1 | **No** | — | Finding 7 |
| NFR-2 | Yes | Conventions cost | Field-level Deferred OK |
| NFR-3 | Yes | Error convention | — |
| NFR-4 | **No** | — | Finding 7 |
| NFR-5 | Thin | AD-8 + “实现” | Finding 3 |

---

## Recommended autofix set (for Finalize parent)

1. Pin or properly Deferred **ID strategy** (Finding 1).  
2. Add **QualityFeedback** (or equivalent) to AD-6 + FR-11 map (Finding 2).  
3. Decide or Deferred **rate-limit home** for NFR-5 (Finding 3).  
4. Close **SSE event-type name set** in AD-4/Conventions; leave field schema Deferred (Finding 4).  
5. Deferred **CI + local OSS/model mock** under ops (Finding 5).  
6. Bump or annotate MyBatis **2.3.2**; pin or Deferred OSS SDK; annotate jjwt LIMS pin (Finding 6).  
7. One-line map rows for **NFR-1 / NFR-4** (Finding 7).  
8. Optional: harden AD-7; pin HistoryQuery retention decision owner (Findings 8–9).

After those, spine meets good-spine checklist for initiative → feature handoff.

---

## Verdict rationale

Not **fail**: paradigm and load-bearing ADs (ledger, SSE, ownership, Pi copy, auth, OSS) correctly fix the divergences that would wreck parallel feature work; no placeholders; Boot EOL honesty is good.

Not **pass**: HIGH gaps (PK open-in-conventions, FR-11 feedback owner, NFR-5 rate-limit silence) plus Deferred SSE naming and ops CI silence would still let two units diverge before first stories reconcile.

**pass-with-fixes** — apply the autofix set above, then re-lint / light re-walk.
