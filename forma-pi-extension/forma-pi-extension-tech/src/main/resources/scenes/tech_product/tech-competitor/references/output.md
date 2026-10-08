# Output schema — 竞品分析（Urlcomp 对齐）

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿：** 成功时不要输出 `{"output":...}`；盘上已有 reminder 指定的 **output**（通常 `view.json`）即成功。

无可用正文（链接失败且无粘贴）时**不要**写这些文件，只回人话。

## 三态纪律

| `status` | 含义 |
|----------|------|
| `found` | 公开页/粘贴正文可核对；`value`/`bullets` 须能对上同层或 `excerpts` 的 `quotes` |
| `inferred` | 推断；**必须**有简短 `reason`；禁止标成 found |
| `not_public` | 材料没有；`value` 可空；可写 `reason` 说明为何未公开 |

禁止估算 MRR / CAC；禁止无 quotes 写 found 标价或功能。

## artifact（领域实体）

写入 **`artifact.json` 的根对象**。

| 字段 | 要求 |
|------|------|
| `title` | 产品短名；与 `view.title` 相同 |
| `oneLiner` | 一句话定位（本品是什么） |
| `source` | **`fetch`** 或 **`paste`** |
| `sourceUrl` | 有链接时填写；fetch 优先工具 `finalUrl` |
| `concern` | 用户「我关心…」；没有则 `""` |
| `excerpts` | 来自 `ingest_competitor`；`quotes[]` 为原文子串 |
| `snapshot.positioning` | `{ status, value, quotes? }` |
| `snapshot.audience` | 同上 |
| `snapshot.pricingSignal` | 同上；无公开价 → `not_public` |
| `whyPay` | `{ status, bullets[], quotes?, reason? }` |
| `packaging` | `{ status, value, reason? }` 公开套餐信号 |
| `growthSignals` | `{ status, items[], reason? }`；无证据可整层 `not_public` |
| `rivals` | `{ status, reason?, items: [{ name, note }] }`；**默认 `inferred`** |
| `uncertainties` | 字符串数组 |

## view

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 中文标题 |
| `format` | **`html`** |
| `content` | Mustache 渲染；含三态标签、对手列表、免责「请对照原文核实」 |

**禁止**手递按钮；**禁止** v1 `blocks` JSON。

## 示例（节选）

```json
{
  "title": "Notion",
  "oneLiner": "面向知识工作的一体化文档与协作空间。",
  "source": "fetch",
  "sourceUrl": "https://www.notion.so",
  "concern": "",
  "excerpts": [{ "heading": "Hero", "quotes": ["All-in-one workspace for notes, docs & projects"] }],
  "snapshot": {
    "positioning": {
      "status": "found",
      "value": "All-in-one workspace for notes, docs & projects",
      "quotes": ["All-in-one workspace for notes, docs & projects"]
    },
    "audience": { "status": "not_public", "value": "", "reason": "首页未明确受众句" },
    "pricingSignal": { "status": "not_public", "value": "", "reason": "本轮未抓定价页" }
  },
  "whyPay": {
    "status": "found",
    "bullets": ["笔记、文档与项目放在同一工作区"],
    "quotes": ["All-in-one workspace for notes, docs & projects"]
  },
  "packaging": { "status": "not_public", "value": "", "reason": "首页未见公开价档" },
  "growthSignals": { "status": "not_public", "items": [] },
  "rivals": {
    "status": "inferred",
    "reason": "由定位推断，页上未逐条列举",
    "items": [{ "name": "Coda", "note": "协作文档向" }]
  },
  "uncertainties": ["公开价档需打开 Pricing 页再核"]
}
```

## 质量对照

| 好 | 坏 |
|----|-----|
| found 句能在 excerpts 找到 | 编造「Pro $20/月」且标 found |
| rivals 标 inferred 并写 reason | 把猜测对手标 found |
| 无材料层用 not_public | 空编增长数据 |
