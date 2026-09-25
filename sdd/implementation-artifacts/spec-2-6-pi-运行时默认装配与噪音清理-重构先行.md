---
title: '2.6 Pi 运行时默认装配与噪音清理（重构先行）'
type: 'refactor'
created: '2026-09-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '58b4b84f71f7b43a61fd16afe9a344d3dcced9a3'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `lippi-pi-agent` 仍把 Sqlite Session 与「有 JedisPool 就 Redis Checkpointer」当生产默认，WRITE 工具审批默认挂起，Prompt 侧残留 contribution SPI / 文档漂移；后续 2.7–2.9 容易继续踩坑。

**Approach:** 按 pi-agent-slim 收紧默认装配与 Prompt 注入面：禁静默 Sqlite、WRITE 审批默认关、Redis CP 仅显式开关、冻结三槽 allowlist 并清噪音文档/死代码；**不**落地 MySQL Session/CP，**不**拆 Graph / 删 Skill。

**Decisions:**
- 保留完整规格（Session + WRITE + Redis CP + Prompt 噪音一次做完），接受略超 1600 token 的上下文风险
- SessionStore MissingBean → 显式 `InMemorySessionStore`（非生产真相；文档标明；2.7 再换 MySQL `@Primary`）；禁止静默 Sqlite

## Boundaries & Constraints

**Always:**
- MissingBean 时注册显式 `InMemorySessionStore`；禁止静默 `SqliteSessionStore` / 落 `{cwd}/.lippi-pi/state.db`；Sqlite 仅显式 opt-in / test（AD-S8）
- WRITE 审批默认关：无显式开启时 WRITE 工具不因审批挂起；仍拦截 FORBIDDEN；保留 `resume` / Checkpointer / HITL 端口供 2.8–2.9（AD-S2）
- Redis Checkpointer 仅当显式属性（如 `lims.pi.checkpoint.redis.enabled=true`）才可 `@Primary`；有 `JedisPool` alone 不得抢默认（AD-S2/S9）
- Prompt 保持 stable/context/variable；注入键仅 AD-S10 allowlist；`SystemPromptInput`/`PromptBuilder` 为唯一 system 组装器；`ContextOverwrite` 仅追加 `before_agent_start`（AD-S4/S10）
- 以 `lippi-pi-agent` 相关测试绿为验收底线；过时 javadoc/README 与「生产默认 Sqlite / 不做 MySQL」表述一并改掉

**Never:**
- 本故事实现 `MysqlSessionStore` / `MysqlCheckpointer` / `pi_session*` / `pi_graph_checkpoint` DDL（→ 2.7–2.8）
- 将执行核改为 while-loop 或删除 StateGraph（AD-S1）
- 删除 Skill 平台、斜杠命令或 LIMS 内置 skill 资源目录（AD-S5）
- 新增 contribution/maps SPI 或 allowlist 外 system 键；节点/application 另拼 agent system
- 实现 `ask_human` / `human_input_required` SSE / resume 业务续跑（→ 2.9）
- 改 ebus 积分结算语义、GenerationRun 业务逻辑、前端 Agent 壳

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Session 无显式 Bean | 仅加载 Pi 自动配置，未设 Sqlite 路径、无外部 SessionStore | 装配 `InMemorySessionStore`；不得创建 cwd `.lippi-pi/state.db` | 禁止静默 Sqlite |
| Session 显式 Sqlite | 测试/配置给出 sqlite-path 或测试 `@Bean` | 仍可用 `SqliteSessionStore`（非生产默认叙事） | 路径非法 → 清晰失败 |
| WRITE 默认 | 注册 WRITE 工具且未开审批开关 | 工具直接执行（与 READ 同策略面），不 `needs_hitl` 审批 | FORBIDDEN 仍拒绝 |
| WRITE 显式开 | `write-approval.enabled=true`（最终属性名实现定） | WRITE 无 APPROVE 时挂起，现有 resume 路径可用 | 与今日行为一致 |
| Redis CP | 有 `JedisPool` 但未开 redis.enabled | 默认非 Redis `@Primary`（内存或其它显式 Bean） | 仅 enabled=true 时 Redis Primary |
| Prompt 非法键 | `SystemPromptInput` put allowlist 外键 | 拒绝或忽略（实现选一种并测住）；无 contribution 合并路径 | 不扩 SPI |
| 回归底线 | `mvn -pl lippi-pi-agent test` | BUILD SUCCESS | 改测对齐新默认 |

</frozen-after-approval>

## Code Map

