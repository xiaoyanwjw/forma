---
title: '3.1 GenerationRun、SSE 与会话必绑场景'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'da6d70d6941c9c6890e6a5858addefb7e08cb415'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-3-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiate">

## Intent

**Problem:** 2.1 已有 GenerationRun 空跑 + JWT SSE，但创建请求不强制场景；无场景的计费跑批无法挂到正确能力包，也违反 AD-15。

**Approach:** 在现有 empty-run / SSE 骨架上强制 `sceneId` 或 `sceneCode`（经 SceneCatalog 解析并落库到 run 与 `pi_session`）；缺省/未知/灰卡拒绝并给人话错误；保持 fetch+ReadableStream+JWT 与「流结束不结算、只 release」。本故事只收紧 API + DryRun，工作台发送仍禁用。

**Decisions:**
- 工作台接线：只收紧 API + DryRun 必传场景；`/scenes/ecommerce` 发送仍禁用，真实发起留给 3.3/3.4
- 会话级落库：`ebus_generation_run` 与 `pi_session` 均增加场景列；创建/复用 session 时写入解析后的 sceneId/sceneCode
- 规格超长：Keep full spec，接受略超 1600 token 的上下文风险

## Boundaries & Constraints

**Always:**
- 创建计费 `GenerationRun`（含 empty 试跑）必须带 `sceneId` 或 `sceneCode`；至少一项非空，经 SceneCatalog 解析为真实可用场景后持久化到 **run 与 `pi_session`**（AD-15）
- 积分仍全站共用；只经 CreditLedger；空跑路径 `reserveOne` + 结束 `release`，禁止 `settle`（对齐 2.1 / FR3）
- SSE 事件名闭合集不变（AD-4）；浏览器 `fetch` + `ReadableStream` + Bearer JWT，禁用原生 `EventSource`
- 失败用 `run_failed` 人话原因，不静默断流；Controller 薄 + `SecuritySupport.requireUserId()`
- 下一号 SQL `009_*.sql`（可同文件或拆文件改 run + pi_session）；同步 H2 `schema-h2.sql`

