---
title: '3.4 生成选品清单并结算 1 积分'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'a4e9ffbd0dfa19313116504d2657d156c7a97a2e'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-3-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 电商工作台已有会话壳与 `ecommerce-picklist` 骨架，但真正发起选品只会空跑（预占后释放、永不结算）；没有 PicklistArtifact 落库，Computer 仍靠演示 fixture，用户拿不到可结算的「约 8–12 条带可卖理由」清单。

**Approach:** 新增计费选品生成路径：预占 1 分 → 注入填肉后的选品 skill → 模型产出结构化清单 → 可用成果落库并挂 `GenerationRun.artifactRef` → 结算 → SSE `artifact_ready`/`run_settled`；工作台发送/选品胶囊接真流，Computer 展示真实成果。空跑接口保持「永不结算」。

**Decisions:**
- 空跑 `POST .../runs/empty` 语义不变（只 release）；计费选品走新入口，不把空跑改成可 settle
- `templateId` 近端固定默认身份（无品类选择器 UI）；不新建运营模板后台
- 应用层在 `AgentSession.prompt` 成功后解析成果再落库/结算；工具不得写积分
- **选品字段（1B）**：每条含品名、建议客单/价格带、需求/竞争/利润/风险四维简评、可卖理由、差异化切入点；清单级 `disclaimer` 声明「基于通用知识推断，非实时平台数据」；禁止伪造实时平台指标数字
- **澄清（2A）**：本故事不做 `ask_human`；信息过少时优先按合理默认假设生成并在文案标明假设，仍无法形成合格结构则人话失败+release

## Boundaries & Constraints

**Always:**
- 登录 JWT；余额 ≥1 才可开始；预占 1 → 可用成果落库后 `settle`；失败/取消 `release`；禁止仅因 SSE 结束扣分（FR3, AD-5/7）
- 成功约 8–12 条候选；每条含可读可卖理由 + 四维简评 + 价格带 + 差异化点；清单含非实时数据声明；无明显违规胡编；国内通用默认风格（FR7, NFR1；无品类模板选择步骤）
- 成果必含 `templateId`，挂到当前 `GenerationRun.artifactRef`；每次生成新 Run+新预占（AD-6/7）
- 提示词/skill 正文在 classpath，按 `sceneCode=ecommerce` + `skillId=ecommerce-picklist` 加载（AD-16）
- SSE 闭合名：`run_started` | `message_delta` | `tool_*` | `artifact_ready` | `run_failed` | `run_settled`；fetch+ReadableStream+JWT
- 单次 Run 可观测模型用量/成本字段或结构化日志（NFR2）

**Never:**
- 不实现 Listing/`ecommerce-skulist` 结算（3.6）；不做超范围拉回产品化（3.5）；不做导出/质量反馈/近 60 天历史页（3.7/3.8）
- 不接真实淘宝/拼多多/闲鱼等平台数据 API；不实现 `ask_human` / `human_input_required` 选品澄清 UI（留给后续）；不在浏览器下发系统提示词/tool 定义
- Agent 工具不写 CreditLedger；不改 SceneCatalog 所有权；不把空跑改成 settle 路径
- 不做品类模板选择器 UI / CatalogTemplate 运营后台（Epic 近端明确后置）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 成功选品 | 已登录、余额≥1、电商会话；发选品意图 | 预占→流式输出→落库 8–12 条（含四维+理由+disclaimer）→settle→`artifact_ready`+`run_settled`；Computer 显示清单 | N/A |
| 信息偏少但可假设 | 用户只说「帮我选品」等 | 按国内通用默认假设生成；文案标明假设；结构仍须合格 | N/A |
| 余额不足 | available&lt;1 | 不预占、不跑模型；人话提示去套餐/升级 | 前端可见原因 |
| 生成失败/取消 | 模型失败、解析失败、用户取消 | `release`；`run_failed`；人话原因 | 不 settle |
| 成果不合格 | 条数非 8–12，或缺理由/四维/disclaimer | 视为不可用成果：release + `run_failed` | 不 settle、不假交付 |
| 空跑对照 | 调 empty 接口 | 仍只 release，永不 settle / 无 artifact | 保持 3.1 契约 |

</frozen-after-approval>

## Code Map

