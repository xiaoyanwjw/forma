# 电商 Skill 上线门禁与打磨包 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让电商 Pack（选品 → 点候选硬交接 → Listing 策划/确认/执行）达到可上线：功能红线过关，Skill 按业界作者实践做质量打磨，并用人工 scorecard 放行。

**Architecture:** 权威 Skill 在 `forma-starter/src/main/resources/scenes/ecommerce/`；FE 在 Computer 选品 list 上增加「做上架素材」，用 `view.list.items[].id` + 标题/原链组装 listing 提示词并同 `sessionId` 调 `streamListingRun`；Skill 侧补 item id、handoff 必填与质量原则/好坏对照；eval 文档支撑人工放行。不改积分账本语义、不接生图/上架 API、不做 CI 自动评模型。

**Tech Stack:** Vue3 / Vitest（`forma-web`）、Java 8 / Spring Boot 2.7（Pack 由 starter classpath 注册）、Markdown Skill / references

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-29-ecommerce-skill-launch-bar-design.md`（accepted）
- 黄金路径：同会话选品 → 点候选 → skulist 策划 → ask_human → 执行；禁止用手打商品名「作弊」验收 H1
- Open points 钉死：`items[].id` = `pl-1` 顺序号；交接缺字段 **FE 主拦 + skill Verification 双保险**；按钮文案 **做上架素材**；质量对照例先放 `output.md`，过长再拆 `quality.md`
- `artifact_ready` 仅 `view` + `artifactRef` → **list item 必须带 `id`**（与 artifact `items[].id` 同值），FE 才能交接
- Skill 权威副本：`forma-starter/src/main/resources/scenes/ecommerce/**`；改完后 **同步**  
  `forma-application/src/test/resources/scenes/ecommerce/**` 与  
  `pi-agent/src/test/resources/scenes/ecommerce/**`
- 质量层：遵循 spec §6.0（concise、progressive disclosure、自由度匹配、好坏对照）；禁止把 SKILL.md 堆成百科
- P/H/Q 红线不可豁免；不引入自动 Eval Harness

## File map

| Path | Responsibility |
|------|----------------|
| `forma-web/src/types/business/computerView.ts` | `ComputerListItem.id?` 解析 |
| `forma-web/src/utils/listingHandoff.ts` | 组装 listing 提示词；缺字段返回 null |
| `forma-web/src/components/business/computer/ComputerRenderer.vue` | list 行「做上架素材」；emit handoff |
| `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue` | 接 emit → 同 session `streamListingRun` |
| `.../ecommerce-picklist/SKILL.md` + `references/output.md` | id + 质量原则 + 好坏例 |
| `.../ecommerce-skulist/SKILL.md` + `references/output.md` | handoff + 质量原则 + 好坏例 |
| `docs/superpowers/evals/ecommerce-launch/{cases,scorecard,RELEASE}.md` | 人工评测与放行 |

## Spec → Task coverage

| Spec 块 | Task |
|---------|------|
| §5 硬交接 / H1–H2 | Task 1–3 |
| §6.2–6.3 picklist 功能+质量 | Task 4 |
| §6.4–6.5 skulist 功能+质量 | Task 5 |
| §6.0 Q\* / mirrors | Task 4–6 |
| §7 eval / 放行 | Task 7 |
| §4 门禁（人工执行） | Task 7 + 放行跑批（非代码） |

---

### Task 1: FE — `listingHandoff` 纯函数 + list `id` 类型

**Files:**
- Modify: `forma-web/src/types/business/computerView.ts`
- Create: `forma-web/src/utils/listingHandoff.ts`
- Create: `forma-web/src/utils/listingHandoff.test.ts`
- Modify: `forma-web/src/types/business/computerView.ts`（parse list item `id`；若已有 parse 测则扩 `ComputerRenderer.test.ts` 或 computerView 测）

**Interfaces:**
- Produces:
  ```ts
  export type ListingHandoffInput = {
    title: string
    href?: string
    id?: string
    niche?: string
    painPoint?: string
    angle?: string
  }
  /** 缺 title 或非 https href 或空 id → null（FE 主拦） */
  export function buildListingHandoffText(input: ListingHandoffInput): string | null
  export function lineTextByKind(lines: ComputerListLine[] | undefined, kind: string): string | undefined
  ```
- Produces: `ComputerListItem.id?: string`；parse 时保留非空 trim 字符串

- [ ] **Step 1: 写失败测**

```ts
// listingHandoff.test.ts
import { describe, expect, it } from 'vitest'
import { buildListingHandoffText } from './listingHandoff'