**Never:**
- 不实现选品/Listing 真结算、`artifact_ready`/`run_settled`（留给 3.4/3.6）
- 不加载 SceneCapabilityPack（3.2）；不做会话态三栏壳（3.3）；不接通工作台发送
- 不按场景拆账本；不因 SSE 流结束扣分
- 不改 CreditLedger 语义；不重写 `002`/`003`/`008` 既有表结构（仅追加新迁移）
- 不接受 `COMING_SOON` 场景发起计费 run（AD-18）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 仅 sceneCode=ecommerce | 登录用户，场景 AVAILABLE | 解析 bizId，run+pi_session 落库 scene，预占+SSE 空跑，结束 release | N/A |
| 仅 sceneId=电商 bizId | 同上 | 解析 sceneCode，落库两侧，同上 | N/A |
| 两者都给且一致 | 同上 | 通过，落库两侧字段 | N/A |
| 两者都缺 | 创建请求 | 拒绝，不建 run、不预占 | 人话：请选择场景 |
| 未知 id/code | 创建请求 | 拒绝 | 人话：场景不存在 |
| 两者冲突 / 灰卡 | id 与 code 非同一行，或 status≠AVAILABLE | 拒绝 | 人话说明 |
| 流正常/异常结束 | 已预占的空跑 | 只 release；从不 settle | release 失败仍 `run_failed` 说清 |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-domain/.../agent/model/GenerationRun.java` — 扩展 start 携带 scene；无场景字段时补齐
- `lippi-ai-ebus-domain/.../scene/model/Scene.java` + `SceneRepository` — 今日仅 `listOrdered()`；需 `findByBizId` / `findBySceneCode`
- `lippi-ai-ebus-application/.../agent/command/StartEmptyRunCommand.java` — 增 `sceneId`/`sceneCode`
- `lippi-ai-ebus-application/.../agent/service/AgentApplicationService.java` — `prepareEmptyRun` 校验解析场景后再 `reserveOne`；新建/复用 session 时写 `pi_session` 场景列
- `lippi-ai-ebus-interfaces/.../agent/AgentController.java` — `POST /api/v1/agent/runs/empty` 读场景参数
- `lippi-ai-ebus-infrastructure/.../GenerationRunPO.java` + Mapper/XML/`GenerationRunRepositoryImpl` — 读写场景列
- `pi_session` PO/Mapper/SessionStore 实现（Epic 2.7 路径）— 读写场景列；创建/更新会话时写入
- `APP-META/bootstrap/sql/004_ebus_generation_run.sql`、`005_pi_session.sql`（只读参考）→ 新 `009_*.sql` 追加列；`schema-h2.sql` 同步
- `APP-META/bootstrap/sql/008_ebus_scene.sql` — 电商 `sceneCode=ecommerce`、固定 bizId 种子
- `lippi-ai-ebus-web/src/api/business/agent/agent.ts` + `types/business/agent.ts` + `composables/agent/useAgentEmptyRun.ts` + `views/agent/AgentDryRun.vue` — 请求必带场景；保持 fetch 流
- `EcommerceWorkspacePlaceholder.vue` — **本故事不改发送行为**（仍禁用）；已有 sceneCode 供后续用
- 既有测：`AgentApplicationServiceTest`、`agent.flow.test.ts` — 补场景必填/拒绝/不 settle
- **勿改：** CreditLedger 写路径；AD-4 事件名；Identity/JWT；`EventSource` 禁令；2.1 空跑不 settle 契约

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/009_*.sql` + `schema-h2.sql` -- 为 `ebus_generation_run` 与 `pi_session` 增加 `scene_id`/`scene_code` 及必要索引 -- 落库 AD-15
- [x] `SceneRepository` + infra 实现 -- 增加按 bizId / sceneCode 查询 -- 供 run/session 创建解析
- [x] `GenerationRun` 域模型 + PO/Mapper/Repo -- 持久化场景字段 -- run 可归属场景
- [x] `pi_session` PO/Mapper/SessionStore -- 读写场景列；创建或复用会话时写入解析结果 -- 会话必绑场景
- [x] `StartEmptyRunCommand` + `AgentApplicationService.prepareEmptyRun` + `AgentController` -- 必填并校验场景；灰卡/未知/冲突拒绝；通过后再预占并写 run+session -- 堵住无场景跑批
- [x] `AgentApplicationServiceTest`（及必要接口测）-- 覆盖 I/O 矩阵：缺场景/未知/冲突/灰卡/成功；断言从不 settle；断言 session 带场景 -- 锁契约
- [x] `lippi-ai-ebus-web` agent api/types/composable/DryRun -- 传 sceneId 或 sceneCode；保持 fetch+ReadableStream+JWT；不改工作台发送 -- 前端契约对齐
- [x] FE 单测 -- 无 EventSource；缺场景不发请求或断言请求体含场景 -- 防回退

**Acceptance Criteria:**
- Given 已登录用户发起计费 empty run，when 请求缺少 sceneId 与 sceneCode，then 拒绝且不预占、不落 run，返回人话错误
- Given 携带有效 ecommerce 场景，when 以 fetch+ReadableStream+JWT 消费 SSE，then 收到约定 AD-4 事件流，且 run 与对应 `pi_session` 均已持久化场景
- Given 空跑进行中，when 仅 SSE 流结束或中断，then 只 release、从不 settle；积分账本无按场景拆分
- Given 电商工作台空态，when 本故事完成后，then 发送仍禁用（不发起计费 run）

## Implementation Notes

- 2026-09-26：`009_generation_run_pi_session_scene.sql` 为 run/`pi_session` 增加可空 `scene_id`/`scene_code`（应用层强制）；H2 同步。
- `prepareEmptyRun`：先 `resolveAvailableScene` → `bindSessionScene` → `reserveOne` → 落 run；缺省/未知/冲突/灰卡人话拒绝且不预占。
- 新端口 `PiSessionSceneRepository`：会话不存在则 insert 带场景行；已绑不同场景拒绝；无场景则 update。
- FE：`streamEmptyRun` 缺场景抛 `ApiError(400)` 不发请求；DryRun 传 `sceneCode=ecommerce`；工作台发送未改。
- 顺手修 `EcommerceWorkspacePlaceholder.test.ts` 严格空值，否则 `npm run build` 失败。
- 验证：`AgentApplicationServiceTest` 绿；`agent.flow.test.ts` 5/5；全量 reactor 仍可能被无关 `ClasspathToolBootstrapTest` 挡住。
- `009_generation_run_pi_session_scene.sql`：对 `ebus_generation_run` / `pi_session` **追加可空** `scene_id`/`scene_code`（开发期空表友好，不破坏本地 compose）；应用层在 `prepareEmptyRun` 强制 AVAILABLE 场景后再预占与落库。
- 会话场景：新增 `PiSessionSceneRepository`（与 `MysqlSessionStore` 共表）；复用 session 时若已绑不同场景则人话拒绝；尚无场景则写入。
- 前端 DryRun 固定传 `sceneCode=ecommerce`；工作台发送未改（仍禁用）。
- 评审修补：`AgentEmptyRunIntegrationTest` 传 `sceneCode=ecommerce`，断言 run/`pi_session` 场景列与种子一致，并补缺场景→400；`PiSessionSceneRepositoryImpl` 在 DuplicateKey/已有行路径写前再读，冲突抛 `IllegalStateException`，由 `bindSessionScene` 映射为人话「当前会话已绑定其他场景」。

