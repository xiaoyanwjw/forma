# Generation 成果落库简化：历史宽进 + View 门禁 Settle（方案 A）

**Date:** 2026-09-27  
**Status:** accepted  
**Decision:** 方案 A — `ebus_artifact` 服务于会话/历史回显（宽进）；**凡 settle 成功（含无 Skill）都写库**并带 `artifactRef`；计费 settle 仅要求 **可投影 Computer `view`**（组件策略校验）；删除选品专用硬校验落库链。  
**Related:**  
- [`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md)  
- [`2026-09-27-picklist-marketplace-search-skill-design.md`](./2026-09-27-picklist-marketplace-search-skill-design.md)  
- Spine AD-4 / AD-5 / AD-6（本规约对 AD-5「可用成果」作操作化重解，见 §8）  

**无历史包袱假设：** 不保留「仅有业务 DTO、无 Skill `view`」的 Legacy 投影与 FE `items` 驱动 Computer 路径。

---

## 1. Problem

当前计费选品链路过重：

1. `PicklistArtifactParser` / `PicklistApplicationService` 对 8–12、四维、`非实时`、`sourceUrl` 等做**硬门禁**，与「历史只要能回显」目标不对齐。  
2. `PicklistViewProjector` + `LegacyPicklistFallbackProjector` 在无 Skill `view` 时从 DTO 拼 Computer，把平台绑死在选品形状上。  
3. `PicklistArtifactPersistPlugin` + `PicklistArtifactDTO` + Persist 命令形成平行类型栈；Listing 再复制一套成本高。  
4. 产品已定：**artifact 表 = 历史回显**；**settle = 可投影 view**；业务质量交给 Skill 提示（软约束），应用层不做选品字段硬校验。

## 2. Goal

1. **落库 = 回显**：`ebus_artifact` 挂在 run / 消息上，历史打开时用 `payload_json`（含可渲染 `view`）还原 Computer；**不**用选品字段硬校验决定能否进表。  
2. **凡 settle 成功都写库**：含无 Skill（草稿 markdown）与计费 Skill；SSE 一律带 `artifactRef`。`persistAs` **只**决定 `artifact_type` 标签，**不再**表示「none = 不写表」。  
3. **Settle 收紧在 View**：`ComputerViewResolver` / Normalize（及可选按 Skill 的组件策略）得到合法 `ComputerDocument` 后才 settle（AD-5 操作化）。  
4. **删除选品专用硬校验落库类型栈**（§6）；FE **只渲染 `view`**。  
5. 解析器定名 **`GenerationOutputParser`**（薄拆信封，不做业务字段校验）。  
6. 落库定名具体类 **`ArtifactPersistPlugin`**（+ 可选薄 `ArtifactWriter`）：按 `artifact_type` 写库，不内嵌选品规则。**不再**定义同名 SPI 接口；Agent 直接注入该类（或 Writer）。

## 3. Non-goals

- 本文件不强制改 Spine 全文（落地前用本规约 + 修订记录；必要时另开 course-correction）。  
- 不删除 `search_sku` 工具或 SKILL 写作指引（软约束仍在）。  
- 不在本规约定「≥1 次 `search_sku`」是否保留为 settle 附加门——见 §7.3（默认可保留，与选品 Validator 无关）。  
- 不做 `metadata.output` YAML 全量驱动 `SkillRunProfile`（可后置）。  
- 不引入任意 HTML / 未登记 block type。

---

## 4. 目标运行时形状

```text
reserve
  → AgentSession / 模型回合（可含 search_sku 等 tool）
  → 终态文本
  → GenerationOutputParser
        · rawView: Map（可空）
        · businessPayload: JSON 对象或整段兜底结构（可空对象）
  → ComputerViewResolver
        · Normalize(rawView)  [有 rawView 时]
        · NoSkillMarkdown    [无 Skill / skillBound=false：终态 → markdown view]
        · （禁止 LegacyPicklist）
  → 可投影 view？
        · 否 → release → run_failed（人话：无法生成可预览结果；不写 ebus_artifact）
        · 是 → ArtifactPersistPlugin（具体类，无 SPI 接口）
              · 写入 ebus_artifact（artifact_type ← persistAs 映射，见 §5.2）
              · payload_json 含投影后 view 快照 + businessPayload（见 §5.2）
              · 返回 artifactRef
           → settle → artifact_ready(view, artifactRef) + run_settled
```

**原则：**

- **先可投影，再落库，再 settle**：保证历史回显的就是用户当时看到的 Computer；投影失败不落库。  
- 落库失败（写库异常）→ release，不 settle。  
- **不再**因「不足 8 条 / 缺 sourceUrl / disclaimer 无非实时」拒绝落库或拒绝 settle（这些改由 Skill 约束；原链仍建议出现在 **view.list[].href**，由 Normalize https 规则与 FE 渲染保障预览体验）。

---

## 5. 组件合同

### 5.1 `GenerationOutputParser`

**职责：** 从模型终态文本解析双轨（或兼容单轨）输出。

| 输入 | 模型 `finalResponse` 字符串 |
| 输出 | `ParsedGenerationOutput { rawView, businessPayload, parseNotes? }` |

**行为（最小）：**

1. 尝试提取 JSON 对象（含可选 ```json 围栏）。  
2. 若存在 `view` 键且为 object → `rawView = view`。  
3. 若存在 `artifact` 键 → `businessPayload = artifact`（原样 Map/树，**不**建 Picklist DTO）。  
4. 若无信封、仅有文本 → `rawView = null`，`businessPayload` 可为 `{ "text": "..." }` 或空对象（实现二选一，须在代码注释钉死）；无 Skill 路径仍可靠 NoSkillMarkdown。  
5. **禁止**：8–12、四维前缀、`非实时` 子串、`sourceUrl` 必填等业务抛错。

### 5.2 `ArtifactPersistPlugin`（具体类） / Writer

**职责：** 在 **已得到可投影 `ComputerDocument`** 之后，把回显载荷写入 ArtifactStore；选品 / Listing / 无 Skill **共用**这一份实现。

**命名约定：** 类名即 `ArtifactPersistPlugin`；**删除**现有 `interface ArtifactPersistPlugin` SPI 与按 `persistAs` 多实现注册。

**`persistAs` → `artifact_type`（标签，非「是否写库」开关）：**

| `SkillRunProfile.persistAs` | `ebus_artifact.artifact_type` | 场景 |
|-----------------------------|-------------------------------|------|
| `picklist` | `picklist` | 计费选品 |
| `sku`（未来） | `sku` | Listing 等 |
| `none`（无 Skill） | `chat` | 草稿 / 闲聊 markdown 回显 |

> `none` **仍要写库**；映射为类型标签 `chat`，便于历史列表筛选。实现可用常量 `ARTIFACT_TYPE_CHAT = "chat"`，勿再把 `none` 写成「跳过 Persist」。

| 字段 | 规则 |
|------|------|
| `biz_id` | 新 UUID = `artifactRef` |
| `artifact_type` | 上表映射；**禁止**因 `persistAs=none` 跳过写入 |
| `payload_json` | **回显载荷（钉死）**：`{ "view": <投影后 ComputerDocument>, "data": <businessPayload> }`。无 Skill 时 `data` 可为 `{ "text": "..." }` 或空对象 |
| `title` | 摘要：优先 `view` 语义 title / 首个 markdown 截断；禁止依赖选品 item[0].title 硬编码逻辑作为唯一路径 |

**SSE `artifact_ready`：** **一律**带投影后的 `view` + `artifactRef`（含无 Skill）；**不要求**再下发完整 `items[]` 业务包（历史从库读 `payload_json.view`）。

### 5.3 View 策略 = 前端组件合同门禁

沿用双轨规约 §1 白名单与现有策略模式：

| 策略 | 何时 |
|------|------|
| **NormalizeViewProjector** | 有 `rawView`：校验 `version===1`、已知 `blocks[].type`、清洗 `list.href`（仅 https）等；**失败 / 空文档 = 不可投影** |
| **NoSkillMarkdownProjector** | 无 Skill：终态 → 单 `markdown` block |
| **LegacyPicklistFallbackProjector** | **删除** |

可选后置：**按 Skill 的 ViewContractStrategy**（例如 picklist 建议含 `list`；listing 建议含 `media`）——仍校验**组件组合**，不校验业务 DTO 字段。近端可不做，仅 Normalize 白名单即可 settle。

### 5.4 FE

- Computer **只**吃 `ComputerDocument`（`ComputerRenderer` + `computerView.ts`）。  
- 删除「有 `items` 无 `view` 仍开旧 pick-list DOM」分支。  
- 历史回显：读 artifact 载荷中的 `view`（或约定结构）→ 同一 Renderer。

---

## 6. 移除清单（目标态）

### 6.1 后端删除

| 类型 / 类 | 说明 |
|-----------|------|
| `PicklistViewProjector` | 由 Skill `view` + Normalize 取代 |
| `LegacyPicklistFallbackProjector` | 无包袱 |
| `PicklistArtifactPersistPlugin` | → 具体类 `ArtifactPersistPlugin` |
| `interface ArtifactPersistPlugin` | **删除** SPI；不再按 `persistAs` 多实现注册 |
| `PicklistApplicationService` | 硬校验写库删除 |
| `PicklistArtifactParser` | → `GenerationOutputParser` |
| `PicklistArtifactDTO` | 不再需要 |
| `PersistPicklistCommand` / `PersistPicklistItemCommand` | 不再需要 |
| 依赖上述的专用测试 | 改为 Parser/Writer/Normalize/Agent 通用测 |

`PicklistDefaults`：若仅 Skill/文案引用可删或缩为非门禁常量；禁止再作为 settle/persist 硬门。

### 6.2 前端删除 / 收缩

| 项 | 说明 |
|----|------|
| `PicklistArtifactPayload.items` 驱动预览 | view-first |
| `useAgentPicklistRun` 无 view 有 items 的兼容 | 删除 |
| Workspace 旧 pick-list 布局分支 | 删除 |

### 6.3 保留

| 项 | 说明 |
|----|------|
| `ArtifactRepository` / `ebus_artifact` | 历史存储 |
| 具体类 `ArtifactPersistPlugin` | 唯一落库入口；无接口、无按类型插件列表 |
| `NormalizeViewProjector` / `NoSkillMarkdownProjector` / `ComputerViewResolver` | settle 门禁 |
| `SkillRunProfile` / Agent 编排 / CreditHold / SSE | 骨架保留；`persistAs=none` 改为「类型=chat 仍落库」 |
| `search_sku` + SKILL 软约束 | 产品质量 |
| `ComputerRenderer` 组件白名单 | FE 合同 |

---

## 7. 与其它规约的关系

### 7.1 双轨 Computer 合同

仍有效：Skill 主产出 `view`；业务包 `artifact` 为次轨（无 Skill 通常无业务包，仍有投影 `view`）。  
**修订点：**  
1. 「可用 artifact 业务校验后 settle」→「**可投影 view 后 settle**」。  
2. 落库改为**历史回显宽进**，且与 settle 对齐：**先 project → 成功再 persist → settle**；投影失败不落库。  
3. 双轨文中「无 Skill：`artifact_ready(view)` 无 `artifactRef`」**作废**——无 Skill 成功路径同样写 `ebus_artifact`（`artifact_type=chat`）并带 `artifactRef`。

### 7.2 选品 search_sku 设计

仍要求 Skill 流程、原链进 **`view.list[].href`**、工具 `detailUrl`。  
**修订点：** 应用层**不再**用 `sourceUrl` / 条数硬校验拒绝 settle；预览跳转依赖 Normalize + FE 对 `href` 的 https 规则。SKILL 正文可继续要求 `sourceUrl` 与 `href` 对齐（模型软约束）。

### 7.3 `search_sku` 次数门禁

与本简化正交。推荐近端**保留**「计费选品 ≥1 次成功 `search_sku`」（空 hits = 失败）作为质量门，实现位置仍在 `AgentApplicationService`，不依赖已删 Parser。

---

## 8. AD-5 操作化（方案 A）

| 旧操作化（选品） | 新操作化（A） |
|------------------|---------------|
| 业务 Validator 通过的 Picklist 形状 = 可用成果 | **可投影 ComputerDocument** = 可用成果（settle 条件） |
| 不合格业务 JSON → 不落库、不 settle | 可投影则**落库 + settle**（宽进，含无 Skill→`chat`）；不可投影 → **不落库、不 settle** |
| Legacy DTO→view | **禁止** |

CreditLedger 仍是唯一积分写入者；预占 / 失败 release 不变。

---

## 9. Acceptance

- [x] 1. 计费选品成功路径：库中有 `ebus_artifact`（`artifact_type=picklist`）；SSE 带可渲染 `view` + `artifactRef`；发生 settle。  
- [x] 2. **无 Skill 成功路径**：库中有 `ebus_artifact`（`artifact_type=chat`）；`payload_json.view` 为 markdown Computer；SSE 带 `view` + `artifactRef`；发生 settle。（`streamGenerationRunNoSkillSettlesOnUsableMarkdownView`）  
- [x] 3. 有模型终态且不可投影：不 settle、**不写** artifact；**不**因缺 8–12 / 缺 `sourceUrl` 单独失败（若保留 search_sku 门，仅因该门失败）。  
- [x] 4. 代码库中不再存在 §6.1 所列类；Agent **无**「`persistAs=none` 跳过落库」分支。  
- [x] 5. FE Computer 仅 `ComputerRenderer`；历史回显读 `payload_json.view`；无 items 旧布局。  
- [x] 6. `GenerationOutputParser` / `ArtifactPersistPlugin` 有单测；Normalize 失败不 settle、不落库有 Agent 测。

## 10. 落地顺序（建议）

1. 定稿本规格（用户审阅）。  
2. 实现 `GenerationOutputParser` + 具体类 `ArtifactPersistPlugin`（或 Writer）；删 SPI 接口与 `Picklist*` 插件；Agent 改为 **project → persist → settle**，`none→chat` 仍写库。  
3. 去掉 Legacy 链与 Picklist 硬校验类；改测试（含无 Skill 落库断言）。  
4. FE view-only；历史读 `payload_json.view`；删 items 兼容。  
5. 更新双轨规约（废「无 Skill 无 artifactRef」）与 search_sku /（可选）Spine AD-5 注释。  
6. 回归：选品回合、无 Skill 回显、search_sku 门（若保留）。

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-27 | 初稿：方案 A；`GenerationOutputParser`；Default 落库；删除 Picklist 硬校验栈与 Legacy 投影 |
| 2026-09-27 | 落库定名具体类 `ArtifactPersistPlugin`；删除同名 SPI 接口与多实现注册 |
| 2026-09-27 | **回显对齐**：凡 settle 成功都写 `ebus_artifact`（含无 Skill→`artifact_type=chat`）；`persistAs` 只作类型标签；顺序改为 project → persist → settle；废「none 跳过落库 / 无 artifactRef」 |
| 2026-09-27 | Status → **accepted**；§9 验收勾选（Tasks 1–5 落地 + Task 6 规约修订） |
