---
title: '4.2 账户页个人资料分区 UI'
type: 'feature'
created: '2026-09-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'bd0169f0ced2a6559242020f0ec92b24beead255'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-4-context.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 顶栏头像已指向 `/me`，但页面仍是调试用「当前用户」清单，没有 Manus 式账户壳与可改显示名的个人资料区；用户无法按设置心智管理资料。

**Approach:** 将 `/me` 改造成账户页：桌面左栏 / 窄屏顶签三分区（个人资料 / 使用情况 / 安全）；默认个人资料；对接既有 `GET|PATCH /api/v1/account/profile`，邮箱只读、显示名（字段 `username`）可编辑保存；用量/安全区仅占位，真能力留给 4.3/4.4。

**Decisions:**
- 退出登录：**A** — 在「安全」占位区保留现有 `clearToken`+跳登录；4.4 再换成完整安全区

## Boundaries & Constraints

**Always:**
- 入口：登录态顶栏头像 → 账户页；默认个人资料（UX-DR9）
- 桌面左栏三分区 + 右内容；窄屏改为顶部分页签且可键盘切换（UX-DR10/11）
- 顶栏用既有 `AppHeader`，契约与全站一致（UX-DR1）
- 资料读写只用 `getAccountProfile` / `updateAccountProfile`；UI 文案「显示名称」绑定字段 `username`（延续 4.1，无 `displayName`）
- 邮箱只读展示；用户可见文案无内部黑话（UX-DR12）
- 未登录本地门禁（`getToken`）与 401 清 Token，对齐 `CreditPlan` 模式
- 「安全」占位区保留退出登录（`clearToken` + 跳登录），供 4.4 前使用

**Never:**
- 不实现用量流水真数据 / 改密 / 删除账户真能力（4.3/4.4）
- 不新增后端 API、不改 `/api/v1/account/profile` 契约、不加 `displayName`
- 不在账户页支付；套餐仅跳转既有 `/credits`
- 不假装「更换头像 / 更换邮箱 / 删除账户」已可用（若展示则 disabled / 即将开放）
- 不写积分、不直连大模型

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 进入账户 | 已登录点头像 | 到 `/me`；默认个人资料；可见三分区导航 | 无 Token → 提示未登录并可去登录 |
| 读资料 | 有 JWT | 展示邮箱（只读）与当前显示名（username） | API 失败 → 人话错误；401 → 清 Token |
| 保存显示名 | 合法未占用新名 | PATCH 成功；页面与后续 GET 显示新名 | 校验/冲突错误展示 `ApiError.message`，不静默 |
| 切换分区 | 点使用情况/安全 | 切到对应占位内容；个人资料不丢未保存草稿可接受（简单实现即可） | N/A |
| 窄屏导航 | 视口 ≤ 约定断点 | 分区导航呈顶签样式，仍可键盘切换 | N/A |

</frozen-after-approval>

## Code Map

- `forma-web/src/views/identity/AuthMe.vue` — 现调试页；本故事改造成账户壳+资料区（可重命名为 `AccountSettings.vue` 并改 router import）
- `forma-web/src/router/index.ts` — 保持 `path: '/me'` / `name: 'me'`，组件指向新账户页
- `forma-web/src/components/common/AppHeader.vue` — 头像已链 `name: 'me'`；一般无需改，测例已覆盖
- `forma-web/src/api/identity/account.ts` + `types/identity/account.ts` — 4.1 已交付；页面直接复用，勿改契约
- `forma-web/src/views/business/credit/CreditPlan.vue` — shell + `getToken`/`ApiError`/401 范本
- `forma-web/src/views/identity/AuthLogin.vue` — 表单 submit / loading / error 范本
- `sdd/.../mockups/profile.html` — 布局与文案参照（左栏/顶签、显示名称、disabled 头像）
- `forma-web/src/views/identity/` — 新增页面测（组件测或 view 测）；更新依赖 `/me` 文案的测例若有

**Reuse：** `AppHeader`、`account` api/types、`ApiError`、`getToken`/`clearToken`、套餐路由 `credits`。

**Do not change：** 后端 Account/Identity；`getMe`/`/api/v1/me` 登录自检路径；CreditLedger；用量/改密 API（尚无）。

## Tasks & Acceptance

