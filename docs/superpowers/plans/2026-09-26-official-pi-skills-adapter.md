# 官方 Pi Skill 形态 + ResourceLoader 收口 + Tool 按名启用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `PiResourceLoader` 按官方形态加载 `SKILL.md`（及 tools/prompts），Application 的 `SceneCapabilityPackLoader` 只按 `sceneCode` 选型校验；删掉 LIMS skill 与 `ToolLevel`/json Manifest 双轨。

**Architecture:** Skill/tool/prompt 发现收口到 pi-agent `PiResourceLoader`（对标官方 ResourceLoader）。场景包留在 ebus Application：查已注册 skill，不二次解析正文。Tool 只保留「注册 + 按名启用」；skill 的 `allowed-tools` 驱动本轮白名单。

**Tech Stack:** Java 8、Spring Boot 2.7、JUnit 5、Mockito、classpath 资源、现有 `AgentSession`

**Design:** `docs/superpowers/specs/2026-09-26-official-pi-skills-adapter-design.md`  
**Baseline:** `abf52e1`

## Global Constraints

- 不替换 `AgentSession`；不引入 pi-mono TS runtime
- 不接受浏览器下发 system/tool 正文（AD-16）
- 空跑仍只 release、从不 settle；缺包人话「场景能力暂不可用」
- skill id 官方连字符：`ecommerce-picklist` / `ecommerce-skulist`；空跑默认前者
- 电商 `allowed-tools` 默认仅 `read_skill`
- 提交仅在用户明确要求时执行（下列 Commit 步骤可跳过）
- 验证：`mvn -pl lippi-ai-ebus-starter -am test`

---

## File Structure

| 文件 | 职责 |
|------|------|
| `lippi-pi-agent/.../skill/SkillManifest.java` | 瘦身：id、description、promptRef、allowedTools、sceneCode（可选，由路径推导） |
| `lippi-pi-agent/.../skill/SkillMdLoader.java` | 解析目录/`SKILL.md` → Manifest；替换 json bootstrap |
| `lippi-pi-agent/.../skill/InMemorySkillConfig.java` | 按 id 注册（去掉 version 双键）；校验只要求 id+description+正文定位+allowedTools 可空 |
| `lippi-pi-agent/.../resource/DefaultPiResourceLoader.java` | 启动扫 scenes/**/SKILL.md + prompts；填充 SkillConfig；工具由 ToolConfig 注入 |
| `lippi-pi-agent/.../config/AgentConfiguration.java` | Bean：loader 驱动 skill 装载；代码注册 `read_skill`；去掉 json skill/tool 扫描 |
| `lippi-pi-agent/.../tool/*` | 去掉 ToolLevel；薄注册；`ToolPolicyExtension` 按「未注册/未激活」拒绝 |
| `lippi-pi-agent/src/main/resources/skills/**` | **删除** LIMS 遗留 skill |
| `lippi-ai-ebus-starter/.../scenes/ecommerce/**/SKILL.md` | 官方双 skill 目录 |
| `lippi-ai-ebus-application/.../SceneCapabilityPackLoader.java` | 只查 SkillConfig/Loader 快照；不解析 md/json |
| `lippi-ai-ebus-application/.../EbusSkillConfiguration.java` | 仅扩展 loader 扫描 pattern（若仍需要），禁止第二套解析 |
| `sdd/implementation-artifacts/sprint-status.yaml` | 3-2 → done；新增 3-2b in-progress/ready |
| 相关单测 | 随各 Task 改写/删除 CertificateOcr 专测 |

---

### Task 1: Sprint 键 + 瘦 Skill 模型 + SkillMd 解析（TDD）

**Files:**
- Modify: `sdd/implementation-artifacts/sprint-status.yaml`
- Modify: `lippi-pi-agent/.../skill/SkillManifest.java`
- Create: `lippi-pi-agent/.../skill/SkillMdLoader.java`
- Delete or gut: `SkillGraphTopology.java`（无引用后删）、`SkillManifestJsonLoader` 待 Task 2 删
- Test: `lippi-pi-agent/src/test/java/com/xmut/lims/pi/agent/skill/SkillMdLoaderTest.java`
- Modify: `InMemorySkillConfig.java` + `InMemorySkillConfigTest.java`（去 version / maxToolLevel / graphTopology）

**Interfaces:**
- Consumes: classpath `SKILL.md` 文本
- Produces:
  - `SkillManifest` 字段：`String id`, `String description`, `String promptRef`, `List<String> allowedTools`, `String sceneCode`（可 null）
  - `SkillMdLoader.load(Resource skillMd, String sceneCodeFromPath)` → `SkillManifest`
  - `InMemorySkillConfig.register(manifest)` 仅按 `id` 索引

