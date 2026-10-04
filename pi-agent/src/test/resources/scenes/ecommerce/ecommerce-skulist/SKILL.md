---
name: ecommerce-skulist
description: >-
  生成上架素材：先策划分镜（短字段 + view），经 ask_human 确认或补充后，再出执行稿与生图 Prompt（view + artifact）。
  在用户提到上架、主图、详情文案、商品素材，或从选品候选点「做上架素材」时使用。
allowed-tools: ask_human, read_skill, write_file, read_file, render_view
metadata:
  output:
    billing: true
    persistAs: sku
    hideFromHistory: listing_plan
    requiresView: true
    viewPath: exec/view.json
    artifactPath: exec/artifact.json
    planViewPath: plan/view.json
    planArtifactPath: plan/artifact.json
---

# Listing 套装

国内电商**成交素材**编辑：同一次 Run 内先后产出**策划**与**执行**两段可用成果。界面换淘/闲/抖只换预览壳，**不拆多套文案**。

- **策划：** 成交方向、主图分镜（3～5）、详情大纲、标题草稿；`view` v2 **`format: html`** + `content`（小节标题 + 列表）；**不要**上架四字段与生图 Prompt。
- **执行：** 仅用户 `confirm_execute` 后补齐上架四字段 + 对齐的 `framePrompts`；`view` v2 HTML 文档（含 `data-forma-media-role="hero"` 占位图 + 分镜/详情/Prompt 标题段）。

**不**要求用户上传图片；**不**调用生图模型。近端不强制 `mediaObjectId`（无系统占位挂载）；日后 skill 触发出图再写入 MediaStore。

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

抽出品名、原链、`picklistItemId`（如 `pl-2`）、可选参考/痛点/角度。有原链 + 品名时**禁止换品**。原链/条目 id/参考写入 `assumptions`；`artifact.picklistItemId` = 交接 id。输入含「原链」或「来源选品条目」时，`picklistItemId` **必填**且与输入一致（策划与执行均同）。无交接的口述 Listing 可省略该字段。

2. **读懂商品。** 优先用交接；不够则按国内数码配件/桌面默认假设写入 `assumptions`。用户说「优先淘宝」等 → 记入假设；语气按国内电商成交方向。**禁止**在未出策划前调用 `ask_human`。

3. **固定底 + 成交驱动力。** `templateId` = `domestic-generic-default`（全程不可改，含补充轮）。`driver` 一句：谁 + 场景 + 为什么买；家居日用多选「痛点/效率」或「视觉/质感」。

4. **构造领域实体（策划）。** 按 [output.md](references/output.md) §策划拼出策划 **artifact**（`driver` / `frames` 3～5 条≤40 字 / `modules` 3～5 条 / `titleDraft` / `assumptions` / 交接字段等），再 `write_file` → `plan/artifact.json`（相对 run 根，**仅**策划 artifact）。可用 `read_file` 自检。

5. **渲染策划视图。** 调用 **`render_view`**（`artifact`: `plan/artifact.json`，`out`: `plan/view.json`，`template`: `template/plan/view.mustache`）。**不要** hero 占位图、详情三件套、生图 Prompt。勿手写 HTML `content`。

6. **策划交付。** 本轮 view / artifact 路径以 `<reminder>` 为准（策划段通常为 `plan/view.json` + `plan/artifact.json`）。**成功** = 两文件已在盘上。不要在对话里输出 `{"output":...}` 或整包 JSON。过下方「策划」Verification 后再 `ask_human`。

7. **立刻 `ask_human`**（勿在 Computer / JSON 里自造确认按钮）：

```json
{
  "question": "策划分镜已出。确认后将生成执行稿与生图 Prompt（再扣 1 积分）。也可补充需求让我改策划。",
  "options": [
    { "id": "confirm_execute", "label": "确认，出执行稿" },
    { "id": "supplement", "label": "补充需求" }
  ],
  "allowFreeText": true
}
```

- **`confirm_execute`** → 继续第 8 步。**禁止**在收到此选项前写 `framePrompts` 或上架四字段。
- **`supplement` 和/或自由文本**（无 option 的纯文本同补充）→ **只改策划**（`driver` / `frames` / `modules` / `titleDraft` / `assumptions`；**不得**改 `templateId` / `picklistItemId`）；回到第 4～7 步重写 `plan/*`、再 `ask_human`。

8. **构造领域实体（执行）。** 继承策划字段（可微调 `titleDraft` / `frames` / `modules`，仍须满足条数与门禁；`picklistItemId` 原样保留）。补齐完整执行 **artifact**：上架四字段（像真实淘宝详情，不要鸡汤问答腔）+ 与 `frames` **等长**的 `framePrompts`（每项 `{ "prompt": "…", "negative": "…" }`，`negative` 可选；只出 Prompt，不调生图）。  
   - `detailTitle`：品类词 + 2～4 个卖点词；  
   - `detailBody`：3～6 短段——场景/痛点一句 → 卖点/感受 → 可知规格（未知勿编）；少用长提问开场；  
   - `displayNotes`：主图顺序与禁区（短）；  
   - `heroPlan`：首图画面任务 + 短卖点。  
   再 `write_file` → `exec/artifact.json`（**仅**执行 artifact）。见 [output.md](references/output.md) §执行。

