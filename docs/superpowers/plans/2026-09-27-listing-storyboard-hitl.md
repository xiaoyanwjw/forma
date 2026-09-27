# Listing 策划分镜 + ask_human HITL — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Listing 同 Run 内先出策划分镜并扣 1 分，经 `ask_human`（确认 / 补充需求）后续写执行稿+Prompt 再扣 1 分；Computer 只用通用 blocks。

**Architecture:** 扩展 `ecommerce-skulist` 两阶段输出；新增 `ArtifactType.LISTING_PLAN` 与门闩 A settle；实现 `ask_human` + SSE `human_input_required` + resume API/UI（原 2.9 切片）；确认后再 `reserveOne` 执行 hold（顺序预占，避免双 hold 改账本）。执行仍走 SKU 门禁 + 占位主图挂载。

**Tech Stack:** Java 8 / Spring Boot 2.7（`lippi-ai-ebus-application` + `lippi-pi-agent`）、Vue3 / Vitest（`lippi-ai-ebus-web`）、MySQL checkpoint resume（已有 2.8）

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-27-listing-storyboard-hitl-design.md`（accepted）
- Dual-track Computer: 仅 `note|list|markdown|media|section`
- **Hold 选型（钉死）：** 开始 `reserveOne`→门闩 A settle 策划；`confirm_execute` 时再 `reserveOne` 执行 hold；补充需求不另占
- **artifact_type（钉死）：** 策划=`listing_plan`；执行=`sku`
- **补充需求：** 不得改 `templateId`（仍 `domestic-generic-default`）
- **ask_human 选项：** `confirm_execute` | `supplement`；`allowFreeText: true`
- 不真出图；无 `platformCopies`；无 Computer 自造确认按钮
- AD-5：可用成果落库后才 settle；挂起期间不 settle **执行** 分
- 密钥 / 模型仍只经后端

## File map

| Path | Responsibility |
|------|----------------|
| `.../artifact/model/ArtifactType.java` | 增 `LISTING_PLAN("listing_plan")` |
| `.../agent/support/SkillRunProfile.java` | `PERSIST_LISTING_PLAN`；listing 两阶段 profile 标记 |
| `.../agent/support/ArtifactPersistPlugin.java` | plan 门禁（短字段）vs sku 门禁（四字段+media） |
| `.../scenes/ecommerce/ecommerce-skulist/SKILL.md` + `references/output.md`（+test mirrors） | 两阶段 workflow + ask_human |
| `lippi-pi-agent/.../tool/AskHumanToolHandler.java`（或 ebus 注册） | 工具 `ask_human`；挂起 |
| `.../sse/PiEventToAd4Mapper.java` | 映射 `human_input_required` |
| `.../agent/service/AgentApplicationService.java` | 门闩 A/B、resume、二次 reserve |
| `.../web/.../AgentController.java` | `POST .../runs/{runId}/resume` |
| `lippi-ai-ebus-web/.../agent.ts` + types | resume API；解析 `human_input_required` |
| `.../useAgentListingRun.ts` + workspace UI | 选项按钮 + 补充文本；二次 artifact_ready |
| `ComputerRenderer.vue` | 策划标题不套 Listing 手机壳（无三 section 时走通用块） |

## Spec open points → decisions

| Open | Decision |
|------|----------|
| 双 hold vs 确认后再占 | **确认后再 `reserveOne`** |
| 策划 type 命名 | **`listing_plan`** |
| 补充改 templateId | **禁止** |
| 2.9 依赖 | **本计划含 ask_human 切片** |

---

### Task 1: Domain — `ArtifactType.LISTING_PLAN` + persist 门禁

**Files:**
- Modify: `lippi-ai-ebus-domain/.../artifact/model/ArtifactType.java`
- Modify: `lippi-ai-ebus-application/.../agent/support/SkillRunProfile.java`
- Modify: `lippi-ai-ebus-application/.../agent/support/ArtifactPersistPlugin.java`
- Modify: `.../ArtifactPersistPluginTest.java`
- Modify: 任意 `ArtifactType.fromCode` / DB 写入测试

**Interfaces:**
- Produces: `ArtifactType.LISTING_PLAN`；`SkillRunProfile.PERSIST_LISTING_PLAN = "listing_plan"`
- Produces: `requireUsableListingPlanPayload(Map)` — 校验 `templateId`、`driver`、`frames`(3–5)、`modules`(3–5)、`titleDraft`
- Keeps: `requireUsableSkuPayload` 不变（执行用）

- [ ] **Step 1: 写失败测**

```java
@Test
void persist_listingPlan_rejectsFewerThanThreeFrames() {
    Map<String, Object> p = new LinkedHashMap<String, Object>();
    p.put("templateId", "domestic-generic-default");
    p.put("driver", "痛点");
    p.put("frames", Collections.singletonList("只有一张"));
    p.put("modules", Arrays.asList("a", "b", "c"));
    p.put("titleDraft", "标题");
    assertThrows(BusinessException.class, () ->
            plugin.persist("u1", "r1", "ecommerce", "listing_plan", planView(), p));
}

