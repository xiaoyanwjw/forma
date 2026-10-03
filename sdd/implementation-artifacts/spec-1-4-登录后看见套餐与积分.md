---
title: '1.4 登录后看见套餐与积分'
type: 'feature'
created: '2026-09-24'
status: 'review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '05ad923a35e82da328739e4a02cdfca1a907df9c'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 账本 `GET /api/v1/credits` 已可用，但登录后前端仍只显示身份，店主看不到当前套餐、剩余积分与下次重置，也无法对照三档价目或在积分不足时得到人话指引。

**Approach:** 登录后进独立套餐页消费 `GET /api/v1/credits`，展示档位、可用积分与下次重置（东八区），三档以套餐卡对照；`available===0` 时常驻人话提示可升级或等待重置。本故事只做读展示，不改账本写路径。

**Decisions:**
- 独立路由 `/credits`：当前摘要 + 三档套餐卡；`/me` 仍只身份，可互相导航
- 登录/注册成功后默认跳 `/credits`（不再落 `/me`）
- `available === 0` 时在 `/credits` 常驻不足人话（可升级或等待重置）；不接生成流程
- 价目区用三张并列卡（参考 Manus 定价卡：大价格、副标、主状态区、要点列表）；当前档可辨；无支付 CTA（1.5 前）
- 人 renegotiate：原 UX-DR5「价目表行、非孪生卡」改为三卡（2026-09-24 walkthrough）

## Boundaries & Constraints

**Always:**
- 积分数据只经 `GET /api/v1/credits`（JWT）；前端不写账本
- 展示：当前套餐档、本月剩余积分、下次重置时间（FR2）；时间存 UTC、界面东八区
- 套餐呈现为三张并列卡（Manus 式信息层次）；当前档边框/「当前套餐」可辨；不画假购买按钮
- 视觉贴近 Epic 1：冷荧光纸色 + 真黑 + 店章红；Space Grotesk / Noto Sans SC（能复用则复用）
- FE 配方按 `03-fe`：`api` 只 HTTP、类型只在 `types`、页面在 `views/business/credit/`
- 档位文案映射：`FREE`→免费、`PRO`→Pro、`PLUS`→Plus；额度 20/200/600；价目月费静态「¥0 / 待定 / 待定」（无定价 API）
- 路由名建议 `credits`，path `/credits`

**Never:**
- 不实现预占/结算/释放 UI，不直改积分表
- 不实现手工改档/支付（1.5/AD-9）
- 不实现 Agent 壳、生成 SSE、落地页品牌页（1.6/Epic 2）
- 不引入 Pinia；不改 Identity JWT/限流/后端 Credit 写服务
- 不把套餐并入 `/me`；不把登录默认落地改回 `/me`

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 登录后落地 | 登录/注册成功 | `afterLogin` 后 `push({ name: 'credits' })`；见档位/可用/下次重置 + 三张套餐卡 | N/A |
| 打开 `/credits` | 有效 JWT + 免费账本 | 当前档可辨；东八区可读重置时间 | N/A |
| 未登录访问 `/credits` | 无 token | 不调用 credits；引导去登录 | 不误报账本错误 |
| Token 失效 | 401 | 清 token；人话提示并回登录 | 用 `ApiError.message` |
| 积分不足 | `available === 0` | 页内常驻人话：可升级或等待下次重置 | 不伪造扣费 |
| 有预占 | `reserved > 0` | 剩余以 `available` 为准展示 | 不把 reserved 当主数字 |

</frozen-after-approval>

## Code Map

- `forma-interfaces/.../CreditController.java` — `GET /api/v1/credits`
- `forma-application/.../dto/CreditBalanceDTO.java` — `tier`/`available`/`balance`/`reserved`/`nextResetAt`/`periodAnchorAt`
- `forma-web/src/api/{http,client}.ts` + `api/identity/afterLogin.ts` — JWT 与统一 `request`
- `forma-web/src/views/identity/AuthMe.vue` + `AuthLogin.vue` / `AuthRegister.vue` — 现登录后 `push({ name: 'me' })`
- `forma-web/src/router/index.ts` — 现仅 login/register/me
- `forma-web/src/api/identity/auth.ts` + `types/identity/auth.ts` + `auth.flow.test.ts` — FE 分层范本
- UX：参考 Manus `manus.im/pricing` 三卡层次；`app.html`（侧栏「免费 · 14/20」与不足文案）；旧 `pricing.html` 三行稿已 superseded
- Continuity（1.3）：公开仅 GET；写路径勿动；`available = balance − reserved`

**Reuse：** HTTP 客户端、`ApiError`、JWT localStorage、Identity 页面风格。

**Do not change：** Credit 写路径 / CAS；Identity 后端；`sdd/planning-artifacts/**`；Pi 模块。

## Tasks & Acceptance

**Execution:**
- [x] `forma-web/src/types/business/credit.ts` — `CreditBalance` + 静态三档价目常量（标签/额度/月费文案）— 类型唯一出处
- [x] `forma-web/src/api/business/credit/credit.ts` — `getCredits()` → `GET /api/v1/credits` — 只 HTTP
- [x] `forma-web/src/views/business/credit/CreditPlan.vue` — `/credits`：摘要 + 三张套餐卡 + `available===0` 不足人话 — FR2/NFR3（人改 UX：卡非行）
- [x] `forma-web/src/router/index.ts` + Login/Register 跳转 + `/me`↔`/credits` 链 — 登录默认落 credits
- [x] `forma-web` 测（api 与/或页面关键断言）— 覆盖矩阵：未登录、成功展示、不足文案、三卡当前档
- [x] `README.md`（若缺）— 补登录后 `/credits` 一句 — 人可跟测

