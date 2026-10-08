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

帮用户按**关注域**拿到今天（或近 N 天）值得跟的 **3～7 条**产品动态：列表候选 → 过滤去重 → 默认深挖 1 条 → 短条目 + 出处。没有关注域且无粘贴列表就不要写盘。

## When to use

- **用：** 产品早报、今日值得跟、关注域扫描、Product Hunt 列表、粘贴 Newsletter/列表正文要整理成早报
- **不用：** 单品官网分层拆解 → 竞品分析（`tech-competitor`）
- **不用：** 科技文章/文档一页摘要 → 科技前沿「链接速读」（`tech-digest`）

## Workflow

> 占位：完整取数与深挖步骤在后续 Task 落地；近端纪律见 [output.md](references/output.md)。

1. **确认输入。** 关注域/主题必填语义；可选时间窗（默认今天/近 1 天）。有粘贴列表则可跳过列表 Actor。
2. **拉候选或解析粘贴。** 主源 Product Hunt（`search_product_launches`）；paste 路径从正文抽候选卡。
3. **过滤 / 去重 / 截断。** 关键词粗过滤 + URL/标题去重；目标 3～7 条（保留前 12 供再筛）。
4. **深挖（默认 1）。** `deepFetch` 默认 1：只对拟入选第 1 条 `fetch_web_page`；`0`=关。
5. **写 artifact。** 按 [output.md](references/output.md) 拼完整 **artifact** → `write_file` → `artifact.json`。
6. **渲染视图。** 调用 **`render_view`** → `view.json`。勿手写 HTML。
7. **成功标准。** 盘上已有 reminder 中的 **output**；不要输出整包 JSON。

## Output

成功终态：盘上 reminder 指定的 **output**。字段与示例 → [output.md](references/output.md)。

## Verification

- [ ] 有关注域或可用粘贴列表
- [ ] `items` 非空（目标 3～7）；每条尽量有 `sourceUrl`
- [ ] `source` 为 `ph` 或 `paste`；`metadata.persistAs` = `tech_briefing`
- [ ] 已写 `artifact.json` 并成功 `render_view` → `view.json`
- [ ] 无候选且无粘贴 → Fail，不 settle

## Failures

只回一句人话：

- 没给关注域也没粘贴列表
- 列表空且无粘贴 / 全部取数失败
- 完全离题 → 拉回本 Skill

## Boundaries

- 禁止无候选编造条目；禁止无 `items` 仍 settle
- 近端主源仅 Product Hunt；不做多源合订、定时推送
