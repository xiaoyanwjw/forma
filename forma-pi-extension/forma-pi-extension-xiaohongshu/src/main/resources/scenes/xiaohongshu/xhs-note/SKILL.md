---
name: xhs-note
description: >-
  一次成稿产出小红书种草笔记：标题备选、正文、标签与配图提示（JSON：view + artifact）。
  在用户提到写笔记、种草文案、标题 tags，或从选题/爆文点「写成笔记」时使用。
allowed-tools: read_skill write_file read_file render_view
metadata:
  billing: true
  persistAs: xhs_note
  requiresView: true
  output: view.json
---

# 笔记种草稿

帮卖家写出**可直接发的种草笔记**：标题备选 + 正文 + 标签 + 配图提示。一次成稿，无确认轮、无二次扣费。配图分镜内嵌为本技能的 `imageHints`，不另开技能。

## When to use

- **用：** 写笔记、种草正文、标题备选、标签、配图怎么拍；或用户消息含「写成笔记」「按骨架写笔记」的交接提示
- **不用：** 只要选题清单、发什么角度 → `xhs-topiclist`
- **不用：** 拆解别人爆文结构、先出骨架再改写 → `xhs-break`

## Workflow

1. **解析交接（若有）。** 用户消息常来自选题「写成笔记」或爆文「按骨架写笔记」：

```text
请把选题「{title}」写成一篇小红书笔记。
来源选题条目：{id}
钩子：...
角度：...
可选笔记原链：{url}
```

```text
请按下面骨架为我的商品「{product}」写一篇小红书笔记。
骨架：...
改写要点：...
```

抽出品名、`topicItemId`（如 `tp-2`）、钩子/角度、可选 `sourceNoteUrl`、骨架摘要。有选题交接时**禁止换题**。交接字段写入 `assumptions`；`artifact.topicItemId` = 交接 id。输入含「来源选题条目」或明确 `tp-n` 时，`topicItemId` **必填**且与输入一致。口述笔记（无选题交接）可省略该字段。爆文交接近端不强制 DB 关联 id，把骨架摘要与目标商品写入 `assumptions`。

2. **读懂商品与语气。** 优先用交接；不够则按国内数码配件/桌面默认假设写入 `assumptions`。语气像真人分享。信息不够时写入假设，仍一次成稿，**勿先追问、勿 `ask_human`**。

3. **禁止检索/拉详情。** 本技能 **没有** `search_xhs_note` / `fetch_xhs_note`。不要假装搜过笔记或打开过链接正文。可选 `sourceNoteUrl` 只作为假设备注，不据此编造未提供的功效/数据。

4. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**（`titleOptions` 3–5、`body`、`tags` 5–10、`imageHints` 3–5、交接字段等），再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact 对象）。可用 `read_file` 自检。

5. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`，模板 `template/view.mustache`；`format` 默认为 **html**）。勿手写 `view.content`。

6. **交付路径与成功标准。** 本轮交付路径以 user 消息开头的 `<reminder>` 为准（禁止改名、禁止复用上一轮路径）。**成功** = 盘上已有 reminder 中的 **output**（通常 `view.json`）。不要在对话里输出 `{"output":...}` 或粘贴整包 JSON。

7. **过 Verification。** 全部勾上再结束本轮；任一不满足 → Fail 或改盘后重跑 `render_view`。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **真人分享感：** 像朋友转述使用场景；禁电商详情腔、禁「亲们冲」空洞口号连发
- **不编造：** 未提供的功效、认证、实验室数据、销量、排名一律不写
- **一次成稿：** 不要策划/确认两段；不要让用户先选标题再写正文
- **配图提示：** `imageHints` 是可执行拍摄提示（机位/主体/字幕），不是「拍几张好看的」
- **标签：** 5–10 个，含品类词 + 场景词；勿堆无关热词

## Output

成功终态：**盘上** reminder 指定的 **output**（通常 `view.json`）。完整字段与示例 → [output.md](references/output.md)。

速记：

- 有选题交接时 `artifact.topicItemId` 必填且与输入 `tp-n` 一致
- `titleOptions` **3–5**；`tags` **5–10**；`imageHints` **3–5**
- `view.version` = **`2`**；`view.format` = **`html`**；`view.title` 与 `artifact.title` 同一中文标题
- 成功路径无跑题闲聊

## Verification

输出前逐项自检（全部通过才允许结束本轮）：

- [ ] 本轮**未**调用 `search_xhs_note` / `fetch_xhs_note` / `ask_human`
- [ ] 已写 `artifact.json`，且已成功调用 **`render_view`** 写出 **`view.json`**
- [ ] `<reminder>` 中的 output 已写盘；**未**在对话里贴整包大 JSON 或 `{"output":...}`
- [ ] 输入含选题条目 id 时 `topicItemId` 必填且一致；口述笔记可省略
- [ ] `titleOptions` 为 3–5 条互不重复的可发标题
- [ ] `body` 非空，真人分享感，未编造未提供功效/数据
- [ ] `tags` 5–10；`imageHints` 3–5 且可执行
- [ ] `view.version` = **`2`**；`view.format` = **`html`**；`view.content` 可见标题备选 + 正文 + 标签 + 配图提示
- [ ] `view.title` / `artifact.title` 为同一中文标题
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] 成功路径无跑题闲聊；失败路径无人话以外的假 JSON

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 完全离题闲聊，看不出要写哪篇笔记
- 用户只要选题清单或只要爆文拆解（引导去对应胶囊，不要硬写笔记）

## Boundaries

- 不二次 HITL、不拆策划/执行两段扣费
- 不调用检索或详情工具；不编造未提供的功效、认证、销量、热榜
- 不自动发帖、不写私信/投流 API