@Test
void persist_listingPlan_acceptsMinimalPlan() {
    PersistedGenerationArtifact out = plugin.persist(
            "u1", "r1", "ecommerce", "listing_plan", planView(), usablePlanPayload());
    assertNotNull(out.getArtifactRef());
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl lippi-ai-ebus-application -Dtest=ArtifactPersistPluginTest -DfailIfNoTests=false test`  
Expected: 新测 FAIL（无 `listing_plan` / 无方法）

- [ ] **Step 3: 实现 enum + resolveType + requireUsableListingPlanPayload**

```java
// ArtifactType
LISTING_PLAN("listing_plan"),

// SkillRunProfile
public static final String PERSIST_LISTING_PLAN = "listing_plan";

// ArtifactPersistPlugin.resolveType
if (PERSIST_LISTING_PLAN.equals(persistAs)) {
    return ArtifactType.LISTING_PLAN;
}

// persist() 分支
if (type == ArtifactType.LISTING_PLAN) {
    requireUsableListingPlanPayload(data);
} else if (type == ArtifactType.SKU) {
    requireUsableSkuPayload(data);
}
```

`frames`/`modules`：`List` 且 size 3..5，每项 trim 非空字符串，单条长度建议 ≤80（软：超长不挡 settle）。

- [ ] **Step 4: 跑测通过**

Run: 同上  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-domain lippi-ai-ebus-application
git commit -m "$(cat <<'EOF'
feat(artifact): add listing_plan type and plan persist gate

EOF
)"
```

---

### Task 2: Skill — 两阶段 skulist 合同

**Files:**
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md`
- Modify: `.../references/output.md`
- Sync mirrors: `lippi-ai-ebus-application/src/test/resources/...`、`lippi-pi-agent/src/test/resources/...`
- Modify: `allowed-tools` 含 `ask_human`（及现有 `read_skill`）

**Interfaces:**
- Produces: skill 规定先输出策划双轨 → 调 `ask_human` → 确认后输出执行双轨；补充需求只改策划字段

- [ ] **Step 1: 重写 SKILL.md Workflow**

要点（写入文件，勿留「勿 ask_human」）：

1. 先输出策划 `{view, artifact}`（短字段，view 映射见 spec §4.2）  
2. 立刻 `ask_human`，options 与 spec §5.1 一致  
3. `confirm_execute` → 再输出执行 `{view, artifact}`（四字段 + `framePrompts`，view 见 §4.3）  
4. `supplement` / 自由文本 → 只改策划 → 再 `ask_human`  
5. 禁止未确认写满 Prompt；禁止 `platformCopies`

- [ ] **Step 2: 重写 output.md**

分「策划示例」「执行示例」两段 JSON；注明 metadata：

```yaml
metadata:
  output:
    billing: true
    persistAs: sku   # 终态执行；策划中间态由应用按 phase/listing_plan 落库（见 Task 4）
    requiresView: true
```

说明：应用层在 ask_human 前用解析到的策划 payload 以 `listing_plan` 落库；确认后的终态仍 `persistAs=sku`。

- [ ] **Step 3: 同步三份镜像**

```bash
SRC=lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist
cp "$SRC/SKILL.md" lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-skulist/
cp "$SRC/SKILL.md" lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-skulist/
cp "$SRC/references/output.md" lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-skulist/references/
cp "$SRC/references/output.md" lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-skulist/references/
```

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-starter lippi-ai-ebus-application/src/test/resources lippi-pi-agent/src/test/resources
git commit -m "$(cat <<'EOF'
docs(skill): skulist two-phase storyboard + ask_human

EOF
)"
```

---

### Task 3: Runtime — `ask_human` 工具 + `human_input_required` SSE

**Files:**
- Create: `lippi-ai-ebus-application/.../agent/tool/AskHumanToolHandler.java`（或 pi-agent tools 包，按现有 `search_sku` / `read_skill` 注册方式）
- Modify: ToolCatalog 装配（`EbusToolCatalogConfiguration` 或等价）
- Modify: `PiEventToAd4Mapper.java` + test
- Modify: `Ad4EventName`（若尚未含 `human_input_required` 则补齐；已有则只映射）

**Interfaces:**
- Tool name: `ask_human`
- Args: `question: string`, `options: [{id, label}]`, `allowFreeText?: boolean`
- Behavior: before/execute 路径触发 HITL suspend（对齐 AD-S12：`ToolLevel.HUMAN` 或专用分支）；**不**走 WRITE 审批
- SSE payload: `{ question, options, allowFreeText, toolCallId, runId }`

- [ ] **Step 1: 写 Mapper 测**

```java
@Test
void mapsAskHumanSuspendToHumanInputRequired() {
    // 构造含 ask_human 挂起的 Pi 事件 / Tool 结果
    Ad4SseEvent ev = mapper.map(...);
    assertEquals(Ad4EventName.human_input_required, ev.getName());
    assertEquals("confirm_execute", ((List<?>) ((Map) ev.getData().get("options")).get(0)) /* 按实际结构断言 */);
}
```

- [ ] **Step 2: 实现 ToolHandler + 注册 + Mapper**

参考 `ResumeRequest` tool-result 模式；挂起时 Checkpointer 已由 2.8 落 MySQL。

- [ ] **Step 3: 单测绿**

Run: `mvn -pl lippi-ai-ebus-application -Dtest=PiEventToAd4MapperTest,AskHumanToolHandlerTest -DfailIfNoTests=false test`

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(agent): ask_human tool and human_input_required SSE

EOF
)"
```

---

### Task 4: Application — 门闩 A/B + resume + 顺序预占

**Files:**
- Modify: `AgentApplicationService.java`（listing 流）
- Modify: `GenerationRunContext` 或等价：增加 `planSettled`、`execHoldId`、`planArtifactRef`
- Create/Modify: Web `AgentController` resume 端点
- Modify: `AgentApplicationServiceTest.java`（listing HITL 场景）

**Interfaces:**
- `POST /api/v1/agent/runs/{runId}/resume` body: `{ toolCallId, optionId?, freeText? }`
- 门闩 A：解析到可用策划（view+plan 字段）且即将/已经 `ask_human` → `persist(listing_plan)` → `settle(planHold)` → emit `artifact_ready`（策划）→ **不** `run_settled`
- 门闩 B：resume=`confirm_execute` → `reserveOne` execHold → 续跑 → 执行 payload + mount media → `persist(sku)` → `settle(execHold)` → `artifact_ready` + `run_settled`
- resume=`supplement` → 不 reserve；续跑改策划；可再次门闩 A **不**重复 settle（`planSettled==true`）

- [ ] **Step 1: 写失败测（编排）**

```java
@Test
void listing_planGate_settlesOnce_thenHumanInputRequired() { /* fake agent: plan json then ask_human suspend */ }

