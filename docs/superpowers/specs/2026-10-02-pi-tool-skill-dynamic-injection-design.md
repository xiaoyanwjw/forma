# Pi / 业务 Skill·Tool 动态注入（两阶段）（设计）

**Date:** 2026-10-02  
**Status:** accepted  
**Decision:**  
- **先**做动态注入与分类（Phase 1），**再**把业务 Skill/Tool **物理拆**到 `forma-pi-extension`（Phase 2）  
- 分类：Pi 内核内置（含 Base 四工具）vs 业务领域  
- 平台工具（`ask_human`、`write_file`、`read_file`、`bash`）算 **Pi 内置**，不进业务 extension  
- 配置类命名：`BaseToolsConfiguration`、`SkuToolsConfiguration`、`XhsToolsConfiguration`  
- Tool：**Phase 1 即采用「`*.tool.json` 只声明 schema + Java Handler」双轨**；catalog = 扫盘 Manifest ∪ 代码 Binding（复用/恢复 `InMemoryToolCatalog.merge` + `ToolHandlerAutoBinder`）  
- 禁止巨型 `@Primary` 手写全量 `Tool` 列表；禁止指望纯 md 无 Java 就能执行工具

**Related:**  
- [`2026-10-01-agent-run-workspace-design.md`](./2026-10-01-agent-run-workspace-design.md)（workspace 三件套）  
- [`2026-10-01-xiaohongshu-scene-skills-design.md`](./2026-10-01-xiaohongshu-scene-skills-design.md)  
- Spine AD-11（依赖方向）、AD-13（模块命名）、AD-16（场景能力包在代码）  
- 现网：`AgentConfiguration`（`read_skill` + Skill classpath 扫描）、`PiToolCatalogConfiguration`（ebus 整表覆盖）  

---

## 1. Problem

- **Skill** 已靠 `classpath*:scenes/*/*/SKILL.md` 扫描，业务 jar 放资源即可；分类语义未写清。  
- **Tool** 在 `forma-application` 的 `PiToolCatalogConfiguration` 里硬编码全集；每加工具必改该类，与 pi 默认 `ToolCatalog` 用 `@Primary` 对抗。  
- 电商 / 小红书工具与 workspace、ask_human 混在同一配置，边界不清，不利于后续拆模块。

---

## 2. Goals / Non-goals

### Goals

1. 明确 **Pi 内置 vs 业务** 归属；两边都能进入运行时 catalog。  
2. Phase 1：  
   - **schema**：`classpath*:tools/**/*.tool.json`（只声明 id / description / parameters / text / handlerClass）  
   - **执行**：Java `ToolHandler` Bean（或由 `handlerClass` 解析）；catalog 用 `merge(scanned, coded)`  
   - 加新工具 = 加 json + 保证 handler 在 Spring 里，**无需**改巨型聚合列表  
3. Phase 2：业务 Skill 资源 + 业务 Tool（json + handler 配置）迁入 `forma-pi-extension`。  
4. 计费、落库、`SceneCapabilityPackLoader` 仍留在 ebus application。

### Non-goals

- 本设计不抽 `pi-*` 为跨仓共享库（Ask first；本仓内 extension 即可）。  
- 不改积分账本 / SSE 协议 / skill 正文业务语义。  
- 不把 Apify / Credit 依赖引入 `pi-agent`。  
- Phase 1 不强制新建 Maven 模块（可先在现有模块拆 Configuration）。  
- **不**支持「仅 md、无 Java Handler」的可执行工具；**不**要求运行时热加载任意代码。  
- 不恢复「双份真相」：同一 tool id 禁止 json 与代码各写一套互相打架的 schema（以 json 为 schema 真源，代码侧只贡献 Handler）。

---

## 3. 分类表（拍板）

| 档 | 内容 | 模块归属 |
|----|------|----------|
| **Pi 内核内置** | Tool：`read_skill`、`ask_human`、`write_file`、`read_file`、`bash` | `pi-agent`（`BaseToolsConfiguration` + 现有 `read_skill`） |
| **业务领域** | Tool：`search_sku`、`search_xhs_note`、`fetch_xhs_note`（及 Port/Searcher/Properties） | Phase1：`application` 内 `SkuToolsConfiguration` / `XhsToolsConfiguration`；Phase2：`forma-pi-extension` |
| **业务 Skill** | `scenes/ecommerce/**`、`scenes/xiaohongshu/**` 的 `SKILL.md` + `references/` | Phase1：仍在 starter（及 test mirrors）；Phase2：资源迁 extension（或 starter 依赖 extension 携带的资源） |

