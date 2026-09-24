# Review: Version / Reality Check — Architecture Spine

| Field | Value |
| --- | --- |
| **Spine** | `architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md` |
| **Lens** | Finalize — version & live-default reality check |
| **Reviewed** | 2026-09-24 |
| **Verdict** | **FAIL** |

## Scope

Verify every **committed** stack/technology decision was web-researched or reality-checked (existing project, live starter), not asserted from training data. Flag anything that could be out of date and was not confirmed against the web, LIMS, or `create-vue@latest`.

**Reality anchors used**

| Anchor | Source |
| --- | --- |
| LIMS backend | `/Users/echo/workspace/lippi-ai-lims/backend/pom.xml` (+ infrastructure OSS pin) |
| Spine + memlog | `ARCHITECTURE-SPINE.md`, `.memlog.md` |
| Live starter | `create-vue@3.24.0` templates on GitHub (`template/base`, `template/config/typescript`); `npm view create-vue@latest` → `3.24.0` |
| Web | Spring Boot 2.7 docs/releases; MyBatis spring-boot-starter releases; Maven Central OSS SDK; jjwt releases; Docker Compose install docs |

---

## Verdict summary

**FAIL.** LIMS-aligned backend pins (Java 8, Boot 2.7.18, MyBatis, jjwt) and the Boot EOL callout are correctly grounded in the existing project and public records. The **greenfield frontend starter claims are not**: the Stack table asserts create-vue defaults that do not match the live scaffold (TypeScript **5.x** vs **~6.0.0**; Vite caution still underspecifies **Vite ^8** and required **Node ^22.18 / ≥24.12**). Those rows must be corrected before the spine is treated as build-ready.

---

## Decision-by-decision check

### Backend (LIMS-aligned) — mostly confirmed

| Claim | Spine status | Reality check | Result |
| --- | --- | --- | --- |
| Java 8 | Stack + AD-3/upgrade Deferred | LIMS `java.version=1.8`; Boot 2.7 docs: Java 8–21 | **Confirmed** (project + docs) |
| Spring Boot 2.7.18 | Stack; notes **OSS EOL**; Deferred upgrade | LIMS parent `2.7.18`; final OSS 2.7.x (2023-11-23); OSS support ended ~2023-11-24 | **Confirmed**; EOL correctly deferred |
| “Boot 2.7 最高可至 21” | Stack | Spring Boot 2.7.x getting-started: compatible through Java 21 | **Confirmed** (web) |
| MyBatis 3.5.13 / starter 2.3.1 | Stack | LIMS properties match exactly | **Confirmed** (project) |
| jjwt 0.11.5 | Stack | LIMS `jwt.version=0.11.5` | **Confirmed** (project); not latest (see findings) |
| MySQL 8.0.x | Stack (“compose 钉补丁版”) | Technology exists; LIMS connector `8.0.33`; **no compose image tag pinned or researched** | **Partial** — product choice OK; patch pin not reality-checked |
| Aliyun OSS (service) | AD-9 | Product still exists; V1 SDK `aliyun-sdk-oss` and V2 `alibabacloud-oss-v2` both live | **Exists**; SDK line under-specified (see findings) |
| OSS Java SDK version | “脚手架时查 Maven Central 当前稳定版并钉死” | Central V1 latest observed **3.18.5** (2026-01); LIMS uses **3.17.1** | **Intentionally unpinned** — OK for spine, but no LIMS/Central check recorded |
| Docker Compose V2 | Stack + AD-10 | `docker compose` plugin is current install path; Compose CLI releases now at **v5.x** while “V2” still means plugin-vs-V1 | **Naming OK**; version label is architectural, not a pin |

### Frontend (greenfield starter) — gaps / wrong defaults

| Claim | Spine status | Live `create-vue@3.24.0` (2026-09-24) | Result |
| --- | --- | --- | --- |
| Vue via `npm create vue@latest` | Stack | Official path; package **3.24.0** | **Confirmed** as starter choice |
| Vue **3.5.x** | Stack | Template `vue: ^3.5.42`; npm vue latest also 3.5.x | **Confirmed** |
| Vite “以 create-vue 当下为准（**勿假定 Vite 6**）” | Stack | Template **`vite: ^8.2.2`** | **Caution good**; still **not** stating live default (Vite 8) |
| TypeScript “create-vue 默认 **5.x** 线” | Stack | Template **`typescript: ~6.0.0`** | **Wrong vs live starter** |
| Node engines | *Not in Stack* | Template `engines.node`: **`^22.18.0 \|\| >=24.12.0`** | **Missing** greenfield default |
| Memlog `(version) Frontend seed: … Vite 6.x + TypeScript 5.x` | Process note | Contradicts live templates | Shows prior assertion was **not** fully live-checked |

### Non-version technology fitness (spot-check)

| Decision | Still exists / fits? | Notes |
| --- | --- | --- |
| Vue SPA + Spring Boot modular monolith | Yes | Greenfield + LIMS copy path still coherent |
| SSE + `fetch` + `ReadableStream` (not `EventSource`) | Yes | Browser `EventSource` still cannot set `Authorization` — rule is sound |
| MyBatis (not JPA) | Yes | Fits Boot 2.7 / Java 8; 2.3.x line is last for Boot 2.7 |
| JWT Bearer for REST + SSE | Yes | jjwt still maintained; pin is old-by-choice |
| Vendor-copy LIMS `pi-ai` / `pi-agent` | Project existence | Modules present under LIMS backend; not a public version pin |

---

## Findings (ordered by severity)

