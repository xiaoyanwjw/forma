---
name: review-adversarial
lens: adversarial-divergence
target: ARCHITECTURE-SPINE.md
altitude: initiative (units below = features/epics)
status: complete
created: 2026-09-24
verdict: FAIL — not divergence-safe for parallel feature builds
---

# Adversarial Architecture Review — lippi-ai-ebusiness spine

## Method

Altitude is **initiative**; units one level down are **features / epics**. For each attack: invent two units that each obey every ADOPTED AD and Consistency Convention **to the letter**, then show they still ship incompatible shared-data shapes, dual ownership of one entity, or conflicting state-mutation paths. Every surviving pair is a hole — close with a new or tightened AD (not more seed prose).

Spine under attack: `ARCHITECTURE-SPINE.md` (draft, 2026-09-24).

## Verdict

**FAIL.** AD-1..AD-11 correctly fence client/model/积分 and module write tables at a coarse grain, but they leave the **cross-feature contracts** that actually couple FR-7↔FR-9, AD-5↔AD-7↔AD-4, and Listing↔Media **unnamed**. Two feature teams can each be AD-compliant and still refuse to integrate. Five load-bearing holes below; recommended closing ADs at end.

---

## Attack pairs

### Pair 1 — Clashing shared-data shapes: Picklist handoff ↔ Listing input

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **F-Picklist** (FR-7..8) | AgentRuntime + CatalogTemplate + PicklistArtifact | AD-4 SSE generate; AD-6 only PicklistArtifact writes Picklist; AD-7 persists 8–12 candidates each with a free-text `reason` string; AD-5 settle after persist via application |
| **F-Listing** (FR-9..10) | AgentRuntime + ListingArtifact + MediaStore | Same ADs; starts only from a “selected candidate”; expects item shape `{ productName, sellingPoints[], attrs{}, sourceItemId }` |

**Clash:** AD-7 defines *success thresholds* (“带理由的候选”, “详情文案 + 主图 URL”), not the **inter-feature payload**. The ER seed shows `User` owns both Picklist and ListingPack with **no edge** between them. Nothing binds:

- whether Listing requires `sourcePicklistId` + `selectedItemId`, an embedded snapshot, or free-form product text;
- PicklistItem field schema (flat `reason: string` vs structured bullets/attrs);
- whether CatalogTemplate fields are projected into items or only into the Agent prompt.

F-Picklist ships `items[].reason: string`. F-Listing’s application layer rejects or re-prompts because it cannot find `sellingPoints` / attrs. Both modules write only their owned tables (AD-6). Integration breaks at the **shared handoff shape**, which is not an AD.

**Hole:** no AD for Picklist↔Listing contract (minimum item schema + how a Listing run names its source candidate).

---

### Pair 2 — Two owners of one entity: 主图 identity (URL vs object key)

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **F-Listing-Meta** | ListingArtifact | AD-6: writes “主图元数据（URL）” on ListingPack; AD-7/AD-9: success = at least one main-image URL; MediaStore called only to upload bytes |
| **F-Media-Canon** | MediaStore (+ Listing consumers) | AD-6: writes OSS object keys; AD-9: bytes only in OSS; exposes `resolveUrl(key)` as the canonical address; insists ListingPack store `mediaObjectId` only |

**Clash:** The same domain entity — **one main image for a ListingPack** — is split across two owners with competing identifiers:

- F-Listing-Meta persists absolute URL strings on ListingArtifact rows (literal AD-6 text).
- F-Media-Canon treats object key / MediaObject id as canonical; URL is derived and must not be duplicated.

Export, HistoryQuery, and “replace main image” then disagree: update URL in place vs rotate key and invalidate. AD-6’s table **explicitly assigns URL to ListingArtifact and keys to MediaStore**, inviting dual ownership of one conceptual entity. AD-9 only says bytes go to OSS — it does not pick a single address of record.

**Hole:** tighten AD-6/AD-9 so **one** module owns the durable image identity (recommend: MediaStore owns key + URL minting; ListingArtifact stores only `mediaObjectId` / ordered refs).

---

