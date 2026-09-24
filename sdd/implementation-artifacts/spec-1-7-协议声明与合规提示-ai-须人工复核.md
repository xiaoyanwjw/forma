---
title: '1.7 协议声明与合规提示（AI 须人工复核）'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '80f18b1fab2c5f8d1acb805fb5bf7b2fd9f71ee9'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 注册与进入工具前缺少「AI 生成须人工复核后再上架、不承诺销售效果」的明示与确认，责任边界不清，也不满足 NFR4。

**Approach:** 用静态合规文案 + 注册页强制确认（勾选或等效），在用户完成注册前已知悉；落地页页脚短声明可见。注册 API 须校验 `agreedToAiDisclaimer=true`（不落库）。不做法务 CMS；老用户与「首次生成」门禁留给 Epic 2。

**Decisions:**
- 后端强制：注册请求必须 `agreedToAiDisclaimer: true`，否则拒绝；不新增用户表同意列、不落库同意时间
- 存量用户：本故事只闸新注册；老用户等到 Epic 2「首次生成」再补闸
- 落地形态：页脚/角落一行短声明，不抢第一视口「我能为你做什么？」主构图

## Boundaries & Constraints

**Always:**
- 可见声明须含：AI 生成内容须人工复核后再上架；不承诺销售效果
- 用户须确认已知悉后才能完成注册（勾选或等效明示）
- `POST /api/v1/auth/register` 须校验 `agreedToAiDisclaimer === true`；false/缺省人话拒绝；不落库
- 静态文案即可；视觉延续 Adam（荧光纸 / 真黑 / 店章红 + Space Grotesk / Noto Sans SC）
- FE 测沿用 createApp + memory router；BE 扩 Identity 注册单测/集成测

