---
title: '1.3 积分账本（额度 / 预占 / 结算 / 月重置）'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '880ac15527387ee39f0be128f3da1ce9e7267c49'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Identity 已可鉴权，但尚无 CreditLedger：无法按套餐管理月额度，也没有「检查→预占 1→可用成果落库后结算 / 否则释放」与月重置；后续生成计费无处可接。

**Approach:** 新建 CreditLedger 域（仅它可写余额与套餐档）：三档额度、账本初始化、预占/结算/释放、按订阅锚点月重置（剩余清零不结转）；对外提供鉴权查询接口；写路径以 ApplicationService 供 Epic 2 编排，本故事用集成测证明。

**Decisions:**
- 域目录按 `02-be`：`business/credit`（非 Identity 平铺）
- 本故事无前端积分 UI（1.4）、无手工改档 API（1.5）、不接 GenerationRun/SSE（Epic 2）
- 公开写 REST 不开放预占/结算/释放（防绕过编排）；仅 `GET` 查询（含懒月检）
- Hold 无自动超时；释放仅显式调用；可用额 = 余额 − 活跃预占之和，余额够则允许多 hold
- 月重置在查询或预占前懒执行（无独立定时任务本故事）
- 订阅周期锚点：注册日滚动月——以注册时刻（UTC）为锚点，下次重置为「同日同时刻 +1 日历月」（无该日则夹到月末）；改档后锚点是否重算留给 1.5
- 账本创建：注册成功后同步建免费账本（Identity 注册事务内调 Credit 建账）；注册即可查到免费 20
- 已有用户无账本（1.2 遗留）：查询时补偿建免费账本（锚点=首次补偿时刻），避免老账号 404

## Boundaries & Constraints

**Always:**
- 仅 CreditLedger 写余额、预占、结算、月重置、套餐档（AD-5/AD-6）
- 额度：免费 20 / Pro 200 / Plus 600；月重置剩余清零不结转（FR4/FR5）
- 计费：`检查 → 预占 1 → 仅可用成果已持久化后结算 → 否则释放`；禁止「流结束即扣」
- 包根 `com.xmut.ebus`；写 `CreditApplicationService`，读 `CreditQueryService`；Controller 薄、注入 `userId`
- ID 为 UUID 字符串；时间存 UTC；REST 错误 `code` + 人话 `message`
- SQL 追加 `002_*.sql`；H2 测试 schema 同步

