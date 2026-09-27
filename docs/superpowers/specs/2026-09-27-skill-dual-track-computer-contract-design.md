# Skill 双轨输出 × Computer 通用组件 × 无 Skill 默认 Markdown

日期：2026-09-27  
状态：草案（设计规约，本轮不强制改代码）  
范围：Computer **通用组件**合同、Skill 如何对准该合同输出、无 Skill 默认 Markdown、业务落库轨与积分 settle 的边界  
前置：[`2026-09-26-computer-view-protocol-design.md`](./2026-09-26-computer-view-protocol-design.md)；Spine AD-4 / AD-5 / AD-6 / AD-7  
修订关系：相对 09-26「Skill 不输出 blocks / view 仅后端投影」——**本规约改为：先定通用组件；Skill 主产出 `view`；业务 `artifact` 为可选第二轨；Projector = 策略链（规范化 + 无 Skill→markdown）。**

---

## 问题

1. 编排与预览按「选品 / Listing」定制，通用壳吃不到通用数据。  
2. 若在输出合同里先写死 `artifactType: "picklist"`，平台层又被业务类型绑死。  
3. 需要：**FE 只认通用组件**；Skill / Agent 学会填这些组件；业务类型只出现在**落库/计费**插件里。

## 目标

1. **先定义并冻结 Computer 通用组件白名单**（与业务名无关）。  
2. Skill 终态**首先**产出合法 `view`（blocks 只用白名单）。  
3. 计费场景**另外**带可校验的业务包（下文称 `artifact`）；类型名由业务插件解释，**不进入 Computer 渲染协议**。  
4. **Projector 策略模式**（`supports` + `project`）：对已有 `view` 做**规范化**；**无 Skill** 时把终态文本**包成 markdown**；默认不 settle。  
5. **Settle** 仍只认「可用 `artifact` 已持久化」（AD-5）。

## 非目标

- 本文件不实现代码拆分。  
- 不引入任意 HTML / 未登记 block type。  
- 不在通用合同里枚举 `picklist` / `sku` 等业务枚举作为渲染必需字段。

---

## 决策摘要

| 项 | 选择 |
|----|------|
| 平台核心 | **通用组件**（§1），Computer 只渲染它们 |
| Skill 主输出 | `view: ComputerDocument` |
| Skill 次输出 | 可选 `artifact`（不透明，供校验落库） |
| 业务类型 | 仅 Skill 元数据 / persist 插件，不进 view |
| 无 Skill | **NoSkillMarkdown** 策略：终态文本 → 单个 `markdown` block |
| Projector | **策略模式**（`supports` + `project`）：规范化已有 view；无 Skill 包 markdown（§5） |

---

## 1. 通用组件白名单（v1）——先定这个

整份协议的「原子」是 **block**。所有 Skill / 无 Skill / Projector 策略最终都必须变成下面之一。

### 1.1 ComputerDocument

```ts
interface ComputerDocument {
  version: 1
  /** 文档标题：建议语义 key（如 report / draft），FE 字典出文案；也允许直接短文案 */
  title: string
  /** 可选状态 key 或短文案（如 ready / draft） */
  status?: string
  blocks: ComputerBlock[]
}

type ComputerBlock =
  | MarkdownBlock
  | NoteBlock
  | ListBlock
  | MediaBlock
  | SectionBlock
```

未知 `type`：FE **跳过并打日志**，不白屏。

### 1.2 五个通用组件

| type | 组件职责 | 字段（稳定） |
|------|----------|----------------|
| `markdown` | 长文 / 默认 Agent 输出 | `text: string`（Markdown 子集，见 §1.3） |
| `note` | 弱提示、脚注、假设 | `text: string`；可选 `tone?: "mute"\|"default"`；可选 `kind?: string`（FE 可加前缀） |
| `list` | 结构化条目列表 | 见 §1.4 |
| `media` | 一张图平面 | `role?: "hero"`；`alt?`；`placeholder?`；`mediaObjectId?`；`src?`（演示/已签发） |
| `section` | 小标题 + 正文 | `heading: string`；`body: string`；可选 `tone?` |

**禁止**在白名单里出现：`picklist`、`sku`、`电商` 等业务 type。业务差异只体现在 **怎么组合这些组件**，以及 **list 里 kind 字符串的词典**（产品包可扩展词典，协议不写死业务 kind 枚举）。

