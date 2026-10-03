---
title: '2.8b MySQL ResumeIdempotencyStore'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '70de28f4b533f5f00494c5da16b11afff51ec41a'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md'
  - '{project-root}/sdd/implementation-artifacts/spec-2-8-mysql-图-checkpoint-与-resume-续跑端口.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 2.8 后图 CP 已落 MySQL，但 `ResumeIdempotencyStore` 默认仍是进程内 InMemory；多 Pod 下同一 `confirmId` 可能双跑工具。Redis 幂等仅随 Redis CP 显式开启，不是 Adam 默认。

**Approach:** 在 ebus-infrastructure 落地 `@Primary MysqlResumeIdempotencyStore`（表 `pi_resume_idempotency`），语义对齐 InMemory/Redis；与 CP/Session 分表。顺带在 `DefaultAgent.resume` 抽出 `claim` / `resolveResumeResult` / `complete` 收敛幂等与双模式样板（**不改** claim→跑→complete/abandon 状态机）。打通多实例 resume 幂等，供 2.9 使用。

**Decisions:**
- 表 PK：`(run_id, confirm_request_id)`；列含 `phase`（in_progress/completed）、结果摘要 JSON、`updated_at`、`expires_at`（TTL 默认 86400，属性仍用 `lims.pi.resume-idem.ttl-seconds`，**本故事不改 lims 前缀**）
- 装配门闩对齐 2.8：无 `RedisCheckpointer`/`RedisResumeIdempotencyStore` bean 时 Mysql 为 Primary；显式 Redis CP 开启时 Redis 幂等抢 Primary
- `claim` 用 DB 原子插入（或等价 UNIQUE 冲突处理）模拟 SET NX
- **B 路径：** `DefaultAgent` 私有助手命名：`claim`（占位门闸）→ `resolveResumeResult`（双模式）→ `complete(claimed,…)`（SUSPENDED/异常 → store.abandon；其余终态 → store.complete；未 claim → no-op）。既有 HITL 金样须仍绿

## Boundaries & Constraints

**Always:**
- 实现完整 `ResumeIdempotencyStore`：`claim` / `complete` / `abandon` / `deleteByRun`；行为对齐 `InMemoryResumeIdempotencyStore` / `RedisResumeIdempotencyStore`
- DDL 仅 `APP-META/bootstrap/sql/007_pi_resume_idempotency.sql` + 同步 `schema-h2.sql`；**禁止**与 `pi_graph_checkpoint` / `pi_session*` 混表
- 适配器在 `…/infrastructure/checkpoint/`（或邻包）；端口仍在 pi-agent；**pi-agent 不依赖 MyBatis**
- 过期行读路径视同缺失（可按 Redis 习惯在 claim 时重试）；终态 run 清理走既有 `deleteByRun` 调用点

**Never:**
- ask_human / SSE UI / 积分（→ 2.9）
- 改 `ResumeIdempotencyStore` 方法签名；改 Checkpointer / SessionStore 语义
- 批量重命名 `lims.pi.*` → 其他前缀（另故事）
- 把幂等摘要塞进 `pi_graph_checkpoint` 同行
- 砍掉 claim/complete/abandon 状态机，或改「失败一律 abandon / 再挂起也 complete」等语义

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 首次 claim | 新 (runId, confirmId) | CLAIMED；行 phase=in_progress | 写库失败 → 清晰异常 |
| 幂等重放 | 已 complete 同键再 claim | COMPLETED + 缓存摘要；不双跑 | 摘要损坏 → completed(FAILED 文案) 或等价明确失败 |
| 并发 in_progress | 他方已占位未完成 | IN_PROGRESS | N/A |
| abandon | 再挂起 | 删该键；同 confirmId 可再 claim | N/A |
| deleteByRun | 终态清理 | 该 run 下全部键删除 | N/A |
| 过期 | expires_at 已过 | 视同无键；可重新 CLAIMED | N/A |
| 装配默认 | starter 有 MySQL、未开 Redis CP | Primary = MysqlResumeIdempotencyStore | MissingBean → InMemory（测/非完整 profile） |
| Redis 显式开 | redis.enabled + JedisPool | Redis 幂等 Primary；Mysql 不抢 | 与 2.8 CP 门闩一致 |

