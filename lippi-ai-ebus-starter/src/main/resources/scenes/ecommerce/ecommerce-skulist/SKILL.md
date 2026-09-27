---
name: ecommerce-skulist
description: >-
  两阶段 Listing：先策划分镜（短字段 + view），经 ask_human 确认或补充后，再出执行稿与生图 Prompt（view + artifact）。
  在用户提到上架、Listing、主图、详情文案、商品素材时使用。选品清单请改用 ecommerce-picklist。
allowed-tools: ask_human, read_skill
metadata:
  output:
    billing: true
    persistAs: sku
    requiresView: true
---

# Listing 套装（策划 → 确认 → 执行）

同一次 Run 内分 **策划** 与 **执行** 两段可用成果：

1. **策划**：成交方向、主图分镜（3～5 条）、详情大纲、标题草稿；`view` 用通用 blocks 展示分镜，**不要**上架四字段与生图 Prompt。
2. **`ask_human`**：用户确认出执行稿，或补充需求改策划（可多轮）。
3. **执行**（仅 `confirm_execute` 后）：补齐上架四字段 + 与分镜对齐的 `framePrompts`；`view` 对齐 Listing 预览（hero `media` + 三 `section` + Prompt 摘要）。

界面切换淘/闲/抖只换预览壳，**不拆多套文案**。  
**不**要求用户上传图片；**不**调用生图模型。执行阶段主图位由系统在结算前挂载占位图（`mediaObjectId`）。

## When to use

- 适合：上架素材、Listing、主图方案、详情文案、展示说明、策划分镜
- 不适合：选品清单 / 测款候选（改走 `ecommerce-picklist`）

## Workflow

### Phase A — 策划

1. **读懂商品。** 信息不够时按国内厨房/日用默认假设，写入 `assumptions`。用户说「优先淘宝」等 → 记入假设；语气仍按国内电商成交方向写。**禁止**在未出策划前调用 `ask_human`。
2. **固定底。** `templateId` = `domestic-generic-default`（全程不可改，补充需求也不得改 `templateId`）。
3. **定成交驱动力。** 写入 `driver`（一句成交方向）；家居日用多选「痛点/效率」或「视觉/质感」。
4. **写策划短字段。** `frames`（3～5 条主图分镜短句，每条 ≤40 字）、`modules`（3～5 条详情大纲短句）、`titleDraft`（标题草稿一行）。详见 [output.md](references/output.md) §策划。
5. **写策划 `view`。** 按 spec 映射：`driver` → `note`；`frames[0]` → hero `media` placeholder；`frames` → ordered `list`；`titleDraft` / `modules` → `section`；可选 `assumptions` → `note`。策划阶段**不要**「详情标题/正文/展示说明」三 section。
6. **输出策划 JSON。** 一个 `{ "view": …, "artifact": … }` 对象（策划字段 only）。
7. **立刻调用 `ask_human`**（参数与下方一致，勿在 Computer / JSON 里自造确认按钮）：

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
| `supplement` 和/或自由文本 | **只改策划**（`driver` / `frames` / `modules` / `titleDraft` / `assumptions`；**不得**改 `templateId`）；重输出策划 `{view, artifact}`；**再次** `ask_human`（同上参数） |
| 仅自由文本（无 option） | 视为补充说明，同 `supplement` |

### Phase C — 执行（仅 `confirm_execute` 后）

1. **继承策划字段**（可微调 `titleDraft` / `frames` / `modules`，仍须满足条数与门禁）。
2. **写上架四字段**（卖货口吻）：`heroPlan`、`detailTitle`、`detailBody`、`displayNotes`。
3. **写 `framePrompts`**：与 `frames` **等长**；每项 `{ "prompt": "…", "negative": "…" }`（`negative` 可选）。只出 Prompt，不调生图。
4. **写执行 `view`。** hero `media`（对齐 `heroPlan`）+ 分镜 `list` + 三 `section`（详情标题/正文/展示说明）+ **一条** `section`「生图 Prompt」摘要（全文以 `artifact.framePrompts` 为准）。
5. **过 Verification（执行）。** 全部勾上再输出**最终** JSON。
6. **禁止**输出 `platformCopies` / `preferredPlatform`。

## Output

- **策划中间态**：`view` + 策划 `artifact`（短字段）→ 随后 `ask_human`。
- **执行终态**：`view` + 完整 `artifact`（策划字段 + 四字段 + `framePrompts`）。字段与示例 → [output.md](references/output.md)。

应用层：策划可用成果在首次 `ask_human` 前以 `listing_plan` 落库；确认后的终态仍按 Skill 元数据 `persistAs: sku` 落库。

## Verification

### 策划 JSON（Phase A / 补充后重出）

- [ ] `view.version` = `1`；`view.title` / `artifact.title` 为同一中文标题
- [ ] `artifact.templateId` = `domestic-generic-default`
- [ ] `driver`、`titleDraft` 非空
- [ ] `frames`、`modules` 各 3～5 条非空短句
- [ ] 策划 `view` **无**「详情标题/正文/展示说明」三 section；**无** `framePrompts` / 上架四字段
- [ ] `blocks` 仅 `note` / `list` / `markdown` / `media` / `section`
- [ ] 输出策划 JSON 后**必须**调用 `ask_human`（未确认前禁止 Phase C）
- [ ] 未编造 BSR / 销量 / 资质；未宣称违禁功效

### 执行 JSON（Phase C，终态）

- [ ] 继承策划必填字段；四字段均非空，读起来像上架素材
- [ ] `framePrompts.length` = `frames.length`；每条 `prompt` 非空
- [ ] `detailTitle` 含品类 + 卖点词；`detailBody` 有场景钩子
- [ ] `heroPlan` 写清首图画面任务 + 短卖点（非空说明书腔）
- [ ] 执行 `view` 含 hero `media` + 三详情 `section` + Prompt 摘要 `section`
- [ ] `mediaObjectIds` 可 `[]`（系统挂载后 settle 前须有真实 id）
- [ ] **不要** `platformCopies`
- [ ] 成功路径对象外无闲聊

## Failures

下列情况**只回一句人话原因**，不要输出 JSON：

- 即使用默认假设仍无法形成合格策划结构（缺分镜/大纲/标题草稿）
- 用户确认执行后仍无法形成合格四字段或对齐的 `framePrompts`

## Boundaries

- 不调用生图；不要求用户上传图；不编造第二套主图 URL 真相
- 不强制、不调用 `search_sku`
- 不写积分账本；不做真实平台上架 API
- 确认交互**仅**通过 `ask_human`；Computer 内不嵌入确认按钮
- 前端只展示；不指望前端改写或分拆语气
