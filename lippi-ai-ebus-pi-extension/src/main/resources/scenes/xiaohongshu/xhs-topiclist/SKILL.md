---
name: xhs-topiclist
description: >-
  经配置的小红书笔记检索（如 Mock / Apify）用 search_xhs_note 产出 8–12 条带 tp-n item id 的种草选题清单（JSON：view + artifact）。
  在用户提到选题、发什么、种草方向、内容日历时使用。
allowed-tools: read_skill search_xhs_note write_file read_file bash
metadata:
  output:
    billing: true
    persistAs: xhs_topiclist
    requiresView: true
---

# 选题清单

帮卖家做**小红书种草选题**：从一次检索返回的候选里写出可发清单，不是复读工具标题。检索成功时尽量挂用户能点开的笔记原链；检索失败时允许模型补选题，但禁止假链。

## When to use

- **用：** 选题、发什么、种草方向、内容角度、可发清单
- **不用：** 写完整笔记正文 / 标题备选 / 配图提示 → `xhs-note`
- **不用：** 拆解爆文结构、按骨架改写 → `xhs-break`

## Workflow

1. **提炼搜索词。** 信息不够时把假设写入 `artifact.assumptions`，仍先搜，勿先追问。收成**一个**最稳 `query`。
2. **只调用 `search_xhs_note` 一次。** 扩词、合法校验与模型重排在服务端完成；你只发这一遍。禁止并行、禁止换词连搜。禁止在 skill 内再做检索式筛选或二次重排。
3. **从返回候选写清单，或走 fallback。**
   - **工具成功且有 hits：** `artifact.source` = `apify`。在候选中选出 **8–12** 条写入 view/artifact，并写质量字段（见 Quality）。有 hits 时勿臆造工具未返回的笔记。`sourceNoteUrl` 仅允许来自该次工具 hits 的 `noteUrl`（绝对 `https:`）。
   - **工具失败 / 空 hits：** 仍须产出 **8–12** 条合格选题；`artifact.source` = `model_fallback`；**禁止**编造笔记链接（不要写 `sourceNoteUrl`，list 不要写 `href`）。
4. **分配 id。** 按最终清单顺序为每条赋 `tp-1`…`tp-n`；后续领域实体与视图实体的 `id` **同序同值**。
5. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**，再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact 对象）。可用 `read_file` 自检。
6. **构造视图实体。** 按同一批 `tp-n` 与顺序拼出完整 **view**（mute `note` + 有序 `list`），再 `write_file` → `view.json`（相对 run 根，**仅** view 对象）。可用 `read_file` 自检。
7. **拼出终态文件。** 用 `write_file` 把 view 与 artifact 合并写入 `final.json`（相对 run 根）。内容是一个 JSON 对象：`view` 取 `view.json` 的对象，`artifact` 取 `artifact.json` 的对象。环境里若已有 `bash` / `python3` 可以用它们拼文件，但不要依赖 `python3`；没有它们时仍用 `write_file` 写 `final.json`。
8. **终稿只输出指针。** 对话里**仅**一个 JSON 对象（无围栏、无其它文字）：`{"output":"final.json"}`。禁止在对话里粘贴整包 `{view, artifact}`。
9. **过 Verification。** 全部勾上再发指针；任一不满足 → Fail 或改盘后重拼。

## Tool: search_xhs_note

笔记链接来自本工具返回的 `noteUrl`；实际检索实现由服务端配置决定（如 `mock` 或 `apify`）。主 agent 只见 `query` + `pageSize`。

| 参数 | 说明 |
|------|------|
| `query` | 必填；本轮只发这一次调用 |
| `pageSize` | 返回候选条数上限（服务端重排后）；建议 `12`～`20` |

空结果或工具错误 → **不要再调** `search_xhs_note`；改走 `source=model_fallback`（与选品 Fail 不同）。

## Quality

