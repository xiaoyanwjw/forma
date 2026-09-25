---
title: '2.7 MySQL SessionStore（pi_session / pi_session_entry）'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '709e00d410e68935c307aa7300b3e443ca705e05'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-pi-agent-slim-2026-09-25/ARCHITECTURE-SPINE.md'
  - '{project-root}/sdd/context/02-be.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 生产仍靠 Pi 过渡默认 `InMemorySessionStore`（或显式 Sqlite），对话 transcript 无法跨实例/重启一致；后续澄清挂起与历史都缺可靠 Session 底座。

**Approach:** 在 ebus-infrastructure 落地 `@Primary MysqlSessionStore`（实现现有 **Pi Message 投影** `SessionStore` 端口），DDL 用 slim 表 `pi_session` / `pi_session_entry`；Adam 生产默认 MySQL，禁静默 Sqlite。  
**不是** Hermes 线性 `messages` 表，也**不是**上游 Pi SessionTree 全量（CompactionEntry / parent 边 walk / Part 3）；本仓是 AD-S6..S8 **混合子集**。

**Decisions:**
- AD-11：infrastructure 增加 `lippi-pi-agent` 依赖；适配器仍在 infrastructure；在父 Spine AD-11 / 文档注明「Pi Session（及后续 Checkpoint）适配器」例外，不把实现塞进 starter/application
- Session 模型：对外只暴露 Message 端口；`pi_session_entry` 仅为适配器私有编码（灵感来自上游 Entry，本阶段仅 `message`）

## Boundaries & Constraints

**Always:**
- 实现完整 `SessionStore`：`getOrCreate` / `append` / `load` / `setCompactAnchor` / `listRecent` / `listChildren` / `findSummary` / `updateTitle` / `save` / `find` / `delete`；行为对齐 `InMemorySessionStore` / `SqliteSessionStore`（AD-S8）
- **1 行 = 1 Message**；`payload` = 单个 Message JSON 对象（非数组、非 envelope）；`entry_type` 仅 `message`；`parent_id` 本阶段恒 null/忽略；`UNIQUE(session_id, seq)`（AD-S6）
- Entry 列下限：`id`（UUID）、`session_id`、`seq`、`entry_type`、`run_id`、`payload`、`created_at`——**勿**照搬 Sqlite 的 `role`/`content`/`payload_json` 拆列或表名 `pi_session_message`
- **Compact 协议（对齐 InMemory/Sqlite 金样，AD-S7）：**
  - 唯一真相：`pi_session.compact_anchor_seq`（默认 0）；**禁止** `entry_type=compaction`
  - `load` / `find` 投影：仅 `seq > compact_anchor_seq` 且 role≠system；锚点及之前的 entry **保留在库、不物理删除**
  - `setCompactAnchor(sessionId, seq, summary)`：`0 ≤ seq < nextSeq`；`seq == 当前锚点` → 幂等直接返回（不重复写 summary）
  - `seq` 越界（&lt;0 或 ≥ nextSeq）或会话不存在 → `IllegalArgumentException`（文案含 `out of range` / `not found`）
  - `summary != null` 且非 system：追加 **一条** `message` Entry，`run_id = "compact-" + seq + "-" + nextSeq`（计入幂等 runId 集）；system summary 忽略不写
  - 成功更新锚点时刷新 `updated_at`；不改既有 entry 的 seq
- `append` 同 `(sessionId, runId)` 若已存在任一 entry → **整批跳过**（AD-S11）；compact 产生的 `compact-*` runId 同样参与幂等集
- `listRecent` **不**在适配器内按 user 隐式过滤；`Session.Meta` 暂无 userId → `user_id` 列可空暂写 NULL（AD-S11）
- DDL **仅** `APP-META/bootstrap/sql` 下一号脚本 + 同步 `schema-h2.sql`；表名锁定 `pi_session` / `pi_session_entry`（小写）
- Adam/ebus：`MysqlSessionStore` `@Primary`；禁止生产注册 `SqliteSessionStore`；不得静默落 `{cwd}/.lippi-pi/state.db`
- 适配器在 infrastructure；**pi-agent 不依赖 MyBatis**；端口类型不暴露 Entry

