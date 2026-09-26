# 电商工作台默认进会话壳 + 精简顶栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/scenes/ecommerce` 一打开就是会话壳（空白对话）；去掉空态；「新任务」清空后仍留会话壳；工作台顶栏隐藏「历史」「套餐」。

**Architecture:** 删除 `EcommerceWorkspacePlaceholder` 的 empty/session 双态，挂载即渲染现有 session 壳。`AppHeader` 增加 `hideSecondaryNav` prop，仅工作台传入。画廊路由目标不变。

**Tech Stack:** Vue 3、Vue Router、Vitest、现有 `AppHeader` / 会话 CSS / demo fixtures

**Design:** `docs/superpowers/specs/2026-09-26-ecommerce-session-default-entry-design.md`

## Global Constraints

- 不接 `streamEmptyRun` / agent / generation / 计费 API（零生成请求）
- 不改 `SceneGallery` / `SceneCard` 的 `to`（仍 `name: 'scene-ecommerce'`）
- 不删右侧积分 chip /「升级」/ 账户；不实现真历史（3.8）
- 不新增子路由
- 提交仅在用户明确要求时执行（下列 Commit 步骤可跳过）
- 验证：`cd lippi-ai-ebus-web && npm test -- AppHeader EcommerceWorkspace` 与 `npm run lint`

---

## File Structure

| 文件 | 职责 |
|------|------|
| `lippi-ai-ebus-web/src/components/common/AppHeader.vue` | 新增 `hideSecondaryNav`；为 true 时不渲染「历史」「套餐」 |
| `lippi-ai-ebus-web/src/components/common/AppHeader.test.ts` | 覆盖 hide / 默认仍显示 |
| `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue` | 删空态；默认会话壳；`newTask` 只清空；传 `hideSecondaryNav` |
| `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts` | 改写为空线程进页 + 新任务留会话 + 顶栏无历史套餐 |
| `docs/superpowers/specs/2026-09-26-ecommerce-session-default-entry-design.md` | 已存在的设计依据（本计划不改） |

---

### Task 1: AppHeader `hideSecondaryNav`

**Files:**
- Modify: `lippi-ai-ebus-web/src/components/common/AppHeader.vue`
- Test: `lippi-ai-ebus-web/src/components/common/AppHeader.test.ts`

**Interfaces:**
- Consumes: 现有 `sceneBreadcrumb` / `activeNav`
- Produces: `hideSecondaryNav?: boolean`（default `false`）

- [ ] **Step 1: Write the failing test**

在 `AppHeader.test.ts` 的 `describe('AppHeader')` 内追加：

```ts
  it('hideSecondaryNav hides history and plans but keeps breadcrumb and upgrade', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountHeader({
      sceneBreadcrumb: '电商开店',
      hideSecondaryNav: true,
    })
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('[aria-label="面包屑"]')?.textContent).toMatch(/电商开店/)
    expect(mounted.root.querySelector('[data-nav="history"]')).toBeNull()
    expect(
      Array.from(mounted.root.querySelectorAll('a.nav-link')).some((a) =>
        a.textContent?.includes('套餐'),
      ),
    ).toBe(false)
    expect(mounted.root.textContent).toMatch(/升级/)
  })
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd lippi-ai-ebus-web && npm test -- AppHeader.test.ts -t "hideSecondaryNav"
```

Expected: FAIL（prop 无效或「历史」「套餐」仍在）

- [ ] **Step 3: Minimal implementation**

在 `AppHeader.vue` props 中增加：

```ts
    /** When true, hide 历史 / 套餐 from primary nav (workbench) */
    hideSecondaryNav?: boolean
```

defaults: `hideSecondaryNav: false`

模板中用 `v-if="!props.hideSecondaryNav"` 包住「历史」`<span>` 与「套餐」`<RouterLink>`（约 L98–115）。

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
cd lippi-ai-ebus-web && npm test -- AppHeader.test.ts
```

Expected: PASS（含原有用例 + 新用例）

- [ ] **Step 5: Commit（仅当用户要求）**

```bash
git add lippi-ai-ebus-web/src/components/common/AppHeader.vue \
  lippi-ai-ebus-web/src/components/common/AppHeader.test.ts
