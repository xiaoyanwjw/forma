# Output schema

`view` 给界面渲染；`artifact` 落库回显。两边同一事实，不是互相拷贝。

成功时只返回**一个** JSON 对象（可用 ` ```json ` 围栏），对象外不要闲聊。

```json
{ "view": { }, "artifact": { } }
```

## Contents

1. [对齐规则](#对齐规则)
2. [文案风格（对齐国内电商）](#文案风格对齐国内电商)
3. [view](#view)
4. [artifact](#artifact)
5. [示例](#示例)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | `view` 与 `artifact` 同一商品事实；**只一套文案字段** |
| 主图 | 业务真相为系统挂载后的 `mediaObjectId`；模型侧可留空 `mediaObjectIds`，但必须写清 `heroPlan` |
| 风格底 | `templateId` 固定 `domestic-generic-default` |
| 口吻 | 国内电商成交文案（淘宝/天猫系可搜、可转化）；不是摄影 brief、不是合规备忘录 |
| 假设 | 信息不足时写 `assumptions`，勿假装用户已提供细节 |
| 禁止 | 伪造销量/榜单/资质/评价；**不要**输出 `platformCopies` |

## 文案风格（对齐国内电商）

借鉴常见电商 Listing / 主图文案 Skill 的结构，压缩进四字段：

| 字段 | 写法（要） | 禁止（不要） |
|------|------------|--------------|
| `heroPlan` | 首图**视觉任务**：一眼认出商品 + 1 个核心购买理由；可写建议图内短文案（≤6 字/行、≤3 行）；可补 2～3 张后续图任务（卖点/场景） | 「白底居中、避免杂乱道具与水印」这类空说明书；不要写生图 Prompt |
| `detailTitle` | **可搜索宝贝标题**：品类词 + 2～4 个属性/场景卖点（如 防滑 / 易清洗 / 可折叠）；约 20～40 字，读起来像淘宝标题 | 括号堆砌参数；「（防滑·可折叠·多色）」策划腔 |
| `detailBody` | **商详卖点段**：先场景痛点钩子 → 核心好处 2～3 条 → 材质/用法一句 → 适用场景；口语、好扫读；可用换行 | 纯参数表；「上架前请按实物核对」公文腔当正文主体 |
| `displayNotes` | **给店主的落地说明**：主图顺序（如 首图卖点→使用场景→细节）+ 图内文案建议 + 合规一句 | 只列「勿编造销量」而无主图任务 |

利益翻译：每个卖点尽量写成 `功能 → 用户好处`（例：硅胶柔软 → 碗碟不易滑、可卷折收纳）。

用户点名「优先淘宝」：语气更偏搜索标题与商详卖点；仍只输出这一套公共字段。

## view

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | 给人看的中文标题（与 `artifact.title` 一致） |
| `status` | 可选；成功可写 `ready` |
| `blocks` | 仅 `note` / `list` / `markdown` / `media` / `section` |

Listing 常用块：

1. `media`（`role: hero`）：`placeholder` / `alt` 对应 `heroPlan` 要点；`mediaObjectId` 可省略
2. `section`「详情标题」→ `detailTitle`
3. `section`「详情正文」→ `detailBody`
4. `section`「展示说明」→ `displayNotes`（可用 `tone: mute`）

## artifact

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `templateId` | `domestic-generic-default` |
| `heroPlan` / `detailTitle` / `detailBody` / `displayNotes` | 非空；按上文风格 |
| `mediaObjectIds` | 可先 `[]`；系统挂载后至少 1 个真实 id |
| `picklistItemId` | 可选 |
| `assumptions` | 可选 |

不要把 `blocks` 写进 `artifact`。不要输出 `platformCopies` / `preferredPlatform`。

## 示例

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
        "placeholder": "首图：沥水垫铺满台面特写，水珠顺槽流走；角标「台面干爽」。续图：碗碟不滑 / 一卷收纳。",
        "alt": "主图方案"
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
        "body": "主图顺序：①首图沥水动态/干爽卖点 ②碗碟防滑 ③卷折收纳。图内文案宜短（如「台面干爽」「一卷收纳」）。勿写杀菌医疗功效，勿编造月销。",
        "tone": "mute"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 上架素材",
    "templateId": "domestic-generic-default",
    "heroPlan": "首图：沥水垫铺满台面特写，水珠顺槽流走；角标「台面干爽」。续图：碗碟不滑 / 一卷收纳。",
    "detailTitle": "厨房硅胶沥水垫 防滑易清洗 可折叠收纳 多色可选",
    "detailBody": "洗完碗碟台面积水？铺上一块就能沥干。\n防滑纹理托住碗盘不易滑；食品接触级硅胶，柔软好清洗。\n用完一卷，抽屉里也能塞下，小户型厨房更省事。",
    "displayNotes": "主图顺序：①首图沥水动态/干爽卖点 ②碗碟防滑 ③卷折收纳。图内文案宜短（如「台面干爽」「一卷收纳」）。勿写杀菌医疗功效，勿编造月销。",
    "mediaObjectIds": [],
    "picklistItemId": null,
    "assumptions": "用户优先适配淘宝；按国内电商成交文案写一套公共字段"
  }
}
```

失败路径：不要输出本 JSON，只回人话（见 SKILL § Failures）。
