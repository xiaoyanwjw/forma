---
name: ecommerce-skulist
description: >-
  生成上架素材：先策划分镜（短字段 + view），经 ask_human 确认或补充后，再出执行稿与生图 Prompt（view + artifact）。
  在用户提到上架、主图、详情文案、商品素材，或从选品候选点「做上架素材」时使用。
  不要用于选品清单 / 测款候选——那些请用 ecommerce-picklist。
allowed-tools: ask_human, read_skill, write_file, read_file, bash
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
---

# Listing 套装（策划 → 确认 → 执行）

国内电商**成交素材**编辑：同一次 Run 内分 **策划** 与 **执行** 两段可用成果。界面换淘/闲/抖只换预览壳，**不拆多套文案**。

1. **策划**：成交方向、主图分镜（3～5 条）、详情大纲、标题草稿；`view` **仅一篇** `markdown`（好读），**不要**上架四字段与生图 Prompt。
2. **`ask_human`**：用户确认出执行稿，或补充需求改策划（可多轮）。
3. **执行**（仅 `confirm_execute` 后）：补齐上架四字段 + 与分镜对齐的 `framePrompts`；`view` 对齐 Listing 预览（hero `media` + 三 `section` + Prompt 摘要）。

**不**要求用户上传图片；**不**调用生图模型。执行阶段主图位由系统在结算前挂载占位图（`mediaObjectId`）。

## When to use

- **用：** 上架素材、Listing、主图方案、详情文案、展示说明、策划分镜；或用户消息含「原链」「来源选品条目」的交接提示
- **不用：** 选品清单 / 测款候选 → `ecommerce-picklist`

## Handoff

用户消息常来自选品 list「做上架素材」，形态：

```text
请为商品「{title}」生成上架素材。
原链：{href}
来源选品条目：{id}
参考：...
痛点：...
角度：...
```

解析规则（进入 Phase A 之前）：

1. 抽出商品名（`「…」`）、原链（`原链：` 后 URL）、`picklistItemId`（`来源选品条目：` 后 id，如 `pl-2`）、可选参考/痛点/角度。
2. **禁止丢掉交接品名**：有原链 + 品名时，以交接商品为唯一对象；不得换成别的品或凭空另起炉灶。
3. 将原链、条目 id、参考写入 `assumptions`（可压缩成一句）；`artifact.picklistItemId` = 交接 id。
4. **必填门禁：** 输入含「原链」或「来源选品条目」时，`picklistItemId` **必填**且与输入一致（策划与执行均同）。无交接的口述 Listing 可省略该字段。

## Workflow

### Phase A — 策划

1. **读懂商品。** 优先用 Handoff；信息不够时按国内厨房/日用默认假设，写入 `assumptions`。用户说「优先淘宝」等 → 记入假设；语气仍按国内电商成交方向写。**禁止**在未出策划前调用 `ask_human`。
2. **固定底。** `templateId` = `domestic-generic-default`（全程不可改，补充需求也不得改 `templateId`）。
3. **定成交驱动力。** 写入 `driver`（一句：谁 + 场景 + 为什么买）；家居日用多选「痛点/效率」或「视觉/质感」。
4. **写策划短字段。** `frames`（3～5 条主图分镜短句，每条 ≤40 字）、`modules`（3～5 条详情大纲短句）、`titleDraft`（标题草稿一行）。详见 [output.md](references/output.md) §策划。
5. **写策划 `view`。** `blocks` **只含 1 个** `markdown`：固定小标题 `## 成交方向` / `## 主图分镜`（有序列表，与 `frames` 一致）/ `## 标题草稿` / `## 详情大纲`（有序列表，与 `modules` 一致）/ 可选 `## 假设`。正文与 `artifact` 同一事实。策划阶段**不要**多块 `note`/`list`/`media`/`section` 拼盘，**不要**「详情标题/正文/展示说明」三 section。
6. **分步写盘（策划，相对 run 工作区根）。** `write_file` → `plan/artifact.json`（**仅**策划 artifact 对象），`write_file` → `plan/view.json`（**仅**策划 view 对象）；可用 `read_file` 自检。字段见 [output.md](references/output.md) §策划。
7. **拼出策划终态。** 用 `write_file` 把策划 view 与 artifact 合并写入 `plan/final.json`（相对 run 根）。内容是一个 JSON 对象：`view` 取 `plan/view.json` 的对象，`artifact` 取 `plan/artifact.json` 的对象。这是支持的合并方式。环境里若已有 `bash` / `python3` 可以用它们拼文件，但不要依赖 `python3`；没有它们时仍用 `write_file` 写 `plan/final.json`。
8. **策划终稿只输出指针。** 对话里**仅**一个 JSON 对象（无围栏、无其它文字）：`{"output":"plan/final.json"}`。禁止在对话里粘贴整包 `{view, artifact}`。
9. **立刻调用 `ask_human`**（参数与下方一致，勿在 Computer / JSON 里自造确认按钮）：

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

