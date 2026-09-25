---
name: review-adversarial
lens: adversarial-divergence
target: ARCHITECTURE-SPINE.md
altitude: feature (units below = stories / PR workstreams)
status: complete
created: 2026-09-25
verdict: FAIL — not divergence-safe for parallel story builds
---

# Adversarial Architecture Review — pi-agent-slim spine

## Method

Altitude is **feature**; units one level down are **stories / PR workstreams** that can ship independently under the same spine. For each attack: invent two units that each obey every ADOPTED AD-S* and Consistency Convention **to the letter**, then show they still ship incompatible shared-data shapes, dual ownership of one entity, or conflicting state-mutation paths. Every surviving pair is a hole — close with a new or tightened AD (not more seed prose or ER commentary).

Spine under attack: `architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md` (draft, 2026-09-25).

Focus seams (per finalize gate): **Session MySQL Entry shape**, **SessionStore port vs adapter**, **Checkpointer vs Session**, **Prompt 3-slot injection**.

## Verdict

**FAIL.** AD-S1..S5 correctly fence “don’t rewrite the graph”, “HITL default off”, “MySQL Session ≠ Checkpoint”, “three prompt slots”, and “don’t touch Skills”. Those are coarse ownership fences. They leave the **wire contracts** that couple `SessionStore` callers ↔ MySQL adapter ↔ Entry rows ↔ Checkpointer beans ↔ `SystemPromptInput` **unnamed**. Two story teams can each be AD-compliant and still refuse to integrate at compile time, migration time, or runtime prompt/transcript shape.

---

## Attack pairs

### Pair 1 — Clashing shared-data shapes: MySQL Entry grain (one-msg vs one-append)

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **S-Entry-Per-Message** | `MysqlSessionStore` (ebus-infra) + DDL | AD-S3: append-only Entry rows, `type=message`, storage-assigned `id`/`seq`/`timestamp`, optional `parentId`, payload = message JSON; ER seed `PI_SESSION_ENTRY`; port stays `append(sessionId, runId, List<Message>)` |
| **S-Entry-Per-RunBatch** | same port + own DDL | Same AD-S3 letter: one Entry per `append` call; `payload` = JSON array of messages; `run_id` on the row; still “at least type=message”; `seq` still monotonic per session |

**Clash:** AD-S3 names Entry *existence* and a minimum column list, not **row grain** or **payload schema**.

- S-Entry-Per-Message: `load` walks N rows → N `Message`; tree edges use `parent_id` between message Entries; History UI / compaction / “replay one turn” assume 1:1 Entry↔Message.
- S-Entry-Per-RunBatch: one row per turn; `payload` is `Message[]`; `parent_id` (if any) points at prior *batch*; same `SessionStore.load` still returns `List<Message>` so AgentSession compiles.

Both satisfy “append-only Entry”、“payload 为消息 JSON”、“同 runId 幂等”. Integration fails when:

- compaction / `setCompactAnchor(seq, …)` means “hide messages with seq≤N” vs “hide batch rows with seq≤N”;
- a third story reads raw SQL for FR12 history and assumes the ER 1:1 shape;
- fork (`parentSessionId` / entry `parentId`) copies different graphs.

**Hole:** no AD locking **Entry grain** (1 Entry = 1 Message), **payload JSON schema** (single Message object, not array/envelope), and whether `parent_id` is message-tree edge, unused, or batch-link.

---

### Pair 2 — Clashing shared-data shapes: compaction truth (column vs compaction Entry)

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **S-Compact-Column** | MysqlSessionStore + `PI_SESSION.compact_anchor_seq` | AD-S3: `compactAnchorSeq（或等价 compaction entry）`; `load` 仅投影 `seq > compactAnchor`; `setCompactAnchor` updates meta column |
| **S-Compact-EntryType** | MysqlSessionStore + `entry_type=compaction` | Same AD-S3 “或等价”; writes a compaction Entry; load skips everything before that entry’s seq; may leave `compact_anchor_seq` null/0 |

**Clash:** The spine **explicitly OR-gates** two persistence models. Both are AD-legal. AgentSession / CLI `/compact` / resume-after-compact then disagree on:

- where the anchor lives (meta vs transcript);
- whether summary text is a normal `message` row, a side effect of `setCompactAnchor`, or the compaction Entry payload;
- idempotent re-compact and cross-instance readers.