describe('buildListingHandoffText', () => {
  it('returns null when href missing or not https', () => {
    expect(buildListingHandoffText({ title: '垫', id: 'pl-1' })).toBeNull()
    expect(buildListingHandoffText({ title: '垫', id: 'pl-1', href: 'http://x' })).toBeNull()
  })
  it('returns null when id blank', () => {
    expect(buildListingHandoffText({
      title: '垫', id: '  ', href: 'https://item.example/1',
    })).toBeNull()
  })
  it('builds prompt with title, url, id, optional refs', () => {
    const text = buildListingHandoffText({
      title: '【优先试】硅胶沥水垫',
      id: 'pl-2',
      href: 'https://item.example/1',
      niche: '租房厨房',
      painPoint: '水渍',
      angle: '小户型',
    })
    expect(text).toContain('硅胶沥水垫')
    expect(text).not.toContain('【优先试】')
    expect(text).toContain('https://item.example/1')
    expect(text).toContain('来源选品条目：pl-2')
    expect(text).toContain('租房厨房')
  })
})
```

- [ ] **Step 2: Run 确认失败**

```bash
cd forma-web && npx vitest run src/utils/listingHandoff.test.ts
```

Expected: FAIL module not found / function missing

- [ ] **Step 3: 实现**

`ComputerListItem` 增加 `id?: string`；在现有 list item parse 处：

```ts
if (typeof raw.id === 'string' && raw.id.trim()) {
  item.id = raw.id.trim()
}
```

`listingHandoff.ts`：

```ts
const PRIORITY_MARK = '【优先试】'

export function buildListingHandoffText(input: ListingHandoffInput): string | null {
  const title = (input.title || '').replace(PRIORITY_MARK, '').trim()
  const href = (input.href || '').trim()
  const id = (input.id || '').trim()
  if (!title || !id) return null
  if (!/^https:\/\//i.test(href)) return null
  const refs = [
    input.niche?.trim() && `参考：${input.niche.trim()}`,
    input.painPoint?.trim() && `痛点：${input.painPoint.trim()}`,
    input.angle?.trim() && `角度：${input.angle.trim()}`,
  ].filter(Boolean)
  return [
    `请为商品「${title}」生成上架素材。`,
    `原链：${href}`,
    `来源选品条目：${id}`,
    ...refs,
  ].join('\n')
}
```

`lineTextByKind`：从 `lines` 找 `kind` 匹配的第一条 `text`。

- [ ] **Step 4: Run 确认通过**

```bash
cd forma-web && npx vitest run src/utils/listingHandoff.test.ts
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add forma-web/src/utils/listingHandoff.ts \
  forma-web/src/utils/listingHandoff.test.ts \
  forma-web/src/types/business/computerView.ts
git commit -m "$(cat <<'EOF'
feat(web): add listing handoff text builder and list item id

EOF
)"
```

---

### Task 2: FE — ComputerRenderer 选品行「做上架素材」

**Files:**
- Modify: `forma-web/src/components/business/computer/ComputerRenderer.vue`
- Modify: `forma-web/src/components/business/computer/ComputerRenderer.test.ts`

**Interfaces:**
- Consumes: `buildListingHandoffText`, `lineTextByKind`
- Produces: emit `listing-handoff` with `{ text: string }`（仅当 build 非 null）
- Props: 可选 `enableListingHandoff?: boolean`（默认 `false`；工作台选品成果为 true，避免 Listing 预览 list 误出按钮）

- [ ] **Step 1: 写失败测**

在 `ComputerRenderer.test.ts` 增加：挂载含 `badge: priority`、`id: 'pl-1'`、`href: https://...`、`lines` 含 niche 的 picklist view，`enableListingHandoff: true`；点击「做上架素材」断言 emit `listing-handoff` 且 `text` 含原链与 `pl-1`。  
另测：无 `id` 时按钮 disabled 或不渲染可点主按钮；`enableListingHandoff: false` 时无按钮。

- [ ] **Step 2: Run 确认失败**

```bash
cd forma-web && npx vitest run src/components/business/computer/ComputerRenderer.test.ts
```

