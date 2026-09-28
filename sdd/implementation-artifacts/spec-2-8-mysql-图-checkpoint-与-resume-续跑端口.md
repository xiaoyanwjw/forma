---
title: '2.8 MySQL 图 Checkpoint 与 resume 续跑端口'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'b1dc7d8a105dc995cfa802be04e23bb70c0a168e'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 图挂起态仍落在进程内 `InMemoryCheckpointer`（或可选 Redis）；跨实例/重启丢 SUSPENDED 快照，后续 `ask_human` 无法同 run 续跑。现有 `AgentSession.resume` 仅 WRITE 批准形状（`decision`/`approved`），不能按 `toolCallId` 注入 tool result。

**Approach:** 在 ebus-infrastructure 落地 `@Primary MysqlCheckpointer`（表 `pi_graph_checkpoint`）+ 扩展 `ResumeRequest`/`DefaultAgent.resume`：支持把人工答案写成对应 `toolCallId` 的 tool result 并重跑 `tools`→继续 loop。打通挂起/恢复管道，供 2.9 使用。

**Decisions:**
- 表形跟 slim ER：`run_id` PK，一行一 run（upsert）；`graph_state` = `CheckpointCodec` 全量 JSON（复用 Redis 同编解码）；`expires_at` 对齐 `lims.pi.checkpoint.ttl-seconds`（默认 7200）
- Resume 双模式互斥：① **tool-result 路径**（`toolCallId` + 结果正文）— 写入 Messages、从挂起 `TOOL_CALLS` 移除该 call，**不**再跑该 handler；② **WRITE 路径**（既有 `decision`/`approved`）— 行为不变。二者都缺 → fail-closed FAILED
- `ResumeIdempotencyStore` 本故事不换 MySQL：仍 MissingBean→InMemory，可选 Redis；`confirmId` 建议客户端传
- Redis CP 仅 `lims.pi.checkpoint.redis.enabled=true` 时可抢 `@Primary`；Adam 默认 MySQL

## Boundaries & Constraints

**Always:**
- 实现完整 `Checkpointer`：`save` / `loadLatest` / `load` / `listByRun` / `deleteByRun`；语义对齐 InMemory/Redis（终态删 CP、SUSPENDED 保留）
- DDL 仅 `APP-META/bootstrap/sql/006_pi_graph_checkpoint.sql` + 同步 `schema-h2.sql`；表名 `pi_graph_checkpoint`；**禁止**与 `pi_session*` 混表
- 适配器在 `…/infrastructure/checkpoint/`；端口仍在 pi-agent；**pi-agent 不依赖 MyBatis**
- Adam：`MysqlCheckpointer` `@Primary`；过期 `expires_at` 视同无 CP（`loadLatest` 空）
- `confirmId` 非空时仍走现有幂等 store；空则仅 `activeRuns` 互斥
- tool-result resume：合成 `ToolResult` 进 transcript（`Message.withToolResults`），清该 `toolCallId` 挂起 call，再 `compiled.resume`；可再次 SUSPENDED

**Never:**
- 实现 `ask_human` 工具、SSE `human_input_required`、前端选项 UI、取消挂起释放预占（→ 2.9）
- 改积分 / GenerationRun / SessionStore / `pi_session*`；拆 StateGraph；删 Skill；开 WRITE 默认审批；Redis 作静默默认
- 用新 `prompt` 冒充续跑；把 CP 塞进 Session 表；在 pi-agent 引入 MyBatis
- 本故事落地 `MysqlResumeIdempotencyStore`（多 Pod 幂等仍靠可选 Redis 或后续故事）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HITL 挂起落盘 | ToolNode needsHitl，Primary=Mysql | `save` upsert 该 run；`loadLatest` 可得；`expires_at`≈now+ttl | 写库失败 → 清晰异常 |
| tool-result resume | SUSPENDED CP + `toolCallId`+正文 | Messages 含 tool result；该 call 不再执行；loop 继续 | 无 CP/过期 → FAILED「No checkpoint」 |
| WRITE resume | 同既有 + `approved=true` | 行为对齐 `CheckpointPersistenceHitlTest` | 缺 decision 且无 tool-result → FAILED |
| 终态清理 | resume→SUCCESS/FAILED/CANCELLED | `deleteByRun`；库无该 run 行 | N/A |
| 再挂起 | resume 后又 needsHitl | CP 保留/更新；幂等占位 `abandon` | N/A |
| confirm 幂等 | 同 `(runId, confirmId)` 再 resume | 返回首次终态摘要，不双跑 | IN_PROGRESS → FAILED |
| Redis 显式开 | `redis.enabled=true`+JedisPool | Redis `@Primary` 覆盖 Mysql | 未开则 Mysql Primary |
| 装配默认 | starter 有 MySQL | 解析 `Checkpointer` → MysqlCheckpointer | 无 Mysql bean 时 pi MissingBean→InMemory |

