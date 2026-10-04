# Output schema

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"view.json"}
```

不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 **`view.json`**，并与同目录 **`artifact.json`** 对齐落库。

无可用正文（链接失败且无粘贴）时**不要**写这些文件，只回人话。

## Contents

1. [对齐规则](#对齐规则)
2. [artifact（领域实体）](#artifact领域实体)
3. [view（视图实体）](#view视图实体)
4. [示例](#示例)
5. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 原文 | 正文必须真实存在：`fetch` ← `fetch_web_page` / WCC 工具返回；`paste` ← 用户粘贴 |
| `source` | 仅 **`fetch`** 或 **`paste`**（落库枚举；SKILL 与 artifact 均不写 `apify`） |
| `sourceUrl` | 可选；`fetch` 成功时有链接则写入，优先用工具 `finalUrl` |
| `excerpts` | 由 `excerpt_chunks` 产出；`quotes[]` 必须是原文子串；主模型总结**只**看 `title` + `excerpts` + 可选 `concern` |
| 视图 | 标题、一句话、要点、原文摘录、适合谁、需核实、原文链接；含「AI 摘要，请对照原文」 |
| 禁止 | 空摘录仍写要点、把块全文塞进 artifact、编造原文、二次拉页 |

**近端不提供**文末手递选型说明书按钮（与 xhs-break 不同）。

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同；页标题或自拟短标题 |
| `oneLiner` | 一句话：这篇原文在讲什么（不是「值不值买」评测） |
| `points` | 字符串数组；条数不限；每条应能对照某一 `quotes` 条目 |
| `excerpts` | 对象数组；每项含 `heading`（小节标题）与 `quotes`（原文子串数组） |
| `forWhom` | 适合谁看 / 不适合谁；摘录未说明受众时写「原文未说明」 |
| `uncertainties` | 字符串数组；原文没说清、需人工核实；过长被抽块也写这里 |
| `sourceUrl` | 有链接时填写 |
| `source` | **`fetch`** 或 **`paste`** |
| `concern` | 用户说的「我关心…」；没有则空字符串或省略 |

不要把 `blocks` / `format` / `content` / 原文全文块写进 `artifact`。原文工作区文件（如 `source.md`）仅供工具链读取，不整页复制进 artifact。

## view（视图实体）

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 给人看的中文标题 |
| `format` | **`html`** |
| `content` | 由 [view.mustache](../template/view.mustache) 渲染的 HTML；根节点 `<article class="markdown-body">` |

正文结构（模板固定）：

1. `<h1>` = `title`
2. `<p class="forma-lede">` = `oneLiner`
3. 免责声明：**AI 摘要，请对照原文。**
4. `<h2>要点</h2>` = `points` 列表
5. `<h2>原文摘录</h2>` = `excerpts`（`heading` + `blockquote` quotes）
6. `<h2>适合谁</h2>` = `forWhom`
7. 有 `uncertainties` 时 `<h2>需核实</h2>` 列表
8. 有 `sourceUrl` 时原文链接

不要把各切块全文再贴一遍到 view。摘录与要点条数不卡死上限（前端可折叠）。

**禁止** 再输出 v1 `blocks` JSON 视图。**禁止** 在 view 末尾添加 `data-forma-action="handoff"` 按钮。

### HTML 与转义

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内的 ASCII 双引号用 HTML 实体（如 `&quot;`）转义。

## 示例

链接拉取成功路径。先写领域实体，再 `render_view` 写视图，再输出指针。

### `artifact.json`（领域实体）

```json
{
  "title": "Forma Edge 推理节点 · 速读",
  "oneLiner": "Forma 发布可在局域网部署的轻量推理节点，面向小团队离线试模型。",
  "points": [
    "单节点默认占用 8GB 内存，文档写明需 AVX2 CPU。",
    "支持从控制台导入 GGUF 权重，不强制云端账号。",
    "官方示例延迟在 100 token/s 量级，未承诺具体硬件下的 SLA。"
  ],
  "excerpts": [
    {
      "heading": "部署与硬件",
      "quotes": [
        "Edge 节点可在单机 Docker 中启动，最低 8GB RAM。",
        "CPU 需支持 AVX2，否则安装脚本会拒绝继续。"
      ]
    },
    {
      "heading": "模型导入",
      "quotes": [
        "控制台提供 GGUF 导入向导，权重文件保存在本地卷。"
      ]
    }
  ],
  "forWhom": "适合要在内网试开源权重的小团队；不适合需要多区域自动扩缩的企业级训练集群。",
  "uncertainties": [
    "原文未说明 Windows 客户端是否支持同一安装包。"
  ],
  "sourceUrl": "https://example.com/forma-edge-release",
  "source": "fetch",
  "concern": "我关心能不能完全离线、不用注册云账号。"
}
```

### `view.json`（`render_view` 产出）

对上例 `artifact.json` 调用 `render_view` 后，`view.json` 含 v2 字段；`content` 由模板填充，且含「AI 摘要，请对照原文」；**不含**手递按钮。

**对话终稿指针：**

```json
{"output":"view.json"}
```

粘贴正文成功时，将 `"source": "paste"`，`sourceUrl` 可省略；`excerpts` 仍须来自 `excerpt_chunks`。

失败路径示例（仅人话，无 JSON）：

```text
没拉到这篇页面的可见正文。请换一条公开链接，或直接把正文粘贴过来再速读。
```

## 质量对照

### 好（可交付）

- **oneLiner：** 概括「原文在说什么」，能在 `excerpts` 的 quotes 中找到依据
- **source：** `fetch` 且正文与工具一致；或 `paste` 且能在用户消息中找到
- **excerpts：** 每句 quote 是原文子串；短文可以只有一块
- **points：** 条数灵活，但不应与摘录无关的泛化空话

### 坏（禁止）

- **空摘录：** `excerpts` 为空仍写 artifact / view
- **假源：** `source=fetch` 但 quotes 是模型编的、不在原文里
- **全文灌模型：** 把 `source.md` 整页贴进总结提示或 artifact
- **万能壳：** points 只有「优点/缺点/总结」且无法指回 quote
- **错场景：** 用户给美食探店文仍硬做科技速读（应友好拉回）
