---
name: tech-briefing
description: >-
  从 Product Hunt 今日列表产出 3～7 条带回出处的产品向早报（JSON：view + artifact）。
  在用户提到产品早报、今日值得跟、Product Hunt 扫描时使用。
allowed-tools: read_skill recall_products research_products write_file read_file render_view
metadata:
  billing: true
  persistAs: tech_briefing
  requiresView: true
  output: view.json
---

# 产品早报

帮用户拿到今天（或近 N 天）**Product Hunt 列表**里值得跟的 **3～7 条**产品动态：召回候选（去重截断）→ 默认深挖 1 条 → 短条目 + 出处。近端**不要求关注域**；有粘贴列表时走 paste，否则直接拉 PH。

## When to use

- **用：** 产品早报、今日值得跟、Product Hunt 列表、粘贴 Newsletter/列表正文要整理成早报
- **不用：** 单品官网分层拆解 → 竞品分析（`tech-competitor`）
- **不用：** 科技文章/文档一页摘要 → 科技前沿「链接速读」（`tech-digest`）
- **不用：** 选品 / Listing / 小红书 → 对应场景

## Workflow

1. **确认输入。**
   - **默认无需 topic。** 用户只说「出早报 / 今天 PH」即可。
   - 可选粘贴列表（`paste`）：有则跳过 PH。
   - 可选时间窗（`window`）：默认今天 / 近 1 天；写入 `artifact.windowLabel`。
   - 可选 `deepFetch`：整数，**默认 1**，近端上限 **1**；`0` = 关深挖。

2. **取候选。** 调用 **`recall_products`**（可不传 `topic`；有 `paste` 则传入）。
   - paste 路径：`artifact.source` = **`paste`**。
   - PH 路径：`artifact.source` = **`ph`**（当天热门列表）。
   - **0 候选 → Fail。** 禁止编造条目。

3. **深挖（默认 1）。** 调用 **`research_products`**（传入 `candidates` 与 `deepFetch`，默认 1）。
   - `deepFetch` **默认 1**：工具对前 `deepFetch` 条有 URL 的候选各抓取至多一次，写入该条 `evidence`。
   - `deepFetch=0`：跳过抓取，纯列表写条目。
   - 单条抓取失败：该条可无 evidence，勿编造；可记入 `uncertainties`。禁止整轮因单条失败而 Fail。

4. **写条目。** 按 [output.md](references/output.md) 拼完整 **artifact**：`title`（中文，与后续 `view.title` 相同，如「今天产品早报」）、可选 `topic`（用户若主动提了才写）、`windowLabel`、`source`、`deepFetch`（本轮实际深挖条数）、`items`（**3～7**，有几条依据出几条）、`uncertainties`。每条含 `title` / `oneLiner` / `whyNow` / 尽量有 `sourceUrl`；`status` 以 **`found`** 为主。**禁止无候选编造。** `write_file` → `artifact.json`。

5. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`）。勿手写 HTML。近端**无**手递按钮。

6. **成功标准。** 盘上已有 reminder 中的 **output**（通常 `view.json`）。不要在对话里输出 `{"output":...}` 或整包 JSON。过 Verification 再结束。

## Tool: recall_products

| 参数 | 说明 |
|------|------|
| `topic` | **可选**；近端早报默认不传，直接拉 PH 列表 |
| `window` | 可选时间窗 |
| `paste` | 粘贴列表正文；有则工具内解析为 candidates，跳过 PH |

失败或 0 候选：禁止编造；走 Failures。

## Tool: research_products

| 参数 | 说明 |
|------|------|
| `candidates` | `recall_products` 返回的候选列表 |
| `candidatesPath` | 可选；工作区 JSON 路径（含 candidates） |
| `deepFetch` | 默认 1，近端上限 1；`0`=跳过抓取 |

本轮至多按 `deepFetch` 次单页抓取；不并行、不二次拉页。

## Tool: render_view

默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`。

## Quality

- **有出处：** 每条尽量对应候选卡 URL / tagline；禁止空壳「今日必读」
- **深挖诚实：** `deepFetch` 仅为 0 或 1；勿假装全员详情
- **条数诚实：** 不足 3 标「候选偏少」；0 条 Fail

## Output

成功终态：盘上 reminder 指定的 **output**。字段与示例 → [output.md](references/output.md)。

## Verification

- [ ] 已调 `recall_products` 且候选非空（默认 PH，或 paste）
- [ ] 已调 `research_products`（`deepFetch=0` 时仍可调但应跳过抓取）
- [ ] `artifact.title` 非空，与 `view.title` 一致（如「今天产品早报」）
- [ ] `items` 非空（目标 3～7）；每条尽量有 `sourceUrl`；无候选编造
- [ ] `source` 为 `ph` 或 `paste`；`metadata.persistAs` = `tech_briefing`
- [ ] 已写 `artifact.json` 并成功 `render_view` → `view.json`
- [ ] 未输出 `{"output":...}`；view 无手递按钮
- [ ] 成功路径无跑题闲聊

## Failures

只回一句人话：

- 列表空 / 全部取数失败且无粘贴
- 完全离题 → 拉回本 Skill

## Boundaries

- 禁止无候选编造条目；禁止无 `items` 仍 settle
- 近端主源仅 Product Hunt；不做多源合订、定时推送；**不强制关注域**
- 不二次 / 并行抓取；不代登、不收 Cookie、不解析 PDF
