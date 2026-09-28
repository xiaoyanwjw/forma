---
title: '4.4 安全分区：改密真能力与退出'
type: 'feature'
created: '2026-09-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '87dd3651d0be0a862ba140de480d8db284ebbe86'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-4-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 账户页「安全」分区改密仍是占位；用户无法真正更换密码，安全感缺失。退出虽已可用，需与真改密一并验收。

**Approach:** 新增 Identity 改密真接口 `PUT /api/v1/account/password`（旧密码校验 + 新密码合规才更新哈希）；安全区启用改密表单（仅当前密码 + 新密码两栏）；保留既有客户端退出清 JWT；删除账户继续禁用/即将开放。

**Decisions:**
- 改密成功后会话：**B 保持登录** — 仅提示成功，不清 JWT、不跳转；下次登录须用新密码（旧 JWT 可用至过期）
- 改密表单：**B 两栏** — 只要「当前密码 + 新密码」，不要确认新密码栏

## Boundaries & Constraints

**Always:**
- 改密经 `/api/v1/account/password`，写口归 Identity；旧密码正确且新密码合规才更新 `password_hash`
- 新密码规则与注册对齐：非空、至少 6 位、最长 72（BCrypt 上限）
- 错误给人话中文提示，失败时密码哈希不变
- 改密成功：保持当前登录态，仅成功提示；不清 token、不强制重登
- 退出：清除本地 JWT（`clearToken`）并回登录页；再访问受保护页需重新登录
- 「删除账户」若展示则禁用/即将开放，不得假装已删除
- Controller 薄，注入 `userId`；用户可见文案无内部黑话

**Never:**
- 不引入服务端 JWT 黑名单 / Redis 登出 / 全局 router guard（近端无此基础设施）
- 改密成功后不清本地 JWT、不跳转登录（与退出路径区分）
- 不在 `/api/v1/account/**` 增加积分预占/结算/改档写口
- 不实现真实删号、换邮箱、换头像
- 不改 profile GET|PATCH、credits usage 契约与行为
- 不改注册/登录路径形状；不放宽或收紧注册已有密码规则之外的复杂度（无强制大小写/符号）
- 不加「确认新密码」输入栏

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 改密成功 | 已登录；旧密码正确；新密码合规 | `PUT` 成功；UI 提示成功且保持登录；此后新登录须用新密码 | 无 JWT → 未授权 |
| 旧密码错 | 旧密码不匹配 | 人话错误；`password_hash` 不变 | 如「当前密码不正确」 |
| 新密码不合规 | 空 / &lt;6 / &gt;72 | 人话错误；哈希不变 | 如「密码至少 6 位」 |
| 新旧相同 | 新密码=旧密码 | 人话拒绝；哈希不变 | 如「新密码不能与当前密码相同」 |
| 退出 | 点「退出」 | 本地 JWT 清除；跳转登录；再进账户需登录 | N/A |
| 删号占位 | 展示删除行 | 控件禁用，文案即将开放 | 不得调用删号 API |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-interfaces/.../web/identity/AccountController.java` — 增 `PUT /password`；复用 `SecuritySupport.requireUserId()`；无 Credit 依赖
- `lippi-ai-ebus-interfaces/.../vo/identity/` — 新增改密 Request VO（oldPassword / newPassword + Bean Validation）
- `lippi-ai-ebus-application/.../identity/service/IdentityApplicationService.java` — 增 `changePassword`：matches 旧哈希 → 校验新规则 → hash → 持久化
- `lippi-ai-ebus-application/.../identity/command/` — `ChangePasswordCommand`（继承 `BaseCommand`）
- `lippi-ai-ebus-domain/.../identity/repository/UserRepository.java` + `UserMapper` / Impl / XML — 增 `updatePasswordHash(userId, hash, updatedAt)`
- `lippi-ai-ebus-domain/.../identity/port/PasswordHasher.java` + `BcryptPasswordHasher` — 复用 hash/matches，不改算法
- `lippi-ai-ebus-starter/.../AccountProfileIntegrationTest.java`（或 Auth IT）— 改密成功后可用新密码登录、旧密码失败、错误不改哈希、未授权
- `lippi-ai-ebus-application/.../IdentityApplicationServiceTest.java` — 单测旧错/不合规/相同/成功
- `lippi-ai-ebus-web/src/api/identity/account.ts` + types — `changeAccountPassword`
- `lippi-ai-ebus-web/src/views/identity/AccountSettings.vue` — 替换改密占位为真表单；保留 `logout()`；删除仍禁用
- `lippi-ai-ebus-web/src/views/identity/AccountSettings.test.ts` — 改密成功/失败提示、退出既有断言保留
- `lippi-ai-ebus-web/src/api/http.ts` — `clearToken` 复用；不新造 auth store
- `sdd/.../mockups/profile.html` `#panel-security` — 行布局参照（本故事启用改密与退出）

**Reuse：** `IdentityApplicationService` 注册密码规则与 `PasswordHasher`；`AccountController` 薄写模式；IT `registerAndLogin`；FE `logout` + security 分区壳。

**Do not change：** `AccountCreditController` / usage；profile 契约；Credit 写路径；JWT 签发与无黑名单策略；删除账户占位语义。

## Tasks & Acceptance