### 1.3 Markdown 子集（`markdown`）

允许：段落、标题、列表、粗斜体、行内代码、`https:` 链接。  
禁止：HTML、脚本、iframe；配图走 `media`，不走 markdown 外链图（v1）。

### 1.4 `list` 组件（通用，非选品专用）

```ts
interface ListBlock {
  type: "list"
  ordered?: boolean  // 默认 true
  items: ListItem[]
}

interface ListItem {
  title: string
  badge?: string           // 语义 token 或短文案；FE 可映射
  lines?: ListLine[]
  tags?: ListTag[]
}

interface ListLine {
  text: string
  /** 可选语义 token，FE 词典 → 标签文案；协议不规定业务枚举 */
  kind?: string
  label?: string           // 兼容旧数据；新产出优先 kind
  emphasis?: "default" | "price"
}

interface ListTag {
  text: string
  kind?: string
  tone?: "neutral" | "positive" | "caution" | "danger" | "info" | "safe"
}
```

说明：`kind: "painPoint"` 可以出现在**某个 Skill 的写作指引**里，并在 FE 词典登记；**本规约不把 painPoint 写成平台枚举**。

### 1.5 最小合法 `view` 例子（无任何业务类型名）

```json
{
  "version": 1,
  "title": "report",
  "status": "ready",
  "blocks": [
    { "type": "note", "tone": "mute", "text": "以下为推断结果，非实时数据。" },
    {
      "type": "list",
      "ordered": true,
      "items": [
        {
          "badge": "priority",
          "title": "示例条目 A",
          "lines": [
            { "kind": "fact", "text": "19–39 元", "emphasis": "price" },
            { "kind": "fact", "text": "一句话要点" }
          ],
          "tags": [
            { "text": "高｜稳定", "tone": "positive" }
          ]
        }
      ]
    },
    { "type": "markdown", "text": "## 补充说明\n可选长文。" },
    { "type": "media", "role": "hero", "placeholder": "主图占位" },
    { "type": "section", "heading": "段落标题", "body": "段落正文" }
  ]
}
```

加新组件：**先改本表 + FE Renderer，再允许 Skill 使用。**

---

## 2. Skill 如何输出（对准通用组件）

### 2.1 终态形状（view 优先）

模型终态**一个 JSON**（可围栏）。**平台层推荐形态：**

```json
{
  "view": {
    "version": 1,
    "title": "report",
    "status": "ready",
    "blocks": []
  },
  "artifact": { }
}
```

| 字段 | 含义 | 谁消费 |
|------|------|--------|
| `view` | **唯一**给 Computer 的数据；blocks ∈ §1 白名单 | FE Renderer；所有 Skill 宜填 |
| `artifact` | 业务不透明包；形状由**该 Skill 的落库校验器**解释 | Persist / settle 插件；Computer **忽略** |

**不要**把 `artifactType: "picklist"` 写进「通用输出合同」示例当必填顶栏字段。  
若计费需要类型：放在 **Skill 包配置 / front matter**（§2.3），或放在 `artifact` 内部由插件读取——**渲染路径看不见它**。

| 场景 | `view` | `artifact` |
|------|--------|------------|
| 计费 Skill | **宜填**；经 **Normalize** 策略后上屏 | **必填**且过插件校验 |
| 非计费 Skill | **宜填**；Normalize | 可选 |
| 无 Skill | **NoSkillMarkdown** 策略合成 | 无 |

### 2.2 Skill 正文「分步」（通用写法）

1. 若本 Skill 要计费：先写出满足校验器的 `artifact`。  
2. 再把同一事实编成 `view.blocks`，**只使用 §1 组件**。  
3. 自检：结算相关事实以 `artifact` 为准；`view` 不得编造 artifact 没有的关键结论。

### 2.3 Skill 包侧配置（业务类型待在这里）

```text
# Skill 元数据（不是 Computer 协议）
output:
  billing: true|false
  # 仅 persist/settle 插件使用，例如 picklist | sku | none
  persistAs: <plugin-id> | none
  requiresView: true|false
```

平台 Run 管道只问：`billing?` → 调哪个 **persist 插件**；**不**把 `persistAs` 塞进 `view`。

