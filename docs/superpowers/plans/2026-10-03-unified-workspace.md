# Unified Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge ecommerce + xiaohongshu into one `Workspace.vue` driven by `SceneWorkspaceSpec`, with a single skill-run engine (including HITL) and data-driven Computer badges.

**Architecture:** Lift HITL into `useAgentSkillRun`; express scene differences only via Spec (`paneBySkillId`, `paneByArtifactType`, handoffs, `artifactTypes`); route `/scenes/:sceneCode` to one shell. Capsule toggle + optional `skillId` is largely already on both old pages—finish slots/`「…」` and delete intent leftovers while merging.

**Tech Stack:** Vue 3 / Vite / Vitest / existing `streamAgentRun` + `resumeGenerationRun` / Workspace* components / ComputerRenderer

**Specs:**  
- `docs/superpowers/specs/2026-10-03-unified-workspace-design.md`  
- `docs/superpowers/specs/2026-10-03-capsule-skill-selection-design.md`（发送子决策；多数 UI 已在旧页落地）

## Global Constraints

- Computer 槽命名用 **`pane*`**，禁止新代码用 `kindBy*` / `whenKind` 作为 Spec 字段名  
- Badge：**数据驱动**；删除 `priorityBadgeLabel` prop 与 Workspace 接线  
- 发送：有 `selectedSkillId` 才带 `skillId`；空选可发；无字符意图匹配  
- 槽位：高亮与发送拦截统一 **`「…」`**（`hasPromptSlots`）  
- 删除演示预览与 DEMO Computer 假数据路径  
- 不改 SSE 事件名 / 积分账本；handoff 文案模板可留在 Spec `buildText`  
- 测试命令在 `lippi-ai-ebus-web/`：`npm test -- --run <paths>`

---

## File map

| Path | Responsibility |
|------|----------------|
| `src/composables/agent/useAgentSkillRun.ts` | 唯一计费 SSE；HITL `pendingHuman` + `resumeSkillRun` |
| `src/composables/agent/useAgentListingRun.ts` | 迁移后删除（或极薄废弃 re-export，优先删除） |
| `src/composables/agent/useAgentPicklistRun.ts` | 迁移后删除；`CREDITS_CHANGED_EVENT` 挪到 `agentEvents.ts` 或 skill run 文件 |
| `src/views/business/scene/workspace/types.ts` | `SceneWorkspaceSpec` 类型 |
| `src/views/business/scene/ecommerce/spec.ts` | 电商 Spec + handoff `buildText` |
| `src/views/business/scene/xiaohongshu/spec.ts` | 小红书 Spec + handoff `buildText` |
| `src/views/business/scene/workspace/registry.ts` | `getSceneWorkspaceSpec(sceneCode)` |
| `src/views/business/scene/Workspace.vue` | 统一壳 |
| `src/views/business/scene/Workspace.test.ts` | 按 `sceneCode` 参数化的核心测（可先迁电商关键测） |
| `src/router/index.ts` | 两场景路由 → `Workspace.vue` |
| `ComputerRenderer.vue` | 去掉 `priorityBadgeLabel`；badge 原样展示 |
| 删除 | `EcommerceWorkspace.vue` / `XiaohongshuWorkspace.vue`（测迁完后） |

---

### Task 1: HITL → `useAgentSkillRun`

**Files:**
- Modify: `lippi-ai-ebus-web/src/composables/agent/useAgentSkillRun.ts`
- Create: `lippi-ai-ebus-web/src/composables/agent/useAgentSkillRun.test.ts`（若尚无）
- Modify: copy helpers from `useAgentListingRun.ts`（`toHumanInputRequired` / `parseAskHumanOptions` / `DEFAULT_ASK_OPTIONS`）

**Interfaces:**
- Consumes: `streamAgentRun`, `resumeGenerationRun`, `HumanInputRequiredPayload`
- Produces:
  - `pendingHuman: Ref<HumanInputRequiredPayload | null>`
  - `runId: Ref<string | null>`
  - `resumeSkillRun(options: { optionId?: string; freeText?: string }): Promise<void>`
  - existing `startSkillRun`（`skillId?` 已可选）

