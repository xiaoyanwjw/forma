# Listing 策划 view 改为单篇 Markdown（设计）

**Date:** 2026-09-28  
**Status:** accepted  
**Decision:** 方案 1 — 策划阶段 Computer 只渲染 **一条** `markdown` block；`artifact` 短字段不变；执行稿 view 不变。  
**Parent:** [`2026-09-27-listing-storyboard-hitl-design.md`](./2026-09-27-listing-storyboard-hitl-design.md)

---

## 1. Problem

策划阶段 `view.blocks` 用多块 `note` / `list` / `media` / `section` 拼分镜，信息正确但碎，用户难当「一篇策划」读。

## 2. Decision

| 层 | 约定 |
|----|------|
| 策划 `view.blocks` | **仅 1 个** `{ "type": "markdown", "text": "…" }` |
| Markdown 结构 | 固定小标题：`## 成交方向` / `## 主图分镜`（有序列表）/ `## 标题草稿` / `## 详情大纲`（有序列表）/ 可选 `## 假设` |
| 策划 `artifact` | 仍短字段门闩：`templateId` / `driver` / `frames` 3～5 / `modules` 3～5 / `titleDraft`（+ 可选 `assumptions`） |
| 事实一致 | Markdown 正文与 `artifact` 同一事实，勿各写一套 |
| 执行稿 | **不改**：仍 hero `media` + 三 `section` + Prompt 摘要 |
| FE | 不新增业务组件；沿用通用 `markdown` block（当前按纯文本 `pre-wrap` 展示即可） |
| 计费 / HITL | 不变：策划 settle → `ask_human` → 确认后再执行 settle |

## 3. Non-goals

- 不真出图；不在 Computer 内嵌确认按钮。  
- 不把 Markdown 当落库真源（真源仍是 `artifact`）。  
- 本迭代不强制上 Markdown 富渲染（粗体/标题样式）；可读性靠小标题与列表足够。  
- 不改选品清单。

## 4. Skill / 示例改动面

- `ecommerce-skulist/SKILL.md`：策划 `view` 改为「单 markdown」。  
- `references/output.md`：策划 mapping + 示例 JSON。  
- 父规约 §4.2 策划 `view` 映射改为指向本文。  
- 测试里仍用「有 `view` + 可用 plan payload」即可；若有断言多 block 类型的用例，改为单 markdown。

## 5. 示例（片段）

```json
{
  "view": {
    "version": 1,
    "title": "硅胶沥水垫 · 策划分镜",
    "status": "draft",
    "blocks": [
      {
        "type": "markdown",
        "text": "## 成交方向\n台面干爽 + 防滑收纳，打动小户型厨房用户。\n\n## 主图分镜\n1. 首图：沥水动态特写 + 「台面干爽」角标\n2. 图2：碗碟防滑纹理近景\n3. 图3：一卷收纳进抽屉\n\n## 标题草稿\n厨房硅胶沥水垫 防滑易清洗 可折叠收纳\n\n## 详情大纲\n1. 洗完碗碟台面积水？一块垫解决沥干\n2. 防滑纹理 + 食品接触级硅胶，好清洗\n3. 卷折收纳，小户型厨房省空间\n\n## 假设\n按国内电商、优先淘宝语气；未提供实物图。"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 策划分镜",
    "templateId": "domestic-generic-default",
    "driver": "台面干爽 + 防滑收纳，小户型厨房省心",
    "frames": ["首图：沥水动态特写 + 「台面干爽」角标", "图2：碗碟防滑纹理近景", "图3：一卷收纳进抽屉"],
    "modules": ["洗完碗碟台面积水？一块垫解决沥干", "防滑纹理 + 食品接触级硅胶，好清洗", "卷折收纳，小户型厨房省空间"],
    "titleDraft": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳",
    "assumptions": "用户优先适配淘宝；按国内电商成交方向写策划"
  }
}
```

## 6. Done when

1. 新跑 Listing：Computer 策划态是一篇带小标题的 Markdown，不再碎块拼盘。  
2. 策划仍能落库 + 扣 1 分 + 出现 `ask_human`。  
3. 确认后执行稿观感与现网一致。  
