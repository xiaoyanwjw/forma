# Output schema

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `template/view.mustache`；**默认 `format: html`**）。**勿**手写 `content`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"view.json"}
```

不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 **`view.json`**，并与同目录 **`artifact.json`** 对齐落库。

`artifact` = 领域实体（成稿事实）；`view` = 视图实体（界面渲染）。两边同一事实。

本 Skill **一次成稿**：无 `plan/` / `exec/`，无 `ask_human`。

## Contents

1. [对齐规则](#对齐规则)
2. [artifact（领域实体）](#artifact领域实体)
3. [view（视图实体）](#view视图实体)
4. [示例](#示例)
5. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 同一事实 | `view.content` HTML 各小节 = `artifact` 对应字段 |
| 交接 | 输入含「来源选题条目」或 `tp-n` 时，`topicItemId` **必填**且与输入一致 |
| 口述 | 无选题交接时可省略 `topicItemId` |
| 假设 | 信息不足或有交接时写 `assumptions`（含钩子/角度/骨架摘要，勿编造功效） |
| 禁止 | 伪造认证、实验室数据、销量、官方热榜 |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同的中文成稿标题 |
| `topicItemId` | 可选；有选题交接时必填（如 `tp-2`） |
| `productName` | 种草对象（商品或品类） |
| `titleOptions` | **3–5** 条可发标题 |
| `body` | 笔记正文（真人分享；可含换行） |
| `tags` | **5–10** 个标签（不要自带 `#` 或与带 `#` 的写法保持一致即可，全清单统一） |
| `imageHints` | **3–5** 条配图/分镜拍摄提示 |
| `assumptions` | 可选 |

不要把 `blocks` / `format` / `content` 写进 `artifact`。

## view（视图实体）

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | 给人看的中文标题 |
| `format` | **`html`** |
| `content` | 由 [view.mustache](../template/view.mustache) 渲染的 HTML 字符串 |

`content` 固定小节（模板与 `artifact` 同一事实）：

| 小节 | 内容 |
|------|------|
| 盾牌 deck | 状态 · 可改写；可选对象 = `productName` |
| `<h2>标题备选</h2>` | 有序列表 = `titleOptions` |
| `<h2>正文</h2>` | = `body`（按行分段） |
| `<h2>标签</h2>` | = `tags`（胶囊） |
| `<h2>配图提示</h2>` | 有序列表 = `imageHints` |
| `<h2>假设</h2>` | 可选 = `assumptions` |

**禁止** 再输出 v1 `blocks` JSON 视图。

### HTML 与 JSON

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内双引号用实体转义。

## 示例

黄金路径：用户消息含 `来源选题条目：tp-1`。先写领域实体，再写视图实体，再合并。

### `artifact.json`（领域实体）

```json
{
  "title": "Mac Mini 拓展坞 · 种草笔记",
  "topicItemId": "tp-1",
  "productName": "Mac Mini 拓展坞",
  "titleOptions": [
    "Mini 背后那一团线，我换成拓展坞就不想拆了",
    "接显示器总缺口？先看这块坞怎么走线",
    "桌面只留一根视频线：Mini 底座拓展"
  ],
  "body": "Mini 接到显示器后，机身底下永远拖着一串转接头。后来换成一块和机身差不多宽的拓展坞，HDMI、U盘、网线都从底座走，桌上只剩电源和一根视频线。\n\n没拿过苹果官方认证材料，就不写兼容保证；买之前对一下自己的口：HDMI 版本、是不是要 2.5G 网口。\n\n如果你也是居家办公把 Mini 接到外接屏，可以先看坞的宽度和走线孔，别只看主图颜色。",
  "tags": ["Mac Mini", "桌搭", "拓展坞", "居家办公", "理线", "显示器"],
  "imageHints": [
    "首图：线乱桌面 vs 坞藏线后，左右对比，字幕「桌面清了」",
    "图2：HDMI / USB / 网口特写，手插上 U 盘",
    "图3：机身下走线孔，线从底座出去"
  ],
  "assumptions": "交接 tp-1；钩子=接口不够线乱；角度=居家办公 Mini 桌搭；未提供官方认证故不写"
}
```

### `view.json`（`render_view` 产出）

对上例 `artifact.json` 调用 `render_view` 后，`view.json` 含 v2 字段；`format` 为 **`html`**，`content` 由 [view.mustache](../template/view.mustache) 填充。

**对话终稿指针：**

```json
{"output":"view.json"}
```

失败路径：不要输出指针或本 JSON，只回人话（见 SKILL § Failures）。

## 质量对照

### 好（可交付）

- **正文：** 有具体使用场景与保留意见（「我没测过除菌数据」）
- **配图：** 「湿台面 vs 垫上碗碟左右对比」可执行
- **交接：** `topicItemId` = `tp-1` 与输入一致

### 坏（禁止）

- **正文：** 「食品级医用除菌率 99.9%，全网热销第一」（用户未提供）
- **配图：** 「拍几张好看的生活图」
- **流程：** 先让用户选标题再写正文，或调用搜索工具
