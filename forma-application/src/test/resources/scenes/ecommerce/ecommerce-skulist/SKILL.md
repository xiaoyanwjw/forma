---
name: ecommerce-skulist
description: >-
  一次生成上架素材：成交方向、主图分镜、详情三件套与生图 Prompt（view + artifact）。
  在用户提到上架、主图、详情文案、商品素材，或从选品候选点「做上架素材」时使用。
allowed-tools: read_skill write_file read_file render_view
metadata:
  billing: true
  persistAs: sku
  requiresView: true
  output: view.json
---

# Listing 套装

国内电商**成交素材**编辑：一轮写完整上架稿。界面换淘/闲/抖只换预览壳，**不拆多套文案**。

- **交付：** 成交方向、主图分镜（3～5）、详情大纲、标题草稿，以及上架四字段 + 对齐的 `framePrompts`；`view` v2 HTML（含 `data-forma-media-role="hero"` 占位图 + 分镜/详情/Prompt 标题段）。

**不**要求用户上传图片；**不**调用生图模型。近端不强制 `mediaObjectId`。

## When to use

- **用：** 上架素材、Listing、主图方案、详情文案、展示说明、策划分镜；或用户消息含「原链」「来源选品条目」的交接提示
- **不用：** 选品清单 / 测款候选 → `ecommerce-picklist`

## Workflow

1. **解析交接（若有）。** 用户消息常来自选品「做上架素材」：

```text
请为商品「{title}」生成上架素材。
原链：{href}
来源选品条目：{id}
参考：...
痛点：...
角度：...
```

抽出品名、原链、`picklistItemId`（如 `pl-2`）、可选参考/痛点/角度。有原链 + 品名时**禁止换品**。原链/条目 id/参考写入 `assumptions`；`artifact.picklistItemId` = 交接 id。输入含「原链」或「来源选品条目」时，`picklistItemId` **必填**且与输入一致。无交接的口述 Listing 可省略该字段。

2. **读懂商品。** 优先用交接；不够则按国内数码配件/桌面默认假设写入 `assumptions`。用户说「优先淘宝」等 → 记入假设；语气按国内电商成交方向。

3. **固定底 + 成交驱动力。** `templateId` = `domestic-generic-default`（不可改）。`driver` 一句：谁 + 场景 + 为什么买；家居日用多选「痛点/效率」或「视觉/质感」。

4. **构造领域实体。** 按 [output.md](references/output.md) 拼出完整 **artifact**：`driver` / `frames` 3～5 条≤40 字 / `modules` 3～5 条 / `titleDraft` / 上架四字段 + 与 `frames` **等长**的 `framePrompts`（每项 `{ "prompt": "…", "negative": "…" }`，`negative` 可选；只出 Prompt，不调生图）。
   - `detailTitle`：品类词 + 2～4 个卖点词；
   - `detailBody`：3～6 短段——场景/痛点一句 → 卖点/感受 → 可知规格（未知勿编）；少用长提问开场；
   - `displayNotes`：主图顺序与禁区（短）；
   - `heroPlan`：首图画面任务 + 短卖点。
   再 `write_file` → `artifact.json`（相对 run 根，**仅** artifact）。可用 `read_file` 自检。

5. **渲染视图。** 调用 **`render_view`**（`artifact`: `artifact.json`，`out`: `view.json`，`template`: `template/exec/view.mustache`）。勿手写 HTML `content`。

6. **交付。** 本轮路径以 `<reminder>` 为准（通常 `view.json`）。**成功** = reminder 中的 output 已写盘。不要在对话里输出 `{"output":...}` 或整包 JSON。过 Verification 后结束。**禁止**输出 `platformCopies` / `preferredPlatform`。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **driver：** 谁 + 场景 + 为什么买（一句）；禁「提升生活品质」
- **frames：** 每条=画面任务（主体/场景/卖点之一），机位不重复；禁「展示产品」「突出卖点」×3
- **detailTitle：** 品类词 + 2～4 个可检索卖点词；禁纯情绪词堆砌
- **detailBody：** 场景落地 → 卖点/感受 → 可知规格；短段；禁连续 ≥2 问句开场；禁编造参数
- **displayNotes：** 主图顺序与禁区（短、可执行）；禁「注意美观」
- **framePrompts：** 与 frames 逐条对齐；含主体+场景+约束；禁空壳「8k/杰作/最佳质量」

## Output

`<reminder>` 指定的 output 已写盘（通常 `view.json`）。字段、示例与好坏例 → [output.md](references/output.md)。

应用层按 `persistAs: sku` 落库（读 reminder 中的 output）。

## Verification

- [ ] `view.version` = **`2`**；`view.format` = **`html`**；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] `driver`、`titleDraft` 非空；`driver` 含场景/受众，非空泛品质套话
- [ ] `frames`、`modules` 各 3～5 条非空短句；`frames` 无同义重复机位
- [ ] 若输入含「来源选品条目」或「原链」→ `picklistItemId` 非空且与输入一致；`assumptions` 含原链或交接摘要
- [ ] 四字段均非空；`framePrompts.length` = `frames.length`；每条 `prompt` 非空；非空壳「8k/杰作/最佳质量」
- [ ] `detailTitle` 含品类 + 卖点词；`detailBody` 像真实详情短段，无连续 ≥2 问句开场
- [ ] `heroPlan` 写清首图画面任务 + 短卖点
- [ ] `view.content` 含 `data-forma-media-role="hero"` 占位图 + 主图分镜 / 详情三件套 / 生图 Prompt 标题段
- [ ] HTML 与 `artifact` 短字段同一事实
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] `mediaObjectIds` 可缺省或 `[]`（近端不强制）
- [ ] **不要** `platformCopies`
- [ ] 已写 `artifact.json`，且已成功 **`render_view`** 写出 **`view.json`**
- [ ] reminder 中的 output 已写盘；**未**输出 `{"output":...}`
- [ ] 未编造 BSR / 销量 / 资质；未宣称违禁功效
- [ ] 成功路径无跑题闲聊

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 即使用默认假设仍无法形成合格分镜/大纲/标题草稿与上架四字段、对齐的 `framePrompts`
- 交接输入含「原链」或「来源选品条目」但无法解析出一致的 `picklistItemId`

## Boundaries

- 不调用生图；不要求用户上传图；不编造第二套主图 URL 真相
- 不强制、不调用 `search_sku`
- 不写积分账本；不做真实平台上架 API
- 不调用 `ask_human`；Computer 内不嵌入确认按钮
- 前端只展示；不指望前端改写或分拆语气
