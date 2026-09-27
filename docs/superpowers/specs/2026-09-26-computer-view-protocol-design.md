# Computer View 协议：业务成果 + 后端投影渲染文档

日期：2026-09-26  
范围：前端 Computer 预览抽象 + 后端选品投影；协议预留并实现素材块组件  
前置：故事 3.4 计费选品已交付；Listing 真结算属 3.6

## 问题

Adam's Computer 内选品 / Listing 预览写死在工作台页面；选品 `artifact_ready` 直接吐业务字段给 UI。每加一种成果就要改一页 Vue，渲染与结算契约耦死，缺少可复用的「结果预览」抽象。素材（Listing）与选品共用同一 Computer 壳，协议若不覆盖素材块，下一故事仍会写死第二套 DOM。

## 目标

1. 定义稳定的 **`ComputerDocument`（view）** 协议：Computer 只按 `blocks` 渲染。
2. 采用 **B：业务成果 + 渲染文档**；**view 由后端投影**（不由模型直接写 UI）。
3. 块集同时覆盖 **选品** 与 **素材**：选品用 `note`/`list`；素材用 `media`/`section`（本文件一次定稿）。
4. **本轮实现**：`PicklistViewProjector` + FE `ComputerRenderer`（含素材块组件）；Listing **演示**改为同构 `view` fixture（不接 3.6 真结算）。
5. 无 `view` 时旧选品字段可兜底；Listing 演示不再依赖页面内写死结构。

## 非目标

- Skill 不改为输出 blocks（模型仍只出业务 JSON）。
- 不做 Listing 真预占/落库/结算/`ListingViewProjector` 接线（3.6）。
- 不引入 HTML/Markdown 任意渲染；不在本文件外临时加块类型。
- 不改 CreditLedger / 选品落库表；不把 view 持久化为第二套真相（近端可不落库，仅 SSE / 前端 fixture）。

## 决策摘要

| 项 | 选择 |
|----|------|
| 协议形态 | B：domain + view |
| view 生产者 | 后端投影（演示素材用 FE fixture 同构 document） |
| 本轮接线 | 选品真投影 + 素材演示 view |
| 块集 | `note` · `list` · `media` · `section` |
| Computer 数据源 | 优先 `view`；选品无 view 时旧字段兜底 |

## 协议

### ComputerDocument

```ts
interface ComputerDocument {
  version: 1
  /** 语义 key（picklist / listingPreview）或旧版中文标题；FE 映射文案 */
  title: string
  /** 语义 key（settled / demo）或旧版中文状态 */
  status?: string
  blocks: ComputerBlock[]
}

type ComputerBlock =
  | { type: 'note'; text: string; tone?: 'mute' | 'default'; kind?: 'assumptions' | string }
  | {
      type: 'list'
      ordered?: boolean    // 默认 true
      items: ComputerListItem[]
    }
  | {
      type: 'media'
      role?: 'hero'        // 默认 hero：主图方案大平面
      alt?: string
      /** 可读 URL（近端演示或已签发）；真路径优先用 mediaObjectId 由 FE/网关解析 */
      src?: string
      mediaObjectId?: string
      /** 无图时的占位文案，如「主图方案预览」 */
      placeholder?: string
    }
  | {
      type: 'section'
      heading: string      // 如「详情标题」「详情正文」
      body: string
    }

interface ComputerListItem {
  /** 语义 key（priority）或旧版中文 badge；FE 映射文案 */
  badge?: string
  title: string
  /**
   * 正文行。投影器用 kind（priceBand/painPoint/angle/diff/niche）+ text + 可选 emphasis。
   * 旧版 label 中文或纯 string 仍可解析；FE 负责 locale，不拆业务字符串。
   */
  lines?: Array<
    | string
    | {
        text: string
        kind?: 'priceBand' | 'painPoint' | 'angle' | 'diff' | 'niche' | string
        label?: string
        emphasis?: 'default' | 'price'
      }
  >
  /**
   * 标签。投影器用 kind（demand/competition/margin/risk）+ 原始 text + tone。
   * FE 拼前缀文案；旧版纯 string / 已拼好的 text → 原样展示。
   */
  tags?: Array<
    | string
    | {
        text: string
        kind?: 'demand' | 'competition' | 'margin' | 'risk' | string
        tone?: 'neutral' | 'positive' | 'caution' | 'danger' | 'info' | 'safe'
      }
  >
}
```

- `version` 固定 `1`；未知 `blocks[].type`：FE 跳过该块并打日志，不整页崩。
- 禁止 `html` / 任意 script；文本纯文本转义。
- **`media`**：有 `src` 则展示图；否则若有 `mediaObjectId` 由 FE 按现有 Media 约定解析（3.6 接通）；皆无则显示 `placeholder`。
- **`section`**：素材文案的通用分段（标题/正文/展示说明均可叠多块）。

