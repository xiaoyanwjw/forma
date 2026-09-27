---
name: ecommerce-skulist
description: >-
  按国内通用默认风格生成 Listing 套装（主图方案说明 + 详情文案 + 展示说明；JSON：view + artifact）。
  在用户提到上架、Listing、主图、详情文案、商品素材时使用。
  选品清单请改用 ecommerce-picklist。
allowed-tools: read_skill
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
---

# Listing 套装

为用户要上架的商品整理一套可改后上架的素材：主图方案说明、详情标题/正文、展示说明。  
**不**要求用户上传图片；**不**调用生图模型。主图位由系统在结算前挂载占位图（`mediaObjectId`）。

## When to use

- 适合：上架素材、Listing、主图方案、详情文案、展示说明
- 不适合：选品清单 / 测款候选（改走 `ecommerce-picklist`）

## Workflow

1. **读懂商品。** 信息不够时按国内通用默认假设生成，并把假设写进 `artifact.assumptions`；勿追问、勿 `ask_human`。
2. **固定风格。** `templateId` 必须是 `domestic-generic-default`；不做品类模板选择。
3. **写主图方案（文案）。** 说明构图、主体、卖点标注、背景与禁忌（勿写真实外链当唯一主图真相）。`mediaObjectIds` 可先写空数组；系统会挂占位图。
4. **写详情与展示说明。** 标题清晰、正文可读可改；展示说明交代卖点顺序与合规注意。
5. **写终态 JSON。** 先 `artifact`，再用同一事实写 `view`；字段与示例见 [output.md](references/output.md)。
6. **过 Verification。** 全部勾上再输出；任一不满足 → Fail 或改稿。

## Output

成功终态是一个对象：`view`（给界面）+ `artifact`（落库回显）。完整字段、对齐规则、示例 → [output.md](references/output.md)。

速记：

- `view.version` = `1`；`view.title` 与 `artifact.title` 同一中文标题
- 必须有 `media`（hero）块：`placeholder`/`alt` 写主图方案要点；`mediaObjectId` 可空（系统填充）
- `section`：详情标题、详情正文、展示说明（与 artifact 字段对齐）
- `templateId` = `domestic-generic-default`
- 禁止伪造实时平台指标与违规功效宣称

## Verification

输出前逐项自检（全部通过才允许发 JSON）：

- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] `heroPlan` / media `placeholder` 含可读主图方案说明（非空）
- [ ] `detailTitle`、`detailBody`、`displayNotes` 均非空
- [ ] `blocks` 含 hero `media` + 对应 `section`；仅白名单类型
- [ ] 未编造 BSR / 生意参谋 / 实时销量榜等全站指标
- [ ] 未宣称需特殊资质的功效/医疗/违禁表述
- [ ] 成功路径对象外无闲聊；失败路径无人话以外的假 JSON

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 即使用默认假设仍无法形成合格结构（缺文案/展示说明/主图方案）

## Boundaries

- 不调用生图；不要求用户上传图；不编造第二套主图 URL 真相
- 不强制、不调用 `search_sku`
- 不写积分账本；不做真实平台上架 API