</frozen-after-approval>

## Code Map

- `lippi-pi-agent/.../graph/checkpoint/Checkpointer.java` · `Checkpoint.java` · `CheckpointCodec.java` — 端口与编解码（复用）
- `…/InMemoryCheckpointer.java` · `…/redis/RedisCheckpointer.java` · `RedisCheckpointerTest` — 行为金样（save/loadLatest/deleteByRun/TTL）
- `…/agent/CheckpointPersistenceHitlTest.java` · `ToolConfigHitlIntegrationTest` — HITL resume / 幂等 / 终态删 CP
- `…/agent/DefaultAgent.java`（`resume` / `prepare`）· `ResumeRequest.java` — **扩 tool-result 路径**；WRITE 路径保留
- `…/graph/GraphExecutor.java` · `ToolNode.java` — 挂起 save；resume merge input 后重入当前节点
- `…/config/AgentConfiguration.java` — MissingBean→InMemoryCheckpointer
- `…/config/PiCheckpointAutoConfiguration.java` — Redis 可选 Primary 门闩（勿破坏）
- Continuity 2.7：`…/infrastructure/session/MysqlSessionStore.java` + `PiSessionMapper*` — MyBatis 配方；下一号 DDL=`006`
- `APP-META/bootstrap/sql/005_pi_session.sql` — 列风格范本；新建 `006_pi_graph_checkpoint.sql`
- `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql` · `MysqlSessionStoreIntegrationTest` — H2 同步 + IT/`@Primary` 范本
- 父 Spine AD-11 — infrastructure→pi-agent 仅适配器例外（已含 Checkpoint）

**Reuse：** CheckpointCodec；Session MyBatis 布局；HITL 金样测；ttl 配置键。

