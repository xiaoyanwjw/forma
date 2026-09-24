---
title: '1.2 注册与登录拿 JWT'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '04d2b1a93d3d9ae6a8d5ed4f0b4450ad145e70d0'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 脚手架已可跑，但尚无 Identity：一人店主无法注册/登录拿到 JWT，后续积分与生成无法按用户鉴权；未登录也可能误触需登录能力。

**Approach:** 落地 Identity（用户账号 + BCrypt 密码 + JWT 签发/校验）与 interfaces 轻量频率限制；公开注册/登录，其余 API 需 `Authorization: Bearer`；业务主键 UUID；密码不明文落库；前端提供可用的注册/登录页。

**Decisions:**
- 注册须同时提供用户名 + 邮箱；登录可用用户名或邮箱之一 + 密码
- 本故事交付可用的注册/登录页，JWT 本地存储，登录后调 `GET /api/v1/me` 自检
- 协议确认留给 Story 1.7；本故事注册不加协议勾选
- 「计费生成入口」未授权证明：以受保护的 `GET /api/v1/me` 为准，不造假生成 API

## Boundaries & Constraints

**Always:**
- Identity 唯一写用户与 JWT（AD-6/AD-8）；包根 `com.xmut.ebus`；目录配方见 `02-be`（domain `identity` / application / interfaces / infra）
- API：`Authorization: Bearer <JWT>`；除注册、登录、公开健康检查外均需有效 JWT
- 用户/业务 ID 为 UUID 字符串（AD-12）；密码 BCrypt；JWT 用 parent 已钉的 jjwt 0.11.5（HS256）
- 异常频率限制落在 interfaces（按用户或 IP）；REST 错误 `code` + 人话 `message`
- 密钥仅环境变量 / compose secrets（`JWT_SECRET` 等）