9. **渲染执行视图。** 调用 **`render_view`**（`artifact`: `exec/artifact.json`，`out`: `exec/view.json`，`template`: `template/exec/view.mustache`）。模板含 hero 占位 `<img data-forma-media-role="hero" …>`。勿手写 HTML `content`。

10. **执行交付。** 本轮 view / artifact 路径以 `<reminder>` 为准（执行段通常为 `exec/view.json` + `exec/artifact.json`）。**成功** = 两文件已在盘上。过下方「执行」Verification 后结束。**禁止**输出 `platformCopies` / `preferredPlatform`；不要在对话里输出 `{"output":...}`。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **driver：** 谁 + 场景 + 为什么买（一句）；禁「提升生活品质」
- **frames：** 每条=画面任务（主体/场景/卖点之一），机位不重复；禁「展示产品」「突出卖点」×3
- **detailTitle：** 品类词 + 2～4 个可检索卖点词；禁纯情绪词堆砌
- **detailBody：** 场景落地 → 卖点/感受 → 可知规格；短段；禁连续 ≥2 问句开场；禁编造参数
- **displayNotes：** 主图顺序与禁区（短、可执行）；禁「注意美观」
- **framePrompts：** 与 frames 逐条对齐；含主体+场景+约束；禁空壳「8k/杰作/最佳质量」

## Output

- **策划：** `<reminder>` 指定的 plan view + plan artifact 两文件已在盘上（通常 `plan/view.json` + `plan/artifact.json`）→ 随后 `ask_human`
- **执行：** `<reminder>` 指定的 exec view + exec artifact 两文件已在盘上（通常 `exec/view.json` + `exec/artifact.json`）  
字段、示例与好坏例 → [output.md](references/output.md)。

应用层：首次 `ask_human` 前以 `listing_plan` 落库（读 `plan/view.json` + 同目录 `plan/artifact.json`）；确认后按 `persistAs: sku` 落库（读 `exec/view.json` + 同目录 `exec/artifact.json`）。同一 `runId` 工作区在策划 settle 后**保留**，供补充/执行继续写盘。

## Verification

### 策划段（reminder 中的 plan 路径）

- [ ] `view.version` = **`2`**；`view.format` = **`html`**；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] `driver`、`titleDraft` 非空；`driver` 含场景/受众，非空泛品质套话
- [ ] `frames`、`modules` 各 3～5 条非空短句；`frames` 无同义重复机位
- [ ] 若输入含「来源选品条目」或「原链」→ `picklistItemId` 非空且与输入一致；`assumptions` 含原链或交接摘要
- [ ] 策划 `view.content` HTML 含成交方向 / 主图分镜 / 标题草稿 / 详情大纲；**无** hero 占位；**无** `framePrompts` / 上架四字段
- [ ] HTML 与 `artifact` 短字段同一事实
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] 已写 `plan/artifact.json`，且已成功 **`render_view`** 写出 **`plan/view.json`**
- [ ] reminder 中的 plan view / plan artifact 均已写盘；**必须** `ask_human`（未确认前禁止执行稿）；**未**输出 `{"output":...}`
- [ ] 未编造 BSR / 销量 / 资质；未宣称违禁功效

### 执行段（reminder 中的 exec 路径）

- [ ] 继承策划必填字段（含交接路径下的 `picklistItemId`）；四字段均非空
- [ ] `framePrompts.length` = `frames.length`；每条 `prompt` 非空；非空壳「8k/杰作/最佳质量」
- [ ] `detailTitle` 含品类 + 卖点词；`detailBody` 像真实详情短段，无连续 ≥2 问句开场
- [ ] `heroPlan` 写清首图画面任务 + 短卖点
- [ ] 执行 `view.version` = **`2`**；`view.format` = **`html`**；`content` 含 `data-forma-media-role="hero"` 占位图 + 主图分镜 / 详情三件套 / 生图 Prompt 标题段
- [ ] **未** 输出 v1 `blocks` JSON 视图
- [ ] `mediaObjectIds` 可缺省或 `[]`（近端不强制）
- [ ] **不要** `platformCopies`
- [ ] 已写 `exec/artifact.json`，且已成功 **`render_view`** 写出 **`exec/view.json`**
- [ ] reminder 中的 exec view / exec artifact 均已写盘；**未**输出 `{"output":...}`
- [ ] 成功路径无跑题闲聊（`ask_human` 除外）

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 即使用默认假设仍无法形成合格策划结构（缺分镜/大纲/标题草稿）
- 用户确认执行后仍无法形成合格四字段或对齐的 `framePrompts`
- 交接输入含「原链」或「来源选品条目」但无法解析出一致的 `picklistItemId`

## Boundaries

- 不调用生图；不要求用户上传图；不编造第二套主图 URL 真相
- 不强制、不调用 `search_sku`
- 不写积分账本；不做真实平台上架 API
- 确认交互**仅**通过 `ask_human`；Computer 内不嵌入确认按钮
- 前端只展示；不指望前端改写或分拆语气
