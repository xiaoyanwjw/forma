# Computer View Protocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Computer 只渲染 `ComputerDocument.view`；选品由后端 `PicklistViewProjector` 投影进 `artifact_ready`；素材演示改用同构 `media`/`section` fixture。

**Architecture:** 业务 JSON 仍负责结算；展示层是版本化 `ComputerDocument`（`note|list|media|section`）。后端投影选品；FE `ComputerRenderer` 按 block type 分发；无 `view` 时选品走旧字段布局兜底，不在 FE 复制完整投影器。

**Tech Stack:** Java 8 / Spring（application 层投影）· Vue 3 / TypeScript · Vitest · JUnit 5

## Global Constraints

- 协议 `version` 固定为 `1`；块类型仅 `note` | `list` | `media` | `section`
- Skill / Picklist 解析落库 / settle 契约不变；不做 Listing 真结算（3.6）
- 有 `view` 只用后端（或 fixture）document；FE 不实现第二套完整选品投影
- 未知 `blocks[].type`：跳过并 `console.warn`，不白屏
- 文本纯文本展示；`media` 无 `src`/`mediaObjectId` 时显示 `placeholder`
- Spec：`docs/superpowers/specs/2026-09-26-computer-view-protocol-design.md`

## File Map

| 路径 | 职责 |
|------|------|
| `lippi-ai-ebus-application/.../computer/ComputerDocuments.java`（或 `dto/computer/`） | 不可变 view DTO + 序列化为 `Map` |
| `.../picklist/support/PicklistViewProjector.java` | `PicklistArtifactDTO` → `ComputerDocument` |
| `.../agent/service/AgentApplicationService.java` | `toArtifactReady` 挂 `view` |
| `.../PicklistViewProjectorTest.java` | 投影快照 |
| `.../AgentApplicationServiceTest.java` | `artifact_ready` 含 `view` |
| `lippi-ai-ebus-web/src/types/business/computerView.ts` | TS 协议类型 + `parseComputerDocument` |
| `.../components/business/computer/ComputerRenderer.vue` | 块分发 |
| `.../computer/blocks/ComputerNoteBlock.vue` 等（可内联同目录） | 四块 UI |
| `.../scene/ecommerceDemoFixtures.ts` | `DEMO_LISTING_VIEW` |
| `.../EcommerceWorkspacePlaceholder.vue` | Computer 只挂 Renderer |
| `.../composables/agent/useAgentPicklistRun.ts` | 解析 `data.view` |
| 对应 `*.test.ts` / `*Test.java` | 锁行为 |

---

### Task 1: Backend ComputerDocument + PicklistViewProjector

**Files:**
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/computer/ComputerDocument.java`
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/computer/ComputerBlock.java`（可用静态工厂 + `Map` 避免深层 Jackson 多态折腾；或简单 POJO + `toMap()`）
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/picklist/support/PicklistViewProjector.java`
- Test: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/picklist/support/PicklistViewProjectorTest.java`

**Interfaces:**
- Consumes: `PicklistArtifactDTO`（已有）
- Produces: `PicklistViewProjector#project(PicklistArtifactDTO) → Map<String, Object>`（可直接 SSE 序列化的 document）

- [ ] **Step 1: Write failing projector test**

```java
@Test
void projectsDisclaimerAssumptionsPriorityAndDims() {
    List<PicklistArtifactDTO.PicklistItemDTO> items = new ArrayList<PicklistArtifactDTO.PicklistItemDTO>();
    items.add(new PicklistArtifactDTO.PicklistItemDTO(
            "【优先试】硅胶垫", "19-39",
            "痛点：积水；切入：刚需；差异：多色",
            "细分：厨房；多色",
            "高｜稳", "中｜可切", "中｜友好", "低｜合规"));
    items.add(new PicklistArtifactDTO.PicklistItemDTO(
            "置物架", "29-59", "痛点：a；切入：b；差异：c",
            "细分：收纳；免打孔", "中｜x", "中｜y", "高｜z", "低｜w"));
    // pad to 8 with varied niches if needed for other tests — this unit only needs 2+ for structure
    while (items.size() < 8) {
        int i = items.size();
        items.add(new PicklistArtifactDTO.PicklistItemDTO(
                "品" + i, "19-39", "痛点：p；切入：c；差异：d",
                "细分：细分" + (i % 3) + "；x", "高｜d", "中｜c", "中｜m", "低｜r"));
    }
    PicklistArtifactDTO dto = new PicklistArtifactDTO(
            "pl-1", "run-1", "domestic-generic-default",
            "基于通用知识推断，非实时平台数据", "默认假设", items);

    Map<String, Object> view = new PicklistViewProjector().project(dto);

    assertEquals(Integer.valueOf(1), view.get("version"));
    assertEquals("选品清单", view.get("title"));
    assertEquals("已结算", view.get("status"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
    assertEquals("note", blocks.get(0).get("type"));
    assertTrue(String.valueOf(blocks.get(0).get("text")).contains("非实时"));
    assertEquals("note", blocks.get(1).get("type"));
    assertTrue(String.valueOf(blocks.get(1).get("text")).startsWith("假设："));
    assertEquals("list", blocks.get(2).get("type"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> listItems = (List<Map<String, Object>>) blocks.get(2).get("items");
    assertEquals("优先试", listItems.get(0).get("badge"));
    assertEquals("硅胶垫", listItems.get(0).get("title"));
    @SuppressWarnings("unchecked")
    List<String> tags = (List<String>) listItems.get(0).get("tags");
    assertTrue(tags.get(0).startsWith("需求 "));
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -pl lippi-ai-ebus-application -am test -Dtest=PicklistViewProjectorTest -DfailIfNoTests=false`  
Expected: 编译失败或测试找不到 `PicklistViewProjector`

