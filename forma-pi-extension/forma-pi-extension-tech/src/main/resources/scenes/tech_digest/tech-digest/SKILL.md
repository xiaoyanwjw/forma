---
name: tech-digest
description: >-
  用 fetch_web_page 或用户粘贴正文速读一篇科技产品页、AI 文章或技术文档，产出一页摘要（JSON：view + artifact）。
  在用户提到速读链接、解析文章、技术文档摘要时使用。
allowed-tools: read_skill fetch_web_page excerpt_chunks write_file read_file render_view
metadata:
  billing: true
  persistAs: tech_digest
  requiresView: true
  output: view.json
---

# 科技速读

帮用户**速读一篇**科技产品页、AI 文章或技术文档：拉取或粘贴原文 → 代码切块摘句 → 只根据摘录写一页摘要。没有可用正文就不要写盘、不要编造。

## When to use

- **用：** 速读链接、解析文章、技术文档摘要、产品页一页看懂、用户给了 http(s) 链接或粘贴正文
- **不用：** 选品清单 / Listing → 电商场景
- **不用：** 小红书拆解 / 写笔记 → 小红书场景
- **不用：** 订票、发帖、投顾医嘱 → 短聊拉回本 Skill 范围

## Workflow

1. **判断原文从哪来。**
   - **已有粘贴正文**（用户消息里已有足够长的可见正文）：`artifact.source` = **`paste`**。正文取自用户粘贴。若同时给了链接，可把链接写入 `sourceUrl`，仍以粘贴为准。**不要**再调 `fetch_web_page`（**0 次** fetch）。
   - **只有 http(s) 链接、没有可用粘贴正文：** 调用 `fetch_web_page` **至多 1 次**。成功则 `source` = **`fetch`**，`sourceUrl` 优先用工具返回的 `finalUrl`；正文已写入 run 工作区（工具返回的 `sourcePath`，默认 `source.md`）。失败或正文太短 → 进入 Failures，**禁止**编造原文后继续。
   - **既无链接也无粘贴正文：** Fail。不要空写摘要。

2. **禁止二次拉页。** 不并行、不换 URL 连拉。同一轮 **至多 1 次** `fetch_web_page`；粘贴路径 **0 次**。

3. **切块摘句（恰好 1 次）。** 对 `source=fetch` 用工具返回的 `sourcePath`；对 `source=paste` 先把用户粘贴正文 `write_file` 到工作区（如 `source.md`）再调 `excerpt_chunks`。本轮 **`excerpt_chunks` 恰好 1 次**。主模型写 `title` / `oneLiner` / `points` / `forWhom` / `uncertainties` 时**只使用**工具返回的 `excerpts`（及用户可选 `concern`），**禁止**把 `source.md` 全文读进总结提示，**禁止**把原文全文块塞进 artifact 字段。

4. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**（`excerpts` 须来自 `excerpt_chunks`；每句 `quotes` 必须是原文子串），再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact 对象）。可用 `read_file` 自检 artifact，**不要**为总结而 `read_file` 整页 `source.md` 贴进提示或 artifact。

5. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`，模板 `template/view.mustache`）。勿手写 HTML `view.content`。近端**无**文末手递按钮。

6. **交付路径与成功标准。** 本轮交付路径以 user 消息开头的 `<reminder>` 为准（禁止改名、禁止复用上一轮路径）。**成功** = 盘上已有 reminder 中的 **output**（通常 `view.json`）。不要在对话里输出 `{"output":...}` 或粘贴整包 JSON。

7. **过 Verification。** 全部勾上再结束本轮；任一不满足 → Fail 或改盘后重跑 `render_view`。

## Tool: fetch_web_page

按 URL 拉取**一页**可见正文（Apify WCC）；正文写入工作区，工具返回里**不带**全文。

| 参数 | 说明 |
|------|------|
| `url` | http(s) 页面链接；已有粘贴正文则可不调 |

成功：用 `sourcePath`（默认 `source.md`）作为 `excerpt_chunks` 输入；`finalUrl` / 标题等按工具返回写入 artifact。  
失败或正文太短：**禁止**编造；走 Failures。

## Tool: excerpt_chunks

对 `sourcePath` 指向的工作区 Markdown/正文做代码切块 + 旁路摘句，返回 `excerpts`（`heading` + `quotes[]`）。

| 参数 | 说明 |
|------|------|
| `sourcePath` | 工作区相对路径，默认 `source.md` |

本轮 **恰好调用 1 次**。总结阶段**只**消费返回的 `excerpts`，不整页读 source。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **可对照：** `points` 与 `excerpts.quotes` 能指回原文子串，禁万能「优点/缺点/总结」空壳
- **oneLiner：** 概括「原文在讲什么」，不是值不值买式评测
- **原文真实：** `source=fetch` 的正文与 quotes 来自工具链；`source=paste` 须能在用户消息里找到对应段落
- **concern：** 用户说「我关心…」时写入 `concern`，并体现在 `forWhom` / `points` 取舍上

## Output

成功终态：**盘上** reminder 指定的 **output**（通常 `view.json`）。完整字段与示例 → [output.md](references/output.md)。

速记：

- `source` = **`fetch`** 或 **`paste`**（不要用别的枚举）
- 无可用正文不写盘、不当作成功交付
- `view.version` = **`2`**；`view.format` = **`html`**；含「AI 摘要，请对照原文」
- 成功路径无跑题闲聊（失败路径只人话）

## Verification

输出前逐项自检（全部通过才允许结束本轮）：

- [ ] 有可用原文（来自粘贴或一次成功的 `fetch_web_page`），且 `excerpts` 非空
- [ ] 有链接且无粘贴时，本轮 `fetch_web_page` **至多 1 次**且成功；已有粘贴则 **0 次** fetch
- [ ] 本轮 `excerpt_chunks` **恰好 1 次**，且 artifact 的 `excerpts` 来自该次返回
- [ ] `source=fetch` 时 quotes 来自拉取正文；`source=paste` 时来自用户粘贴，未用假链冒充拉取成功
- [ ] 主模型总结**未**把 `source.md` 全文灌进提示或 artifact 全文字段
- [ ] 已写 `artifact.json`，且已成功调用 **`render_view`** 写出 **`view.json`**
- [ ] `<reminder>` 中的 output 已写盘；**未**输出 `{"output":...}`
- [ ] `title` / `oneLiner` / `points` / `forWhom` 非空且可对照 `excerpts`
- [ ] `view.version` = **`2`**；`view.format` = **`html`**；view 含免责声明，**无**手递按钮
- [ ] `view.title` / `artifact.title` 为同一中文标题
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] 成功路径无跑题闲聊

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 用户没给链接也没粘贴正文
- 给了链接但 `fetch_web_page` 失败 / 正文太短，且用户没有粘贴正文 → 请换公开链接或直接粘贴正文后再速读
- 完全离题（订票、发帖、投顾医嘱等）→ 简短说明本 Skill 只做科技文档速读
- 完全离题闲聊

## Boundaries

- 禁止无原文空写、禁止编造 quotes 或假拉取成功
- 不二次 / 并行调用 `fetch_web_page`；不自动换 Actor 重试
- 不代登、不收 Cookie、不解析 PDF
- 不宣称官方热榜；不自动发帖或下单
