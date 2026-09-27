# Listing 策划分镜 + ask_human 确认执行（设计）

**Date:** 2026-09-27  
**Status:** accepted  
**Decision:** 方案一 — 同 Run 内「策划 → ask_human →（可多轮补充）→ 执行稿」；策划与执行各 settle 1 积分；只要 Prompt 不真出图；Computer 只用通用 blocks。  
**Related:**  
- [`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md)  
- [`2026-09-27-generation-artifact-persist-simplify-design.md`](./2026-09-27-generation-artifact-persist-simplify-design.md)  
- Spine AD-4 / AD-5；pi-agent-slim AD-S12 / AD-S13（`ask_human` + `human_input_required` + resume）  
- 现网 `ecommerce-skulist`（四字段 Listing）将演进为本规约两阶段形态  

---

## 1. Problem

当前 Listing skill 一次交付扁平四字段（`heroPlan` / `detailTitle` / `detailBody` / `displayNotes`），口吻接近商详底稿，**不像市面电商素材 Skill 的策划分镜**（转化方向 → 主图分任务 → 详情大纲 → 确认后再出执行稿 / Prompt）。

产品要求向「市面级全流程」靠拢，且：

1. 确认交互用 **`ask_human`**（非 Computer 自造按钮）。  
2. **策划与确认后的执行各扣 1 积分**。  
3. 执行阶段只要 **生图 Prompt**，不调生图；主图位仍系统占位。  
4. 前端继续只渲染通用 Computer 组件，不解析业务专用 JSON 树。

---

## 2. Goals / Non-goals

### Goals

1. Listing 生成拆成 **策划** 与 **执行** 两段可用成果，同 Run、HITL 续跑。  
2. 策划字段短；`view` 用 `note` / `list` / `section` / `media` 表达分镜。  
3. 执行稿补齐上架四字段 + `framePrompts`；`view` 对齐现有 Listing 预览（三 section + hero media）。  
4. `ask_human` 固定选项：**确认出执行稿** / **补充需求**（可多轮改策划后再确认）。  
5. 积分：策划可用后扣 1；确认且执行可用后再扣 1。

### Non-goals

- 真出图 / 生图模型调用。  
- 平台四套 `platformCopies`。  
- Computer 内嵌确认按钮绕过 `ask_human`。  
- 改选品清单流程。  
- 本文件不强制改 Spine 全文（落地前以本规约 + 必要时 course-correction）。

---

## 3. Product flow（§1）

```text
用户：生成上架素材
  → prepare：预占（推荐双 hold：holdPlan + holdExec；或策划 1 + 确认后再 reserve 执行 1）
  → Agent 写策划 view + artifact(plan)
  → 门闩 A：策划 view 可投影 → persist 策划成果 → settle(holdPlan) 扣 1
       → 可选 artifact_ready（策划）→ Computer 显示分镜
  → ask_human（确认 / 补充需求）
  → resume
       ├─ supplement（+ 说明）→ 改策划 view → 再次 ask_human（不扣策划分、不扣执行分）
       └─ confirm_execute → 写执行稿 → 挂占位主图
            → 门闩 B：SKU 可用 → persist SKU → settle(holdExec) 扣 1
            → artifact_ready（执行）+ run_settled
```

| 项 | 约定 |
|----|------|
| 积分 | 策划 1 + 执行 1；各段「可用成果落库」后 settle |
| 挂起 | `human_input_required` 期间不 settle **执行** 那一笔 |
| 生图 | 只出 Prompt；主图系统占位 |
| 平台 | 一套公共文案；淘/闲/抖只换预览壳 |
| 确认 UI | 仅 `ask_human` → 聊天选项（AD-S12） |

---

## 4. Field contract + Computer mapping（§2）

### 4.1 原则

- **Computer 只认** `markdown` | `note` | `list` | `media` | `section`（双轨合同）。  
- 业务字段在 `artifact`；用户可见内容必须已投影进 `view.blocks`。  
- FE **不**根据 `frames` / `framePrompts` 自造组件树。

### 4.2 策划成果（短字段）

| 字段 | 说明 | 必填 |
|------|------|------|
| `templateId` | `domestic-generic-default` | ✓ |
| `driver` | 一句成交方向 | ✓ |
| `frames` | 3～5 条短句（每条 ≤40 字） | ✓ |
| `modules` | 3～5 条详情大纲短句 | ✓ |
| `titleDraft` | 标题草稿一行 | ✓ |
| `assumptions` | 假设 | 可选 |

**策划 `view` 映射**

| 字段 | block |
|------|--------|
| `driver` | `note` mute |
| `frames[0]` | `media` hero，`placeholder` |
| `frames` | `list` ordered，items.title |
| `titleDraft` | `section`「标题草稿」 |
| `modules` | `section`「详情大纲」+ `list` 或正文列举 |
| `assumptions` | `note` `kind: assumptions` |

策划阶段 **不要求**「详情标题/正文/展示说明」三 section；Listing 手机壳可仅在执行稿后启用（或策划也只走通用文档流）。

### 4.3 执行成果（确认后）

| 字段 | 说明 | 必填 |
|------|------|------|
| 继承 | `templateId` / `driver` / `frames` / `modules` / `titleDraft`（可微调） | ✓ |
| `detailTitle` / `detailBody` / `displayNotes` / `heroPlan` | 上架四字段（SKU 门禁） | ✓ |
| `framePrompts[]` | 与 frames 对齐：`{ prompt, negative? }` | ✓ |
| `mediaObjectIds` | 系统挂载后 | settle 前 ✓ |
| `assumptions` | 可选 | |

**执行 `view` 映射**

| 字段 | block |
|------|--------|
| `heroPlan` | `media` hero（+ `mediaObjectId`） |
| `frames` | `list` 分镜 |
| `detailTitle` | `section`「详情标题」 |
| `detailBody` | `section`「详情正文」 |
| `displayNotes` | `section`「展示说明」mute |
| `framePrompts` | **一条** `section`「生图 Prompt」摘要；全文以 artifact 为准 |
| `driver` | 可选 `note` |

现有 FE Listing 识别依赖：hero `media` + 上述三 `section` → 可继续用平台手机壳。

---

## 5. ask_human + billing timing（§3）

### 5.1 工具参数

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

| 用户动作 | 系统 |
|----------|------|
| `confirm_execute` | 续写执行稿 → 占位图 → settle 执行 1 |
| `supplement` + 说明 / 仅自由文本 | 改策划 → 再 ask_human；不扣执行分；不重复扣策划分 |
| （无「只要策划到此结束」默认项） | 若产品后续要加，另开选项 id |

### 5.2 积分表

| 情况 | 积分 |
|------|------|
| 策划失败 | 0；release |
| 策划成功（门闩 A） | 扣 1；其后补充需求不另扣策划分 |
| 确认 → 执行成功 | 再扣 1（共 2） |
| 确认 → 执行失败 | 策划分不退；执行 hold release |
| 挂起取消 / 超时 | 策划分若已 settle 不退；执行 hold release |

### 5.3 与 Spine 的关系

- AD-4：`human_input_required` 用于挂起；**执行** settle 不在等待期间发生。  
- **刻意修订操作化：** 策划 settle 安排在 **第一次** `ask_human` **之前**（门闩 A），以便「只逛策划、多次补充」时策划分已入账，且不把策划扣费拖到 HITL 之后。  
- AD-S12/S13：依赖 `ask_human` 工具、MySQL checkpoint resume、FE 选项 UI；若故事 2.9 未齐，本设计实现时一并交付或标明阻塞。

### 5.4 预占建议

**推荐：** 开始时 `reserve` 2（或两个 hold）。  
**可落地折中：** 先 `reserve` 1 完成策划 settle；用户点确认后再 `reserve` 1 跑执行（仍同 session / 同逻辑 Run 续跑需与 AgentSession resume 模型对齐——实现计划里二选一钉死）。

---

## 6. Skill / persist 影响（实现时展开）

1. `ecommerce-skulist` SKILL：Workflow 改为「先策划 JSON+view → ask_human → 确认后执行 JSON+view」；禁止未确认先写满 Prompt。  
2. Persist：策划成果类型（新 `listing_plan` **或** `sku` + `phase=plan`）与执行 `sku` 门禁分离；执行仍要求四字段 + `mediaObjectIds`。  
3. SSE：允许同 Run 两次 `artifact_ready`（策划 / 执行）或第二次覆盖；FE 以最新 view 为准。  
4. 平台切换：执行稿后启用；文案不拆四套。

---

## 7. Open points（实现计划钉死）

1. 双 hold API vs 确认后再 reserve 的最终选型。  
2. 策划 artifact_type 命名与历史回显标题。  
3. 补充需求是否允许改 `templateId`（默认否）。  
4. `ask_human` / resume FE 与故事 2.9 的依赖切片。

---

## 8. Spec self-review

| 检查 | 结果 |
|------|------|
| 占位符 / TBD | §7 显式 open points，无「某某处再写」悬空句 |
| 与「只要 Prompt」一致 | ✓ 无真出图 |
| 与「通用组件」一致 | ✓ 映射表完整 |
| 与「双次扣分 + ask_human」一致 | ✓ 门闩 A 在 ask_human 前；选项确认/补充 |
| 范围 | 不含选品、不含平台四套文案 |

---

## 9. Approval

请审阅本文件。确认后进入 **writing-plans** 拆实现任务；若要改预占策略或加「到此结束」选项，在本节回复即可。
