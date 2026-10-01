# Output schema

## 交付方式

1. **工作区文件（真源）：** 分步写入子目录，再合并为信封 JSON：
   - **策划：** `plan/artifact.json`、`plan/view.json` → **`plan/final.json`**（内容为下方策划 `{ "view": …, "artifact": … }`）。
   - **执行**（仅 `confirm_execute` 后）：`exec/artifact.json`、`exec/view.json` → **`exec/final.json`**（完整 SKU 信封）。
   - **`supplement`：** 覆盖 `plan/*` 后重拼 `plan/final.json`，再发策划指针并 `ask_human`（同一 `runId` 工作区）。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：
   - 策划（含补充后重出）：`{"output":"plan/final.json"}`
   - 执行终态：`{"output":"exec/final.json"}`

不要在对话里再贴整包 `{view, artifact}`。结算由服务端读对应 `final.json` 后再投影 / 落库。

`view` 给界面渲染；`artifact` 落库回显。两边同一事实，不是互相拷贝。

本 Skill **两阶段**输出：

1. **策划**：盘上 `plan/final.json` + 指针 → 随后 **`ask_human`**（非 JSON 的一部分）。
2. **执行**（用户 `confirm_execute` 后）：盘上 `exec/final.json` + 指针（完整 SKU 字段 + `framePrompts`）。

Skill 元数据（`SKILL.md` front matter）：

```yaml
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
```

说明：应用层在首次 `ask_human` 前，将解析到的**策划** payload 以 `listing_plan` 落库并结算策划积分；用户确认后的**终态**仍按 `persistAs: sku` 落库。

合并后的 `plan/final.json` / `exec/final.json` 内容为 `{ "view": { }, "artifact": { } }` 信封（见下方示例）。对话里只发指针，对象外不要闲聊（`ask_human` 工具调用除外）。

## Contents