- [ ] **Step 1: Write failing tests for HITL**

```ts
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { useAgentSkillRun } from './useAgentSkillRun'

// mock streamAgentRun / resumeGenerationRun to yield human_input_required then settled

it('sets pendingHuman on human_input_required and clears on resume settle', async () => {
  // arrange mock SSE: run_started → human_input_required → (resume stream) run_settled
  const { startSkillRun, pendingHuman, resumeSkillRun, running } = useAgentSkillRun()
  await startSkillRun({
    text: '生成上架',
    skillId: 'ecommerce-skulist',
    sceneCode: 'ecommerce',
  })
  expect(pendingHuman.value?.toolCallId).toBeTruthy()
  await resumeSkillRun({ optionId: 'confirm_execute' })
  expect(pendingHuman.value).toBeNull()
  expect(running.value).toBe(false)
})
```

- [ ] **Step 2: Run test — expect FAIL**（缺 `pendingHuman` / `resumeSkillRun`）

```bash
cd lippi-ai-ebus-web && npm test -- --run src/composables/agent/useAgentSkillRun.test.ts
```

- [ ] **Step 3: Implement HITL in `useAgentSkillRun`**

Port from `useAgentListingRun`:
- track `runId` on `run_started`
- on `human_input_required` → `pendingHuman`
- on `run_failed` / `run_settled` → clear `pendingHuman`
- `resumeSkillRun` calls `resumeGenerationRun({ runId, toolCallId, optionId, freeText, signal })` and consumes SSE with the same `applyEvent` path
- keep `skillId` optional on `startSkillRun`

- [ ] **Step 4: Tests green**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/composables/agent/useAgentSkillRun.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-web/src/composables/agent/useAgentSkillRun.ts \
  lippi-ai-ebus-web/src/composables/agent/useAgentSkillRun.test.ts
git commit -m "$(cat <<'EOF'
feat(web): add HITL resume to useAgentSkillRun

Listing and any skill can pause on human_input_required without a dedicated run composable.
EOF
)"
```

---

### Task 2: Ecommerce listing/picklist → skill run only

**Files:**
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspace.vue`
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspace.test.ts`（HITL 断言仍绿；可改 mock 为通用 `/runs` + skillId）
- Later delete: `useAgentListingRun.ts`, `useAgentPicklistRun.ts`（本 Task 末若无引用则删；`CREDITS_CHANGED_EVENT` 先挪到 `useAgentSkillRun.ts` 或 `agentCredits.ts`）

**Interfaces:**
- Consumes: `startSkillRun`, `resumeSkillRun`, `pendingHuman` from Task 1
- Produces: ecommerce send/handoff/HITL UI 行为不变，但只挂一个 run composable

- [ ] **Step 1: Failing/adjust test** — listing HITL test still expects confirm/supplement; ensure it does not import listing-run-only APIs

- [ ] **Step 2: Replace dual runs in EcommerceWorkspace**

```ts
const {
  running: generationRunning,
  error: skillError,
  artifact: skillArtifact,
  sessionId,
  processEvents,
  pendingHuman: listingPendingHuman,
  startSkillRun,
  resumeSkillRun,
  reset: resetSkillRun,
} = useAgentSkillRun()

// picks: startSkillRun({ ..., skillId: 'ecommerce-picklist' })
// listing: startSkillRun({ ..., skillId: 'ecommerce-skulist' })
// free text: startSkillRun({ ..., skillId: undefined })
// resume: resumeSkillRun({ optionId, freeText })
```

Remove `useAgentPicklistRun` / `useAgentListingRun` imports. Keep HITL UI binding to `listingPendingHuman` (rename to `pendingHuman` when convenient).

- [ ] **Step 3: Run ecommerce HITL + picklist send tests**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/views/business/scene/EcommerceWorkspace.test.ts
```

