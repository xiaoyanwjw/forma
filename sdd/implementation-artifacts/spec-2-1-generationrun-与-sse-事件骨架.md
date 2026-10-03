---
title: '2.1 GenerationRun 与 SSE 事件骨架'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '3d2ea683d463764cbb1315a3eb342257c0595f3f'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 尚无 GenerationRun 与计费生成 SSE：无法把一次生成回合与预占/会话/成果引用关联，前端也没有带 JWT 的流式契约，后续选品（2.4）与 Listing（Epic 3）无处接线。

**Approach:** 落地 AgentRuntime 最小骨架——持久化 `GenerationRun`（holdId + sessionId + artifact 引用位）、JWT 鉴权的闭合 SSE 通道、Pi `AgentSession` 事件到 AD-4 事件名的映射；空跑证明流与关联，**绝不**因流结束结算积分。前端交付 fetch + ReadableStream 客户端契约，并加登录后最小试跑入口（完整 Agent 壳留给 2.3）。

**Decisions:**
- 规格保持完整（含前后端），接受略超 1600 token 的上下文风险
- 前端：除 api/types/composables 外，增加登录后最小试跑入口（一页或按钮发起空跑并展示事件名列表；非正式 Agent 三栏壳）
- 空跑真实预占：调用 `reserveOne`，结束 `release`、禁止 `settle`；试跑期间可用额会暂时 −1

## Boundaries & Constraints

**Always:**
- SSE 事件名仅：`run_started` | `message_delta` | `tool_started` | `tool_finished` | `human_input_required` | `artifact_ready` | `run_failed` | `run_settled`（AD-4）；失败用 `run_failed`，不静默断流
- 浏览器：`fetch` + `ReadableStream` + `Authorization: Bearer`；禁止原生 `EventSource`
- 每次启动 = 新 `GenerationRun` + 新预占；可复用同一聊天 sessionId，不得复用旧 hold（AD-7）
- 结算仅在可用成果持久化之后由 application 调 `CreditApplicationService.settle`；本故事空跑只 `reserveOne` + 结束时 `release`，禁止 `settle`
- `artifact_ready` / `run_settled` 仅在真实「落库→结算」路径发出（留给 2.4）；空跑不发这两类
- AgentRuntime 拥有 Run/SSE/`AgentSession` 编排；积分只经 CreditLedger（AD-5/AD-6）
- 模型只经 `pi-ai`；业务入口只 `AgentSession`；Controller 薄 + `SecuritySupport.requireUserId()`
- ID 为 UUID 字符串；SQL 追加下一号 `004_*.sql`；H2 schema 同步

**Never:**
- 因 SSE/`AGENT_END`/流结束调用 `settle`
- 前端/工具直改积分或直连大模型；另造同义 SSE 事件名
- 实现品类模板（2.2）、Agent 三栏壳/右侧预览（2.3）、真实选品落库与扣 1 分（2.4）、成本计量字段完整落地（2.5）
- 改 CreditLedger 写语义、Identity JWT 栈、`ebus_user` / `002`/`003` 表结构

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 空跑启动 | JWT + 可用积分 ≥ 1 | `reserveOne` → 新建 GenerationRun（holdId/sessionId/artifactRef 空位）→ SSE `run_started` 后有 `message_delta`（可经 AgentSession/桩） | 未登录 → 401 |
| 积分不足 | 可用额 0 | 不创建 Run、不发流 | 人话积分不足（沿用 Credit 错误） |
| 空跑结束 | 桩回合结束且无可用成果 | `release` 预占；发 `run_failed`（标明空跑/无成果）；余额不实扣 | 释放失败记日志+人话；仍不 settle |
| 流中异常 | AgentSession/映射抛错 | 发 `run_failed`；释放 hold；不 settle | 不静默断流 |
| 事件名闭合 | 任意成功/失败路径 | 线上只出现 AD-4 七名之内；mapper/类型声明全七名 | 禁止同义别名 |
| 无 JWT SSE | 缺/坏 Authorization | 不得启动计费生成 | 401 |

</frozen-after-approval>

## Code Map

