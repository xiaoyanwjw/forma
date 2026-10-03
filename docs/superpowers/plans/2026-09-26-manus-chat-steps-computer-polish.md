# Manus Chat Steps + Computer Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show agent-emitted tool steps and foldable `message_delta` text in the ecommerce chat (Manus-style), and polish Computer list visuals—without scene-specific step copy.

**Architecture:** Extend `useAgentPicklistRun` to expose live `progressSteps` + `streamText` from SSE. Workspace agent bubble renders generic `chat-steps` + collapsible message (120-char fold). `ComputerRenderer` CSS-only hierarchy for list items.

**Tech Stack:** Vue 3, Vitest, existing AD-4 SSE types.

**Spec:** `docs/superpowers/specs/2026-09-26-manus-chat-steps-computer-polish-design.md`

## Global Constraints

- Steps/messages from Agent SSE only — no hardcoded picklist step strings
- Fold threshold **120** characters; ≤120 no fold control
- Folded preview = **first 120 chars** + `…` (not tail)
- Result sentence outside fold block, always full
- No new AD-4 event names; no ArtifactStore / skill / projector field changes
- Pure text for message body (no Markdown)

## File Map

| Path | Role |
|------|------|
| `forma-web/src/composables/agent/agentProgress.ts` (new) | Pure helpers: parse tool label, append delta text, fold preview |
| `forma-web/src/composables/agent/agentProgress.test.ts` | Unit tests for helpers |
| `forma-web/src/composables/agent/useAgentPicklistRun.ts` | Expose `progressSteps`, `streamText`; update on SSE |
| `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue` | ChatMessage + UI for steps / fold |
| `forma-web/src/views/business/scene/ecommerceWorkspaceSession.css` or scoped | `.chat-steps`, `.chat-stream` |
| `forma-web/src/components/business/computer/ComputerRenderer.vue` | List visual polish |
| `*.test.ts` | Workspace + ComputerRenderer |

---

### Task 1: Progress helpers (TDD)

**Files:**
- Create: `forma-web/src/composables/agent/agentProgress.ts`
- Test: `forma-web/src/composables/agent/agentProgress.test.ts`

**Interfaces:**
- Produces:
  - `export const MESSAGE_FOLD_THRESHOLD = 120`
  - `export type ProgressStep = { id: string; label: string; done: boolean }`
  - `export function toolEventLabel(data: Record<string, unknown>): string`
  - `export function applyToolStarted(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[]`
  - `export function applyToolFinished(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[]`
  - `export function appendMessageDelta(prev: string, data: Record<string, unknown>): string`
  - `export function foldPreview(text: string, threshold?: number): { needsFold: boolean; preview: string }`

- [ ] **Step 1: Write failing tests**

```ts
import { describe, expect, it } from 'vitest'
import {
  MESSAGE_FOLD_THRESHOLD,
  appendMessageDelta,
  applyToolFinished,
  applyToolStarted,
  foldPreview,
  toolEventLabel,
} from './agentProgress'

describe('agentProgress', () => {
  it('toolEventLabel prefers toolName then name then payload string', () => {
    expect(toolEventLabel({ toolName: 'read_skill' })).toBe('read_skill')
    expect(toolEventLabel({ name: 'echo' })).toBe('echo')
    expect(toolEventLabel({ payload: 'do-thing' })).toBe('do-thing')
    expect(toolEventLabel({})).toBe('tool')
  })

  it('applyToolStarted appends running step; finished marks matching label done', () => {
    let steps = applyToolStarted([], { toolName: 'read_skill' })
    expect(steps).toHaveLength(1)
    expect(steps[0].done).toBe(false)
    expect(steps[0].label).toBe('read_skill')
    steps = applyToolFinished(steps, { toolName: 'read_skill' })
    expect(steps[0].done).toBe(true)
  })

  it('appendMessageDelta concatenates text or delta fields', () => {
    expect(appendMessageDelta('', { text: '你好' })).toBe('你好')
    expect(appendMessageDelta('你好', { delta: '世界' })).toBe('你好世界')
  })

  it('foldPreview uses 120 threshold', () => {
    const short = 'a'.repeat(120)
    expect(foldPreview(short).needsFold).toBe(false)
    expect(foldPreview(short).preview).toBe(short)
    const long = 'a'.repeat(121)
    const folded = foldPreview(long)
    expect(folded.needsFold).toBe(true)
    expect(folded.preview).toBe('a'.repeat(MESSAGE_FOLD_THRESHOLD) + '…')
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd forma-web && npm test -- --run src/composables/agent/agentProgress.test.ts
```

