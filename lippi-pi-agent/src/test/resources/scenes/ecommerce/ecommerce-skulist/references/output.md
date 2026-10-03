# Output schema

## 交付方式

1. **工作区文件：** 先领域实体，再 **`render_view`**（勿手写 HTML `content`）：
   - **策划：** `write_file` → `plan/artifact.json` → **`render_view`**（`artifact`: `plan/artifact.json`，`out`: `plan/view.json`，`template`: `references/plan/view.mustache`）
   - **执行**（仅 `confirm_execute` 后）：`exec/artifact.json` → **`render_view`**（`artifact`: `exec/artifact.json`，`out`: `exec/view.json`，`template`: `references/exec/view.mustache`）
   - **补充：** 重写 `plan/artifact.json` 后重跑策划 `render_view`，再发指针并 `ask_human`
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：
   - 策划（含补充后重出）：`{"output":"plan/view.json"}`
   - 执行终态：`{"output":"exec/view.json"}`

不要在对话里贴分文件全文。结算由服务端读对应 **`plan/view.json`** / **`exec/view.json`**，并与同目录 **`plan/artifact.json`** / **`exec/artifact.json`** 对齐落库。

`artifact` = 领域实体；`view` = 由 `render_view` 渲染的视图实体。两边同一事实。下方示例**按文件分开**给出；**勿**再合并或交付 `final.json`。

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
| 确认 | 盘上 `plan/artifact.json` + `plan/view.json` 就绪并发出指针后必须 `ask_human`；**禁止**未确认前输出 `framePrompts` 或上架四字段 |

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
| `content` | 由 [plan/view.mustache](plan/view.mustache) 渲染的 HTML；根节点 `<article class="markdown-body">` |

HTML 固定小节（模板与 `artifact` 同一事实）：

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
| `content` | 由 [exec/view.mustache](exec/view.mustache) 渲染的 HTML 文档 |

HTML 结构（模板与 `artifact` 同一事实）：

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

黄金路径：用户消息含原链与 `来源选品条目：pl-1`。先写 `plan/artifact.json`，再 **`render_view`** 写出 `plan/view.json`，发指针后**立即** `ask_human`。

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

### `plan/view.json`（`render_view` 产出）

对上例 `plan/artifact.json` 调用策划 `render_view` 后，`plan/view.json` 含 v2 字段；`content` 由 [plan/view.mustache](plan/view.mustache) 填充。

指针：`{"output":"plan/view.json"}`

## 执行示例

仅在用户选择 `confirm_execute` 后。先 `exec/artifact.json`，再 **`render_view`** 写出 `exec/view.json`。

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

### `exec/view.json`（`render_view` 产出）

对上例 `exec/artifact.json` 调用执行 `render_view` 后，`exec/view.json` 含 v2 字段；`content` 由 [exec/view.mustache](exec/view.mustache) 填充，且含 `data-adam-media-role="hero"` 占位图。

指针：`{"output":"exec/view.json"}`

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
