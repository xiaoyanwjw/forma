# Agent Run Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每个 billed run 提供沙箱工作区与 `read_file` / `write_file` / `bash`；模型终稿输出 `{"output":"<relpath>"}`，`GenerationOutputParser` 读盘后再走现有投影 → 落库 → settle。

**Architecture:** ebus 侧 `RunWorkspaceService` 负责路径/建删；`PromptRequest.workspaceRoot` → `TurnInput` → `StateKeys.WORKSPACE_ROOT` → `ToolContext`；三工具注册进 `EbusPiToolCatalogConfiguration`；Parser 增加带 `Path runDir` 的重载（有 `output` 指针则只信盘）。挂起策划 settle **不**删盘；仅终态 OK settle 成功后删 run 目录。

**Tech Stack:** Java 8 / Spring Boot 2.7（`lippi-pi-agent` + `lippi-ai-ebus-application`）、JUnit 5、现有 Skill markdown（`lippi-ai-ebus-starter/.../scenes/ecommerce/`）

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-01-agent-run-workspace-design.md`（accepted）
- 工作区：`{EBUS_WORKSPACE_ROOT|~/.ebus}/sessions/{sessionId}/{runId}/`
- 工具仅三件套；**禁止**内核认识 `plan/`/`exec/`/`merge_json`/`write_json`
- `metadata.output` **不**增加路径字段；结算路径只来自终稿 `{"output":"..."}`
- 有合法 `output` 字符串 → **只信盘**；无指针 → 保留今日内联 JSON 兼容
- 指针失败（越界/缺文件）→ release，不 settle；保留目录
- **仅终态 OK settle 成功后删盘**；挂起策划 settle / Suspend **不**删
- Java 8：无 `var` / `List.of` / `Files.readString`；用 `Files.readAllBytes` + `new String(..., UTF_8)`
- 密钥 / `.env` 实值不入仓

## File map

| Path | Responsibility |
|------|----------------|
| `.../agent/workspace/RunWorkspaceProperties.java` | `ebus.workspace.root` ← env `EBUS_WORKSPACE_ROOT` |
| `.../agent/workspace/RunWorkspaceService.java` (+test) | resolve / ensure / resolveUnder / deleteQuietly |
| `.../agent/workspace/WorkspacePathGuard.java` (+test) | 相对路径沙箱（禁 `..` / 绝对 / 逃逸） |
| `.../agent/tool/workspace/WriteFileToolHandler.java` (+test) | `write_file` |
| `.../agent/tool/workspace/ReadFileToolHandler.java` (+test) | `read_file`（默认上限 2MiB） |
| `.../agent/tool/workspace/BashToolHandler.java` (+test) | `bash`（cwd=run 根；超时 30s；输出截断 64KiB） |
| `.../config/EbusPiToolCatalogConfiguration.java` | 注册三工具 |
| `lippi-pi-agent/.../StateKeys.java` | `WORKSPACE_ROOT` |
| `lippi-pi-agent/.../ToolContext.java` (+test) | `workspaceRoot` |
| `lippi-pi-agent/.../PromptRequest.java` / `TurnInput.java` / `DefaultAgentSession` / `DefaultAgent` | 贯通 workspaceRoot |
| `.../GenerationOutputParser.java` (+test) | `parse(text, runDir)` 指针分支 |
| `.../AgentApplicationService.java` (+test) | ensure 目录、prompt 注入、parse 带 runDir、终态删盘 |
| `.../SkuHitlInterceptor.java` (+test) | 策划 parse 带 runDir |
| `scenes/.../ecommerce-picklist|skulist/SKILL.md` + `output.md` + test mirrors | 分步写盘 + 指针终稿；`allowed-tools` |
| `APP-META/.../.env.example` | `EBUS_WORKSPACE_ROOT` 注释占位 |

## Spec → Task

| Spec | Task |
|------|------|
| §5.1 布局 / 路径守卫 | 1 |
| §5.2 三工具 + ToolContext 注入 | 2–3 |
| §5.3 Parser 指针 | 4 |
| §5.5–5.6 接线 / HITL / 清理 | 5 |
| §6 Skill | 6–7 |

---

### Task 1: `RunWorkspaceService` + 路径守卫

**Files:**
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/workspace/RunWorkspaceProperties.java`
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/workspace/WorkspacePathGuard.java`
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/workspace/RunWorkspaceService.java`
- Create: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/workspace/WorkspacePathGuardTest.java`
- Create: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/workspace/RunWorkspaceServiceTest.java`
- Modify: `APP-META/docker-config/environment/.env.example`（加一行注释占位）

