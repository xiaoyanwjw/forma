# Computer Doc Preview Design

**Date:** 2026-10-03  
**Status:** accepted  
**Decision:** 方案 1 — 薄文档预览：`view` 只承载 `markdown | html`；前端 GitHub README 皮直渲染；手递按钮由 skill 写进 HTML，前端只监听

**Related:**  
- [`2026-10-03-unified-workspace-design.md`](./2026-10-03-unified-workspace-design.md)（Workspace 壳；本设计替换其 Computer 块渲染与 `itemHandoffs`）  
- [`2026-10-03-computer-gitview-design.md`](./2026-10-03-computer-gitview-design.md)（Git 顶栏壳可保留）  
- [`2026-09-26-computer-view-protocol-design.md`](./2026-09-26-computer-view-protocol-design.md)（旧 blocks 协议；本设计取代预览路径）

---

## Goal

右侧 Computer 预览从「通用块组件拼装」改为 **skill 输出文档、前端按 format 渲染**：

- 服务端 / skill：`view` 支持 `markdown` 或 `html`
- 前端：消毒后直渲染，样式先用 GitHub README
- 行内手递（写成笔记 / 生成上架等）：skill 在 HTML 里写完整按钮，前端事件委托开跑

这样新 skill 不必再扩前端块类型，预览层保持薄且通用。

## Non-goals

- iframe 沙箱预览  
- 双轨「结构化 blocks view + 另附 html」  
- 继续维护 `list` / `note` / `section` / storyboard 等通用块组件作为主路径  
- **旧 v1 `blocks` view 兼容**（直接不管；新协议只认 v2）

---

## Decisions locked

| 项 | 选择 |
|----|------|
| 形态 | **方案 1** 薄文档预览 |
| 手递 | 保留；HTML 约定属性触发（会话选项 B） |
| 按钮作者 | Skill 写出完整可点节点（选项 A） |
| 下一轮文案 | 按钮自带 `skillId` + 完整 `prompt`，前端原样开跑（选项 A） |
| format | `markdown` \| `html` 平权，字段标明（选项 C） |
| 覆盖范围 | **全部成果一刀切**（含 Listing 分镜 / 配图位）（选项 B） |
| 旧 v1 | **不管** |

---

## §1 View protocol

`final.json` 信封不变：`{ "view", "artifact" }`。  
只改 **`view`**；`artifact` 仍服务结算 / 落库 / 检索，不负责展示。

### v2 shape

```json
{
  "version": 2,
  "title": "Mac Mini 桌搭 · 居家办公种草选题清单",
  "format": "html",
  "content": "<article class=\"markdown-body\">...</article>"
}
```

| 字段 | 含义 |
|------|------|
| `version` | `2` = 文档预览协议 |
| `title` | Computer 顶栏文件名 / 标题 |
| `format` | `markdown` \| `html` |
| `content` | 正文；`html`（或 md 内嵌 HTML）可含手递按钮 |

新 skill **只出 v2**。解析到非 v2 / 缺 `format`+`content` → 预览失败态即可，不做 blocks 降级。

### Handoff control (in `content`)

```html
<button
  type="button"
  data-adam-action="handoff"
  data-adam-skill-id="xhs-note"
  data-adam-prompt="请把「…」写成小红书种草笔记。…"
>写成笔记</button>
```

前端事件委托：命中 `data-adam-action="handoff"` →

```ts
startSkillRun({
  skillId: dataset.adamSkillId,
  text: dataset.adamPrompt,
  sceneCode,
  sessionId,
})
```

SceneWorkspaceSpec 的 **`itemHandoffs` / 行内 `buildText` 退役**。  
`toolbarHandoffs` 一并退役：顶栏动作若仍需要，也改由文档内按钮承担。

---

## §2 Frontend rendering

**壳：** 保留 Computer 分栏 + Git 顶栏（文件名来自 `title`）。  
**正文：** 单一 `DocPreview`（名称实现时可定），替换 `ComputerRenderer` 块拼装主路径。

流水线：

1. `format === 'markdown'` → `marked` → HTML  
2. `format === 'html'` → 使用 `content`  
3. **消毒**（如 DOMPurify）  
4. 注入带 `markdown-body` 的容器  
5. 样式：`github-markdown-css`（GitHub README 皮）  
6. 点击委托处理手递按钮  

### Sanitizer allowlist (要点)

- 文档标签：`p` / `h1–h3` / `ul` / `ol` / `li` / `table` / `thead` / `tbody` / `tr` / `th` / `td` / `a` / `img` / `code` / `pre` / `blockquote` / `hr` / `strong` / `em` / `span` / `div` / `button` / `article` 等常见结构  
- 手递属性：`data-adam-action` / `data-adam-skill-id` / `data-adam-prompt`  
- `a[href]` / `img[src]`：仅安全 `http(s)`（及既有产品允许的媒体 URL 策略）  
- **禁止** `script`、内联事件、`javascript:` URL  

### Remove / shrink

- `ComputerRenderer` 对 `list` / `note` / `section` / storyboard 的主渲染路径  
- GitView 内 listing storyboard 特例  
- Spec `itemHandoffs` / `toolbarHandoffs` 及 Workspace 接线  
- 依赖「前端按 item 结构画按钮」的测例  

---

## §3 Skill migration & acceptance

### Skill（`lippi-ai-ebus-pi-extension`）

- 各 skill `references/output.md`：`view.json` 改为 v2  
- 清单类（选题 / 选品 / 拆解等）：在 HTML（或 md 内嵌按钮）写出完整 `data-adam-*`  
- Listing 策划 / 执行：分镜与文案也改为 skill 输出的文档 HTML，不再依赖前端 storyboard 组件  
- `artifact` 契约尽量不动  

### FE migration order

1. v2 解析 + `DocPreview`（GitHub 皮 + 消毒 + 手递）  
2. Workspace 切换预览；删 Spec handoffs  
3. 改写全部相关 skill 的 view 模板（电商 + 小红书，含 listing）  
4. 删除旧 blocks 渲染与相关测例  

### Acceptance

- 选题 / 选品 / 笔记 / 拆解 / 上架：右侧为 README 风文档  
- 点文档内手递按钮可开跑，请求 `skillId` + `text` 来自按钮属性  
- 恶意 `script` / `onclick` 不执行  
- `format: markdown` 与 `html` 各至少一条可用路径  

### Explicitly out

- v1 blocks 兼容层  
- iframe 沙箱  
- 双轨结构化 view + html  

---

## Relation to unified workspace

统一 Workspace 壳保留。本设计替换其 **Computer 预览协议与手递来源**：展示与动作下沉到 `view.content`，Spec 不再描述行内 handoff。

---

## Approval

- 方案 1、手递 B→按钮作者 A→prompt A、format C、全量 B：会话确认  
- §1 / §2 / §3：会话确认  
- 修订：v1 不用管（写入 Non-goals）  