git commit -m "$(cat <<'EOF'
feat(web): hide secondary nav on workbench header

EOF
)"
```

---

### Task 2: 工作台单测改写（先红）

**Files:**
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `hideSecondaryNav`（断言工作台挂载后顶栏无历史/套餐）
- Produces: 锁定「默认会话空线程」「新任务留壳」行为

- [ ] **Step 1: Replace empty-state describe with default session shell**

删除整个 `describe('EcommerceWorkspacePlaceholder empty state', …)`。

新增：

```ts
describe('EcommerceWorkspacePlaceholder default session shell', () => {
  let unmount: (() => void) | undefined
  let fetchMock: ReturnType<typeof mockCatalogAndCredits>

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    fetchMock = mockCatalogAndCredits()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    clearToken()
  })

  it('mounts session shell with empty thread, breadcrumb, no home, no secondary nav', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount

    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('h1')).toBeNull()
    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.sidebar')).toBeTruthy()
    expect(mounted.root.querySelector('.side-new')?.textContent).toContain('新任务')
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)

    const crumb = mounted.root.querySelector('.scene-switch')
    expect(crumb?.textContent).toContain('场景')
    expect(crumb?.textContent).toContain('电商开店')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')

    expect(mounted.root.querySelector('[data-nav="history"]')).toBeNull()
    expect(
      Array.from(mounted.root.querySelectorAll('a')).some((a) => a.textContent?.includes('套餐')),
    ).toBe(false)

    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('keeps attach disabled; session send enabled only with prompt text', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const send = mounted.root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    const attach = mounted.root.querySelector(
      '.session button[aria-label="附件"]',
    ) as HTMLButtonElement
    expect(attach.disabled).toBe(true)
    expect(send.disabled).toBe(true)

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    setTextareaValue(area, '帮我做家居选品')
    await flushUi()
    expect(send.disabled).toBe(false)
  })

  it('resolves scene bizId from catalog when available', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-biz-id')).toBe(
      ECOMMERCE.bizId,
    )
  })

  it('still shows session shell with sceneCode when catalog fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/scenes')) {
        return new Response(JSON.stringify({ success: false, code: 500, message: 'fail' }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/api/v1/credits')) {
        return new Response(
          JSON.stringify({
            success: true,
            code: 0,
            message: 'ok',
            data: { tier: 'FREE', balance: 20, available: 14, reserved: 0 },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        )
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-biz-id')).toBeNull()
  })
})
```

- [ ] **Step 2: Fix session-shell helpers and cases that still use `.home`**

在 `describe('… session shell (3.3)')` 中：

1. 将 `enterViaSend` 改为走会话输入框：

```ts
  async function enterViaSend(root: HTMLElement, text = '帮我做家居选品') {
    const area = root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    setTextareaValue(area, text)
    await flushUi()
    const send = root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    expect(send.disabled).toBe(false)
    send.click()
    await flushUi()
  }
```

2. 删除依赖空态进会话的用例：
   - `enters session from send: …`
   - `enters session from picks capsule …`
   - `enters session from listing capsule …`

3. 新增会话内胶囊「只填不发」：

```ts
  it('session picks capsule fills prompt without sending or generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length
    const pathBefore = mounted.router.currentRoute.value.fullPath

    const picks = mounted.root.querySelectorAll<HTMLButtonElement>('.chat-input-wrap .pill')[0]
    picks!.click()
    await flushUi()

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    expect(area.value).toMatch(/选品清单/)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('session listing capsule fills prompt without sending or generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length

    const listing = mounted.root.querySelectorAll<HTMLButtonElement>('.chat-input-wrap .pill')[1]
    listing!.click()
    await flushUi()

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    expect(area.value).toMatch(/上架素材/)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })
```

4. 将 `new task returns to empty state…` 替换为：

```ts
  it('new task clears thread and Computer but stays on session shell', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    ;(mounted.root.querySelector('[data-demo="open-picks"]') as HTMLButtonElement).click()
    await flushUi()
    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()

    ;(mounted.root.querySelector('.side-new') as HTMLButtonElement).click()
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
    expect(mounted.root.querySelector('[data-demo="open-picks"]')).toBeNull()
  })
