---
name: tech-briefing
description: >-
  按关注域产出 3～7 条带回出处的产品向早报（JSON：view + artifact）。
  在用户提到产品早报、今日值得跟、Product Hunt 扫描时使用。
allowed-tools: read_skill search_product_launches fetch_web_page write_file read_file render_view
metadata:
  billing: true
  persistAs: tech_briefing
  requiresView: true
  output: view.json
---

# 产品早报

帮用户按**关注域**拿到今天（或近 N 天）值得跟的 **3～7 条**产品动态：列表候选 → 过滤去重 → 默认深挖 1 条 → 短条目 + 出处。没有关注域且无粘贴列表就不要写盘、不要编造。

## When to use

- **用：** 产品早报、今日值得跟、关注域扫描、Product Hunt 列表、粘贴 Newsletter/列表正文要整理成早报
- **不用：** 单品官网分层拆解 → 竞品分析（`tech-competitor`）
- **不用：** 科技文章/文档一页摘要 → 科技前沿「链接速读」（`tech-digest`）
- **不用：** 选品 / Listing / 小红书 → 对应场景

## Workflow

1. **确认输入。**
   - **无关注域（topic）且无粘贴列表（paste）→ Fail。** 只回一句人话，不写盘、不 settle。
   - 可选时间窗（`window`）：默认今天 / 近 1 天；写入 `artifact.windowLabel`。
   - 可选 `deepFetch`：整数，**默认 1**，近端上限 **1**；`0` = 关深挖。

2. **取候选（二选一，互斥）。**
   - **有 paste：** 从粘贴正文解析为 `candidates[]`（title / tagline / url 等）。**跳过** `search_product_launches`。`artifact.source` = **`paste`**。
   - **否则：** 调用 **`search_product_launches`**（关注域 + 时间窗）。`artifact.source` = **`ph`**。
   - **0 候选 → Fail。** 禁止编造条目。

3. **过滤 / 去重 / 截断。** 关键词粗过滤 + URL/标题去重；目标入选 **3～7**（工具侧可保留前 12 供再筛）。不足 3 且有候选 → 有几条出几条，并写入 `uncertainties`（如「候选偏少」）。

4. **深挖（默认 1）。**
   - `deepFetch` **默认 1**：对拟入选第 **1** 条候选调用 **`fetch_web_page` ×1**，取证据句写入该条 `evidence`（或摘录要点）。
   - `deepFetch=0`：跳过 fetch，纯列表写条目。
   - 本轮 **至多 1 次** `fetch_web_page`；不并行、不二次拉页。失败则该条可无 evidence，勿编造；可记入 `uncertainties`。

5. **写条目。** 按 [output.md](references/output.md) 拼完整 **artifact**：`title`（中文，与后续 `view.title` 相同：域 + 时间窗）、`topic`、`windowLabel`、`source`、`deepFetch`（本轮实际深挖条数）、`items`（**3～7**，有几条依据出几条）、`uncertainties`。每条含 `title` / `oneLiner` / `whyNow` / 尽量有 `sourceUrl`；`status` 以 **`found`** 为主。**禁止无候选编造。** `write_file` → `artifact.json`。

6. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`）。勿手写 HTML。近端**无**手递按钮。

7. **成功标准。** 盘上已有 reminder 中的 **output**（通常 `view.json`）。不要在对话里输出 `{"output":...}` 或整包 JSON。过 Verification 再结束。

## Tool: search_product_launches

| 参数 | 说明 |
|------|------|
| `topic` | 关注域/主题；paste 路径不调本工具 |
| `window` | 可选时间窗 |

失败或 0 候选：禁止编造；走 Failures。

## Tool: fetch_web_page

| 参数 | 说明 |
|------|------|
| `url` | 拟入选第 1 条候选的公开 URL；`deepFetch=0` 时不调 |

本轮至多 1 次。

## Tool: render_view

默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`。

## Quality

- **有出处：** 每条尽量对应候选卡 URL / tagline；禁止空壳「今日必读」
- **深挖诚实：** `deepFetch` 仅为 0 或 1；勿假装全员详情
- **条数诚实：** 不足 3 标「候选偏少」；0 条 Fail

## Output

成功终态：盘上 reminder 指定的 **output**。字段与示例 → [output.md](references/output.md)。

## Verification

- [ ] 有关注域或可用粘贴列表
- [ ] paste 路径未调 `search_product_launches`；PH 路径调了且候选非空
- [ ] `fetch_web_page` ≤1（`deepFetch=0` 时为 0）
- [ ] `artifact.title` 非空，与 `view.title` 一致（域 + 时间窗）
- [ ] `items` 非空（目标 3～7）；每条尽量有 `sourceUrl`；无候选编造
- [ ] `source` 为 `ph` 或 `paste`；`metadata.persistAs` = `tech_briefing`
- [ ] 已写 `artifact.json` 并成功 `render_view` → `view.json`
- [ ] 未输出 `{"output":...}`；view 无手递按钮
- [ ] 成功路径无跑题闲聊

## Failures

只回一句人话：

- 没给关注域也没粘贴列表
- 列表空 / 全部取数失败且无粘贴
- 完全离题 → 拉回本 Skill

## Boundaries

- 禁止无候选编造条目；禁止无 `items` 仍 settle
- 近端主源仅 Product Hunt；不做多源合订、定时推送
- 不二次 / 并行 fetch；不代登、不收 Cookie、不解析 PDF