Expected: FAIL（无按钮 / 无 emit）

- [ ] **Step 3: 最小实现**

在每个 `list` 的 `<li>` 内（外链标题旁）增加：

```html
<button
  v-if="enableListingHandoff"
  type="button"
  class="listing-handoff-btn"
  :disabled="!handoffTextFor(item)"
  @click="emitHandoff(item)"
>
  做上架素材
</button>
```

```ts
const props = defineProps<{ /* existing */ enableListingHandoff?: boolean }>()
const emit = defineEmits<{ 'listing-handoff': [{ text: string }] }>()

function handoffTextFor(item: ComputerListItem): string | null {
  return buildListingHandoffText({
    title: item.title,
    href: item.href,
    id: item.id,
    niche: lineTextByKind(item.lines, 'niche'),
    painPoint: lineTextByKind(item.lines, 'painPoint'),
    angle: lineTextByKind(item.lines, 'angle'),
  })
}
function emitHandoff(item: ComputerListItem) {
  const text = handoffTextFor(item)
  if (text) emit('listing-handoff', { text })
}
```

样式：次要链接仍为标题 `href`；主按钮用现有工作台按钮层次，避免新卡片壳。

- [ ] **Step 4: Run 确认通过**

```bash
cd forma-web && npx vitest run src/components/business/computer/ComputerRenderer.test.ts
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add forma-web/src/components/business/computer/ComputerRenderer.vue \
  forma-web/src/components/business/computer/ComputerRenderer.test.ts
git commit -m "$(cat <<'EOF'
feat(web): add picklist listing-handoff button on Computer list

EOF
)"
```

---

### Task 3: FE — 工作台同 session 发起 skulist

**Files:**
- Modify: `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue`
- Modify: `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts`

**Interfaces:**
- Consumes: Computer `@listing-handoff`；现有 `useAgentListingRun` / 启动 listing 的同一路径（与胶囊「生成上架素材」发送等价，但 text 来自 handoff）
- Produces: 请求 body 含 `skillId: ecommerce-skulist`、handoff `text`、当前 `sessionId`（来自 picklist run 或 workspace session）
- 选品 Computer：`enable-listing-handoff` 仅在当前 Computer 展示 **picklist** 成果时为 true

- [ ] **Step 1: 写失败测**

在 workspace 测中：mock 选品 SSE 出带 `id`/`href` 的 list → 点击「做上架素材」→ 断言 `billedRunApiHits(..., 'ecommerce-skulist')` ≥ 1，且 body `text` 含 `原链：` 与 `来源选品条目：`，且 `sessionId` 与选品 run 相同。  
另：缺 id 的条目不发起请求。

- [ ] **Step 2: Run 确认失败**

```bash
cd forma-web && npx vitest run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
```

Expected: FAIL（无 handoff 路径）

- [ ] **Step 3: 实现**

在渲染选品 Computer 处：

```vue
<ComputerRenderer
  :doc="..."
  :enable-listing-handoff="computerMode === 'picklist'"
  @listing-handoff="onListingHandoff"
/>
```

```ts
async function onListingHandoff(payload: { text: string }) {
  const text = payload.text?.trim()
  if (!text) return
  // 复用现有 startListing / enterViaListing 路径：填 text + 同 session 发送，勿 reset session
  await startListingFromText(text)
}
```

`startListingFromText` 必须传入当前 workspace `sessionId`（picklist 成功后已有的那个）。若 listing 已在 running，先忽略或按现有「运行中」守卫。

- [ ] **Step 4: Run 相关测通过**

```bash
cd forma-web && npx vitest run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts src/utils/listingHandoff.test.ts src/components/business/computer/ComputerRenderer.test.ts
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
git commit -m "$(cat <<'EOF'
feat(web): wire picklist item handoff into same-session listing run

EOF
)"
```

---

### Task 4: Pack — `ecommerce-picklist` 功能 id + 质量层