- [ ] **Step 3: Implement projector**

`PicklistViewProjector.project`：
1. `disclaimer` 非空 → note `tone=mute`
2. `assumptions` 非空 → note 文本 `"假设：" + assumptions`
3. list ordered=true；每条：
   - title 以 `【优先试】` 开头 → badge=`优先试`，title 去前缀
   - lines: `"价格带："+priceBand`, reason, differentiation（有则）
   - tags: `"需求 "+demand` 等四维（字段非空才加）
4. 返回 LinkedHashMap：`version=1`, `title=选品清单`, `status=已结算`, `blocks`

用 `LinkedHashMap` / `ArrayList` 即可，无需单独 Jackson 多态。

- [ ] **Step 4: Run test — expect PASS**

Run: 同上  
Expected: BUILD SUCCESS / tests pass

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/picklist/support/PicklistViewProjector.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/picklist/support/PicklistViewProjectorTest.java
git commit -m "feat(picklist): project ComputerDocument view from artifact"
```

---

### Task 2: Wire view into artifact_ready

**Files:**
- Modify: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/service/AgentApplicationService.java`（`toArtifactReady` ~591–616；构造器注入 `PicklistViewProjector`）
- Modify: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/service/AgentApplicationServiceTest.java`

**Interfaces:**
- Consumes: `PicklistViewProjector#project`
- Produces: `artifact_ready.data.view` Map

- [ ] **Step 1: Extend settle success test to assert view**

在现有 `streamPicklistRunSettlesOnUsableArtifact`（或等价）中，找到 `artifact_ready` 事件后增加：

```java
Ad4SseEvent ready = events.stream()
        .filter(e -> e.getName() == Ad4EventName.artifact_ready)
        .findFirst().orElseThrow(AssertionError::new);
assertNotNull(ready.getData().get("view"));
@SuppressWarnings("unchecked")
Map<String, Object> view = (Map<String, Object>) ready.getData().get("view");
assertEquals(Integer.valueOf(1), view.get("version"));
assertEquals("选品清单", view.get("title"));
```

若测试用 mock `PicklistApplicationService` 返回 `sampleArtifact`，需让 **真实** `PicklistViewProjector` 进 service（`@InjectMocks` + `@Spy` 或手动 `new AgentApplicationService(..., new PicklistViewProjector())`）。按现有测试风格：给 `AgentApplicationService` 增加构造参数 `PicklistViewProjector`，测试里 `new PicklistViewProjector()`。

- [ ] **Step 2: Run test — expect FAIL**（无 view 字段）

Run: `mvn -pl lippi-ai-ebus-application -am test -Dtest=AgentApplicationServiceTest#streamPicklistRunSettlesOnUsableArtifact -DfailIfNoTests=false`

- [ ] **Step 3: Implement wiring**

```java
// toArtifactReady 末尾，在 return 前：
data.put("view", picklistViewProjector.project(artifact));
```

Spring：若当前用手写 `@Bean`/构造注入，同步改配置或仅构造器（已是构造注入则加 final 字段）。