- [ ] **Step 4: Delete unused listing/picklist composables** if grep shows no imports; move `CREDITS_CHANGED_EVENT` export to skill-run module and fix imports

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(web): drive ecommerce billed runs through useAgentSkillRun

Drop dedicated picklist/listing FE run engines; HITL uses shared resume.
EOF
)"
```

---

### Task 3: Badge data-driven（去掉 `priorityBadgeLabel`）

**Files:**
- Modify: `ComputerRenderer.vue`, `ComputerRenderer.test.ts`
- Modify: `XiaohongshuWorkspace.vue`（删 `priority-badge-label`）
- Modify: `XiaohongshuWorkspace.test.ts`（删断言 prop；改 fixture `badge: '优先发'`）
- Modify: ecommerce fixtures/tests that use `badge: 'priority'` → `badge: '优先试'`（或保留标题 `【优先试】` 剥前缀路径）

**Interfaces:**
- Produces: `itemBadge` 返回 `item.badge` trim 后原文；`priority` 语义 key **不再**映射；仍可剥标题前缀 `【优先试】`/`【优先发】`

- [ ] **Step 1: Update failing tests**

```ts
it('renders badge text from document data', async () => {
  // document item badge: '优先发'
  expect(host.querySelector('.priority-tag')?.textContent).toBe('优先发')
})

it('does not accept priorityBadgeLabel prop', async () => {
  // mount without prop; badge: '优先试' on item
  expect(host.querySelector('.priority-tag')?.textContent).toBe('优先试')
})
```

- [ ] **Step 2: Implement** — remove prop; simplify `itemBadge`

```ts
function itemBadge(item: { badge?: string; title: string }): string | undefined {
  const raw = item.badge?.trim()
  if (raw) return raw
  if (hasPriorityTitleMark(item.title)) {
    // derive label from which mark matched, e.g. 优先发 / 优先试
    return markLabelFromTitle(item.title)
  }
  return undefined
}
```

- [ ] **Step 3: Tests green**（computer + xhs workspace）

```bash
cd lippi-ai-ebus-web && npm test -- --run \
  src/components/business/computer/ComputerRenderer.test.ts \
  src/views/business/scene/XiaohongshuWorkspace.test.ts
```

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(web): show list badges from Computer data

Remove priorityBadgeLabel scene wiring; display protocol badge text as-is.
EOF
)"
```

---

### Task 4: `SceneWorkspaceSpec` + registry

**Files:**
- Create: `lippi-ai-ebus-web/src/views/business/scene/workspace/types.ts`
- Create: `lippi-ai-ebus-web/src/views/business/scene/ecommerce/spec.ts`
- Create: `lippi-ai-ebus-web/src/views/business/scene/xiaohongshu/spec.ts`
- Create: `lippi-ai-ebus-web/src/views/business/scene/workspace/registry.ts`
- Create: `lippi-ai-ebus-web/src/views/business/scene/workspace/registry.test.ts`
- Reuse handoff builders: `utils/listingHandoff.ts`, `utils/xhsNoteHandoff.ts`

**Interfaces:**

```ts
// types.ts
export type SceneWorkspaceSpec = {
  sceneCode: string
  breadcrumb: string
  artifactTypes: string[]
  paneBySkillId: Record<string, string>
  paneByArtifactType: Record<string, string>
  itemHandoffs?: Array<{
    whenPane: string
    actionLabel: string
    targetSkillId: string
    buildText: (item: ComputerListItem, index: number) => string | null
  }>
  toolbarHandoffs?: Array<{
    whenPane: string
    actionLabel: string
    targetSkillId: string
    buildText: () => string | null
  }>
}

export function getSceneWorkspaceSpec(sceneCode: string): SceneWorkspaceSpec | null
```

- [ ] **Step 1: Registry test**

```ts
it('resolves ecommerce and xiaohongshu specs with pane maps', () => {
  const e = getSceneWorkspaceSpec('ecommerce')
  expect(e?.paneBySkillId['ecommerce-picklist']).toBe('picks')
  expect(e?.paneByArtifactType.picklist).toBe('picks')
  const x = getSceneWorkspaceSpec('xiaohongshu')
  expect(x?.paneBySkillId['xhs-note']).toBe('note')
  expect(getSceneWorkspaceSpec('nope')).toBeNull()
})
```