**Never:**
- 不引入完整法务 CMS、多版本协议、律师审定流程；不落库同意审计列
- 不实现选品/Listing 生成、Agent 壳、SSE；本故事不做「首次生成」运行时门禁，也不强制老用户补确认
- 不改 JWT 签发、`afterLogin`→`/credits`、CreditLedger、登录主路径
- 不引导违禁宣传话术；不做支付/小程序

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 注册未确认（FE） | 未勾选点提交 | 不调 register API；提示须先确认 | 人话错误 |
| 注册已确认 | 勾选 + 合法表单 + agreed=true | 注册→自动登录→credits | API 错误仍人话 |
| API 未同意 | `agreedToAiDisclaimer` false/缺省 | 不建用户 | 人话拒绝（4xx） |
| 打开注册页 | `/register` | 见合规声明 + 确认控件 | N/A |
| 打开落地页 | `/` | 页脚/角落可见短声明；主构图不变 | N/A |
| 登录/老用户 | `/login` 或已有账号 | 不强制再勾选 | N/A |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-web/src/views/identity/AuthRegister.vue` — 静态声明 + checkbox；未确认不调 API；请求带 `agreedToAiDisclaimer: true`
- `lippi-ai-ebus-web/src/views/marketing/LandingPage.vue` — 页脚/角落短声明（勿改 guest→login / JWT→credits）
- `lippi-ai-ebus-web/src/constants/compliance.ts`（或同级）— 共享短文案，注册/落地共用
- `lippi-ai-ebus-web/src/api/identity/auth.ts` + `types` — register body 增加字段
- `lippi-ai-ebus-web/src/views/identity/auth.landing.test.ts` — 未确认不提交；确认后仍进 credits
- `lippi-ai-ebus-web/src/views/marketing/LandingPage.test.ts` — 断言页脚声明可见
- `interfaces/.../vo/identity/RegisterRequest.java` — 增加 boolean；缺省/false 校验
- `application/.../command/RegisterCommand.java` + `IdentityApplicationService.register` — 未同意早退
- `IdentityApplicationServiceTest` / `AuthIntegrationTest` — 覆盖 API 未同意拒绝
- Continuity：spec-1.2 协议留给 1.7；spec-1.6 不接勾选（本故事改 register 入参，勿动 JWT/`afterLogin`）

**Reuse：** Auth 表单样式、`ApiError`、`auth.landing.test.ts` helpers、`BusinessException`/`ErrorCode`。

**Do not change：** `afterLogin`；登录→credits；CreditLedger；JWT；`LandingPage` 胶囊/输入路由；用户表 schema；生成门禁。

## Tasks & Acceptance

**Execution:**
- [x] `constants/compliance.ts` — 共享文案（人工复核 + 不承诺效果）— NFR4
- [x] `AuthRegister.vue` + auth API/types — 声明、勾选门禁、传 `agreedToAiDisclaimer: true` — AC
- [x] `LandingPage.vue` — 页脚/角落短声明 — AC 落地可见
- [x] `RegisterRequest`→`RegisterCommand`→`register` — 强制 true、不落库 — 防 API 绕过
- [x] FE `auth.landing` + `LandingPage` 测；BE Identity 测 — 覆盖 I/O 矩阵
- [x] `sprint-status`：1-7 → `review`（实现完成后）

**Acceptance Criteria:**
- Given 打开注册页，when 未确认合规，then 无法完成注册且见须确认提示
- Given 已确认且表单合法，when 注册，then 自动登录→credits 旅程不变，且请求带 agreed=true
- Given `agreedToAiDisclaimer` 为 false 或缺省，when 调注册 API，then 不建用户且人话拒绝
- Given 打开落地页，when 浏览，then 页脚/角落可见含人工复核/不承诺效果语义的短声明，且提问主区仍居中
- Given 已有账号登录，when 使用现有页面，then 本故事不强制补确认；无法务 CMS；无生成门禁

## Implementation Notes

- FE：`AI_DISCLAIMER_SHORT` / `AI_DISCLAIMER_REGISTER_LABEL`；注册未勾选人话拦截；落地 `data-testid="ai-disclaimer-footer"`。
- BE：`RegisterRequest.@AssertTrue` + `IdentityApplicationService` 双闸；字段不落库。
- 矩阵测：`auth.landing` 未勾选/已勾选；`LandingPage` 页脚；`AuthIntegrationTest` false/缺省；`IdentityApplicationServiceTest` false。

## Spec Change Log

## Review Triage Log

- false — blind：BE 拒绝文案与 FE 短声明差「仅供参考」、且 AssertTrue/Service 两处重复：Always 只要求「人工复核」「不承诺销售效果」语义；两处文案均已覆盖，不构成产品缺陷。
- false — blind：注册拦截错误未复述合规短语：页上已有 `AI_DISCLAIMER_SHORT` 可见声明；错误句只负责提示须勾选。
- false — blind：提交硬编码 `agreedToAiDisclaimer: true` 未绑 `.value`：未勾选已早退；过闸后值为 true，请求与控件一致。
- low（patch）— blind：`registerMissingDisclaimerRejected` 未断言人话含合规语义：缺字段走 Bean Validation，应与 false 用例同样钉住 message。
- low → reject — blind：checkbox 缺 aria/required/未禁用提交：Design Notes 允许拦截提交；加 a11y 接线超直接修补。
- low → reject — blind：声明样式未显式套店章红/标题字体：落地/注册页已挂 Adam 字体；页脚次要灰字符合短声明角色。
- low → reject — blind：页脚可能进第一视口且测未断言 fold：选型即页脚短声明；不抢主标题即合格，做视口测成本高。
- false — blind：diff 未含 spec 文件：跟踪产物非运行时缺陷；本评审已对照磁盘 spec。
- false — edge：JSON `null` 对 primitive boolean 会 500：Jackson 2.13 将 null 收成 false，再经 `@AssertTrue` 得 400 人话（与缺省同路径）。
- （verification-gap：无缺口发现）

## Design Notes

- 文案骨架：「AI 生成内容仅供参考，须人工复核后再上架；Adam 不承诺销售效果。」
- 「首次生成」+ 老用户补闸：Epic 2 接线时再做。
- 确认：原生 checkbox + label；未勾选禁用提交或提交拦截均可，测须覆盖。
- 字段名固定：`agreedToAiDisclaimer`（FE/BE 一致）。

## Verification

**Commands:**
- `cd lippi-ai-ebus-web && npm run lint && npm test && npm run build` -- expected: 全绿
- `mvn -pl lippi-ai-ebus-starter -am test` -- expected: Identity 相关测绿

**Manual checks (if no CLI):**
- `/register`：未勾选不能注册；勾选后可走通
- `/`：页脚可见短声明；提问主区仍居中