**Do not change：** Checkpointer 方法签名；SessionStore；ask_human/SSE/Credit；Redis 属性契约；Skill/Graph 拓扑。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/006_pi_graph_checkpoint.sql` + `schema-h2.sql` — 建 `pi_graph_checkpoint`（run_id PK、graph_state、updated_at、expires_at；可含 checkpoint_id 列便于 load）— 表真相
- [x] `…/infrastructure/checkpoint/`（PO/Mapper/XML + `MysqlCheckpointer` `@Primary`）— 实现端口；过期视同缺失 — 生产 CP
- [x] `ResumeRequest.java` + `DefaultAgent.resume/prepare` — tool-result 与 WRITE 双模式；金样 WRITE 测仍绿 — 续跑端口
- [x] starter/IT — 覆盖矩阵（落盘/过期/终态删/Primary；与 InMemory HITL 对照的 resume 注入）— 防回归
- [x] pi-agent README/javadoc（若仍写「默认 InMemory CP」）— 标明 Adam 生产默认 MysqlCheckpointer — 叙事一致

**Acceptance Criteria:**
- Given Session 已 MySQL（2.7）且图仍为 agent⇄tools，when 装配 `@Primary MysqlCheckpointer`，then HITL 挂起落盘 `pi_graph_checkpoint`，与 `pi_session*` 分表
- Given SUSPENDED CP，when `AgentSession.resume` 带 `toolCallId`+结果，then 注入 tool result 并重跑 tools→继续 loop（可再次挂起）
- Given 终态 SUCCESS/FAILED/CANCELLED，when resume 结束，then 删除该 run CP；SUSPENDED 保留
- Given `confirmId`，when 同答重放，then 幂等不双跑（既有 store）
- Given Redis CP 未显式开启，when 解析 Checkpointer，then 为 MysqlCheckpointer 而非 Redis
- Given 本故事完成，when 审查范围，then 无 ask_human UI/SSE、无积分语义变更

## Implementation Notes

- 落地 `006_pi_graph_checkpoint.sql` + H2 同步；`MysqlCheckpointer` `@Primary`，`redis.enabled=true` 时不注册以便 Redis 抢 Primary。
- `ResumeRequest.toolCallId` + `humanInput`（结果正文）与 WRITE `decision`/`approved` 互斥；都缺或双有 → FAILED。
- tool-result：`Message.withToolResults` + 从 `TOOL_CALLS` 摘掉该 call，再 `compiled.resume`。
- `ResumeIdempotencyStore` 未换 MySQL（仍 InMemory / 可选 Redis）。
- 验证：`MysqlCheckpointerIntegrationTest` + `CheckpointPersistenceHitlTest` + `ToolConfigHitlIntegrationTest` + `RedisCheckpointerTest` 绿。
- 矩阵补测：`resume_againSuspends_keepsCheckpoint_andAbandonsConfirm`（再挂起 + confirm abandon）。
- 已还原误入本故事的 compaction 提示词改动（与 2.8 无关）。
- Review patch：未知 toolCallId fail-closed；互斥/未知 id 测；Mysql 改为 `@ConditionalOnMissingBean(RedisCheckpointer)` + `AutoConfigureAfter`；`expires_at` 与 Redis 让位 ContextRunner 测。

## Spec Change Log

## Review Triage Log

- medium — `prepareToolResult` 在 `toolCallId` 不在挂起 `TOOL_CALLS` 时仍注入 ToolResult（toolName 可 null）且不摘 call：`DefaultAgent.java` prepareToolResult/resolveToolName 核实。→ patch
- medium（verification-gap）— 双模式互斥无测：搜无 `"mutually exclusive"` 测；`CheckpointPersistenceHitlTest` 仅测都缺/单路径。→ patch
- medium — `redis.enabled=true` 且无 JedisPool 时 Mysql 因 `@ConditionalOnProperty` 不注册，Redis 亦不注册 → 静默 InMemory：`MysqlCheckpointer` 条件与 `PiCheckpointAutoConfiguration` 核实。→ patch
- medium（verification-gap）— Redis 开启时 Mysql 让位无 starter 联合测：`MysqlCheckpointerIntegrationTest` 仅默认 false；pi-agent Redis 测无 Mysql 类。→ patch（与上同根因）
- medium（verification-gap）— `save` 写 `expires_at≈now+ttl` 未断言：`ttlSeconds_defaultsTo7200` 只读 getter；`expired_*` 手工 UPDATE。→ patch
- false — `humanInput==null`→`""` 违反「+结果正文」：空串可作为选项答案正文；规格未要求非空校验
- false — 统一 diff 缺 spec 正文：审查包 mojibake/截断属 tooling，非实现缺陷
- low → reject — 无 `AgentSession.resume` 门面烟测：Session 仅转发 `agent.resume`；加测无新用户面伤害
- low → reject — Mysql IT 未逐行覆盖矩阵（WRITE/幂等/再挂起 tool-result）：同逻辑已由 `CheckpointPersistenceHitlTest` 覆盖
- low → reject — 再挂起仅 WRITE 路径测：abandon 与 tool-result 共用 resume 终态分支
- low → reject — `deleteByRun` 未包 `IllegalStateException`：日常路径罕见；扩包装非最小
- low → reject — `expires_at==null` 永不过期：`save` 必写 expires；仅脏数据
- medium → defer — 过期行只隐藏不物理删：规格要求「过期视同缺失」；清扫属运维后续
- maybe-false → defer — prepare 与 GraphExecutor.resume 间 CP 被他实例改写：同 JVM 有 `activeRuns`；多 Pod 竞态未在 diff 内证明

## Design Notes

- **为何一行一 run：** slim ER 以 `run_id` PK；HITL 只需 latest；`listByRun` 最多 1 条。Redis 历史列表可保留实现，Mysql 不必复刻多版本。
- **tool-result ≠ WRITE 批准：** ask_human 答案是工具输出，不是 APPROVE 后再执行 handler（否则会再次 suspend）。合成 ToolResult + 摘掉挂起 call。
- **幂等 store：** 本故事聚焦 CP 耐久；跨 Pod 的 confirm 幂等继续靠可选 Redis，避免本故事再开一张表。
- **与 2.9 边界：** 管道就绪即可；`ask_human` 工具定义、SSE 事件、前端选项与取消预占留给 2.9。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am -Dtest=MysqlCheckpointerIntegrationTest,CheckpointPersistenceHitlTest,ToolConfigHitlIntegrationTest,RedisCheckpointerTest test` -- expected: 相关测绿
- `mvn -pl lippi-pi-agent -Dtest=CheckpointPersistenceHitlTest,ToolConfigHitlIntegrationTest test` -- expected: WRITE HITL 金样仍绿

**Manual checks (if no CLI):**
- 新库 compose 冷启动后存在 `pi_graph_checkpoint`；与 session 表分家
- 旧 volume 需手工跑 `006`（initdb 只对新数据目录生效）