**Never:**
- 前端/Agent 工具直改积分表；本故事不造假生成 API 或 SSE
- 不实现套餐展示页、价目表视觉、不足提示 UI（1.4）
- 不实现管理改档/支付/微信（1.5/AD-9）
- 不实现 GenerationRun、AgentSession、选品/Listing 落库接线（Epic 2+）
- 不引入 Redis；不把领域逻辑塞进 starter；不改 Identity 鉴权栈与 `ebus_user`

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 注册建账 | 合法注册成功 | 同事务建免费账本：余额 20、锚点=注册时刻、下次重置=+1 日历月 | 建账失败 → 注册整体回滚 |
| 查询额度 | JWT + 已有账本 | 返回档位、可用余额、下次重置（UTC）；过重置点则先懒月重置 | 未登录 → 401；无账本则补偿建免费账 |
| 预占成功 | 可用额 ≥ 1 | 创建 hold（UUID）、可用额 −1；余额未结算前不永久扣减语义清晰 | N/A |
| 余额不足 | 可用额 0 | 拒绝预占 | 人话「积分不足」类 |
| 结算 | 有效活跃 hold + 调用方声明成果已落库 | hold 完结；余额按预占扣实；不可再结算同一 hold | 无效/已完结 hold → 人话错误 |
| 释放 | 有效活跃 hold | hold 释放；可用额恢复 | 同上 |
| 月重置 | 已过下次重置点 | 按当前档重发额度；上月剩余清零；推进下次重置 | 与查询/预占同事务懒触发 |
| 双花防护 | 并发预占超出可用 | 至多扣到可用额；超额失败 | 事务/乐观或条件更新 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-domain/.../identity/` — 镜像形状；Credit 建在 `domain/business/credit/{model,constant,repository}`
- `lippi-ai-ebus-application/.../identity/service/IdentityApplicationService.java` — 注册成功后同事务调 Credit 建免费账本；写服务范本
- `lippi-ai-ebus-application/.../identity/query/IdentityQueryService.java` — 读服务范本 → `CreditQueryService`
- `lippi-ai-ebus-infrastructure/.../persistence/mybatis/{po,mapper}/` + `UserRepositoryImpl` — MyBatis + InstantTypeHandler；Credit PO/Mapper/RepoImpl
- `lippi-ai-ebus-interfaces/.../web/identity/MeController.java` — 薄 Controller + JWT userId 范本 → `CreditController`（仅 GET）
- `lippi-ai-ebus-common/.../exception/ErrorCode.java` — 扩展积分不足/hold 无效等码
- `APP-META/bootstrap/sql/001_ebus_user.sql` — 下一号 `002_ebus_credit*.sql`（账户 + hold）
- `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql` — 同步测表
- `AuthIntegrationTest` / `IdentityApplicationServiceTest` — 集成/单测形状
- Spine AD-5/AD-6/AD-12；`02-be` business 目录配方

**Reuse：** Identity 分层与错误模型；UUID + UTC Instant；`ApiResponse`。

**Do not change：** JWT/Security/限流；`ebus_user`；Pi 模块；`sdd/planning-artifacts/**`；1.2 冻结意图。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/002_*.sql` + `schema-h2.sql` — CreditAccount / CreditHold 表（UUID、档位、余额、锚点、hold 状态）— 持久化
- [x] `lippi-ai-ebus-domain/.../business/credit/` — 模型、档位常量 20/200/600、仓储端口 — 唯一账本真相
- [x] `lippi-ai-ebus-application/.../business/credit/` — `CreditApplicationService`（init/预占/结算/释放/懒月重置）+ `CreditQueryService` — AD-5 编排
- [x] `lippi-ai-ebus-infrastructure/...` — MyBatis PO/Mapper/RepoImpl；条件更新防双花 — 落地并发安全
- [x] `lippi-ai-ebus-interfaces/.../web/business/credit/` — 鉴权 `GET` 额度（懒 init/月检）— 供 1.4/联调
- [x] `ErrorCode` + 全局异常映射 — 积分不足/hold 无效人话
- [x] `IdentityApplicationService` 注册成功钩子 — 同事务建免费账本，与用户同生
- [x] 单元/集成测 — 覆盖 I/O 矩阵（含注册建账、不足、释放、滚动月重置、并发预占）— 防回归

**Acceptance Criteria:**
- Given 新用户注册成功，when 带 JWT 查询额度，then 免费档余额 20，锚点为注册滚动月，且仅 CreditLedger 可写余额
- Given 可用额 ≥ 1，when 预占 1，then 产生 hold；结算后余额实扣；释放则预占回滚且不实扣
- Given 已过该用户下次重置点，when 查询或预占，then 按档重发、上月剩余清零、下次重置按滚动月推进
- Given 无 JWT 或 Agent/前端直改表意图，when 走公开 API，then 无法写余额；写能力仅服务层+测

## Implementation Notes

- 余额模型：`available = balance − reserved`；预占增 reserved，结算减 balance+reserved，释放只减 reserved。
- 测试：`CreditPeriodSupportTest` 3、`CreditApplicationServiceTest` 7、`CreditIntegrationTest` 8 全绿；相关模块 `mvn … test` BUILD SUCCESS。
- 已有 MySQL volume 需执行 `002_ebus_credit.sql` 或重建。
- Hold 无超时（规格决定）；极端 CAS 重试耗尽现为 SYSTEM_ERROR「预占冲突」。
- 评审补丁：settle/release 先 `tryClaimFromActive`（ACTIVE→SETTLED/RELEASED）再改账户，防同 hold 双扣；补月重置保留 reserved / 异用户 hold / 注册建账回滚 / GET 补偿建账测。
- 余额模型：`balance` 为已结算剩余；`reserved` 为 ACTIVE hold 占用之和；`available = balance − reserved`。预占只增 reserved；结算同减 balance+reserved；释放只减 reserved。
- 公开 API 仅 `GET /api/v1/credits`；预占/结算/释放仅 `CreditApplicationService`（供 Epic 2 编排与测试调用）。
- 月重置按 `period_anchor_at` 滚动月懒触发；无独立定时任务。
- 已有 MySQL volume 需重建或手工执行 `002_ebus_credit.sql`。

## Review Triage Log

- medium — `reserveOne` CAS 耗尽抛 `CREDIT_INSUFFICIENT`：`CreditApplicationService.java:99` 核实；竞态会被误报为积分不足。
- medium（verification-gap）— 矩阵「建账失败→注册回滚」无测：pre-verified。
- low → reject — Pro/Plus 额度未测：本故事 AC/默认档为 FREE；改档属 1.5。
- medium（verification-gap）— 月重置后未断言 `reserved` 保留：pre-verified；`applyMonthlyReset` 保留 reserved，测缺口。
- high — `CreditHoldMapper.update` 无 `status=ACTIVE`：并发 settle/release 可对同一 hold 多次 `trySettle` 双扣；edge-case 与 blind 同源。
- low → reject — `ensureReady` 可为任意 userId 建账：公开路径仅 JWT；服务层约定调用方已鉴权。
- defer — H2 与 MySQL schema（FK/索引/精度）双份漂移：1.2 已 defer 同类问题。
- low → reject — `user_id` 无 FK / 删除策略：近端无用户删除故事；扩 FK 非直接用户伤害。
- false — 领域 `applyReserve` 等未走写路径：写路径用 Mapper 条件更新，属有意设计。
- low → reject — `CREDIT_INSUFFICIENT`→402：规格只要人话 message；HTTP 码属实现选择。
- low → reject — README 未列 `periodAnchorAt`：文档完整性，非缺陷。
- medium（verification-gap）— 补偿建账仅测 `ensureReady` 未走 `GET /api/v1/credits`：pre-verified；QueryService 确调 ensureReady。
- high — 同 hold 并发 settle 双扣：见上 hold 无条件更新（carried）。
- false — 月重置后 `reserved > quota` 致 available 负：1.3 无改档，FREE 下 reserved≤balance≤20，重置后 balance=20 不会负。
- medium — `settle`/`release` 的 `userId` 为 null 时 `equals` NPE：`requireActiveHold` 核实。
- low → reject — 坏档位/状态串抛 `IllegalArgumentException`：需脏库才触发，日常遇不到。
- high（claim）— 同 hold 并发双扣：与上 high 合并。
- medium（verification-gap）— settle/release 未测异用户 ownership：pre-verified。
## Design Notes

- 账户与 hold 分表：`CreditAccount` 1—* `CreditHold`；hold 状态至少 ACTIVE / SETTLED / RELEASED。
- 结算入参由调用方保证「成果已落库」；1.3 测用直接调 ApplicationService，不落假成果表。
- 档位枚举存账户上；默认 FREE；改档入口留给 1.5，本故事测可用仓储/测试夹具设 Pro/Plus。
- 查询 DTO：`tier`、`balance`（或 available）、`nextResetAt`；available = balance − ACTIVE holds（若余额字段在结算时才减，则预占期单独算 available——实现时二选一写清并测通：推荐余额在预占时冻结占用字段或 reserved，结算再转实扣）。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-common,lippi-ai-ebus-domain,lippi-ai-ebus-application,lippi-ai-ebus-infrastructure,lippi-ai-ebus-interfaces,lippi-ai-ebus-starter test` -- expected: BUILD SUCCESS，Credit 相关测绿
- `mvn -pl lippi-ai-ebus-starter -am -DskipTests compile` -- expected: BUILD SUCCESS

**Manual checks (if no CLI):**
- 注册/登录后 `GET` 额度见免费 20；服务层预占→释放后可用恢复；预占→结算后实扣；拨钟过重置点后再查额度按档重发
- 已有 MySQL volume 需重建或手工执行 `002_*.sql`