- [ ] **Step 3: Implement `agentProgress.ts`**

```ts
export const MESSAGE_FOLD_THRESHOLD = 120

export type ProgressStep = { id: string; label: string; done: boolean }

export function toolEventLabel(data: Record<string, unknown>): string {
  for (const key of ['toolName', 'name', 'tool', 'payload'] as const) {
    const v = data[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return 'tool'
}

export function applyToolStarted(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[] {
  const label = toolEventLabel(data)
  const id = typeof data.toolCallId === 'string' && data.toolCallId.trim()
    ? data.toolCallId.trim()
    : `tool-${steps.length}-${label}`
  return [...steps, { id, label, done: false }]
}

export function applyToolFinished(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[] {
  const label = toolEventLabel(data)
  const callId = typeof data.toolCallId === 'string' ? data.toolCallId.trim() : ''
  let matched = false
  return steps.map((s) => {
    if (matched) return s
    if ((callId && s.id === callId) || (!callId && s.label === label && !s.done)) {
      matched = true
      return { ...s, done: true }
    }
    return s
  })
}

export function appendMessageDelta(prev: string, data: Record<string, unknown>): string {
  const chunk =
    (typeof data.text === 'string' && data.text) ||
    (typeof data.delta === 'string' && data.delta) ||
    (typeof data.payload === 'string' && data.payload) ||
    ''
  return prev + chunk
}

export function foldPreview(text: string, threshold = MESSAGE_FOLD_THRESHOLD): { needsFold: boolean; preview: string } {
  if (text.length <= threshold) return { needsFold: false, preview: text }
  return { needsFold: true, preview: text.slice(0, threshold) + '…' }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add forma-web/src/composables/agent/agentProgress.ts \
  forma-web/src/composables/agent/agentProgress.test.ts
git commit -m "feat(web): helpers for agent tool steps and message fold"
```

---

### Task 2: Wire progress into useAgentPicklistRun + chat UI

**Files:**
- Modify: `useAgentPicklistRun.ts`
- Modify: `EcommerceWorkspacePlaceholder.vue`
- Modify: `ecommerceWorkspaceSession.css` (or scoped styles)
- Modify: `EcommerceWorkspacePlaceholder.test.ts`

**Interfaces:**
- Consumes: `agentProgress` helpers
- Produces: `progressSteps`, `streamText` refs; chat bubble renders them live then snapshot on finish

- [ ] **Step 1: Extend composable**

On each SSE event inside the loop:

```ts
// refs
const progressSteps = ref<ProgressStep[]>([])
const streamText = ref('')

// reset at start of startPicklistRun / reset()
progressSteps.value = []
streamText.value = ''

if (event.name === 'tool_started') {
  progressSteps.value = applyToolStarted(progressSteps.value, event.data)
}
if (event.name === 'tool_finished') {
  progressSteps.value = applyToolFinished(progressSteps.value, event.data)
}
if (event.name === 'message_delta') {
  streamText.value = appendMessageDelta(streamText.value, event.data)
}
```

Export `progressSteps`, `streamText` from the composable.

- [ ] **Step 2: Extend ChatMessage**

```ts
interface ChatMessage {
  id: string
  role: 'user' | 'agent'
  text: string
  steps?: ProgressStep[]
  streamText?: string
}
```

While running, update the thinking bubble reactively (watch `progressSteps`/`streamText` or patch message each event via watch). Simplest: `watch([progressSteps, streamText], () => { patch thinkingId message })` while `picklistRunning`.

On success/failure finalize:

```ts
messages.value[idx] = {
  id: thinkingId,
  role: 'agent',
  text: reply, // or error
  steps: progressSteps.value.length ? [...progressSteps.value] : undefined,
  streamText: streamText.value.trim() || undefined,
}
```

- [ ] **Step 3: Template for agent body**