### 两种成果的块配方（约定）

| 成果 | document.title（语义 key） | FE 文案 | 典型 blocks |
|------|---------------------------|---------|-------------|
| 选品 | `picklist` | 选品清单 | `note`* → `list` |
| 素材 | `listingPreview` | 上架素材预览 | `media`(hero) → `section`(详情标题) → `section`(详情正文) → 可选 `note` |

### artifact_ready（选品，本轮）

在现有业务字段上增加 `view`：

```json
{
  "artifactType": "picklist",
  "artifactRef": "<picklistId>",
  "picklistId": "...",
  "runId": "...",
  "templateId": "domestic-generic-default",
  "disclaimer": "...",
  "assumptions": "...",
  "items": [ /* 业务条目，供兼容/调试 */ ],
  "view": { "version": 1, "title": "picklist", "status": "settled", "blocks": [ ... ] }
}
```

- **结算真相**仍是业务 `items` + DB；`view` 仅为展示投影。
- 近端不强制把 `view` 写入 MySQL；3.8 历史可用同一投影器重算。

### artifact_ready（素材，3.6 预告，本轮不接线）

形状对称：业务 Listing 字段 + `view`（`media`/`section` 配方）。本轮仅 FE 演示 fixture 使用同构 `ComputerDocument`。

## 选品投影规则（PicklistViewProjector）

输入：`PicklistArtifactDTO`。投影器**只写语义 key / kind + 原始字段值**，不写中文展示文案。

`blocks` 顺序：

1. 若有 `disclaimer` → `note`（tone=mute，text=原文）
2. 若有 `assumptions` → `note`（`kind: "assumptions"`，text=原文；FE 拼「假设：」）
3. 一条 `list`（ordered=true）：
   - title 以 `【优先试】` 开头 → `badge: "priority"`，title 去掉前缀（FE 映射「优先试」）
   - `lines`：`priceBand` / `painPoint` / `angle` / `diff` / `niche`（有则；均为 kind + 原始 text）
   - `tags`：`{ kind: "demand"|"competition"|"margin"|"risk", text: 原始四维值, tone }`（有则）

`title` = `picklist`；`status` = `settled`（FE →「选品清单」/「已结算」）。

## 素材演示 document（本轮 FE fixture）

对齐现 `DEMO_LISTING`，不写死 DOM：

```json
{
  "version": 1,
  "title": "上架素材预览",
  "status": "演示",
  "blocks": [
    { "type": "media", "role": "hero", "placeholder": "主图方案预览", "alt": "主图方案" },
    { "type": "section", "heading": "详情标题", "body": "…" },
    { "type": "section", "heading": "详情正文", "body": "…" }
  ]
}
```

3.6 时由 `ListingViewProjector` 按 `mediaObjectId[]` + 文案字段生成同等结构（可多张 `media` 或后续扩展 gallery——若需要再改本设计）。

## 前端

- `types/business/computerView.ts` — 协议类型
- `ComputerRenderer.vue` — 分发 `note` / `list` / `media` / `section`
- 块组件（可同文件或拆分）：Note / List / Media / Section
- 工作台 Computer：**唯一内容入口为 `ComputerRenderer`**；选品用后端 `view`（或旧字段兜底布局）；素材演示用 fixture `view`
- `useAgentPicklistRun`：解析 `data.view`；无 view → 旧选品布局兜底（**不**在 FE 再实现完整投影器）

## 后端触点（本轮）

- `PicklistViewProjector`
- `AgentApplicationService.toArtifactReady` → `put("view", …)`
- 单测：选品投影快照；`artifact_ready` 含 `view.version === 1` 与 `list`/`note` 块

## 成功标准

- 选品成功：Computer 仅靠 `view` 展示与现网等价信息。
- 素材演示：Computer 仅靠 fixture `view`（`media`+`section`）展示，页面无第二套 Listing 专用 DOM 树。
- 未知 block type 不白屏。
- Skill / 选品解析落库 / settle 不变；无 Listing 计费。

## 后续

- 3.6：`ListingViewProjector` + 真 `artifact_ready.view`；`mediaObjectId` → 可读 URL。
- 3.8：历史详情复用投影器。
- 若需多图画廊 / SKU 元信息条：先修订本设计再加块（例如 `gallery` / `kv`）。
- **平台化演进（2026-09-27）**：Skill **双轨**（`artifact` + `view`）、无 Skill 默认 `markdown`、Projector 降为 Fallback——见 [`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md)。通过后以该规约为准，本文件「Skill 不输出 blocks」非目标作废。

## 开放风险

- FE 双份投影漂移 → 有 `view` 只用后端；无 view 仅旧选品布局。
- `media` 的 URL 签发策略属 MediaStore，本协议只携 `mediaObjectId`/`src`，不发明第二套 URL 真相（对齐 Spine AD-9）。
