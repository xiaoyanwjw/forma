---
name: ecommerce-picklist
description: >-
  经配置的商品检索（如 Mock / Apify 淘宝搜）用 search_sku 产出 8–12 条带原链的可测款选品清单（JSON：view + artifact）。
  在用户提到选品、卖什么、候选清单、测款方向时使用。
  Listing / 主图详情请改用 ecommerce-skulist。
allowed-tools: read_skill search_sku
metadata:
  output:
    billing: true
    persistAs: picklist
    requiresView: true
---

# 选品清单

从商品检索结果中，整理 **8–12** 条可测款候选；每条必须带用户能点开的商品原链。

## When to use

- 适合：选品、卖什么、候选清单、测款方向

## Workflow

1. **提炼搜索词。** 信息不够时把假设写进 `artifact.assumptions`，仍先搜，勿先追问。把用户意图收成**一个**最稳的 `query`。
2. **只调用 `search_sku` 一次。** 禁止并行、禁止换词连搜。失败或空 hits → Fail。
3. **筛选。** 只留带有效 `https` `detailUrl` 的条目。
4. **排名。** 优先：轻小、好发、可视觉差异、可小批量。  
   用户未点名则默认避开：重货/泡货、强季节、高退货尺码服饰、大牌极透明价、特殊资质、侵权/假认证/违禁功效。
5. **凑齐门槛。** 8–12 条，且 **≥3 个不同 `niche`**。凑不齐 → Fail；禁止用常识编造补足，也禁止为此再搜。
6. **写终态 JSON。** 先 `artifact`，再用同一事实写 `view`；字段与示例见 [output.md](references/output.md)。
7. **过 Verification。** 全部勾上再输出；任一不满足 → Fail 或改稿，禁止凑合交付。

## Tool: search_sku

商品链接来自本工具返回的 `detailUrl`；实际检索实现由服务端配置 **`ebus.sku-search.client`** 决定（如 `mock` 或 `apify`）。

| 参数 | 说明 |
|------|------|
| `query` | 必填；本轮只发这一次调用 |
| `platform` | 传给工具的检索上下文；默认 `taobao_tbk`（具体数据源仍取决于服务端 client） |
| `pageSize` | 建议 `12`～`20`（一次拿够候选） |

无 `detailUrl` 的 hits 一律丢弃。空结果或工具错误 → Fail（不要再调 `search_sku`）。

## Output

成功终态是一个对象：`view`（给界面）+ `artifact`（落库回显）。完整字段、对齐规则、示例 → [output.md](references/output.md)。

速记：

- `artifact.items[].sourceUrl` = 工具 `detailUrl`；同条 list `href` = 该 URL；禁止假链
- disclaimer / note 必须包含字面量：`非实时平台全站行情`
- 推荐整句：`候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。`
- 两边均为 8–12 条；`view.version` = `1`
- `view.title` 与 `artifact.title`：本轮生成的中文清单标题（同一文案）

## Verification

输出前逐项自检（全部通过才允许发 JSON）：

- [ ] 本轮恰好 **1** 次 `search_sku`，且成功
- [ ] `artifact.items` 与 `view` list 均为 **8–12** 条，且条数一致、顺序对应
- [ ] 至少 **3** 个不同 `niche`
- [ ] 恰好 **1–2** 条 `artifact.title` 以 `【优先试】` 开头；对应 list 用 `badge: "priority"`（list 标题不加该前缀）
- [ ] 每条 `sourceUrl` / `href` 均来自工具 `detailUrl`，为绝对 `https:`，无编造
- [ ] `demand` / `competition` / `margin` / `risk` 均以 `高｜` / `中｜` / `低｜` 开头
- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题（非 `report`）
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