**Interfaces:**
- Produces: `RunWorkspaceProperties#getRoot(): Path`（空白则 `Paths.get(user.home, ".ebus")`）
- Produces: `WorkspacePathGuard.resolveUnder(Path runDir, String relative): Path`（非法抛 `IllegalArgumentException`）
- Produces: `RunWorkspaceService#runDir(sessionId, runId): Path`
- Produces: `RunWorkspaceService#ensureRunDir(sessionId, runId): Path`（`Files.createDirectories`）
- Produces: `RunWorkspaceService#deleteRunDirQuietly(sessionId, runId): void`

- [ ] **Step 1: 写失败测 — 路径守卫**

```java
@Test
void rejectsDotDotAndAbsolute() {
    Path run = Paths.get("/tmp/ws/sessions/s1/r1").toAbsolutePath().normalize();
    assertThrows(IllegalArgumentException.class,
            () -> WorkspacePathGuard.resolveUnder(run, "../x"));
    assertThrows(IllegalArgumentException.class,
            () -> WorkspacePathGuard.resolveUnder(run, "/etc/passwd"));
}

@Test
void acceptsNestedRelative() throws Exception {
    Path run = Files.createTempDirectory("ebus-ws-");
    Path out = WorkspacePathGuard.resolveUnder(run, "plan/final.json");
    assertTrue(out.startsWith(run));
    assertEquals("final.json", out.getFileName().toString());
}
```

- [ ] **Step 2: 实现 Guard + Properties + Service**

```java
// WorkspacePathGuard.resolveUnder:
//  1) blank relative → IAE
//  2) Paths.get(relative) 若 isAbsolute → IAE
//  3) runDir.resolve(relative).normalize()
//  4) !normalized.startsWith(runDir.normalize()) → IAE
//  5) return normalized

// RunWorkspaceService.runDir:
//  root.resolve("sessions").resolve(sessionId).resolve(runId)
```

`RunWorkspaceProperties`：`@ConfigurationProperties(prefix = "ebus.workspace")`，字段 `String root`；在 `EbusPiToolCatalogConfiguration` 或新建 `@EnableConfigurationProperties` 的小 `@Configuration` 上启用。

`.env.example`：

```bash
# EBUS_WORKSPACE_ROOT=   # optional; default ~/.ebus
```

application 绑定可用：`ebus.workspace.root=${EBUS_WORKSPACE_ROOT:}`（若项目已有类似模式则对齐）。

- [ ] **Step 3: Service 测 — ensure / delete**

```java
@Test
void ensureThenDelete() throws Exception {
    RunWorkspaceService svc = new RunWorkspaceService(propsWithTempRoot());
    Path dir = svc.ensureRunDir("s", "r");
    assertTrue(Files.isDirectory(dir));
    Files.write(dir.resolve("a.txt"), "x".getBytes(StandardCharsets.UTF_8));
    svc.deleteRunDirQuietly("s", "r");
    assertFalse(Files.exists(dir));
}
```

- [ ] **Step 4: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=WorkspacePathGuardTest,RunWorkspaceServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/workspace \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/workspace \
  APP-META/docker-config/environment/.env.example
git commit -m "$(cat <<'EOF'
feat(agent): add per-run workspace path service

EOF
)"
```

---

### Task 2: `ToolContext.workspaceRoot` 贯通

**Files:**
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/graph/StateKeys.java`
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/tool/ToolContext.java`
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/session/PromptRequest.java`
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/TurnInput.java`
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/session/DefaultAgentSession.java`
- Modify: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/agent/DefaultAgent.java`（`prepare` 写入 state）
- Modify/Create tests under `lippi-pi-agent/src/test/.../ToolContextTest.java`（或扩展现有）

**Interfaces:**
- Produces: `StateKeys.WORKSPACE_ROOT`（`String` 绝对路径）
- Produces: `PromptRequest.workspaceRoot` / `TurnInput.workspaceRoot`（可空 String）
- Produces: `ToolContext#getWorkspaceRoot(): String`（可空）
- Consumes: `ToolContext.from` 从 `state.get(WORKSPACE_ROOT)` 读取

