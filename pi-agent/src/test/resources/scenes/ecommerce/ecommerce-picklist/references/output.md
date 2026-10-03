# Output schema

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"view.json"}
```

不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 **`view.json`**，并与同目录 **`artifact.json`** 对齐落库。

`artifact` = 领域实体（测款事实）；`view` = 由模板渲染的界面文档。条目顺序与 id 须一致。

## Contents

1. [对齐规则](#对齐规则)
2. [artifact（领域实体）](#artifact领域实体)
3. [view（视图实体）](#view视图实体)
4. [手递按钮与 prompt 合同](#手递按钮与-prompt-合同)
5. [示例](#示例)
6. [质量对照（条目）](#质量对照条目)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | `view.content` 中速览矩阵与详情各对应一条 `artifact.items[]` |
| id 一致 | 每条手递 prompt 含 `来源选品条目：pl-{n}`，与 `artifact.items[].id` 同序同值 |
| 链接一致 | `sourceUrl` = 该条 `detailUrl`；prompt「原链」= 同一 URL；仅绝对 `https:`；禁止编造 |
| 条数 | 成功时 `artifact.items` 与 HTML 矩阵/详情条数均为 **8–12**（下方示例为简洁只写 1 条） |
| 免责声明 | 非空，且必须包含字面量 **`非实时平台全站行情`**。推荐整句：`候选基于配置的商品检索抽样与服务端排序，非实时平台全站行情。点击可打开商品页核对。` |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同的中文清单标题 |
| `templateId` | `domestic-generic-default` |
| `disclaimer` | 同 view 正文免责声明合同 |
| `assumptions` | 可选；用户信息不足时的搜索假设 |
| `items` | 成功时长度 **8–12** |

### items[]

| 字段 | 要求 |
|------|------|
| `id` | 必填；本清单内唯一；格式 `pl-{n}` 从 1 顺序 |
| `title` | 全清单恰好 **1–2** 条以 `【优先试】` 开头 |
| `priceBand` | 价格带（以工具抽样为准） |
| `painPoint` / `angle` / `diff` | 痛点 / 角度 / 差异化 |
| `niche` | 细分场景；全清单 **≥3 个不同** niche |
| `demand` / `competition` / `margin` / `risk` | 以 `高｜` / `中｜` / `低｜` 开头，后接简评 |
| `sourceUrl` | = 该条 `detailUrl` |

不要把 `blocks` / `format` / `content` 写进 `artifact`。

## view（视图实体）

**`view.json`** 由 `render_view` 写出（根对象，无 `"view":` 包裹）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`**（工具默认） |
| `title` | 来自 `artifact.title` |
| `format` | **`html`** |
| `content` | Mustache 渲染 [view.mustache](../template/view.mustache) 的结果 |

模板数据根 = 整棵 **artifact**；渲染前工具在内存为每条 `items[]` 注入（**不写回** `artifact.json`）：

| 注入字段 | 含义 |
|----------|------|
| `displayTitle` | `title` 去掉 `【优先试】` 标记 |
| `indexLabel` | `01` 起的行号 |
| `priorityTry` | 原 `title` 含 `【优先试】` 时为 true |
| `demandLabel` / `demandClass` 等 | 评分短标签与盾牌色阶（`ok`/`mid`/`warn`/`gray`/`bad`） |
| `itemCount` | 清单条数（根级） |
| `handoffPrompt` | 符合下方手递合同的多行 prompt；缺 `title`/`id` 或非 `https:` 原链时不注入 |

**禁止** v1 `blocks` / `list` JSON 视图；**禁止**模型手写 `content`。

验收：每条有 `https:` 原链时 `handoffPrompt` 含同一「原链」；HTML 按钮由模板输出，点击开跑 `ecommerce-skulist`。

## 手递按钮与 prompt 合同

每条候选 **一条** 按钮：

```html
<button
  type="button"
  data-forma-action="handoff"
  data-forma-skill-id="ecommerce-skulist"
  data-forma-prompt="…"
>做上架素材</button>
```

`data-forma-prompt` 正文须与下列模板一致（`{title}` 为去掉 `【优先试】` 后的商品名；可选行仅在有值时追加）：

```text
请为商品「{title}」生成上架素材。
原链：{https href}
来源选品条目：{id}
参考：{niche?}
痛点：{painPoint?}
角度：{angle?}
```

缺 `title`、缺 `id`、或非 `https:` 原链 → **不要** 为该条写按钮（此类条目不应进入成功清单）。

## 示例

各 1 条示意（交付时均为 8–12）。先写 `artifact.json`，再 `render_view`。

### `artifact.json`（领域实体）

```json
{
  "title": "Mac Mini 配件 79–199 元选品清单",
  "templateId": "domestic-generic-default",
  "disclaimer": "候选基于配置的商品检索抽样与服务端排序，非实时平台全站行情。点击可打开商品页核对。",
  "assumptions": "未指定品类时按国内 Mac Mini 配件测款默认",
  "items": [
    {
      "id": "pl-1",
      "title": "【优先试】Mac Mini 拓展坞",
      "priceBand": "79–199 元",
      "painPoint": "Mini 接显示器后接口不够、线乱",
      "angle": "居家办公桌搭、接口对比好拍",
      "diff": "机身同宽+底部走线好出图",
      "niche": "Mac Mini 扩展",
      "demand": "高｜Mini 接显示器接口搜索意图清晰",
      "competition": "中｜供给多但同质，接口对比可切",
      "margin": "中｜中客单测款友好，注意包邮后毛利",
      "risk": "低｜勿写官方原装或未提供认证",
      "sourceUrl": "https://item.taobao.com/example-sku-1"
    }
  ]
}
```

### `view.json`（`render_view` 产出）

对上例 `artifact.json` 调用 `render_view` 后，`view.json` 含 v2 字段；`content` 由 [view.mustache](../template/view.mustache) 填充，且含 `data-forma-skill-id="ecommerce-skulist"` 手递按钮。

**对话终稿指针：**

```json
{"output":"view.json"}
```

失败路径：不要输出指针或本 JSON，只回人话（见 SKILL § Failures）。

## 质量对照（条目）

各 1 条；对照「优先试理由 / niche / 评分条」。

### 好条目（可交付）

- **优先试：**「Mini 接显示器接口不够高频；拓展坞比同清单普通支架更好拍接口对比」
- **niche：** `Mac Mini 扩展`（场景具体）
- **评分：** `高｜Mini 接显示器接口搜索意图清晰`（挂钩可观察事实）

### 坏条目（禁止）

- **优先试：**「市场需求大、性价比高」（套话，未对比下一条）
- **niche：** `日用` / `家居`（空泛凑数）
- **评分：** `高｜刚需`（无事实锚点）
