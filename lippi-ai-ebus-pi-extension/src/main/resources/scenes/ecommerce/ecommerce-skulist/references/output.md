# Output schema

## 交付方式

1. **工作区文件（真源，分步）：** 先领域实体，再视图实体，最后合并信封（支持的合并是 `write_file`；勿依赖 `python3`）：
   - **策划：** `plan/artifact.json`（仅 artifact）→ `plan/view.json`（仅 view）→ **`plan/final.json`** = `{ "view": <view 根>, "artifact": <artifact 根> }`
   - **执行**（仅 `confirm_execute` 后）：`exec/artifact.json` → `exec/view.json` → **`exec/final.json`**（同上信封）
   - **补充：** 重写 `plan/artifact.json` / `plan/view.json` 后合并 `plan/final.json`，再发指针并 `ask_human`
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：
   - 策划（含补充后重出）：`{"output":"plan/final.json"}`
   - 执行终态：`{"output":"exec/final.json"}`

不要在对话里贴整包 `{view, artifact}`，也不要贴分文件全文。结算由服务端读对应 `final.json`。

`artifact` = 领域实体；`view` = 视图实体。两边同一事实。下方示例**按文件分开**给出；`final.json` 只做合并，不再另造一套字段。

本 Skill **一条 Workflow**：策划分文件 + 指针 + `ask_human` → 确认后执行分文件 + 指针。

Skill 元数据（`SKILL.md` front matter）：

```yaml
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
```

说明：应用层在首次 `ask_human` 前，将解析到的**策划** payload 以 `listing_plan` 落库并结算策划积分；用户确认后的**终态**仍按 `persistAs: sku` 落库。

## Contents