- [ ] **Step 1: 写失败测 — ToolContext 带出 workspace**

```java
@Test
void from_readsWorkspaceRootFromState() {
    GraphState state = new GraphState();
    state.put(StateKeys.WORKSPACE_ROOT, "/tmp/ws/sessions/s/r");
    NodeContext node = new NodeContext("run-1", "tr-1");
    ToolContext ctx = ToolContext.from(node, state);
    assertEquals("/tmp/ws/sessions/s/r", ctx.getWorkspaceRoot());
}
```

- [ ] **Step 2: 实现贯通**

1. `StateKeys.WORKSPACE_ROOT = "workspace_root"`  
2. `ToolContext` 增加字段 `workspaceRoot`；构造与 `from` 从 state 读取（`StringUtils.hasText`）  
3. `PromptRequest` / `TurnInput` 增加可空 `workspaceRoot`（Lombok `@Value` + 显式 ctor 参数列表同步加一参）  
4. `DefaultAgentSession` 建 `TurnInput` 时：`.workspaceRoot(request.getWorkspaceRoot())`  
5. `DefaultAgent.prepare`：若 `turnInput.getWorkspaceRoot()` 有值 → `input.put(StateKeys.WORKSPACE_ROOT, trimmed)`  

注意：所有 `TurnInput(...)/PromptRequest(...)` 手工构造器调用点与测试 builder 需编译通过。

- [ ] **Step 3: 跑测**

```bash
mvn -pl lippi-pi-agent -am -Dtest=ToolContextTest,DefaultAgentSessionTest,PiAutoConfigurationTest test
```

Expected: PASS（若无独立 `ToolContextTest`，跑你新增的测试类名）

- [ ] **Step 4: Commit**

```bash
git add lippi-pi-agent
git commit -m "$(cat <<'EOF'
feat(pi-agent): thread workspaceRoot into ToolContext

EOF
)"
```

---

### Task 3: `read_file` / `write_file` / `bash` 工具

**Files:**
- Create: `.../agent/tool/workspace/WriteFileToolHandler.java`
- Create: `.../agent/tool/workspace/ReadFileToolHandler.java`
- Create: `.../agent/tool/workspace/BashToolHandler.java`
- Create: matching `*Test.java` under `src/test/.../tool/workspace/`
- Modify: `.../config/EbusPiToolCatalogConfiguration.java`

**Interfaces:**
- Consumes: `ToolContext#getWorkspaceRoot()`；空则 `ToolResult.failed(..., "workspace root missing")`
- Consumes: `WorkspacePathGuard.resolveUnder(Paths.get(workspaceRoot), path)`
- Produces: tool names 钉死 `write_file` / `read_file` / `bash`
- Constants: `ReadFileToolHandler.MAX_BYTES = 2 * 1024 * 1024`；`BashToolHandler.TIMEOUT_MS = 30_000`；`MAX_OUTPUT_CHARS = 64 * 1024`

- [ ] **Step 1: 写失败测 — write / read / 越界**

```java
@Test
void writeThenRead_roundTrip() throws Exception {
    Path run = Files.createTempDirectory("ws-");
    WriteFileToolHandler write = new WriteFileToolHandler();
    ReadFileToolHandler read = new ReadFileToolHandler();
    ToolContext ctx = new ToolContext("r", "t", null, run.toString());
    ToolResult w = write.handle(call("write_file", "{\"path\":\"a.json\",\"content\":\"{\\\"x\\\":1}\"}"), ctx);
    assertFalse(w.isError());
    ToolResult r = read.handle(call("read_file", "{\"path\":\"a.json\"}"), ctx);
    assertTrue(r.getOutput().contains("\"x\""));
}

@Test
void write_rejectsEscape() {
    Path run = Files.createTempDirectory("ws-");
    ToolResult r = new WriteFileToolHandler().handle(
            call("write_file", "{\"path\":\"../x\",\"content\":\"no\"}"),
            new ToolContext("r", "t", null, run.toString()));
    assertTrue(r.isError());
}
```

