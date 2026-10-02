# Phase 1: Tool `*.tool.json` + Handler 动态注入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `ToolCatalog` 由 `classpath*:tools/**/*.tool.json`（schema）与 Spring `ToolHandler` Bean（执行）合并而成；拆掉巨型 `PiToolCatalogConfiguration` 手写列表；Base 五工具进 pi-agent。

**Architecture:** 恢复/实现薄 `ToolDefinition` JSON 加载器；`InMemoryToolCatalog.merge(scanned, coded)` + `ToolHandlerAutoBinder`；`BaseToolsConfiguration` / `SkuToolsConfiguration` / `XhsToolsConfiguration` 只注册 Handler（及业务 Port）。本计划 **不含** Phase 2 extension 模块。

**Tech Stack:** Java 8 / Spring Boot 2.7、`lippi-pi-agent`、`lippi-ai-ebus-application`、JUnit 5 + Mockito

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-02-pi-tool-skill-dynamic-injection-design.md`（accepted）— **本计划仅 Phase 1**
- Schema 真源 = `*.tool.json`；代码侧 **禁止**再 `ToolDefinition.builder` 复制 parameters
- 同 id：json 覆盖 schema/text，保留 Handler；有 json 无 Handler → **启动 fail-fast**
- 扫描：`classpath*:tools/**/*.tool.json`
- 配置类名钉死：`BaseToolsConfiguration`、`SkuToolsConfiguration`、`XhsToolsConfiguration`
- Pi 内置：`read_skill`、`ask_human`、`write_file`、`read_file`、`bash`
- 业务：`search_sku`、`search_xhs_note`、`fetch_xhs_note`
- `pi-agent` 不得依赖 Apify / Credit；workspace 路径守卫随 Handler 迁入 pi-agent（或 common），不得把 application 业务包拖进 pi-agent
- 本规约 **取代** `2026-09-26-official-pi-skills-adapter-design.md` 中「停用 *.tool.json」对 schema 轨的表述（仅 schema 文件；Handler 仍代码）
- Java 8：无 `var` / `List.of`

## File map

| Path | Responsibility |
|------|----------------|
| `lippi-pi-agent/.../tool/ToolDefinitionJsonLoader.java` (+test) | 扫盘解析 `*.tool.json` → `List<ToolDefinition>` |
| `lippi-pi-agent/.../config/AgentConfiguration.java` | `ToolCatalog` = merge；删「仅 read_skill of()」 |
| `lippi-pi-agent/.../config/BaseToolsConfiguration.java` | Handler Beans：ask_human / write_file / read_file / bash（+ read_skill 若并入） |
| `lippi-pi-agent/.../resources/tools/base/*.tool.json` | 五工具 schema |
| `lippi-pi-agent/.../tool/workspace/*`（迁入） | 原 ebus workspace handlers + path guard（去 ebus 包名） |
| `lippi-ai-ebus-application/.../SkuToolsConfiguration.java` | SkuSearcher 管线 + SearchSkuToolHandler Bean |
| `.../resources/tools/sku/search_sku.tool.json` | schema |
| `.../XhsToolsConfiguration.java` | Xhs handlers + ports |
| `.../resources/tools/xhs/*.tool.json` | schema |
| `PiToolCatalogConfiguration.java` | 瘦身：仅 ModelCatalog overlay（若需要）；**不再**建 ToolCatalog |

## Spec → Task

| Spec § | Task |
|--------|------|
| 4.2–4.3 loader + merge + fail-fast | 1 |
| Base json + BaseToolsConfiguration + workspace 迁入 | 2 |
| Sku/Xhs json + configs；删巨型列表 | 3 |
| 回归装包 / 工具测 | 4 |

**Phase 2**（extension 模块）→ 另开 plan，本文件不执行。

---

### Task 1: `ToolDefinitionJsonLoader` + catalog merge 接线

**Files:**
- Create: `lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/tool/ToolDefinitionJsonLoader.java`
- Create: `lippi-pi-agent/src/test/java/com/xmut/lims/pi/agent/tool/ToolDefinitionJsonLoaderTest.java`
- Create: `lippi-pi-agent/src/test/resources/tools/fixture/demo_echo.tool.json`（仅测）
- Modify: `AgentConfiguration.java` — `toolConfig` Bean 改为扫描 + merge + fail-fast
- Modify: `ToolCatalogTest.java`（或新测）覆盖「有 json 无 handler 失败」

**Interfaces:**
- Produces: `ToolDefinitionJsonLoader.DEFAULT_PATTERN = "classpath*:tools/**/*.tool.json"`
- Produces: `List<ToolDefinition> load(ResourcePatternResolver)` — 解析 id、description、text、handlerClass、schema（parameters → ToolSchema）
- Produces: `AgentConfiguration.toolConfig(...)` 使用 `merge` + `ToolHandlerAutoBinder.bindFromManifests`；对每个 scanned id，若 binding.handler == null → throw

