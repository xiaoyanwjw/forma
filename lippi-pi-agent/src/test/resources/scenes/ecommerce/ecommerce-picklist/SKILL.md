---
name: ecommerce-picklist
description: >-
  国内电商选品清单：先搜索推广池商品，再排名并输出双轨 JSON。
  在用户要「选品 / 卖什么 / 候选清单」时使用。
allowed-tools: read_skill search_sku
metadata:
  output:
    billing: true
    persistAs: picklist
    requiresView: true
---

# ecommerce-picklist

Adam 电商开店助手的**选品清单**路径（国内通用默认风格，无品类模板选择器）。

## When to use

- 用户要候选卖什么、选品清单、测款方向，或从诉求里提炼可卖 SKU 列表。
- 电商场景两条固定路径之一：**选品**（本 skill）；**素材 / Listing** 走 `ecommerce-skulist`。
- 超范围能力可简短说明后拉回选品或素材；不要假装交付未支持能力。

## Workflow（必须按序）

1. 从用户 query 提炼搜索词（信息不足时在 `artifact.assumptions` 写清假设，**仍先搜**）。
2. 调用 `search_sku`（**至少 1 次成功**）；零次成功搜索不得输出 8–12 条完整候选。
3. 基于工具返回筛选 / 排名（可再搜 1～2 次换词）；**只收录带有效 `detailUrl` 的条目**。
4. 写出合格 `artifact`（每条含 `sourceUrl` = 对应工具条目的 `detailUrl`）。
5. 用同一事实编 `view.blocks`：list **每一项必须**填 `href` = 同条 `artifact` 的 `sourceUrl`（仅白名单块类型）。
6. **只输出一个**双轨 JSON 对象（可包在 ```json 代码块中）。

计费与落库：`metadata.output` 表示本 skill 需可用 `artifact` 落库且 `view` 门禁通过后才 settle；不可用成果不得假装合格。

## Tools

- **`read_skill`**：按需加载本 skill 或其它已注册 skill 正文（编排层使用）。
- **`search_sku`**：
  - 入参：`query`（必填搜索词）、`platform`（默认 `taobao_tbk`，可选 `pdd_ddk`）、`pageSize`（小页，有上限）。
  - 出参摘要列表：每条含 `title`、`price` / 价格相关字段、`category`（若有）、**`detailUrl`（必填，可浏览器打开的 `https` 商品原链 / 推广落地链）**、可选 `rawRef`。
  - **失败或空结果**：用人话说明，可换词再搜 1～2 次；仍无合格条目则终态不得输出假合格 artifact（见 Failures）。
  - 无 `detailUrl` 的条目**不得**进入清单。

## Boundaries

- **做**：推广池检索 → 排序 → 8–12 条可测款候选 + 四维简评 + 可卖理由字段 + 可点原链。
- **不做**：Listing 主图/详情（`ecommerce-skulist`）、积分账本、系统提示词改写、向用户追问澄清（`ask_human`）。
- **禁止**：未成功搜索就编完整清单；编造或手写假链接；`href` / `sourceUrl` 必须来自工具返回的 `detailUrl`。
- **禁止伪造**实时平台指标数字（如 BSR、生意参谋搜索指数、实时销量排行）。
- **默认避开**（除非用户明确要求，不要放进 `artifact.items`）：重货泡货、强季节脉冲、高退货尺码敏感服饰、大牌价格极透明、需特殊资质、侵权/假认证/违禁功效空间。优先轻小件、好发货、可视觉差异化、可小批量测款。

## Output contract

| 字段 | 谁用 | 要求 |
|------|------|------|
| `view` | Computer 屏幕 | **必填**；块类型仅：`note` / `list` / `markdown` / `media` / `section` |
| `artifact` | 落库与扣积分 | **必填**；业务字段见下 |

先保证 `artifact` 校验能过，再把**同一事实**编进 `view.blocks`（不要在 `view` 里编造 `artifact` 没有的关键结论）。

### 硬性规则（`artifact`）

1. `items` 条数 **8–12**（含）；**≥3 个不同 `niche`**；禁止同质变体堆砌。
2. 恰好 **1–2** 条 `title` 以 `【优先试】` 开头；对应 `view` list 项 `"badge": "priority"`（`view.title` 不加该前缀）。
3. 每条非空：`title`、`priceBand`、`painPoint`、`angle`、`diff`、`niche`、`demand`、`competition`、`margin`、`risk`、**`sourceUrl`（非空 `https`，= 工具 `detailUrl`）**。
4. 四维以 `高｜` / `中｜` / `低｜`（全角竖线）开头后接简评。
5. 清单级非空 `disclaimer`（推广池抽样口径，见示例）；`view` 用 `note`（`tone: mute`）复述。
6. `templateId` 固定 `domestic-generic-default`。

### 双轨示例（各 1 条；交付时两边均 8–12 条且一一对应）

```json
{
  "view": {
    "version": 1,
    "title": "report",
    "status": "ready",
    "blocks": [
      {
        "type": "note",
        "tone": "mute",
        "text": "候选基于淘宝客/多多客推广池抽样检索与助手排序，非实时平台全站行情。点击可打开平台商品页核对。"
      },
      {
        "type": "list",
        "ordered": true,
        "items": [
          {
            "badge": "priority",
            "title": "硅胶沥水垫（多色）",
            "href": "https://item.taobao.com/example-sku-1",
            "lines": [
              { "kind": "priceBand", "text": "19–39 元", "emphasis": "price" },
              { "kind": "painPoint", "text": "水槽边易积水难打理" },
              { "kind": "angle", "text": "租房厨房刚需且轻小好发" },
              { "kind": "diff", "text": "多色套装+厚度对比主图" },
              { "kind": "niche", "text": "厨房沥水收纳" }
            ],
            "tags": [
              { "kind": "demand", "text": "高｜台面积水刚需、搜索意图清晰", "tone": "positive" },
              { "kind": "competition", "text": "中｜供给多但同质，视觉差异可切", "tone": "neutral" },
              { "kind": "margin", "text": "中｜低客单测款友好，注意包邮后毛利", "tone": "neutral" },
              { "kind": "risk", "text": "低｜勿夸大功效；材质合规表述", "tone": "positive" }
            ]
          }
        ]
      }
    ]
  },
  "artifact": {
    "templateId": "domestic-generic-default",
    "disclaimer": "候选基于淘宝客/多多客推广池抽样检索与助手排序，非实时平台全站行情。点击可打开平台商品页核对。",
    "assumptions": "未指定品类时按国内小件家居日用测款默认",
    "items": [
      {
        "title": "【优先试】硅胶沥水垫（多色）",
        "priceBand": "19–39 元",
        "painPoint": "水槽边易积水难打理",
        "angle": "租房厨房刚需且轻小好发",
        "diff": "多色套装+厚度对比主图",
        "niche": "厨房沥水收纳",
        "demand": "高｜台面积水刚需、搜索意图清晰",
        "competition": "中｜供给多但同质，视觉差异可切",
        "margin": "中｜低客单测款友好，注意包邮后毛利",
        "risk": "低｜勿夸大功效；材质合规表述",
        "sourceUrl": "https://item.taobao.com/example-sku-1"
      }
    ]
  }
}
```

`view.title` 用语义 key（如 `report`），不要写中文标题。`badge` 仅用 `priority` 或省略。`tone` 仅用 `mute` / `positive` / `warning` / `neutral`。禁止在 `view` 里写业务类型名（如 `picklist`）。预览验收：list 每项必须有 `href`（= 同条 `sourceUrl`），候选须可跳转原商品。

## Failures

- **工具错误 / 无结果**：`search_sku` 空 hits 视为失败（不算成功搜索）；用人话说明原因，不编造全站蓝海清单；不输出假合格 artifact。
- **工具结果缺 `detailUrl`**：该条不得进入 8–12；合格条数不足则整单失败、不 settle。
- **零次成功 `search_sku`**：不得交付完整选品 artifact。
