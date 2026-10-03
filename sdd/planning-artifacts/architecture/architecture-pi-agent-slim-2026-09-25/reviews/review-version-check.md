# Review: Version / Reality Check — pi-agent-slim Spine

| Field | Value |
| --- | --- |
| **Spine** | `architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md` |
| **Lens** | Named technologies + upstream model reality check (not AD re-litigation) |
| **Altitude** | feature (slim); may inherit parent Stack |
| **Reviewed** | 2026-09-25 |
| **Verdict** | **PASS with WARN** |

## Scope

Reality-check every **named** technology / storage / upstream model claim against project evidence — not training memory. Anchors requested:

| Anchor | Path / evidence |
| --- | --- |
| Parent APP-META MySQL | `APP-META/docker-config/docker-compose.yml` → `mysql:8.0.36` |
| ebus MyBatis | `forma-infrastructure` mappers + parent POM `mybatis-spring-boot.version=2.3.1` / `mybatis.version=3.5.13` |
| Upstream pi Entry | `~/workspace/pi/packages/agent` (`src/harness/session/types.ts`, `docs/harness.md` Part 2/3) |
| Parent spine Stack | `architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md` § Stack |
| Current Java SessionStore | `pi-agent` `SessionStore` / `schema.sql` / `SqliteSessionStore` |

---

## Verdict summary

**PASS with WARN.** Production Session backend **MySQL** and adapter stack **MyBatis in ebus-infrastructure** are grounded in APP-META + ebus + parent spine. Upstream **Entry / SessionTree Part 2** naming is grounded in `pi/packages/agent/docs/harness.md` and `Entry` types — not invented. Feature spine has **no Stack table**; for feature-altitude slim that is acceptable **if** parent pins are inherited, but the omission is still a **WARN** (new module pins like `sqlite-jdbc` / optional Jedis are unnamed). Several ER/meta fields are **Java SessionStore–shaped**, not pure upstream `SessionMetadata` / `MessageEntry` — label them as hybrid subset, not “verbatim pi.”

---

## Decision-by-decision check

### MySQL (AD-S3 production SessionStore)

| Claim | Spine | Reality | Result |
| --- | --- | --- | --- |
| Shared APP-META / business MySQL | AD-S3, Conventions | Compose service `mysql` image **`mysql:8.0.36`**; starter `DB_HOST: mysql`; parent AD-10 + Stack MySQL 8.0.x | **Confirmed** |
| Patch pin | *Not in feature Stack* | Parent says “compose 钉补丁”; APP-META **already** pins `8.0.36`; parent POM connector `mysql.version=8.0.33` | **Confirmed in APP-META**; feature spine silent on pin (inherit OK) |
| No second primary store | Inherited parent convention | Matches parent “MyBatis 访问 MySQL；禁止第二套主存储” | **Confirmed** (policy) |

### MyBatis (adapter placement)

| Claim | Spine | Reality | Result |
| --- | --- | --- | --- |
| Adapter in `forma-infrastructure` (MyBatis); `pi-agent` **不**依赖 MyBatis | AD-S3, Structural Seed | Infra POM has `mybatis-spring-boot-starter`; packages `…persistence.mybatis…`; `pi-agent/pom.xml` has **no** MyBatis dep | **Confirmed** (convention + deps) |
| Version | *Not in feature Stack* | Parent POM / Stack: MyBatis **3.5.13**, starter **2.3.1** | **Confirmed via parent**; not re-pinned here |
| `MysqlSessionStore` exists | Structural Seed target | **Not present** in tree yet — seed is target shape | **OK for draft AD** (not a false “already shipped” claim) |

### Upstream pi Entry / SessionTree (AD-S3 logical model)

| Claim | Spine | Reality (`~/workspace/pi`) | Result |
| --- | --- | --- | --- |
| `packages/agent` SessionTree / Entry | AD-S3 sources + rule | `EntryBase`: `id`, `seq`, `parentId`, `timestamp`, `type`; `MessageEntry` `type: "message"`; `SessionTree` API | **Confirmed** |
| Part 2 会话树子集 / Part 3 Deferred | AD-S3, Deferred | `packages/agent/docs/harness.md`: **Part 2 — The conversation tree**; **Part 3 — The operation state machine**; registers / write-once documented | **Confirmed** (doc sections, not folklore) |
| `agentLoop` while vs retained StateGraph | Design Paradigm, AD-S1 | `packages/agent/src/agent-loop.ts` exports `agentLoop` with `while (true)`; Java `DefaultAgent` uses `StateGraph` | **Confirmed** both sides |
| append-only Entry + meta split; ≠ Checkpointer | AD-S3 | Upstream: entries write-once; Java already separates `SessionStore` vs `pi:checkpoint:` | **Confirmed** intent |
| Single default lane; defer multi-lane / Operation SM | AD-S3 / Deferred | Matches Part 2 vs Part 3 split in harness.md | **Confirmed** |
| Session meta: `title`, `source`, `compactAnchorSeq`, `user_id` | AD-S3 + ER | Upstream `SessionMetadata` is minimal (`id`, `createdAt`, `parentSessionId?`). `title`/`source`/`compact_anchor_seq` match **Java** `Session` / `schema.sql`. `user_id` is ebus bind (explicitly allowed) | **Partial** — hybrid Java+ebus, not pure pi meta |
| `run_id` on entry row | ER `PI_SESSION_ENTRY.run_id` | Upstream `MessageEntry` has **no** `runId`; `runId` appears on **operation records**. Java `pi_session_message.run_id` + `append(..., runId, ...)` idempotency | **Partial** — Java SessionStore semantics, not Entry type field |
| `compactAnchorSeq` vs compaction entry | AD-S3 hedges “或等价 compaction entry” | Upstream: `CompactionEntry` type; Java: `compact_anchor_seq` column | **OK** (hedge matches evidence) |
| Current production = SQLite | Spine **forbids** silent SQLite for Adam | `SessionStore` Javadoc still says production = SQLite; `schema.sql` is **flat** `pi_session_message` (role/content), **not** Entry `parent_id` / `entry_type` | **Not a false claim** — spine is **target override**; note code/docs still lag AD-S3 |

