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

不要在对话里贴整包 `{view, artifact}`，也不要在对话里贴分文件全文。结算由服务端读 `final.json` 后再投影 / 落库。

无原文（链接失败且无粘贴）时**不要**写这些文件，只回人话。

## Contents

1. [对齐规则](#对齐规则)
2. [artifact（领域实体）](#artifact领域实体)
3. [view（视图实体）](#view视图实体)
4. [示例](#示例)
5. [质量对照](#质量对照)

## 对齐规则

| 规则 | 说明 |
|------|------|
| 原文 | `sourceBody` 必须真实存在：`apify` ← `fetch_xhs_note`；`paste` ← 用户粘贴 |
| `source` | 仅 `apify` 或 `paste` |
| `sourceUrl` | 可选；有链接时写入；`apify` 时优先用工具 `noteUrl` |
| 视图 | 上拆解要点（`structure`），下骨架+改写（`skeleton` / `rewrite`） |
| 禁止 | 空拆、假原文、把搜索 hits 冒充本篇正文 |

## artifact（领域实体）

写入 **`artifact.json` 的根对象**（文件里不要再包一层 `"artifact":`）。

| 字段 | 要求 |
|------|------|
| `title` | 与 `view.title` 相同 |
| `source` | `apify` 或 `paste` |
| `sourceUrl` | 可选 |
| `sourceTitle` | 原文标题（工具或粘贴） |
| `sourceBody` | 原文正文（必须非空） |
| `structure` | 结构拆解（可对照原文） |
| `skeleton` | 可复用骨架 |
| `rewrite` | 改写稿 |
| `targetProduct` | 可选；用户点名商品时填写 |
| `assumptions` | 可选 |

不要把 `blocks` 写进 `artifact`。

## view（视图实体）

写入 **`view.json` 的根对象**（文件里不要再包一层 `"view":`）。

| 字段 | 要求 |
|------|------|
| `version` | `1` |
| `title` | 给人看的中文标题 |
| `status` | 可选；成功可写 `ready` |
| `blocks` | 仅 `note` / `list` / `markdown` / `media` / `section` |

推荐一块 `markdown`：

| 小节 | 内容 |
|------|------|
| `## 拆解要点` | = `structure` |
| `## 骨架` | = `skeleton` |
| `## 改写稿` | = `rewrite` |

上半是拆解，下半是骨架+改写；不要把原文全文再贴一遍到 view（原文留在 artifact）。

## 示例

链接拉取成功路径。先写领域实体，再写视图实体，再合并。

### `artifact.json`（领域实体）

```json
{
  "title": "Mac Mini 拓展坞 · 爆文拆解改写",
  "source": "apify",
  "sourceUrl": "https://www.xiaohongshu.com/explore/example-note-1",
  "sourceTitle": "Mini 背后那一团线我终于藏住了",
  "sourceBody": "Mini 接到显示器后背后永远一串转接头。换成一块和机身差不多宽的拓展坞，HDMI、U盘、网线从底座走。",
  "structure": "痛点开场（接口不够线乱）→ 方案（拓展坞）→ 使用动作（插口/走线）→ 桌面收束。未用官方认证压人。",
  "skeleton": "场景痛点一句 → 方案物件一句 → 2 个可拍使用动作 → 收纳/体积收束 → 不承诺未提供数据",
  "rewrite": "Mini 接到显示器后，机身底下永远拖着一串转接头。我没有换主机，只加了一块 Mac Mini 拓展坞：HDMI、U盘、网线从底座走，桌上只剩电源和一根视频线。官方认证我没看到说明书就不写，买之前对一下自己的口。",
  "targetProduct": "Mac Mini 拓展坞"
}
```

### `view.json`（视图实体）

```json
{
  "version": 1,
  "title": "Mac Mini 拓展坞 · 爆文拆解改写",
  "status": "ready",
  "blocks": [
    {
      "type": "markdown",
      "text": "## 拆解要点\n痛点开场（接口不够线乱）→ 方案（拓展坞）→ 使用动作（插口/走线）→ 桌面收束。未用官方认证压人。\n\n## 骨架\n场景痛点一句 → 方案物件一句 → 2 个可拍使用动作 → 桌面收束 → 不承诺未提供认证\n\n## 改写稿\nMini 接到显示器后，机身底下永远拖着一串转接头。我没有换主机，只加了一块 Mac Mini 拓展坞：HDMI、U盘、网线从底座走，桌上只剩电源和一根视频线。官方认证我没看到说明书就不写，买之前对一下自己的口。"
    }
  ]
}
```

### `final.json`（合并，非手写第二套事实）

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

失败路径示例（仅人话，无 JSON）：

```text
没拉到这篇笔记正文。请换一条完整分享链接，或直接把原文粘贴过来再拆。
```

## 质量对照

### 好（可交付）

- **structure：** 点出「接口不够开场 + 藏线收束」，能在 `sourceBody` 找到对应句
- **source：** `apify` 且 body 与工具一致；或 `paste` 且能在用户消息中找到

### 坏（禁止）

- **空拆：** 无 body 仍写「爆款结构：黄金三秒」
- **假源：** `source=apify` 但正文是模型编的
- **万能壳：** structure 只有「开头/中/结尾」三字，无法对照原文