---

## 3. 无 Skill：由 NoSkillMarkdown 策略产出

不单独写死在 Runtime 里拼 JSON；走 §5 策略链。效果等价于：

```json
{
  "version": 1,
  "title": "draft",
  "blocks": [
    { "type": "markdown", "text": "<finalResponse>" }
  ]
}
```

默认不 settle。聊天区仍可有 `message_delta`。

---

## 4. Runtime 管道（与业务名解耦）

```
reserve? → prompt(skill|none) → final
  → 组装 ViewProjectContext
  → ViewProjectorChain.project(ctx)   // 策略：Normalize | NoSkillMarkdown | …
  → 若 billing：artifact 校验 → persist → settle → artifact_ready(view + artifactRef)
  → 若失败：release + run_failed
```

`artifact_ready` 给 FE 的**渲染字段只有 `view`**。业务 ref 仅用于历史/跳转：

```json
{
  "artifactRef": "<id>",
  "view": { "version": 1, "title": "report", "blocks": [] }
}
```

（可选附带 `persistAs` 供客户端埋点，**禁止** FE 按该字段换 DOM 树。）

---

## 5. Projector：策略模式（`supports` + `project`）

平台只认这一套接口；**不做**「按选品字段手写页面」的主逻辑。现有 `PicklistViewProjector` 过渡期可改造成某业务策略，或逐步退役。

### 5.1 接口

```java
public interface ComputerViewProjector {
    /** 是否处理该上下文；链上按注册顺序找第一个 true */
    boolean supports(ViewProjectContext context);

    /** 产出 §1 合法 ComputerDocument；supports 为 true 时不得返回 null */
    Map<String, Object> project(ViewProjectContext context);
}
```

```java
public final class ViewProjectContext {
    private final boolean skillBound;           // 本回合是否绑定了 Skill
    private final String finalResponse;         // 模型终态原文
    private final Map<String, Object> rawView;  // 解析出的 view，可能为 null / 脏
    private final Object artifact;              // 可选；规范化一般不读
    // getter…
}
```

### 5.2 链（顺序固定）

```text
ViewProjectorChain:
  for (p : projectors) if (p.supports(ctx)) return p.project(ctx);
  → 计费且仍无 view：失败（不 settle）
  → 非计费：可再兜底空 document 或失败（产品定；默认失败更清晰）
```

### 5.3 内置策略（v1）

| 策略 | `supports` | `project` 做什么 |
|------|------------|------------------|
| **NormalizeViewProjector** | `rawView != null`（有候选 view） | 校验 `version===1`；丢掉未知 `blocks[].type`；补默认 `ordered`；去掉非法 `tone`/`emphasis`；保证 `title` 非空；**不**根据业务字段发明内容 |
| **NoSkillMarkdownProjector** | `!skillBound` 且有非空 `finalResponse` | 包成单个 `markdown` block（§3）；`title` 用 `draft` |

注册顺序建议：`NormalizeView` → `NoSkillMarkdown`。

### 5.4 明确不做（本规约）

- 再从 `artifact.items`「翻译」出 list（旧 Picklist 主路径）——若过渡期仍需要，单独加第三策略（如 `LegacyPicklistFallbackProjector`），`supports` = skillBound && rawView==null && artifact 可识别；**默认不进 v1 必选链**。  
- 在策略里写中文「痛点 / 选品清单」——文案归 FE 词典。

### 5.5 行为例子

1. **有 Skill + 合法 view** → Normalize 命中 → 洗一遍 blocks 后返回。  
2. **有 Skill + 脏 view（含未知 type）** → Normalize 丢掉未知块，保留合法块。  
3. **无 Skill + 一段模型正文** → Normalize 不命中 → NoSkillMarkdown 命中 → `{ markdown: 正文 }`。  
4. **有 Skill、计费、无 view** → 两策略都不该「瞎编业务 list」→ 链失败 → release（或仅当显式挂上 Legacy 策略才补）。

---

## 6. 业务 Skill 如何「用」通用组件（附录例，非协议本体）

以下仅说明「选品 Skill 可以这样编 view」，**不**把字段升格为平台类型。

`view` 仍只用 `note` + `list`；`artifact` 内可以是选品 JSON（校验器私有）：