### Other named tech (spot-check)

| Name | Spine role | Evidence | Result |
| --- | --- | --- | --- |
| Redis (optional CP) | AD-S2 default OFF | `jedis` optional in `pi-agent/pom.xml`; **no** Redis service in APP-META compose | **Confirmed** “don’t assemble by default”; Jedis version from Boot BOM only — unpinned in feature Stack |
| SQLite / `SqliteSessionStore` | Test/local only | Module pin `sqlite-jdbc` **3.45.3.0**; schema present | **Exists**; pin not echoed in feature Stack |
| StateGraph / GraphExecutor | AD-S1 retain | Present under `pi-agent` graph package | **Confirmed** (in-repo, no public version pin needed) |
| Hermes Contribution SPI | AD-S4 forbid growth | `StableContribution` / `ContextContribution` / `VolatileContribution` in tree | **Confirmed** (in-repo) |
| `PiEventBus` / AD-4 | Inherited | Parent AD-4 + ebus `PiEventToAd4Mapper` | **Out of version scope**; existence OK |

---

## Stack section (feature-altitude)

| Observation | Severity |
| --- | --- |
| Feature spine has **no** `## Stack` table | **WARN** — acceptable for slim feature altitude **if** readers treat parent Stack as binding; spine should add one line: “Stack pins inherit `architecture-lippi-ai-ebusiness-2026-09-24` unless overridden.” |
| New/touched pins not listed: MySQL compose tag, `sqlite-jdbc`, optional Jedis | **WARN** — not FAIL; MySQL already pinned in APP-META; others are test/optional paths under AD-S2/S3 |
| No training-asserted “latest MySQL/MyBatis” versions invented in this spine | **Good** — names only; versions live in parent / POMs / compose |

---

## Findings (ordered by severity)

### F1 — WARN: Feature Stack omitted without explicit inherit clause

- Slim altitude may stay thin, but implementers can miss parent pins (`MySQL 8.0.x` / MyBatis 3.5.13 / starter 2.3.1).
- **Fix:** One sentence under Conventions or a 3-row Stack: inherit parent; cite APP-META `mysql:8.0.36`; note `sqlite-jdbc 3.45.3.0` for retained test store.

### F2 — WARN: “对齐 pi Entry” overstates field-level identity

- Evidence supports **structure**: append-only typed entries, seq/id/parentId/timestamp, meta≠checkpoint, Part 2 subset / Part 3 deferred.
- ER/`run_id`/`title`/`source`/`compact_anchor_seq` lean **Java SessionStore + ebus**, not upstream `MessageEntry` / minimal `SessionMetadata`.
- **Fix:** Phrase as “pi Part 2 Entry **subset** + existing Java SessionStore meta/idempotency”; keep `run_id` as adapter/idempotency column, not “pi Entry field.”

### F3 — INFO: Code still documents SQLite as production SessionStore

- Javadoc/`SqliteSessionStore` “生产 SessionStore” contradicts AD-S3 target (MySQL). Expected lag for draft spine; track in implement stories, not a version FAIL.
- Current SQLite schema is flat messages — migrating to Entry-shaped `session_entry` is **new work**, not rename.

### F4 — INFO: Redis absent from APP-META matches AD-S2

- Optional Jedis/Redis CP correctly deferred; no compose Redis to “reality-check” as default.

---

## What was adequately evidence-based

1. **MySQL** already in parent APP-META (`mysql:8.0.36`) + ebus JDBC — AD-S3 production choice is project-backed.
2. **MyBatis** is the ebus persistence convention; ports-in-pi-agent / adapter-in-infrastructure matches module deps.
3. **pi Entry / Part 2–3** grounded in `~/workspace/pi/packages/agent` types + `docs/harness.md`.
4. **StateGraph retain vs `agentLoop` while** both exist in respective codebases.
5. No feature-spine claim of “latest” MyBatis/MySQL from training; versions deferred to parent/compose/POM.

---

## Required corrections before clean PASS (optional for draft)

1. Add explicit **inherit parent Stack** (or 3-row mini Stack).
2. Soften Entry wording to **hybrid subset** (Java meta/`run_id` called out).
3. Optionally note APP-META MySQL tag `8.0.36` as the shared pin.

Architectural ADs (AD-S1..S5 content) were not re-litigated; this lens only checks **named tech / model grounding**.

---

## Evidence snapshot (2026-09-25)

```text
APP-META/docker-config/docker-compose.yml:
  mysql: image mysql:8.0.36

Parent POM:
  mybatis 3.5.13, mybatis-spring-boot-starter 2.3.1, mysql-connector 8.0.33

pi-agent/pom.xml:
  sqlite-jdbc 3.45.3.0; jedis optional (no Redis in APP-META)

pi packages/agent:
  EntryBase / MessageEntry in src/harness/session/types.ts
  docs/harness.md Part 2 (conversation tree) / Part 3 (operation SM)
  src/agent-loop.ts agentLoop while(true)

Current Java schema (pi-agent .../pi/session/schema.sql):
  pi_session + flat pi_session_message (not Entry parent_id/entry_type yet)
```