**Acceptance Criteria:**
- Given 登录/注册成功，when 进入应用，then 默认落 `/credits` 且可见档位、剩余积分、下次重置（东八区）
- Given 打开 `/credits`，when 浏览套餐区，then 三档为并列套餐卡（大价格 + 额度副标 + 当前可辨），无支付按钮；`/me` 仍只身份
- Given `available === 0`，when 查看 `/credits`，then 常驻人话提示可升级或等待重置
- Given 无 JWT，when 访问 `/credits`，then 不误调成功态；引导登录

## Implementation Notes

- 落地：`/credits` → `CreditPlan.vue`；登录/注册 `push({ name: 'credits' })`；`/me` 链「套餐与积分」。
- 类型/API：`types/business/credit.ts`（价目常量 + 东八区格式化 + 不足文案）+ `getCredits()`。
- 工具：拆出 `vitest.config.ts`；`index.html` 加载 Space Grotesk / Noto Sans SC；`auth.flow.test` 适配 `noUncheckedIndexedAccess`。
- 验证：`npm run lint && npm test && npm run build` 全绿（20 tests）；评审补丁后挂载测覆盖矩阵。
- 风险：未对手动联调真实后端；`nextResetAt` 按 ISO UTC 解析。
- 评审补丁：CreditPlan/Auth/router 真挂载与 resolve 测；null data 守卫；东八区钉死 `YYYY/MM/DD HH:mm`；荧光纸铺满视口；README 补不足人话。
- Rework（walkthrough）：价目区改 Manus 式三卡；当前档黑底「当前套餐」状态条 + 店章红描边；无购买按钮。

## Spec Change Log

- 2026-09-24 walkthrough Rework：人要求三卡并参考 Manus 定价 UI；冻结意图中 UX-DR5「价目表行」改为三张并列卡（无支付 CTA）；实现与验收同步。

## Review Triage Log

- medium（verification-gap）— 登录落地仅 `readFileSync` 扫源码：pre-verified；AuthLogin/AuthRegister 未跑 `router.push`。
- medium（verification-gap）— CreditPlan 未登录/401 门禁测在测试内镜像：pre-verified；删 early-return/`clearToken` 仍绿。
- medium（verification-gap）— 不足/available/reserved 靠源码串与同义断言：pre-verified；模板反转仍绿。
- medium（verification-gap）— 东八区重置仅测 helper：pre-verified；页面绑回 ISO 仍绿。
- medium（verification-gap）— `/credits` 路由无 counting 测：pre-verified；删路由条目源码测仍绿。
- low → defer — AuthMe→credits 链无测：次要导航；有页面 harness 再补。
- medium（blind）— 矩阵测弱/镜像：与 verification-gap 同源，合并 patch。
- low（blind）— `formatNextResetAtShanghai` 松散 regex：真；钉死完整可读串即可。
- low → reject — Google Fonts CDN 国内可达性：近端可接受 system-ui 回退；自托管字体超本故事最小补丁。
- low → reject — `/credits` 无退出：意图允许经 `/me` 登出；非缺陷。
- low → reject — 非 401 无重试：日常可刷新；加分支超直接修正。
- low（blind）— README 未提不足人话：真；补一句即可。
- false — sprint 标 review 但 diff 无 spec 文件：跟踪产物非用户缺陷。
- false — `tier | string` 导致高亮失败：后端枚举名固定 FREE/PRO/PLUS；日常达不到。
- medium（edge）— `getCredits` 返回 null/undefined 空白页：核实 `request` 可返回空 data；需守卫。
- low → reject — `available < 0` 不提示：账本正常路径不产出负 available；脏库边缘。
- false — 未知档不高亮：同枚举契约；日常达不到。

## Design Notes

- 价目数据前端静态常量即可（后端无定价目录）；当前档用 API `tier` 标在对应卡（边框 +「当前套餐」状态条）。
- 卡层次对齐 Manus：大价格 → 灰色副标（额度）→ 状态条 → 简短要点；色与字体仍用 Adam（荧光纸 / 真黑 / 店章红）。
- 主数字用 `available`；可选次要展示「预占中 reserved」不抢主叙事。
- `nextResetAt` ISO 解析后 `Asia/Shanghai` 格式化为可读本地时间（勿裸 UTC 串糊用户）。
- 不足文案示例：「积分不足。可升级套餐，或等到下次重置后再用。」（无支付按钮承诺）
- 注册/登录后进套餐入口：免费 20、下次重置可读、三卡高亮免费

## Verification

**Commands:**
- `cd forma-web && npm run lint && npm test && npm run build` -- expected: 全绿
- （可选）后端已有 credits 测不必重跑全 reactor，除非改了 BE

**Manual checks (if no CLI):**
- 注册/登录后进套餐入口：免费 20、下次重置可读、三张套餐卡高亮免费
- 清 token 访问入口：引导登录
- （可用测库把余额拨到 0 或 mock）见不足人话