- [ ] **Step 2: Implement specs**

Ecommerce `itemHandoffs`: `whenPane: 'picks'`, `actionLabel: '做上架素材'`, `targetSkillId: 'ecommerce-skulist'`, `buildText` → `listingHandoffTextForItem`.

XHS: topiclist → note；toolbar break → note（`buildXhsBreakNoteHandoffText` 需闭包读 live break view—**允许** `buildText` 由 Workspace 注入时再绑，或 toolbar handoff 在 Spec 里只给 meta，`buildText` 在 Workspace 注册。推荐：Spec 放纯函数能表达的 item handoff；toolbar break 的 `buildText` 在 `xiaohongshu/spec.ts` 导出工厂 `createXhsToolbarHandoffs(getBreakView, getLastPrompt)` 以免 Spec 变“死表”。保持 YAGNI：先 itemHandoffs 纯函数；toolbar 在 Workspace 用 spec 的 `whenPane`/`actionLabel`/`targetSkillId` + 本地 `buildText`。）

**Locked approach for toolbar:** Spec fields `whenPane` / `actionLabel` / `targetSkillId` only; `buildText` for break handoff stays a function exported beside spec:

```ts
// xiaohongshu/spec.ts
export const xhsSpec: SceneWorkspaceSpec = { ... itemHandoffs, toolbarHandoffs: undefined }
export function buildXhsBreakToolbarText(ctx: { view; lastPrompt }): string | null
```

Workspace wires toolbar using those constants + builder.

- [ ] **Step 3: Tests green + commit**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/views/business/scene/workspace/registry.test.ts
git commit -m "$(cat <<'EOF'
feat(web): add SceneWorkspaceSpec registry for ecommerce and xhs

Pane maps and item handoffs become data; no kind* Spec fields.
EOF
)"
```

---

### Task 5: Unified `Workspace.vue` + router

**Files:**
- Create: `lippi-ai-ebus-web/src/views/business/scene/Workspace.vue`
- Create: `lippi-ai-ebus-web/src/views/business/scene/Workspace.test.ts`（先迁 2–3 个冒烟：胶囊选中带 skillId、空选无 skillId、listing HITL 或 xhs topic send）
- Modify: `lippi-ai-ebus-web/src/router/index.ts`, `router/index.test.ts`
- Modify: delete demo buttons / DEMO view fallbacks（不要再 `return DEMO_*_VIEW`）

**Interfaces:**
- Consumes: `getSceneWorkspaceSpec(route.params.sceneCode)`, `useAgentSkillRun`, capsule toggle, `hasPromptSlots`
- Produces: single shell for both scenes

- [ ] **Step 1: Router points both names to Workspace**

```ts
{
  path: '/scenes/:sceneCode',
  name: 'scene-workspace',
  component: () => import('@/views/business/scene/Workspace.vue'),
},
// keep name aliases or redirect:
// /scenes/ecommerce and /scenes/xiaohongshu → same component with sceneCode param
```

Prefer **one** dynamic route; update gallery links if they use named routes. Update `router/index.test.ts` accordingly.

- [ ] **Step 2: Implement Workspace.vue**

Skeleton behavior:
1. `const spec = computed(() => getSceneWorkspaceSpec(sceneCode))` — null → 提示回画廊  
2. `selectedSkillId` toggle（同现页）  
3. `sendFromSession`: `hasPromptSlots(text)` 拦截；然后 `startSkillRun({ text, skillId: selectedSkillId ?? undefined, sceneCode })`；`activePane = paneBySkillId[skillId]` when present  
4. `liveByPane: Record<string, GenerationArtifactPayload | null>`  
5. HITL UI when `pendingHuman`  
6. Computer: `item-action-label` from matching `itemHandoffs` for `activePane`  
7. `newTask` clears `selectedSkillId`  
8. Session load: `Promise.all(spec.artifactTypes.map(t => getLatestSessionArtifact(sid, t)))`  
9. **No** demo preview buttons  

Slot message copy（统一）: `请先改完「」里的示例再发送，或点上方胶囊使用示例。`

- [ ] **Step 3: Smoke tests on Workspace**

```ts
it('posts skillId when capsule selected (ecommerce)', async () => { ... })
it('omits skillId when no capsule selected', async () => { ... })
it('xiaohongshu topic capsule posts xhs-topiclist', async () => { ... })
```

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(web): add unified Workspace routed by sceneCode

One shell reads SceneWorkspaceSpec; gallery scenes share skill-run + HITL.
EOF
)"
```