- [ ] **Step 1: 更新 sprint**

在 `sprint-status.yaml` 的 epic-3 下：

```yaml
  3-2-电商场景能力包按-scenecode-加载: done
  3-2b-官方-agent-skills-形态与-skill-tool-瘦身: in-progress
```

（若键名需与 epics 对齐，实现时保持 yaml 既有命名风格。）

- [ ] **Step 2: 写失败单测 — 解析最小 SKILL.md**

`SkillMdLoaderTest.java`：

```java
@Test
void loadsOfficialFrontmatterAndBodyRef() throws Exception {
    String md = ""
            + "---\n"
            + "name: ecommerce-picklist\n"
            + "description: Picklist skill for Adam ecommerce.\n"
            + "allowed-tools: read_skill\n"
            + "---\n"
            + "\n"
            + "# Body\n"
            + "Rules here.\n";
    Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
        @Override public String getFilename() { return "SKILL.md"; }
        @Override public String getDescription() {
            return "class path resource [scenes/ecommerce/ecommerce-picklist/SKILL.md]";
        }
    };
    SkillManifest m = SkillMdLoader.load(resource, "ecommerce");
    assertThat(m.getId()).isEqualTo("ecommerce-picklist");
    assertThat(m.getDescription()).contains("Picklist");
    assertThat(m.getAllowedTools()).containsExactly("read_skill");
    assertThat(m.getSceneCode()).isEqualTo("ecommerce");
    assertThat(m.getPromptRef()).contains("scenes/ecommerce/ecommerce-picklist/SKILL.md");
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn -pl lippi-pi-agent -Dtest=SkillMdLoaderTest test
```

Expected: FAIL（类不存在或 API 不匹配）

- [ ] **Step 4: 实现瘦 `SkillManifest` + `SkillMdLoader`**

- `SkillManifest`：删除 `version`、`displayName`、`skillsPrompt`（若正文只走 promptRef）、`maxToolLevel`、`graphTopology`、`modelUseCase`、`contentHash`；新增 `allowedTools`、`sceneCode`。
- `SkillMdLoader`：用简单 YAML frontmatter 解析（可用现有依赖；若无 YAML 库，手写 `---` 块键值解析，仅支持本故事需要的标量键）。
- `promptRef`：设为可被 `ResourceLoader.getResource` 打开的 classpath 位置（从 Resource URL/描述推导，或加载时显式传入 location）。
- `InMemorySkillConfig.validate`：要求 `id`、`description`、非空 `promptRef`；`allowedTools` 可为 empty；**禁止**再要求 version/topology/level。
- 注册表：`currentById` 仅按 id；删除 versionKey 路径（或 version 固定废弃）。

- [ ] **Step 5: 跑通 SkillMdLoaderTest + 修正 InMemorySkillConfigTest**

```bash
mvn -pl lippi-pi-agent -Dtest=SkillMdLoaderTest,InMemorySkillConfigTest test
```

Expected: PASS（旧测改为新字段构造；删掉 version/topology 断言）

- [ ] **Step 6: Commit（可选）**

```bash
git add sdd/implementation-artifacts/sprint-status.yaml lippi-pi-agent/src/main/java/com/xmut/lims/pi/agent/skill/ lippi-pi-agent/src/test/java/com/xmut/lims/pi/agent/skill/
git commit -m "$(cat <<'EOF'
feat(pi-agent): add SKILL.md loader and slim SkillManifest

EOF
)"
```

---

### Task 2: ResourceLoader 扫 SKILL.md；删除 LIMS skill 资源

**Files:**
- Modify: `DefaultPiResourceLoader.java`、`PiResourceLoader.java`（如需 `listSkillsByScene`）
- Modify: `AgentConfiguration.java`（skillConfig 由 loader/bootstrap-md 填充）
- Delete: `src/main/resources/skills/certificate-ocr*`、`walk-in-import*`、`test-*-schema*`、`inst-pdf-extract*`
- Delete: `SkillManifestJsonLoader.java`、`ClasspathSkillBootstrap.java`（或改成委托 SkillMdLoader 后删旧 pattern）
- Delete/rewrite tests: `ClasspathSkillBootstrapTest`、`CertificateOcrPiIntegrationTest`、`DefaultPiResourceLoaderTest` 中 LIMS 依赖
- Test: `DefaultPiResourceLoaderTest` 增加「从 test resources 加载 scenes/**/SKILL.md」

