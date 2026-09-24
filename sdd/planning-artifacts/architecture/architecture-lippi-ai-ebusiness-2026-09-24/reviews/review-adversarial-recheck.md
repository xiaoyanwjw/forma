---
name: review-adversarial-recheck
lens: adversarial-divergence-recheck
target: ARCHITECTURE-SPINE.md
prior: review-adversarial.md
altitude: initiative (units below = features/epics)
status: complete
created: 2026-09-24
verdict: pass-with-nits
---

# Adversarial recheck — architecture spine (post-autofix)

Spine re-read: `ARCHITECTURE-SPINE.md` (updated 2026-09-24). Prior attack: `review-adversarial.md` (verdict FAIL).

## Prior holes

| Hole | Status | One sentence |
| --- | --- | --- |
| Picklist↔Listing handoff | **FIXED** | AD-7 + AD-6 + ER lock Listing source as optional `picklistItemId` (nullable for 自填) with `PicklistItem → ListingPack` edge, so teams cannot invent incompatible source-naming strategies. |
| Media URL dual ownership | **FIXED** | AD-6/AD-9 make MediaStore own `objectKey` / URL minting and ListingArtifact store only `mediaObjectId[]` — no second URL truth. |
| Settle on SSE vs persist | **FIXED** | AD-5 forbids settle on SSE end; AD-7/AD-11 require application settle only after usable persist, then emit `artifact_ready` / `run_settled`. |
| GenerationRun correlation | **FIXED** | AD-6 gives AgentRuntime sole write of `GenerationRun` (holdId + sessionId + artifactRef); ER + conventions put usage on that join. |
| Retry lifecycle | **FIXED** | AD-7: every billed generate including retry = new `GenerationRun` + new hold; same `AgentSession` may be reused, old hold must not. |

## New critical divergence holes (max 2)

1. **Artifact visibility after retry** — No AD for lifecycle states (`available` / `superseded` / …); HistoryQuery (FR-12) and artifact writers (FR-11 retry) can still disagree on whether prior packs remain listed.
2. **CatalogTemplate → Picklist link** — ER shows `shapes` but no AD requires PicklistArtifact to persist `templateId` (and optional version snapshot); FR-8 / History “by template” can fork prompt-only vs FK.

## Verdict

**pass-with-nits** — the five load-bearing prior holes are closed; two secondary seam gaps remain and should be tightened before splitting History vs Catalog/Picklist workstreams, but they do not reopen the original FR-7↔FR-9 / ledger↔SSE / Listing↔Media clashes.