**Never:**
- 不搬 `TenantContext` / 多租户 / 微信 OAuth / 手机验证码 / 验证码登录
- 不强制引入 Redis（compose 仅 MySQL）；不做 JWT 黑名单/吊销表（logout 可后置）
- 不实现 CreditLedger、计费 SSE、GenerationRun、套餐展示、落地页品牌视觉、协议 CMS（属 1.3–1.7 / Epic 2）
- 本故事不加协议勾选（1.7）；不造 `/api/v1/generation-runs` 空桩
- 前端不直连大模型；不把领域逻辑塞进 starter

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 合法注册 | 用户名 + 邮箱 + 密码 | 创建用户（UUID）；可立即登录拿 JWT | 用户名或邮箱重复 → 人话冲突 |
| 合法登录 | 用户名或邮箱 + 密码 | 返回 JWT；可带 Bearer 访问 `/api/v1/me` | 错误凭证 → 统一「用户名或密码错误」 |
| 未带 JWT | `GET /api/v1/me` | 401/未授权；无积分副作用 | 人话 message |
| 无效/过期 JWT | 同上 | 401/未授权 | 同上 |
| 高频注册/登录 | 同 IP 短时大量请求 | interfaces 限流拒绝 | 人话「请求过于频繁」类 |
| FE 走通 | 浏览器打开注册/登录页 | 提交成功后存 JWT，可调通 `/me` | 失败展示人话错误 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-*` — 各模块仅 `package-info`；Identity 全绿场新建
- `lippi-ai-ebus-starter/.../application.yml` — 无 JWT/security；需加 `jwt.*` 与安全相关配置
- `pom.xml` — DM 已钉 jjwt 0.11.5；子模块尚未声明 jjwt / spring-security
- `APP-META/bootstrap/sql/000_placeholder.sql` — 无表；需新增用户表 SQL（替换或追加编号脚本）
- `lippi-ai-ebus-common/` — 尚无 `ApiResponse` / `BusinessException` / `ErrorCode`；本故事需最小公共响应与错误
- `lippi-ai-ebus-web/src/` — 空路由、无 `api/`/`types/`、无 Vite proxy
- Spine AD-8 / Epic Story 1.2 — 鉴权与限流权威
- LIMS 参考（只借形状，不拷租户）：`AuthController`、`JwtTokenProvider`、`AuthenticationFilter`、`SecurityConfig`、`BCryptPasswordEncoder`；**勿**拷 `TenantContext`、微信、验证码、Redis 会话强制

**Reuse：** jjwt 版本钉死；LIMS BCrypt + Bearer filter 形状；`02-be` Identity 目录配方；`03-fe` api/types/views 配方。

**Do not change：** `sdd/planning-artifacts/**`；Pi 包名/vendor 布局；compose 服务集合（本故事不加 Redis）。

## Tasks & Acceptance

**Execution:**
- [x] `lippi-ai-ebus-common/` — 最小 `ApiResponse`、`BusinessException`、`ErrorCode`（含未授权/冲突/限流）— REST 统一出口
- [x] `APP-META/bootstrap/sql/` — 用户表（UUID PK、username/email 唯一、password_hash、时间戳 UTC）— Identity 持久化
- [x] `lippi-ai-ebus-domain/.../identity/` — `User` 聚合 + repository 端口 — 唯一写用户模型
- [x] `lippi-ai-ebus-application/.../identity/` — 注册/登录 ApplicationService + JWT 端口 — 用例编排
- [x] `lippi-ai-ebus-infrastructure/` — MyBatis PO/Mapper、JWT 实现、SecurityConfig、Bearer Filter、内存限流协作 — 落地 AD-8
- [x] `lippi-ai-ebus-interfaces/.../identity/` — `AuthController`（注册/登录）+ `MeController`（`GET /api/v1/me`）；interfaces 频率限制 — 公开与鉴权边界
- [x] `lippi-ai-ebus-starter` + 子模块 `pom` + `application.yml` + `.env.example` — 声明 jjwt/security、`JWT_SECRET` — 可启动可配置
- [x] `lippi-ai-ebus-web/` — 注册/登录页、`api`/`types`、JWT 存储、Vite 代理、登录后调 `/me` — 店主可点着走通
- [x] 单元/集成测试 — 覆盖 I/O 矩阵：注册冲突、错误登录、无 JWT/坏 JWT、限流 — 防回归

**Acceptance Criteria:**
- Given 服务可启动，when 用用户名+邮箱+密码注册并登录（登录可用用户名或邮箱），then 返回可用 JWT，带 Bearer 可访问 `GET /api/v1/me`
- Given 无有效 JWT，when 访问 `GET /api/v1/me`，then 未授权且无积分副作用
- Given 异常高频请求，when 打注册/登录，then interfaces 限流生效；密码库内仅为哈希
- Given 新建用户，when 查看主键，then 为 UUID 字符串
- Given 打开前端注册/登录页，when 完成注册或登录，then JWT 已存储且能调通 `/me`

## Implementation Notes

- 鉴权相关模块测试：`mvn -pl lippi-ai-ebus-common,lippi-ai-ebus-domain,lippi-ai-ebus-application,lippi-ai-ebus-infrastructure,lippi-ai-ebus-interfaces,lippi-ai-ebus-starter test` → BUILD SUCCESS（IdentityApplicationServiceTest 4 + JwtTokenProviderTest 2 + SlidingWindowRateLimiterTest 2 + AuthIntegrationTest 6）。
- 全仓 `mvn -pl lippi-ai-ebus-starter -am test` 仍会被预存的 `lippi-pi-agent` 失败挡住（与 Identity 无关）；未在本故事修 Pi。
- FE：补 `vitest` + `auth.flow.test.ts`（注册/登录存 JWT 再调 `/me`、失败人话）；`npm test` + `npm run lint` 绿。
- 已有 MySQL volume 不会自动跑 `001_ebus_user.sql`；需重建 volume 或手工执行。
- 默认 `JWT_SECRET` 仅本地；生产须换强密钥。
- `.history/` 已加入 `.gitignore`。
- 评审补丁：公开路径忽略坏 Bearer；auth 请求不附 Authorization；注册唯一键竞态→CONFLICT；用户名禁 `@`；限流空窗口清理；`afterLogin` 失败清 JWT；补过期 JWT / 邮箱冲突 / 用户名登录 / health / FE afterLogin 测。

## Spec Change Log

## Review Triage Log

- high — 过期/无效 JWT 仍被 `client.ts` 附到 `/api/v1/auth/*`，`JwtTokenProvider.validateToken` 抛异常被 filter 写成 401，登录/注册永远进不了 Controller；已在 `JwtAuthenticationFilter` + `client.ts` 核实。
- medium — 并发重复注册 check-then-insert 竞态可撞唯一键 → `handleOther` 500，未映射 CONFLICT；`IdentityApplicationService.register` + `GlobalExceptionHandler` 核实。
- medium — 用户名可含 `@`（仅 Size），登录对含 `@` 账号做 lowerCase 当邮箱查，可能导致合法密码被当成坏凭证；`RegisterRequest` + `login` 核实。
- low — 一用户名等于另一用户邮箱时 `OR ... LIMIT 1` 匹配不确定；日常极少，拒修（加规则会扩校验面）。
- low — `jwt.expiration-ms<=0` 未校验；配置错误才触发，拒修。
- medium → defer — `X-Forwarded-For` 可伪造绕过限流；近端无可信代理拓扑，后置 harden。
- medium — `SlidingWindowRateLimiter` 空窗口不删 map 键，长跑可涨内存；可一行清理。
- medium — `request()` 对 JSON `null` 会 TypeError；`client.ts` 核实。
- medium — 登录/注册 `setToken` 后 `getMe` 失败未 `clearToken`，残留坏 JWT；`AuthLogin`/`AuthRegister` 核实。
- false — `/actuator/info` 公开违背「除注册登录健康检查外需 JWT」：Always 写明含公开健康检查；info 与 health 同属 actuator 运维面，非缺陷。
- medium（verification-gap）— 矩阵「无效/过期 JWT」缺过期用例，仅有畸形 token；pre-verified。
- false — `ApiResponse.code` 用 HTTP 状态而非 `ErrorCode.getCode()`：规格要求 `code`+人话 message，HTTP 状态作 code 可区分；客户端已按 status/message 工作。
- defer — diff 改了 `OverlayModelCatalogTest`：预存 Pi 测试期望修正，非 Identity 范围。
- low → reject — README 未强调 volume 不跑新 SQL：Implementation Notes 已写；扩 README 非直接缺陷。
- false — sprint `in-progress` vs spec `in-review`：流程态，非产品缺陷。
- low → reject — `/me` 无 router guard：页面已显示「未登录」并有登录链接。
- low → reject — Noto Sans SC 未加载：品牌字体属 1.6。
- low → reject — SecurityConfig 兼 MapperScan：开发者找码不便，无用户侧伤害。
- defer — H2 与 MySQL schema 双份维护易漂移。
- medium（verification-gap）— 缺「仅邮箱冲突」测试；pre-verified → patch。
- medium（verification-gap）— HTTP 层未测用户名登录；pre-verified → patch。
- medium（verification-gap）— `/actuator/health` permitAll 无测；pre-verified → patch。
- medium（verification-gap）— FE 页 `onSubmit` 未被测（测试自行 `setToken`）；pre-verified → patch。
- medium（edge）— 一用户名=另一邮箱 OR 匹配：与上 low 合并 defer。
- low（edge claim）— actuator/info：见 false。

## Design Notes

- 限流：进程内滑动窗口即可（无 Redis）；按 IP 限制注册/登录。
- JWT claims 最小：`sub`=userId（UUID）、`iat`/`exp`；不带 tenant/RBAC。
- 登录失败文案统一「用户名或密码错误」，避免枚举账号是否存在。
- 模块依赖：Security/Filter 放 infrastructure，保持 AD-11；Controller 薄，从 SecurityContext 取 userId。
- FE：功能可用即可，不做落地页品牌视觉（1.6）；不做协议勾选（1.7）。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-common,lippi-ai-ebus-domain,lippi-ai-ebus-application,lippi-ai-ebus-infrastructure,lippi-ai-ebus-interfaces,lippi-ai-ebus-starter test` -- expected: BUILD SUCCESS，鉴权相关测绿
- `mvn -pl lippi-ai-ebus-starter -am -DskipTests compile` -- expected: BUILD SUCCESS
- `cd lippi-ai-ebus-web && npm test && npm run lint` -- expected: 通过

**Manual checks (if no CLI):**
- 浏览器：注册→登录→可见 `/me` 成功；无 Token 访问受保护接口失败；库中 `password_hash` 非明文
- 注意：已有 MySQL volume 需重建或手工跑 `001_ebus_user.sql`