ER seed shows **both** `compact_anchor_seq` on session **and** free `entry_type` on entries — inviting dual implementation without a single source of truth.

**Hole:** pick **one** compaction persistence model in an AD (recommend: meta `compact_anchor_seq` + optional summary `message` Entry; forbid a second compaction protocol this phase).

---

### Pair 3 — Dual ownership / port–adapter contract: who evolves `SessionStore`?

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **S-Port-MessageFacade** | `lippi-pi-agent` `SessionStore` | AD-S3: port stays in pi-agent; keeps today’s `List<Message> load/append`; Entry is an **adapter-private** storage detail; InMemory/Sqlite keep Message semantics for tests |
| **S-Port-EntryAPI** | `lippi-pi-agent` SessionStore refactor | AD-S3: “逻辑对齐上游 Entry”; exposes `appendEntry` / `loadEntries` / typed `entry_type`; adapter becomes a thin MyBatis map; “Message projection” becomes a helper — still “端口在 pi-agent、适配器在 infrastructure、不依赖 MyBatis” |

**Clash:** AD-S3 binds **module placement** (port vs MyBatis adapter) and **logical** Entry alignment, but not whether Entry is:

1. a **storage encoding** behind the existing Message port, or  
2. the **public port type** that AgentSession / History must speak.

S-Port-MessageFacade ships MySQL tables matching the ER while AgentSession never sees Entry. S-Port-EntryAPI breaks every caller and both in-tree stores to “align with pi”. Both claim AD-S3. Parallel PRs: infra migration assumes (1); agent core refactor assumes (2) → merge conflict of API + tests + Sqlite.

Secondary wire gaps left open by the same AD:

- **DDL ownership:** spine does not say whether APP-META SQL, ebus Flyway/Liquibase, or infra module owns `PI_SESSION*` creation. Two units can each ship a migration with different table/column names (`pi_session` vs `PI_SESSION` vs `session`).
- **Bean default race:** Conventions say SessionStore→MySQL `@Primary`; existing `AgentConfiguration` still `@ConditionalOnMissingBean` → Sqlite. Unit **S-Starter-MySQL** registers `@Primary MysqlSessionStore`. Unit **S-Pi-Default-Sqlite** “only changes docs” and leaves MissingBean=Sqlite. Adam “禁止无配置时静默落盘 SQLite” is a **starter policy sentence**, not a binding condition on the pi-agent auto-config. A profile that forgets the infrastructure bean still gets cwd SQLite — AD-S3 letter (“adapter @Primary”) is satisfied whenever the bean *is* present, and silent when absent.

**Hole:** AD that (a) freezes **port API surface for this phase** (Message projection in / Entry out of public port **or** the reverse — pick one), (b) names **sole DDL owner + exact table/column names**, (c) forbids Sqlite as any non-test `@Bean` when Adam/ebus profile is active (positive condition, not only “@Primary when someone remembers”).

---

### Pair 4 — Conflicting state-mutation paths: Checkpointer vs Session (who is mid-run truth?)

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **S-Session-Only-Durability** | AgentSession + MysqlSessionStore; Checkpointer = InMemory/No-Op | AD-S2: default InMemory/No-Op; AD-S3: Session ≠ Checkpointer, no shared table/key; billing path 不得依赖 resume; transcript append after turns is the only durable conversation |
| **S-Checkpoint-When-RedisPresent** | keep `PiCheckpointAutoConfiguration`; optional Redis CP `@Primary` when `JedisPool` exists | AD-S2: “API 与类型保留”、“可选 Redis 实现可留在树内”、“除非显式配置打开”; interprets “有 JedisPool” as the explicit open; still uses key prefix `pi:checkpoint:` ≠ Session tables |

**Clash:** AD-S2 forbids Adam *depending on resume to finish billing*, and forbids shared Session/Checkpoint keyspace. It does **not** bind:

- whether **mid-graph tool-loop state** may be durable when Redis appears;
- what “显式配置打开” means (property flag vs bean presence vs profile);
- after crash: recover from **Session transcript only** vs **Checkpointer latest** vs both (and which wins).

S-Session-Only-Durability: pod restart mid-tools → Session has partial messages; graph state gone; next `prompt` continues from Message list; no `resume`.  
S-Checkpoint-When-RedisPresent: same crash → Redis has graph checkpoint; operator/agent calls `resume`; Session may also have appended deltas → **double application** of tool results or divergent message lists.