---

### Task 6: Migrate full tests + delete old pages

**Files:**
- Move/adapt: `EcommerceWorkspace.test.ts` → assert against `Workspace` + `sceneCode=ecommerce`（或 mount helper）
- Move/adapt: `XiaohongshuWorkspace.test.ts` similarly  
- Delete: `EcommerceWorkspace.vue`, `XiaohongshuWorkspace.vue`, demo fixtures only used by demo buttons  
- Grep-clean: `priorityBadgeLabel`, `useAgentListingRun`, `useAgentPicklistRun`, `detectXhsKind`, `isPicklistIntent`, `data-demo`, `【品类】` send guards  
- Optionally slim `workspaceKinds.ts` into spec modules and delete obsolete kind helpers used only for intent

- [ ] **Step 1: Port remaining ecommerce/xhs tests to Workspace mount helper**

```ts
async function mountWorkspace(sceneCode: 'ecommerce' | 'xiaohongshu') {
  // router push /scenes/${sceneCode} or pass prop if Workspace reads route
}
```

- [ ] **Step 2: Full related suite green**

```bash
cd lippi-ai-ebus-web && npm test -- --run \
  src/views/business/scene/ \
  src/composables/agent/useAgentSkillRun.test.ts \
  src/components/business/computer/ComputerRenderer.test.ts \
  src/router/index.test.ts
```

- [ ] **Step 3: Grep gate**

```bash
cd lippi-ai-ebus-web && rg -n "priorityBadgeLabel|useAgentListingRun|useAgentPicklistRun|detectXhsKind|isPicklistIntent|isListingIntent|EcommerceWorkspace|XiaohongshuWorkspace|data-demo=\"open-" src || true
```

Expected: no hits in `src/`（测试名字符串除外）

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(web): remove dual scene Workspace pages

Tests target unified Workspace; drop demo previews and intent helpers.
EOF
)"
```

---

### Task 7: Verify

- [ ] **Step 1: Run focused suite + type-check**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/views/business/scene/ src/composables/agent/useAgentSkillRun.test.ts src/components/business/computer/ComputerRenderer.test.ts src/router/index.test.ts
cd lippi-ai-ebus-web && npm run type-check
```

Expected: all green

- [ ] **Step 2: Manual smoke checklist**（本地 `npm run dev`）
  - 电商：选中选品胶囊发送 → Computer picks；手递上架 → HITL → 确认  
  - 小红书：选题 → 写成笔记；拆解顶栏手递  
  - 空选发送：网络请求无 `skillId`  
  - 无演示按钮  

---

## Spec coverage check

| Spec requirement | Task |
|------------------|------|
| HITL in skill run | 1–2 |
| Single Workspace + Spec `pane*` | 4–5 |
| Capsule selectedSkillId / optional skillId | 5（旧页已有，合并时保留） |
| Badge data-driven | 3 |
| `「…」` slots | 5–6 |
| Delete demo | 5–6 |
| Artifact types from Spec | 4–5 |
| Item/toolbar handoffs | 4–5 |
| Delete dual pages | 6 |

## Placeholder scan

None intentional; toolbar break `buildText` factory called out explicitly in Task 4.

---

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-10-03-unified-workspace.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with executing-plans checkpoints  

Which approach?