@Test
void listing_confirm_reservesExec_andSettlesSku() { /* resume confirm → sku settle */ }

@Test
void listing_supplement_doesNotSettleExec() { /* resume supplement → no second settle */ }
```

- [ ] **Step 2: 跑测 FAIL**

- [ ] **Step 3: 实现编排**

伪代码（落入 `streamBilledRun` listing 分支或专用 `streamListingStoryboardRun`）：

```java
// prepare: holdId = reserveOne(); context.planSettled = false;
// onAgentTurn / onTool: if (!planSettled && looksLikePlan(parsed)) {
//   persist listing_plan; settle(holdId); planSettled=true; emit artifact_ready; holdId=null;
// }
// on suspend ask_human: emit human_input_required; return (stream keeps open or client reconnects per existing SSE design)
// resume(confirm): execHold = reserveOne(); setHoldId(execHold); agentSession.resume(...);
//   on complete exec: mount; persist sku; settle(execHold); artifact_ready; run_settled
// resume(supplement): agentSession.resume(...); // may ask_human again
```

若现有 SSE 在 suspend 时关闭连接：文档约定 FE 在 `human_input_required` 后停读；resume 用**新 SSE** 或同步返回后续事件——实现时与现有 `AgentSession.resume` 行为对齐，并在本 Task 测里钉死一种（推荐：**resume 端点返回 SSE 续流**）。

- [ ] **Step 4: 测绿 + Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(agent): listing plan settle, ask_human resume, exec reserve

EOF
)"
```