### Pair 3 — Conflicting state-mutation paths: settle-on-persist vs settle-on-SSE-complete

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **F-Settle-Persist** | application + PicklistArtifact / ListingArtifact | AD-11: application orchestrates「落库 → 结算」; AD-7: usable persist triggers AD-5 settle in the same app service after `saveUsable()` |
| **F-Settle-Stream** | application + AgentRuntime SSE | AD-4: stream includes「成果就绪」; AD-5: settle when run succeeds; wires settle/release to SSE lifecycle (`artifact.ready` → settle; stream error / abort → release) |

**Clash:** Both claim AD-5’s sequence and AD-11’s orchestration. They disagree on **which event is the mutation trigger**:

1. Persist succeeds → F-Settle-Persist **settles** hold H.
2. SSE then fails (client disconnect, proxy timeout) before/without a clean `artifact.ready` → F-Settle-Stream **releases** H.

Same CreditHold, two mutation paths. Conversely: stream ends “complete” with a tool claiming success while persist is still in flight → settle without AD-7 artifact, or double settle if both paths fire. AD-5 names the *business* sequence but not the **single allowed caller / hook** (and forbids Agent tools from writing credits — not which application entrypoint may). AD-4’s “成果就绪” event is undefined relative to DB commit.

**Hole:** one AD that names the **sole settlement/release transition point** (e.g. only after durable AD-7 write committed; SSE events are notifications, never credit triggers; exactly one application command owns hold lifecycle).

---

### Pair 4 — Two owners of one entity: GenerationRun / hold↔session↔artifact join

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **F-Session-Join** | AgentRuntime | AD-6: AgentRuntime writes AgentSession; embeds `creditHoldId`, `artifactType`, `artifactId`, token usage on the session row |
| **F-Hold-Join** | CreditLedger | AD-6: CreditLedger writes CreditHold; embeds `agentSessionId`, `artifactId`, usage summary on the hold / ledger side |

**Clash:** Conventions require “每次计费生成记录模型用量” with **no owner**. The ER seed has User–CreditHold and User–AgentSession but **no GenerationRun**. Both features invent the correlation record on “their” table — each is the sole writer of that table (AD-6 letter) — yet both own the **same logical entity** (one billed generation). HistoryQuery, FR-11 retry, and NFR-2 cost rollups then join differently; retries (AD-7: new billing action) attach the new hold to session vs create a sibling session inconsistently (see Pair 5).

**Hole:** AD naming **GenerationRun** (or equivalent) with a single owner, and forbidding the other side from storing the inverse as source of truth (FK direction fixed).

---

### Pair 5 — Conflicting state-mutation paths + shape: retry / session reuse

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **F-Retry-NewSession** | AgentRuntime + web SSE | AD-7: “重试视为新的计费动作” → new CreditHold + **new** AgentSession + new SSE connection |
| **F-Retry-SameSession** | AgentRuntime + web SSE | AD-7: new billing action = new hold only; **reuses** AgentSession / conversation thread; SSE reconnects with same `sessionId` |

**Clash:** AD-7 locks billing semantics, not **session identity**. AD-4 locks transport, not whether one UJ-2 “不满意再生成” is one session or many. SPA routing, HistoryQuery “conversation”, and hold correlation (Pair 4) diverge. Both are AD-legal.

**Hole:** AD for session lifecycle on retry/regenerate (new session vs new turn-on-same-session) and how that maps to holds.

---

## Secondary pairs (still holes; lower blast radius)

### S1 — Primary key dialect (convention invites split)

Consistency Conventions: “雪花/UUID 字符串主键（**实现时二选一**并全库一致）”.

- **F-Identity**: UUID strings for User / JWT `sub`.
- **F-Artifacts**: snowflake strings for Picklist / ListingPack / CreditHold.

No AD **locks the choice before parallel features start**. “全库一致” is a wish, not a gate. Close: promote to AD with one chosen scheme (or “Identity picks; all others follow before first migration”).

### S2 — CatalogTemplate → Picklist link ownership

ER: `CatalogTemplate ||--o{ Picklist : shapes`. AD-6: CatalogTemplate writes config only; PicklistArtifact writes Picklist.

- **F-Template-FK**: PicklistArtifact persists `templateId` (+ version).
- **F-Template-PromptOnly**: AgentRuntime snapshots template into session context; Picklist has no FK.

History “filter by template” and FR-8 verification disagree. Close: AD that PicklistArtifact **must** persist `templateId` (and optionally immutable template version snapshot) at creation.

### S3 — SSE event envelope deferred too late

Deferred: “SSE 事件 JSON schema 细表（实现第一个 Agent 故事时钉死）”.

