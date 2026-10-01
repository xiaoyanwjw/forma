# Output schema

## 交付方式

1. **工作区文件（真源）：** 分步写入 `artifact.json`、`view.json`，再用 `write_file` 合并为 run 根下 **`final.json`**（内容为下方 `{ "view": …, "artifact": … }` 信封）。支持的合并是 `write_file`；`bash` / `python3` 仅在环境里已有时可选，不是必须。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"final.json"}
```

不要在对话里再贴整包 `{view, artifact}`。结算由服务端读 `final.json` 后再投影 / 落库。

`view` 给界面渲染；`artifact` 落库回显。两边同一事实，不是互相拷贝。

## Contents

1. [对齐规则](#对齐规则)
2. [view](#view)
3. [artifact](#artifact)
4. [示例](#示例)
5. [质量对照（条目）](#质量对照条目)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | `view` 每个候选对应一条 `artifact.items[]` |
| id 一致 | `items[].id` = `list.items[].id` = `pl-{n}`，从 1 按最终顺序编号，同序同值 |
| 链接一致 | `sourceUrl` = 该条 `detailUrl`；list `href` = 同一 `sourceUrl`；仅绝对 `https:`；禁止编造 |
| 条数 | 成功时两边均为 **8–12**（下方示例为简洁只写 1 条） |
| 免责声明 | 非空，且必须包含字面量 **`非实时平台全站行情`**。推荐整句：`候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。` |

## view

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | **给人看的中文标题**（由本轮生成，建议与 `artifact.title` 一致，如「厨房小件 19–39 元选品清单」）；勿写裸 key `report` / `picklist` |
| `status` | 可选；成功可写 `ready`（界面不展示） |
| `blocks` | 仅 `note` / `list` / `markdown` / `media` / `section` |

选品常用两块：

1. `note`（`tone: mute`）：放免责声明（含 `非实时平台全站行情`）
2. `list`（`ordered: true`）：每条候选一行

### list.items[]

| 字段 | 要求 |
|------|------|
| `id` | 必填；本清单内唯一；格式 `pl-{n}` 从 1 顺序；与对应 `artifact.items[].id` 同序同值 |
| `title` | 商品名；此处**不加** `【优先试】` |
| `href` | = 对应 `artifact.items[].sourceUrl` |
| `badge` | 优先试条目用 `"priority"`（全清单 1–2 条） |
| `lines` | 短事实；`kind` 如 `priceBand` / `painPoint` / `angle` / `diff` / `niche` |
| `tags` | 评分条；`kind`：`demand` / `competition` / `margin` / `risk` |

`tone` 仅：`mute` / `positive` / `warning` / `neutral`。  
验收：每条 list 的 `href` 能打开真实商品页。

## artifact

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同的中文清单标题 |
| `templateId` | `domestic-generic-default` |
| `disclaimer` | 同 view note 的免责声明合同 |
| `assumptions` | 可选；用户信息不足时的搜索假设 |
| `items` | 成功时长度 **8–12** |

### items[]

| 字段 | 要求 |
|------|------|
| `id` | 必填；本清单内唯一；格式 `pl-{n}` 从 1 顺序；与 list 同序同 id |
| `title` | 全清单恰好 **1–2** 条以 `【优先试】` 开头 |
| `priceBand` | 价格带（以工具抽样为准） |
| `painPoint` / `angle` / `diff` | 痛点 / 角度 / 差异化 |
| `niche` | 细分场景；全清单 **≥3 个不同** niche |
| `demand` / `competition` / `margin` / `risk` | 以 `高｜` / `中｜` / `低｜` 开头，后接简评 |
| `sourceUrl` | = 该条 `detailUrl` |

不要把 `blocks` / `badge` / `lines` / `tags` 写进 `artifact`。

## 示例

以下为 **`final.json` 文件内容**（各 1 条；交付时两边均 8–12）。

```json
{
  "view": {
    "version": 1,
    "title": "厨房小件 19–39 元选品清单",
    "status": "ready",
    "blocks": [
      {
        "type": "note",
        "tone": "mute",
        "text": "候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。"
      },
      {
        "type": "list",
        "ordered": true,
        "items": [
          {
            "id": "pl-1",
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
    "title": "厨房小件 19–39 元选品清单",
    "templateId": "domestic-generic-default",
    "disclaimer": "候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。",
    "assumptions": "未指定品类时按国内小件家居日用测款默认",
    "items": [
      {
        "id": "pl-1",
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

**对话终稿指针（单独一行 JSON，非文件内容）：**

```json
{"output":"final.json"}
```

失败路径：不要输出指针或本 JSON，只回人话（见 SKILL § Failures）。

## 质量对照（条目）

各 1 条；对照「优先试理由 / niche / 评分条」。

### 好条目（可交付）

- **优先试：**「租房厨房台面积水高频；轻小好发，比同清单不锈钢沥水架更易视觉差异测款」
- **niche：** `厨房沥水收纳`（场景具体）
- **评分：** `高｜台面积水刚需、搜索意图清晰`（挂钩可观察事实）

### 坏条目（禁止）

- **优先试：**「市场需求大、性价比高」（套话，未对比下一条）
- **niche：** `日用` / `家居`（空泛凑数）
- **评分：** `高｜刚需`（无事实锚点）
