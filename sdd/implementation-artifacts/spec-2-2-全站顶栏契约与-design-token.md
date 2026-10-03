---
title: '2.2 全站顶栏契约与 DESIGN token'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '2dedb4b05f8dc87243398cdcf21ce60c31358391'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/DESIGN.md'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/styles.css'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 各页没有统一应用顶栏与 DESIGN token：Vue 无共享壳、无 CSS 变量；Landing 自带旧营销顶栏与 Space Grotesk；套餐等页无顶栏。后续画廊/工作台会导航漂移、品牌感分裂。

**Approach:** 落地全局 DESIGN token（含字体与点阵底）+ 可复用 `AppHeader` 契约组件（全宽贴边；左 Logo+场景/面包屑+历史+套餐；右积分+升级+头像）；挂到本故事约定的主壳页；焦点环可见；顶栏文案无内部黑话。不含画廊卡、工作台空态、真实账户页实现。

**Decisions:**
- Token 以 UX `DESIGN.md` + mockup `styles.css` `:root` 为准（`--canvas/--surface/--ink/--mute/--line/--accent/--ok` 等、圆角间距、`--header-h: 56px`）
- 字体改为 Instrument Sans + Noto Sans SC（替换 Space Grotesk）
- 顶栏禁止整条 `max-width` 居中悬浮胶囊；内容区可另限宽
- 工作台面包屑模式（「场景 / 电商开店」）由 props 支持，本故事不必交付工作台页
- 头像入口位保留（链到现有 `/me` 或约定路径）；账户三分区 UI → Epic 4
- 「套餐」「升级」近端链到现有 `/credits`（套餐三档 UI 属 1-4，非本故事）
- 登录/注册页不加应用顶栏；Landing `/` 营销顶栏本故事不改（→ 2.3）
- 挂载：`/credits` + 新增场景/历史占位路由页专门验收顶栏（访客或登录均可验槽位）
- 导航：「场景」暂链 `/`；「历史」仅视觉占位（不可导航进假历史功能）；占位页可经路由直达以便验收顶栏

## Boundaries & Constraints

**Always:**
- UX-DR1 顶栏槽位与全宽贴边；UX-DR2 token/字体/点阵；UX-DR11 可交互焦点环；UX-DR12 顶栏可见文案无黑话
- 跨页复用组件放 `components/common/`（见 `03-fe`）
- 已登录时右侧积分尽量用现有 Credit 只读 API；失败给人话提示，不露技术栈

**Never:**
- 场景画廊大卡片 / 灰卡 toast / 工作台空态（→ 2.3～2.5）
- 历史列表页、账户三分区、套餐三档方案卡正文（→ 后故事）
- 改后端 SceneCatalog / CreditLedger / JWT
- 顶栏做成居中悬浮胶囊；紫渐变主题；用户可见「示意 / 近端 / 空壳」等黑话

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 契约布局 | 打开已挂顶栏的主壳页 | 全宽 sticky 顶栏；左 Logo+导航；右积分+升级+头像；无顶栏 max-width 胶囊 | N/A |
| Token 渲染 | 任意已接 token 页 | canvas 点阵底 + 指定字体族；颜色/圆角取自 CSS 变量 | N/A |
| 焦点 | Tab 到顶栏链接/按钮 | `:focus-visible` 环可见 | N/A |
| 积分 | 已登录且 Credit API 成功 | 右侧展示可读余额（产品文案） | API 失败：弱提示或不挡导航，不崩页 |
| 未登录（若该页允许） | 无 JWT | 不假装已登录积分；右区为登录/注册或约定访客态 | N/A |
| 面包屑模式 | header 传入场景面包屑 | 左侧「场景」位变为「场景 / 当前场景」且可回场景 | N/A |

</frozen-after-approval>

## Code Map

- `sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/DESIGN.md` — 色/字/圆角/顶栏 56px 契约
- `…/mockups/styles.css` — `:root` token 金样 + `.app-header` 全宽贴边（禁胶囊注释）
- `…/mockups/index.html` · `pricing.html` · `history.html` · `profile.html` — 同构顶栏 markup
- `…/mockups/scene-ecommerce.html` + `manus-chat.css` — 面包屑顶栏变体；`.pill:focus-visible` 焦点参考
- `forma-web/src/App.vue` — 仅 RouterView；全局底色 `#f0f2f5`（待换 token）
- `forma-web/index.html` — 现载 Space Grotesk + Noto（改 Instrument Sans）
- `forma-web/src/views/marketing/LandingPage.vue` — 唯一现有顶栏（旧营销）；scoped hex + 粉红焦点
- `forma-web/src/views/business/credit/CreditPlan.vue` — 无顶栏主壳页，宜首挂
- `forma-web/src/api/business/credit/credit.ts` · `types/business/credit.ts` — 积分只读
- `forma-web/src/router/index.ts` — 平铺路由，无 layout
- `sdd/context/03-fe.md` — `components/common/` 跨页组件配方
- **Reuse：** mockup `.app-header` 结构；Credit API；Landing 登录态判断思路（`getToken`）
- **Do not change：** SceneCatalog API、后端账本、画廊/工作台业务页、Auth 登录注册流程本身