- `forma-starter/.../scenes/ecommerce/ecommerce-picklist/SKILL.md`（+ application/pi-agent test 镜像） — **填肉**：输出 JSON 契约、条数、理由质量规则、禁胡编/违规；`allowed-tools` 近端仍以 `read_skill` 为主
- `AgentApplicationService` / `AgentController` — 新增计费选品流（复用 prepare 场景绑定+预占模式）；**勿**让 empty 路径 settle
- `CreditApplicationService.reserveOne` / `settle` / `release` — 唯一积分写口
- `GenerationRun` + `004`/`009` SQL — `artifactRef` + `markSettled`；成本可观测挂本 Run
- **新建** Picklist 域（按 `02-be`）：`domain/.../picklist/`、`PicklistApplicationService`、`010_ebus_picklist.sql` (+ item 表)、H2 `schema-h2.sql` 同步
- `SceneCapabilityPackLoader` / `ecommerce-picklist` — 复用；默认 skill id 已是 hyphenated
- FE：`EcommerceWorkspacePlaceholder.vue`、`api/business/agent/agent.ts`、`types/business/agent.ts`、`useAgentEmptyRun`（可抽计费 composable）— 发送/选品胶囊 → 真 SSE；`artifact_ready` → Computer；替换演示假成功路径
- `ecommerceDemoFixtures.ts` — 可保留作离线演示 fallback，真跑成功后不再冒充成果
- **勿改：** Listing/skulist 结算；CreditLedger 写者边界；AD-4 事件名；EventSource

## Tasks & Acceptance

**Execution:**
- [x] `ecommerce-picklist/SKILL.md`（及测试资源镜像） -- 填肉：8–12 条、四维+价格带+理由+差异化+disclaimer、禁伪造实时数据与违规胡编 -- skill 质量（1B）
- [x] `APP-META/bootstrap/sql/010_*.sql` + H2 -- Picklist / PicklistItem 表；必含 `templateId`、user、run 关联与结构化字段列/JSON -- AD-6/7
- [x] `domain`+`application` Picklist* -- 可用成果落库校验（条数/理由/四维/disclaimer）；供 Agent 编排调用 -- 所有权边界
- [x] `AgentApplicationService` + `AgentController` -- 计费选品：预占→prompt(skillId=ecommerce-picklist)→解析落库→settle→`artifact_ready`/`run_settled`；失败 release；无 ask_human -- FR3/7 + 2A
- [x] GenerationRun 成本可观测 -- 持久化或结构化日志至少覆盖单次用量/成本占位 -- NFR2
- [x] FE 工作台 -- 选品胶囊/发送接计费 SSE；Computer 展示真清单（含四维可读呈现）；余额不足/失败人话；刷新积分 -- UX-DR5
- [x] 单测 -- 覆盖矩阵：成功 settle、余额不足、失败 release、不合格成果不 settle、empty 仍不 settle -- 锁计费

**Acceptance Criteria:**
- Given 已登录且余额≥1 的电商会话，when 发起选品生成，then 预占 1 分，成功落库约 8–12 条含四维简评与可卖理由的候选（含非实时声明），并结算 1 分
- Given 生成失败或成果不合格，when 结算判定，then 不扣或退回预占，并给出人话原因
- Given 余额不足，when 尝试开始选品，then 不可开始并提示套餐/升级
- Given 单次选品结束，when 检查可观测性，then 该次模型用量/成本可统计

## Implementation Notes

- 2026-09-26：新增 `POST /api/v1/agent/runs/picklist`；空跑仍只 release。Picklist 表 `010_ebus_picklist.sql`；skill 三处镜像已同步。
- 计费编排：prompt OK → `PicklistArtifactParser` → `persistUsable` → settle → `artifact_ready`/`run_settled`。
- NFR2：`modelUsage` 日志含 token/成本占位与 `responseChars`（TurnResult 真实用量后置贯通）。
- FE：`useAgentPicklistRun` + 选品意图启发式；Computer 展示四维；`ebus:credits-changed` 刷积分。
- 已知风险：settle 失败不自动 release；非选品文案不走计费（Listing 仍演示）。
- 评审补丁：settledOk 防 SETTLED→FAILED；disclaimer/templateId/长度守卫；newTask reset；失败也刷积分；补 settle 失败/payload/MockMvc/H2/FE body 测。
## Spec Change Log

## Review Triage Log