- `lippi-pi-agent/.../config/AgentConfiguration.java` — `sessionStore()` 今日 `@ConditionalOnMissingBean` → `SqliteSessionStore`；改为 MissingBean → `InMemorySessionStore`
- `lippi-pi-agent/.../session/SqliteSessionStore.java` · `InMemorySessionStore.java` · `SessionStore.java` — 保留类；改「生产默认 Sqlite / 不做 MySQL」javadoc
- `lippi-pi-agent/.../config/PiAutoConfiguration.java` + `META-INF/spring.factories` — 入口；通常只跟测
- `lippi-pi-agent/.../extension/ToolPolicyExtension.java` + `AgentConfiguration.toolPolicyExtension` — WRITE 默认关（属性门控）
- `lippi-pi-agent/.../config/PiCheckpointAutoConfiguration.java`（或 Redis 变体）+ `PiRedisCheckpointAutoConfigurationTest` — JedisPool 不再自动 `@Primary`
- `lippi-pi-agent/.../agent/SystemPromptInput.java` · `DefaultPromptBuilder.java` — allowlist 强制；去掉 `CONTRIBUTION` / Contribution 合并
- `lippi-pi-agent/.../agent/StableContribution.java` · `ContextContribution.java` · `VolatileContribution.java`（及仅测用的 `SystemPromptCache` 若确认无主路径引用）— 删除或冻结为不可扩展死面
- `lippi-pi-agent/.../extension/ContextOverwrite.java` · `DefaultAgent.java` · `graph/node/AgentTurnNode.java` — 确认唯一 format 出口；不改 Graph 拓扑
- `lippi-pi-agent/src/test/.../PiAutoConfigurationTest.java` 等 — 断言默认 InMemory（非 Sqlite）
- `lippi-pi-agent/README.md` — 默认装配与命名漂移（ConversationLoop / BeforeAgentStartResult → 现名）
- Continuity from 2.1：业务仍只注入 `AgentSession`；勿改 GenerationRun/SSE 映射；starter 无需为本故事加 Session Bean（Pi 默认已是 InMemory）

**Reuse：** 现有 `InMemorySessionStore`、`ToolPolicyExtension` FORBIDDEN 路径、`ContextOverwrite` 三段追加、AD-S10 键名常量。

**Do not change：** StateGraph 拓扑；Skill/斜杠；ebus CreditLedger / GenerationRun 结算；2.7–2.9 存储与 ask_human。

## Tasks & Acceptance

**Execution:**
- [x] `lippi-pi-agent/.../config/AgentConfiguration.java` — MissingBean → `InMemorySessionStore`；可选仅 sqlite-path 显式才注册 Sqlite — 对齐 AD-S8
- [x] `lippi-pi-agent/.../session/*.java` + README/javadoc — 改生产叙事：默认 InMemory（过渡）、生产目标 MySQL（2.7）；Sqlite≠默认 — 去误导
- [x] `lippi-pi-agent/.../extension/ToolPolicyExtension.java` (+ 配置绑定) — WRITE 审批默认关、属性可开 — AD-S2
- [x] `lippi-pi-agent/.../config/*Checkpoint*AutoConfiguration*.java` + 对应测 — Redis CP 需显式 enabled — AD-S2/S9
- [x] `SystemPromptInput` / `DefaultPromptBuilder` / Contribution 接口 — allowlist + 删/冻 SPI — AD-S4/S10
- [x] `lippi-pi-agent` 测试（含 `PiAutoConfigurationTest`、Prompt/Policy/Checkpoint）— 覆盖 I/O 矩阵 — 防回归

**Acceptance Criteria:**
- Given 仅 Pi 自动配置且无显式 SessionStore/sqlite-path，when 应用装配，then 得到 `InMemorySessionStore`，且不在 cwd 创建 `.lippi-pi/state.db`
- Given WRITE 工具且审批开关默认，when 调用 WRITE，then 不因「待审批」挂起；FORBIDDEN 仍拒绝
- Given 存在 JedisPool 但未开 redis.enabled，when 解析 Checkpointer，then Redis 不是默认 Primary
- Given Prompt put allowlist 外键或旧 contribution 路径，when 组装 system，then 无扩键/无 SPI 合并；三槽与 `ContextOverwrite` 仍可用
- Given 本故事完成，when 审查范围，then 无 MysqlSessionStore/MysqlCheckpointer/ask_human 实现，StateGraph 与 Skill 仍在

## Implementation Notes