**产品规则（非 Tool SPI）：** `SceneCapabilityPackLoader`、`SkillRunProfile`、`ArtifactPersistPlugin` → 始终 **application**。

---

## 4. Phase 1 — 动态注入（含 `*.tool.json` schema）

### 4.1 Skill vs Tool 动态程度（钉死）

| | Skill | Tool（Phase 1） |
|--|-------|-----------------|
| 声明 | `SKILL.md` | `*.tool.json`（schema / 文案 / handlerClass） |
| 执行 | 无（说明书） | Java `ToolHandler` |
| 发现 | `classpath*:scenes/*/*/SKILL.md` | `classpath*:tools/**/*.tool.json` |
| 加能力 | 加 md | 加 json + Handler Bean（已有类可只加 json） |

### 4.2 Tool 聚合合同

```text
scanned = load ToolDefinition from classpath*:tools/**/*.tool.json
coded   = ToolHandler Beans（按类型/名）或 ToolHandlerAutoBinder.bindFromManifests(scanned, beanFactory)
ToolCatalog = InMemoryToolCatalog.merge(scanned, coded)
```

行为对齐现有 `InMemoryToolCatalog.merge`：

- **同 id**：scanned Manifest **覆盖** schema/text；**保留** coded Handler。  
- **仅有 Handler、无 json**：可 stub definition（过渡）；Phase 1 结束前 Base + 业务工具应都有 json。  
- **仅有 json、无 Handler**：catalog 可登记但不可执行 → 启动失败或明确报错（推荐 **fail-fast**）。

`handlerClass` 解析优先用 Spring 已有 Bean（`ToolHandlerAutoBinder`），避免无参 new 掉依赖注入。

### 4.3 `*.tool.json` 最小字段

```json
{
  "id": "search_sku",
  "description": "按配置检索 SKU 样本；每条命中含 https detailUrl",
  "text": "[search_sku] 按配置检索 SKU 样本。使用 query，可选 pageSize；禁止编造 detailUrl。",
  "handlerClass": "com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler",
  "schema": {
    "name": "search_sku",
    "description": "…",
    "parameters": { "type": "object", "properties": { … }, "required": ["query"] }
  }
}
```

- `id` / `handlerClass` / `schema`（或等价 parameters）必填。  
- 扫描器产出 `ToolDefinition`（仓内已有形状；若曾删除 JsonLoader，Phase 1 **恢复/重写**薄加载器即可）。  
- 资源布局建议：  
  - Pi 内置：`pi-agent/.../resources/tools/base/*.tool.json`  
  - 业务：`…/resources/tools/sku/search_sku.tool.json`、`tools/xhs/*.tool.json`（Phase2 随 extension）

### 4.4 配置类职责（Handler 侧，不再内联 schema）

| 类名 | 职责 | Phase 1 模块 |
|------|------|----------------|
| `BaseToolsConfiguration` | 注册 `ask_human` / `write_file` / `read_file` / `bash` 的 **Handler Bean**（+ workspace 依赖）；schema 来自 json | `pi-agent` |
| `SkuToolsConfiguration` | `SearchSkuToolHandler` + `SkuSearcher` / Port / Reranker / Properties | `application` |
| `XhsToolsConfiguration` | `SearchXhsNoteToolHandler` / `FetchXhsNoteToolHandler` + Port 管线 | `application` |

`read_skill`：可保留 `AgentConfiguration` 注册 Handler；schema 亦可迁 `tools/base/read_skill.tool.json`。

`PiToolCatalogConfiguration`：删除手写 `ToolDefinition.builder` 大段；改为「扫 json + merge handlers」或直接使用 pi-agent 统一 `ToolCatalog` `@Bean`（ebus 只贡献 Handler + json 资源）。`ModelCatalog` rerank overlay 可留在 ebus 侧独立配置。

### 4.5 Skill