- [ ] **Step 1: 写失败测 — loader 解析最小 json**

```java
@Test
void load_reads_id_handlerClass_and_parameters() throws Exception {
    // 用 test classpath tools/fixture/demo_echo.tool.json
    List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(resolver);
    ToolDefinition d = defs.stream().filter(x -> "demo_echo".equals(x.getId())).findFirst().get();
    assertEquals("com.example.DemoEchoHandler", d.getHandlerClass());
    assertNotNull(d.getSchema());
    assertEquals("demo_echo", d.schemaOrDefault().getName());
}
```

- [ ] **Step 2: Run — expect FAIL（类不存在）**

```bash
mvn -pl lippi-pi-agent -Dtest=ToolDefinitionJsonLoaderTest test
```

- [ ] **Step 3: 实现 loader（Jackson 读树 → ToolDefinition.builder）**

字段映射：根 `id`；`handlerClass`；`text`；`description`；`schema.name|description|parameters`（parameters 可为 JsonNode 存进 ToolSchema，对齐现网 `ToolSchema` API）。

- [ ] **Step 4: 写测 — merge 后无 handler fail-fast**

```java
@Test
void toolCatalog_fails_when_json_has_no_handler_bean() {
    // 仅 scanned demo_echo，beanFactory 无该类 → AgentConfiguration 组装抛 ToolValidationException 或 IllegalStateException
}
```

实现：在 `toolConfig` 里 merge 后遍历 `catalog.all()`，`getHandler(id)` 为空则抛。

- [ ] **Step 5: Run PASS + Commit**

```bash
git commit -m "feat(pi-agent): load tool schemas from *.tool.json and merge handlers"
```

---

### Task 2: Base 五工具 json + `BaseToolsConfiguration` + workspace 迁入 pi-agent

**Files:**
- Create: `lippi-pi-agent/src/main/resources/tools/base/read_skill.tool.json`
- Create: `.../ask_human.tool.json`
- Create: `.../write_file.tool.json`、`read_file.tool.json`、`bash.tool.json`
- Create: `lippi-pi-agent/.../config/BaseToolsConfiguration.java`
- Move（包名改为 `com.xmut.lims.pi.agent.tool...`）:
  - `AskHumanToolHandler`（自 ebus；`TOOL_NAME` 仍对齐 `ToolPolicyExtension.ASK_HUMAN_TOOL`）
  - `WriteFileToolHandler` / `ReadFileToolHandler` / `BashToolHandler` + `WorkspaceToolSupport`
  - `WorkspacePathGuard`（自 ebus application workspace 包；或抽到 `lippi-pi-agent` 内）
- Delete ebus 旧 handler 类（或 deprecate 转发一期——**优先直接迁并改 import**）
- Update ebus 测试 import

**Interfaces:**
- `BaseToolsConfiguration` `@Bean` 各 Handler（无参或注入 ObjectMapper）
- json `handlerClass` = 迁入后的 FQCN
- schema 内容从现网 `PiToolCatalogConfiguration` 静态方法 **剪切**进 json（参数字段保持一致）

- [ ] **Step 1: 为每个 base tool 写 json（先提交资源）+ 单测断言 loader 能扫到五 id**

- [ ] **Step 2: 迁 Handler 到 pi-agent；`BaseToolsConfiguration` 注册 Bean**

注意：`WriteFile` 等若依赖 ebus `WorkspacePathGuard`，**一并迁入** pi-agent（纯路径沙箱，无业务）。更新所有引用。

- [ ] **Step 3: 从 `AgentConfiguration.readSkillTool` 去掉内联 schema；read_skill 只留 Handler Bean + json**

- [ ] **Step 4: Run**

```bash
mvn -pl lippi-pi-agent,lippi-ai-ebus-application -am -DfailIfNoTests=false \
  -Dtest=ToolDefinitionJsonLoaderTest,ToolCatalogTest,AskHumanToolHandlerTest,WriteFileToolHandlerTest,ReadFileToolHandlerTest,BashToolHandlerTest test
```

