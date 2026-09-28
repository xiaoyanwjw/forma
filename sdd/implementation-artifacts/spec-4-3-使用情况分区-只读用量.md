---
title: '4.3 使用情况分区（只读用量）'
type: 'feature'
created: '2026-09-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'bd1507887d7f6deda60d2cd0fc4e177d118951e5'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-4-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 账户页「使用情况」仍是占位文案；用户看不到与 CreditLedger 一致的本月摘要与可读流水，无法判断额度怎么花掉。

**Approach:** 新增只读 `GET /api/v1/account/credits/usage`（CreditLedger 聚合：摘要 + 流水），替换账户页用量占位为摘要卡与流水列表（空态不白屏）；保留跳转套餐页；禁止经 account 写积分。

**Decisions:**
- 流水行标题：**A 通用人话** — 仅「已扣分」+ 时间 + 金额；不 join GenerationRun / 产物名
- 流水状态：**A 仅 SETTLED** — 只列真实扣分；不含 ACTIVE / RELEASED

## Boundaries & Constraints

**Always:**
- 账户主契约前缀 `/api/v1/account/**`；用量只读归 CreditLedger，经 account 路由暴露
- 摘要与账本一致：本月剩余 / 额度、本月已用、下次重置（语义对齐既有 `CreditBalance` / 套餐页）
- 流水仅 `SETTLED` hold；行文案为通用「已扣分」+ 时间 + 金额（不 join 产物）
- 无流水时分区空态人话说明，不报错白屏
- 提供跳转套餐页入口；本页不支付
- 既有 `GET /api/v1/credits` 保留兼容；Controller 薄，读走 `CreditQueryService`
- 用户可见文案无内部黑话

**Never:**
- 不在 `/api/v1/account/**` 增加预占 / 结算 / 释放 / 改档写口
- 不改 CreditLedger 写路径与 CAS；不 invent 假流水；不 join GenerationRun 凑假标题
- 不展示 ACTIVE / RELEASED 流水行
- 不实现改密 / 删除账户 / 支付（4.4 / 套餐）
- 不改个人资料 GET|PATCH 契约与安全区退出行为
- 前端不直连大模型、不写积分

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 有用量 | 已登录；有 SETTLED hold | `GET /api/v1/account/credits/usage` 返回摘要 + 仅 SETTLED 流水；UI 摘要卡与「已扣分」列表 | 无 JWT → 未授权 |
| 无流水 | 已登录；无 SETTLED（可有 ACTIVE） | 摘要仍可读；`entries` 空；UI 空态说明 | N/A |
| 懒建账 | 用户尚无 credit 账户 | 经既有 `ensureReady` 懒建后返回摘要（与 `/credits` 一致） | N/A |
| 兼容 | `GET /api/v1/credits` | 形状不变 | 无 JWT → 未授权 |
| 禁止写 | 审查 account 面 | 无结算/预占/改档写端点 | 评审/测试断言 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-interfaces/.../web/identity/AccountController.java` — 仅 profile；本故事**另增**薄控制器挂 `/api/v1/account/credits/usage`（避免 Identity Controller 依赖 Credit）；保持无写积分
- `lippi-ai-ebus-interfaces/.../web/business/credit/CreditController.java` — `GET /api/v1/credits` 兼容保留
- `lippi-ai-ebus-application/.../credit/query/CreditQueryService.java` + usage DTO — `findUsage(userId)`；摘要复用余额字段 + `CreditTier.monthlyQuota`；entries 仅 SETTLED
- `lippi-ai-ebus-domain/.../credit/repository/CreditHoldRepository.java` + `CreditHoldMapper` / Impl — 按 `userId` + `SETTLED` 时间倒序列表（限近 50）
- `lippi-ai-ebus-domain/.../credit/model/CreditHold.java` + `CreditHoldStatus` — 流水行源；标题固定人话「已扣分」
- `lippi-ai-ebus-starter/.../AccountProfileIntegrationTest.java`（或新 IT）— 扩 usage 读/空态/鉴权；断言 account 无积分写口
- `lippi-ai-ebus-web/src/views/identity/AccountSettings.vue` — 替换 usage 占位：摘要 + 流水/空态 + 套餐链
- `lippi-ai-ebus-web/src/api/identity/account.ts` + `types/identity/account.ts` — 增 usage 客户端（或 credit 域 api + types）
- `lippi-ai-ebus-web/src/api/business/credit/credit.ts` + `types/business/credit.ts` — 可复用格式化辅助；主契约仍走 `/account/credits/usage`
- `lippi-ai-ebus-web/src/views/business/credit/CreditPlan.vue` — 摘要展示范本；勿改支付语义
- `sdd/.../mockups/profile.html` `#panel-usage` — 布局参照（摘要三格 + 列表 + 套餐注）
- `lippi-ai-ebus-web/src/views/identity/AccountSettings.test.ts` — 覆盖加载成功、空态、错误/401、切到 usage

**Reuse：** `CreditQueryService.ensureReady` 路径、`CreditBalanceDTO`、`creditTierLabel` / `formatNextResetAtShanghai`、账户页壳与 `credits` 路由链、Auth/Account IT 形状。

**Do not change：** `CreditApplicationService` 预占/结算/释放/改档；`CreditAdminController`；profile GET|PATCH；安全区退出；`GET /credits` 响应形状；GenerationRun 写路径。

