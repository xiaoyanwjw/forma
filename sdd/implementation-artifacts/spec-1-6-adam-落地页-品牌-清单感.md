---
title: '1.6 Adam 落地页（Manus 式居中提问）'
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

**Problem:** 打开站点根路径若是登录表单或「品牌+清单广告牌」，都缺少像 Manus 那样「立刻能开干」的工具第一印象。

**Approach:** `/` 做成 Manus 式居中提问空状态（主标题「我能为你做什么？」+ 快捷胶囊 + 大输入外观）；结构对齐 `mockups/app.html` home，色与字体仍用 Adam（荧光纸 / 真黑 / 店章红 + Space Grotesk / Noto Sans SC）。第一版只要居中提问区；不实现真对话 / Computer / SSE（Epic 2）。

**Decisions:**
- 已登录访问 `/`：仍展示该页（不自动 redirect）
- 未登录：输入框只读外观；点输入区或任一快捷胶囊 → `/login`；角落可留「注册」文字链
- 已登录：点输入区或胶囊 → `/credits`（Agent 未就绪前的过渡）
- 主标题文案固定：「我能为你做什么？」

## Boundaries & Constraints

**Always:**
- `/` 公开渲染居中提问空状态（无需 JWT）
- 色：冷荧光纸 `#F0F2F5` + 真黑 `#121212` + 店章红 `#E11D48`；字体 Space Grotesk + Noto Sans SC（UX-DR1/UX-DR2）
- 结构学 Manus（居中标题、胶囊、大圆角输入外观）；不搬 Inter / Newsreader / `#fafafa` 色板
- 禁用奶油陶土/紫霓虹/薄荷绿空壳风；无全大写 eyebrow、按钮尾「→」堆砌
- FE：`views/marketing/LandingPage.vue` + 路由；测用现有 createApp 挂载风格
- 有 JWT 时不对 `/` 做强制 redirect 到 `/credits`

**Never:**
- 不实现真 Agent 对话、SSE、选品生成、右侧 Computer / 会话侧栏（Epic 2）
- 不接协议勾选（1.7）、不改 Identity/Credit API 与登录后 `afterLogin`→`/credits`
- 不引入支付 CTA；不把 `mockups/app.html` 整页（含 session 三栏）复制进生产
- 未登录不允许在输入框内真编辑/发任务；第一版不放清单纸片并置英雄区

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 访问根路径 | 打开 `/` | 居中：「我能为你做什么？」+ 胶囊 + 输入外观 | N/A |
| 未登录点输入/胶囊 | 点 prompt 或 pill | 进 `/login` | N/A |
| 未登录点注册链 | 点角落「注册」 | 进 `/register` | N/A |
| 已登录再访 `/` | 本地有 JWT | 仍渲染该页；点输入/胶囊 → `/credits` | N/A |
| 小屏 | 窄视口 | 标题+胶囊+输入仍居中可读 | N/A |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-web/src/router/index.ts` — `/` → `landing`（LandingPage）
- `lippi-ai-ebus-web/src/views/marketing/LandingPage.vue` — **Rework**：Manus 式居中提问壳
- `lippi-ai-ebus-web/src/views/marketing/LandingPage.test.ts` — 重写断言（标题、胶囊、click→login/credits）
- `lippi-ai-ebus-web/src/router/index.test.ts` — 根路径仍 landing；JWT 不 redirect
- UX 参考：`mockups/app.html` home + `styles.css`（仅结构）；色板仍 `DESIGN.md` Adam
- Continuity：勿动 Credit / Identity API；`afterLogin`→credits

**Reuse：** 字体链接、荧光纸底、createApp + memory router 测法、路由名 `landing`。

**Do not change：** JWT/Identity/Credit API；`afterLogin`→credits；后端模块。

## Tasks & Acceptance

**Execution:**
- [x] `LandingPage.vue` — 居中标题「我能为你做什么？」+ 胶囊 + 只读输入外观；guest→login / JWT→credits
- [x] `LandingPage.test.ts` — 标题、胶囊、点击导航、无 eyebrow/「→」；去掉清单纸片断言
- [x] 根 `README.md` / `lippi-ai-ebus-web/README.md` — 一句改为 Manus 式居中提问门面
- [x] `sprint-status`：1-6 → `review`

**Acceptance Criteria:**
- Given 打开 `/`，when 看第一视口，then 居中见「我能为你做什么？」+ 快捷胶囊 + 大输入外观（无清单纸片并置）
- Given 未登录，when 点输入区或胶囊，then 进 `/login`；输入不可真编辑发任务
- Given 已登录，when 打开 `/`，then 仍见该页；点输入/胶囊进 `/credits`（不强制 redirect）
- Given 审视视觉，then Adam 色板与字体；结构像 Manus home，但非 Inter/Newsreader/`#fafafa`

## Implementation Notes

- 前一版（品牌 + 清单纸片）经 walkthrough Rework；人选择方案 2（Manus 居中提问）+ 未登录一点就登录 + 第一版无 Computer。
- 组件路径仍 `LandingPage.vue`；路由名仍 `landing`。
- Rework 实现：顶栏仅 logo + 登录/注册（或套餐链）；主区标题+胶囊+整块 button 伪装输入；无 contenteditable。

## Spec Change Log

- 2026-09-24：落地页实现完成；Tasks 勾选；组件命名为 LandingPage。
- 2026-09-24：walkthrough Rework — Intent 改为 Manus 式居中提问；去掉品牌+清单并置验收。
- 2026-09-24：Rework 实现完成；Tasks 再勾选；人确认页面 OK；sprint → done。

## Review Triage Log

- （前一版评审记录保留；Rework 实现后再跑新一轮）
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

- 第一视口：标题 + 胶囊（选品清单 / 生成素材）+ 输入外观；勿堆套餐价目。
- 胶囊与 prompt 点击同源：guest→login，JWT→credits。
- 入场动效：最多一次轻量；须尊重 `prefers-reduced-motion`。

## Verification

**Commands:**
- `cd lippi-ai-ebus-web && npm run lint && npm test && npm run build` -- expected: 全绿

**Manual checks (if no CLI):**
- 打开 `/`：居中「我能为你做什么？」+ 输入壳；点一下进登录
- 登录后旅程仍落 `/credits`（回归）；再开 `/` 不强制踢走