- [ ] **Step 4: Run AgentApplicationServiceTest picklist 相关 — PASS**

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/service/AgentApplicationService.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/service/AgentApplicationServiceTest.java
git commit -m "feat(agent): attach ComputerDocument view on picklist artifact_ready"
```

---

### Task 3: FE computerView types + ComputerRenderer

**Files:**
- Create: `lippi-ai-ebus-web/src/types/business/computerView.ts`
- Create: `lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.vue`
- Create: `lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.test.ts`

**Interfaces:**
- Produces: `ComputerDocument`, `parseComputerDocument(raw: unknown): ComputerDocument | null`, `<ComputerRenderer :document="doc" />`

- [ ] **Step 1: Write failing renderer test**

```ts
import { createApp, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import ComputerRenderer from './ComputerRenderer.vue'
import type { ComputerDocument } from '@/types/business/computerView'

const doc: ComputerDocument = {
  version: 1,
  title: '选品清单',
  status: '已结算',
  blocks: [
    { type: 'note', text: '非实时说明', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: [{ badge: '优先试', title: '硅胶垫', lines: ['价格带：19-39'], tags: ['需求 高｜稳'] }],
    },
    { type: 'media', role: 'hero', placeholder: '主图方案预览' },
    { type: 'section', heading: '详情标题', body: '标题文案' },
  ],
}

describe('ComputerRenderer', () => {
  it('renders note list media section and skips unknown', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        ...doc,
        blocks: [...doc.blocks, { type: 'nope' } as never],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.textContent).toMatch(/非实时说明/)
    expect(host.textContent).toMatch(/优先试/)
    expect(host.textContent).toMatch(/硅胶垫/)
    expect(host.textContent).toMatch(/主图方案预览/)
    expect(host.textContent).toMatch(/详情标题/)
    expect(host.textContent).toMatch(/标题文案/)
    app.unmount()
    host.remove()
  })
})
```

- [ ] **Step 2: Run — FAIL**

Run: `cd lippi-ai-ebus-web && npm test -- --run src/components/business/computer/ComputerRenderer.test.ts`

- [ ] **Step 3: Implement types + renderer**

`computerView.ts`：按规格定义联合类型；`parseComputerDocument`：校验 `version===1`、`title` 字符串、`blocks` 数组；逐块按 `type` 收窄，未知 type 丢弃。

`ComputerRenderer.vue`：
- 顶栏用 `document.title` + `document.status`（与现 `comp-card-head` 对齐）
- `v-for` blocks：`note` / `list` / `media` / `section`
- `media`：有 `src` 用 `<img>`；否则 placeholder 平面（复用现 `.listing-hero` 视觉近似）
- `list`：ordered 用 `<ol>`；badge 用现有 `.priority-tag` 样式类名可迁入组件

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-web/src/types/business/computerView.ts \
  lippi-ai-ebus-web/src/components/business/computer/
git commit -m "feat(web): add ComputerRenderer for view protocol blocks"
```

---

### Task 4: Listing demo fixture as ComputerDocument

**Files:**
- Modify: `lippi-ai-ebus-web/src/views/business/scene/ecommerceDemoFixtures.ts`
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue`
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts`

**Interfaces:**
- Produces: `DEMO_LISTING_VIEW: ComputerDocument`；工作台 `computerDocument` computed

- [ ] **Step 1: Add fixture + failing test that listing Computer has no `.listing-stack`**

```ts
export const DEMO_LISTING_VIEW: ComputerDocument = {
  version: 1,
  title: '上架素材预览',
  status: '演示',
  blocks: [
    { type: 'media', role: 'hero', placeholder: '主图方案预览', alt: '主图方案' },
    { type: 'section', heading: '详情标题', body: DEMO_LISTING.title },
    { type: 'section', heading: '详情正文', body: DEMO_LISTING.body },
  ],
}
```

测试（打开 listing 演示后）：

```ts
expect(mounted.root.querySelector('.listing-stack')).toBeNull()
expect(mounted.root.textContent).toMatch(/主图方案预览/)
expect(mounted.root.textContent).toMatch(/详情标题/)
expect(mounted.root.textContent).toMatch(DEMO_LISTING.title)
```

- [ ] **Step 2: Run listing-related test — FAIL**（仍有 `.listing-stack`）

- [ ] **Step 3: Refactor workspace Computer body**

```vue
<aside ... class="computer">
  <!-- bar 不变 -->
  <div class="computer-body">
    <div v-if="activeComputerDoc" class="comp-card">
      <ComputerRenderer :document="activeComputerDoc" />
    </div>
  </div>
</aside>
```

```ts
const activeComputerDoc = computed(() => {
  if (computerKind.value === 'listing') return DEMO_LISTING_VIEW
  if (computerKind.value === 'picks') {
    if (livePicklist.value?.view) return livePicklist.value.view
    return null // Task 5 补旧字段兜底；本步选品仍可先要求 view
  }
  return null
})
```