- `high` — settle 成功后若 `artifact_ready`/`run_settled` emit 抛错，catch 仍 `markRunFailed`，会把已 SETTLED 的 Run 改成 FAILED（账本已扣、状态矛盾）。→ patch
- `false` — settle 失败后必须 release：可用成果已落库再 release = 白嫖；当前 hold 卡住+人话联系支持符合计费语义。implementation notes 已记。
- `medium` — 客户端取消/断流时服务端仍可能跑完并 settle：`AgentSession.prompt` 无中断端口（与 empty-run 同构）。→ defer（服务端）；FE 侧 newTask 须 abort → patch
- `medium` — SSE mid-stream 失败只设 aborted、不取消 prompt：同构 empty-run 限制。→ defer
- `medium` — disclaimer 仅非空校验，未锁「非实时」语义，可结算假声明。→ patch
- `medium` — `templateId` 错误值仍可落库，违反固定默认身份。→ patch
- `low` — skill JSON 示例仅 1 条易诱导模型少输出：修示例标注即可。→ patch
- `medium` — `run_failed`/释放后不派发 `ebus:credits-changed`，顶栏可用积分陈旧。→ patch
- `medium` — `newTask` 不清 `sessionId`/不 abort，晚到的 artifact 可能重开 Computer。→ patch
- `false` — 无 picklist 读 API：近端 Computer 靠当场 SSE；回看属 3.8，意图未要求本故事做查询接口。
- `medium`（verification-gap）— settle 失败路径无测。→ patch
- `medium`（verification-gap）— credits-changed / 顶栏刷新无测。→ patch
- `medium`（verification-gap）— `artifact_ready` 仅校验事件名、未锁 payload 字段。→ patch
- `medium`（verification-gap）— `/runs/picklist` 无 MockMvc/集成测。→ patch
- `medium`（verification-gap）— Picklist MyBatis 落库无运行时测。→ patch
- `medium`（verification-gap）— FE 成功测未断言 POST body/URL。→ patch
- `low` — 字段超 VARCHAR 变 DB 错而非「不合格成果」：可加长度守卫。→ patch（persist 前校验）
- `maybe-false` — `markRunSettled` 找不到 Run 时静默：需造仓储空洞才触发；未证实日常可达。→ reject（仅会是 low）
- `false` — 意图启发式导致无法计费：选品胶囊模板含「选品清单」，主路径可达；独立「永远计费」按钮非冻结意图。
- `low` — edge「settle 前 holdClosed」：代码在 settle 成功后才 `holdClosed=true`，该时序不成立。→ false

## Design Notes

### 市面选品调研摘要（用于 skill 质量，非另开范围）

主流工具与方法论（Jungle Scout / Helium 10 / 亚马逊商机探测器 / 拼多多四问框架）共识：

1. **有人买吗（需求）**：搜索量/趋势/季节性，而非「我喜欢」
2. **挤得进吗（竞争）**：供给密度、头部集中度、评价壁垒、Listing 质量缺口
3. **赚得到吗（利润）**：售价带 − 货源/物流/平台费后的净利润空间
4. **扛得住吗（风险）**：合规/专利/退货/季节脉冲/供应链

「可卖理由」高质量写法：来自**场景痛点 / 差评缺口 / 差异化切入**，不是空喊「很火」「蓝海」。

Adam 近端**无实时平台数据**：skill 用「框架化推理 + 明确非实时声明」逼近市面分析结构（已选 1B），避免假装引用实时 BSR/生意参谋数字。本故事不做 ask_human（2A）。

### 成果 JSON 形状（契约示意）

```json
{
  "templateId": "domestic-generic-default",
  "disclaimer": "基于通用电商知识推断，非实时平台数据",
  "assumptions": "未指定品类时按国内小件家居日用测款默认",
  "items": [
    {
      "title": "硅胶沥水垫（多色）",
      "priceBand": "19–39 元",
      "reason": "厨房台面积水刚需；轻小件好发；可从颜色/厚度做差异",
      "differentiation": "多色套装 + 厚度对比主图",
      "demand": "刚需场景稳定",
      "competition": "供给多但同质，可用视觉差异切入",
      "margin": "低客单测款友好",
      "risk": "勿夸大功效；注意材质合规表述"
    }
  ]
}
```

条数须落在 8–12；缺 `reason` / 四维 / `priceBand` / `differentiation` / 清单 `disclaimer`，或条数不合 → 不可用成果。

## Verification

**Commands:**
- `mvn -pl forma-starter -am test` -- 相关后端测绿（含 settle/release 与 skill 契约）
- `cd forma-web && npm test -- EcommerceWorkspace AppHeader agent` -- 工作台/流式相关测绿
- `cd forma-web && npm run lint` -- 无新增 lint 错误

**Manual checks:**
- 登录有余额：工作台选品 → Computer 出真清单 → 积分 −1
- 余额 0：无法开始，提示升级
- 人为失败/断流：预占释放，不出现假清单