**Never:**
- 实现 `MysqlCheckpointer` / `pi_graph_checkpoint` / `AgentSession.resume` 业务续跑（→ 2.8）
- 实现 `ask_human` / `human_input_required` UI（→ 2.9）
- 改 `SessionStore` 公共 API 为 Entry 端口；发明别名表名；把 Entry 提升为公共类型
- 改积分/GenerationRun/SSE 语义；拆 StateGraph；删 Skill；改 Identity JWT
- 在适配器内做 FR12「仅本人」过滤或新建应用侧会话索引（留给历史故事）
- 接线 CLI/`AgentSession.compact` 用户命令（端口 `setCompactAnchor` 已就绪即可；CLI 后置）
- compact 时物理 DELETE 历史 entry，或另造 `entry_type=compaction` / CompactionEntry 协议
- 复刻 Hermes：用 `active` 软删、或靠 `parent_session_id` 新开会话当「压缩」；`parent_session_id` 仅供 `listChildren` 会话树，不是 compact 手段
- 复刻上游 SessionTree 全量：`parent_id` 消息边 walk、多 lane、BranchSummary、Part 3 Operation SM；或把 Entry 提升为公共端口（`appendEntry`/`loadEntries`）
- 把一行做成「整批 Message 数组」或 payload envelope（破坏 1:1 粒度）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| getOrCreate 新建 | Meta.sessionId 空 | 生成 UUID；`source` 默认 `api`；空 transcript；anchor=0 | 写库失败 → 清晰异常 |
| getOrCreate 已存在 | 同 sessionId | 返回已有会话，不重置 transcript | 并发唯一冲突 → 再查返回 |
| append 首次 | 新 runId + N 条非 system | 单调 seq；每 Message 一行 entry；更新 updated_at | system 丢弃 |
| append 幂等 | 同 sessionId+runId 再 append | 整批跳过，seq/内容不变 | N/A |
| load 投影 | 有 anchor=k 与 system 行 | 仅 seq>k 且 role≠system；库内仍保留 ≤k 行 | 无会话 → 空列表（与现实现一致） |
| compact 隐藏 | append 得 seq1..2 后 `setCompactAnchor(2, summary)` 再 append 新消息 | `load` = `[summary, 新消息]`；`compactAnchorSeq=2`；旧行仍在表 | N/A |
| compact 越界 | seq≥nextSeq 或 &lt;0 | 抛 `IllegalArgumentException`（out of range）；锚点不变 | 不写 summary |
| compact 幂等 | 同 seq 再调一次（换 summary 文案） | 第二次 no-op；`load` 仍只有首次 summary，不重复 | N/A |
| compact 无 summary | summary=null 或 system | 只更新锚点；不追加 entry | N/A |
| compact 会话不存在 | 未知 sessionId | `IllegalArgumentException`（not found） | N/A |
| listRecent | limit 任意 | updated_at DESC；limit 钳 1..200；**不**按 user 过滤 | N/A |
| 生产装配 | starter 起（有 MySQL） | `@Primary` 为 MysqlSessionStore；非 Sqlite/非误抢 InMemory | Bean 缺失则退回 Pi MissingBean=InMemory（测/非完整 profile） |
| 禁 Sqlite 默认 | 无 sqlite-path | 不得创建 cwd `.lippi-pi/state.db` | N/A |

</frozen-after-approval>

## Code Map