本 Task 结束时：listing 全走 Renderer；选品若测试仍依赖旧 DOM，可暂时保留 picks 旧分支 **仅当** `!view`——但成功 SSE 测在 Task 5 改完。为减小半成品：本步 listing 必改；picks 若 `livePicklist.view` 有则 Renderer，否则旧 DOM（双轨一任务内允许，Task 5 收紧）。

- [ ] **Step 4: Run EcommerceWorkspace tests — listing 断言 PASS**

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-web/src/views/business/scene/ecommerceDemoFixtures.ts \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
git commit -m "feat(web): render listing demo via ComputerDocument fixture"
```

---

### Task 5: Parse artifact view + picklist Computer on view

**Files:**
- Modify: `lippi-ai-ebus-web/src/types/business/agent.ts` — `PicklistArtifactPayload.view?: ComputerDocument`
- Modify: `lippi-ai-ebus-web/src/composables/agent/useAgentPicklistRun.ts` — `toPicklistArtifact` 解析 view
- Modify: `EcommerceWorkspacePlaceholder.vue` / `.test.ts` — SSE mock 带 `view`；断言 Renderer 内容 / 无旧 `.pick-list` 手写结构（若 class 迁到 Renderer 内可断言文案）

**Interfaces:**
- Consumes: `parseComputerDocument`
- Produces: `PicklistArtifactPayload.view`

- [ ] **Step 1: Update SSE mock in workspace test to include view**

```ts
view: {
  version: 1,
  title: '选品清单',
  status: '已结算',
  blocks: [
    { type: 'note', text: '基于通用电商知识推断，非实时平台数据', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: SAMPLE_ITEMS.map((it) => ({
        badge: it.title.startsWith('【优先试】') ? '优先试' : undefined,
        title: it.title.replace(/^【优先试】/, ''),
        lines: [`价格带：${it.priceBand}`, it.reason, it.differentiation],
        tags: [`需求 ${it.demand}`, `竞争 ${it.competition}`, `利润 ${it.margin}`, `风险 ${it.risk}`],
      })),
    },
  ],
}
```

断言：`toMatch(/非实时/)`、`优先试`、`ComputerRenderer` 挂载；**不要**再依赖页面级手写 `.pick-list`（若 Renderer 内部仍用该类名则可保留）。

- [ ] **Step 2: Run picklist SSE test — FAIL**（view 未解析）

- [ ] **Step 3: Implement parse + prefer view in UI**

```ts
// toPicklistArtifact
view: parseComputerDocument(o.view) ?? undefined
```

```ts
// activeComputerDoc picks branch
if (livePicklist.value?.view) return livePicklist.value.view
// 旧字段兜底：保留最小旧 DOM 或返回 null+提示「未收到视图」——规格要求旧字段兜底布局。
// 实现：仅当无 view 且有 items 时，用内联 FallbackPicklistCard（复制旧模板一段），禁止叫 project()。
```

- [ ] **Step 4: Run** `npm test -- --run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts src/components/business/computer/ComputerRenderer.test.ts` — PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-web/src/types/business/agent.ts \
  lippi-ai-ebus-web/src/composables/agent/useAgentPicklistRun.ts \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
git commit -m "feat(web): consume artifact_ready.view in ecommerce Computer"
```

---

### Task 6: Verification sweep

- [ ] **Step 1: Backend**

Run: `mvn -pl lippi-ai-ebus-application -am test -Dtest=PicklistViewProjectorTest,AgentApplicationServiceTest -DfailIfNoTests=false`  
Expected: SUCCESS

- [ ] **Step 2: Frontend**

Run: `cd lippi-ai-ebus-web && npm test -- --run src/components/business/computer src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts && npm run lint`  
Expected: 全绿

- [ ] **Step 3: Manual smoke（可选）**  
登录有余额 → 选品 → Computer 为 Renderer 清单；点演示上架素材 → media+section，无 `.listing-stack`

- [ ] **Step 4: Final commit if dirty**（文档已存在则无需再改；若 Implementation Notes 要记一笔可追加到 design 底部「落地」）

---

## Spec coverage self-check

| Spec 要求 | Task |
|-----------|------|
| ComputerDocument + 四块类型 | 1, 3 |
| PicklistViewProjector 规则 | 1 |
| artifact_ready.view | 2 |
| FE ComputerRenderer | 3 |
| Listing demo → view fixture | 4 |
| 选品消费 view + 无 view 旧布局兜底 | 5 |
| 未知 type 不白屏 | 3 |
| 不做 Listing 结算 / 不改 skill | 全局约束 |

## Placeholder scan

无 TBD /「类似 Task N」式省略；关键断言与命令已写出。