（`ToolContext` 四参 ctor 在 Task 2 落地；`call` 辅助仿 `AskHumanToolHandlerTest` / `ToolTestSupport`。）

- [ ] **Step 2: 实现三 Handler**

`write_file`：`path`+`content` 必填；`Files.createDirectories(parent)`；`Files.write(..., UTF_8)`；成功 `ToolResult.ok` 短消息含相对 path。

`read_file`：读字节；超 `MAX_BYTES` → failed 或截断并在 output 注明（钉死：**failed**「file too large」）。

`bash`：`command` 必填；`ProcessBuilder` `directory(runDir)`；`redirectErrorStream(true)`；等超时则 `destroyForcibly` + failed；stdout 超长截断并后缀 `\n...[truncated]`。

- [ ] **Step 3: 注册 Catalog**

`EbusPiToolCatalogConfiguration.toolCatalog` 的 `Arrays.asList` 追加三个 `Tool`（schema 仿 `askHumanTool`：object properties）。更新类注释：`read_skill` + `search_sku` + `ask_human` + workspace 三件套。

- [ ] **Step 4: bash 测（可用 `echo hi`）**

```java
@Test
void bash_echo() throws Exception {
    Path run = Files.createTempDirectory("ws-");
    ToolResult r = new BashToolHandler().handle(
            call("bash", "{\"command\":\"echo hi\"}"),
            new ToolContext("r", "t", null, run.toString()));
    assertFalse(r.isError());
    assertTrue(r.getOutput().contains("hi"));
}
```

- [ ] **Step 5: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=WriteFileToolHandlerTest,ReadFileToolHandlerTest,BashToolHandlerTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/tool/workspace \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/tool/workspace \
  lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/config/EbusPiToolCatalogConfiguration.java
git commit -m "$(cat <<'EOF'
feat(agent): add sandboxed read_file write_file bash tools

EOF
)"
```

---

### Task 4: `GenerationOutputParser` 指针读盘

**Files:**
- Modify: `.../agent/support/GenerationOutputParser.java`
- Modify: `.../agent/support/GenerationOutputParserTest.java`

**Interfaces:**
- Produces: `parse(String finalResponse)` — **行为不变**（兼容）
- Produces: `parse(String finalResponse, Path runWorkspaceRoot)` —  
  - 抽出 JSON 后若 `output` 为非空字符串：  
    - `runWorkspaceRoot == null` → 当作无指针，走旧逻辑（或 failed——钉死：**需要 runDir，否则抛 `IllegalArgumentException("workspace required for output pointer")`）  
    - `WorkspacePathGuard.resolveUnder` + `Files.exists` + 读 UTF-8 → 对**文件内容**再走现有双轨  
    - 缺文件/越界/IO → 抛 `IllegalArgumentException`（消息人话：`output file missing: ...` / `invalid output path`）  
  - 无 `output` 字符串 → 与 `parse(String)` 相同  
- 钉死：根上同时有 `output` 与 `view`/`artifact` 时 **只信盘**（忽略根上 view/artifact）

- [ ] **Step 1: 写失败测**

```java
@Test
void outputPointer_readsFileForDualTrack() throws Exception {
    Path run = Files.createTempDirectory("parse-ws-");
    String body = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[]},\"artifact\":{\"items\":[]}}";
    Files.write(run.resolve("final.json"), body.getBytes(StandardCharsets.UTF_8));
    ParsedGenerationOutput out = parser.parse("{\"output\":\"final.json\"}", run);
    assertNotNull(out.getRawView());
    assertEquals("t", out.getRawView().get("title"));
}

@Test
void outputPointer_missingFile_throws() throws Exception {
    Path run = Files.createTempDirectory("parse-ws-");
    assertThrows(IllegalArgumentException.class,
            () -> parser.parse("{\"output\":\"nope.json\"}", run));
}

