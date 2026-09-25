# lippi-ai-lims-pi-agent

LIMS **Pi Agent** Maven 模块：应用层新 AI 助手用例的**唯一**编排入口（对齐 pi-mono 的 agent 层）。

## 模块分层（对齐 pi-mono）

| Maven artifact | 包根 | 职责 |
| --- | --- | --- |
| `lippi-ai-lims-pi-ai` | `com.xmut.lims.pi.ai.*` | Message / ModelProvider / ToolCall 协议类型 |
| **`lippi-ai-lims-pi-agent`**（本模块） | `com.xmut.lims.pi.agent.*` | AgentSession、Agent、StateGraph、Tool/Skill/Event |
| `lippi-ai-lims-pi-cli` | `com.xmut.lims.pi.cli.*` | 开发者 REPL；只依赖 agent |

依赖方向：`cli → agent → ai`（`ai` 禁止依赖 `agent`）。业务方依赖 **`lippi-ai-lims-pi-agent`** 即可（传递引入 `pi-ai`）。

## 对齐目标（Story 51-12）

- **产品秩序**：对齐开源 [pi agent](https://github.com/badlogic/pi-mono) 的 **AgentSession / Session / Runtime Agent** 分离。
- **执行层**：继续 **自研 StateGraph**（不引入第三方 LangGraph）。
- **System prompt**：保留 **Stable / Context / Volatile** 三段（`PromptBuilder`）；Session **禁止**手拼厂商 HTTP / 第二套 system。
- 历史投资（原 Hermes 模块 51-1～51-9）保留；现行命名与门面以 Pi / `AgentSession` 为准。  
  真相源：`_bmad-output/planning-artifacts/architecture/23-pi-agent-runtime.md`。

```text
CALLERS（OCR / Gateway / 工作台 / CLI）
     ↓  AgentSession.subscribe / prompt / compact / resume / cancel
PRODUCT   AgentSession + SessionStore（≠ Checkpoint）+ PiEventBus
     ↓  command? → 展开 /prompt|/skill → before_agent_start
RUNTIME   DefaultAgent → StateGraph（agent ⇄ tools）；Node 只 Emitter.emit
          ※ tools 内：tool_execution_start → before_tool_call → execute → after_tool_call → tool_execution_end
          ※ WRITE 审批默认关；显式开启时 before_tool_call 可挂起，resume 重跑同一节点
          ※ 返回前 emit(agent_end)（旁路；51-11 指标仍后续）
```

| 层 | 做什么 | 不做什么 |
| --- | --- | --- |
| AgentSession | subscribe / prompt / resume / cancel；订 Session；持有 PiEventBus | 拼 Prompt、选模型、直连 HTTP |
| Agent + PromptBuilder | 三段 system、模型/工具编排 | 对外门面 |
| SessionStore | 会话投影 / append transcript（过渡默认 **InMemorySessionStore**；Sqlite 仅显式 `sqlite-path`；生产目标 MySQL → 2.7） | 静默落 `{cwd}/.lippi-pi/state.db`；与 `pi:checkpoint:` 混存 |
| StateGraph [Lippi] | 可取消超步 / HITL interrupt | 认知决策 |

### 命名速查

| 概念 | 本仓库 |
| --- | --- |
| 产品门面 | **`AgentSession`**（唯一对外） |
| Runtime Agent | `com.xmut.lims.pi.agent.Agent`（内部；**不**注册业务 Bean） |
| `prompt` | `AgentSession.prompt` → 驱动一轮 → `TurnResult`（过程只 `emit`） |
| `subscribe` | `AgentSession.subscribe(Consumer<PiEvent>)` → bus `observe`；长驻；CLI 打印 `MESSAGE_UPDATE` |
| `PiEventBus` | Session 持有；`observe` 只读 / `on` 同步归约 / `emit` 唯一出口 |
| `interrupt` / cancel | `AgentSession.cancel` |
| HITL resume | `AgentSession.resume`（[LIMS]；≠ Session `/resume`） |
| `prompt_builder` 三段 | `PromptBuilder` |
| Graph | `StateGraph` [Lippi] |

## 唯一门面

```text
com.xmut.lims.pi.agent.session.AgentSession
```

| 方法 | 用途 | 标签 |
| --- | --- | --- |
| `subscribe(Consumer<PiEvent>)` | 长驻观察生命周期事件（`MESSAGE_UPDATE` 等）；返回取消订阅 | 对齐 pi |
| `prompt(PromptRequest)` | **主路径**：hydrate 后跑 loop，返回 `TurnResult`；过程只经 bus `emit` | 对齐 pi |
| `compact(CompactRequest)` | 压缩会话（M1 可桩） | |
| `resume(PromptRequest)` | **@Deprecated** 等同 `prompt`（请直接用 prompt + sessionId） | |
| `cancel(String runId, String reason)` | 下一超步边界停止 | ≈ interrupt |
| `resume(ResumeRequest)` | HITL / Graph checkpoint 恢复（同样经 bus 发事件） | **[LIMS]** |

Spring：`@Autowired AgentSession`（`@ConditionalOnMissingBean(AgentSession.class)`）。  
**禁止**：业务注入 `DefaultAgent` / `pi.agent.Agent` 作门面。

Agent 入参是已绑定的 **`TurnInput`**（完整 chat 轴 `messages`）；直测用 `TurnInput.withUser("hi")`。

## 控制流（Story 51-2 / 51-3 / 51-3b / 51-4 / 51-12 / 51-13 / 51-14 / 51-16）

`AgentSession.prompt` 为主路径（**已删除** `stream(PromptRequest, StreamObserver)`）。时序（锁死）：

```text
SessionStore.getOrCreate
→ bus emit COMMAND（已登记斜杠命令可短路，不入图、不 append）
→ 展开 /prompt 或 /skill → user 文本
→ SessionStore.load hydrate（省略 sessionId = 新会话，无旧历史）
→ merge：history + 本轮 user（完整 chat 轴）
→ bus emit BEFORE_AGENT_START（增量 → SystemPromptInput 三段 map）
→ DefaultAgent.run(TurnInput, Emitter)   ← 已绑定；不再二次追加 user
→ bus emit AGENT_END
→ status==OK 才 appendMessages（相对进图快照的后缀差集；禁止整表 save 当增量）
```

`SessionTranscript`：merge / append delta / 本轮 user 解析（纯函数，无 I/O）。
`resume`：**不**跑 command / 斜杠展开 / hydrate / `before_agent_start`；仍直接 `agent.resume`；返回前可发 `agent_end`（本切片 OK 后可不落 Session）。

`DefaultAgent.run` → **[Lippi]** `CompiledGraph.invoke`：

```text
START → agent ⇄ tools → agent → END
              ↑ 仅 WRITE 审批显式开启且未批准时本节点挂起，resume 重跑
```

- **Memory**：暂未接入（原 51-7 脚手架已收掉；有业务记忆需求再加）
- `agent`：读 `MESSAGES` + `SYSTEM_PROMPT` → `request=sanitize([system]+messages)` → `ModelProvider.complete`
- `tools`：对齐开源 pi，**逐 call** 经 bus：`tool_execution_start` → `before_tool_call` → execute → `after_tool_call` → `tool_execution_end`
  - **before_tool_call**：配置期 `bus.on`（含必装的 `ToolPolicyExtension`）；FORBIDDEN/deny 失败结果直接写入 `MESSAGES`；Adam 默认 WRITE 审批关（`lims.pi.tool.write-approval.enabled=false`）；开启后未批准 WRITE → 本节点申请挂起（`__interrupt__`），resume 后**重跑 `tools`** 消费 `TOOL_APPROVAL`
  - **execute**：只执行策略已放行的调用；成功/失败结果立刻写入 `MESSAGES`
  - **after_tool_call**：每条 Tool 结果（含失败）可归约改写
  - `TOOL_RESULTS` 仅作短暂暂存，执行后清空（不再回灌 AgentTurn）
- 安全阀：`IterationBudget` → maxSupersteps / overallTimeout（**分段**：resume 从 now 重算，HITL 等待不占执行预算）
- Checkpoint：**[Lippi HITL]** Graph Checkpoint（≠ Memory/SessionStore；≠ 上游文件 CheckpointManager；`Agent.resume` ≠ Session `/resume`）
  - **本模块过渡默认**：`InMemoryCheckpointer`。Redis 仅当 `lims.pi.checkpoint.redis.enabled=true` **且** 有 `JedisPool` 时由 `PiCheckpointAutoConfiguration` `@Primary` 注册（有池 alone 不得抢默认）
  - **Adam 生产目标**：MySQL `pi_graph_checkpoint`（Story 2.8）；≠ Session 表
  - **TTL**：`lims.pi.checkpoint.ttl-seconds`（默认 7200=2h，应对 HITL 等待窗口）；过期后 resume → 明确失败（无 CP）
  - 仅 interrupt 落盘；终态 SUCCESS/FAILED/CANCELLED → `deleteByRun`；**SUSPENDED 保留**
  - resume 幂等：非空 `ResumeRequest.confirmRequestId` → `SET NX EX`（`pi:resume-idem:…`）；空则非幂等（仍受 `activeRuns` 互斥）
  - 幂等冲突：同 `(runId, confirmRequestId)` 若已 `completed` → 短路返回缓存摘要；若仍 `in_progress` → `FAILED`（客户端应退避或换新 confirmId）
  - 键仅 `runId`（+ 可选 `confirmRequestId`）；**已移除** `tenantId` / `userId`

**批次语义：** WRITE 审批开启时，同一超步任一 WRITE 未批准 → **整批挂起**（同批 READ 亦不先执行）。审批默认关时 WRITE 与 READ 同策略面直接执行。

**禁止**：第二套 Planner while；禁止 `chat`+`chatWithTools` 双方法；禁止节点内私自再拼一份 system。

## L2 Resource + Extension（Story 51-14）✅

对齐开源 pi 的 ResourceLoader + Extension 心智；Java 类型避开 Spring `ResourceLoader` 撞名。

| 类型 | 说明 |
| ---- | ---- |
| `PiResourceLoader` / `DefaultPiResourceLoader` | 冻结 Agent Context；委托既有 Skill/Tool bootstrap；扫 `classpath*:prompts/*.md` |
| `PromptTemplate` | `name` / `description` / `body`（frontmatter 只读 description，无 YAML 依赖） |
| `AgentResourceSnapshot` | skills 目录、prompt 表、extension 名 |
| `PiEventBus` | Session 持有的生命周期总线：`observe` 只读、`on` 同步归约、`emit` 唯一出口 |
| `PiExtensionRegistrar` | 启动期 `register(bus)`；**不再**对外方法式 `beforeAgentStart()` 扇出 |
| `ExtensionRunner` | 实现 `PiExtensionRegistrar`；把既有扩展登记到 bus |
| `PiExtension` | Java 8 默认方法；Spring Bean 或 `META-INF/services` SPI；Policy 也是其中一员；**禁止** jiti / 执行 `.ts` |

| 钩子 | 时机 | 行为 |
| ---- | ---- | ---- |
| `command` | `prompt` 最前 | 已登记斜杠命令短路，不入图 |
| `before_agent_start` | 展开后、入图前 | 增量按段进 `SystemPromptInput` 的 stable / context / variable |
| `tool_call` | `tools` 节点执行 Tool **之前**（整批） | `ToolCallHook.beforeToolCall`；策略键不可覆盖 |
| `tool_result` | `tools` 节点每条 Tool **执行后** | `ToolCallHook.afterToolCall`；旁路 |
| `onToolAudit` | 闸门决策 / 真正执行 | FORBIDDEN、SUSPEND、APPROVE、DENY、EXECUTE；旁路。实现 `PiExtension.onToolAudit` 即可收集 |
| `agent_end` | `prompt` / `resume` 返回前 | 旁路；抛错只打日志。**不**落 51-11 指标 |

斜杠三条路：`command` 优先于模板；`/echo args` 展开进 user；`/skill:id` 正文进 user 且走 EXPLICIT Selector；未知 `/foo` 当普通 user。OCR 多模态且 text 不是斜杠 → 跳过展开。

启动扫 `classpath*:prompts/*.md`；`reload()` 始终可重扫，不走配置。

**本故事不做：** SkillRouter / `lims.nav`（51-10）；token/费用指标（51-11）。Session hydrate 见 **51-16**；Sqlite opt-in 见 **51-17**（生产目标 MySQL Session → Story 2.7）。

## L2 PromptBuilder（Hermes naming）✅

对齐上游 [`prompt_builder` / Prompt Assembly](https://hermes-agent.nousresearch.com/docs/developer-guide/prompt-assembly)。

### 可缓存 system（一条 `role=system`）

| 段 | 内容 | `SystemPromptInput` map 键 |
| ---- | ---- | ------------------- |
| **stable** | soul · skills · tools · **core** | `soul` · `skills` · `tools` · **`core`** |
| **context** | AGENTS.md · HERMES.md · page/context | `agents` · `hermes` · **`context`** |
| **variable** | recall(MEMORY.md) · USER.md · **before_agent_start** | `memory` · `user` · **`before_agent_start`** |

`SystemPromptInput.format()` = join 三个 map（跳过空白）；**入图前**由 `DefaultAgent.input` 写成 `SYSTEM_PROMPT`。图内 `AgentTurnNode` 只读这一条。

`ContextModifier{overwrite, append}` 各含 `stable/context/variable`；应用到 `SystemPromptInput` 时先整段覆盖再追加，**不**和 page/`context` 混成一坨。

> **命名纠正（FR23）：** Core → **Stable**。`memory` **仅** Recall → variable。勿把 Core 折叠进 `memory`。

Stable 拼接序：map 插入序（soul 缺省时补 `DEFAULT_SOUL`）→ 整段 truncate。

注入键 **仅 AD-S10 allowlist**（上表 10 键）；`put` / Builder 对非法键（含旧 `contribution`）忽略。**已删除** Contribution SPI。

### Chat 轴

| | 内容 |
| --- | --- |
| **messages** | 持久 transcript（不含 system）；本轮 user / tool / human **直接写入** |
| **request** | `sanitize([system] + messages)` → `complete` |

| 类型 | 说明 |
| ---- | ---- |
| `PromptBuilder` | `stable` / `system` / 消息 `format` / `sanitize` |
| `SystemPromptInput` | 三个有序 map + allowlist + `format()` / `apply(ContextModifier)` |
| `SystemPromptStable` | 三段袋（`variable` ≈ upstream `volatile`） |
| `SYSTEM_PROMPT` | 入图前 format 好的 system 全文；节点只读此键 |

**Agent 入图：**

```text
MESSAGES       = conversationHistory（剥离 system；与本轮 user 去重）+ 本轮 user
SYSTEM_PROMPT  = SystemPromptInput.format(stable/context/variable maps)
AVAILABLE_TOOLS / MODEL_USE_CASE = TurnBinder（API 轴，不是 system 文本）
```

`AgentTurnNode`：`MESSAGES → SYSTEM_PROMPT → compress(messages) → request → complete → persist`。

`resume`：人工说明写入 `MESSAGES`（同时短暂留在 `HUMAN_INPUT` 供 deny 原因）。

## L2 ModelProvider（Story 51-3）✅

| 类型                                   | 说明                                                             |
| -------------------------------------- | ---------------------------------------------------------------- |
| `ModelProvider.complete(ModelRequest)` | **[Lippi 端口]**；语义对齐一次 completions；`tools` 可选         |
| `ModelRequest` / `ModelResponse`       | hermes 自有 DTO；`toolCalls` 永不 null                           |
| `ModelCatalog` + `ModelDescriptor`     | `useCase` → 模型；`supportsNativeToolCalling`；`ModelModality`   |
| `ProtocolRoutingModelProvider`         | native → 结构化 tool_calls；false → ReAct 文本回落               |
| Decorator 链                           | `RateLimited → Cached → Audited → Retried → 底层`（标 [Lippi]）  |
| 反向端口                               | `ModelRateLimit` / `ModelCache` / `ModelCallRecorder` + **NOOP** |
| `StubModelProvider`                    | 无 API Key 默认回显；`@ConditionalOnMissingBean`                 |

```java
String system = systemPromptCache.getOrBuild(promptBuilder, input);
List<Message> request = promptBuilder.sanitize(Message.request(system, messages));
ModelResponse response = modelProvider.complete(ModelRequest.builder()
        .messages(request)
        .tools(toolSchemas)   // null/empty → 不带 tools
        .useCase("pi.default")
        .sessionId(sessionId) // 限流键；空白时装饰器用 "_"
        .build());
```

**不做（后续故事）：** 真 DashScope HTTP；Gateway session hygiene（52-x）。

## L2 ContextCompressor（Story 51-8）✅

对齐上游 [Context Compression](https://hermes-agent.nousresearch.com/docs/developer-guide/context-compression-and-caching) 的「超阈压缩、保护 tail」语义；类型名 **`ContextCompressor`**（FR22）。

| 压缩对象 | 非对象 |
| -------- | ------ |
| Context 段（`agents` / `hermes`）超 `contextMaxChars` → head + `...[truncated]` | Stable（soul / skills / tools / **core**） |
| 中间 history → 一条 `[context_summary]` user 消息 | Volatile 本轮 user/tool（靠 invalidate+rebuild） |
| 可选 Phase0：尾外过长 tool 结果一行摘要 | Graph Checkpoint / Contribution SPI |

| 配置键（`CompressionConfig`） | 默认 | 含义 |
| ----------------------------- | ---- | ---- |
| `enabled` | `true` | false → `ContextCompressor.NOOP` |
| `maxPromptChars` | `48000` | system+messages 总字符超阈则压 history（≈12k tokens @ chars/4） |
| `protectLastK` | `8` | 尾部保留消息条数 |
| `contextMaxChars` | `4000` | Context 段硬上限 |
| `summaryPrefix` | `[context_summary]\n` | 中间折叠前缀 |

**注入顺序（AgentTurn）：** append → `system=cache.getOrBuild` → `compress` →（Context 变则 `invalidate`+rebuild）→ `format` → `complete`；压缩后 messages **写回** `MESSAGES`。

**失败降级：** 压缩抛错 / LLM 摘要失败 → 打日志，继续用未压缩 messages 完成 turn（**不** FAILED）。

**LLM 摘要（可选）：** useCase = `pi.compression`（≠ `pi.default`）；Catalog 未配或调用失败 → deterministic digest。本模块默认 deterministic 即可用。

**≠ Graph Checkpoint ≠ SessionStore。** 字符估 token 非精确 tokenizer。

## L3 SkillConfig（Story 51-9）✅

版本化 Skill 资产平台（FR22 / FR24）。包：`com.xmut.lims.pi.agent.skill`。

| 类型 | 说明 |
| ---- | ---- |
| `SkillManifest` | 不可变值对象：id / version / prompt / toolWhitelist / maxToolLevel / graphTopology |
| `SkillConfig` / `InMemorySkillConfig` | register / get / resolve / **manifests** / unregister / replace / **sealBootstrap** |
| `ClasspathSkillBootstrap` | 启动扫 `classpath*:skills/*.skill.json` → `registerBootstrap`（**默认开启**；内置含 `certificate.ocr`） |
| `ClasspathToolBootstrap` | 启动扫 `classpath*:tools/*.tool.json` → Manifest |
| `handlerClass`（JSON） | 可选 FQCN；`ToolHandlerAutoBinder` 自动 createBean 并按 id 绑定 |
| `SkillSelector` / `ActiveSkill` | 本轮 EXPLICIT 选择（`skillId` > `domain`） |
| `SkillValidationException` | 缺字段、非法值、重复 id+version、生产运行时突变拒绝 |
| `SkillConfigProperties` | `pi.skills.allow-runtime-mutation`（**默认 false**） |
| `TurnBinder` / `TurnBindings` | **极薄投影**：`ToolConfig` + `SkillConfig` + `ActiveSkill` → Stable + API |
| `SkillGraphTopology` | `TOOL_LOOP` \| `SIMPLE_AGENT_END`（Manifest 声明偏好；Agent **不**按此换图） |

**与 Tool 对称命名：**

| Skill | Tool |
| ----- | ---- |
| `SkillConfig` | `ToolConfig` |
| `SkillManifest` | `ToolManifest`（`text` → Stable；`schema` → API） |
| `ClasspathSkillBootstrap` | `ClasspathToolBootstrap`（Manifest only；Handler 代码合并） |
| `get` / `resolve` / `manifests` | `get` / `resolve` / `manifests` |
| `skillsText` → `SKILLS` | `toolsText` → `TOOLS` |
| `availableSkills` | `availableTools` |

**API 要点：**

- `resolve(id)`：取该 id **最近一次成功入册**更新的「当前」指针（非 semver 比较）
- `get(id, version)`：精确版本
- 同 `id`+`version` 默认 **拒绝覆盖**；显式 `replace` 仅 mutation=true
- 启动装载：`ClasspathSkillBootstrap` → `registerBootstrap` → **`sealBootstrap()`**（关闭启动窗；之后 bootstrap 拒绝）
- Tool：扫 `*.tool.json`；有 `handlerClass` → 自动装配 Handler；无则声明型（仅 schema）

**加载路径（Scanner + 极薄 bind）：**

1. **Boot Skill：** 扫 `classpath*:skills/*.skill.json`（含 `certificate.ocr`）→ SkillConfig → seal  
2. **Boot Tool：** 扫 `classpath*:tools/*.tool.json` → Manifest；`handlerClass` 自动绑 Handler  
3. **Select：** `SkillSelector.select` → `ActiveSkill`（EXPLICIT `skillId`；AUTO 属 51-10）  
4. **Bind：** `TurnBinder.bind(toolConfig, skillConfig, activeSkill)`  

投影规则：

- `skillsText`：目录摘要（id/displayName/description）+「用 `read_skill` 拉正文」指引；**不是** Skill 全文
- `availableSkills`：有 Active → 当前 skill；无 Active → `skillConfig.manifests()` 目录（同时进 `skillsText`）
- `toolsText` / `availableTools`：Active 时按 `whitelist` **同步裁剪**（如 `certificate.ocr` → `["read_skill"]`）
- Skill 正文：`promptRef`（优先）或内联 `skillsPrompt`，经工具 `read_skill` 返回（OCR 正确性不依赖此路径）

**生产禁自改（≠ 上游）：** 上游 Hermes 允许 agent 改 `~/.hermes/skills/`；本仓库 **FR24 更严**——默认禁止运行时 register/replace/unregister，bootstrap 窗在 Bean 装配后封印，**禁止**「模型 Tool 写 Skill 文件并热更新生产 Registry」。`read_skill` 只读，且限制为当前 ActiveSkill。测试可 `allow-runtime-mutation=true` 或在 seal 前 bootstrap。

**内置 Skill：** `certificate.ocr`、`test-standard-schema`（`promptRef` → md 为**唯一规则源**，`toolWhitelist=["read_skill"]`）；**不**由 application 声明 SkillManifest。业务入口传 `skillId` + 薄 user（application `v1.txt` 仅短指令 + 本轮数据），要求先 `read_skill`。`lims.nav` / **SkillRouter** 属 **51-10**。

## 垂直切片：证书 OCR（Story 51-5）✅

| 项 | 说明 |
| -- | ---- |
| Skill | `skills/certificate-ocr.skill.json` + `certificate-ocr.md`（**规则 SSOT**） |
| 加载 | Stable 目录摘要；Skill 全文经 `read_skill`；user 薄指令来自 `ai-prompts/certificate-ocr/v1.txt` |
| 图 | Agent **唯一** Tool-loop；whitelist=`["read_skill"]`；`SIMPLE_AGENT_END` 仅为 Manifest 声明偏好 |
| Message | user = 短指令（含先 read_skill）+ `image_url`（VL）；字段细则在 Skill md |
| useCase | `AgentTurnNode` ← `StateKeys.MODEL_USE_CASE` ← ActiveSkill；缺省 `pi.default` |
| 生产 Provider | infrastructure `AiCapabilityPiModelProvider` 桥接 agent MultimodalChat |
| 业务入口 | application `CertificateOcrAgent` 组消息 + `skillId` + 解析 DTO |

**≠** agent `ToolRegistry` / Capability；**≠** ContextCompressor / Checkpoint / HITL 职责变更。

## L3 Memory（Story 51-7）⏸ 暂缓

暂无真实业务记忆源；脚手架（`MemoryStore` / `MemoryManager` / `MemorySnapshot`）已从运行路径移除。
`SystemPromptInput` 仍保留 `core` / `memory` / `user` 键名常量，方便日后接回。

有业务需求时再恢复：Session 预加载 → 编进 `SYSTEM_PROMPT`；前缀须 `pi:memory:`（绝不用 `pi:checkpoint:`）。

## L2 ToolConfig [LIMS] + WRITE HITL（Story 51-4）✅

对齐上游 [Security / approval](https://hermes-agent.nousresearch.com/docs/user-guide/security) 的「执行前闸门」语义，但分级与 HITL 为 **[LIMS] 扩展**（上游仅有危险 shell 命令 approval，**不是** Hermes native 原语）。

| 类型 | 说明 |
| ---- | ---- |
| `ToolLevel` | `READ \| SUGGEST \| WRITE \| FORBIDDEN` |
| `ToolManifest` | 工具资产说明书（与 SkillManifest 对称）：`text` → Stable；schema → API |
| `ToolBinding` | Manifest + Handler（或 `handlerOnly` 占位，由 JSON 覆盖 Manifest） |
| `handlerClass` | JSON 字段；生产推荐靠它自动装配，无需 Config `@Bean` Handler |
| `ToolConfig` / `DefaultToolConfig` | Manifest 目录 + 分级闸门；**缺省 fail-closed**（未知 → FORBIDDEN） |
| `ToolRegistration` | 兼容包装 = Binding（新代码优先 Manifest + Binding） |
| `ToolContext` | `runId` / `traceId` / `activeSkillId`（hermes 自有；≠ agent ToolContext；**已移除** tenant/user） |
| `ToolAuditEvent` | 闸门 / 执行审计事件；经 `PiExtension.onToolAudit` 旁路分发 |
| `ToolDecision` | `APPROVE \| DENY` → `ResumeRequest.decision` / `approved` |

**WRITE HITL 流程：**

1. 模型产出含 WRITE 的 `TOOL_CALLS` → `tools` 内 `beforeToolCall` → `SUSPENDED`（checkpoint 保留；**不**调 handler）
2. `AgentSession.resume(ResumeRequest)` **[LIMS] HITL**（≠ 上游 Session `/resume`；绑 Graph Checkpoint [Lippi]）

   - **必须**携带 `decision`（APPROVE|DENY）或 `approved` 布尔；缺决策 → **FAILED**（fail-closed，禁止再挂起）
   - `decision=APPROVE` → 重跑 `tools`：闸门放行 → 执行一次 → 继续超步
   - `decision=DENY` → 写拒绝 `ToolResult`（可带 `humanInput` 原因）→ 回 agent（模型可见拒绝）

批次语义：同一超步任一 WRITE 未批准 → **整批挂起**（同批 READ 亦不先执行）。

生产默认：扫内置 Skills（`certificate.ocr`）；Tool 扫 JSON + merge 代码 Binding（无则空 Config，可 chat）。入图：`SkillSelector` → `TurnBinder(tool, skill, active)`；`AVAILABLE_TOOLS`/`TOOLS` ← whitelist 同步裁剪；无 Active 时 `AVAILABLE_SKILLS` ← `skillConfig.manifests()` 目录。

## 定位

| 模块                       | 职责                                        |
| -------------------------- | ------------------------------------------- |
| **`lippi-ai-lims-pi-agent`** | **Pi Runtime**（AgentSession + StateGraph） |
| `lippi-ai-lims-pi-ai`      | 模型端口 + DashScope / DeepSeek HTTP |

## 包根

`com.xmut.lims.pi`

- `session.AgentSession` / `DefaultAgentSession` / `PromptRequest` / `TurnResult` / `Session` / `SessionStore`
- `event.PiEventBus` / `PiEvent` / `PiEventType` / `Emitter`（Session 持有 bus；Agent/Node 只 `emit`）
- `agent.Agent` / `DefaultAgent`（Runtime 内部；**非**业务 Bean）
- `agent.PromptBuilder` / `DefaultToolLoopGraph`
- `graph.*` [Lippi]：`StateGraph` / `GraphExecutor` / `ToolNode` / …
- `extension.*`：`PiExtensionRegistrar` / `ExtensionRunner`（启动期 `register(bus)`）/ `PiExtension` / `ToolPolicyExtension`（必装闸门）
- `resource.*`：`PiResourceLoader` / `DefaultPiResourceLoader` / `PromptTemplate`（**不要**叫 Spring `ResourceLoader`）
- `message.Message`
- `model.ModelProvider` [Lippi] / Catalog / Decorator / Stub
- `tool.ToolConfig` [LIMS] / `ToolManifest` / `ToolBinding` / `ToolLevel`
- `skill.SkillConfig` / `ClasspathSkillBootstrap` / `SkillSelector`；`loop.TurnBinder`（Story 51-9）

## 自动装配

- `PiCheckpointAutoConfiguration`（仅 `lims.pi.checkpoint.redis.enabled=true` + `JedisPool` 时 `@Primary` Redis）→ `PiAutoConfiguration` → `AgentConfiguration`
- `META-INF/spring.factories`（Boot 2.7）
- 默认 Bean：`Checkpointer`（InMemory；Redis 需显式 enabled）、`ResumeIdempotencyStore`、`SessionStore`（**InMemorySessionStore**；显式 `lims.pi.session.sqlite-path` → Sqlite）、`ModelCatalog`、`ModelProvider`、`PromptBuilder`、`CompressionConfig`、`ContextCompressor`、`ToolConfig`、`SkillConfig`、`PiResourceLoader`、`ToolPolicyExtension`（WRITE 审批默认关）、`ExtensionRunner`（`PiExtensionRegistrar`）、**`AgentSession`**（持有 `PiEventBus`）
- **不**注册公共 `Agent` Bean（仅 Session 内部委托）

### Session 运维注意（Story 51-17 / AD-S8）

| 项 | 说明 |
| --- | --- |
| 过渡默认 | `InMemorySessionStore`（MissingBean；进程内；**非**生产真相） |
| 生产目标 | MySQL `pi_session` / `pi_session_entry`（Story 2.7；ebus-infrastructure `@Primary`） |
| Sqlite | 仅显式 `lims.pi.session.sqlite-path` / 单测；**禁止**空路径静默创建 `{cwd}/.lippi-pi/state.db` |
| 主键 | Schema v1：`UNIQUE(session_id)`；**已移除** `tenant_id` / `user_id` |
| 旧库 | 含 `tenant_id` 或 `user_version < 1` → **拒绝打开**；请删除该 `state.db` 后重试（不迁移） |
| WRITE 审批 | 默认关；`lims.pi.tool.write-approval.enabled=true` 可开 |
| Redis CP | 默认关；`lims.pi.checkpoint.redis.enabled=true` 才可 Primary |
| 禁止 | 把 `state.db` 提交进 git；Session 与 Checkpoint 混表 |

## 模块边界

- **依赖**：仅 `lippi-ai-lims-common`（+ Spring / Jackson 等）
- **禁止**：domain 禁 pi 编排 import
- **禁止**：Session 与 Checkpoint 共用 `pi:checkpoint:`
- **禁止**：新类型名 `PromptAssembler`；拆掉 Stable/Context/Volatile
- **Prompt / Skill 资源**：Skill 内置在本模块；业务细则可放 application

## 后续故事

| Story | 层 | 内容 |
| --- | --- | --- |
| 51-1～51-9 | — | 原 Hermes 投资 ✅（已迁 Pi 包名） |
| 51-5 | 切片 | Skill `certificate.ocr` → `AgentSession.prompt` ✅ |
| **51-12** | M0/M1 | Pi 更名 + `AgentSession` 骨架 ✅ |
| **51-13** | M2 | 去 load_memory；Policy→`tool_call` Extension hook ✅ |
| **51-14** | M3 | ResourceLoader + prompts/ + ExtensionRunner ✅ |
| **51-15** | CLI | 独立模块 `lippi-ai-lims-pi-cli` 开发者 REPL（无 `-p`；真模型 fail-fast）✅ |
| **51-16** | M4 | SessionStore hydrate：`getOrCreate`/`load`/`appendMessages`；prompt 前强制 load；InMemory 同语义 ✅ |
| **51-17** | M4 | SqliteSessionStore（显式 path opt-in；**非**生产默认；生产目标 MySQL → 2.7）✅ |
| 51-10 | L2 | lims.nav + SkillRouter（仍后续） |
| 51-11 | L3 | 使用指标（挂 `agent_end`；仍后续） |
