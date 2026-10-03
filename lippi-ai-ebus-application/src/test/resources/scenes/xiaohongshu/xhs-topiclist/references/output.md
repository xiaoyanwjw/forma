# Output schema

## 交付方式

1. **工作区文件：**
   - 领域实体 → `write_file` → `artifact.json`（**仅** artifact 对象，见下方示例）
   - 视图 → 调用 **`render_view`**（默认读 `artifact.json`，写 `view.json`，模板 `references/view.mustache`）。**勿**手写 HTML `content`。
2. **对话终稿（指针）：** 成功时**只**输出一个 JSON 对象，无围栏、无其它文字：

```json
{"output":"view.json"}
```

不要在对话里贴 `artifact.json` / `view.json` 全文。结算由服务端读 **`view.json`**，并与同目录 **`artifact.json`** 对齐落库。

`artifact` = 领域实体（选题事实）；`view` = 由模板渲染的界面文档。条目顺序与 id 须一致。

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

**`view.json`** 由 `render_view` 写出（根对象，无 `"view":` 包裹）。

| 字段 | 要求 |
|------|------|
| `version` | **`2`**（工具默认） |
| `title` | 来自 `artifact.title` |
| `format` | **`html`** |
| `content` | Mustache 渲染 [view.mustache](view.mustache) 的结果 |

模板数据根 = 整棵 **artifact**；渲染前工具在内存为每条 `items[]` 注入（**不写回** `artifact.json`）：

| 注入字段 | 含义 |
|----------|------|
| `displayTitle` | `title` 去掉 `【优先发】` 前缀 |
| `handoffPrompt` | 符合下方手递合同的多行 prompt；缺 `title`/`id` 或无链规则时不注入 |

**禁止** v1 `blocks` / `list` JSON 视图；**禁止**模型手写 `content`。

验收：有 `https:` 原笔记时 `handoffPrompt` 含「原笔记」行；HTML 按钮由模板输出，点击开跑 `xhs-note`。

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

各 1 条示意（交付时均为 8–12）。先写 `artifact.json`，再 `render_view`。

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

### `view.json`（`render_view` 产出）

对上例 `artifact.json` 调用 `render_view` 后，`view.json` 含 v2 字段；`content` 由 [view.mustache](view.mustache) 填充，且含 `data-adam-skill-id="xhs-note"` 手递按钮。

**对话终稿指针：**

```json
{"output":"view.json"}
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