</frozen-after-approval>

## Code Map

- `pi-agent/.../graph/checkpoint/ResumeIdempotencyStore.java` — 端口（勿改签名）
- `…/InMemoryResumeIdempotencyStore.java` · `…/redis/RedisResumeIdempotencyStore.java` · `RedisResumeIdempotencyStoreTest` — 金样（claim NX、complete 摘要、abandon、corrupt→FAILED、TTL）
- Redis 摘要 JSON：`{phase, runId, status, finalResponse, messages[{role,content,toolCallId}]}`（复用同形状）
- `…/agent/DefaultAgent.java` `resume` — `claim` / `resolveResumeResult` / `complete`（store.abandon|store.complete）；**不**调 `deleteByRun`（AC3 保留幂等键；勿改状态机语义）
- `…/config/AgentConfiguration.java` — MissingBean→InMemoryResumeIdempotencyStore
- `…/config/PiCheckpointAutoConfiguration.java` — Redis 幂等 `@Primary`（`lims.pi.checkpoint.redis.enabled` + JedisPool）
- Continuity 2.8：`…/checkpoint/MysqlCheckpointerConfiguration.java` — `@AutoConfigureAfter(PiCheckpointAutoConfiguration)` + `@ConditionalOnMissingBean(RedisCheckpointer)`；本故事对幂等镜像 `@ConditionalOnMissingBean(RedisResumeIdempotencyStore)`（可同 Configuration 类增 Bean 或邻类）
- `…/META-INF/spring.factories` — 注册 AutoConfiguration
- `APP-META/bootstrap/sql/006_pi_graph_checkpoint.sql` → 新建 `007_pi_resume_idempotency.sql`；`schema-h2.sql` 同步
- `CheckpointPersistenceHitlTest` — 双批准/双拒绝/再挂起 abandon 金样（InMemory；勿破）

**Reuse：** Redis 编解码形状；MysqlCheckpointer 装配门闩；MyBatis PO/Mapper 配方。