@Test
void inlineWithoutPointer_unchanged() {
    ParsedGenerationOutput out = parser.parse(
            "{\"view\":{\"version\":1,\"blocks\":[]},\"artifact\":{}}",
            Files.createTempDirectory("x"));
    assertNotNull(out.getRawView());
}
```

- [ ] **Step 2: 实现重载**

在成功 `readTree` 出 root object 后、提取 view 之前：

```java
JsonNode outputNode = root.get("output");
if (outputNode != null && outputNode.isTextual() && StringUtils.hasText(outputNode.asText())) {
    if (runWorkspaceRoot == null) {
        throw new IllegalArgumentException("workspace required for output pointer");
    }
    Path file = WorkspacePathGuard.resolveUnder(runWorkspaceRoot, outputNode.asText().trim());
    if (!Files.isRegularFile(file)) {
        throw new IllegalArgumentException("output file missing: " + outputNode.asText());
    }
    byte[] bytes = Files.readAllBytes(file);
    String fileText = new String(bytes, StandardCharsets.UTF_8);
    return parse(fileText); // 递归走无指针路径；文件内勿再含 output 指针（若含，二次解析仍可处理一层——YAGNI：文件内再指针则再读一次即可，循环风险用 depth 或禁止文件内 output）
}
```

钉死：**文件内容若再含 `output` 指针，忽略并按无指针双轨处理该 JSON**（避免递归）。实现：私有 `parseEnvelope(String, Path, boolean allowPointer)`。

- [ ] **Step 3: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=GenerationOutputParserTest test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParser.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParserTest.java
git commit -m "$(cat <<'EOF'
feat(agent): resolve generation output pointer from workspace file

EOF
)"
```

---

### Task 5: Agent / HITL 接线 + 终态清理

**Files:**
- Modify: `.../agent/service/AgentApplicationService.java`
- Modify: `.../agent/support/SkuHitlInterceptor.java`
- Modify: `.../agent/service/AgentApplicationServiceTest.java`（及相关 interceptor 测，若有）
- Inject: `RunWorkspaceService`

**Interfaces:**
- Consumes: `RunWorkspaceService.ensureRunDir(sessionId, runId)` before each `agentSession.prompt` / resume prompt  
- Produces: `PromptRequest.builder()...workspaceRoot(runDir.toAbsolutePath().toString())`  
- Consumes: `generationOutputParser.parse(finalResponse, runDir)`（两处 OK 路径 + `SkuHitlInterceptor` 策划 parse）  
- Pointer/`IllegalArgumentException` → 转 `BusinessException` 人话，走现有 release/`run_failed`  
- `settleBilledRun` **成功末尾**（emit 之后）：`runWorkspaceService.deleteRunDirQuietly(sessionId, runId)`  
- `settleOnSuspended` / 策划 settle：**禁止** delete  

- [ ] **Step 1: 写/改测 — prompt 带 workspaceRoot；指针失败不 settle**

在 `AgentApplicationServiceTest` 用临时目录 + 真实 `GenerationOutputParser` 或 spy：

```java
@Test
void promptReceivesWorkspaceRoot() {
    // mock RunWorkspaceService.ensureRunDir → temp
    // when prompt → capture PromptRequest
    // assert request.getWorkspaceRoot() equals temp absolute path
}

@Test
void outputPointerMissing_releasesWithoutSettle() {
    // finalResponse = {"output":"missing.json"}
    // empty temp run dir
    // verify creditHoldSupport.release / never settle；emit run_failed
}
```

- [ ] **Step 2: 实现接线**

1. 构造注入 `RunWorkspaceService`。  
2. `streamGenerationRun` / resume 流里，调用 `prompt` 前：

```java
Path runDir = runWorkspaceService.ensureRunDir(context.getSessionId(), context.getRunId());
agentSession.prompt(PromptRequest.builder()
        .runId(context.getRunId())
        .sessionId(context.getSessionId())
        .workspaceRoot(runDir.toAbsolutePath().toString())
        // ...existing fields
        .build());
```

3. 替换两处 `generationOutputParser.parse(finalResponse)` 为带 `runDir`；catch `IllegalArgumentException` → `BusinessException(PARAM_INVALID 或 SYSTEM_ERROR, ex.getMessage())`。  
4. `SkuHitlInterceptor`：注入 `RunWorkspaceService`，`parse(planText, runWorkspaceService.runDir(sessionId, runId))`（session/run 从 `BilledRunContext` / `GenerationRun` 取）。  
5. `settleBilledRun` 成功路径末尾 `deleteRunDirQuietly`。