## Spec Change Log

## Review Triage Log

- `high` — AgentEmptyRunIntegrationTest 仍无 scene 参数，成功/402 路径现为 400。verified：本地跑测 Status 400「请先选择场景」、Async not started。→ patch
- `high` — 同集成测不断言 run/pi_session 场景列。verified：SELECT 仅 biz_id/hold_id/session_id/artifact_ref/status。→ patch（与上同根因）
- `false` — MysqlSessionStore 未写场景列致文档/实现分叉。证据：本故事用 `PiSessionSceneRepository` 在 prepare 阶段先写同表；SessionStore `ensureSessionRow` 见行即跳过，不抹场景。
- `defer` — ensureBound insert 时 userId=null。证据：与既有 MysqlSessionStore 建行模式一致；本人历史归属属后续故事。
- `medium` — ensureBound 并发 DuplicateKey/已有行路径 updateScene 不复核绑定，可绕过场景冲突拒绝。verified：`PiSessionSceneRepositoryImpl` 68–72 直接 updateScene。→ patch
- `low` — GenerationRun.start 不校验空白 scene。rejected：日常仅 ApplicationService 写入且已解析 AVAILABLE；加强域守卫收益低。
- `false` — SSE/EmptyRunContext 未带回 scene。证据：AC 要求落库与事件流名，不要求 payload 含场景。
- `false` — I/O 矩阵未列「会话已绑其他场景」。证据：修矩阵=改本 build 规格，按规则拒绝；行为已有单测与 Design Notes。
- `low` — 缺「同场景复用跳过 ensureBound」与 XOR 解析单测。rejected：同场景再 ensureBound 仍写相同值；冲突/未知已覆盖。
- `false` — 预占失败可能留下 session 绑定。证据：`prepareEmptyRun` 有 `@Transactional(rollbackFor=Exception.class)`，reserve 抛错应回滚。
- `low` — FE sessionId 未 trim。rejected：空白 session 非常用路径，修复杂度大于日常伤害。
- `false` — DryRun 写死 ecommerce。证据：Intent Decisions 明确 DryRun 传场景；种子码即 ecommerce。
- `low` — PiSessionSceneRepositoryImpl 用 Instant.now 而非 Clock。rejected：注入 Clock 属额外表面，用户无感。
- `false` — DB 列可空且无跨表一致性约束。证据：Design Notes 明示可空+应用层强制。
- `medium` — 仅一侧 scene 字段非空时 hasScene=false 可被覆盖。rejected：应用始终双写；半残行非本故事可达日常路径。
- `defer` — 客户端可传他人 sessionId 无归属校验。证据：2.1 已接受任意 sessionId，非本故事引入。
- `maybe-false` — updateScene 影响 0 行致 run 有场景 session 无行。未核实并发删行可达性；若真则为 medium。→ defer
- `high` — verification-gap：集成测未观察场景落库。pre-verified。→ patch（并入集成测修补）
- `defer` — verification-gap：AgentDryRun 无组件测。disposition 同层 defer；API 契约已由 agent.flow 锁定。
## Design Notes

- 解析顺序建议：两者都有 → 分别查 Catalog，bizId 与 code 必须指向同一行；仅其一 → 查到 AVAILABLE 行后两侧都写入 run 与 `pi_session`。
- 人话错误面向用户（如「请先选择场景」「该场景尚未开放」），禁止内部黑话。
- 既有无场景历史行（若有）由迁移策略处理：开发期可允许 `009` 对空表直接加列，或先可空再应用层强制——实现时选简单且不破坏本地 compose 的方案并记入 Implementation Notes。
- 复用已有 `sessionId` 时：若该 `pi_session` 已有场景且与请求解析结果不一致 → 拒绝（人话）；若尚无场景 → 写入本次解析结果。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test` -- 相关单测绿，含场景拒绝与不 settle
- `cd lippi-ai-ebus-web && npm run lint && npm run build` -- 前端类型与构建通过
