# Agent Run Workspace：分步写盘 + 指针结算（设计）

**Date:** 2026-10-01  
**Status:** accepted  
**Decision:**  
- 模型用沙箱基础工具在 per-run 工作区分步写 JSON，再用 `bash`（如 `python3`）拼成最终文件  
- 结算真源 = **盘上文件**；模型终稿只给相对路径指针 `{"output":"<relpath>"}`，由 `GenerationOutputParser` 读盘后再走现有 `{view, artifact}` 双轨  
- 覆盖 **选品 + 素材**（`ecommerce-picklist` / `ecommerce-skulist`）  
- `plan/` / `exec/` 等业务子目录 **只写在 skill 文案**，不进 pi-agent 内核  
- HITL resume **同一 `runId`**，工作区目录复用  

**Related:**  
- [`2026-09-27-generation-artifact-persist-simplify-design.md`](./2026-09-27-generation-artifact-persist-simplify-design.md)（`GenerationOutputParser` / project → persist → settle）  
- [`2026-09-27-listing-storyboard-hitl-design.md`](./2026-09-27-listing-storyboard-hitl-design.md)（素材策划 HITL / 同 run resume）  
- Spine AD-4 / AD-5（可用成果落库后再 settle）  

---

## 1. Problem

今日计费 skill 要求模型在**一轮对话终稿**里直接吐出完整 `{view, artifact}`（常带围栏）。大 JSON 易截断、难分步自检，也不利于素材「先策划文件、再执行文件」的同 run 多段成果。

仓内尚无通用「按 run 隔离的工作目录 + 读写/命令」工具；`GenerationOutputParser` 只吃终稿字符串。

## 2. Goal

1. 每个 billed run 有隔离工作区：`{workspaceRoot}/sessions/{sessionId}/{runId}/`。  
2. Agent 底层只提供沙箱三件套：`read_file` / `write_file` / `bash`。  
3. Skill 用工具分步落盘并拼出最终 JSON；终稿输出指针 `{"output":"<相对 run 根的路径>"}`。  
4. 应用层读指针文件 → 现有双轨解析 → Computer 投影 → persist → settle。  
5. HITL（素材）同 `runId` 复用目录；业务子路径由 skill / ebus Interceptor 约定。  
6. `metadata.output` **不**新增路径字段；继续只管 `billing` / `persistAs` / `requiresView`。

## 3. Non-goals

- 不在 pi-agent 内核认识 `plan/`、`exec/`、`merge_json`、`write_json`。  
- 不把工作区当长期制品库（真源仍是 `ebus_artifact` + DB）；盘只服务当次 run 生成。  
- 不引入前端直连工作区或 OSS 同步工作区。  
- 不在本规约定 bash 完整安全加固清单的最终产品化（实现须有超时/输出截断/路径沙箱；禁网等按现网能力渐进）。  
- 不强制立刻删除「终稿内联完整 `{view,artifact}`」的兼容路径（过渡期保留，见 §5.3）。

---

## 4. 目标运行时形状

```text
prepareGenerationRun → runId
  → 确保工作区目录存在；ToolContext 注入 workspace 根（模型不可改）
  → AgentSession turn（可 HITL suspend/resume，同一 runId）
        · write_file / read_file / bash 在 run 根下分步写拼
  → 终态 OK：finalResponse ≈ {"output":"final.json"}   # 或 plan/final.json 等
  → GenerationOutputParser
        · 若根对象仅/含指针 output（字符串相对路径）
              → 校验路径落在 run 根内 → 读文件 → 再双轨拆 view/artifact
        · 否则：按今日规则解析终稿内联 JSON（过渡兼容）
  → ComputerViewResolver → ArtifactPersistPlugin → settle
  → 终态 OK settle 成功后删除该 runId 工作区目录（失败则保留排障；挂起策划 settle 不删）
```

**原则：**

- **盘上文件是 JSON 真源**；终稿指针只负责指路。  
- **底层无业务目录名**；选品用根下 `final.json`，素材用 skill 约定的 `plan/final.json` / `exec/final.json`。  
- Suspend **不**删盘；策划挂起 settle **不**删盘；只有 **run 终态 OK** 的 settle 成功才删盘。  
- 可用成果门闩（OK / 策划段 Interceptor）才读指针文件结算。

---

## 5. 组件合同

### 5.1 工作区布局

| 项 | 约定 |
|----|------|
| 根 | `EBUS_WORKSPACE_ROOT`，缺省 `~/.ebus`（实现可用 `user.home` + `.ebus`） |
| Run 目录 | `{root}/sessions/{sessionId}/{runId}/` |
| 相对路径 | 工具与 `output` 指针一律相对 **该 run 目录** |
| 生命周期 | `prompt` / `resume` 前 `mkdirs`；**仅终态 OK 且本 run 完全结束**的 settle 成功后删除 run 目录；挂起路径策划 settle **不**删盘；失败保留；可选日后 session 级扫残留 |

### 5.2 基础工具（pi-agent / ebus 注册，沙箱）

| 工具 | 入参（概念） | 行为 |
|------|----------------|------|
| `write_file` | `path`（相对）、`content`（文本） | 写入 run 根下；自动建父目录 |
| `read_file` | `path`（相对） | 读文本；过大可截断并注明 |
| `bash` | `command`（字符串） | cwd = run 根；超时 + stdout/stderr 截断 |

**路径守卫（硬规则）：**

- 规范化后必须落在当前 run 根下  
- 禁止绝对路径、`..`、symlink 逃逸  
- `sessionId` / `runId` / workspace 根由运行时注入，**模型不能改根**

**Skill 步骤示例（文案约定，非内核 API）：**

