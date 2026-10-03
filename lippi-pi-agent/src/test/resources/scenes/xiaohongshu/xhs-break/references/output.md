# Output schema

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `references/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"view.json"}
```

不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 **`view.json`**，并与同目录 **`artifact.json`** 对齐落库。

无原文（链接失败且无粘贴）时**不要**写这些文件，只回人话。

## Contents

1. [对齐规则](#对齐规则)
2. [artifact（领域实体）](#artifact领域实体)
3. [view（视图实体）](#view视图实体)
4. [手递按钮与 prompt 合同](#手递按钮与-prompt-合同)
5. [示例](#示例)
6. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 原文 | `sourceBody` 必须真实存在：`apify` ← `fetch_xhs_note`；`paste` ← 用户粘贴 |
| `source` | 仅 `apify` 或 `paste` |
| `sourceUrl` | 可选；有链接时写入；`apify` 时优先用工具 `noteUrl` |
| 视图 | 上拆解要点（`structure`），下骨架+改写（`skeleton` / `rewrite`） |
| 手递 | 文末 **一条**「按骨架写笔记」按钮，prompt 与 artifact 字段一致（见下节） |
| 禁止 | 空拆、假原文、把搜索 hits 冒充本篇正文 |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `source` | `apify` 或 `paste` |
| `sourceUrl` | 可选 |
| `sourceTitle` | 原文标题（工具或粘贴） |
| `sourceBody` | 原文正文（必须非空） |
| `structure` | 结构拆解（可对照原文） |
| `skeleton` | 可复用骨架 |
| `rewrite` | 改写稿 |
| `targetProduct` | 可选；用户点名商品时填写 |
| `assumptions` | 可选 |

不要把 `blocks` / `format` / `content` 写进 `artifact`。

## view（视图实体）

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 给人看的中文标题 |
| `format` | **`html`** |
| `content` | 由 [view.mustache](view.mustache) 渲染的 HTML；根节点 `<article class="markdown-body">` |

正文结构（模板固定）：

1. `<h1>` = 标题
2. `<h2>拆解要点</h2>` = `structure`
3. `<h2>骨架</h2>` = `skeleton`
4. `<h2>改写稿</h2>` = `rewrite`
5. 文末手递按钮；`data-adam-prompt` 由工具注入 **`handoffPrompt`**（见下一节）

不要把原文全文再贴一遍到 view（原文留在 artifact）。

**禁止** 再输出 v1 `blocks` JSON 视图。

### HTML 与转义

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内的 ASCII 双引号用 HTML 实体（如 `&quot;`）转义。
- `data-adam-prompt` 内多行 prompt 在 JSON 里用 `\n` 表示。

验收：点击由前端开跑 `xhs-note`，`text` 来自 `data-adam-prompt`。

## 手递按钮与 prompt 合同

拆解视图 **一条** 按钮：

```html
<button
  type="button"
  data-adam-action="handoff"
  data-adam-skill-id="xhs-note"
  data-adam-prompt="…"
>按骨架写笔记</button>
```

`handoffPrompt` / `data-adam-prompt` 正文须与下列逻辑一致（工具 `ViewRenderHelpers.buildXhsBreakNoteHandoffText`；长字段截断：`structure` ≤240 字、`skeleton` / `rewrite` 各 ≤400 字，超出加 `…`）：

- 有 `targetProduct` 时首行：  
  `请按这次爆文拆解的骨架，写一篇关于「{targetProduct}」的小红书种草笔记，语气像真人分享。`
- 无 `targetProduct` 时首行：  
  `请按这次爆文拆解的骨架写一篇小红书种草笔记，语气像真人分享。`
- 可选续行（有值才写）：  
  `结构要点：{structure}`  
  `骨架：{skeleton}`  
  `改写参考：{rewrite}`

缺 `skeleton` 且缺 `rewrite` 时仍应写按钮，但 prompt 至少含首行 + 已有的结构/骨架/改写行。

## 示例

链接拉取成功路径。先写领域实体，再写视图实体，再合并。

### `artifact.json`（领域实体）

```json
{
  "title": "Mac Mini 拓展坞 · 爆文拆解改写",
  "source": "apify",
  "sourceUrl": "https://www.xiaohongshu.com/explore/example-note-1",
  "sourceTitle": "Mini 背后那一团线我终于藏住了",
  "sourceBody": "Mini 接到显示器后背后永远一串转接头。换成一块和机身差不多宽的拓展坞，HDMI、U盘、网线从底座走。",
  "structure": "痛点开场（接口不够线乱）→ 方案（拓展坞）→ 使用动作（插口/走线）→ 桌面收束。未用官方认证压人。",
  "skeleton": "场景痛点一句 → 方案物件一句 → 2 个可拍使用动作 → 收纳/体积收束 → 不承诺未提供数据",
  "rewrite": "Mini 接到显示器后，机身底下永远拖着一串转接头。我没有换主机，只加了一块 Mac Mini 拓展坞：HDMI、U盘、网线从底座走，桌上只剩电源和一根视频线。官方认证我没看到说明书就不写，买之前对一下自己的口。",
  "targetProduct": "Mac Mini 拓展坞"
}
```

### `view.json`（`render_view` 产出）

对上例 `artifact.json` 调用 `render_view` 后，`view.json` 含 v2 字段；`content` 由 [view.mustache](view.mustache) 填充，且含 `data-adam-skill-id="xhs-note"` 手递按钮。

**对话终稿指针：**

```json
{"output":"view.json"}
```

失败路径示例（仅人话，无 JSON）：

```text
没拉到这篇笔记正文。请换一条完整分享链接，或直接把原文粘贴过来再拆。
```

## 质量对照

### 好（可交付）

- **structure：** 点出「接口不够开场 + 藏线收束」，能在 `sourceBody` 找到对应句
- **source：** `apify` 且 body 与工具一致；或 `paste` 且能在用户消息中找到
- **手递：** prompt 首行含目标商品（有 `targetProduct` 时）且结构与 artifact 一致

### 坏（禁止）

- **空拆：** 无 body 仍写「爆款结构：黄金三秒」
- **假源：** `source=apify` 但正文是模型编的
- **万能壳：** structure 只有「开头/中/结尾」三字，无法对照原文
