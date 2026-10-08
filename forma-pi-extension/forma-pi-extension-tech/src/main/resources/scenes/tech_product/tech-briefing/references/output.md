# Output schema — 产品早报（ToolRadar / Launly 对齐）

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿：** 成功时不要输出 `{"output":...}`；盘上已有 reminder 指定的 **output**（通常 `view.json`）即成功。

无关注域且无粘贴、列表空且无粘贴、全部取数失败时**不要**写这些文件，只回人话，不 settle。

## Skill 元数据

`SKILL.md` front matter：

| 键 | 值 |
|----|-----|
| `metadata.persistAs` | **`tech_briefing`** |
| `metadata.requiresView` | `true` |
| `metadata.output` | `view.json` |

## 取数纪律（近端）

| 项 | 约定 |
|----|------|
| 主源 | Product Hunt（`source=ph`）；粘贴列表 → `source=paste` |
| 条目数 | 成功路径目标 **3～7**；不足 3 且有候选 → 有几条出几条并可写入 `uncertainties`；**0 条 → Fail** |
| `deepFetch` | **默认 1**：只深挖拟入选第 1 条；`0`=关；近端上限 **1** |
| 禁止 | 无候选编造条目；无 `items` 仍 settle；「今日必读 Top10」空壳 |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**。

| 字段 | 要求 |
|------|------|
| `title` | 中文标题；与 `view.title` 相同（域 + 时间窗语义） |
| `topic` | 用户关注域/主题短句 |
| `windowLabel` | 时间窗展示文案（如「今天」「近 1 天」） |
| `source` | **`ph`** 或 **`paste`** |
| `deepFetch` | 本轮实际深挖条数；默认按 **1** 执行（`0`=关） |
| `items` | 数组，长度 **1～7**（成功路径目标 3～7） |
| `uncertainties` | 字符串数组（源不稳、筛选过猛、候选偏少等） |

### `items[]` 每条

| 字段 | 要求 |
|------|------|
| `title` | 产品/动态短名 |
| `oneLiner` | 是什么（1 句） |
| `whyNow` | 为什么现在值得跟（1～2 句） |
| `forWhom` | 可选；适合谁 |
| `sourceUrl` | 尽量有；来自候选卡 URL |
| `evidence` | 可选短句；来自 tagline 或深挖 quotes |
| `status` | 以 **`found`** 为主（有候选卡依据）；禁止无候选编造 |

## view

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 中文标题（域 + 时间窗语义） |
| `format` | **`html`** |
| `content` | Mustache 渲染；抬头（域 + 时间窗）+ 条目卡片（是什么 / 为什么值得跟 / 出处） |

**禁止**手递按钮；**禁止** v1 `blocks` JSON。

## 示例（节选）

```json
{
  "title": "AI coding agents · 今天产品早报",
  "topic": "AI coding agents",
  "windowLabel": "今天",
  "source": "ph",
  "deepFetch": 1,
  "items": [
    {
      "title": "Cursor Rules Hub",
      "oneLiner": "面向团队的 Cursor rules 分享与发现目录。",
      "whyNow": "刚上 PH，讨论多，适合跟规则分发形态。",
      "forWhom": "在团队里推 AI IDE 规范的工程负责人",
      "sourceUrl": "https://www.producthunt.com/posts/example",
      "evidence": "Share and discover Cursor rules across your team",
      "status": "found"
    }
  ],
  "uncertainties": ["列表源仅 Product Hunt，非全网热点"]
}
```

## 质量对照

| 好 | 坏 |
|----|-----|
| 每条能对应候选卡 URL / tagline | 无候选仍写 5 条「必读」 |
| `source=ph` 且 `deepFetch` 为 0 或 1 | 假装爬了全员详情 |
| 不足 3 条时诚实标「候选偏少」 | 无 `items` 仍 settle |