- `forma-application/.../business/credit/service/CreditApplicationService.java` — `reserveOne` / `settle` / `release`；本故事只调 reserve+release
- `forma-interfaces/.../security/SecuritySupport.java` + `JwtAuthenticationFilter` — SSE/REST 取 `userId`
- `forma-interfaces/.../web/business/credit/CreditController.java` — 薄 Controller 范本 → Agent SSE Controller
- `pi-agent/.../session/AgentSession.java` — `subscribe` / `prompt`；业务唯一入口
- `pi-agent/.../event/PiEvent` + `PiEventType` — 映射到 AD-4 事件名（勿改 Pi 模块事件枚举本身）
- `forma-domain/.../business/credit/` — 域分层范本 → 新建 `domain/business/agent/`（GenerationRun 模型+仓储端口）
- `APP-META/bootstrap/sql/003_ebus_credit_tier_change.sql` — 下一号 `004_ebus_generation_run.sql`
- `forma-starter/src/test/resources/schema-h2.sql` — 同步测表
- `forma-web/src/api/client.ts` + `http.ts` — JWT Bearer；`request()` 只解析 JSON，**不可**直接复用读 SSE
- `forma-web/src/api/business/credit/` + `types/business/credit.ts` — api/types 分家范本 → agent SSE 模块
- `sdd/context/03-fe.md` — `composables/agent/` + fetch/ReadableStream 约定
- Spine AD-4/AD-5/AD-6/AD-7；`epic-2-context.md`

**Reuse：** Credit 预占 API、JWT、AgentSession、StubModelProvider、域分层配方。