- 属性名落地：`lims.pi.tool.write-approval.enabled`（默认 false）；`lims.pi.checkpoint.redis.enabled`（默认未开，需 `true` 才注册 Redis `@Primary`）。
- Prompt 非法键策略：**忽略**（`SystemPromptInput.put` / Builder）；已删 `CONTRIBUTION` 与三 Contribution 接口、仅测用的 `SystemPromptCache`。
- HITL 单测助手 `PiTestBus` 显式 `writeApprovalEnabled=true`，保留现有 WRITE 审批 / resume 测路径。
- 验收命令：故事相关测（`PiAutoConfigurationTest` / `PiRedisCheckpointAutoConfigurationTest` / `ToolPolicyExtensionTest` / `DefaultPromptBuilderTest` / `PageContextPromptTest`）已绿。全模块 `mvn -pl lippi-pi-agent test` 仍有 **基线既有** 失败（与本改无关，stash 前后一致）：`ClasspathToolBootstrapTest`、`AgentTurnNodeStreamTest`×2、`ToolConfigHitlIntegrationTest.write_resume_paddedRunId…`、`DefaultAgentSessionTest`/`DefaultConversationLoopTest` 空参 NPE。
- Review patch：删空 `PageContextPromptTest`；补 write-approval=true / Redis-off ResumeIdem 断言；README Loop→Agent + 幂等冲突短句；epic-2 Decision B 仅 InMemory。

## Spec Change Log

## Review Triage Log

- low — README 仍残留「Loop 入参 / loop.ConversationLoop」：`README.md` 核实；本故事改 Runtime 命名未清完。→ patch
- low — README 删 resume 幂等冲突语义：diff 核实删了 completed/in_progress 说明；代码路径仍在。→ patch（补回短句）
- low → reject — Session 运维表收缩丢多 Pod 禁令：2.7 再写生产 MySQL 约束即可；非日常缺陷。
- false — 审批关时 WRITE+DENY 仍 allow：`ToolPolicyExtension.java:109-124` 核实；审批关则无审批决策语义，DENY 不适用；与 AD-S2「默认关」一致。
- medium — `PageContextPromptTest.loop_input_…` 空 `@Test`：`PageContextPromptTest.java:28-30` 核实空体。→ patch
- low — 测名 `allowlist_rejects` 实为忽略：`DefaultPromptBuilderTest` 核实。→ patch（改名）
- false — `StateKeys.PAGE_CONTEXT` 与 Prompt `context` 键冲突丢上下文：主路径仅常量定义、无写入 Prompt；`SystemPromptInput.CONTEXT` 已用。
- medium — `epic-2-context` 仍写「失败启动或 InMemory」：与 Decision B 矛盾。→ patch
- low → reject — 缺 application.yml 配置样例：README/javadoc 已列属性；加元数据属扩面。
- false — `resolveSqlitePath("")` 仍指 cwd db：装配层空路径不调 Sqlite；javadoc 已写明；直接 `new` 属显式用法。
- low → reject — 缺自动装配下 WRITE 真执行集成测：`ToolPolicyExtensionTest` + `PiAutoConfigurationTest` 已覆盖默认关策略面。
- low → defer — 旧 cwd `state.db` 存在时切 InMemory 无告警：过渡期可接受；加 warn 非最小必要。
- medium → defer — `redis.enabled=true` 无 JedisPool 静默 InMemory：条件注解按设计；fail-fast 需扩启动语义，非本故事最小补丁。
- false — （edge）审批关 + DENY：同上 false。
- medium（verification-gap）— Spring 未测 `write-approval.enabled=true` 绑定：pre-verified；仅构造器 true 测。→ patch
- medium（verification-gap）— without-enabled 未断言 ResumeIdempotencyStore：pre-verified。→ patch
- medium — `Checkpointer` javadoc 仍称 Redis 生产默认：`Checkpointer.java:9-10` 核实。→ patch

## Design Notes

- **为何重构先行：** 2.7 上 MySQL Session、2.8 CP、2.9 ask_human 都依赖「默认不再说谎」。先改装配与文档，避免新适配器与旧 Sqlite/Redis 默认并存。
- **WRITE vs ask_human：** 本故事只关 WRITE 审批默认；挂起/resume **端口保留**。ask_human 的工具与 SSE 在 2.9，勿在政策里删掉 HITL 基础设施。
- **Allowlist 强制：** AD-S10 已列键名但代码未拦；本故事在 `put`/Builder 落地拒绝或忽略，并删 `CONTRIBUTION` 常量与三 Contribution 接口（若无生产实现依赖）。

## Verification

**Commands:**
- `mvn -pl lippi-pi-agent test` -- expected: BUILD SUCCESS
- `mvn -pl lippi-ai-ebus-starter -am test` -- expected: 至少与 Agent/空跑相关测仍绿（InMemory 默认下 starter 应可起）

**Manual checks (if no CLI):**
- 无 sqlite-path 冷启动后，工作目录无新建 `.lippi-pi/state.db`；默认 Session 为进程内 InMemory
- README 不再声称「生产默认 Sqlite / 有 JedisPool 即 Redis Primary」