```json
{
  "view": {
    "version": 1,
    "title": "report",
    "status": "ready",
    "blocks": [
      { "type": "note", "tone": "mute", "text": "基于通用电商知识推断，非实时平台数据" },
      {
        "type": "list",
        "ordered": true,
        "items": [
          {
            "badge": "priority",
            "title": "硅胶沥水垫（多色）",
            "lines": [
              { "kind": "priceBand", "text": "19–39 元", "emphasis": "price" },
              { "kind": "painPoint", "text": "水槽边易积水难打理" },
              { "kind": "angle", "text": "租房厨房刚需且轻小好发" },
              { "kind": "diff", "text": "多色套装+厚度对比主图" },
              { "kind": "niche", "text": "厨房沥水收纳" }
            ],
            "tags": [
              { "kind": "demand", "text": "高｜台面积水刚需", "tone": "positive" }
            ]
          }
        ]
      }
    ]
  },
  "artifact": {
    "templateId": "domestic-generic-default",
    "disclaimer": "…",
    "items": [ { "title": "【优先试】硅胶沥水垫（多色）", "painPoint": "…", "…": "…" } ]
  }
}
```

FE 若登记了 `painPoint → 痛点` 词典则显示标签；未登记则只显示 `text` 或回退 `kind` 原文——**组件仍然通用**。

素材类 Skill：同一 `view` 合同，blocks 改为 `media` + `section`，`artifact` 换另一校验器。

---

## 7. 成功标准

- [ ] §1 五组件 + Document 成为 FE/解析的唯一渲染合同；测试不含「必须出现 picklist 字符串」。  
- [x] Skill 输出示例以 `view` 为首；`artifact` 可选且不进 Renderer。（选品计费仍必填 `artifact`；parser 已认信封，扁平 JSON 兼容）  
- [x] 存在 `ComputerViewProjector` + Chain；单测覆盖 Normalize / NoSkillMarkdown 的 `supports` 分流。  
- [x] 无 Skill → 仅经 NoSkillMarkdown 得到一个 `markdown` block。  
- [x] 业务枚举只出现在 Skill 元数据 / persist 插件，不出现在 ComputerBlock.type。

## 8. 开放问题

1. `title` / `status` 是否强制语义 key（禁止中文）——建议新产出用 key，旧中文透传兼容。  
2. list.`kind` 是否要平台级公共词典（`fact` / `price` / …）再允许产品扩展——可第二版收紧。  
3. `view` 是否落库——仍建议 3.8 再定。

## 9. 建议落地顺序

1. 评审 §1 组件表 + §5 策略接口。  
2. FE 补齐 `markdown`，Renderer/解析与白名单一致。  
3. 实现 `ComputerViewProjector` / `ViewProjectContext` / Chain + 两策略单测。  
4. 改 Skill 文档：先教 `view`；业务放 `artifact`。  
5. Runtime 接入 Chain；再瘦 `streamPicklistRun`；旧 `PicklistViewProjector` 标为 Legacy 可选策略或删除。

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-27 | 初稿 |
| 2026-09-27 | 通用组件升为 §1；去掉合同里的 `artifactType: picklist` |
| 2026-09-27 | **Projector = 策略模式**：Normalize + NoSkillMarkdown（`supports`/`project`）；弱化「从业务数据生成 list」为主路径 |
| 2026-09-27 | **落地**：Chain（Normalize → LegacyPicklist → NoSkillMarkdown）接入 `artifact_ready.view`；FE `markdown` block；SKILL 双轨说明。信封解析 / 去 Legacy / CreditHold 拆分仍属后续 |
| 2026-09-27 | **信封落地**：`PicklistArtifactParser` 解析 `{view,artifact}`；`rawView` 走 Normalize；SKILL 主契约改为 view 优先；扁平 JSON 仍兼容 Legacy |
| 2026-09-27 | **管道分层**：`CreditHoldSupport` + `ComputerViewResolver`；`streamPicklistRun` 顺序改为 parse→persist→**project 门禁**→settle→emit |
| 2026-09-27 | **通用 Run**：`streamGenerationRun` + `SkillRunProfile` + `ArtifactPersistPlugin`；`POST /api/v1/agent/runs`；empty/picklist 为别名 |