### F1 — FAIL: TypeScript “默认 5.x” contradicts live create-vue

- **Spine:** Stack → TypeScript | “create-vue 默认 5.x 线，lockfile 为准”
- **Live:** `create-vue@3.24.0` `template/config/typescript/package.json` → `"typescript": "~6.0.0"`
- **Why it matters:** Committed default is wrong even though “lockfile 为准” softens the pin. Implementers reading the Stack table will expect TS 5.
- **Fix:** Change to “create-vue 默认 ~6.x（以脚手架当日 lockfile 为准）” or drop the major-line guess and say only “scaffold + lockfile.”

### F2 — FAIL: Greenfield Vite/Node live defaults not recorded

- **Spine:** Correctly warns not to assume Vite 6; does **not** state that current create-vue ships **Vite ^8.2.x** and **Node ^22.18 \|\| ≥24.12**.
- **Memlog** still says “Vite 6.x + TypeScript 5.x” despite a claimed “Verified … create-vue@latest (npm 2026-09)” — verification incomplete.
- **Why it matters:** Local Docker/dev envelopes and CI Node images will break if agents assume Node 18/20 or Vite 5/6 tutorials.
- **Fix:** Add Stack rows (or a scaffold note): Vite ^8 (create-vue@3.24), Node engines as above; re-verify at scaffold.

### F3 — WARN: Boot 2.7.18 EOL is correctly flagged; memlog EOL date is sloppy

- **Spine Deferred / Stack:** OSS EOL called out — **good** and web-backed (final OSS release 2.7.18; free OSS support ended 2023).
- **Memlog:** “EOL since 2023-06” — approximate/alternate calendar (some secondary sources cite mid-2023; Spring’s final OSS cutover is Nov 2023). Not spine-blocking, but shows date was not carefully cited.
- **Commercial note (optional clarity):** Broadcom/Tanzu commercial support for 2.7 was extended toward end of 2026 — does **not** change OSS EOL for this greenfield/OSS path.

### F4 — WARN: MyBatis pin is LIMS-correct but one patch behind Boot 2.7 final line

- LIMS / spine: **3.5.13 / 2.3.1**
- Upstream last 2.3.x: **mybatis-spring-boot 2.3.2** (MyBatis **3.5.14**, Boot 2.7.18) — “probably last planned 2.3.x”; maintainers recommend Boot 3 / starter 3.x.
- **Status:** Alignment decision is intentional and reality-checked against LIMS. Not a FAIL unless the spine claimed “latest compatible.”
- **Suggestion:** Note “LIMS pin; optional bump to 2.3.2 at scaffold” in Stack or Deferred.

### F5 — WARN: jjwt 0.11.5 and OSS SDK under-specified relative to 2026 ecosystem

- **jjwt:** Project pin **0.11.5** (LIMS) vs current **0.13.0** (breaking API since 0.12). Intentional copy-friction choice — OK if labeled “LIMS-aligned, not latest.”
- **OSS:** Spine defers Maven pin (good). LIMS already uses **aliyun-sdk-oss 3.17.1**; Central V1 **3.18.5**; Alibaba also ships **V2** (`alibabacloud-oss-v2`). Greenfield could copy LIMS 3.17.1 *or* pin Central current *or* evaluate V2 — none of that was web/project-checked into the Stack row.
- **MySQL 8.0.x:** “compose 钉补丁版” without a researched tag (e.g. `mysql:8.0.xx`) remains a soft claim.

---

## What was adequately evidence-based

1. **Java 8 + Spring Boot 2.7.18 + MyBatis + jjwt** — matched to LIMS `pom.xml`; Boot EOL and Java 8–21 range match Spring docs.
2. **Vue 3.5.x + create-vue@latest as seed** — still the official Vue SPA path; Vue major line claim holds.
3. **Vite “don’t assume Vite 6”** — directionally correct vs live Vite 8 (better than hard-coding 6).
4. **Deferring concrete OSS SDK version to scaffold** — honest about not pinning from memory.
5. **Deferred Boot 3 / Java 17+** — appropriate given vendor-copy friction and documented EOL.

---

## Required corrections before PASS

1. Fix Stack **TypeScript** default to match live create-vue (**~6.0** / lockfile), not 5.x.
2. Record live **Vite ^8** and **Node engines** from create-vue templates (or “re-read package.json at scaffold” with no false majors).
3. Optionally: align memlog frontend version note; add MyBatis 2.3.2 / OSS pin notes as WARN-level clarity.

---

## Evidence snapshot (2026-09-24)

```text
LIMS backend/pom.xml:
  spring-boot 2.7.18, java 1.8, mybatis 3.5.13, mybatis-spring-boot 2.3.1, jwt 0.11.5

LIMS infrastructure:
  aliyun-sdk-oss 3.17.1

create-vue@3.24.0 template/base/package.json:
  vue ^3.5.42, vite ^8.2.2, engines.node ^22.18.0 || >=24.12.0

create-vue@3.24.0 template/config/typescript/package.json:
  typescript ~6.0.0

Web:
  Spring Boot 2.7.18 final OSS; compatible Java 8–21
  mybatis-spring-boot 2.3.2 last 2.3.x (MyBatis 3.5.14)
  aliyun-sdk-oss 3.18.5 on Maven Central (V1)
  jjwt 0.13.0 current
```

---

## Reviewer note

Architectural ADs (modular monolith, vendor-copy Pi, SSE, credit ledger, OSS media) were not re-litigated here; this lens only checks **version/existence/starter-default** grounding. After Stack row fixes for create-vue live defaults, re-run this lens for PASS.