**Files:**
- Modify: `forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Modify: `forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/references/output.md`
- Sync copies after edit（Task 6 可合并；本任务先改 starter）

**Interfaces:**
- Produces: artifact `items[].id` = `pl-1`… 与 list 顺序一致；**list.items[].id 同值**（供 FE）
- Produces: description 含互斥；质量原则表 + output.md 好/坏对照；Verification 增 id / 可行动理由勾选

- [ ] **Step 1: 基线笔记（不改代码）**

用现网 skill 心智过一遍 spec §6.7 步 1：列出 3 条常见偷懒（套话优先试、niche=日用、评分条无事实）。写入 PR/提交说明或 `evals/.../RELEASE.md` 草稿「Baseline notes」小节，便于对照例对症。

- [ ] **Step 2: 改 `output.md` schema**

在 `items[]` 与 `list.items[]` 表增加：

| 字段 | 要求 |
|------|------|
| `id` | 必填；本清单内唯一；格式 `pl-{n}` 从 1 顺序；与 list 同序同 id |

示例 JSON 每条带 `"id": "pl-1"`。  
新增小节 **「质量对照（条目）」**：各 1 个好条目 / 坏条目（优先试理由、niche、评分条）。

- [ ] **Step 3: 改 `SKILL.md`**

按 spec §6.0 重组（保持低自由度门禁）：

- 重写 `description`：选品能力 + 触发词 +「Listing/主图详情请用 ecommerce-skulist」  
- Overview 一句角色：测款筛选，非搜索复读  
- Workflow 步：生成 id；筛选/排名后写质量字段  
- 新增 **Quality** 短节（原则 bullet + 链到 output 好坏例），勿贴长示例  
- Verification 增加：  
  - [ ] 每条 `id` 非空且为 `pl-n`  
  - [ ] list 与 artifact 同 id 同序  
  - [ ] 优先试含可行动「为何先测」  
  - [ ] niche ≥3 且非空泛「日用」三连  
  - [ ] 评分条挂钩可观察事实  

删重复常识段；schema 细节只指向 `references/output.md`。

- [ ] **Step 4: Q\* 自检（人工勾选写入提交说明）**

对照 spec §4.4 Q1–Q6 对 picklist 勾一遍；不通过则改到通过。

- [ ] **Step 5: Commit**

```bash
git add forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist
git commit -m "$(cat <<'EOF'
feat(pack): raise ecommerce-picklist with item ids and quality bar

EOF
)"
```

---

### Task 5: Pack — `ecommerce-skulist` handoff + 质量层

**Files:**
- Modify: `forma-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md`
- Modify: `forma-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/references/output.md`

**Interfaces:**
- Produces: Handoff 段；用户消息含「原链」或「来源选品条目」时 `picklistItemId` **必填**且与输入一致  
- Produces: 质量原则 + 策划/详情/framePrompt 好/坏对照；description 互斥选品

- [ ] **Step 1: 改 `output.md`**

- `picklistItemId`：黄金路径必填（标注：有交接输入时）  
- 策划/执行示例带非 null `picklistItemId` 与 assumptions 含原链  
- 小节 **质量对照**：`driver` 好/坏；`detailBody` 好/坏；一条 `framePrompt` 好/坏  

- [ ] **Step 2: 改 `SKILL.md`**

- `description`：上架素材 + 触发 +「选品清单用 ecommerce-picklist」  
- 新增 **Handoff**（Phase A 之前）：解析标题/原链/条目 id/参考 → assumptions + `picklistItemId`；禁止丢掉交接品名  
- **Quality** 短节：driver/frames/四字段/framePrompts 原则 + 链到对照例  
- Verification：  
  - [ ] 若输入含「来源选品条目」或「原链」→ `picklistItemId` 非空且一致  
  - [ ] frames 无同义重复  
  - [ ] detailBody 无连续 ≥2 问句开场  
  - [ ] framePrompts 非空壳「8k/杰作」  

保持 ask_human JSON 与低自由度门禁不变。

- [ ] **Step 3: Q\* 自检**

对 skulist 勾 Q1–Q6。

- [ ] **Step 4: Commit**

```bash
git add forma-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist
git commit -m "$(cat <<'EOF'
feat(pack): add skulist handoff contract and listing quality bar