### Phase B — 用户响应

| 用户选择 | Agent 行为 |
|----------|------------|
| `confirm_execute` | 进入 Phase C；**禁止**在未收到此选项前写 `framePrompts` 或上架四字段 |
| `supplement` 和/或自由文本 | **只改策划**（`driver` / `frames` / `modules` / `titleDraft` / `assumptions`；**不得**改 `templateId` / `picklistItemId`）；**覆盖** `plan/artifact.json` / `plan/view.json`，再用 `write_file` 重写 `plan/final.json`，再发指针 `{"output":"plan/final.json"}`；**再次** `ask_human`（同上参数） |
| 仅自由文本（无 option） | 视为补充说明，同 `supplement` |

### Phase C — 执行（仅 `confirm_execute` 后）

1. **继承策划字段**（可微调 `titleDraft` / `frames` / `modules`，仍须满足条数与门禁；`picklistItemId` 原样保留）。
2. **写上架四字段**（像真实淘宝详情，不要鸡汤问答腔）：
   - `detailTitle`：品类词 + 2～4 个卖点词，可检索、可读；
   - `detailBody`：3～6 短段或条目——先一句场景/痛点，再写核心卖点与使用感受，可带 1～2 句规格/材质（未知勿编造具体参数）；少用「想换机又怕踩坑」式长提问开场；
   - `displayNotes`：主图顺序与禁区（短）；
   - `heroPlan`：首图画面任务 + 短卖点（给系统挂位，不必在 Adam 大图区展示）。
3. **写 `framePrompts`**：与 `frames` **等长**；每项 `{ "prompt": "…", "negative": "…" }`（`negative` 可选）。只出 Prompt，不调生图。
4. **写执行 `view`。** hero `media`（对齐 `heroPlan`）+ 分镜 `list` + 三 `section`（详情标题/正文/展示说明）+ **一条** `section`「生图 Prompt」（供界面并入主图分镜展示）：`body` 用有序列表写出与 `framePrompts` **逐条对应**的完整 `prompt`（可附 `negative:` 行）。界面会按「一条分镜描述 + 一条 prompt」成对展示，勿只写「共 N 条、详见 artifact」。
5. **分步写盘（执行，相对 run 工作区根）。** `write_file` → `exec/artifact.json`（**仅**完整执行 artifact），`write_file` → `exec/view.json`（**仅**执行 view）；可用 `read_file` 自检。
6. **拼出执行终态。** 用 `write_file` 把执行 view 与 artifact 合并写入 `exec/final.json`。内容是一个 JSON 对象：`view` 取 `exec/view.json` 的对象，`artifact` 取 `exec/artifact.json` 的对象。支持的合并是 `write_file`；`bash` / `python3` 仅在环境里已有时可选。
7. **过 Verification（执行）。** 全部勾上再发指针。
8. **执行终稿只输出指针。** 对话里**仅** `{"output":"exec/final.json"}`（无围栏、无整包 JSON）。
9. **禁止**输出 `platformCopies` / `preferredPlatform`。

