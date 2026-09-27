---
name: ecommerce-skulist
description: >-
  按国内电商成交文案习惯生成 Listing（主图方案 + 详情标题/正文 + 展示说明；JSON：view + artifact）。
  在用户提到上架、Listing、主图、详情文案、商品素材时使用。选品清单请改用 ecommerce-picklist。
allowed-tools: read_skill
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
---

# Listing 套装

为用户要上架的商品整理**一套可落库字段**（`heroPlan` / `detailTitle` / `detailBody` / `displayNotes`）。  
写法对齐国内电商（淘宝/天猫系）成交文案：利益清晰、可搜索、可进详情，而不是摄影技术说明或策划备忘。  
界面切换淘/闲/抖只换预览壳，**不拆多套文案**。  
**不**要求用户上传图片；**不**调用生图模型。主图位由系统在结算前挂载占位图（`mediaObjectId`）。

参考业界电商视觉/文案 Skill 的做法（如转化驱动力、Feature→Benefit、主图分任务），但本 Skill **只交付四字段 JSON**，不做分镜确认门、不写生图 Prompt。

## When to use

- 适合：上架素材、Listing、主图方案、详情文案、展示说明
- 不适合：选品清单 / 测款候选（改走 `ecommerce-picklist`）

## Workflow

1. **读懂商品。** 信息不够时按国内厨房/日用默认假设，写入 `assumptions`。用户说「优先淘宝」等 → 记入假设，仍只写一套字段，语气按国内电商成交文案写。勿 `ask_human`。
2. **固定底。** `templateId` = `domestic-generic-default`。
3. **定成交驱动力（心里选，不必另起字段）。** 家居日用多选「痛点/效率」或「视觉/质感」；只保留 Top 3 购买理由，并做 `功能 → 好处 → 场景` 利益翻译。
4. **写四字段（卖货口吻，不是策划备注）。** 详见 [output.md](references/output.md)。
5. **写 `view`。** hero `media` + 三个 `section`，与 artifact 四字段一致。
6. **过 Verification。** 全部勾上再输出。

## Output

成功终态：`view` + `artifact`。字段与示例 → [output.md](references/output.md)。

速记：

- 一套 `heroPlan` / `detailTitle` / `detailBody` / `displayNotes`
- 标题像能搜到的宝贝名；正文像商详卖点；主图方案写「画面任务 + 短卖点」，禁止「避免杂乱道具」这类说明书腔
- `templateId` = `domestic-generic-default`
- 禁止伪造销量/榜单/资质；禁止医疗功效；**不要**输出 `platformCopies`

## Verification

输出前逐项自检（全部通过才允许发 JSON）：

- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] 四字段均非空，且读起来像上架素材（非摄影/合规 checklist 备忘）
- [ ] `detailTitle` 含品类 + 卖点词，可作搜索标题
- [ ] `detailBody` 有痛点或场景钩子，再讲材质/用法，不是参数罗列
- [ ] `heroPlan` 写清首图画面任务 + 可落图的短卖点，不是「白底居中避免水印」空话
- [ ] `blocks` 与四字段一致
- [ ] 未编造 BSR / 生意参谋 / 实时销量 / 直播在线人数等
- [ ] 未宣称需特殊资质的功效/医疗/违禁表述
- [ ] 成功路径对象外无闲聊

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 即使用默认假设仍无法形成合格结构（缺文案/展示说明/主图方案）

## Boundaries

- 不调用生图；不要求用户上传图；不编造第二套主图 URL 真相
- 不强制、不调用 `search_sku`
- 不写积分账本；不做真实平台上架 API
- 前端只展示；不指望前端改写或分拆语气
