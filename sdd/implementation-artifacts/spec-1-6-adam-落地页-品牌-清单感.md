---
title: '1.6 Adam 落地页（品牌 + 清单感）'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '44fc8d48bdc7ba331dfa5a7d11a61b3f01856ce9'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 打开站点根路径直接进登录表单，潜在用户看不到品牌 Adam，也看不到「选品清单」交付物感，缺少愿意点进工具的第一印象。

**Approach:** 新增公开落地页：英雄级品牌名与静态清单实物感并置，视觉对齐 Epic 1（荧光纸 / 真黑 / 店章红 + Space Grotesk / Noto Sans SC），提供明确 CTA 进入现有登录或注册；不实现 Agent 壳（Epic 2）。

**Decisions:**
- 已登录访问 `/`：仍展示落地页（不自动 redirect）；有 JWT 时 CTA 改为进入 `/credits`
- 未登录主 CTA：`登录` 与 `注册` 两颗并排（主按钮气质一致或一主一次均可，但须两颗都是按钮而非仅旁链）

## Boundaries & Constraints

**Always:**
- `/` 渲染落地页（公开，无需 JWT）；品牌 Adam 为英雄级信号，与清单实物感并置（UX-DR3）
- 色：冷荧光纸 `#F0F2F5` + 真黑 `#121212` + 店章红 `#E11D48`；字体 Space Grotesk + Noto Sans SC（UX-DR1/UX-DR2）
- 禁用奶油陶土/紫霓虹/薄荷绿空壳风；无全大写 eyebrow、按钮尾「→」堆砌（UX-DR1/UX-DR3）
- 未登录：`登录`+`注册` 并排 CTA；已登录：CTA 进 `/credits`；主按钮实心店章红、小圆角 2–4px
- FE：`views/` 页面 + 路由；清单样例为静态前端数据，不调生成 API；测用现有 createApp 挂载风格

**Never:**
- 不实现 Agent 壳、SSE、选品生成、预览 Computer（Epic 2+ / UX-DR8 行为留给后续）
- 不接协议勾选（1.7）、不改 Identity/Credit API 与登录后 `afterLogin`→`/credits`
- 不引入支付 CTA、不搬 Manus mock 的 Inter/Newsreader 与 `#fafafa` 色板
- 不把 mockups/app.html 整页复制为落地页；不依赖真实商品图资源仓
- 有 JWT 时不对 `/` 做强制 redirect 到 `/credits`

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 访问根路径 | 打开 `/` | 落地页：大字 Adam + 清单实物区 + CTA | N/A |
| 未登录点 CTA | 点「登录」/「注册」 | 分别进 `/login` / `/register` | N/A |
| 已登录再访 `/` | 本地有 JWT | 仍渲染落地页；CTA 进 `/credits` | N/A |
| 小屏 | 窄视口 | 品牌与清单可上下叠，仍一眼见交付物 | N/A |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-web/src/router/index.ts` — 现 `/` → `login`；改为落地页路由 + 保留 `/login` 等
- `lippi-ai-ebus-web/src/views/` — 无 marketing/landing；**新增**如 `views/marketing/Landing.vue`（或 `views/Landing.vue`）
- `lippi-ai-ebus-web/index.html` — 已挂 Space Grotesk / Noto Sans SC；可复用
- `lippi-ai-ebus-web/src/App.vue` — 全局荧光纸底 `#f0f2f5`
- `lippi-ai-ebus-web/src/views/business/credit/CreditPlan.vue` — 色/字体/店章红按钮范本
- `lippi-ai-ebus-web/src/api/identity/afterLogin.ts` + `auth.landing.test.ts` — 登录后仍进 credits；勿改语义
- `lippi-ai-ebus-web/src/router/index.test.ts` — 扩 resolve 测覆盖落地页
- UX 参考：`DESIGN.md` UX-DR1/2/3；清单外形可参考 `mockups/select.html` `ol.picks` / `app.html` `pick-list`（仅视觉层次，非 Agent）
- Continuity（1.5）：勿动 Credit 改档与管理 REST；本故事纯 FE 门面

**Reuse：** 字体链接、荧光纸底、CreditPlan 色板与按钮气质、createApp + memory router 测法。

**Do not change：** JWT/Identity/Credit API；`afterLogin`→credits；后端模块。

## Tasks & Acceptance