---

### Task 5: FE — `human_input_required` UI + resume + 双次 artifact

**Files:**
- Modify: `lippi-ai-ebus-web/src/types/business/agent.ts` — 事件名与 payload 类型
- Modify: `lippi-ai-ebus-web/src/api/business/agent/agent.ts` — `resumeGenerationRun(...)`
- Modify: `useAgentListingRun.ts`
- Modify: `EcommerceWorkspacePlaceholder.vue` — 选项条 + 补充输入
- Test: `agent.flow.test.ts` / workspace 测（择一）

**Interfaces:**
- `resumeGenerationRun({ runId, toolCallId, optionId?, freeText?, signal })` → 续读 SSE
- UI：收到 `human_input_required` 渲染两按钮；选「补充需求」展开 textarea，提交时 `optionId=supplement`

- [ ] **Step 1: 写 FE 测**

断言：事件到达后出现「确认，出执行稿」「补充需求」；点击确认调用 resume 且 body 含 `confirm_execute`。

- [ ] **Step 2: 实现 API + composable + UI**

- [ ] **Step 3: `artifact_ready` 两次时 `liveListing` / Computer 用最新 `view`；策划阶段无三 section 时不显示平台手机壳（沿用现有 `isListingPreview` 启发式即可）**

- [ ] **Step 4: `npm test` 相关文件绿 + Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(web): ask_human options UI and listing resume SSE

EOF
)"
```

---

### Task 6: 回归与手工冒烟

**Files:** 无新文件；更新 spec status 已 accepted

- [ ] **Step 1: 后端**

```bash
mvn -pl lippi-ai-ebus-starter -am -Dtest=ArtifactPersistPluginTest,AgentApplicationServiceTest,PiEventToAd4MapperTest -DfailIfNoTests=false test
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 前端**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/components/business/computer/ComputerRenderer.test.ts src/api/business/agent/agent.flow.test.ts
```

- [ ] **Step 3: 手工**

1. 启动后端 + web；积分 ≥2  
2. 「生成上架素材」→ Computer 出现分镜 list → 聊天出现 ask_human 选项 → 余额 -1  
3. 补充需求 → 分镜更新 → 再次选项 → 余额不变  
4. 确认 → 执行稿三 section + Prompt 摘要 → 余额再 -1 → 可切淘/闲/抖壳  

- [ ] **Step 4: Commit 仅当有修复**

---

## Plan self-review

| Spec 项 | Task |
|---------|------|
| 策划短字段 + view 映射 | T1 + T2 |
| 执行四字段 + framePrompts + view | T2 + T4 门闩 B |
| ask_human 确认/补充 | T3 + T5 |
| 策划扣 1 / 执行扣 1 | T4 |
| 顺序预占 | T4（Global Constraints） |
| 通用组件 only | T2/T5；无新 block type |
| 不真出图 | T2 Boundaries |
| listing_plan type | T1 |

Placeholder scan: 无 TBD；resume SSE 形态在 T4 钉「与现网 resume 对齐并测死一种」。

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-27-listing-storyboard-hitl.md`.

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每 Task 新子代理 + 任务间审查  
2. **Inline Execution** — 本会话按 executing-plans 批量推进并设检查点  

Which approach?
