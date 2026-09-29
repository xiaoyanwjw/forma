---
name: ecommerce-picklist
description: >-
  经配置的商品检索（如 Mock / Apify 淘宝搜）用 search_sku 产出 8–12 条带原链与 pl-n item id 的可测款选品清单（JSON：view + artifact）。
  在用户提到选品、卖什么、候选清单、测款方向时使用。
  不要用于 Listing / 主图 / 详情文案——那些请用 ecommerce-skulist。
allowed-tools: read_skill search_sku
metadata:
  output:
    billing: true
    persistAs: picklist
    requiresView: true
---

# 选品清单

帮卖家做**测款筛选**：从一次检索结果里挑可行动候选，不是复读工具标题。每条必须带用户能点开的商品原链。

## When to use

- **用：** 选品、卖什么、候选清单、测款方向
- **不用：** 上架素材、主图分镜、详情文案 → `ecommerce-skulist`

## Workflow

1. **提炼搜索词。** 信息不够时把假设写入 `artifact.assumptions`，仍先搜，勿先追问。收成**一个**最稳 `query`。
2. **只调用 `search_sku` 一次。** 禁止并行、禁止换词连搜。失败或空 hits → Fail。
3. **筛选。** 只留带有效 `https` `detailUrl` 的条目。无链丢弃。
4. **排名。** 优先：轻小、好发、可视觉差异、可小批量。  
   用户未点名则默认避开：重货/泡货、强季节、高退货尺码服饰、大牌极透明价、特殊资质、侵权/假认证/违禁功效。  
   同质微差最多留 1 条代表；为凑数塞违禁/假功效 → 禁止。
5. **凑齐门槛并写质量字段。** 8–12 条，**≥3 个不同 `niche`**。凑不齐 → Fail；禁止编造补足，禁止为此再搜。  
   每条写可验证的痛点/角度/差异与评分条（见 Quality）。
6. **分配 id。** 按最终顺序为每条赋 `pl-1`…`pl-n`；`artifact.items[].id` 与 `view.list.items[].id` **同序同值**。
7. **写终态 JSON。** 先 `artifact`，再用同一事实写 `view`；字段与示例见 [output.md](references/output.md)。
8. **过 Verification。** 全部勾上再输出；任一不满足 → Fail 或改稿。

## Tool: search_sku

商品链接来自本工具返回的 `detailUrl`；实际检索实现由服务端配置 **`ebus.sku-search.client`** 决定（如 `mock` 或 `apify`）。

| 参数 | 说明 |
|------|------|
| `query` | 必填；本轮只发这一次调用 |
| `platform` | 传给工具的检索上下文；默认 `taobao_tbk`（具体数据源仍取决于服务端 client） |
| `pageSize` | 建议 `12`～`20`（一次拿够候选） |

空结果或工具错误 → Fail（不要再调 `search_sku`）。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照条目)）：

- **优先试：** 答清「为何先测它 vs 清单下一条」；禁「市场需求大」「性价比高」
- **niche：** 具体到场景/人群；禁「日用」「家居」空泛三连
- **痛点 / 角度 / 差异：** 各一句可感知表述；禁三句同义反复
- **评分条：** `高｜`/`中｜`/`低｜` + 本条可观察事实；禁「高｜刚需」无事实
- **反凑数：** 同质微差最多 1 条代表；不够则 Fail

## Output

成功终态：一个对象 `view` + `artifact`。完整字段、对齐、示例与好坏例 → [output.md](references/output.md)。

速记：

- 每条 `id` = `pl-{n}`；list 与 artifact **同 id 同序**
- `artifact.items[].sourceUrl` = 工具 `detailUrl`；同条 list `href` = 该 URL；绝对 `https:`；禁止假链
- disclaimer / note 必须包含字面量：`非实时平台全站行情`
- 推荐整句：`候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。`
- 两边均为 8–12 条；`view.version` = `1`
- `view.title` 与 `artifact.title`：本轮生成的中文清单标题（同一文案）

## Verification

输出前逐项自检（全部通过才允许发 JSON）：

- [ ] 本轮恰好 **1** 次 `search_sku`，且成功
- [ ] `artifact.items` 与 `view` list 均为 **8–12** 条，条数一致、顺序对应
- [ ] 每条 `id` 非空，格式 `pl-n`（从 1 顺序）；list 与 artifact **同 id 同序**
- [ ] 至少 **3** 个不同 `niche`，且无空泛「日用」「家居」三连凑数
- [ ] 恰好 **1–2** 条 `artifact.title` 以 `【优先试】` 开头；对应 list `badge: "priority"`（list 标题不加该前缀）
- [ ] 优先试条目含可行动「为何先测」理由（痛点/角度/差异至少一处说清相对下一条的优势）
- [ ] 每条 `sourceUrl` / `href` 均来自工具 `detailUrl`，绝对 `https:`，无编造
- [ ] `demand` / `competition` / `margin` / `risk` 均以 `高｜` / `中｜` / `低｜` 开头，且挂钩本条可观察事实
- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `blocks` 仅白名单类型；含 mute `note` 免责声明
- [ ] disclaimer / note 含字面量 `非实时平台全站行情`
- [ ] 未编造 BSR / 生意参谋 / 实时销量榜等全站指标
- [ ] 成功路径对象外无闲聊；失败路径无人话以外的假 JSON

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- `search_sku` 失败 / 空 hits，或筛完后无可用 `detailUrl`
- 筛完后合格条数不足 8（即使想再搜也不允许）

## Boundaries

- 不宣称联盟官方推广池或全站实时行情；不编造链接，不编造全站实时指标（BSR、生意参谋、实时销量榜等）
- 不二次 / 并行调用 `search_sku`