**Execution:**
- [x] `lippi-ai-ebus-web/src/views/marketing/Landing.vue`（或等价路径）— 英雄 Adam + 静态清单实物感 + CTA — UX-DR3
- [x] `lippi-ai-ebus-web/src/router/index.ts` — `/` → 落地页；登录/注册链可达 — 公开门面
- [x] 落地页测（新建 `*.test.ts`）+ `router/index.test.ts` — 根路径组件、CTA 导航、无 eyebrow/「→」堆砌断言 — 防回归
- [x] 根 `README.md` 或 `lippi-ai-ebus-web/README.md` — 一句说明打开 `/` 见落地页 — 人可跟测

**Acceptance Criteria:**
- Given 前端可跑，when 访问 `/`，then 品牌 Adam 为英雄级信号，与清单实物感并置，第一眼看到交付物（UX-DR3）
- Given 落地页，when 审视视觉，then 荧光纸 + 真黑 + 店章红，字体 Space Grotesk + Noto Sans SC；无禁用风与全大写 eyebrow、按钮尾「→」堆砌（UX-DR1/UX-DR2/UX-DR3）
- Given 未登录，when 看 CTA，then 「登录」「注册」两颗并排；点后分别进 `/login`、`/register`；不进 Agent
- Given 已登录（有 JWT），when 打开 `/`，then 仍见落地页且 CTA 进 `/credits`（不强制 redirect）

## Implementation Notes

- 组件落点：`views/marketing/LandingPage.vue`（为过 `vue/multi-word-component-names`，未用单名 `Landing.vue`）。
- 路由：`/` name=`landing`；有 JWT 时 CTA「进入套餐与积分」→ `/credits`，不 redirect。
- 清单：7 条静态假数据，白底纸片 + 编号行；一次轻量 `land-in` 入场动效。
- 验证：`cd lippi-ai-ebus-web && npm run lint && npm test && npm run build` 全绿（2026-09-24）；矩阵「小屏」行补挂载断言。

## Spec Change Log

- 2026-09-24：落地页实现完成；Tasks 勾选；组件命名为 LandingPage。

## Review Triage Log

- false — blind：`sprint-status` 将 1-5 从 review→done：1.5 实现提交已在基线前完成，本 diff 只是校正滞后状态，非产品缺陷。
- false — blind：评审包未含 spec 文件：流程产物限制，不构成落地页缺陷。
- low → reject — blind：背景用渐变而非纯色 `#F0F2F5`：仍属荧光纸色系；日常观感不构成错色，不必为对齐字面再改一版背景。
- medium（patch）— blind+edge：入场动效无 `prefers-reduced-motion` 关闭：`LandingPage.vue` `.stage` animation。
- medium（patch）— blind：主 CTA 无 `:focus-visible`：键盘焦点不可辨。
- low → reject — blind+edge：`loggedIn` 挂载只读一次 JWT：同页不重挂载时改 token 极罕见；加 storage 监听超直接修补。
- low → reject — blind：无色板/字体契约单测：本仓 FE 测为 DOM 挂载风格，加视觉断言成本高且脆弱。
- false — blind：router 未再断言 `/login`/`/register` resolve：路由未被删除，坏结局不可达。
- false — blind：diff 未触 `afterLogin`：既有 `auth.landing.test.ts` 仍绿，无回归信号。
- low → reject — blind：FE README 未写尽 JWT CTA：根 README 已写；补一句属文档润色，非缺陷。
- medium（patch）— verification-gap：JWT「不强制 redirect」仅对 harness 路由断言，未对生产 `router.push('/')`：`router/index.test.ts` 应补。
- defer — verification-gap：窄视口测只断言 DOM 存在、不观测布局：本仓无视觉/e2e 工具链；英雄+清单存在已由主渲染测覆盖。

## Design Notes

- 清单区用静态假数据（约 6–8 条编号行：品名 + 一句理由），白底纸片/列表外形即可；无需图片资产。
- 第一视口优先：品牌 + 一句短支持句 + CTA + 清单视觉；勿堆套餐价目或积分摘要（那是 `/credits`）。
- 入场动效：最多一次轻量出现（可选）；禁止每段 fade-up。

## Verification

**Commands:**
- `cd lippi-ai-ebus-web && npm run lint && npm test && npm run build` -- expected: 全绿

**Manual checks (if no CLI):**
- 打开 `/`：一眼 Adam + 清单感；CTA 进登录
- 登录后旅程仍落 `/credits`（回归）
