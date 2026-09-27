# Output schema

`view` 给界面渲染；`artifact` 落库回显。两边同一事实，不是互相拷贝。

本 Skill **两阶段**输出：

1. **策划**：短字段 JSON → 随后 **`ask_human`**（非 JSON 的一部分）。
2. **执行**（用户 `confirm_execute` 后）：完整 SKU 字段 + `framePrompts` 的 JSON 终态。

Skill 元数据（`SKILL.md` front matter）：

```yaml
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
```

说明：应用层在首次 `ask_human` 前，将解析到的**策划** payload 以 `listing_plan` 落库并结算策划积分；用户确认后的**终态**仍按 `persistAs: sku` 落库。

每次阶段成功时返回**一个** JSON 对象（可用 ` ```json ` 围栏），对象外不要闲聊（`ask_human` 工具调用除外）。

```json
{ "view": { }, "artifact": { } }
```

## Contents

1. [对齐规则](#对齐规则)
2. [策划 artifact + view](#策划-artifact--view)
3. [执行 artifact + view](#执行-artifact--view)
4. [策划示例](#策划示例)
5. [执行示例](#执行示例)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | 各阶段 `view` 与 `artifact` 同一商品事实 |
| 主图 | 策划：`frames[0]` → hero `media` placeholder；执行：业务真相为系统挂载后的 `mediaObjectId`，须写清 `heroPlan` |
| 风格底 | `templateId` 固定 `domestic-generic-default`（补充需求**不可**改） |
| 口吻 | 国内电商成交文案；策划偏分镜与大纲，执行偏可搜索标题与商详卖点 |
| 假设 | 信息不足时写 `assumptions` |
| 禁止 | 伪造销量/榜单/资质；**不要**输出 `platformCopies` / `preferredPlatform` |
| 确认 | 策划 JSON 后必须 `ask_human`；**禁止**未确认前输出 `framePrompts` 或上架四字段 |

## 策划 artifact + view

### artifact（策划）

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `templateId` | `domestic-generic-default` |
| `driver` | 一句成交方向 |
| `frames` | 3～5 条主图分镜短句（每条 ≤40 字） |
| `modules` | 3～5 条详情大纲短句 |
| `titleDraft` | 标题草稿一行 |
| `assumptions` | 可选 |
| `picklistItemId` | 可选 |

策划阶段**不要**：`heroPlan` / `detailTitle` / `detailBody` / `displayNotes` / `framePrompts` / `mediaObjectIds`（除非应用后挂）。

### view（策划）

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | 给人看的中文标题 |
| `status` | 可选；可写 `draft` 或 `ready` |
| `blocks` | 仅 `note` / `list` / `markdown` / `media` / `section` |

| 策划字段 | block |
|----------|--------|
| `driver` | `note`，`tone: mute` |
| `frames[0]` | `media`，`role: hero`，`placeholder` |
| `frames` | `list`，ordered，items 用 `title` |
| `titleDraft` | `section`「标题草稿」 |
| `modules` | `section`「详情大纲」+ `list` 或正文列举 |
| `assumptions` | `note`，可选 `kind: assumptions` |

## 执行 artifact + view

### artifact（执行）

| 字段 | 要求 |
|------|------|
| 继承 | `templateId` / `driver` / `frames` / `modules` / `titleDraft`（可微调） |
| `heroPlan` / `detailTitle` / `detailBody` / `displayNotes` | 非空；上架四字段 |
| `framePrompts` | 与 `frames` 等长：`[{ "prompt": "…", "negative": "…" }]` |
| `mediaObjectIds` | 可先 `[]`；系统挂载后 settle 前至少 1 个真实 id |
| `title` | 与 `view.title` 相同 |
| `assumptions` / `picklistItemId` | 可选 |

不要把 `blocks` 写进 `artifact`。

### view（执行）

| 字段 | block |
|------|--------|
| `heroPlan` | `media` hero（+ 可选 `mediaObjectId`） |
| `frames` | `list` 分镜 |
| `detailTitle` | `section`「详情标题」 |
| `detailBody` | `section`「详情正文」 |
| `displayNotes` | `section`「展示说明」，`tone: mute` |
| `framePrompts` | **一条** `section`「生图 Prompt」摘要 |
| `driver` | 可选 `note` |

## 策划示例

策划 JSON 输出后，**立即**调用 `ask_human`（见 SKILL.md），不要在本 JSON 内嵌确认 UI。

```json
{
  "view": {
    "version": 1,
    "title": "硅胶沥水垫 · 策划分镜",
    "status": "draft",
    "blocks": [
      {
        "type": "note",
        "text": "成交方向：台面干爽 + 防滑收纳，打动小户型厨房用户。",
        "tone": "mute"
      },
      {
        "type": "media",
        "role": "hero",
        "placeholder": "首图：沥水垫铺满台面，水珠顺槽流走，角标「台面干爽」。",
        "alt": "主图分镜 1"
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
        "heading": "标题草稿",
        "body": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳"
      },
      {
        "type": "section",
        "heading": "详情大纲",
        "body": "痛点钩子 → 防滑/易洗好处 → 材质一句 → 适用场景"
      },
      {
        "type": "list",
        "ordered": true,
        "items": [
          { "title": "洗完碗碟台面积水？一块垫解决沥干" },
          { "title": "防滑纹理 + 食品接触级硅胶，好清洗" },
          { "title": "卷折收纳，小户型厨房省空间" }
        ]
      },
      {
        "type": "note",
        "text": "假设：按国内电商、优先淘宝语气；未提供实物图。",
        "kind": "assumptions",
        "tone": "mute"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 策划分镜",
    "templateId": "domestic-generic-default",
    "driver": "台面干爽 + 防滑收纳，小户型厨房省心",
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
    "assumptions": "用户优先适配淘宝；按国内电商成交方向写策划"
  }
}
```

## 执行示例

仅在用户选择 `confirm_execute` 后输出（终态 `persistAs: sku`）。

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
        "body": "洗完碗碟台面积水？铺上一块就能沥干。\n防滑纹理托住碗盘不易滑；食品接触级硅胶，柔软好清洗。\n用完一卷，抽屉里也能塞下，小户型厨房更省事。"
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
        "body": "共 3 条：首图台面沥水特写；图2防滑纹理；图3卷折收纳。完整 prompt 见 artifact.framePrompts。"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 上架素材",
    "templateId": "domestic-generic-default",
    "driver": "台面干爽 + 防滑收纳，小户型厨房省心",
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
    "detailBody": "洗完碗碟台面积水？铺上一块就能沥干。\n防滑纹理托住碗盘不易滑；食品接触级硅胶，柔软好清洗。\n用完一卷，抽屉里也能塞下，小户型厨房更省事。",
    "displayNotes": "主图顺序：①首图沥水动态 ②防滑 ③卷折收纳。图内文案宜短。勿写杀菌医疗功效，勿编造月销。",
    "framePrompts": [
      {
        "prompt": "Product photo, silicone dish drying mat on kitchen counter, water droplets draining into grooves, clean bright kitchen, short Chinese text overlay 台面干爽, commercial e-commerce style, soft daylight",
        "negative": " cluttered props, watermark, medical claims"
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
    "picklistItemId": null,
    "assumptions": "用户确认策划后生成执行稿与生图 Prompt；主图由系统占位挂载"
  }
}
```

失败路径：不要输出本 JSON，只回人话（见 SKILL § Failures）。
