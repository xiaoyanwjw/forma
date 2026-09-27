# Output schema

`view` 给界面渲染；`artifact` 落库回显。两边同一事实，不是互相拷贝。

成功时只返回**一个** JSON 对象（可用 ` ```json ` 围栏），对象外不要闲聊。

```json
{ "view": { }, "artifact": { } }
```

## Contents

1. [对齐规则](#对齐规则)
2. [view](#view)
3. [artifact](#artifact)
4. [示例](#示例)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | view 的详情/展示 section 与 artifact 对应字段同义 |
| 主图 | 业务真相为系统挂载后的 `mediaObjectId`；模型侧可留空 `mediaObjectIds`，但必须写清 `heroPlan`（主图方案文案） |
| 风格 | `templateId` 固定 `domestic-generic-default` |
| 假设 | 信息不足时写 `assumptions`，勿假装用户已提供细节 |

## view

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | 给人看的中文标题（与 `artifact.title` 一致） |
| `status` | 可选；成功可写 `ready` |
| `blocks` | 仅 `note` / `list` / `markdown` / `media` / `section` |

Listing 常用块：

1. `media`（`role: hero`）：`placeholder` / `alt` 写主图方案要点；`mediaObjectId` 可省略（系统填充）
2. `section`「详情标题」→ body = `detailTitle`
3. `section`「详情正文」→ body = `detailBody`
4. `section`「展示说明」→ body = `displayNotes`（可用 `tone: mute`）

## artifact

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `templateId` | `domestic-generic-default` |
| `heroPlan` | 非空；主图方案说明（构图/主体/卖点/禁忌） |
| `detailTitle` | 非空 |
| `detailBody` | 非空 |
| `displayNotes` | 非空 |
| `mediaObjectIds` | 可先 `[]`；系统挂载后至少 1 个真实 id |
| `picklistItemId` | 可选；有关联选品条目时填写 |
| `assumptions` | 可选 |

不要把 `blocks` 写进 `artifact`。

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
        "placeholder": "白底俯拍沥水垫；左下角「多色可选」角标；右侧厚度对比小图",
        "alt": "主图方案"
      },
      {
        "type": "section",
        "heading": "详情标题",
        "body": "厨房硅胶沥水垫 多色防滑易清洗"
      },
      {
        "type": "section",
        "heading": "详情正文",
        "body": "台面水渍不再积；食品级硅胶触感软、折叠收纳；多色可选，适合租房与小户型厨房。"
      },
      {
        "type": "section",
        "heading": "展示说明",
        "body": "主图突出颜色与厚度对比；详情先痛点后材质；勿宣称医疗/杀菌功效。",
        "tone": "mute"
      }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 上架素材",
    "templateId": "domestic-generic-default",
    "heroPlan": "白底俯拍沥水垫；左下角「多色可选」角标；右侧厚度对比小图；避免杂乱道具。",
    "detailTitle": "厨房硅胶沥水垫 多色防滑易清洗",
    "detailBody": "台面水渍不再积；食品级硅胶触感软、折叠收纳；多色可选，适合租房与小户型厨房。",
    "displayNotes": "主图突出颜色与厚度对比；详情先痛点后材质；勿宣称医疗/杀菌功效。",
    "mediaObjectIds": [],
    "picklistItemId": null,
    "assumptions": "未指定平台时按国内淘宝通用详情结构默认"
  }
}
```

失败路径：不要输出本 JSON，只回人话（见 SKILL § Failures）。
