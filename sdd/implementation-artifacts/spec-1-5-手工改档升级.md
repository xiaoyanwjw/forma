---
title: '1.5 手工改档升级'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '7217a0401c787cf13373ddaaa4cf2d4caf8b29f0'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 账本只有免费建账与月重置，无法把用户从免费改到 Pro/Plus 并立刻按新档额度使用；近端又不能接支付，联调「用尽→升级」旅程断掉。

**Approach:** 在 CreditLedger 内增加可审计的手工改档写用例：把目标用户升到 Pro 或 Plus、立即刷新为新档月额度，经受控管理入口调用；不接微信/支付宝。

**Decisions:**
- 管理入口：受控 REST（管理路径，联调用 curl）；无前端管理台、无支付 CTA；店主侧仍只读 `/credits`
- 授权：环境变量用户 ID 白名单；调用方须带 JWT 且 `userId` 在名单内，否则 403；不改 JWT 签发主路径、不做产品级 RBAC
- 改档后：`period_anchor_at = 改档时刻`，并重算 `next_reset_at`；`balance = 新档月额度`，保留活跃 `reserved`；查询立刻见新档与可用额
- 仅允许升级（档位序 FREE &lt; PRO &lt; PLUS）；同档幂等成功不重复写审计；降级拒绝
- 目标无账本时先 `ensureReady` 再改档；审计追加表同事务记录谁/何时/从→到（含目标用户）

## Boundaries & Constraints

**Always:**
- 仅 CreditLedger 写档位与余额（AD-5/AD-6）；额度仍 20/200/600
- 改档可审计：操作者、时间、旧档→新档（FR6）
- 受控 REST + 环境变量白名单闸门；不接入微信支付/支付宝（AD-9）
- 写 `CreditApplicationService` + Command；Controller 薄；包根 `com.xmut.ebus`
- SQL 追加 `003_*.sql`；H2 `schema-h2.sql` 同步；ID 为 UUID；时间 UTC；错误 `code` + 人话 `message`

**Never:**
- 不实现支付网关、商户号、前端购买按钮/管理台
- 不开放预占/结算/释放公开 REST；不改 `GET /api/v1/credits` 契约语义
- 不引入完整 RBAC；不以任意登录用户默认可改档
- 不改 Identity 注册/登录主路径；不实现 Agent/生成/落地页（1.6+/Epic 2+）
- 前端不直改账本；不附带 APP-META 运维脚本封装（本故事不做）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 升级成功 | FREE→PRO（或 PLUS），JWT 在白名单 | 档位更新；锚点=改档时刻；余额=新档额度；审计一行；查询可见 | N/A |
| 再升一档 | PRO→PLUS | 同成功路径 | N/A |
| 同档幂等 | 已是 PRO 再改 PRO | 余额/审计不重复突变；成功或明确无变更 | N/A |
| 降级拒绝 | PRO→FREE 等 | 不变 | 人话拒绝 |
| 未授权 | 无 JWT 或不在白名单 | 不变 | 401/403 |
| 目标无账本 | 合法目标 userId 尚无账本 | ensureReady 后改档 | 非法空 ID → 人话错误 |
| 活跃预占中改档 | reserved&gt;0 | 保留 reserved；balance=新额度 | 若 reserved&gt;新额度，available 可为负直至 hold 完结——须测清 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-application/.../business/credit/service/CreditApplicationService.java` — 现有 init/ensureReady/预占结算；**新增 changeTier/upgrade 写用例**
- `lippi-ai-ebus-application/.../business/credit/service/CreditCasWriter.java` + `CreditAccountRepository` / Mapper — 现无更新 `tier`/`period_anchor_at` 的 CAS；需扩展
- `lippi-ai-ebus-domain/.../business/credit/model/CreditAccount.java` — `setTier` 裸 setter；宜加 `applyUpgrade(...)` 领域行为
- `lippi-ai-ebus-domain/.../business/credit/constant/CreditTier.java` — FREE/PRO/PLUS 与额度
- `lippi-ai-ebus-interfaces/.../web/business/credit/` — 现有 `CreditController` 仅 GET；新增管理改档 Controller（如 `/api/v1/admin/credits/...`）
- `SecurityConfig` + 白名单配置（env，如 `CREDIT_ADMIN_USER_IDS`）— JWT 仍只认登录；改档前校验调用方在名单
- `lippi-ai-ebus-common/.../exception/ErrorCode.java` — 补 FORBIDDEN / 改档非法
- `APP-META/bootstrap/sql/002_ebus_credit.sql` — 下一号 `003_*` 审计表；同步 `schema-h2.sql`；`.env.example` 补白名单说明
- `CreditApplicationServiceTest` / `CreditIntegrationTest` — 扩 I/O 矩阵
- Spine AD-5/AD-9；`02-be`；spec-1-3（锚点重算已定案为本故事）

**Reuse：** Command/`BaseCommand`、`LoggerUtils`、`BusinessException`、Credit CAS、`SecuritySupport.requireUserId()`。

**Do not change：** 公开 `GET /api/v1/credits` 字段语义；reserve/settle/release 公开不可达；JWT 签发；支付；1.4 套餐页购买 CTA。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/003_*.sql` + `schema-h2.sql` — 改档审计表（谁/何时/从→到/目标用户）— 可审计
- [x] `domain/.../business/credit/` — `applyUpgrade` + 档位序校验（含锚点重算）— 唯一账本真相
- [x] `application/.../business/credit/` — `ChangeTierCommand` + `CreditApplicationService` 改档 + CAS — AD-5 编排
- [x] `infrastructure/...` — Mapper/Repo：tier+balance+锚点条件更新；审计写入 — 并发安全
- [x] `interfaces/...` — 受控 REST + 白名单闸门（env）— 联调 curl 可达
- [x] `ErrorCode` + 全局映射 — 未授权/降级/非法目标人话
- [x] 单元/集成测 — 覆盖 I/O 矩阵（升级可见、锚点重算、审计、幂等、拒绝降级、白名单、预占中改档）— 防回归

