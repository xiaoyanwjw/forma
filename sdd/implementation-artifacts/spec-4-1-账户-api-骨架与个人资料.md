---
title: '4.1 账户 API 骨架与个人资料'
type: 'feature'
created: '2026-09-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '8a9686c2f036a405ad4cf80e12011cc50ed480b5'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-4-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 账户页需要统一的 `/api/v1/account/**` 主契约，但仓库仅有兼容用的 `GET /api/v1/me` 与积分读口；用户无法经账户前缀读邮箱、改用户名，后续资料/用量/安全 UI 无后端锚点。

**Approach:** 落地 `GET|PATCH /api/v1/account/profile`：邮箱只读，**可改字段为既有 `username`（不新增 `display_name` 列/字段）**；保留 `/me`、`/credits` 兼容；禁止经 account 写积分结算/改档。本故事交付后端契约 + FE `api`/`types`（无账户页 UI，留给 4.2）。

**Decisions:**
- 不引入 `display_name` / `displayName`：资料区「显示名」即 `username`（登录名与展示名合一）；改名=改 `username`（唯一约束仍在）
- `GET /me`：**A** — 不改形状；仅 `/api/v1/account/profile` 作为账户页主读/写契约（`/me` 已含 `username`，保持原样）
- 规格体量：Keep full spec（接受略超建议 token 区间）

## Boundaries & Constraints

**Always:**
- 账户页主契约前缀 `/api/v1/account/**`（AD-17）；资料写归 Identity
- 邮箱只读；`username` 可经 profile PATCH 修改（校验规则对齐注册：非空、长度、禁止 `@`、唯一）
- Controller 薄：`SecuritySupport.requireUserId()` → Command / QueryService；写走 `IdentityApplicationService`，读走 `IdentityQueryService`
- 无新用户表列；UserRepository 需支持 `updateUsername`（或等价 update）
- 既有 `GET /api/v1/me`、`GET /api/v1/credits` 保留兼容；账户页主契约仍是 `/account`
- REST 错误 `code` + 人话 `message`；用户可见文案无内部黑话