## Tasks & Acceptance

**Execution:**
- [x] `forma-web/src/styles/tokens.css`（或邻名）+ 在 `main.ts`/`App.vue` 引入 — 落地 DESIGN `:root` 变量、点阵底、全局字体与基础 `:focus-visible` — UX-DR2/11
- [x] `forma-web/index.html` — Google Fonts 改为 Instrument Sans + Noto Sans SC — 字体契约
- [x] `forma-web/src/components/common/AppHeader.vue` — 全宽契约顶栏；支持默认导航 vs 面包屑 props；「场景」→`/`，「历史」不可点；右区积分/升级/头像 — UX-DR1
- [x] `router` + 占位页（场景/历史）+ `/credits` 挂载 `AppHeader`（layout 或页内）— 1C 验收面；登录注册/Landing 不加契约顶栏
- [x] 已登录积分芯片接 `credit` API；失败弱降级 — 顶栏积分位
- [x] 顶栏与占位页可见文案审一遍（无「示意/近端/空壳」等）— UX-DR12
- [x] 组件/挂载相关单测或现有测试更新（至少断言顶栏关键槽位/无 max-width 胶囊类名约定）— 防回归

**Acceptance Criteria:**
- Given 已挂顶栏的主壳页，when 查看顶栏，then 全宽贴边且槽位为左 Logo+场景(或面包屑)+历史+套餐、右积分+升级+头像；整条顶栏无 max-width 居中胶囊
- Given 全局样式已接入，when 渲染该页，then 使用 DESIGN token（含点阵底与 Instrument Sans + Noto Sans SC）
- Given 键盘用户，when 聚焦顶栏可交互控件，then 焦点环可见
- Given 顶栏文案，when 目视，then 无内部黑话

## Implementation Notes

- 2026-09-26：落地 `tokens.css` + `AppHeader`；挂载 `/credits`、`/scenes`、`/history`；「场景」→`/`，「历史」`aria-disabled` 不可点；Landing/登录注册未改。
- 验证：`npm run test` 46 绿；`npm run lint` 绿；`npm run build` 绿。
- 矩阵补测：token/`focus-visible` 规则断言 + 顶栏控件可键盘聚焦。
- 已登录时 AppHeader 与 CreditPlan 可能各打一次 `getCredits`（可接受；后续可合并）。
- Review patch：`onAuthChange` 驱动顶栏登录态；面包屑 trim；`aria-current`；壳页/空积分/token 运行时测补齐；Landing/AgentDryRun 改用 `var(--font)`。

## Spec Change Log

## Review Triage Log

- `medium` — `loggedIn` 在 setup 一次性读 token，同页 `clearToken`（如 CreditPlan 401）后右栏仍像已登录。verified：`AppHeader.vue:20` + `CreditPlan.vue:50`。→ patch
- `medium` — 三壳页未断言挂载 `AppHeader`；删掉模板挂载测仍绿。verified：verification-gap 举证。→ patch
- `medium` — `activeNav`/`.on` 高亮无断言。verified：`AppHeader.test.ts` 传 props 未查 class。→ patch
- `medium` — `getCredits()` 返回 null 的「积分暂不可用」无测。verified：仅 catch 路径有测。→ patch
- `medium` — token/`focus-visible` 仅读 CSS 文本，未证 `main.ts` 加载生效。verified：verification-gap。→ patch
- `medium` — `index.html` 去掉 Space Grotesk 后 Landing/AgentDryRun 仍写该族，静默回退。verified：Landing/AgentDryRun `font-family`。→ patch
- `low` — `sceneBreadcrumb` 纯空白仍进面包屑模式。verified：`v-if="props.sceneBreadcrumb"`。→ patch
- `low` — 当前页无 `aria-current`。verified：仅 CSS `.on`。→ patch
- `false` — `/scenes` 高亮却链到 `/`「不可达」。证据：冻结决策「场景」→`/`；占位页经路由直达验收，非顶栏入口。
- `false` — unified diff 缺 `forma-web/` 前缀会导致文件错位。证据：仓内文件路径正确；前缀是 diff 拼装产物。
- `low` — 测例标题写 API failure 却 stub 成功。rejected：仅标题误导，改标题收益低。
- `low` — CreditPlan `.error` 仍硬编码 `#e11d48`、无 danger token。rejected：DESIGN 未定义危险色；补 token 超出直修。

## Design Notes

顶栏槽位（与 EXPERIENCE / mockup 一致）：

```text
[Logo Adam] [场景|面包屑] [历史] [套餐]     ……     [积分芯片] [升级] [头像]
```

CSS 变量名优先对齐 mockup `styles.css`（`--ok` 对应 DESIGN `success`）。内容柱限宽（如画廊 960px）不得套在 `<header>` 上。

## Verification

**Commands:**
- `cd forma-web && npm run lint` -- expected: 绿
- `cd forma-web && npm run build` -- expected: 绿
- 相关单测（若新增）`npm run test` 或项目既有 test 命令 -- expected: 绿

**Manual checks (if no CLI):**
- 打开已挂顶栏页：对照 mockup 槽位；DevTools 确认 header 无居中 max-width；Tab 看焦点环；已登录看积分芯片