1. `write_file` → `artifact.json`  
2. `write_file` → `view.json`  
3. `bash` → `python3 -c '...'` 拼出 `final.json`（或 `plan/final.json`）  
4. 终稿输出 `{"output":"final.json"}`

### 5.3 `GenerationOutputParser` 扩展

**新增指针分支（优先于「整包当业务载荷」误伤）：**

1. 从终稿提取 JSON 对象（含围栏；候选策略与今日一致，偏后段）。  
2. 若存在字符串字段 **`output`**，且值为相对路径：  
   - 解析为 run 根下文件；越界 / 不存在 / 非 UTF-8 文本 → 解析失败（上层 release，不 settle）。  
   - 读取文件全文，再对该全文走**现有**双轨规则（`view` / `artifact` / 文本兜底）。  
3. 若无可用 `output` 指针 → **保持今日行为**（内联 `{view,artifact}` 或纯文本），作过渡兼容。  

**调用方：** `AgentApplicationService`（及素材 Interceptor 等）在 parse 时传入当前 run 的工作区根（或 `PathResolver`）；Parser 本身仍不做选品字段硬校验。

**指针形态（钉死）：**

```json
{"output":"final.json"}
```

- 字段名固定为 `output`（字符串）。  
- 不要在指针对象里再塞整份 `view`/`artifact`（有则忽略指针优先读盘，或实现选「有 output 则只信盘」——推荐 **有合法 `output` 则只信盘**）。

### 5.4 `metadata.output`（不变职责）

```yaml
metadata:
  output:
    billing: true
    persistAs: picklist   # / listing_plan / … 现网枚举
    requiresView: true
```

**不**增加 `workspaceFinal` 或把 `metadata.output` 改成路径字符串。结算读哪由 **agent 终稿指针** 决定。

### 5.5 HITL（素材，业务层）

- 同一 `runId`、同一工作区根。  
- 策划段：skill 写 `plan/*`，终稿（或 Interceptor 触发的可用成果）指针如 `{"output":"plan/final.json"}` → settle 策划。  
- `confirm_execute` resume：继续写 `exec/*`，指针 `{"output":"exec/final.json"}`。  
- `supplement`：覆盖 `plan/*` 后重拼，再 `ask_human`；不换 runId。  
- pi-agent **只**提供工作区 + 三工具；何时 settle、指针指向哪，由 skill + ebus Interceptor 决定。

### 5.6 失败与清理

| 情况 | 行为 |
|------|------|
| 缺文件 / 路径越界 / 盘上非合法 JSON 信封 | release hold；`run_failed` 人话原因；**保留**工作区 |
| 投影失败 / 落库失败 | 同现网；保留工作区 |
| 终态 OK settle 成功（run 结束） | 删除该 `runId` 目录 |
| 挂起路径策划 settle | **不**删盘（exec / supplement 还要用） |
| Suspend | 不删盘、不 settle |

---

## 6. Skill 改动范围（近端）

| 资产 | 改什么 |
|------|--------|
| `ecommerce-picklist` SKILL + `output.md` | 分步写 `artifact.json` / `view.json` → 拼 `final.json` → 终稿只输出指针；Verification 对齐 |
| `ecommerce-skulist` SKILL + `output.md` | 策划 `plan/`、执行 `exec/`；确认前后指针不同；HITL 与现网一致 |
| allowed-tools / ToolPolicy | 放开 `read_file` / `write_file` / `bash`（与现网 ask_human / search_sku 并存） |

业务质量红线（条数、真链、ask_human 顺序等）仍在 skill 软约束 + 现有 Interceptor，本设计不放宽 settle 的 view 门禁。

---

## 7. 测试要点

1. Parser：指针 → 读临时目录文件 → 得到 rawView / businessPayload。  
2. Parser：越界路径 / 缺文件 → 失败语义（上层不 settle）。  
3. Parser：无指针时内联 JSON 仍通过（兼容）。  
4. 工具：相对路径写入；`..` / 绝对路径拒绝。  
5. HITL：suspend 后目录仍在；resume 同 runId 可继续写；策划/执行各读对应指针文件。  
6. Settle 成功后目录删除；失败保留。

---

## 8. 实现分期（建议）

1. **Workspace + 三工具 + ToolContext 注入**（可先单测沙箱）。  
2. **`GenerationOutputParser` 指针读盘** + Agent 接线。  
3. **改 picklist skill** 走指针路径。  
4. **改 skulist skill** + Interceptor 对齐 plan/exec 指针。  
5. **清理策略** 与集成冒烟。

---

## 9. Open questions（非阻塞）

- `bash` 禁网 / 可用解释器白名单的严格程度（实现阶段按环境能力定最小集）。  
- 工作区磁盘配额与单文件大小上限数值。  
- 过渡期结束后是否删除「终稿内联大 JSON」兼容（另开清理任务）。

---

## 10. Self-review

- [x] 无 `workspaceFinal` / 未把 `metadata.output` 改成路径字符串  
- [x] 与「结算读盘 + 终稿指针」用户拍板一致  
- [x] 底层工具仅为 read/write/bash；plan/exec 仅 skill  
- [x] 同 runId HITL 与清理策略写清  
- [x] 与现有 Parser → 投影 → persist → settle 顺序兼容  
- [x] Non-goals / 过渡兼容标明，避免范围膨胀  

---

## 11. Revision history

| Date | Note |
|------|------|
| 2026-10-01 | 初稿：A + 盘上真源；三件套工具；终稿 `{"output":...}`；metadata.output 职责不变 |
| 2026-10-01 | 澄清：仅终态 OK settle 删盘；挂起策划 settle 保留工作区 |
