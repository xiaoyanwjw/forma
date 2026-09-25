# ContextModifier design

**Date:** 2026-09-25  
**Status:** implemented (2026-09-25)  
**Module:** `lippi-pi-agent`

## Problem

`ContextOverwrite` is misnamed and weak:

- Name suggests replace; implementation only **appends** into allowlist key `before_agent_start` on each of the three maps.
- `formatStable()` always injects `DEFAULT_SOUL` when `soul` is empty, so hook text never truly owns the stable segment.
- Callers who expect “overwrite took effect” see no visible change when base maps already dominate the formatted system string.

## Goal

Replace `ContextOverwrite` with `ContextModifier` that supports **whole-segment overwrite** and **append**, with clear merge rules across multiple `before_agent_start` handlers.

## Non-goals

- No new prompt keys / Contribution SPI (AD-S10 allowlist for map-based base assembly stays).
- No second system assembler outside `SystemPromptInput` / `PromptBuilder`.
- No change to Graph topology or Tool-loop.

## Shape

```text
ContextModifier
  overwrite: PromptSegments?   // null segment = leave that slot alone
  append:    PromptSegments?

PromptSegments
  stable:   String?
  context:  String?
  variable: String?
```

Factory helpers (illustrative):

- `ContextModifier.empty()`
- `ContextModifier.overwrite(stable, context, variable)` — append null
- `ContextModifier.append(stable, context, variable)` — overwrite null
- `ContextModifier.of(overwrite, append)`

Delete type `ContextOverwrite`. Rename call sites:

| Old | New |
|-----|-----|
| `TurnInput.contextOverwrite` | `TurnInput.contextModifier` |
| `SystemPromptInput.extend(ContextOverwrite)` | `SystemPromptInput.apply(ContextModifier)` |
| EventBus merge return type | `ContextModifier` |

## Apply semantics (single modifier on a built `SystemPromptInput`)

Order (fixed):

1. Build base maps as today (skills / tools / page `context`, etc.).
2. For each segment `X ∈ {stable, context, variable}`:
   - If `overwrite.X` has text → **replace entire segment `X` with that opaque string** (no default soul injection for that segment; map contents for `X` are ignored for `format()` of that segment).
   - If `append.X` has text → append after the current formatted segment `X` with `\n\n`.
3. `format()` = join non-blank stable + context + variable blocks.

Null / blank field = no-op for that field.

## Multi-handler merge (`DefaultPiEventBus` `BEFORE_AGENT_START`)

Scan handlers in registration order:

- **overwrite** per segment: **last non-blank wins**.
- **append** per segment: **concatenate** all non-blank pieces in order (`\n\n`).
- Emit one merged `ContextModifier`, then Session / Loop applies it with the single-modifier order above (overwrite then append).

Not last-write-wins for append. Not first-lock for overwrite.

## Why approach A (opaque segment strings)

Clearing maps and stuffing one allowlist key still leaves `formatStable()` prepending `DEFAULT_SOUL`, so “whole-segment overwrite” would remain false. Opaque segment override flags (or equivalent internal state) make overwrite mean what callers expect.

Implementation sketch (internal to `SystemPromptInput`, not public SPI):

- Optional `stableOverride` / `contextOverride` / `variableOverride` strings set by apply(overwrite).
- `formatStable()`: if override set → return truncated override; else existing map path + default soul.
- Append applies to the **formatted** segment string (override or map path), then truncate stable if needed.

## Test plan

- Unit: apply overwrite-only, append-only, both (overwrite then append).
- Unit: blank/null fields no-op; empty modifier no-op.
- EventBus: two handlers overwrite same segment → last wins; appends concatenate.
- Session / Loop: modifier from extension appears in `TurnInput` and in final `SYSTEM_PROMPT`.
- Regression: base skills/tools/context still format when modifier empty.

## Architecture notes

- Align docs that still say “`ContextOverwrite` 只追加”: Spine / README / Story 2.6 wording → `ContextModifier` overwrite+append.
- AD-S10: map allowlist remains for **base** assembly; overwrite bypasses maps for that segment’s format output only—does not invent new keys.

## Spec self-review

- [x] No placeholder APIs left unnamed
- [x] Overwrite vs append order fixed
- [x] Multi-handler merge fixed
- [x] Scope limited to prompt modifier + call sites
- [x] Explains prior “not taking effect” failure mode
