# Output schema

## 交付方式

1. **工作区文件（真源，分步）：**
   - 领域实体 → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图实体 → `view.json`（**仅** view 对象，见下方示例）
   - 再用 `write_file` 合并为 run 根下 **`final.json`**：`{ "view": <view.json 根对象>, "artifact": <artifact.json 根对象> }`。支持的合并是 `write_file`；勿依赖 `python3`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"final.json"}
```

不要在对话里贴整包 `{view, artifact}`，也不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 `final.json` 后再投影 / 落库。

`artifact` = 领域实体（测款事实）；`view` = 视图实体（界面渲染）。两边同一事实，不是互相拷贝。

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
| 同一事实 | `view.content` 中每条 `<li>` 对应一条 `artifact.items[]` |
| id 一致 | 每条手递 prompt 含 `来源选品条目：pl-{n}`，与 `artifact.items[].id` 同序同值 |
| 链接一致 | `sourceUrl` = 该条 `detailUrl`；prompt「原链」= 同一 URL；仅绝对 `https:`；禁止编造 |
| 条数 | 成功时 `artifact.items` 与 HTML 有序列表均为 **8–12**（下方示例为简洁只写 1 条） |
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

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | **给人看的中文标题**（建议与 `artifact.title` 一致）；勿写裸 key `report` / `picklist` |
| `format` | **`html`** |
| `content` | 完整 HTML 字符串；根节点建议 `<article class="markdown-body">` |

正文结构建议：

1. `<h1>` = 清单标题（可与 `title` 相同）
2. 免责声明段落（`class="cv-note"` 或 `<blockquote>`，含 `非实时平台全站行情`）
3. `<ol>` 有序列表：每条候选一个 `<li>`
4. 每条 `<li>` 内展示：商品名（**不加** `【优先试】`）、价格带 / 痛点 / 角度等短事实；优先试条目可加「优先试」标记
5. 每条 `<li>` 末尾 **必须** 含手递按钮（见下一节）

**禁止** 再输出 v1 `blocks` / `list` JSON 视图。

### HTML 与转义

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内的 ASCII 双引号用 HTML 实体（如 `&quot;`）转义。
- `data-adam-prompt` 内多行 prompt 在 JSON 里用 `\n` 表示换行。

验收：每条有 `https:` 原链的条目，其按钮 prompt 含同一原链；点击由前端开跑 `ecommerce-skulist`。

## 手递按钮与 prompt 合同

每条候选 **一条** 按钮：

```html
<button
  type="button"
  data-adam-action="handoff"
  data-adam-skill-id="ecommerce-skulist"
  data-adam-prompt="…"
>做上架素材</button>
```

`data-adam-prompt` 正文须与下列模板一致（`{title}` 为去掉 `【优先试】` 后的商品名；可选行仅在有值时追加）：

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

各 1 条示意（交付时均为 8–12）。先写领域实体，再写视图实体，最后合并。

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

### `view.json`（视图实体）

```json
{
  "version": 2,
  "title": "Mac Mini 配件 79–199 元选品清单",
  "format": "html",
  "content": "<article class=\"markdown-body\"><h1>Mac Mini 配件 79–199 元选品清单</h1><p class=\"cv-note\">候选基于配置的商品检索抽样与服务端排序，非实时平台全站行情。点击可打开商品页核对。</p><ol><li><p><span class=\"priority-tag\">优先试</span> <strong>Mac Mini 拓展坞</strong></p><p>79–199 元 · 痛点：Mini 接显示器后接口不够、线乱 · 切入：居家办公桌搭、接口对比好拍 · 细分：Mac Mini 扩展</p><p>需求：高｜Mini 接显示器接口搜索意图清晰 · 竞争：中｜供给多但同质 · 利润：中｜中客单测款友好 · 风险：低｜勿写官方原装或未提供认证</p><p><button type=\"button\" data-adam-action=\"handoff\" data-adam-skill-id=\"ecommerce-skulist\" data-adam-prompt=\"请为商品「Mac Mini 拓展坞」生成上架素材。\n原链：https://item.taobao.com/example-sku-1\n来源选品条目：pl-1\n参考：Mac Mini 扩展\n痛点：Mini 接显示器后接口不够、线乱\n角度：居家办公桌搭、接口对比好拍\">做上架素材</button></p></li></ol></article>"
}
```

### `final.json`（合并，非手写第二套事实）

把上面两个**根对象**包进信封即可（不要改写字段）：

```json
{
  "view": { "...同 view.json 根对象..." },
  "artifact": { "...同 artifact.json 根对象..." }
}
```

**对话终稿指针：**

```json
{"output":"final.json"}
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
