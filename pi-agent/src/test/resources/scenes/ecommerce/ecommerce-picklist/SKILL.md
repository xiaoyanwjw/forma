---
name: ecommerce-picklist
description: >-
  经配置的商品检索（如 Mock / Apify 淘宝搜）用 search_sku 产出 8–12 条带原链与 pl-n item id 的可测款选品清单（artifact + render_view → view.json）。
  在用户提到选品、卖什么、候选清单、测款方向时使用。
allowed-tools: read_skill search_sku write_file read_file render_view
metadata:
  output:
    billing: true
    persistAs: picklist
    requiresView: true
    viewPath: view.json
    artifactPath: artifact.json
---

# 选品清单

帮卖家做**测款选品**：从一次检索返回的候选里写出可行动清单，不是复读工具标题。每条必须带用户能点开的商品原链。

## When to use

- **用：** 选品、卖什么、候选清单、测款方向
- **不用：** 上架素材、主图分镜、详情文案 → `ecommerce-skulist`

## Workflow

1. **提炼搜索词。** 信息不够时把假设写入 `artifact.assumptions`，仍先搜，勿先追问。收成**一个**最稳 `query`。
2. **只调用 `search_sku` 一次。** 检索、合法校验与模型重排在服务端完成；你只发这一遍。禁止并行、禁止换词连搜。失败或空 hits → Fail。
3. **从返回候选写清单。** 工具 hits 已是可用候选（含 `https` `detailUrl`）。在其中选出 **8–12** 条写入 view/artifact，并写质量字段（见 Quality）。  
   要求：**≥3 个不同 `niche`**；同质微差最多 1 条代表；凑不齐 → Fail；禁止编造补足，禁止为此再搜。  
   默认避开（用户未点名时）：重货/泡货、强季节、高退货尺码服饰、大牌极透明价、特殊资质、侵权/假认证/违禁功效。
4. **分配 id。** 按最终清单顺序为每条赋 `pl-1`…`pl-n`；后续领域实体与视图实体的 `id` **同序同值**。
5. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**（测款领域对象：条目、质量字段、assumptions 等），再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact 对象）。可用 `read_file` 自检。
6. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`，模板 `template/view.mustache`）。勿手写 HTML `view.content`。
7. **交付路径与成功标准。** 本轮应写入的 view / artifact 相对路径以 user 消息开头的 `<reminder>` 为准（禁止改名、禁止复用上一轮路径）。**成功** = 盘上已有 reminder 中的 **view** 与 **artifact** 两文件（`write_file` + `render_view`）。不要在对话里输出 `{"output":...}` 或粘贴 `artifact.json` / `view.json` 全文。
8. **过 Verification。** 全部勾上再结束本轮；任一不满足 → Fail 或改盘后重跑 `render_view`。

## Tool: search_sku

商品链接来自本工具返回的 `detailUrl`；实际检索实现由服务端配置 **`forma.sku-search.client`** 决定（如 `mock` 或 `apify`）。

| 参数 | 说明 |
|------|------|
| `query` | 必填；本轮只发这一次调用 |
| `pageSize` | 返回候选条数上限（服务端重排后）；建议 `12`～`20` |

空结果或工具错误 → Fail（不要再调 `search_sku`）。

## Quality

在**工具返回的候选**中选出条目并撰写下列字段；勿臆造工具未返回的商品。不必再做一次「检索式筛选/排名」——排序与合法链路由服务端处理；你负责测款叙事与门槛。

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照条目)）：

- **优先试：** 答清「为何先测它 vs 清单下一条」；禁「市场需求大」「性价比高」
- **niche：** 具体到场景/人群；禁「日用」「家居」空泛三连
- **痛点 / 角度 / 差异：** 各一句可感知表述；禁三句同义反复
- **评分条：** `高｜`/`中｜`/`低｜` + 本条可观察事实；禁「高｜刚需」无事实
- **反凑数：** 同质微差最多 1 条代表；不够则 Fail

## Output

成功终态：**盘上** reminder 指定的 **view** + **artifact** 两文件（通常 `view.json` + `artifact.json`，由 `write_file` / `render_view` 产出）。完整字段、模板与好坏例 → [output.md](references/output.md)。

速记：

- 每条 `id` = `pl-{n}`；HTML 列表与 artifact **同 id 同序**（手递 prompt 含 `来源选品条目：pl-n`）
- `artifact.items[].sourceUrl` = 工具 `detailUrl`；同条手递 prompt「原链」= 该 URL；绝对 `https:`；禁止假链
- 免责声明（HTML 正文）必须包含字面量：`非实时平台全站行情`
- 推荐整句：`候选基于配置的商品检索抽样与服务端排序，非实时平台全站行情。点击可打开商品页核对。`
- 两边均为 8–12 条；`view.json` 由模板生成，`view.version` = **`2`**；`view.format` = **`html`**
- 模板 [view.mustache](template/view.mustache) 为每条详情输出手递按钮（`data-forma-skill-id="ecommerce-skulist"`）；prompt 由工具注入 `handoffPrompt`，合同见 [output.md §手递](references/output.md#手递按钮与-prompt-合同)
- `view.title` 与 `artifact.title`：本轮生成的中文清单标题（同一文案）

## Verification

输出前逐项自检（全部通过才允许结束本轮）：

- [ ] 本轮恰好 **1** 次 `search_sku`，且成功
- [ ] 已写 `artifact.json`，且已成功调用 **`render_view`** 写出 **`view.json`**
- [ ] `<reminder>` 中的 view / artifact 两路径均已写盘；**未**在对话里贴整包大 JSON 或 `{"output":...}`
- [ ] `artifact.items` 与 `view.json` 内 HTML `<ol>` 条目均为 **8–12** 条，条数一致、顺序对应
- [ ] 每条 `id` 非空，格式 `pl-n`（从 1 顺序）；手递 prompt 与 artifact **同 id 同序**
- [ ] 至少 **3** 个不同 `niche`，且无空泛「日用」「家居」三连凑数
- [ ] 恰好 **1–2** 条 `artifact.items[].title` 以 `【优先试】` 开头；HTML 展示标题不加该前缀（模板用 `displayTitle`）
- [ ] 优先试条目含可行动「为何先测」理由（痛点/角度/差异至少一处说清相对下一条的优势）
- [ ] 每条 `sourceUrl` 来自工具 `detailUrl`，绝对 `https:`，无编造；同条手递 prompt「原链」一致
- [ ] `demand` / `competition` / `margin` / `risk` 均以 `高｜` / `中｜` / `低｜` 开头，且挂钩本条可观察事实
- [ ] `view.version` = **`2`**；`view.format` = **`html`**；`view.content` 非空
- [ ] 每条详情含手递按钮：`data-forma-skill-id="ecommerce-skulist"`，标签「做上架素材」，`data-forma-prompt` 符合 output 合同（含 niche/痛点/角度可选行）
- [ ] `view.title` / `artifact.title` 为同一中文标题；正文免责声明含字面量 `非实时平台全站行情`
- [ ] **未** 输出 v1 `blocks` / `list` JSON 视图
- [ ] 未编造 BSR / 生意参谋 / 实时销量榜等全站指标
- [ ] 成功路径无跑题闲聊；失败路径无人话以外的假 JSON

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- `search_sku` 失败 / 空 hits，或返回候选不足 8 条可用
- 候选不足以凑齐 8 条合格清单（即使想再搜也不允许）

## Boundaries

- 不宣称联盟官方推广池或全站实时行情；不编造链接，不编造全站实时指标（BSR、生意参谋、实时销量榜等）
- 不二次 / 并行调用 `search_sku`