（测试类随包迁移后改名路径。）

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pi-agent): base tools via tool.json and BaseToolsConfiguration"
```

---

### Task 3: Sku / Xhs `*.tool.json` + 业务 Configuration；瘦身 `PiToolCatalogConfiguration`

**Files:**
- Create: `lippi-ai-ebus-application/src/main/resources/tools/sku/search_sku.tool.json`
- Create: `.../tools/xhs/search_xhs_note.tool.json`、`fetch_xhs_note.tool.json`
- Create: `.../config/SkuToolsConfiguration.java`
- Create: `.../config/XhsToolsConfiguration.java`
- Modify: `PiToolCatalogConfiguration.java` — **删除** `toolCatalog` `@Bean` 及全部 `static Tool xxxTool(...)`；保留 `ModelCatalog` overlay / Properties `@EnableConfigurationProperties` 可挪到 Sku/Xhs configs
- Ensure application 组件扫描能吃到新 `@Configuration`
- Update `EbusPiToolCatalogConfigurationTest` 等：不再断言「ebus Primary 整表覆盖」，改为断言 resolve 业务 tool id + schema 来自资源

**Interfaces:**
- Sku/Xhs configs 只 `@Bean` Handler + Port/Searcher（与现网装配相同依赖）
- json `handlerClass` 指向现网 FQCN（仍在 application 包）

- [ ] **Step 1: 从 `PiToolCatalogConfiguration` 复制 schema 到三个 json；写测 loader 在 application test 能扫到**

- [ ] **Step 2: 抽出 `SkuToolsConfiguration` / `XhsToolsConfiguration`；删除巨型 `toolCatalog` 方法**

此时 **唯一** `ToolCatalog` 来自 pi-agent `AgentConfiguration`（ConditionalOnMissingBean 去掉对抗：ebus **不得**再声明 ToolCatalog）。

- [ ] **Step 3: 改测试**

```java
@Test
void resolves_search_sku_schema_from_json() {
    // Spring context or manual load+merge with real SearchSkuToolHandler bean
    assertTrue(catalog.resolve("search_sku").isPresent());
    // parameters 含 query
}
```

- [ ] **Step 4: Run**

```bash
mvn -pl lippi-ai-ebus-starter -am -DfailIfNoTests=false \
  -Dtest=EbusPiToolCatalogConfigurationTest,SearchSkuToolHandlerTest,SearchXhsNoteToolHandlerTest,FetchXhsNoteToolHandlerTest,EbusPrimaryToolCatalogOverrideTest test
```

若 `EbusPrimaryToolCatalogOverrideTest` 已无意义 → 删除或改写为「无第二 ToolCatalog」。

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(ebus): sku/xhs tools via tool.json; drop monolithic ToolCatalog"
```

---

### Task 4: 回归与文档钉

**Files:**
- Modify: 若 README / AGENTS 仍写「停用 tool.json」→ 改一句指向本 spec
- Run broader tests

- [ ] **Step 1: Run**

```bash
mvn -pl lippi-ai-ebus-starter -am -DfailIfNoTests=false \
  -Dtest=SceneCapabilityPackBootstrapTest,SkillsTest,ToolCatalogTest,XhsNoteSearcherTest test
cd lippi-ai-ebus-web && npm test -- --run XiaohongshuWorkspace 2>/dev/null || true
```

- [ ] **Step 2: 确认生产 tool id 集合**

`read_skill, ask_human, write_file, read_file, bash, search_sku, search_xhs_note, fetch_xhs_note` 均能 `resolve` 且 handler 非 null。

- [ ] **Step 3: Commit**（若有文档小改）

```bash
git commit -m "docs: note tool.json schema track supersedes prior deprecation"
```

---

## Self-review

| Spec Phase 1 要求 | Task |
|-------------------|------|
| json schema 扫描 | 1 |
| merge + fail-fast | 1 |
| Base 五工具 pi-agent | 2 |
| Sku/Xhs 拆配置 + json | 3 |
| 删巨型列表 | 3 |
| 验收回归 | 4 |
| Phase 2 extension | **不在本 plan** |

---

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-10-02-pi-tool-json-dynamic-injection-phase1.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每 Task 新开子代理  
2. **Inline Execution** — 本会话连续做  

Which approach?