EOF
)"
```

---

### Task 6: Sync skill mirrors + bootstrap 冒烟

**Files:**
- Sync:  
  `forma-application/src/test/resources/scenes/ecommerce/**`  
  `pi-agent/src/test/resources/scenes/ecommerce/**`  
  ← 与 starter 权威目录内容一致

- [ ] **Step 1: 同步**

```bash
rsync -a --delete \
  forma-starter/src/main/resources/scenes/ecommerce/ \
  forma-application/src/test/resources/scenes/ecommerce/
rsync -a --delete \
  forma-starter/src/main/resources/scenes/ecommerce/ \
  pi-agent/src/test/resources/scenes/ecommerce/
diff -qr \
  forma-starter/src/main/resources/scenes/ecommerce \
  forma-application/src/test/resources/scenes/ecommerce
```

Expected: no differences

- [ ] **Step 2: 跑相关后端测**

```bash
mvn -pl forma-starter -am -Dtest=SceneCapabilityPackBootstrapTest,SceneCapabilityPackLoaderTest -DfailIfNoTests=false test
```

Expected: PASS（或仅 skip 不存在的测名时调整为仓库内真实类名）

- [ ] **Step 3: Commit**

```bash
git add forma-application/src/test/resources/scenes/ecommerce \
  pi-agent/src/test/resources/scenes/ecommerce
git commit -m "$(cat <<'EOF'
chore(pack): sync ecommerce scene skill mirrors

EOF
)"
```

---

### Task 7: Eval 文档（cases / scorecard / RELEASE 模板）

**Files:**
- Create: `docs/superpowers/evals/ecommerce-launch/cases.md`
- Create: `docs/superpowers/evals/ecommerce-launch/scorecard.md`
- Create: `docs/superpowers/evals/ecommerce-launch/RELEASE.md`

**Interfaces:**
- Produces: E2E-01～05、NEG-01～03 可执行脚本；打分表含 P\*/H\*/B/Q\*；RELEASE 模板含模型/日期/执行人/结论

- [ ] **Step 1: 写 `cases.md`**

每条含：id、首句用户输入、期望路径、协议期望、路径期望、内容关注点。  
E2E 必须写明：**点 Computer「做上架素材」，禁止手打品名。**

- [ ] **Step 2: 写 `scorecard.md`**

表格列：case id | Q\* | P1–P6 | H1–H4 | B 四维（选品/Listing）| 笔记。  
页首粘贴放行线（与 spec §7.3 一致）。

- [ ] **Step 3: 写 `RELEASE.md`**

```markdown
# Ecommerce Pack Release

- Date:
- Model:
- Operator:
- Q* pass: yes/no
- P/H red lines: 0 failures? yes/no
- B averages:
- Verdict: SHIP / NO-SHIP
- Baseline laziness addressed in references: yes/no
- Notes:
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/evals/ecommerce-launch
git commit -m "$(cat <<'EOF'
docs(eval): add ecommerce launch scorecard and case scripts

EOF
)"
```

---

### Task 8: 人工放行跑批（非编码，门禁关闭）

**Files:**
- Update: `docs/superpowers/evals/ecommerce-launch/scorecard.md`（填本批结果）  
- Update: `docs/superpowers/evals/ecommerce-launch/RELEASE.md`（结论）

- [ ] **Step 1:** 本地起服务（模型 Key 已配），按 `cases.md` 跑 E2E-01～05 + NEG-01～03  
- [ ] **Step 2:** 填 scorecard；Q\* 先检文档再跑模型  
- [ ] **Step 3:** 若 P/H/B 不及格 → 回到 Task 4/5 只改 Pack/对照例，重跑失败用例  
- [ ] **Step 4:** SHIP 时 `RELEASE.md` Verdict=`SHIP` 并 commit

```bash
git add docs/superpowers/evals/ecommerce-launch
git commit -m "$(cat <<'EOF'
docs(eval): record ecommerce pack launch scorecard verdict

EOF
)"
```

---

## Plan self-review

1. **Spec coverage:** §5→T1–3；§6 功能/质量→T4–5；Q\*/mirrors→T4–6；§7→T7–8；§4 门禁在 T7–8 执行。  
2. **Placeholders:** 无 TBD；open points 已钉死。  
3. **一致性:** handoff 文案字段与 skill Handoff / FE `buildListingHandoffText` 对齐；`pl-n` 与 list `id` 一致。  
4. **风险:** 旧成果无 `id` 时按钮不可用（预期）；新 skill 产出后黄金路径才满血。  

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-29-ecommerce-skill-launch-bar.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每个 Task 派一个新 subagent，Task 间审查，迭代快  
2. **Inline Execution** — 本会话按 executing-plans 连续做，设检查点  

Which approach?