**Do not change：** CreditLedger 结算条件；Identity；Pi 包名/门面边界；Landing/Credits 主路径；2.2–2.5 业务成果。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/004_ebus_generation_run.sql` + `schema-h2.sql` — GenerationRun 表（UUID、userId、holdId、sessionId、artifactRef 可空、状态、时间）— Run 持久化
- [x] `forma-domain/.../business/agent/` — GenerationRun 模型 + 仓储端口 — AgentRuntime 真相
- [x] `forma-application/.../business/agent/` — ApplicationService：预占→建 Run→AgentSession/桩→PiEvent→AD-4 映射；结束 release、禁止 settle — 编排
- [x] `forma-infrastructure/...` — PO/Mapper/RepoImpl — 落地存储
- [x] `forma-interfaces/.../web/business/agent/` — JWT SSE 启动端点（`text/event-stream`）— 闭合事件下发
- [x] `forma-web/src/api|types|composables/.../agent/` — fetch+ReadableStream+JWT 解析 AD-4 事件（不经 `request().json()`）— 客户端契约
- [x] `forma-web` 登录后最小试跑页/入口 — 发起空跑并展示 AD-4 事件名列表（非正式 Agent 壳）
- [x] 单元/集成测 — 覆盖 I/O 矩阵（含不足、空跑 release 不 settle、事件名、无 JWT）— 防回归

**Acceptance Criteria:**
- Given 已登录且积分充足，when 启动空跑，then 新建 GenerationRun 并关联真实 holdId 与 sessionId，artifact 引用位可空，SSE 出现 `run_started` 与至少一条 `message_delta`
- Given 空跑结束且无可用成果，when 流结束，then hold 已 release、余额未因本次实扣，且未调用 settle；失败路径出现 `run_failed`
- Given 任意路径，when 观察 SSE，then 事件名均在 AD-4 七名之内；客户端用 fetch+ReadableStream+JWT，不用 EventSource
- Given 无 JWT 或积分不足，when 尝试启动，then 不创建计费 Run / 不开始成功流，并返回人话错误

## Implementation Notes

- API：`POST /api/v1/agent/runs/empty`（JWT）；预占失败先返回 JSON 业务错，成功再开 SSE。
- 空跑：`reserveOne` → Run → AgentSession → AD-4 映射 → `release` + `run_failed`；从不 `settle`；不发 `artifact_ready`/`run_settled`。
- 前端：`/agent/dry-run` + `/me` 入口；`fetch`+ReadableStream 解析事件。
- 验证：`AgentApplicationServiceTest` / `PiEventToAd4MapperTest` / `AgentEmptyRunIntegrationTest` 全绿；FE lint + agent/router 测绿。全量 `-am test` 仍可能被无关的 `pi-agent` WIP 拖红。
- 评审补丁：release 失败不再谎称已释放；SSE sink 中断仍 release+`run_failed`；试跑 AbortController；H2 索引；executor `@PreDestroy`；补 session 复用 / payload / 合成 delta 测。

## Spec Change Log

## Review Triage Log

- medium — `releaseHoldSafe` 吞异常仍走「已释放」语义：`AgentApplicationService.java` 核实；release 失败时 hold 可仍 ACTIVE，但流仍发「预占已释放」。→ patch
- medium（verification-gap）— AD-7 sessionId 复用 / 新 hold 无测：pre-verified；忽略 query `sessionId` 或复用 hold 测仍绿。→ patch
- medium（verification-gap）— `run_failed` 的 `emptyRun`/reason 无断言：pre-verified。→ patch
- medium — 合成 `message_delta` 回退无单测：`AgentApplicationService.java` 核实有回退；测仅覆盖已有 MESSAGE_UPDATE。→ patch
- medium — 集成测未断言 `run_started` 含 runId/holdId/sessionId：`runStartedData` 已写字段，测只查事件名。→ patch
- medium — 试跑页离开不 abort：`agent.ts` 已支持 `signal`，`useAgentEmptyRun` 未传 AbortController / 未 onUnmounted。→ patch
- low — H2 `schema-h2.sql` 缺 generation_run 索引：与 MySQL `004` 漂移。→ patch
- low — `CachedThreadPool` 无 `@PreDestroy`：`AgentController` 核实；骨架可接受但 redeploy 泄漏。→ patch
- medium — catch 内 `emit(run_failed)` 再抛会断流无事件：`streamEmptyRun` catch 核实。→ patch
- medium — Pi subscribe 内 sink 抛错被 `DefaultPiEventBus` 吞掉：核实 observe catch RuntimeException；客户端可丢 delta。→ patch
- maybe-false（medium 未核实）— SSE timeout 时 prompt 挂死 hold：需确认 timeout 后是否总能走到 release；应用 `AgentSession.cancel`。→ defer
- false — sprint/spec 仍显示未完成：规格已 `in-review`、任务已勾选；sprint `in-progress` 符合 build 流程。
- false — 客户端静默跳过非 AD-4 名：闭合集合下未知名本不应出现；跳过非缺陷。
- low → reject — `produces`/返回 `Object` 协商模糊：运行时 SseEmitter 可用；改签名非直接用户伤害。
- low → reject — sessionId 未做 UUID 格式校验：默认自造 UUID；非法串最多晚失败，加校验属扩面。
- false — sessionId 跨用户归属：2.1 无 ebus 会话所有权模型；Pi session 本故事不鉴权归属。
- low → reject — `markFailed` 无状态机 / public setter：本故事无 SETTLED 路径；留给 2.4。
- defer — `TOOL_EXECUTION_UPDATE` 未映射：AD-4 无对应细粒度事件；非本故事缺口。
- defer — `startEmptyRun` 自调用绕过 `@Transactional`：Controller 走代理 `prepareEmptyRun`；便利方法仅测用。
- low → reject — `markRunFailed` 行缺失时静默：正常路径必有行；补偿属扩面。
- low → reject — `EmptyRunContext` 空 holdId：仅由 prepare 构造，字段齐全。
- low → reject — `fromCode` 脏数据炸：测试/迁移写入合法枚举。

## Design Notes

- **空跑与终结事件：** `artifact_ready`/`run_settled` 绑定「落库→结算」顺序（AD-7）；空跑只演示进度类事件 + `run_failed`，避免假结算语义。类型与 mapper 仍声明全七名，供 2.4 接线。
- **Pi 映射：** `MESSAGE_UPDATE`→`message_delta`；工具起止→`tool_started`/`tool_finished`；异常/`AGENT_END` 且无成果→`run_failed`（本故事）。勿把 Pi 内部名直接暴露给浏览器。
- **API 形状（代理决定）：** 单次 `POST`（JWT）直接返回 `text/event-stream`，便于一个 fetch 读完；路径挂在 `/api/v1/agent/...` 下，具体段名实现时与 `02-be` 对齐。

## Verification

**Commands:**
- `mvn -pl forma-starter -am test` -- expected: BUILD SUCCESS，含 Agent/SSE 相关测
- `cd forma-web && npm run lint` -- expected: 无新增 lint 错误

**Manual checks (if no CLI):**
- 带 JWT 启动空跑：应看到 `run_started`/`message_delta`，结束后积分未实扣（预占已释放）
- 无 Token 调用：401；积分 0：人话不足且无 Run