1. [对齐规则](#对齐规则)
2. [策划 artifact（领域）](#策划-artifact领域)
3. [策划 view（视图）](#策划-view视图)
4. [执行 artifact（领域）](#执行-artifact领域)
5. [执行 view（视图）](#执行-view视图)
6. [策划示例](#策划示例)
7. [执行示例](#执行示例)
8. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | 各阶段 `view.content` HTML 与 `artifact` 短字段一致 |
| 主图 | 策划：HTML「主图分镜」有序列表（与 `frames` 一致）；执行：`heroPlan` 写入正文 + **占位 hero 图**（见执行 view） |
| 风格底 | `templateId` 固定 `domestic-generic-default`（补充需求**不可**改） |
| 口吻 | 国内电商成交文案；策划偏分镜与大纲，执行偏可搜索标题与商详卖点 |
| 假设 | 信息不足时写 `assumptions`；有交接时写入原链与条目 id 摘要 |
| 交接 | 输入含「原链」或「来源选品条目」时，`picklistItemId` **必填**且与输入一致（黄金路径） |
| 禁止 | 伪造销量/榜单/资质；**不要**输出 `platformCopies` / `preferredPlatform` |
| 确认 | 盘上 `plan/final.json` 就绪并发出指针后必须 `ask_human`；**禁止**未确认前输出 `framePrompts` 或上架四字段 |

## 策划 artifact（领域）

写入 **`plan/artifact.json` 根对象**（不要再包 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与策划 `view.title` 相同 |
| `templateId` | `domestic-generic-default` |
| `driver` | 一句成交方向（谁 + 场景 + 为什么买） |
| `frames` | 3～5 条主图分镜短句（每条 ≤40 字；机位不重复） |
| `modules` | 3～5 条详情大纲短句 |
| `titleDraft` | 标题草稿一行 |
| `assumptions` | 可选；有交接时建议含原链与条目摘要 |
| `picklistItemId` | 有交接输入时**必填**（如 `pl-2`）；无交接的口述 Listing 可省略 |

策划阶段**不要**：`heroPlan` / `detailTitle` / `detailBody` / `displayNotes` / `framePrompts` / `mediaObjectIds`（除非应用后挂）。

## 策划 view（视图）

写入 **`plan/view.json` 根对象**（不要再包 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 给人看的中文标题 |
| `format` | **`html`** |
| `content` | 完整 HTML；根节点建议 `<article class="markdown-body">` |

HTML 固定小节（与 `artifact` 同一事实）：

| 小节 | 内容 |
|------|------|
| `<h2>成交方向</h2>` | = `driver` |
| `<h2>主图分镜</h2>` | `<ol>` = `frames` |
| `<h2>标题草稿</h2>` | = `titleDraft` |
| `<h2>详情大纲</h2>` | `<ol>` = `modules` |
| `<h2>假设</h2>` | 可选 = `assumptions` |

策划阶段**不要** hero 占位图、详情三件套、生图 Prompt 段。

**禁止** v1 `blocks` / storyboard JSON 视图。

## 执行 artifact（领域）

写入 **`exec/artifact.json` 根对象**。

| 字段 | 要求 |
|------|------|
| 继承 | `templateId` / `driver` / `frames` / `modules` / `titleDraft`（可微调）；`picklistItemId` 原样保留 |
| `heroPlan` / `detailTitle` / `detailBody` / `displayNotes` | 非空；上架四字段 |
| `framePrompts` | 与 `frames` 等长：`[{ "prompt": "…", "negative": "…" }]` |
| `mediaObjectIds` | 可先 `[]`；系统挂载后 settle 前至少 1 个真实 id |
| `title` | 与执行 `view.title` 相同 |
| `assumptions` | 可选 |
| `picklistItemId` | 有交接时必填且与策划一致 |

不要把 `blocks` / `format` / `content` 写进 `artifact`。

## 执行 view（视图）

写入 **`exec/view.json` 根对象**。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 给人看的中文标题 |
| `format` | **`html`** |
| `content` | 完整 HTML 文档（分镜与文案均以 **标题 + 段落/列表** 表达，不再用 blocks 拼装） |

HTML 结构建议（与 `artifact` 同一事实）：

1. `<h1>` = 标题  
2. **主图占位**（结算前由系统 patch `src`）：  
   `<img data-adam-media-role="hero" alt="主图占位" src="">`  
   可选紧跟一句 `heroPlan` 摘要  
3. `<h2>主图分镜</h2>` — `<ol>` 与 `frames` 等长；第 i 条分镜下可跟第 i 条 prompt 摘要（与 `framePrompts[i]` 对齐）  
4. `<h2>详情标题</h2>` — `detailTitle`  
5. `<h2>详情正文</h2>` — `detailBody`（可 `<p>` 分段）  
6. `<h2>展示说明</h2>` — `displayNotes`（可用 `class="cv-note"` 弱化）  
7. `<h2>生图 Prompt</h2>` — 有序列表，与 `framePrompts` 逐条对应（含 `negative` 时写在同条下方）

**禁止** v1 `blocks` / `media` block / `list` block JSON 视图。

### HTML 与转义

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内双引号用 `&quot;` 等实体转义。

## 策划示例

黄金路径：用户消息含原链与 `来源选品条目：pl-1`。先写领域实体，再写视图实体，再合并 `plan/final.json`，发指针后**立即** `ask_human`。

### `plan/artifact.json`

```json
{
  "title": "Mac Mini 拓展坞 · 策划分镜",
  "templateId": "domestic-generic-default",
  "driver": "居家办公把 Mini 接到显示器：接口不够 + 线要藏",
  "frames": [
    "首图：桌面前后对比 + 「线藏住了」角标",
    "图2：HDMI / USB / 网口特写",
    "图3：机身下走线隐藏"
  ],
  "modules": [
    "Mini 接显示器总缺口？一块坞把口补齐",
    "多口扩展 + 底部走线，桌面只留一套线",
    "机身同宽，不额外占桌面"
  ],
  "titleDraft": "Mac Mini 拓展坞 多口扩展 走线隐藏",
  "picklistItemId": "pl-1",
  "assumptions": "交接 pl-1；原链 https://item.taobao.com/example-sku-1；参考 Mac Mini 扩展 / 接口不够 / 居家办公；优先淘宝语气"
}
```

### `plan/view.json`

```json
{
  "version": 2,
  "title": "Mac Mini 拓展坞 · 策划分镜",
  "format": "html",
  "content": "<article class=\"markdown-body\"><h1>Mac Mini 拓展坞 · 策划分镜</h1><h2>成交方向</h2><p>居家办公把 Mini 接到显示器：接口不够 + 线要藏。</p><h2>主图分镜</h2><ol><li>首图：桌面前后对比 + 「线藏住了」角标</li><li>图2：HDMI / USB / 网口特写</li><li>图3：机身下走线隐藏</li></ol><h2>标题草稿</h2><p>Mac Mini 拓展坞 多口扩展 走线隐藏</p><h2>详情大纲</h2><ol><li>Mini 接显示器总缺口？一块坞把口补齐</li><li>多口扩展 + 底部走线，桌面只留一套线</li><li>机身同宽，不额外占桌面</li></ol><h2>假设</h2><p>交接 pl-1；原链 https://item.taobao.com/example-sku-1；参考 Mac Mini 扩展 / 接口不够 / 居家办公。</p></article>"
}
```

### `plan/final.json`（合并）

```json
{
  "view": { "...同 plan/view.json 根对象..." },
  "artifact": { "...同 plan/artifact.json 根对象..." }
}
```

指针：`{"output":"plan/final.json"}`

## 执行示例

仅在用户选择 `confirm_execute` 后。先 `exec/artifact.json`，再 `exec/view.json`，再合并 `exec/final.json`。

### `exec/artifact.json`

```json
{
  "title": "Mac Mini 拓展坞 · 上架素材",
  "templateId": "domestic-generic-default",
  "driver": "居家办公把 Mini 接到显示器：接口不够 + 线要藏",
  "frames": [
    "首图：桌面前后对比 + 「线藏住了」角标",
    "图2：HDMI / USB / 网口特写",
    "图3：机身下走线隐藏"
  ],
  "modules": [
    "Mini 接显示器总缺口？一块坞把口补齐",
    "多口扩展 + 底部走线，桌面只留一套线",
    "机身同宽，不额外占桌面"
  ],
  "titleDraft": "Mac Mini 拓展坞 多口扩展 走线隐藏",
  "heroPlan": "首图：线乱桌面 vs 坞藏线后；角标「线藏住了」。续图：接口特写 / 底部走线。",
  "detailTitle": "Mac Mini 拓展坞 多口扩展 走线隐藏 桌面不乱",
  "detailBody": "Mini 接显示器后接口不够、线乱，换成一块和机身差不多宽的拓展坞。\nHDMI、USB、网线从底座走，桌上只留电源和一根视频线。\n买前对一下自己的口，说明书里没有的认证不要写。",
  "displayNotes": "主图顺序：①桌面前后对比 ②接口特写 ③底部走线。图内文案宜短。勿写官方原装或未提供的认证，勿编造带宽实测。",
  "framePrompts": [
    {
      "prompt": "Product photo, Mac Mini with matching-width USB-C hub dock under the chassis, clean desk, HDMI USB ethernet cables exiting the base, short Chinese text overlay 线藏住了, commercial e-commerce style, soft daylight",
      "negative": "cluttered props, watermark, Apple official logo claims"
    },
    {
      "prompt": "Close-up of Mac Mini dock ports HDMI USB ethernet, hand inserting a USB drive, desk background blur, e-commerce detail shot",
      "negative": "blurry, distorted text"
    },
    {
      "prompt": "Cable routing through Mac Mini dock underside, hidden wiring, clean home office desk, e-commerce lifestyle photo",
      "negative": "messy cables covering the product, unrelated products"
    }
  ],
  "mediaObjectIds": [],
  "picklistItemId": "pl-1",
  "assumptions": "交接 pl-1；原链 https://item.taobao.com/example-sku-1；用户确认策划后生成执行稿；主图由系统占位挂载"
}
```

### `exec/view.json`

```json
{
  "version": 2,
  "title": "Mac Mini 拓展坞 · 上架素材",
  "format": "html",
  "content": "<article class=\"markdown-body\"><h1>Mac Mini 拓展坞 · 上架素材</h1><p><img data-adam-media-role=\"hero\" alt=\"主图占位\" src=\"\"></p><p>首图：线乱桌面 vs 坞藏线后；角标「线藏住了」。续图：接口特写 / 底部走线。</p><h2>主图分镜</h2><ol><li><p>首图：桌面前后对比 + 「线藏住了」角标</p><p><code>Product photo, Mac Mini with matching-width USB-C hub dock…</code></p></li><li><p>图2：HDMI / USB / 网口特写</p></li><li><p>图3：机身下走线隐藏</p></li></ol><h2>详情标题</h2><p>Mac Mini 拓展坞 多口扩展 走线隐藏 桌面不乱</p><h2>详情正文</h2><p>Mini 接显示器后接口不够、线乱，换成一块和机身差不多宽的拓展坞。</p><p>HDMI、USB、网线从底座走，桌上只留电源和一根视频线。</p><p>买前对一下自己的口，说明书里没有的认证不要写。</p><h2>展示说明</h2><p class=\"cv-note\">主图顺序：①桌面前后对比 ②接口特写 ③底部走线。图内文案宜短。勿写官方原装或未提供的认证，勿编造带宽实测。</p><h2>生图 Prompt</h2><ol><li><p>Product photo, Mac Mini with matching-width USB-C hub dock under the chassis, clean desk, HDMI USB ethernet cables exiting the base, short Chinese text overlay 线藏住了, commercial e-commerce style, soft daylight</p><p>negative: cluttered props, watermark, Apple official logo claims</p></li><li><p>Close-up of Mac Mini dock ports HDMI USB ethernet, hand inserting a USB drive, desk background blur, e-commerce detail shot</p><p>negative: blurry, distorted text</p></li><li><p>Cable routing through Mac Mini dock underside, hidden wiring, clean home office desk, e-commerce lifestyle photo</p><p>negative: messy cables covering the product, unrelated products</p></li></ol></article>"
}
```

### `exec/final.json`（合并）

```json
{
  "view": { "...同 exec/view.json 根对象..." },
  "artifact": { "...同 exec/artifact.json 根对象..." }
}
```

指针：`{"output":"exec/final.json"}`

失败路径：不要输出本 JSON，只回人话（见 SKILL § Failures）。

## 质量对照

各 1 组；对照 `driver` / `detailBody` / `framePrompt`。

### driver

| | 文案 |
|--|------|
| **好** | `居家办公把 Mini 接到显示器：接口不够 + 线要藏`（谁 + 场景 + 为什么买） |
| **坏** | `提升生活品质，让桌面更美好`（空泛套话，无受众/场景） |

### detailBody

| | 文案 |
|--|------|
| **好** | `Mini 接显示器后接口不够、线乱，换成一块和机身差不多宽的拓展坞。\nHDMI、USB、网线从底座走。\n买前对一下自己的口，说明书没有的认证不要写。`（场景 → 卖点 → 可知规格；无编造尺寸数值） |
| **坏** | `想换坞又怕踩坑？接口不够怎么办？会不会不兼容？……`（连续 ≥2 问句开场 + 鸡汤腔） |

### framePrompt（一条）

| | 文案 |
|--|------|
| **好** | `Product photo, silicone dish drying mat on kitchen counter, water droplets draining into grooves, clean bright kitchen, short Chinese text overlay 台面干爽, commercial e-commerce style, soft daylight`（主体 + 场景 + 约束，对齐首图分镜） |
| **坏** | `8k, masterpiece, best quality, ultra detailed product`（空壳形容词，无主体/场景） |