## Tasks & Acceptance

**Execution:**
- [x] `CreditHoldRepository` + Mapper — `listSettledByUserId`（时间倒序、上限 50）— 流水数据源
- [x] `CreditQueryService` + usage DTO — 摘要（剩余/额度/已用/重置）+ SETTLED entries（标题「已扣分」）— 只读聚合
- [x] `interfaces` account credits 读口 — `GET /api/v1/account/credits/usage`；注入 `userId` — HTTP 面
- [x] IT / Query 单测 — 覆盖矩阵：有 SETTLED、仅 ACTIVE 时空态、未授权；account 无写积分断言
- [x] FE `api` + `types` — usage 客户端对齐契约
- [x] `AccountSettings.vue` — 替换 usage 占位为摘要+流水/空态+套餐入口
- [x] `AccountSettings.test.ts` — usage 加载/空态/错误至少覆盖矩阵关键项

**Acceptance Criteria:**
- Given 打开「使用情况」且加载成功，when 有 SETTLED 流水，then 展示与 CreditLedger 一致的摘要及「已扣分」列表（只读）
- Given 无 SETTLED 流水，when 打开该分区，then 空态说明而非报错白屏
- Given 使用情况区，when 需要看套餐，then 可跳转套餐页且本页无支付
- Given 任意 account 接口，when 审查写面，then 无预占/结算/改档能力

## Implementation Notes

- 落地：`AccountCreditController` GET `/api/v1/account/credits/usage`；`CreditQueryService.findUsage`；`listSettledByUserId`；FE `getAccountCreditUsage` + `AccountSettings` 用量区懒加载。
- 摘要：`used = monthlyQuota - available`；流水仅 SETTLED，标题「已扣分」，`delta = -amount`，上限 50。
- 验证：后端 `*Credit*,*Account*` 绿；前端 identity 测绿；lint 通过。
- 评审补丁：每次进入 usage 重拉；失败态含旧数据也可重试；IT 覆盖 RELEASED/倒序/LIMIT；FE 套餐链与重试断言收紧。
## Spec Change Log

## Review Triage Log

- false — Blind：本月已用含预占与流水仅 SETTLED 对不上：Design Notes 与冻结决策已钉 `used=quota−available` 且流水仅 SETTLED；与套餐页剩余语义一致，非缺陷。
- low → reject — Blind：缺 user_id+status+updated_at 复合索引：近端用户 hold 量小，加迁移超日常必要复杂度。
- medium — Blind：`usageLoaded` 为 true 后切回用量不重拉：同页会话内摘要/流水会过期。
- low → reject — Blind：未提示「仅最近 50 条」：免费档难积满 50 条 SETTLED，文案非日常必遇。
- low → reject — Blind：`findUsageDoesNotSurfaceActiveHoldsInEntries` 弱测：产品行为已由 IT `getCreditUsageListsOnlySettledHolds` 覆盖。
- medium — Blind：套餐链断言 `a[href="/credits"], a` 会匹配任意 `<a>`，测不到真链。
- low → reject — Blind：流水时间复用 `formatNextResetAtShanghai`：展示正确，仅命名观感。
- false — Blind：FE 未展示 `tier`/`amount`：契约字段可扩展，非用户可见缺陷。
- medium — Blind：已有 `usage` 时失败仅文案无重试：与懒加载一次成功后不刷新路径相关。
- false — Blind：sprint 与规格文件状态不同步：流程态，非产品缺陷。
- false — Edge：`available > monthlyQuota` 导致 used 为负：账本写路径 balance≤档额度，不可达。
- false — Edge：`limit<=0`：公开面固定传 `USAGE_ENTRY_LIMIT=50`，不可达。
- false — Edge：SETTLED amount≤0：创建 hold 固定 `HOLD_AMOUNT=1`，不可达。
- medium — Edge：`usageLoaded` 后重访不刷新（同 Blind 过期）。
- medium — Verification-gap：RELEASED hold 从未断言不出现在 entries；SQL 若放宽仍绿。
- medium — Verification-gap：多行 ORDER BY / LIMIT 未测，持久层倒序与上限可回归。
- medium — Verification-gap：「重试」未点击断言第二次请求，死按钮仍绿。
- low → reject — Verification-gap Other：ACTIVE 单测误导命名：同 Blind 弱测，IT 已覆盖。
## Design Notes

- 摘要「本月已用」= `monthlyQuota - available`（与套餐页「本月剩余 = available」对齐）；额度取当前档 `CreditTier.getMonthlyQuota()`。
- 流水不 join GenerationRun；行标题固定「已扣分」，delta 为 −amount。
- 为保住 4.1「AccountController 无 Credit 依赖」测试心智，独立薄 Controller 挂同一 `/api/v1/account` 前缀。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test -Dtest='*Credit*,*Account*' -DfailIfNoTests=false` — 相关后端测绿
- `cd lippi-ai-ebus-web && npm test -- --run src/views/identity src/api/identity` — 相关前端测绿
- `cd lippi-ai-ebus-web && npm run lint` — 无新增 lint 错

**Manual checks (if no CLI):**
- 登录 → 账户 → 使用情况：有消耗见摘要+流水；新号见空态；点套餐链进套餐页