## Quality

原则（好坏对照见 [output.md §质量对照](references/output.md#质量对照)）：

- **driver：** 谁 + 场景 + 为什么买（一句）；禁「提升生活品质」
- **frames：** 每条=画面任务（主体/场景/卖点之一），机位不重复；禁「展示产品」「突出卖点」×3
- **detailTitle：** 品类词 + 2～4 个可检索卖点词；禁纯情绪词堆砌
- **detailBody：** 场景落地 → 卖点/感受 → 可知规格；短段；禁连续 ≥2 问句开场；禁编造参数
- **displayNotes：** 主图顺序与禁区（短、可执行）；禁「注意美观」
- **framePrompts：** 与 frames 逐条对齐；含主体+场景+约束；禁空壳「8k/杰作/最佳质量」

## Output

- **策划中间态（盘上真源）：** `plan/final.json`（策划 `{view, artifact}` 信封）+ 对话指针 `{"output":"plan/final.json"}` → 随后 `ask_human`。
- **执行终态（盘上真源）：** `exec/final.json`（完整 `{view, artifact}`）+ 对话指针 `{"output":"exec/final.json"}`。字段、示例与好坏例 → [output.md](references/output.md)。

应用层：策划可用成果在首次 `ask_human` 前以 `listing_plan` 落库（读 `plan/final.json`）；确认后的终态仍按 Skill 元数据 `persistAs: sku` 落库（读 `exec/final.json`）。同一 `runId` 工作区在挂起策划 settle 后**保留**，供 `supplement` / `confirm_execute` 继续写盘。

## Verification

### 策划终稿 / 指针（Phase A，`plan/final.json`）

- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] `driver`、`titleDraft` 非空；`driver` 含场景/受众，非空泛品质套话
- [ ] `frames`、`modules` 各 3～5 条非空短句；`frames` 无同义重复机位
- [ ] 若输入含「来源选品条目」或「原链」→ `picklistItemId` 非空且与输入一致；`assumptions` 含原链或交接摘要
- [ ] 策划 `view.blocks` **恰好 1 个** `markdown`（含上述小标题）；**无**「详情标题/正文/展示说明」三 section；**无** `framePrompts` / 上架四字段
- [ ] Markdown 与 `artifact` 短字段同一事实
- [ ] 已写 `plan/artifact.json`、`plan/view.json`，且已用 `write_file` 写出 **`plan/final.json`**
- [ ] 策划终稿对话**仅** `{"output":"plan/final.json"}`；**未**在对话里贴整包大 JSON
- [ ] 发策划指针后**必须**调用 `ask_human`（未确认前禁止 Phase C）
- [ ] 未编造 BSR / 销量 / 资质；未宣称违禁功效

### 执行终稿 / 指针（Phase C，`exec/final.json`）

- [ ] 继承策划必填字段（含交接路径下的 `picklistItemId`）；四字段均非空，读起来像上架素材
- [ ] `framePrompts.length` = `frames.length`；每条 `prompt` 非空；非空壳「8k/杰作/最佳质量」
- [ ] `detailTitle` 含品类 + 卖点词；`detailBody` 像真实详情短段（场景一句 + 卖点/材质），无连续 ≥2 问句开场
- [ ] `heroPlan` 写清首图画面任务 + 短卖点（非空说明书腔）
- [ ] 执行 `view` 含 hero `media` + 三详情 `section` + Prompt 摘要 `section`
- [ ] `mediaObjectIds` 可 `[]`（系统挂载后 settle 前须有真实 id）
- [ ] **不要** `platformCopies`
- [ ] 已写 `exec/artifact.json`、`exec/view.json`，且已用 `write_file` 写出 **`exec/final.json`**
- [ ] 执行终稿对话**仅** `{"output":"exec/final.json"}`；**未**在对话里贴整包大 JSON
- [ ] 成功路径除指针外无闲聊（`ask_human` 工具调用除外）

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