**Acceptance Criteria:**
- Given 目标用户为免费档且调用方 JWT 在白名单，when 经管理 REST 改档为 Pro 或 Plus，then 查询可见新套餐档与对应月额度，且锚点为改档时刻（FR6）
- Given 一次成功改档，when 查审计记录，then 有谁/何时/从→到
- Given 任意路径，when 改档，then 不出现微信支付/支付宝依赖（AD-9）
- Given 调用方不在白名单，when 尝试改档，then 账本不变且 403

## Implementation Notes

- 管理入口：`POST /api/v1/admin/credits/change-tier`，body `{ targetUserId, targetTier }`；操作者 JWT `userId` 须在 `CREDIT_ADMIN_USER_IDS`。
- 改档 CAS：`updateTierBalanceAndPeriod` + 同事务写 `ebus_credit_tier_change`；同档幂等不落第二行审计。
- 已有 MySQL volume：手动执行 `003_ebus_credit_tier_change.sql` 或重建 volume。

## Spec Change Log

## Review Triage Log

- false — blind：`spec-1-4` status=`review` 与 sprint `done` 矛盾：属 baseline 前未提交的 1.4 脏树，非本故事因果。
- false — blind：diff 末尾 `spec-1-5` 无正文 hunk：评审包生成瑕疵；磁盘规格完整可读，不构成产品缺陷。
- defer — blind：规划侧 UX-DR5「价目行」未与 Manus 三卡定案对齐：1.4 walkthrough 人改，属规划文档滞后。
- high（patch）— blind+edge：`changeTier`→`ensureReady` 不校验 Identity 用户存在，任意 UUID 可建孤儿账本并升级；`CreditApplicationService.java:changeTier`。
- false — blind：无审计查询 HTTP：AC 要求可审计落库（谁/何时/从→到），测试 JDBC 已证；意图不含管理读 API。
- low → reject — blind：`replaceAllowedUserIds` 生产可变：仅测试热替换；日常调用方不会注入改名单，加封装成本高于收益。
- defer — blind：非当前档 `card-status` 显示 `—` 像假购买条：`CreditPlan.vue` 属 1.4 展示，非本故事改档。
- defer — blind：套餐卡重复额度文案：同上 1.4 UI。
- defer — blind：「全部已上线模板可用」超前承诺：同上 1.4 文案。
- defer — blind：`.plan-card` 12px 圆角偏离小圆角指引：同上 1.4 视觉。
- medium（verification-gap/patch）— 无效 `targetTier` HTTP 映射无测：可致 500 漏网；disposition=patch。
- low → reject — blind：并发升级失败者误报「不能降级」：极难日常触发；改文案需竞态语义，非直接小修。
- defer — blind：H2 审计表缺 MySQL FK：与 1.2/1.3 schema 双份漂移同类，非本故事引入。
- false — edge：`requireOperatorAllowed` 未用 trim 返回值：JWT `sub` 由本系统签发无空白；坏结局不可达。
- medium（verification-gap/patch）— 升级后未断言持久化/GET 的 `next_reset_at`：可掩盖 mapper 漏写；disposition=patch。
- medium（verification-gap/patch）— IT 全靠 `replaceAllowedUserIds` 绕过 `CREDIT_ADMIN_USER_IDS` 绑定：env 接线可坏而 CI 仍绿；disposition=patch。

## Design Notes

- 改档与审计同事务：账户 CAS 成功才落审计行。
- 预占中改档对齐月重置：保留 `reserved`；新额度 &lt; reserved 时 available 暂负——测试钉死。
- 白名单读环境变量；名单空则所有改档 403（防误开）。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test` -- expected: BUILD SUCCESS，Credit 改档相关测绿
- `mvn -pl lippi-ai-ebus-starter -am -DskipTests compile` -- expected: BUILD SUCCESS

**Manual checks (if no CLI):**
- 免费用户经管理入口升 Pro 后，店主 JWT `GET /api/v1/credits` 见 PRO 与 200
- 审计表可查操作者与从→到；无支付相关配置被引入
