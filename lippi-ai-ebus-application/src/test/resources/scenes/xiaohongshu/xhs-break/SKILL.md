---
name: xhs-break
description: >-
  用 fetch_xhs_note 或用户粘贴正文拆解一篇小红书笔记，产出结构、骨架与改写稿（JSON：view + artifact）。
  在用户提到拆解爆文、仿写结构、按爆文改写成自己的商品时使用。
allowed-tools: read_skill fetch_xhs_note write_file read_file render_view
metadata:
  output:
    billing: true
    persistAs: xhs_break
    requiresView: true
---

# 爆文拆解

帮卖家把**一篇已有笔记**拆成可对照的结构 + 骨架，并改写成自己的商品口吻。没有原文就不要拆。

## When to use

- **用：** 拆解爆文、分析结构、按某篇改写成我的商品、给链接或粘贴正文
- **不用：** 只要选题清单 → `xhs-topiclist`
- **不用：** 已经有骨架、只要写成完整笔记 → `xhs-note`

## Workflow

1. **判断原文从哪来。**
   - **已有粘贴正文**（用户消息里已有完整笔记本文）：`artifact.source` = `paste`。`sourceBody` / `sourceTitle` 取自粘贴。**不要**再调 `fetch_xhs_note`。若同时给了链接，可把链接写入 `sourceUrl`，仍以粘贴正文为准。
   - **只有链接 / 短链 / noteId、没有可用正文：** 调用 `fetch_xhs_note` **至多 1 次**。成功则 `source` = `apify`，`sourceTitle` / `sourceBody` / `sourceUrl` 必须来自工具返回（`title` / `body` / `noteUrl`）。失败 → 进入 Failures，**禁止**编造原文后继续拆。
   - **既无链接也无粘贴正文：** Fail。不要空拆。

2. **禁止二次拉取。** 不并行、不换链连拉。不要调用 `search_xhs_note`。

3. **对照原文拆解。** 写出 `structure`（结构要点，须能对照原文段落/钩子）、`skeleton`（可复用骨架）、`rewrite`（改写成用户商品；未指定商品时按原文品类做示范改写，并写入 `assumptions`）。`targetProduct` 用户点名时必填。

4. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**，再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact 对象）。可用 `read_file` 自检。

5. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`，模板 `references/view.mustache`）。模板文末含「按骨架写笔记」手递按钮；`handoffPrompt` 由工具注入，合同见 [output.md §手递](references/output.md#手递按钮与-prompt-合同)。勿手写 HTML `view.content`。

6. **终稿只输出指针。** 对话里**仅**一个 JSON 对象（无围栏、无其它文字）：`{"output":"view.json"}`。

7. **过 Verification。** 全部勾上再发指针；任一不满足 → Fail 或改盘后重跑 `render_view`。

## Tool: fetch_xhs_note

按 URL 取**一篇**正文，不是召回管线。优先完整分享链（常含 `xsec_token`）。

| 参数 | 说明 |
|------|------|
| `url` 或 `noteUrl` | 笔记 URL / 分享短链；已有粘贴正文则可不调 |

成功：用工具的 `title` / `body` / `noteUrl` 填 `sourceTitle` / `sourceBody` / `sourceUrl`。  
失败或空正文：**禁止**编造；走 Failures。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **可对照：** `structure` 能指回原文钩子/段落，禁「开头吸引人、中间干货、结尾转化」万能空壳
- **骨架可复用：** `skeleton` 抽的是结构槽位，不是原文逐句抄袭
- **改写换品不换结构：** `rewrite` 落到用户商品；不编造未提供功效
- **原文真实：** `source=apify` 的正文必须来自 `fetch_xhs_note`；`source=paste` 必须能在用户消息里找到对应段落

## Output

成功终态：**盘上** `artifact.json` + **`view.json`**（`render_view` 产出）+ **对话**指针 `{"output":"view.json"}`。完整字段与示例 → [output.md](references/output.md)。

速记：

- `source` = `apify` 或 `paste`（不要用别的枚举）
- 无原文不发指针
- `view.version` = **`2`**；`view.format` = **`html`**；上拆解、下骨架+改写 + 手递按钮
- 成功路径除指针外无闲聊（失败路径只人话）

## Verification

输出前逐项自检（全部通过才允许发指针）：

- [ ] 有可用 `sourceBody`（来自粘贴或一次成功的 `fetch_xhs_note`）
- [ ] 有链接且无粘贴时，本轮 `fetch_xhs_note` **至多 1 次**且成功；已有粘贴则 **0** 次 fetch
- [ ] `source=apify` 时 `sourceTitle` / `sourceBody` 来自工具，未编造
- [ ] `source=paste` 时正文来自用户粘贴，未用假链冒充拉取成功
- [ ] 已写 `artifact.json`，且已成功调用 **`render_view`** 写出 **`view.json`**
- [ ] 终稿对话**仅** `{"output":"view.json"}`
- [ ] `structure` / `skeleton` / `rewrite` 均非空，且拆解可对照原文
- [ ] `view.version` = **`2`**；`view.format` = **`html`**；`view.content` 上为拆解要点、下为骨架+改写
- [ ] 文末含手递按钮：标签「按骨架写笔记」，`data-adam-skill-id="xhs-note"`，`data-adam-prompt` 符合 output 合同
- [ ] `view.title` / `artifact.title` 为同一中文标题
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] 成功路径除指针外无闲聊

## Failures

下列情况**只回一句人话原因**，不要输出 JSON 或指针：

- 用户没给链接也没粘贴正文
- 给了链接但 `fetch_xhs_note` 失败/空正文，且用户没有粘贴正文 → 请改贴完整分享链或直接粘贴笔记正文后再拆
- 完全离题闲聊

## Boundaries

- 禁止无原文空拆、禁止编造爆文正文
- 不二次 / 并行调用 `fetch_xhs_note`；不调用 `search_xhs_note`
- 不宣称官方热榜；不自动发帖