Both are AD-legal: neither shares `pi:checkpoint:` with Session MySQL; billing “can” complete without resume on the happy path. The incompatible mutation is **recovery path ownership** of one run’s state.

Also: AD-S2 allows “No-Op” Checkpointer; AD-S1 keeps StateGraph. A No-Op that drops every save vs InMemory that keeps process-local state changes testability and multi-instance behavior without an AD saying which default bean Adam starter must wire.

**Hole:** AD that (1) defines Adam’s **sole mid-run durability policy** this phase (recommend: Checkpointer always No-Op/InMemory; Redis CP requires named property e.g. `lims.pi.checkpoint.redis.enabled=true`, not mere `JedisPool`), (2) forbids using Checkpointer as conversation truth (Session transcript only for reload), (3) states crash behavior: no resume on Adam default path.

---

### Pair 5 — Clashing injection shapes: Prompt 3-slot “纯字符串” vs map segments

| Unit | Scope | How it obeys the letter |
| --- | --- | --- |
| **S-Prompt-ThreeStrings** | AgentSession / application | AD-S4: inject **纯字符串** into stable/context/volatile; `before_agent_start` returns three strings (or `ContextOverwrite`); no new map SPI; node does not rebuild system |
| **S-Prompt-NamedMaps** | keep / extend current `SystemPromptInput` maps | AD-S4: “或等价简单结构”; three slots remain maps keyed `soul`/`skills`/…/`before_agent_start`; Session sets map entries; `extend(ContextOverwrite)` writes the same keys; still “no new Contribution SPI” |

**Clash:** AD-S4’s parenthetical **“或等价简单结构”** legitimizes both the prose ideal (three strings) and the **current code** (three `Map<String,String>` + reserved keys + contribution merge in `DefaultPromptBuilder`). 

- S-Prompt-ThreeStrings ships a Session API `prompt(sessionId, text, SystemPromptParts{stable,context,volatile})` and expects PromptBuilder to concatenate three blobs (cache-friendly).
- S-Prompt-NamedMaps ships page context into `context["context"]`, soul into `stable["soul"]`, and hooks into `BEFORE_AGENT_START` inside **each** map; format order and truncation (`stableMaxChars`) depend on key iteration.

Callers, extensions, and tests diverge: one team deletes map keys as “SPI sprawl”; the other treats map keys as the stability contract. Conventions say “三槽字符串；扩展用 before_agent_start 增量” — still silent on:

- who **owns** filling base stable/context (Session vs application vs DefaultAgent bootstrap);
- whether `ContextOverwrite` may write into **all three** slots or only volatile;
- whether Contribution/Hermes keys remain legal “not a new SPI” or are banned noise under AD-S5 cleanup.

**Hole:** AD that freezes the **wire type** for this phase: either (A) three opaque strings + `ContextOverwrite` append-only into those three, forbidding named sub-keys in the public inject path, **or** (B) the existing ordered maps with an **enum/allowlist of keys** and a single owner for each key. Ban half-migrations. Name the sole assembler (`PromptBuilder` / `SystemPromptInput.format`) and forbid a second system string elsewhere (already said for nodes — extend to Session/application).

---

## Secondary pairs (still holes; lower blast radius)

### S1 — `user_id` optional column vs listRecent semantics

AD-S3: user binding by ebus application; “可在 MySQL 适配表加 `user_id`”；**不**改 SessionStore 键语义.

- **S-History-FilterInApp**: `listRecent` returns global-by-updated; application filters by joining its own session↔user table.
- **S-History-FilterInAdapter**: adapter adds `user_id` and *silently* scopes `listRecent` when a ThreadLocal/user hint exists — port signature unchanged (letter OK).

FR12 “仅本人” then depends on undocumented adapter behavior. Close: AD — either port gains explicit `listRecent(userId, …)` owned by application passing userId, or adapter **must not** filter and application owns all ACL.

### S2 — Session meta fields vs `Session.Meta` / `getOrCreate`

ER lists `title`, `parent_session_id`, `source`, timestamps. Port has `getOrCreate(Meta)`, `updateTitle`, `listChildren`.

- **S-Meta-Minimal**: only `sessionId` + timestamps; title/parent/source deferred.
- **S-Meta-FullTree**: requires parent/source on create; forks copy entry trees via `parentId`.

AD-S3 says meta “等” — open-ended. Close: minimum required meta columns + create invariants for this phase.

### S3 — `runId` idempotency scope