```vue
<ul v-if="m.steps?.length" class="chat-steps">
  <li v-for="s in m.steps" :key="s.id">
    <span class="ok" v-if="s.done">✓</span>
    <span class="pending" v-else>·</span>
    {{ s.label }}
  </li>
</ul>
<div v-if="m.streamText" class="chat-stream">
  <button
    v-if="foldPreview(m.streamText).needsFold"
    type="button"
    class="chat-stream-toggle"
    @click="toggleStreamExpand(m.id)"
  >
    {{ expandedStreamIds.has(m.id) ? '收起' : '展开' }}
  </button>
  <pre class="chat-stream-body">{{
    expandedStreamIds.has(m.id) || !foldPreview(m.streamText).needsFold
      ? m.streamText
      : foldPreview(m.streamText).preview
  }}</pre>
</div>
<p>{{ m.text }}</p>
```

Use a `Set`/`ref<Record<string, boolean>>` for expand state. Style `.chat-steps` like Manus mock (chip background, ✓ green).

- [ ] **Step 4: Tests**

Update SSE mock in success test to include:

```
event: tool_started\ndata: {"toolName":"read_skill"}\n\n
event: tool_finished\ndata: {"toolName":"read_skill"}\n\n
event: message_delta\ndata: {"text":"${'x'.repeat(130)}"}\n\n
```

Assert `.chat-steps` contains `read_skill` and ✓; `.chat-stream-body` shows folded preview (ends with `…`); click 展开 shows full length; no picklist-specific step strings in source (grep test optional).

Also assert short message (≤120) has no toggle.

- [ ] **Step 5: Run**

```bash
cd forma-web && npm test -- --run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts src/composables/agent/agentProgress.test.ts
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(web): show agent tool steps and foldable message_delta in chat"
```

---

### Task 3: ComputerRenderer list polish

**Files:**
- Modify: `ComputerRenderer.vue` (scoped CSS + optional class on price line)
- Modify: `ComputerRenderer.test.ts`

**Interfaces:**
- Consumes: existing list block shape
- Produces: stronger hierarchy (dims pills, priority tag, price line weight)

- [ ] **Step 1: Failing/extend test**

```ts
it('renders dim tags as pills and price line emphasis', async () => {
  // mount doc with list item lines: ['价格带：19-39', '痛点：x'], tags: ['需求 高']
  expect(root.querySelector('.dims span.dim-pill')).toBeTruthy()
  expect(root.querySelector('.r.price')?.textContent).toMatch(/价格带/)
})
```

- [ ] **Step 2: Template tweak**

```vue
<div
  v-for="(line, lineIndex) in item.lines || []"
  :key="lineIndex"
  class="r"
  :class="{ price: line.startsWith('价格带') }"
>
  {{ line }}
</div>
<div v-if="item.tags?.length" class="dims">
  <span v-for="(tag, tagIndex) in item.tags" :key="tagIndex" class="dim-pill">{{ tag }}</span>
</div>
```

- [ ] **Step 3: CSS** — strengthen `.n`, `.priority-tag`, `.r.price { color: var(--ink); font-weight: 500 }`, `.dim-pill { border: 1px solid var(--line); border-radius: 999px; padding: 2px 8px; background: var(--chip, var(--line-2)); }`

- [ ] **Step 4: Run ComputerRenderer + workspace tests**

```bash
cd forma-web && npm test -- --run src/components/business/computer src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts && npm run lint
```

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(web): polish Computer list hierarchy and dim pills"
```

---

### Task 4: Spec status + light verify

**Files:**
- Modify: design spec status → `implemented`

- [ ] **Step 1: Mark spec implemented**

- [ ] **Step 2: Run focused FE suite once more**

```bash
cd forma-web && npm test -- --run src/composables/agent/agentProgress.test.ts src/components/business/computer src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
```

- [ ] **Step 3: Commit**

```bash
git commit -m "docs: mark manus chat steps computer polish implemented"
```

---

## Spec coverage self-check

| Spec | Task |
|------|------|
| tool_* → chat-steps | 1–2 |
| message_delta fold 120 | 1–2 |
| No scene-specific step copy | 2 (tests + helpers) |
| Computer pills / hierarchy | 3 |
| Result sentence outside fold | 2 |

## Placeholder scan

No TBD; threshold and code paths specified.