1. [对齐规则](#对齐规则)
2. [策划 artifact + view](#策划-artifact--view)
3. [执行 artifact + view](#执行-artifact--view)
4. [策划示例](#策划示例)
5. [执行示例](#执行示例)
6. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | 各阶段 `view` 与 `artifact` 同一商品事实 |
| 主图 | 策划：写进 Markdown「主图分镜」列表（与 `frames` 一致）；执行：业务真相为系统挂载后的 `mediaObjectId`，须写清 `heroPlan` |
| 风格底 | `templateId` 固定 `domestic-generic-default`（补充需求**不可**改） |
| 口吻 | 国内电商成交文案；策划偏分镜与大纲，执行偏可搜索标题与商详卖点 |
| 假设 | 信息不足时写 `assumptions`；有交接时写入原链与条目 id 摘要 |
| 交接 | 输入含「原链」或「来源选品条目」时，`picklistItemId` **必填**且与输入一致（黄金路径） |
| 禁止 | 伪造销量/榜单/资质；**不要**输出 `platformCopies` / `preferredPlatform` |
| 确认 | 盘上 `plan/final.json` 就绪并发出指针 `{"output":"plan/final.json"}` 后必须 `ask_human`；**禁止**未确认前输出 `framePrompts` 或上架四字段 |

## 策划 artifact + view

### artifact（策划）

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `templateId` | `domestic-generic-default` |
| `driver` | 一句成交方向（谁 + 场景 + 为什么买） |
| `frames` | 3～5 条主图分镜短句（每条 ≤40 字；机位不重复） |
| `modules` | 3～5 条详情大纲短句 |
| `titleDraft` | 标题草稿一行 |
| `assumptions` | 可选；有交接时建议含原链与条目摘要 |
| `picklistItemId` | 有交接输入时**必填**（如 `pl-2`）；无交接的口述 Listing 可省略 |

策划阶段**不要**：`heroPlan` / `detailTitle` / `detailBody` / `displayNotes` / `framePrompts` / `mediaObjectIds`（除非应用后挂）。

### view（策划）

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | 给人看的中文标题 |
| `status` | 可选；可写 `draft` 或 `ready` |
| `blocks` | **恰好 1 个** `{ "type": "markdown", "text": "…" }` |

Markdown `text` 固定小标题（与 `artifact` 同一事实）：

| 小节 | 内容 |
|------|------|
| `## 成交方向` | = `driver` |
| `## 主图分镜` | 有序列表 = `frames` |
| `## 标题草稿` | = `titleDraft` |
| `## 详情大纲` | 有序列表 = `modules` |
| `## 假设` | 可选 = `assumptions` |

策划阶段**不要**再用多块 `note` / `list` / `media` / `section` 拼盘。

## 执行 artifact + view

### artifact（执行）

| 字段 | 要求 |
|------|------|
| 继承 | `templateId` / `driver` / `frames` / `modules` / `titleDraft`（可微调）；`picklistItemId` 原样保留 |
| `heroPlan` / `detailTitle` / `detailBody` / `displayNotes` | 非空；上架四字段 |
| `framePrompts` | 与 `frames` 等长：`[{ "prompt": "…", "negative": "…" }]` |
| `mediaObjectIds` | 可先 `[]`；系统挂载后 settle 前至少 1 个真实 id |
| `title` | 与 `view.title` 相同 |
| `assumptions` | 可选 |
| `picklistItemId` | 有交接时必填且与策划一致 |

不要把 `blocks` 写进 `artifact`。

### view（执行）

| 字段 | block |
|------|--------|
| `heroPlan` | `media` hero（+ 可选 `mediaObjectId`） |
| `frames` | `list` 分镜 |
| `detailTitle` | `section`「详情标题」 |
| `detailBody` | `section`「详情正文」 |
| `displayNotes` | `section`「展示说明」，`tone: mute` |
| `framePrompts` | **一条** `section`「生图 Prompt」：`body` 有序列表与 `frames` / `artifact.framePrompts` 等长（可附 negative）。界面把 Prompt **并入「主图分镜」**：第 i 条分镜标题下跟第 i 条 prompt，不再单独占一大块标题区 |
| `driver` | 可选 `note` |

## 策划示例

以下为 **`plan/final.json` 文件内容**（写入盘后合并）。发指针 `{"output":"plan/final.json"}` 后，**立即**调用 `ask_human`（见 SKILL.md），不要在内嵌确认 UI。

（黄金路径：用户消息含原链与 `来源选品条目：pl-1`。）

```json
{
  "view": {
    "version": 1,
    "title": "硅胶沥水垫 · 策划分镜",
    "status": "draft",
    "blocks": [
      {
        "type": "markdown",
        "text": "## 成交方向\n租房小户型厨房用户：台面干爽 + 防滑收纳，少擦台面。\n\n## 主图分镜\n1. 首图：沥水动态特写 + 「台面干爽」角标\n2. 图2：碗碟防滑纹理近景\n3. 图3：一卷收纳进抽屉\n\n## 标题草稿\n厨房硅胶沥水垫 防滑易清洗 可折叠收纳\n\n## 详情大纲\n1. 洗完碗碟台面积水？一块垫解决沥干\n2. 防滑纹理 + 食品接触级硅胶，好清洗\n3. 卷折收纳，小户型厨房省空间\n\n## 假设\n交接 pl-1；原链 https://item.taobao.com/example-sku-1；参考厨房沥水收纳 / 台面积水 / 租房轻小。"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 策划分镜",
    "templateId": "domestic-generic-default",
    "driver": "租房小户型厨房用户：台面干爽 + 防滑收纳，少擦台面",
    "frames": [
      "首图：沥水动态特写 + 「台面干爽」角标",
      "图2：碗碟防滑纹理近景",
      "图3：一卷收纳进抽屉"
    ],
    "modules": [
      "洗完碗碟台面积水？一块垫解决沥干",
      "防滑纹理 + 食品接触级硅胶，好清洗",
      "卷折收纳，小户型厨房省空间"
    ],
    "titleDraft": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳",
    "picklistItemId": "pl-1",
    "assumptions": "交接 pl-1；原链 https://item.taobao.com/example-sku-1；参考厨房沥水收纳 / 台面积水 / 租房轻小；优先淘宝语气"
  }
}
```

## 执行示例

仅在用户选择 `confirm_execute` 后写入 **`exec/final.json`** 并输出指针 `{"output":"exec/final.json"}`（终态 `persistAs: sku`）。

```json
{
  "view": {
    "version": 1,
    "title": "硅胶沥水垫 · 上架素材",
    "status": "ready",
    "blocks": [
      {
        "type": "media",
        "role": "hero",
        "placeholder": "首图：沥水垫铺满台面特写，水珠顺槽流走；角标「台面干爽」。",
        "alt": "主图方案"
      },
      {
        "type": "list",
        "ordered": true,
        "items": [
          { "title": "首图：沥水动态特写 + 「台面干爽」角标" },
          { "title": "图2：碗碟防滑纹理近景" },
          { "title": "图3：一卷收纳进抽屉" }
        ]
      },
      {
        "type": "section",
        "heading": "详情标题",
        "body": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳 多色可选"
      },
      {
        "type": "section",
        "heading": "详情正文",
        "body": "洗完碗碟台面积水，铺上一块就能沥干，抹布不用一直擦。\n防滑纹理托住碗盘不易滑；食品接触级硅胶，柔软好清洗，水龙头下冲一冲就净。\n用完一卷塞进抽屉，小户型厨房、租房台面都省事。尺寸与颜色可选，按水槽边或台面长度挑选。"
      },
      {
        "type": "section",
        "heading": "展示说明",
        "body": "主图顺序：①首图沥水动态 ②防滑 ③卷折收纳。图内文案宜短。勿写杀菌医疗功效，勿编造月销。",
        "tone": "mute"
      },
      {
        "type": "section",
        "heading": "生图 Prompt",
        "body": "1. Product photo, silicone dish drying mat on kitchen counter, water droplets draining into grooves, clean bright kitchen, short Chinese text overlay 台面干爽, commercial e-commerce style, soft daylight\n   negative: cluttered props, watermark, medical claims\n2. Close-up of textured silicone mat surface holding plates and bowls securely, anti-slip pattern visible, kitchen background blur, e-commerce detail shot\n   negative: blurry, distorted text\n3. Rolled silicone drying mat fitting into kitchen drawer, compact storage scene, warm home kitchen, e-commerce lifestyle photo\n   negative: messy drawer, unrelated products"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 上架素材",
    "templateId": "domestic-generic-default",
    "driver": "租房小户型厨房用户：台面干爽 + 防滑收纳，少擦台面",
    "frames": [
      "首图：沥水动态特写 + 「台面干爽」角标",
      "图2：碗碟防滑纹理近景",
      "图3：一卷收纳进抽屉"
    ],
    "modules": [
      "洗完碗碟台面积水？一块垫解决沥干",
      "防滑纹理 + 食品接触级硅胶，好清洗",
      "卷折收纳，小户型厨房省空间"
    ],
    "titleDraft": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳",
    "heroPlan": "首图：沥水垫铺满台面特写，水珠顺槽流走；角标「台面干爽」。续图：碗碟不滑 / 一卷收纳。",
    "detailTitle": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳 多色可选",
    "detailBody": "洗完碗碟台面积水，铺上一块就能沥干，抹布不用一直擦。\n防滑纹理托住碗盘不易滑；食品接触级硅胶，柔软好清洗。\n用完一卷塞进抽屉，小户型厨房更省事。",
    "displayNotes": "主图顺序：①首图沥水动态 ②防滑 ③卷折收纳。图内文案宜短。勿写杀菌医疗功效，勿编造月销。",
    "framePrompts": [
      {
        "prompt": "Product photo, silicone dish drying mat on kitchen counter, water droplets draining into grooves, clean bright kitchen, short Chinese text overlay 台面干爽, commercial e-commerce style, soft daylight",
        "negative": "cluttered props, watermark, medical claims"
      },
      {
        "prompt": "Close-up of textured silicone mat surface holding plates and bowls securely, anti-slip pattern visible, kitchen background blur, e-commerce detail shot",
        "negative": "blurry, distorted text"
      },
      {
        "prompt": "Rolled silicone drying mat fitting into kitchen drawer, compact storage scene, warm home kitchen, e-commerce lifestyle photo",
        "negative": "messy drawer, unrelated products"
      }
    ],
    "mediaObjectIds": [],
    "picklistItemId": "pl-1",
    "assumptions": "交接 pl-1；原链 https://item.taobao.com/example-sku-1；用户确认策划后生成执行稿；主图由系统占位挂载"
  }
}
```

失败路径：不要输出本 JSON，只回人话（见 SKILL § Failures）。

## 质量对照

各 1 组；对照 `driver` / `detailBody` / `framePrompt`。

### driver

| | 文案 |
|--|------|
| **好** | `租房小户型厨房用户：台面干爽 + 防滑收纳，少擦台面`（谁 + 场景 + 为什么买） |
| **坏** | `提升生活品质，让厨房更美好`（空泛套话，无受众/场景） |

### detailBody

| | 文案 |
|--|------|
| **好** | `洗完碗碟台面积水，铺上一块就能沥干。\n防滑纹理托住碗盘；食品接触级硅胶好清洗。\n用完一卷塞进抽屉，小户型更省事。`（场景 → 卖点 → 可知规格；无编造尺寸数值） |
| **坏** | `想换垫又怕踩坑？台面总积水怎么办？多久洗一次？会不会发霉？……`（连续 ≥2 问句开场 + 鸡汤腔） |

### framePrompt（一条）

| | 文案 |
|--|------|
| **好** | `Product photo, silicone dish drying mat on kitchen counter, water droplets draining into grooves, clean bright kitchen, short Chinese text overlay 台面干爽, commercial e-commerce style, soft daylight`（主体 + 场景 + 约束，对齐首图分镜） |
| **坏** | `8k, masterpiece, best quality, ultra detailed product`（空壳形容词，无主体/场景） |