**Never:**
- 不新增 `ebus_user.display_name` 或 API `displayName`
- 不在 `/api/v1/account/**` 下增加积分结算、预占、改档或任何 CreditLedger 写口
- 不实现改密、用量流水、退出、删除账户 UI/API（4.3 / 4.4）
- 不实现账户页分区 UI / 顶栏跳转改造（4.2）
- 不把 `email` 改为可写；不混淆场景表 `ebus_scene.display_name`
- 前端不直连大模型、不写积分

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 读资料 | 已登录 JWT | `GET /api/v1/account/profile` 返回 `userId`、只读 `email`、`username` | 无 JWT → 未授权 |
| 合法改用户名 | `PATCH` body `{ username }` 合法且未占用 | 更新成功；再次 GET `/account/profile` 与后续登录可用新名 | N/A |
| 非法用户名 | 空/超长/仅空白/含 `@` | 不落库 | 人话校验错误 |
| 用户名冲突 | 新名已被他人占用 | 不落库 | 人话冲突（CONFLICT） |
| 禁止写积分 | 审查 account 写面 | 无结算/改档/预占端点或服务调用 | 评审/测试断言 |
| 兼容保留 | `GET /me`、`GET /credits` | 与现网一致（形状不变） | 无 JWT → 未授权 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-interfaces/.../web/identity/MeController.java` — 现有 `GET /api/v1/me`；旁路新建 `AccountController`（`/api/v1/account`）
- `lippi-ai-ebus-interfaces/.../web/business/credit/CreditController.java` — `GET /api/v1/credits` 兼容保留；勿迁入 account 写
- `lippi-ai-ebus-application/.../identity/query/IdentityQueryService.java` + `dto/MeDTO.java` — 读我保持原样；另增 profile DTO/读方法
- `lippi-ai-ebus-application/.../identity/service/IdentityApplicationService.java` + `command/RegisterCommand` — 注册校验可复用到改用户名；新增 `UpdateUsernameCommand`（名可微调）；**勿**复制 `initFreeAccount`
- `lippi-ai-ebus-domain/.../identity/model/User.java` + `repository/UserRepository.java` — 已有 `username`；端口现仅 insert/select，需 update
- `lippi-ai-ebus-infrastructure/.../persistence/repository/identity/UserRepositoryImpl.java` + `UserMapper.xml` / `UserPO` — 增加 username update
- `APP-META/bootstrap/sql/001_ebus_user.sql` — **不改表结构**；勿碰场景 `display_name`
- `lippi-ai-ebus-starter/src/test/java/.../AuthIntegrationTest.java` — MockMvc + H2 范本；本故事加 Account IT
- `lippi-ai-ebus-application/src/test/.../IdentityApplicationServiceTest.java` — 写用例 Mockito 范本
- `lippi-ai-ebus-web/src/api/identity/auth.ts` + `types` — 现有 `/me`；新增 profile `api`/`types`（无页面）
- `sdd/implementation-artifacts/epic-4-context.md` — Epic 约束；Spine 路径 `/account/profile`（字段用 `username` 而非独立显示名）

**Reuse：** `SecuritySupport`、`BaseCommand`、注册时 username 校验与唯一性检查、`BusinessException`/`ErrorCode`、`ApiResponse`、Auth IT 形状。

**Do not change：** CreditLedger 结算/预占/改档；`JwtAuthenticationFilter` 主流程（JWT `sub`=userId，改名不影响既有 token）；场景 `display_name`；Pi vendor；用户表 DDL。

## Tasks & Acceptance

**Execution:**
- [x] `domain/identity` + infra User Mapper/Repository — `updateUsername`（或等价 update）— Identity 唯一写用户
- [x] `IdentityApplicationService` + `UpdateUsernameCommand` + 单元测试 — 校验、唯一性、落库；覆盖非法名/冲突
- [x] `IdentityQueryService` + profile DTO — `GET` 投影只读邮箱与 `username` — 账户读契约
- [x] `interfaces/.../AccountController` — `GET|PATCH /api/v1/account/profile`；注入 `userId` — HTTP 面
- [x] **不**改 `MeDTO`/`GET /me` 形状；**保持** `/credits` 兼容且 account 无积分写口；**不**加 `display_name` 列
- [x] `AccountProfileIntegrationTest`（或扩 Auth IT）— 覆盖 I/O 矩阵鉴权/改名/非法名/冲突
- [x] `lippi-ai-ebus-web` `api` + `types`（无账户页 UI）— FE 契约对齐，供 4.2

**Acceptance Criteria:**
- Given 已登录，when `GET /api/v1/account/profile`，then 返回只读邮箱与当前 `username`
- Given 合法且未占用的新用户名，when `PATCH /api/v1/account/profile`，then 更新成功且再次 GET 可见；可用新名登录
- Given 任意 account 写接口，when 实现或评审，then 不存在经 account 写入积分结算/改档/预占的能力，且无 `displayName`/`display_name`
- Given 既有客户端，when 调 `GET /me` 与 `GET /credits`，then 仍可用且形状不变

## Implementation Notes

- 落地：`AccountController` GET|PATCH `/api/v1/account/profile`；`UpdateUsernameCommand` + `IdentityApplicationService.updateUsername`；`UserMapper.updateUsername`；FE `api/identity/account.ts` + types。
- 校验：username trim、2–64、禁 `@`、唯一；同名幂等不写库。
- 验证：`mvn -pl lippi-ai-ebus-starter -am clean test -Dtest=IdentityApplicationServiceTest,AccountProfileIntegrationTest,AuthIntegrationTest -DfailIfNoTests=false` 绿；`npm test -- --run src/api/identity` + `npm run lint` 绿。
- 矩阵覆盖：读资料 / 未授权 / 改名+新名登录 / 非法名 / 冲突 / me+credits 兼容 / AccountController 无积分写口（反射断言）。
- 风险：脏 `target/` 残留 Mapper 可能导致 IT 起不来，需 `mvn clean`。
- 评审补丁：`updateUsername` 返回是否更新到行（0 行→UNAUTHORIZED）；补并发唯一键/超长/0 行单测与 IT 超长；FE PATCH 断言 Authorization；纠正误改的 3-6/3-8 sprint 状态。
## Spec Change Log

## Review Triage Log

- false — Blind：diff 缺 AccountController/IT：评审用 diff 在错误 cwd 生成漏收 untracked；仓内文件存在且 IT 已跑绿。
- false — Blind：FE 落在仓库根 `src/`：实为 `lippi-ai-ebus-web/src/...`；错误 cwd 的 diff 路径前缀丢失。
- medium — Blind：`sprint-status.yaml` 把 `3-6`/`3-8` 从 main 的 review/backlog 改成 done，与本故事无关 — 污染进度表。
- medium — Blind+Edge：`UserRepositoryImpl.updateUsername` 丢弃影响行数，服务在 0 行更新时仍返回成功 DTO — 并发删用户/错 id 会假成功。
- medium — Verification-gap：`updateUsername` 的 `DataIntegrityViolationException`→CONFLICT 无单测（仅 register/save 有）。
- medium — Verification-gap：超长（>64）用户名拒绝无测；矩阵「超长」未覆盖。
- low → reject — Blind：注册共用 `requireValidUsername` 缺新 register 测：RegisterRequest Bean Validation + 既有注册测已覆盖入口；服务层复用非新行为面。
- low → reject — Blind：`findProfile` 缺单测：`AccountProfileIntegrationTest.getProfile*` 已覆盖投影与未授权。
- low — Blind：FE `updateAccountProfile` 未断言 Authorization：`request()` 会附 JWT，但写测缺一行断言易回归。
- false — Blind：IT/端到端不在 diff：同 cwd 漏收；`AccountProfileIntegrationTest` 存在且已验证通过。

## Design Notes

- 路径仍用 Spine：`/api/v1/account/profile`；人话「显示名」映射到字段 `username`（产品选择：不拆独立显示名）。
- PATCH body：`{ "username": "..." }`；响应：`userId`、`email`、`username`。
- 改名后 JWT 仍有效（`sub`=userId）；下次登录须用新用户名（或邮箱）。
- 本故事不含账户页 UI；FE 仅 `api`/`types`。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test -Dtest=IdentityApplicationServiceTest,AccountProfileIntegrationTest,AuthIntegrationTest` — 相关测绿（IT 类名以落地为准）
- `cd lippi-ai-ebus-web && npm test -- --run src/api/identity` — 账户/auth 相关测绿（若新增）
- `cd lippi-ai-ebus-web && npm run lint` — 无新增 lint 错

**Manual checks (if no CLI):**
- 无需执行用户表 DDL 迁移（无新列）
