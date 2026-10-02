# Computer GitView（统一 README 画布）Design

**Date:** 2026-10-03  
**Status:** accepted  
**Decision:** Computer 预览区统一 GitHub README 气质；通用画布 `GitView`；上架分镜投影进 `gitDoc.ts`（无 `Listing*` 方法名）

**Related:**  
- [`2026-09-26-computer-view-protocol-design.md`](./2026-09-26-computer-view-protocol-design.md)  
- [`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md)  
- [`2026-09-27-listing-storyboard-hitl-design.md`](./2026-09-27-listing-storyboard-hitl-design.md)

---

## Goal

右侧 Computer **所有场景成果**（选品 / 上架策划 / 上架执行 / 小红书选题·笔记·拆解 / History）统一为 GitHub README 风格：

- 文件条（`*.md`）
- 标题下划线、有序列表、code block、弱提示
- 行内交互按钮保留（做上架素材 / 写成笔记等）

## Non-goals

- 不改 SSE、skill、积分、`ComputerBlock` 协议字段
- 不把全部内容先拼成一大段 Markdown 再解析（会丢掉按钮）
- 不做真实 GitHub API / 仓库联动
- 不引入 `Listing*` 组件或方法名

---

## Decisions locked

| 项 | 选择 |
|----|------|
| 统一深度 | **B**：壳 + 内容都像 README |
| 文件名 | **C**：默认 `document.title` → slug`.md`；场景可传 `fileName` 覆盖 |
| 架构 | **GitView 通用画布**；`ComputerRenderer` 把 blocks 画成 README 元素 |
| 上架投影命名 | 方案 **3**（动词风，无 Listing 前缀） |

---

## Naming

| 角色 | 名字 |
|------|------|
| README 画布组件 | `GitView.vue` |
| 上架分镜/文案投影模块 | `gitDoc.ts` |
| 投影结果类型 | `StoryboardDoc` |
| blocks → 文档字段 | `toStoryboardDoc(blocks)` |
| 是否走分镜文档布局 | `hasStoryboardDoc(doc)` |
| 分镜 zip（已有） | `buildStoryboardBeats` / `parseFramePromptEntries` |
| 文档入口 | 仍为 `ComputerRenderer.vue`（场景不直接碰 GitView） |

**删除：**

- `ListingReadmePreview.vue`
- `listingPreview.ts`（迁入 `gitDoc.ts` 并改名）
- 逐步废弃 `presentation?: 'readme' | 'blocks'`（统一 GitView 后不再需要双皮）

**不采用：** `projectGitDoc` / `isGitDoc`（选品/笔记也进 GitView，语义过宽）。

---

## Architecture

```text
Scene (ecommerce / xhs / history)
  → ComputerRenderer(document, fileName?, itemAction…)
       → GitView(fileName, meta?)
            └── body: README 元素
                 ← block 映射（renderer）
                 ← 若 hasStoryboardDoc：toStoryboardDoc 填分镜区
```

### `GitView.vue`

**负责：**

- 文件条 UI（name · 可选 meta）
- `.git-md` 排版 token（h1/h2/h3、ol、pre、p、blockquote、lead）
- 默认 `fileName`：由 `title` slug 生成；props.`fileName` 优先

**不负责：**

- 解析 `ComputerBlock`
- 业务按钮逻辑

### `ComputerRenderer.vue`

**负责：**

- 包一层 `GitView`
- 将每种 block 映射为 README 元素（见下表）
- list 行内 `item-action` 等交互
- 若 `hasStoryboardDoc(document)`：用 `toStoryboardDoc` 渲染「主图分镜 / 详情文案」区（替代旧 ListingReadme 专用皮）

### `gitDoc.ts`

纯函数，仅服务「分镜 + 详情文案」双轨文档（上架执行稿）：

```ts
export type StoryboardDoc = {
  detailTitle: string
  detailBody: string
  displayNotes: string
  frames: string[]
  framePromptsSummary: string
}

export function toStoryboardDoc(blocks: ComputerBlock[]): StoryboardDoc
export function hasStoryboardDoc(doc: { title?: string; blocks: ComputerBlock[] } | null | undefined): boolean
// + LISTING_SECTION_HEADINGS 常量（或改名为 STORYBOARD_SECTION_HEADINGS）
// + buildStoryboardBeats / parseFramePromptEntries
```

`hasStoryboardDoc` 判定逻辑对齐现网 `isListingReadmeDocument`（title `listingPreview` / `上架|listing` / 详情标题·正文 section；有 picklist list 则否），仅改名不改语义。

---

## Block → README 元素

| `ComputerBlock` | GitView 内呈现 |
|-----------------|----------------|
| `markdown` | MarkdownView + git-md 样式 |
| `list` | `ol`/`ul`：标题、价、标签；**按钮留在行内** |
| `section` | `h2` + 段落（非分镜文档时） |
| `media` | 轻 figure / 方案说明（避免厚卡片） |
| `note` | 弱提示 / blockquote |
| 分镜文档（`hasStoryboardDoc`） | `h2 主图分镜` + `ol` + 每条下 `pre` Prompt；再 `h2 详情文案` |

---

## File name（策略 C）

```ts
// GitView / ComputerRenderer props
fileName?: string

// 解析顺序
1. props.fileName（场景传入，如 picklist.md / note.md）
2. slug(document.title) + '.md'
3. 兜底 'document.md'
```

场景可逐步补显式名；History 可不传。

---

## Migration（实现顺序）

1. 新增 `GitView.vue`（从现有 `.gh-readme` 样式提炼）  
2. `listingPreview.ts` → `gitDoc.ts`，符号改为 `StoryboardDoc` / `toStoryboardDoc` / `hasStoryboardDoc`  
3. `ComputerRenderer` 一律包 `GitView`；blocks 路径改用 git-md 类名  
4. 删除 `ListingReadmePreview.vue`；分镜区改由 `toStoryboardDoc` + GitView 元素组成  
5. 场景可选传 `fileName`；去掉 `presentation` 分叉（或默认 git、短暂兼容）  
6. 更新测试：正向断言 `.git-view` / `.git-md`；选品/笔记/上架/History 同壳  

---

## Test focus

- 选品 list：GitView 壳 + 有序列表 + 行内「做上架素材」  
- 上架策划 markdown：GitView 壳 + md 正文（`hasStoryboardDoc` false）  
- 上架执行：`toStoryboardDoc` 分镜 + Prompt code block  
- 小红书笔记/选题：同壳，无分镜投影  
- `fileName` 覆盖 vs title slug 默认  
- 无回归：平台皮肤 / `ListingReadmePreview` 选择器不再出现  

---

## Open follow-ups（非本设计阻塞）

- section heading 常量是否从 `LISTING_SECTION_*` 改名为 `STORYBOARD_SECTION_*`（实现时一并改）  
- `03-fe.md` 补充 Computer / GitView 配方一句  

---

## Approval

确认本设计后将 Status → **accepted**，再写 implementation plan 并落地。