- `lippi-pi-agent/.../session/SessionStore.java` — 冻结 Message 投影端口（对齐目标）
- `lippi-pi-agent/.../session/InMemorySessionStore.java` · `SqliteSessionStore.java` · `SqliteSessionStoreTest.java` — 行为金样（幂等/compact/load/list）
- `lippi-pi-agent/.../config/AgentConfiguration.java` — MissingBean → InMemory；有 `@Primary` Mysql 后不再抢默认
- `lippi-pi-agent/src/main/resources/pi/session/schema.sql` — Sqlite 旧表名 `pi_session_message`；**勿**照搬到 MySQL（用 `pi_session_entry`）
- `lippi-ai-ebus-infrastructure/pom.xml` — 增加 `lippi-pi-agent` 依赖（Decision A）
- `sdd/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md`（父 AD-11）— 注明 infrastructure→pi-agent 适配器例外
- `lippi-ai-ebus-infrastructure/.../persistence/repository/business/agent/GenerationRunRepositoryImpl.java` + Mapper/PO/XML — MyBatis 适配器范本
- `lippi-ai-ebus-infrastructure/.../session/MysqlSessionStore.java`（新建）— `@Primary` 实现 `SessionStore`
- `APP-META/bootstrap/sql/004_ebus_generation_run.sql` — 下一号 `005_pi_session.sql`
- `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql` — 同步测表
- Continuity from 2.6：InMemory 过渡仍合法；本故事换生产 `@Primary` MySQL；勿改 WRITE/Redis/Prompt 默认

**Reuse：** InMemory/Sqlite 语义；GenerationRun MyBatis 配方；APP-META initdb 挂载。