AD-S3: `append(..., runId, messages)` 同 runId 幂等.

- **S-Idem-SkipEntireAppend**: second append with same runId no-ops entirely.
- **S-Idem-SkipByMessageId**: dedupe inside payload by message id; partial overlap allowed.

AgentSession retry / SSE reconnect double-append behavior diverges. Close: define idempotency as **whole-append skip per (sessionId, runId)**.

---

## What the spine already prevents (attacks that failed)

| Attempt | Blocked by |
| --- | --- |
| Replace StateGraph with while `agentLoop` in this PR | AD-S1 |
| Delete `resume` / Checkpointer types to “slim” | AD-S2 |
| Require HITL WRITE approve on Adam 选品/Listing | AD-S2 |
| Store Session transcript in Redis under `pi:checkpoint:` | AD-S3 |
| Put MyBatis Mapper inside `lippi-pi-agent` | AD-S3 |
| Add Hermes Contribution map SPI / second system assembler in nodes | AD-S4 |
| Delete Skill platform / slash as part of this slim | AD-S5 |
| Settle credits on `AGENT_END` inside pi-agent Tool | Inherited AD-5 + AD-S5 |
| Business injects `DefaultAgent` instead of `AgentSession` | Consistency + inherited AD-3 |

Coarse fences work. Failures are **seam contracts between compliant story owners**.

---

## Recommended closings (new / tightened ADs)

| ID (suggested) | Closes | Rule sketch |
| --- | --- | --- |
| **AD-S6** | Pair 1 | Entry grain: **1 row = 1 Message**; `payload` = one Message JSON object; `entry_type` allowlist this phase = `message` only (+ optional summary message after compact); `parent_id` null or unused (single lane, no message tree required). |
| **AD-S7** | Pair 2 | Compaction: **only** `PI_SESSION.compact_anchor_seq` (+ optional summary Message Entry); forbid `entry_type=compaction` protocol this phase. |
| **AD-S8** | Pair 3 | Port freeze: keep `SessionStore` Message projection API this phase; Entry is adapter-private. DDL: sole owner + exact names (`pi_session` / `pi_session_entry` …). Adam/ebus profile: **no** Sqlite `@Bean`; MissingBean must fail or use InMemory — never cwd file. |
| **AD-S9** | Pair 4 | Adam default Checkpointer = InMemory or No-Op via **explicit property**; Redis CP only if `…checkpoint.redis.enabled=true` (JedisPool alone insufficient). Reload/continue uses **Session transcript only**; `resume` not on billed happy path. |
| **AD-S10** | Pair 5 | Freeze inject wire: choose **maps-with-allowlisted-keys** (match current code) **or** three opaque strings — not both. Name sole filler owners per slot; `ContextOverwrite` append-only; ban new keys and dual assemblers. |
| **AD-S11** | S1–S3 | `listRecent` ACL at application with explicit userId argument **or** documented non-filtering adapter; meta minimum fields; `(sessionId, runId)` whole-append idempotency. |

Amend Consistency Conventions once AD-S6..S10 land so seed ER cannot be read as “OR compaction” or “payload = any JSON about messages”.

---

## Finding index (for finalize gate)

| Sev | Finding | Incompatible pair |
| --- | --- | --- |
| critical | Entry row grain + payload schema unnamed | S-Entry-Per-Message vs S-Entry-Per-RunBatch |
| critical | Compaction dual protocol explicitly OR-gated | S-Compact-Column vs S-Compact-EntryType |
| critical | Port=Message vs Port=Entry + DDL/bean default race | S-Port-MessageFacade vs S-Port-EntryAPI |
| high | Mid-run durability / resume ownership (Session vs CP) | S-Session-Only-Durability vs S-Checkpoint-When-RedisPresent |
| high | Prompt inject type: three strings vs named maps | S-Prompt-ThreeStrings vs S-Prompt-NamedMaps |

Secondary: S1 user_id filtering, S2 meta minimum, S3 runId idempotency grain.

---

## Reviewer note

Feature-altitude spines must lock **storage grain, port surface, default beans, and inject wire types** before splitting “MySQL adapter”, “Session facade”, “checkpoint default”, and “prompt slim” into parallel stories. Seed ER + “逻辑对齐” + “或等价” are divergence magnets, not gates. Until AD-S6..S10 (or equivalent tightenings) land, treat this spine as **unsafe to finalize for parallel implementation**.
