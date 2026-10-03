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

`artifact` = 领域实体（选题事实）；`view` = 视图实体（界面渲染）。两边同一事实，不是互相拷贝。

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
| id 一致 | 每条手递 prompt 含 `（条目 tp-{n}）`，与 `artifact.items[].id` 同序同值 |
| 链接一致 | `source=apify` 时 `sourceNoteUrl` = 工具 `noteUrl`；prompt「原笔记」= 同一 URL；仅绝对 `https:`；禁止编造 |
| fallback | `source=model_fallback` 时不写 `sourceNoteUrl`，prompt **不写**「原笔记」行 |
| 条数 | 成功时 `artifact.items` 与 HTML 有序列表均为 **8–12**（下方示例为简洁只写 1 条） |
| 免责声明 | 非空，且必须包含字面量 **`非实时平台全站行情`**。推荐整句：`选题基于配置的笔记检索抽样与服务端排序，非实时平台全站行情。有链接时可打开笔记页核对。` |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同的中文清单标题 |
| `disclaimer` | 同 view 正文免责声明合同 |
| `assumptions` | 可选；用户信息不足时的搜索假设 |
| `source` | 必填；`apify` 或 `model_fallback` |
| `query` | 本轮实际发给 `search_xhs_note` 的那一个 query |
| `items` | 成功时长度 **8–12** |

### items[]

| 字段 | 要求 |
|------|------|
| `id` | 必填；本清单内唯一；格式 `tp-{n}` 从 1 顺序 |
| `title` | 全清单恰好 **1–2** 条以 `【优先发】` 开头 |
| `hook` | 开场钩子 |
| `angle` | 角度 / 人群切口；全清单 **≥3 个不同** |
| `whyFirst` | 为何先发（相对清单下一条） |
| `risk` | 本条风险（夸大功效、同质、季节等） |
| `sourceNoteUrl` | 可选；仅 `source=apify` 且 URL 来自工具 hits |

不要把 `blocks` / `format` / `content` 写进 `artifact`。

## view（视图实体）

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`** |
| `title` | **给人看的中文标题**（建议与 `artifact.title` 一致）；勿写裸 key |
| `format` | **`html`** |
| `content` | 完整 HTML 字符串；根节点建议 `<article class="markdown-body">` |

正文结构建议：

1. `<h1>` = 清单标题
2. 免责声明段落（含 `非实时平台全站行情`）
3. `<ol>` 有序列表：每条选题一个 `<li>`
4. 每条 `<li>` 内展示：选题名（**不加** `【优先发】`）、钩子 / 角度 / 优先理由 / 风险；优先发条目可加「优先发」标记；有真链时可附可点击链接
5. 每条 `<li>` 末尾 **必须** 含手递按钮（见下一节）

**禁止** 再输出 v1 `blocks` / `list` JSON 视图。

### HTML 与转义

- 写入真实 `view.json` 时，`content` 是 JSON 字符串；属性内的 ASCII 双引号用 HTML 实体（如 `&quot;`）转义。
- `data-adam-prompt` 内多行 prompt 在 JSON 里用 `\n` 表示换行。

验收：有 `https:` 原笔记时 prompt 含「原笔记」行；点击由前端开跑 `xhs-note`。

## 手递按钮与 prompt 合同

每条候选 **一条** 按钮：

```html
<button
  type="button"
  data-adam-action="handoff"
  data-adam-skill-id="xhs-note"
  data-adam-prompt="…"
>写成笔记</button>
```

`data-adam-prompt` 正文须与下列模板一致（`{title}` 为去掉 `【优先发】` 后的选题名；可选行仅在有值时追加；`原笔记` 仅在有合法 `https:` 链接时追加）：

```text
请根据选题「{title}」（条目 {id}）写一篇小红书种草笔记，语气像真人分享。
角度：{angle?}
钩子：{hook?}
原笔记：{https?}
```

缺 `title` 或缺 `id` → **不要** 为该条写按钮。

## 示例

各 1 条示意（交付时均为 8–12）。先写领域实体，再写视图实体，最后合并。

### `artifact.json`（领域实体）

```json
{
  "title": "Mac Mini 桌搭 · 居家办公种草选题清单",
  "disclaimer": "选题基于配置的笔记检索抽样与服务端排序，非实时平台全站行情。有链接时可打开笔记页核对。",
  "assumptions": "面向居家办公 Mac Mini 桌搭；未指定单品时按拓展坞/理线默认",
  "source": "apify",
  "query": "Mac Mini 桌搭",
  "items": [
    {
      "id": "tp-1",
      "title": "【优先发】Mini 背后一串转接头：一块拓展坞收干净",
      "hook": "Mini 接到显示器后，背后永远拖着一串转接头？",
      "angle": "居家办公 Mac Mini 桌搭",
      "whyFirst": "痛点具体、接口对比好拍，比同清单「全屋桌面改造」更好当天拍当天发",
      "risk": "勿宣称食品级认证或除菌数据，除非用户已提供",
      "sourceNoteUrl": "https://www.xiaohongshu.com/explore/example-note-1"
    }
  ]
}
```

### `view.json`（视图实体）

```json
{
  "version": 2,
  "title": "Mac Mini 桌搭 · 居家办公种草选题清单",
  "format": "html",
  "content": "<article class=\"markdown-body\"><h1>Mac Mini 桌搭 · 居家办公种草选题清单</h1><p class=\"cv-note\">选题基于配置的笔记检索抽样与服务端排序，非实时平台全站行情。有链接时可打开笔记页核对。</p><ol><li><p><span class=\"priority-tag\">优先发</span> <strong>Mini 背后一串转接头：一块拓展坞收干净</strong></p><p>视角：Mini 接到显示器后，背后永远拖着一串转接头？ · 切入：居家办公 Mac Mini 桌搭</p><p>优先：痛点具体、接口对比好拍，比全屋改造更好当天发 · 风险：勿宣称未提供的认证或除菌数据</p><p><button type=\"button\" data-adam-action=\"handoff\" data-adam-skill-id=\"xhs-note\" data-adam-prompt=\"请根据选题「Mini 背后一串转接头：一块拓展坞收干净」（条目 tp-1）写一篇小红书种草笔记，语气像真人分享。\n角度：居家办公 Mac Mini 桌搭\n钩子：Mini 接到显示器后，背后永远拖着一串转接头？\n原笔记：https://www.xiaohongshu.com/explore/example-note-1\">写成笔记</button></p></li></ol></article>"
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

各 1 条；对照「优先发理由 / angle / 假链」。

### 好条目（可交付）

- **优先发：**「Mini 接显示器接口不够高频、接口对比好拍，比同清单全屋桌面改造更容易当天发」
- **angle：** `居家办公 Mac Mini 桌搭`（场景具体）
- **链接：** `apify` 用工具 `noteUrl`；`model_fallback` 不写 URL

### 坏条目（禁止）

- **优先发：**「流量大、好种草」（套话，未对比下一条）
- **angle：** `日常` / `种草`（空泛凑数）
- **链接：** fallback 仍写 `https://www.xiaohongshu.com/explore/fake-id`