**Interfaces:**
- Consumes: `SkillMdLoader`、`InMemorySkillConfig`
- Produces:
  - 启动后 `skillConfig.resolve("ecommerce-picklist")` 在有测试资源时可 Optional 非空
  - `SkillConfig.listByScene(String sceneCode)` → `List<SkillManifest>`（新增；按 `sceneCode` 字段过滤）
  - `PiResourceLoader.reload()` 可重扫 skills+prompts（至少 prompts 保持；skills 若 seal 后不可热更则文档注明仅启动装载）

- [ ] **Step 1: 测试资源放一份最小 SKILL.md（pi-agent test）**

```text
lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md
lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md
```

内容含 name/description/`allowed-tools: read_skill` + 短正文。

- [ ] **Step 2: 写失败测 — loader 装载后 snapshot 含两 id**

```java
@Test
void snapshotIncludesSceneSkillsFromSkillMd() {
    InMemorySkillConfig skills = new InMemorySkillConfig(new SkillConfigProperties(false));
    // 构造 DefaultPiResourceLoader，pattern 指向 test classpath scenes
    SkillMdBootstrap.load(skills, resolver, "classpath*:scenes/*/*/SKILL.md");
    skills.sealBootstrap();
    assertThat(skills.resolve("ecommerce-picklist")).isPresent();
    assertThat(skills.listByScene("ecommerce"))
            .extracting(SkillManifest::getId)
            .contains("ecommerce-picklist", "ecommerce-skulist");
}
```

（类名 `SkillMdBootstrap` 可与 `SkillMdLoader` 同文件静态方法 `loadAll(config, resolver, pattern)`。）

- [ ] **Step 3: 实现扫描装载；AgentConfiguration 改用 MD pattern**

```java
// AgentConfiguration.skillConfig — 示意
InMemorySkillConfig config = new InMemorySkillConfig(props);
SkillMdBootstrap.load(config, resourcePatternResolver, "classpath*:scenes/*/*/SKILL.md");
config.sealBootstrap();
return config;
```

`DefaultPiResourceLoader` 构造后 `snapshot().getSkillIds()` 来自 skillConfig。

- [ ] **Step 4: 删除 LIMS 资源与 CertificateOcr 专测；修编译**

```bash
mvn -pl lippi-pi-agent test
```

Expected: 删除/改写后绿；无 certificate.ocr 引用。

- [ ] **Step 5: Commit（可选）**

---

### Task 3: Tool 按名启用 — 去 ToolLevel，代码注册 read_skill

**Files:**
- Modify: `ToolManifest.java` / `Tool.java` / `DefaultToolConfig.java` / `ToolConfig.java` — 去掉 level
- Modify: `ToolPolicyExtension.java` — 未知或未在 active 集 → DENY；删除 WRITE-by-level 分支（或保留 flag 但本故事恒关且不读 level）
- Modify: `AgentConfiguration.toolConfig` — 代码注册 `read_skill`，不再依赖 `ClasspathToolBootstrap` json（可删 `read-skill.tool.json`）
- Modify: `TurnBinder` / `ActiveSkill` — whitelist 来自 `allowedTools`；删除 `modelUseCase` 投影若字段已删
- Rewrite: `ToolPolicyExtensionTest`、`ToolConfigTest`、`ReadSkillToolHandlerTest`
- Delete: `ToolLevel.java`（无引用后）、测试 `sample-echo.tool.json` 若仅演示 level

**Interfaces:**
- Consumes: `SkillManifest.getAllowedTools()`
- Produces:
  - `ToolConfig.schemasForModel(List<String> names)` / `textForModel(names)`（保持现有方法名若已有）
  - `ToolPolicyExtension`：`levelOf` 删除；用 `isRegistered(name)` + 可选 `isActive(name)`（active 可由 Turn 状态或策略扩展从 state 读 `StateKeys` 中的 active 列表）

- [ ] **Step 1: 写失败测 — 未激活 tool 拒绝**

```java
@Test
void deniesToolNotInActiveSet() {
    ToolConfig config = DefaultToolConfig.of(readSkillOnly());
    ToolPolicyExtension ext = new ToolPolicyExtension(config, false);
    // active = [read_skill]；调用 unknown → DENY
}
```

- [ ] **Step 2: 实现去 level + 按名策略；全模块修编译**

```bash
mvn -pl lippi-pi-agent test
```

Expected: PASS

- [ ] **Step 3: Commit（可选）**

---

### Task 4: 电商 SKILL.md 资源 + 薄 SceneCapabilityPackLoader

**Files:**
- Create:  
  `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`  
  `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md`
- Create same under `lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/...`
- Delete: 旧 `ecommerce.*.skill.json`、旧扁平 `ecommerce.*.md`
- Modify: `SceneCapabilityPack.java`、`SceneCapabilityPackLoader.java`
- Modify: `EbusSkillConfiguration.java` — 若默认 AgentConfiguration 已扫 `scenes/*/*/SKILL.md`，可删除二次扫描或改为 no-op / 文档说明
- Test: `SceneCapabilityPackLoaderTest.java`

