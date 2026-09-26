# 官方 Pi Skill 形态适配 + skill/tool 瘦身

**Date:** 2026-09-26  
**Status:** implemented（见 `docs/superpowers/plans/2026-09-26-official-pi-skills-adapter.md`）  
**Baseline:** `abf52e1`（3.2 电商能力包接线已合入）  
**Modules:** `lippi-pi-agent`, `lippi-ai-ebus-application`, `lippi-ai-ebus-starter`

## Problem

1. 3.2 落地的电商能力包使用 `*.skill.json` + `promptRef` → md，对齐的是仓内 LIMS/Hermes 遗留的 `SkillManifest`，**不是**官方 [Pi Agent Skills](https://pi.dev/docs/latest/skills)（目录 + `SKILL.md`）。
2. `lippi-pi-agent` 的 skill/tool 类型偏多（version 双键、`graphTopology`、`maxToolLevel`、`modelUseCase`、JSON Manifest 双轨等），与官方「扫文件 → 摘要进提示词 → 按需读全文 / 按名启用 tool」的薄模型不一致。
3. 内置 certificate-ocr 等 LIMS 技能对本产品无用，应移除而非迁移。

## Goal

在 **不更换** `AgentSession` / 不引入官方 TS runtime 的前提下：

- 以 **`PiResourceLoader` 为 skill/tool/prompt 发现入口**（对标官方 ResourceLoader），收口旁路 bootstrap；
- **`SceneCapabilityPackLoader` 留在 Application**：按 `sceneCode` 选型/校验，不重复解析正文；
- 场景能力包改为官方目录/`SKILL.md` 形态；
- 删除 Manifest 多余字段、LIMS 遗留 skill，以及 Tool level/JSON 双轨；
- Tool 对齐「注册 + 按名启用」。

## Non-goals

- 不替换 `AgentSession`，不引入 pi-mono TS 运行时。
- 不做选品/Listing 真结算、`artifact_ready` / `run_settled`（仍属 3.4/3.6）。
- 不做运营后台改 skill；不接受浏览器下发 system/tool 正文（AD-16 仍成立）。
- 不把本仓 tool 整棵换成官方内置集（`bash`/`edit`/`write`/…）；不在本故事把 `read_skill` 重做成通用 `read` 文件工具。
- 不重做 HITL / resume 审批协议；若保留审批，改为「点名工具」或后续故事，默认关（AD-S2）。

## Decisions（已确认）

| 项 | 选择 |
|----|------|
| 做法 | **A** — `SKILL.md` 适配加载 + 瘦身 Manifest/类，保留 Java 编排 |
| 覆盖范围 | 全量场景 skill 官方化；**删除** LIMS 内置 skill（不迁移） |
| 命名 | 官方连字符：`ecommerce-picklist` / `ecommerce-skulist` |
| 多余字段 | 资源与 Java **一起删**（非 metadata 塞私货、非仅默认值填洞） |
| 对 3.2 | **Rework 资源格式**；接线意图保留。建议 **3-2 标 done**，新开 **3-2b**（或等价键）做本设计 |
| Tool 改造 | **对齐官方「按名启用」**（见下节）；删 `ToolLevel` 对 skill 的强制；薄化策略闸门 |

## Shape

### 分层（对齐官方 ResourceLoader）

官方 Pi：`DefaultResourceLoader` 统一发现 **skills / tools（经 extension+custom）/ prompts / …**；会话从 loader 取资源，再按名启用。

本仓应对齐同一分层，而不是再搞第二套「场景专用 skill 解析器」：

```text
┌─────────────────────────────────────────────────────────┐
│ Application（ebus）                                     │
│  SceneCapabilityPackLoader                              │
│   · 输入 sceneCode                                      │
│   · 从已加载的 Skill 目录中选出该场景包                   │
│   · 校验电商必备 skill id；缺包人话失败                   │
│   · 不负责解析 SKILL.md / 不重复扫 classpath 正文         │
└──────────────────────────▲──────────────────────────────┘
                           │ 查询 / 校验（已注册的 skill）
┌──────────────────────────┴──────────────────────────────┐
│ pi-agent：PiResourceLoader（对标官方 ResourceLoader）     │
│  · 加载 skills（SKILL.md）                               │
│  · 加载 / 注册 tools（薄定义 + handler）                 │
│  · 加载 prompts                                          │
│  · snapshot / reload / 读 skill 正文（read_skill 同源）   │
└─────────────────────────────────────────────────────────┘
```

**现状偏差（本故事要收）：**  
`DefaultPiResourceLoader` 已存在，但 skill/tool 主要在 `Classpath*Bootstrap` + 独立 `SkillConfig`/`ToolCatalog` Bean 旁路装入，loader 多半只是委托；`SceneCapabilityPackLoader`（3.2）又按 `*.skill.json` **再扫一遍**。  
**目标：** 发现与解析收口到 ResourceLoader（或其唯一委托的 Store）；场景层只做 **按 sceneCode 选包/校验**。

### 资源布局

```text
…/resources/scenes/{sceneCode}/{skill-name}/SKILL.md

例：
scenes/ecommerce/ecommerce-picklist/SKILL.md
scenes/ecommerce/ecommerce-skulist/SKILL.md
```

`SKILL.md`：YAML frontmatter（官方字段）+ Markdown 正文（规则 SSOT）。  
由 **PiResourceLoader** 扫入全局 Skill 目录；场景码对应子树 `scenes/{sceneCode}/**`。

### 瘦身后的 Skill 值对象

| 字段 | 来源 |
|------|------|
| `id` | frontmatter `name` |
| `description` | frontmatter `description` |
| `promptRef`（或等价 body 定位） | 指向该 `SKILL.md`，供 `read_skill` 读全文 |
| `allowedTools` | frontmatter `allowed-tools`（空格分隔）；**电商骨架默认仅 `read_skill`** |

**删除字段：** `version`、`displayName`、`graphTopology`、`maxToolLevel`、`modelUseCase`、`contentHash`。  
Registry 改为 **按 id** 索引（不再 `id@version`）。  
**不再有** skill 侧工具「最高等级」概念——本轮能用哪些 tool **只看名字列表**（对标官方 `setActiveTools` / skill `allowed-tools`）。

### Tool：对齐官方「注册 + 按名启用」

官方模型（参照 pi-mono SDK / Extensions）：

1. **注册** — `ToolDefinition`：name、description、parameters、`execute`
2. **启用** — 会话/回合 **名字白名单**（`tools` / `excludeTools` / `setActiveTools`）
3. **策略** — extension 拦 `tool_call`；**不是**给每个 tool 贴 READ/WRITE/FORBIDDEN

本仓目标态（仍 Java，不引入 TS）：

| 概念 | 本仓落地 |
|------|----------|
| 注册 | 薄「工具定义」：`name`、`description`、`schema`、`handler`（代码注册或单文件 bootstrap）；近端至少注册 `read_skill` |
| 启用 | Active skill 的 `allowedTools` → `TurnBinder` 只投影这些名字的 schema/text（等价 `setActiveTools`） |
| 策略 | `ToolPolicyExtension` **变薄**：未注册或未激活 → 拒绝；**不再读 `ToolLevel`**。WRITE 点名审批不在本故事做（默认关） |

**本故事对 Tool 的硬动作：**

- 去掉 tool 定义上的必填 `level` / `version` / 展示用冗余字段（若类型合并则直接删 `ToolLevel`）
- 删除或停用 `*.tool.json` Manifest 双轨与 `ToolDefinitionJsonLoader`（改为代码侧定义；`read_skill` 可内联注册）
- 删除仅测用 / 无 handler 的声明型工具资源
- 合并过厚的 `ToolBinding` / `Tool` / `AutoBinder` 层次（能合成「定义+执行」一层就合成）
- `ExtensionRunner` 仍可要求有一个策略扩展，但其逻辑改为「按名」，不是「按级」

**本故事明确不做：**

- 引入官方内置 `bash`/`edit`/`write`/`grep`/`find`/`ls`
- 将 `read_skill` 改名为通用 `read` 并开放任意路径读文件
- 重做 HITL resume / `ToolDecision` 全协议（若代码路径因删 `ToolLevel` 必须改，只做最小编译通过 + 行为：未知 tool 拒绝）

### 加载路径

1. **PiResourceLoader（启动）**：扫 `classpath*:scenes/*/**/SKILL.md`（+ 可选全局 skills；LIMS 清空后可空）→ 解析 → SkillStore → seal；注册近端 tools（至少 `read_skill`）；加载 prompts。无场景侧 `*.skill.json` / tool json Manifest 双轨。
2. **SceneCapabilityPackLoader（Application，prompt 前）**：输入 `sceneCode` → 向 ResourceLoader/SkillStore **查询**该场景应具备的 skill（按约定：已注册且属于 `scenes/{sceneCode}/` 的 id，或显式清单）→ 电商校验必须含 `ecommerce-picklist` + `ecommerce-skulist`。**不解析 md、不第二次 classpath 读正文。**
3. **streamEmptyRun**：装包校验通过后 `skillId=ecommerce-picklist`；失败人话 + release，不 settle。
4. **回合**：`allowedTools` → TurnBinder 按名裁剪模型可见 tools。

### 类收敛（目标态）

**pi-agent — ResourceLoader 核心**

- `PiResourceLoader` / `DefaultPiResourceLoader`：真正负责 skills + tools + prompts 的发现与快照（对标官方）  
- **`Skill`** 值对象 + **`SkillCatalog`**（register / resolve / `all` / `listByScene`）  
- **`Skills`**：`parse(SKILL.md)` + `loadFromClasspath`（对齐官方 `skills.ts`）  
- 薄 Tool 注册表 + `read_skill`  
- ActiveSkill / SkillSelector / SkillCatalogPrompt（回合投影）  
- 变薄策略扩展（按名拒绝）  

**Application — 场景层**

- `SceneCapabilityPack` + `SceneCapabilityPackLoader`：**选型/校验 only**  
- `EbusSkillConfiguration`：若仍需要，只配置「loader 多扫 scenes 路径」，不再自己 seal 第二份 SkillConfig 逻辑分叉  

**删除**

- `SkillGraphTopology`、`SkillManifestJsonLoader`、旧 json `ClasspathSkillBootstrap`  
- LIMS skill 资源与专测、`maxToolLevel`  
- `ToolLevel`、tool json Manifest 双轨、过厚 Binding/AutoBinder  
- SceneCapabilityPackLoader 内对 `*.skill.json` 的独立解析路径（改为查 Store）  

### 官方 Pi 对照（意图，非逐行移植）

| 官方 | 本仓目标 |
|------|----------|
| `DefaultResourceLoader` | `PiResourceLoader` 统一 skills/tools/prompts |
| 目录 + `SKILL.md` | 同左（classpath 场景树由 loader 扫） |
| 启动只取 name/description | Catalog 摘要；全文 `read_skill` |
| `allowed-tools` | → `allowedTools` → 本轮 active tool **名字**列表 |
| `registerTool` + `setActiveTools` | loader/代码注册 + TurnBinder 按名裁剪 |
| （无「场景包」原语） | **Application** `SceneCapabilityPackLoader` 按 sceneCode 选包 |

### 命名对照（已落地）

| 旧名 | 新名 | 说明 |
|------|------|------|
| `SkillManifest` | `Skill` | 值对象 |
| `SkillMdLoader` / `SkillMdBootstrap` | `Skills`（`parse` / `loadFromClasspath`） | 对齐官方 `skills.ts` |
| `SkillConfig` / `InMemorySkillConfig` / `SkillRegistry` | `SkillCatalog` / `InMemorySkillCatalog` | 技能目录（对标 `loader.getSkills()`） |
| `ToolManifest` | `ToolDefinition` | 薄工具定义 |
| `ToolRegistration` | `Tool` | 已装工具 = 定义 + handler |
| `ToolConfig` / `DefaultToolConfig` / `ToolRegistry` | `ToolCatalog` / `InMemoryToolCatalog` | 工具目录（对标 `getAllTools` / `registerTool` 背后集合） |
| `manifests()` / `getManifest()` | `all()` / `getDefinition()` / `getSkill()` | API |

**刻意保留：** `ToolBinding`、`ToolHandler`、`ToolPolicyExtension`（Java 侧绑定/策略层）。  
官方无 `*Registry` / `*Catalog` 类名；本仓用 Catalog 表达「可查询的已装目录」，便于 Spring 注入。

## Sprint / 故事处置

- **3-2**（电商能力包按 sceneCode 加载）：接线与 AD-16 契约 **done**；资源格式由 3-2b 替换。  
- **新建 3-2b**（建议标题：官方 Agent Skills 形态与 skill/tool 瘦身）：实现本设计；更新 `sprint-status.yaml`；必要时修订 AD-16 包布局假设（路径钉死为上表）。

## Acceptance

1. 电商 classpath 仅官方双 skill 目录，无场景侧 `*.skill.json`。  
2. **PiResourceLoader** 启动后可 resolve `ecommerce-picklist` / `ecommerce-skulist`；空跑默认注入前者。  
3. **SceneCapabilityPackLoader** 只做 sceneCode→包校验/选型，不再独立解析 skill 正文；与 loader 无双份扫描分叉。  
4. 缺包 → 人话失败 + release，不 prompt / 不 settle。  
5. pi-agent 无 certificate-ocr 等 LIMS skill 资源；相关专测删除或改写。  
6. Skill 模型无 version/topology/maxToolLevel/modelUseCase/contentHash；`allowed-tools` 驱动本轮 tool 名字白名单。  
7. Tool 路径无 `ToolLevel` 依赖；无 json Manifest 双轨；电商回合仅暴露 `allowed-tools` 中的工具（默认 `read_skill`）。  
8. 未知或未激活 tool 调用被拒绝；相关单测与 `mvn -pl lippi-ai-ebus-starter -am test` 中受影响测例绿。

## Verification

```bash
mvn -pl lippi-ai-ebus-starter -am test
```

（若仅改后端 skill/tool，可不强制前端 lint。）

## Out of scope follow-ups

- 官方内置 coding tools（`bash`/`edit`/`write`/…）与通用 `read` 文件工具。  
- WRITE 点名审批 / HITL 协议重做。  
- 灰卡场景占位 skill。  
- 与官方 skill 目录互拷（多 harness 共用）——本设计只保证**形态**兼容。
