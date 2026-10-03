# Skill View Mustache Render Design

**Date:** 2026-10-03  
**Status:** accepted  
**Decision:** 方案 1 — Agent 工具 `render_view`：skill 内 Mustache 模板 + `artifact.json` → 写出 v2 `view.json`；对话指针 `{"output":"view.json"}`；同目录读 `artifact.json`

**Related:**  
- [`2026-10-03-computer-doc-preview-design.md`](./2026-10-03-computer-doc-preview-design.md)（Computer 只渲染 v2 md|html；本设计解决 **谁生成 view.content**）  
- Skill 双轨 `artifact` / `view`；`GenerationOutputParser` / settle 读盘指针

---

## Goal

模型不再手写整段 HTML/Markdown `view.content`。改为：

1. 模型只写业务 **`artifact.json`**
2. 调工具用 **skill 模板（Mustache）** 填充 → **`view.json`**
3. 对话终稿只报 **`{"output":"view.json"}`**

预览仍走现有 Computer DocPreview；模板保证手递按钮与结构稳定。

## Non-goals

- 结算时才第一次渲染（盘上必须已有 `view.json`，Agent 可自检）  
- 另造 `view-data.json` 第二份展示 JSON  
- 模型继续手写 `content` / 拼 `final.json`（迁移完成后）  
- iframe / 换前端预览协议  

---

## Decisions locked

| 项 | 选择 |
|----|------|
| 谁渲染 | **Agent 工具** `render_view`（会话选项 B） |
| 数据源 | **`artifact.json`**（选项 A） |
| 工具产出 | **`view.json`**（完整 v2） |
| 对话指针 | **`{"output":"view.json"}`** |
| artifact 定位 | **与 view 同目录**的 `artifact.json`（指针不写第二路径） |
| 模板引擎 | **Mustache** |
| 形态 | 方案 1（专用工具，非 settle 时渲染、非 bash 脚本） |

---

## §1 Tool + template + settle

### Agent flow（每 skill）

1. `write_file` → `artifact.json`（仅业务）  
2. 调用 `render_view`  
3. 对话**仅**输出：`{"output":"view.json"}`（无围栏、无整包 JSON）

Listing 策划/执行：路径可为 `plan/artifact.json` → `plan/view.json`，指针 `{"output":"plan/view.json"}`（同目录 `plan/artifact.json`）。

### Template location

Canonical under skill resources:

```text
lippi-ai-ebus-pi-extension/src/main/resources/scenes/<scene>/<skill>/references/view.mustache
```

Variants when needed:

- `references/plan/view.mustache`
- `references/exec/view.mustache`

### Tool `render_view`

| 入参 | 默认 | 含义 |
|------|------|------|
| `artifact` | `artifact.json` | 相对 run 根的 artifact 路径 |
| `out` | `view.json` | 写出的 view 路径 |
| `template` | skill 默认 `references/view.mustache` | 相对**当前 skill 资源**的 mustache 路径 |

行为：

1. 读 artifact JSON 为对象  
2. （可选）注入展示辅助字段（见 §2）  
3. Mustache 渲染 → HTML 或 Markdown 字符串  
4. 写出 view v2：

```json
{
  "version": 2,
  "title": "<from artifact.title>",
  "format": "html",
  "content": "<rendered>"
}
```

`format`：默认 `html`；可由 skill 旁元数据或模板约定声明为 `markdown`（如笔记）。

### Settle / output parse

- 指针：`{"output":"view.json"}`（或 `plan/view.json` 等）  
- 加载该 path 为 **view**  
- 同目录加载 **`artifact.json`**（若 `output` 为 `plan/view.json`，则读 `plan/artifact.json`）  
- **短期兼容**旧指针 `{"output":"final.json"}`（信封 `{view,artifact}`）；新 skill 文档不再要求 `final.json`  
- 兼容期结束后删除 final 强制路径  

---

## §2 Template contract & migration

### Mustache root

数据根 = **整棵 artifact 对象**（`{{title}}`、`{{#items}}…{{/items}}` 等）。

### Optional tool-injected helpers（不写回 artifact 文件）

渲染前在内存中增强，例如：

- `items[].displayTitle` — 去掉 `【优先发】` / `【优先试】`  
- `items[].handoffPrompt` — 按既有手递合同拼好的完整 prompt（供 `data-adam-prompt`）  

具体 helper 按 skill 注册（选题 / 选品 / 拆解各一份），禁止把展示专用字段强迫进落库 artifact。

### HTML / handoff（与 Computer v2 一致）

- 根节点建议：`<article class="markdown-body">…</article>`  
- 手递：

```html
<button
  type="button"
  data-adam-action="handoff"
  data-adam-skill-id="xhs-note"
  data-adam-prompt="{{handoffPrompt}}"
>写成笔记</button>
```

- 依赖 Mustache **默认 HTML escape**；默认不用三重花括号（`{{{…}}}`）写属性  

### Markdown skills（如 xhs-note）

同一工具；模板产出 Markdown 文本；`view.format = "markdown"`。

### Migration order

1. 实现 `render_view` + 结算认 `output: view.json` + 同目录 artifact  
2. 选题 / 选品 mustache + 改 SKILL/output 文档  
3. 拆解 / listing（plan+exec）/ 笔记  
4. 去掉「手写 view.content / 拼 final.json」步骤  
5. 收掉 final.json 兼容（单独小步）  

### Explicitly out

- 模型手写整段 `content`  
- `view-data.json` 双轨  
- settle 时才渲染（无盘上 view）  

---

## Relation to Computer doc preview

Computer 侧只消费 v2 `format`+`content`。本设计保证 **content 由模板确定性生成**，手递属性与结构不再依赖模型拼字符串。

---

## Approval

- 工具渲染 B、artifact 数据源 A、产出 view.json、指针 `{"output":"view.json"}`、同目录 artifact、Mustache、方案 1：会话确认  
- §1 / §2：会话确认  