**Interfaces:**
- Consumes: `SkillConfig`（注入）
- Produces:
  - `DEFAULT_EMPTY_RUN_SKILL_ID = "ecommerce-picklist"`
  - `load(sceneCode)` → `SceneCapabilityPack`；内部 `skillConfig.listByScene(code)`；电商缺任一 required id → `BusinessException(PARAM_INVALID, MSG_PACK_UNAVAILABLE)`
  - **禁止** `PathMatchingResourcePatternResolver` 读 `*.skill.json`

- [ ] **Step 1: 写失败测 — Loader 查 Store 而非扫 json**

```java
@Test
void loadEcommerceRequiresBothSkillsFromSkillConfig() {
    SkillConfig skills = mock(SkillConfig.class);
    when(skills.listByScene("ecommerce")).thenReturn(Arrays.asList(picklist, skulist));
    SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills);
    SceneCapabilityPack pack = loader.load("ecommerce");
    assertThat(pack.hasSkill("ecommerce-picklist")).isTrue();
}

@Test
void loadFailsWhenPicklistMissing() {
    when(skills.listByScene("ecommerce")).thenReturn(Collections.singletonList(skulistOnly));
    assertThatThrownBy(() -> loader.load("ecommerce"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("场景能力暂不可用");
}
```

- [ ] **Step 2: 实现薄 Loader + 落地 SKILL.md 资源**

SKILL.md 示例（picklist）：

```markdown
---
name: ecommerce-picklist
description: 国内通用默认风格的选品清单生成（骨架，3.4 填肉）
allowed-tools: read_skill
---

# ecommerce-picklist

你是 Adam 电商开店助手的**选品清单**路径……
```

- [ ] **Step 3: 跑 application / starter 相关测**

```bash
mvn -pl lippi-ai-ebus-application,lippi-ai-ebus-starter -am -Dtest=SceneCapabilityPackLoaderTest,SceneCapabilityPackBootstrapTest test
```

Expected: PASS（Bootstrap 测改为断言 ResourceLoader/SkillConfig 能 resolve 新 id）

- [ ] **Step 4: Commit（可选）**

---

### Task 5: 空跑接线改默认 skillId + 全量回归

**Files:**
- Modify: `AgentApplicationService.java`（已用常量则随 Loader 常量变更）
- Modify: `AgentApplicationServiceTest.java` — 所有 `ecommerce.picklist` → `ecommerce-picklist`；mock `SceneCapabilityPackLoader` / SkillConfig
- Modify: `SceneCapabilityPackBootstrapTest.java`
- 扫全仓：`ecommerce.picklist`、`certificate.ocr`、`maxToolLevel`、`ToolLevel`、`*.skill.json`（scenes 下）

- [ ] **Step 1: 更新空跑测断言默认 skillId**

```java
verify(agentSession).prompt(argThat(req ->
        "ecommerce-picklist".equals(req.getSkillId())));
```

- [ ] **Step 2: 全 reactor 测试**

```bash
mvn -pl lippi-ai-ebus-starter -am test
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 将 3-2b 标为 review/done（实现完成后）并 Commit（可选）**

---

### Task 6: 设计/文档对齐（轻量）

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-official-pi-skills-adapter-design.md` status → implemented（完成后）
- 可选：`lippi-pi-agent/README.md` 技能小节改为 SKILL.md + ResourceLoader（勿大段粘贴）
- 可选：`deferred-work.md` 记下「通用 read / 内置 coding tools / WRITE 点名审批」

- [ ] **Step 1: 更新 README 短段落 + deferred**
- [ ] **Step 2: 再跑一次全量 test 确认**
- [ ] **Step 3: Commit（可选）**

---

## Self-Review (plan vs spec)

| Spec 要求 | Task |
|-----------|------|
| PiResourceLoader 统一 skills/tools/prompts | 2、3 |
| SceneCapabilityPackLoader 只选型 | 4 |
| 官方 SKILL.md + 连字符 id | 1、4 |
| 删 LIMS skill | 2 |
| 删 Skill 多余字段 | 1 |
| Tool 按名启用、去 ToolLevel | 3 |
| 空跑默认 ecommerce-picklist + 缺包 release | 5 |
| 3-2 done / 3-2b 故事 | 1、5 |
| 不引入 TS / 不 settle | Global Constraints |

**钉死的软点：** `allowed-tools` 缺省电商为 `read_skill`；json bootstrap **整类替换**为 SkillMd 扫描；场景归属用 Manifest.`sceneCode`（路径推导）。

---

## Execution Handoff