- 继续 `Skills.loadFromClasspath(..., classpath*:scenes/*/*/SKILL.md)`。  
- 不要求 Phase 1 新增 Skill SPI。

### 4.6 验收

- 存在 `search_sku.tool.json`（等）时，catalog 中 schema 与 json 一致；Handler 仍可执行。  
- 新增测试：只加测试用 `*.tool.json` + mock Handler Bean → `resolve(id)` 成功且 schema 来自文件。  
- 缺 Handler 的 json → 启动失败（或测试断言显式错误）。  
- 现有 sku / xhs / workspace / ask_human 行为回归绿。  
- 无第二套 `@Primary` 整表覆盖。

---

## 5. Phase 2 — 物理拆 `forma-pi-extension`

### 5.1 模块

- Artifact：`forma-pi-extension`（业务前缀，符合 AD-13）。  
- `META-INF/spring.factories` → `EnableAutoConfiguration=…SkuToolsAutoConfiguration, …XhsToolsAutoConfiguration`（类名可与 Phase1 对齐或加 `Auto` 后缀）。  
- `starter` 依赖 `extension`。

### 5.2 迁入 / 不迁

| 迁入 extension | 不迁 |
|----------------|------|
| `SkuToolsConfiguration` / `XhsToolsConfiguration` 及 handler / Port 实现 | `BaseToolsConfiguration` + base `*.tool.json`（已在 pi-agent） |
| `tools/sku/*.tool.json`、`tools/xhs/*.tool.json` | 计费 / persist / SSE / `SceneCapabilityPackLoader` |
| `scenes/ecommerce`、`scenes/xiaohongshu`（若从 starter 挪出） | domain / CreditLedger |

### 5.3 依赖方向

```text
starter → extension → pi-agent（+ 所需 common / 少量 application 端口若不可避免则收紧）
pi-agent ↛ extension
pi-agent ↛ Apify / ebus 计费
```

若 extension 需 `ModelProvider` 做 rerank：只依赖 `pi-ai` 端口，配置 useCase 名仍由 properties 驱动。

### 5.4 验收

- 去掉 application 内 Sku/Xhs Tools 配置后，仅靠 starter→extension 仍能注册业务 tools + 扫到业务 skills。  
- 电商 / 小红书装包与计费回归绿。

---

## 6. 迁移顺序

1. Phase 1a：恢复/实现 `*.tool.json` 加载器；`ToolCatalog` = `merge(scanned, coded)`；缺 Handler fail-fast。  
2. Phase 1b：为 Base 五工具（含 `read_skill`）落地 json；`BaseToolsConfiguration` 只注册 Handler；迁入 pi-agent。  
3. Phase 1c：`SkuToolsConfiguration` / `XhsToolsConfiguration` + 对应 json；删掉 `PiToolCatalogConfiguration` 内联 schema。  
4. 回归。  
5. Phase 2：建 `forma-pi-extension`，迁 Sku/Xhs（java + json + 可选 scenes）；starter AutoConfiguration。  
6. 清理 application 残留。

---

## 7. 风险

| 风险 | 缓解 |
|------|------|
| 与旧设计「停用 *.tool.json」表述冲突 | 本规约显式 **恢复 schema 单轨在 json**；代码侧不再 builder 复制 schema |
| json 与 Handler 漂移 | CI 测：每个生产 tool id 必须同时有 json + 可解析 Handler |
| workspace 进 pi-agent 带业务依赖 | Handler 只依赖 ToolContext / workspace SPI |
| `ask_human` HITL | 注册在内核；拦截器仍在 application |
| Boot 2.7 | `spring.factories` |

---

## 8. 已拍板

| 主题 | 决定 |
|------|------|
| 顺序 | 先动态注入，再物理拆 extension |
| 平台四工具 + read_skill | **Pi 内置** |
| 配置类名 | `BaseToolsConfiguration`、`SkuToolsConfiguration`、`XhsToolsConfiguration` |
| Extension 名 | `forma-pi-extension`（Phase 2） |
| Tool schema | **Phase 1 即用 `*.tool.json`**；Handler 仍为 Java |
| Catalog 组装 | `InMemoryToolCatalog.merge` + `ToolHandlerAutoBinder` |