**Execution:**
- [x] `UserRepository` + Mapper/XML — `updatePasswordHash` — 持久化新哈希
- [x] `IdentityApplicationService` + Command — `changePassword` 校验旧密/新规/新旧不同 — 真改密核心
- [x] `AccountController` + Request VO — `PUT /api/v1/account/password` — HTTP 面
- [x] 后端单测 + IT — 覆盖矩阵：成功/旧错/不合规/新旧同/未授权；断言失败不改哈希
- [x] FE `api` + types — `changeAccountPassword` 对齐契约
- [x] `AccountSettings.vue` — 启用两栏改密表单；成功仅提示且保持登录；保留退出与禁用删号
- [x] `AccountSettings.test.ts` — 改密成功保持 token/错误提示 + 退出清 token

**Acceptance Criteria:**
- Given 安全区提交正确旧密码与合规新密码，when 调用改密接口，then 哈希更新；UI 成功提示且会话保持；此后新登录须用新密码
- Given 旧密码错误或新密码不合规，when 提交，then 人话错误且密码不变
- Given 点击退出，when 完成，then 本地 JWT 清除，再访受保护页需登录
- Given 删除账户控件展示，when 近端未实现，then 禁用/即将开放，不得假装已删

## Implementation Notes

- 落地：`PUT /api/v1/account/password`；`IdentityApplicationService.changePassword`；`updatePasswordHash`；FE 两栏改密；成功保持 JWT；退出仍 `clearToken`。
- 旧密码错误用 `PARAM_INVALID`（400），避免 FE 误清 token。
- 新密码规则与注册共用 `requireValidNewPassword`（6～72）。

## Spec Change Log

## Review Triage Log

- low → patch — Blind：`oldPassword` 仅 `@NotBlank`、无 `@Size(max=72)`，与 `LoginRequest` 不一致；超长串仍进 BCrypt `matches`。属实，补 `@Size(max=72)`。
- low → reject — Blind：Bean Validation「密码长度需在 6～72 之间」与服务层「至少 6 / 最长 72」文案不一致：两套皆人话可读，日常不构成缺陷。
- low → reject — Blind：不 trim 空格密码：规格未要求 trim；加 trim 改语义且非日常必遇。
- low → reject — Blind：缺 `minlength="6"`：JS 已校验，HTML 对称性可忽略。
- medium → patch — Blind：改密区非 `<form>`，Enter 无法提交：属实，包成 form + `@submit.prevent`。
- low → reject — Blind：缺 `aria-invalid`/`aria-describedby`：可用，但接线超直接修补且规格未钉 a11y。
- low → reject — Blind：`passwordSaving` 时输入仍可改：竞态极窄，加 disabled 非必要。
- low → reject — Blind：缺 `findById` 空测：鉴权后几乎不可达；`updatePasswordHash==false` 已单测。
- false — Blind：IT 未盖 `>72`：应用层单测 `changePasswordRejectsTooLongNewPassword` 已覆盖。
- low → reject — Blind：FE 未测客户端空/过短/相同：服务端矩阵已钉行为，客户端预检非必测。
- false — Blind：注册 max-72 无新回归测：HTTP VO 本就 `@Size(max=72)`；共享校验未引入产品回归。
- false — Blind：UI 未提示他端 JWT 仍有效：冻结决策 B 即保持本机会话且无黑名单；沉默不等于错误。
- false — Blind：改密无错误旧密限流：已鉴权且须旧密；近端不必为该口加锁。
- false — Blind：mockup `#panel-security` 仍禁用：mockup 仅布局参照，不要求同步启用态。
- false — Blind：Implementation Notes / Change Log 空：修规格文档本身，规则禁止因审查改本 build 规格。
- false — Blind：Command 设 `username` 未读：`BaseCommand` 约定 Controller 注入 userId/username，非死线。
- low → patch — Edge：同 Blind，`oldPassword` 缺 max=72（与 Login 对齐）。
- false — Edge：FE `old-password` maxlength=72 挡遗留超长密：本产品注册 VO 历来 max 72，不可达。
- defer — Edge：`char` 长度 ≤72 但 UTF-8 字节 >72 时 BCrypt 截断：注册路径同样 char 规则，属既有问题非本故事引入。
- low → reject — Edge：并发两次改密无 CAS：同用户双开极罕见；CAS 超直接修补复杂度。

## Design Notes

- 退出保持客户端清 JWT；不做服务端吊销（与既有无 Redis 黑名单一致）。
- 改密成功与退出分离：成功只 toast/行内提示，不清 token；退出才 `clearToken`。
- 改密失败统一 `BusinessException` 中文消息，经 `GlobalExceptionHandler` 返回。
- 新密码与旧密码相同显式拒绝，避免「成功但无感」。
- 表单仅两栏（当前密码 / 新密码），无确认栏。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test -Dtest='*Identity*,*Account*,*Auth*' -DfailIfNoTests=false` — 相关后端测绿
- `cd lippi-ai-ebus-web && npm test -- --run src/views/identity src/api/identity` — 相关前端测绿
- `cd lippi-ai-ebus-web && npm run lint` — 无新增 lint 错

**Manual checks (if no CLI):**
- 登录 → 账户 → 安全：改密成功后按决策处理会话；错误旧密见提示且仍可用旧密登录；退出后回登录
