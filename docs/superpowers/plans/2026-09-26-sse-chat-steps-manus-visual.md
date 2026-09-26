# SSE Chat Steps Manus Visual Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle SSE-driven `chat-steps` and「工作过程」to match Manus/mock plan-checklist look—without fake plan steps or new AD-4 events.

**Architecture:** Keep existing `progressSteps` / `streamText` data path. Change Vue markup (remove trailing「进行中」, chevron disclosure) and CSS (chip background via `--chip`, no hard card border). Add `--chip` token if missing.

**Tech Stack:** Vue 3, Vitest, existing `agentProgress` helpers.

**Spec:** `docs/superpowers/specs/2026-09-26-sse-chat-steps-manus-visual-design.md`

## Global Constraints

- Steps/content from Agent SSE only — no fake plan skeleton / no picklist-specific step strings
- Display labels via existing `toolDisplayLabel`; JSON still filtered by `processStreamText`
- Fold threshold remains **120**; result sentence outside process block
- No new AD-4 event names; no ArtifactStore / Computer progressive changes
- Visual reference: `sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/manus-chat.css` `.msg .chat-steps` (chip `#f5f5f5`, no heavy border)

## File Map

| Path | Role |
|------|------|
| `lippi-ai-ebus-web/src/styles/tokens.css` | Add `--chip: #f5f5f5` |
| `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue` | Steps + disclosure markup |
| `lippi-ai-ebus-web/src/views/business/scene/ecommerceWorkspaceSession.css` | Manus-like process styles |
| `EcommerceWorkspacePlaceholder.test.ts` | Assert no「进行中」label; chevron / process class hooks |

---

### Task 1: Token + markup + CSS (TDD via DOM assertions)

**Files:**
- Modify: `lippi-ai-ebus-web/src/styles/tokens.css`
- Modify: `EcommerceWorkspacePlaceholder.vue` (process block template)
- Modify: `ecommerceWorkspaceSession.css`
- Modify: `EcommerceWorkspacePlaceholder.test.ts`

**Interfaces:**
- Consumes: existing `m.steps`, `streamFoldFor`, `toolDisplayLabel`, `processStreamText`
- Produces: DOM matching Manus checklist cues (chip class hooks for tests)

- [ ] **Step 1: Extend failing tests**

In `EcommerceWorkspacePlaceholder.test.ts`, within the success SSE test (or a focused assertion block after steps render):

```ts
expect(mounted.root.querySelector('.chat-steps')?.textContent).toContain('读取技能说明')
expect(mounted.root.querySelector('.chat-steps')?.textContent).not.toMatch(/进行中/)
expect(mounted.root.querySelector('.chat-steps .step-status')).toBeNull()
expect(mounted.root.querySelector('.chat-stream-head .chat-stream-chevron')).toBeTruthy()
expect(getComputedStyle(mounted.root.querySelector('.chat-steps') as Element).borderWidth === '0px'
  || !(mounted.root.querySelector('.chat-steps') as HTMLElement).style.border).toBeTruthy()
```

Prefer stable class assertions over `getComputedStyle` if happy-dom is flaky:
- `expect(mounted.root.querySelector('.chat-steps')).toBeTruthy()`
- `expect(mounted.root.querySelector('.chat-stream-chevron')).toBeTruthy()`
- `expect(src of tokens.css).toMatch(/--chip:\s*#f5f5f5/)` via `readFileSync` on tokens (optional)

Also assert vue source / rendered HTML has no `.step-status`.

- [ ] **Step 2: Run — expect FAIL**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
```

- [ ] **Step 3: Add `--chip` token**

In `tokens.css` `:root`:

```css
--chip: #f5f5f5;
```

- [ ] **Step 4: Update template**

Steps — remove `.step-status`; keep ok / pending + label:

```vue
<ul v-if="m.steps?.length" class="chat-steps" aria-label="工作步骤">
  <li v-for="s in m.steps" :key="s.id" :class="{ running: !s.done }">
    <span v-if="s.done" class="ok">✓</span>
    <span v-else class="pending" aria-label="进行中">…</span>
    <span class="step-label">{{ toolDisplayLabel(s.label) }}</span>
  </li>
</ul>
```

工作过程 — chevron + title as one control when fold needed; always show title row:

```vue
<div v-if="m.streamText && streamProcessFor(m)" class="chat-stream">
  <button
    type="button"
    class="chat-stream-head"
    :disabled="!streamFoldFor(m).needsFold"
    @click="streamFoldFor(m).needsFold && toggleStreamExpand(m.id)"
  >
    <span class="chat-stream-chevron" aria-hidden="true">{{
      expandedStreamIds.has(m.id) || !streamFoldFor(m).needsFold ? '▾' : '▸'
    }}</span>
    <span class="chat-stream-title">工作过程</span>
    <span v-if="streamFoldFor(m).needsFold" class="chat-stream-toggle">
      {{ expandedStreamIds.has(m.id) ? '收起' : '展开' }}
    </span>
  </button>
  <pre class="chat-stream-body">…</pre>
</div>
```

(Keep fold body logic identical; only wrap head.)

- [ ] **Step 5: CSS — Manus checklist**

```css
.msg .chat-steps {
  list-style: none;
  margin: 0 0 8px;
  padding: 10px 12px;
  background: var(--chip, #f5f5f5);
  border: 0;
  border-radius: 10px;
  font-size: 0.8125rem;
  color: var(--ink);
}
.msg .chat-steps li {
  padding: 3px 0;
  display: flex;
  gap: 8px;
  align-items: center;
}
.msg .chat-steps .ok { color: #16a34a; flex-shrink: 0; }
.msg .chat-steps .pending {
  color: var(--mute);
  flex-shrink: 0;
  animation: chat-step-pulse 1.1s ease-in-out infinite;
}
/* remove .step-status rules */

.msg .chat-stream {
  margin: 0 0 10px;
  padding: 10px 12px;
  background: var(--chip, #f5f5f5);
  border: 0;
  border-radius: 10px;
}
.msg .chat-stream-head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0 0 6px;
  cursor: pointer;
  text-align: left;
  color: inherit;
}
.msg .chat-stream-head:disabled {
  cursor: default;
}
.msg .chat-stream-chevron {
  font-size: 0.7rem;
  color: var(--mute);
  width: 0.9rem;
}
.msg .chat-stream-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--mute);
}
.msg .chat-stream-toggle {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--mute);
}
```

- [ ] **Step 6: Run tests — PASS**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/composables/agent/agentProgress.test.ts src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts && npm run lint
```

- [ ] **Step 7: Commit**

```bash
git add lippi-ai-ebus-web/src/styles/tokens.css \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  lippi-ai-ebus-web/src/views/business/scene/ecommerceWorkspaceSession.css \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
git commit -m "fix(web): align SSE chat steps with Manus checklist visuals"
```

---

### Task 2: Spec status + verify

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-sse-chat-steps-manus-visual-design.md` → `状态：implemented`

- [ ] **Step 1:** Mark implemented  
- [ ] **Step 2:** Re-run focused FE suite (same command as Task 1 Step 6)  
- [ ] **Step 3:** Commit `docs: mark sse chat steps manus visual implemented`

---

## Spec coverage self-check

| Spec | Task |
|------|------|
| SSE-only steps | 1 (no new step sources) |
| Chip / ✓ / no trailing 进行中 | 1 |
| 工作过程 chevron + same chip | 1 |
| JSON / fold / result sentence | unchanged helpers; regression tests |
| Spec status | 2 |

## Placeholder scan

No TBD; CSS values and DOM hooks specified.