- [ ] **Step 3: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=AgentApplicationServiceTest,SkuHitlInterceptorTest,GenerationOutputParserTest test
```

Expected: PASS（若无 `SkuHitlInterceptorTest`，补最小测或在 Agent 测中间接覆盖）

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent
git commit -m "$(cat <<'EOF'
feat(agent): wire run workspace into prompt parse and cleanup

EOF
)"
```

---

### Task 6: `ecommerce-picklist` skill 改指针流程

**Files:**
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/references/output.md`
- Modify mirrors: `lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-picklist/...`  
  以及 `lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-picklist/...`（若存在且需同步）

**Interfaces:**
- `allowed-tools:` 含 `read_skill search_sku write_file read_file bash`
- Workflow：search → 筛选 → `write_file artifact.json` → `write_file view.json` → `bash` 拼 `final.json` → 终稿**仅** `{"output":"final.json"}`
- Verification：终稿不得再贴整包大 JSON；盘上 `final.json` 仍须满足原 view/artifact 规则
- `metadata.output` 三字段不变

- [ ] **Step 1: 改 SKILL.md Workflow 第 7 步及 Verification**

把「写终态 JSON 到对话」改为写盘 + 指针；给一段可复制的 `bash`/`python3` 合并示例（相对 run 根）。

- [ ] **Step 2: 改 output.md**

顶部增加「交付方式」：文件 `final.json` + 对话指针；保留原 JSON schema 示例作为**文件内容**示例，并加指针示例：

```json
{"output":"final.json"}
```

- [ ] **Step 3: 同步 test resources mirrors**

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist \
  lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-picklist \
  lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-picklist
git commit -m "$(cat <<'EOF'
docs(skill): picklist stepwise workspace output pointer

EOF
)"
```

---

### Task 7: `ecommerce-skulist` skill 改 plan/exec 指针

**Files:**
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md`
- Modify: `.../ecommerce-skulist/references/output.md`
- Modify test mirrors（application + pi-agent）

**Interfaces:**
- `allowed-tools:` `ask_human, read_skill, write_file, read_file, bash`
- 策划：写 `plan/artifact.json` + `plan/view.json` → 拼 `plan/final.json` → 终稿/挂起前指针 `{"output":"plan/final.json"}`  
- 执行（`confirm_execute` 后）：`exec/...` → `{"output":"exec/final.json"}`  
- `supplement`：覆盖 `plan/*` 再拼再 `ask_human`  
- 不改 ebus Interceptor 的 settle 时机（仍由现网 HITL）；只改模型产出形态

- [ ] **Step 1: 改 SKILL + output.md（策划/执行两段交付）**

- [ ] **Step 2: 同步 mirrors**

- [ ] **Step 3: 编译/相关测冒烟**

```bash
mvn -pl lippi-ai-ebus-starter -am -DskipTests compile
mvn -pl lippi-ai-ebus-application -am -Dtest=GenerationOutputParserTest,AgentApplicationServiceTest,WriteFileToolHandlerTest test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist \
  lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-skulist \
  lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-skulist
git commit -m "$(cat <<'EOF'
docs(skill): skulist plan/exec workspace output pointers

EOF
)"
```

---

## Plan self-review

1. **Spec coverage:** §5.1→T1；§5.2→T2–3；§5.3→T4；§5.5–5.6→T5（含「挂起不删」澄清）；§6→T6–7；过渡内联兼容→T4。  
2. **Placeholder scan:** 无 TBD；bash 禁网列为非阻塞（与 spec Open questions 一致），本计划不实现禁网。  
3. **Type consistency:** `workspaceRoot` 为绝对路径字符串；Parser 用 `Path`；工具名 `read_file`/`write_file`/`bash`；指针字段 `output`。

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-01-agent-run-workspace.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每个 Task 派一个新 subagent，Task 间人工/我方复核  
2. **Inline Execution** — 本会话按 Task 连续执行，设检查点  

Which approach?