```

5. 保留并依赖新 `enterViaSend`：`sendFromSession…`、`opens Computer with picklist…`、`opens Computer with listing…`、CSS 断点用例。

- [ ] **Step 3: Run tests to verify they fail for the right reasons**

Run:

```bash
cd lippi-ai-ebus-web && npm test -- EcommerceWorkspacePlaceholder.test.ts
```

Expected: FAIL — 仍渲染 `.home` / 新任务回空态 / 顶栏仍有历史套餐等（与尚未改的实现一致）

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
git commit -m "$(cat <<'EOF'
test(web): expect default ecommerce session shell

EOF
)"
```

---

### Task 3: 工作台实现（去空态 + 接线顶栏）

**Files:**
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue`

**Interfaces:**
- Consumes: `AppHeader` 的 `hideSecondaryNav: boolean`
- Produces: 恒定 session UI；`newTask()` 清空演示态

- [ ] **Step 1: Strip empty-state script API**

删除：

- `type ShellMode`、`mode` ref
- 空态 `prompt` ref（保留 `sessionPrompt`）
- `homeSendEnabled`
- `fillPicksEmpty` / `fillListingEmpty` / `sendFromEmpty`
- `enterSession` 若仅服务空态进会话：改为仅被 `sendFromSession` 使用的逻辑，或内联进 `sendFromSession`

保留：`messages`、`computerKind`、`sendFromSession`、`fillPicksSession`、`fillListingSession`、`openPicksComputer`、`openListingComputer`、`closeComputer`、`newTask`（见下）、fixtures、catalog `onMounted`。

`newTask` 最终形态：

```ts
function newTask() {
  messages.value = []
  computerKind.value = null
  sessionPrompt.value = ''
  sessionTitle.value = DEMO_SESSION_TITLE
}
```

（无 `mode.value = 'empty'`）

- [ ] **Step 2: Template — always session; hide secondary nav**

- `<AppHeader :scene-breadcrumb="SCENE_BREADCRUMB" :hide-secondary-nav="true" />`
- 删除整个 `v-if="mode === 'empty'" class="home"` 区块
- 去掉外层 `v-else`；直接渲染 `class="session"`（原会话模板）

- [ ] **Step 3: Remove unused empty-state scoped CSS**

删除 `.home`、`.home-main`、`.home-main h1` 等仅空态使用的规则；保留 `.pill` / `.prompt-box` / `.send-btn` 等会话输入仍用的样式。

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
cd lippi-ai-ebus-web && npm test -- EcommerceWorkspacePlaceholder.test.ts AppHeader.test.ts
```

Expected: PASS

- [ ] **Step 5: Lint**

Run:

```bash
cd lippi-ai-ebus-web && npm run lint
```

Expected: 无新增错误

- [ ] **Step 6: Commit（仅当用户要求）**

```bash
git add lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts \
  lippi-ai-ebus-web/src/components/common/AppHeader.vue \
  lippi-ai-ebus-web/src/components/common/AppHeader.test.ts \
  docs/superpowers/specs/2026-09-26-ecommerce-session-default-entry-design.md \
  docs/superpowers/plans/2026-09-26-ecommerce-session-default-entry.md
git commit -m "$(cat <<'EOF'
feat(web): open ecommerce workbench directly in session shell

EOF
)"
```

---

## Self-Review (plan vs spec)

| Spec 要求 | Task |
|-----------|------|
| 进入即会话壳、无空态 | Task 2 + 3 |
| 新任务清空仍留会话 | Task 2 + 3 |
| 隐藏历史/套餐 | Task 1 + 3 接线 |
| 零生成 API | Task 2 保留 generationApiHits |
| 画廊路由不变 | 无改 Gallery（刻意） |
| 胶囊只填不自动发 | Task 2 新用例 |
| 右侧升级保留 | Task 1 断言 |

无 TBD；prop 名统一 `hideSecondaryNav`。