- **F-Picklist-SSE**: events `text.delta` / `tool.start` / `picklist.ready`.
- **F-Listing-SSE**: events `message` / `tool` / `artifact.completed`.

AD-4 requires SSE and “稳定字符串” in Conventions but **defers the catalog**. Two Agent features ship incompatible client parsers. At initiative altitude, the **event name set + envelope** is a cross-feature invariant — should be an AD (even if field payloads stay Deferred).

### S4 — Soft-delete / visibility for HistoryQuery

HistoryQuery is read-only (AD-6) with no rule for what “本人近期成果” means when artifacts are replaced by retry.

- **F-History**: lists all Picklist/Listing rows by `createdAt`.
- **F-Artifacts**: retry marks prior pack `superseded` and only latest is “available”.

No write AD for superseded flags → HistoryQuery invents filters. Close: AD on artifact lifecycle states (`draft` / `available` / `failed` / `superseded`) and sole writer.

---

## What the spine already prevents (attacks that failed)

These adversarial attempts **could not** stay AD-compliant:

| Attempt | Blocked by |
| --- | --- |
| Vue calls model vendor SDK / holds API keys | AD-1 |
| Separate deployable “credit-service” or “agent-service” | AD-2 |
| Maven-depend on LIMS Pi artifacts / dual TS+Java Pi | AD-3 |
| Polling job API alongside SSE for billed generate | AD-4 (intent) |
| Agent tool or frontend UPDATE balance | AD-5, AD-6 |
| Anonymous SSE generate | AD-8 |
| Image BLOBs in MySQL | AD-9 |
| domain → interfaces dependency | AD-11 |

Coarse fences work. The failures are **seam contracts between compliant owners**.

---

## Recommended closings (new / tightened ADs)

| ID (suggested) | Closes | Rule sketch |
| --- | --- | --- |
| **AD-12** | Pair 1 | Pin PicklistItem minimum schema + Listing start input (`sourcePicklistId` + `selectedItemId` **or** explicit free-form mode); snapshot vs live reference. |
| **AD-13** | Pair 2 | Single address of record for media: MediaStore owns key + URL minting; ListingArtifact stores only ordered `mediaObjectId` refs (amend AD-6 wording that grants URL to Listing). |
| **AD-14** | Pair 3 | Sole credit transition: after AD-7 durable commit, one application command settles/releases; SSE must not settle/release; define abort-after-persist behavior (keep settled). |
| **AD-15** | Pair 4 | Introduce GenerationRun (owner: application or AgentRuntime — pick one) as sole hold↔session↔artifact↔usage join; CreditHold/AgentSession may FK to it, not mirror full correlation. |
| **AD-16** | Pair 5 | Retry/regenerate: new GenerationRun + new hold; **session policy** fixed (new session **or** same session + new run — choose one). |
| **AD-17** | S1 | Lock ID scheme now (UUID **or** snowflake), not “at implement time”. |
| **AD-18** | S3 | Adopt SSE envelope + stable event-type enum for both generate flows before feature split; payload fields may stay Deferred. |

Also amend **AD-6** table rows for ListingArtifact / MediaStore once AD-13 lands; move “用量记录” from Conventions into AD-15’s owner.

---

## Finding index (for parent gate)

| Sev | Finding | Incompatible pair |
| --- | --- | --- |
| critical | No Picklist↔Listing handoff schema | F-Picklist vs F-Listing |
| critical | Dual ownership of 主图 identity (URL vs key) | F-Listing-Meta vs F-Media-Canon |
| critical | Two settlement triggers (persist vs SSE complete) | F-Settle-Persist vs F-Settle-Stream |
| high | Unowned GenerationRun / correlation entity | F-Session-Join vs F-Hold-Join |
| high | Retry session lifecycle undefined | F-Retry-NewSession vs F-Retry-SameSession |

Secondary: S1 ID dialect, S2 template FK, S3 SSE event catalog, S4 artifact lifecycle for HistoryQuery.

---

## Reviewer note

Seed diagrams and Deferred items do **not** bind builders. Initiative altitude must lock seams that **two features** will touch in parallel (选品 vs Listing, ledger vs SSE, Listing vs Media). Until AD-12..AD-16 (or equivalent tightenings) land, treat this spine as **unsafe to split into parallel feature workstreams**.