**Execution:**
- [x] `views/identity` 账户页 — 替换 `AuthMe`：`AppHeader` + 三分区壳 + 默认个人资料；窄屏顶签 + 键盘可切换 — UX-DR9/10/11
- [x] 个人资料区 — 加载 `getAccountProfile`；邮箱只读；显示名称编辑/保存走 `updateAccountProfile({ username })`；禁用「更换头像/更换邮箱」；可选「查看套餐」链 `credits` — 对接 4.1
- [x] 使用情况 / 安全占位 — 短说明「即将完善」类人话，无假流水/假改密成功；安全区保留退出登录 — 给 4.3/4.4 留位
- [x] `router/index.ts` — 组件指向新页，路由名/path 保持 `me`/`/me` — 头像入口不断
- [x] 页面测 — 覆盖矩阵：默认分区、保存显示名、错误展示、窄屏/键盘导航至少一项 — 防回归

**Acceptance Criteria:**
- Given 已登录，when 点击顶栏头像，then 进入账户页且默认个人资料分区
- Given 桌面视口，when 打开账户页，then 左栏为个人资料/使用情况/安全，右侧为当前分区；顶栏与全站一致
- Given 窄屏，when 打开账户页，then 分区导航为顶签（或等价）且可键盘切换
- Given 个人资料区，when 展示与保存显示名，then 邮箱只读、显示名可保存成功，文案无内部黑话

## Implementation Notes

- 落地：`AccountSettings.vue` 替换 `AuthMe.vue`；路由仍 `/me`；`AccountSettings.test.ts` 覆盖矩阵（默认分区、保存、错误、401、键盘切分区、退出）。
- 验证：`npm test -- --run src/views/identity src/components/common/AppHeader.test.ts src/api/identity src/router/index.test.ts` → 43 passed；`npm run lint` 通过。
- 窄屏顶签样式靠 `@media (max-width: 800px)`；自动化覆盖键盘切换，未做浏览器实机缩窄目检。

## Spec Change Log

## Review Triage Log

- false — Blind：diff 路径缺 `forma-web/` 前缀会把页面落到包外：工作树文件在 `forma-web/src/views/identity/AccountSettings.vue`；是 `git diff --no-index` 进临时 diff 的路径表象，非仓内错位。
- false — Blind：规格文件未随变更：untracked 的 `spec-4-2-*.md` 在工作区；非代码缺陷。
- low → reject — Blind：缺 `aria-controls`/panel id：键盘切换已实现；完整 APG 对日常用户影响小，补全非本轮必须。
- low → reject — Blind：loading/needsLogin 时 tab 仍可点：主区仍显示门禁/加载，切换无实质错误态。
- medium — Blind：保存成功后继续改草稿未清 `saveOk`，成功提示与脏状态并存。
- low → reject — Blind：`.settings-card.danger` 边框色弱：纯视觉，不影响退出/删除语义。
- false — Blind：缺窄屏 CSS 断言：任务要求「窄屏/键盘至少一项」，键盘测已覆盖；样式靠 media。
- medium — Verification-gap：非 401 资料加载失败 UI（人话+重试）无测。
- medium — Verification-gap：空显示名客户端守卫无测。
- medium — Verification-gap：「去登录」未断言导航到 `login`。
- low — Verification-gap：邮箱只读无观测断言（仅文本匹配）。
- false — Blind：资料区未展示实时套餐额度：规格仅要求可选跳转套餐，真用量属 4.3。
- low → reject — Blind：头像取 `charAt(0)` 与 mock「陈」不一致：展示偏好，非功能缺陷。
- false — Blind：sprint 仍 in-progress：流程态，完成评审后会推进。
- medium — Edge：PATCH 401 时只设 `needsLogin` 未写 `loadError`，门禁 alert 可能空白。
- false — Edge：`username` null 导致 `.trim()` 抛错：`AccountProfile.username` 为 `string`，正常 API 不达。

## Design Notes

- 路由刻意保持 `/me`：头像与多处 `name: 'me'` 已对齐，避免无谓迁移。
- 「显示名称」仅为 UI 标签；请求/响应字段始终是 `username`。
- 用量/安全占位不要抄 mock 假数据，以免用户以为已可用。

## Verification

**Commands:**
- `cd forma-web && npm test -- --run src/views/identity src/components/common/AppHeader.test.ts src/api/identity` — 相关测绿
- `cd forma-web && npm run lint` — 无新增 lint 错

**Manual checks (if no CLI):**
- 登录后点头像 → 默认个人资料；改名保存后刷新仍在；切使用情况/安全见占位；缩窄窗口见顶签