**Do not change：** 端口签名；claim→store.complete/abandon **语义**（仅允许 `DefaultAgent.claim`/`resolveResumeResult`/`complete` 样板收敛）；Checkpointer/Session；ask_human；`lims.pi` 前缀批量改名。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/007_pi_resume_idempotency.sql` + `schema-h2.sql` — 建 `pi_resume_idempotency` — 表真相
- [x] `…/infrastructure/checkpoint/`（PO/Mapper/XML + `MysqlResumeIdempotencyStore` `@Primary`，门闩对齐 2.8）— 实现端口 — 多 Pod 幂等
- [x] `DefaultAgent.java` — 抽出 `claim` / `resolveResumeResult` / `complete`，替换散落的 store complete/abandon — 样板收敛、语义不变
- [x] starter/IT — 覆盖矩阵（claim/complete/重放/in_progress/abandon/过期/Primary/Redis 让位）— 防回归
- [x] `pi-agent` README 一句 — Adam 生产默认 MySQL resume 幂等 — 叙事一致

**Acceptance Criteria:**
- Given 2.8 CP 已 MySQL，when 装配 MysqlResumeIdempotencyStore，then 同 `(runId, confirmId)` 重放不双跑
- Given Redis CP 未开，when 解析 ResumeIdempotencyStore，then 为 Mysql 实现
- Given Redis CP 显式开启且有 JedisPool，when 解析，then Redis 幂等为 Primary、Mysql 不抢
- Given `DefaultAgent.complete` 落地后，when 跑 `CheckpointPersistenceHitlTest`（含双批准/再挂起 abandon），then 行为与重构前一致
- Given 本故事完成，when 审查范围，then 无 ask_human UI、无 lims.pi 前缀大改名、未改幂等状态机语义

## Implementation Notes

- 落地 `007_pi_resume_idempotency.sql` + H2 同步；`MysqlResumeIdempotencyStore` `@Primary`，门闩 `@ConditionalOnMissingBean(RedisResumeIdempotencyStore)` + `AutoConfigureAfter(PiCheckpointAutoConfiguration)`。
- `claim` 用 INSERT + UNIQUE 冲突模拟 SET NX；过期行删后重试一次；`complete` upsert 摘要 JSON（对齐 Redis 形状）；`abandon`/`deleteByRun` 删行。
- `PiCheckpointAutoConfiguration` Redis 幂等 Bean 返回类型改为 `RedisResumeIdempotencyStore`（与 RedisCheckpointer 一致，便于 MissingBean 门闩）。
- `DefaultAgent.complete`：未 claim no-op；SUSPENDED/异常(null) → store.abandon；其余终态 → store.complete。配套 `claim` / `resolveResumeResult`。
- 验证命令见 Verification。
- Review patch：过期删带 `expires_at` 谓词；读删路径统一 IllegalStateException；补 TTL 与 complete(null)→abandon 测；sprint 2-8 改回 review。

## Spec Change Log

- 助手更名：`finishClaim`→`complete`，`beginClaim`→`claim`，`resolveResumeModes`→`resolveResumeResult`，`ClaimGate`→`Claim`，`ResumeModes`→`ResumeResult`；语义不变。避免已知坏状态：文档仍写旧名导致实现/审查对不上。KEEP：幂等状态机与 Mysql 表形不变。

## Review Triage Log

- medium — `resolveConflict` 对过期行 `deleteByKey` 无 `expires_at` 谓词，并发 `complete` 刷新 TTL 后可被误删再 claim：`MysqlResumeIdempotencyStore.java` 核实。→ patch
- medium — `abandon`/`deleteByRun`/`resolveConflict` 读删未包 `DataAccessException`：同文件核实。→ patch
- medium（verification-gap）— claim/complete 未断言库内 `expires_at≈now+ttl`：IT 仅 getter + 手工 UPDATE。→ patch
- medium（verification-gap）— `finishClaim(null)` 异常→abandon 无 confirmId 测：HITL 仅 SUSPENDED→abandon。→ patch
- medium — sprint 把 `2-8-…` 从 review 改成 done 夹在 2.8b diff：与本故事无关误改。→ patch（改回 review）
- false — finishClaim 使 SUSPENDED 时 abandon 抛错变 FAILED：旧代码同路径无 try；非本 diff 引入
- false — Redis 门闩未测真 PiCheckpointAutoConfiguration：`ConditionTest` 按 2.8 同款 MissingBean 桩
- false — HITL 未证明 finishClaim：`CheckpointPersistenceHitlTest` 已跑绿覆盖同一语义
- false — 仅缺 RedisCheckpointer 仍注册 Mysql 幂等：PiCheckpoint 同开同关两 bean
- low → reject — 无双线程并发 claim 测：UNIQUE 已覆盖；加测过重
- low → reject — 分表 COUNT 断言弱：结构性分表已由 DDL 保证
- low → reject — PK 前缀已够 deleteByRun，多余 run_id 索引：cosmetic
- low → reject — `isBefore` 边界 / H2 CLOB vs JSON：日常极少；双 DDL 既有模式
- medium → defer — 无过期扫表清扫：规格仅要求读路径视同缺失（同 2.8 CP）
- low → defer — TTL 过期后同 confirmId 可再 CLAIMED 的客户端说明：运维/文档面

## Design Notes

- **为何独立故事：** 2.8 聚焦 CP + tool-result；幂等表是多 Pod 正确性补强。
- **`complete`（路径 B）：** 只消重复 `if (claimed) store.complete/abandon`；SUSPENDED 与 catch 仍 abandon，fail-closed/成功仍 complete。与 `claim` / `resolveResumeResult` 配套。
- **`deleteByRun`：** 端口必实现且 IT 覆盖；`DefaultAgent` 终态只删 CP、保留幂等键（与现网一致）。
- **装配：** 镜像 `MysqlCheckpointerConfiguration`；条件用 `RedisResumeIdempotencyStore`。
- **`lims.pi` 前缀：** 只消费既有 TTL 键，不改名。

## Verification

**Commands:**
- `mvn -pl forma-starter -am -DfailIfNoTests=false -Dtest=MysqlResumeIdempotencyStoreIntegrationTest,MysqlResumeIdempotencyStoreConditionTest,CheckpointPersistenceHitlTest,RedisResumeIdempotencyStoreTest test` -- expected: 相关测绿

**Manual checks (if no CLI):**
- 新库存在 `pi_resume_idempotency`；旧 volume 手工跑 `007`