**Do not change：** SessionStore 方法签名；Checkpointer（2.8）；Credit/GenerationRun/SSE；Skill/Graph；Sqlite 类可保留但非生产 Bean。

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/005_pi_session.sql` + `schema-h2.sql` — `pi_session` / `pi_session_entry`（含 entry.`id` UUID、`entry_type`/`run_id`/`payload` JSON、`parent_id` 可空不用；**非** Sqlite `pi_session_message`）— 表真相
- [x] `lippi-ai-ebus-infrastructure/pom.xml` + 父 Spine AD-11 注记 — 接通 `lippi-pi-agent` 依赖（Decision A）— 可编译适配器
- [x] `…/infrastructure/.../session/`（PO/Mapper/XML + `MysqlSessionStore` `@Primary`）— 实现端口 — 生产 Session
- [x] starter/IT 或基础设施测 — 覆盖 I/O 矩阵（含 compact 隐藏/越界/幂等/无 summary、幂等 append、listRecent、装配 Primary）— 防回归
- [x] `lippi-pi-agent` README/javadoc（若仍写「过渡 InMemory」）— 标明 Adam 生产默认 MySQL Session — 叙事一致

**Acceptance Criteria:**
- Given APP-META/H2 已建表，when `getOrCreate`→`append`→`load`，then 行为符合 Message 投影端口，且 1 行 = 1 Message
- Given 同 `(sessionId, runId)` 再次 `append`，when 执行，then 整批幂等跳过
- Given 已 append 多条并 `setCompactAnchor(seq, summary)`，when `load`，then 仅见 summary（若有）与锚点之后消息；表内仍保留 ≤seq 的 entry
- Given 同 seq 再次 `setCompactAnchor`，when 执行，then 幂等、不重复 summary
- Given seq 越界或会话不存在，when `setCompactAnchor`，then 抛 `IllegalArgumentException` 且锚点不变
- Given Adam starter 正常装配，when 解析 `SessionStore`，then 得到 `@Primary MysqlSessionStore`，且无静默 Sqlite 落盘
- Given `listRecent`，when 调用，then 不因隐式 user 过滤结果

## Implementation Notes

- 落地：`MysqlSessionStore`（`@Primary`）+ MyBatis `PiSession*` PO/Mapper；DDL `005_pi_session.sql`；H2 用 CLOB 存 payload。
- IT：`MysqlSessionStoreIntegrationTest`（19 例）覆盖矩阵 + parent/title/listChildren/updateTitle/冒号拒绝/无 state.db；装配断言唯一 Primary 为 Mysql。
- Review patch：`append`/`setCompactAnchor` 对会话行 `SELECT … FOR UPDATE`；写路径拒 `sessionId` 含 `:`；README 区分 Sqlite vs Adam `user_id`。
- 验证：IT 19 绿；金样 SessionStore 测此前已绿。`mvn -pl lippi-ai-ebus-starter -am test` 全量仍可能撞 pi-agent 既有失败（与本故事无关）。
- 既有库 volume 需手工跑 `005`；initdb 只对新数据目录生效。

## Spec Change Log

## Review Triage Log

- medium — append/setCompactAnchor 并发下 `MAX(seq)+1` 与 runId check-then-act 可撞 UNIQUE / 破幂等：`MysqlSessionStore.java` append/setCompactAnchor 核实无行锁；多 Pod 正是 MySQL 目标。→ patch（同根因）
- medium — setCompactAnchor 并发竞态：同上无会话行锁。→ patch（carried 同并发根因）
- medium — Mysql IT 未断言 `parentSessionId`/`title` 落库：`MysqlSessionStoreIntegrationTest` 核实；金样有 `getOrCreate_persists_parent_and_title`。→ patch（verification-gap）
- medium — `listChildren`/`updateTitle`/`findSummary` 无 Mysql 测：IT 核实零调用；`SessionStoreListTest` 仅 InMemory/Sqlite。→ patch（verification-gap）
- medium — sessionId 含 `:` 未拒：InMemory `key()` 拒冒号；Mysql 未校验；Checkpoint 键碰撞风险。→ patch
- low — Primary IT 未断言 cwd 无 `.lippi-pi/state.db`：矩阵有「禁 Sqlite 落盘」行。→ patch（补 `Files.notExists`）
- low — README 仍写 schema 已移除 `user_id`，与 Adam MySQL 可空列矛盾：`README.md` Session 运维表核实。→ patch
- false — find→save 会丢锚点前历史：Sqlite/InMemory `save` 同样按投影重写；compact「不删」指 `setCompactAnchor`，非 compat `save`
- false — `listChildren` 无 LIMIT：金样 InMemory/Sqlite 同样无上限
- false — epic-2-context「失败启动或 InMemory」与代码不符：AgentConfiguration MissingBean→InMemory 正确；规划文略宽非本 diff 缺陷
- false — 规格任务已勾但 sprint 仍 in-progress：工作流状态非代码缺陷
- low → reject — `fromPayload` 不强制 role / toolCalls 形态：仅自家 `toPayload` 写入，畸形库行非日常路径
- low → reject — sessionId/title 超 VARCHAR 无预校验：金样也不预校验；DB 约束足够
- low → reject — H2 缺 FK：测库惯例如此；生产 DDL 有 FK
- medium（verification-gap）→ defer — 生产 `005` 从未被自动化执行（仅 H2）：仓内无 Testcontainers 先例；compose 冒烟属扩面
- low → defer — H2 TIMESTAMP/CLOB vs MySQL DATETIME/JSON 行为差：同上 defer
- low → defer — compose/bootstrap 文档未提示旧 volume 手工跑 `005`：Implementation Notes 已写；改运维文档非最小补丁

## Design Notes

- **命名别混：** 仓库里大量「Hermes」指旧 LIMS 模块投资/Prompt 键名；**会话树 Entry** 灵感来自**上游 Pi SessionTree**，不是 Hermes SQLite。本故事落地的是 slim **混合子集**：端口=现有 Message API，表形=`pi_session_entry`（仅 message）。
- **为何不照搬 Sqlite schema：** Sqlite `pi_session_message` 是过渡存储形状；生产 MySQL 跟 AD-S6/S8 锁定名与 JSON payload。行为金样仍是 InMemory/Sqlite **语义**。
- **Compact = 锚点投影，不是删历史、不是 CompactionEntry：** 金样见 `*SessionStoreTest.setCompactAnchor_*`。`compact-*` runId 避免与业务 run 冲突。
- **user_id：** 列保留可空；`Session.Meta` 尚无 userId，本故事不扩端口。FR12 ACL 由后续 application 显式处理。
- **与 2.8 边界：** 只 Session 表；图 CP 另表另故事。

## Verification

**Commands:**
- `mvn -pl lippi-ai-ebus-starter -am test` -- expected: 与 Session/Agent 相关测绿；新 IT 覆盖矩阵
- `mvn -pl lippi-pi-agent -Dtest=SqliteSessionStoreTest,InMemorySessionStoreTest test` -- expected: 金样仍绿（未改端口）

**Manual checks (if no CLI):**
- 新库 compose 冷启动后存在 `pi_session` / `pi_session_entry`；无 cwd `.lippi-pi/state.db`
- 已初始化旧 volume 需手工跑 `005` SQL（initdb 只对新数据目录生效）