在**工具返回的候选**中选出条目并撰写下列字段（fallback 时按用户品类/人群自拟选题，仍须满足质量门槛）。不必再做一次「检索式筛选/排名」——排序与合法链路由服务端处理；你负责钩子 / 角度 / 优先发。

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照条目)）：

- **优先发：** 答清「为何先发它 vs 清单下一条」；禁「流量大」「好种草」
- **angle：** 具体到场景/人群/表达切口；全清单 **≥3 个不同** 角度或人群；禁「日常」「种草」空泛三连
- **hook：** 开场钩子一句可感知；禁与 title 同义反复
- **whyFirst / risk：** 各一句可行动理由与风险；禁套话
- **反凑数：** 同质微差最多 1 条代表
- **链接：** `source=apify` 时有 hits 勿臆造未返回的笔记；`source=model_fallback` 无假链

## Output

成功终态：**盘上** `final.json`（`view` + `artifact` 信封）+ **对话**指针 `{"output":"final.json"}`。完整字段、对齐、文件示例与好坏例 → [output.md](references/output.md)。

速记：

- 每条 `id` = `tp-{n}`；list 与 artifact **同 id 同序**
- `artifact.source` = `apify` 或 `model_fallback`
- `sourceNoteUrl`（及 list `href`）仅检索成功且 URL 来自工具 hits；fallback **不写链接**
- disclaimer / note 必须包含字面量：`非实时平台全站行情`
- 推荐整句：`选题基于配置的笔记检索抽样与服务端排序，非实时平台全站行情。有链接时可打开笔记页核对。`
- 两边均为 8–12 条；`view.version` = `1`
- `view.title` 与 `artifact.title`：本轮生成的中文清单标题（同一文案）

## Verification

输出前逐项自检（全部通过才允许发指针）：

- [ ] 本轮恰好 **1** 次 `search_xhs_note`（成功或失败都只这一次）
- [ ] 已写 `artifact.json`、`view.json`，且已用 `write_file` 写出 **`final.json`**
- [ ] 终稿对话**仅** `{"output":"final.json"}`；**未**在对话里贴整包大 JSON
- [ ] `final.json` 内 `artifact.items` 与 `view` list 均为 **8–12** 条，条数一致、顺序对应
- [ ] 每条 `id` 非空，格式 `tp-n`（从 1 顺序）；list 与 artifact **同 id 同序**
- [ ] 至少 **3** 个不同 `angle`（或人群切口），且无空泛「日常」「种草」三连凑数
- [ ] 恰好 **1–2** 条 `artifact.items[].title` 以 `【优先发】` 开头；对应 list `badge: "优先试"`（list 标题不加该前缀）
- [ ] list 每行带展示用 `label`（组件不猜中文）
- [ ] 优先发条目含可行动「为何先发」理由（hook/angle/whyFirst 至少一处说清相对下一条的优势）
- [ ] `source=apify` 时每条 `sourceNoteUrl` / `href` 均来自工具 `noteUrl`，绝对 `https:`，无编造
- [ ] `source=model_fallback` 时无 `sourceNoteUrl`、无 list `href`、无假链
- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `blocks` 仅白名单类型；含 mute `note` 免责声明 + 有序 `list`
- [ ] disclaimer / note 含字面量 `非实时平台全站行情`
- [ ] 未编造官方热榜 / 全站实时推广池 / 实时互动榜
- [ ] 成功路径除指针外无闲聊；失败路径无人话以外的假 JSON

## Failures

下列情况**只回一句人话原因**，不要输出 JSON 或指针：

- 无法理解用户要做选题（完全离题闲聊）
- 写出的 8–12 条仍无法满足 Quality（同质凑数、无 3 个角度）且改盘后仍不合格

工具失败 / 空 hits **不是** Fail：走 `model_fallback`。

## Boundaries

- 不宣称官方全站实时热榜或官方推广池；不编造链接，不编造全站实时指标
- 不二次 / 并行调用 `search_xhs_note`；不在 skill 内扩词或二次重排
- 不把本技能写成完整笔记或爆文拆解
