---
title: '3.3 会话态工作台（侧栏 + 对话 + Computer）'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'a44305372957e2c40a1dcbec4d131ca1d56b714f'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-3-context.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/scene-ecommerce.html'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/manus-chat.css'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `/scenes/ecommerce` 只有空态提问壳；用户进入会话后看不到侧栏历史、主对话流，也没有生成结果后的右侧 Adam's Computer，无法边聊边确认清单/上架素材。

**Approach:** 在同一路由把工作台从空态切到会话态三栏壳（侧栏历史 + 主对话 + 有结果后 Computer split），对齐 UX mock；顶栏面包屑保持「场景 / 电商开店」。本故事只做会话壳与预览区域，不接真结算生成。

**Decisions:**
- 空态→会话：胶囊或发送仅切壳 + 本地演示消息，**不调任何生成/空跑 API**
- Computer 预览：前端内置选品清单与 Listing fixture；用页面内可见的演示控件打开（不依赖后端 `artifact_ready`）
- 侧栏：仅「新任务」+ 当前会话一项，**不做假历史列表**、不新增 listRecent REST
- 「新任务」：回到空态，关闭 Computer，清空当前演示线程

## Boundaries & Constraints

**Always:**
- 布局对齐 UX-DR5：侧栏 + 对话 +（有成果后）右侧 Adam's Computer；顶栏 UX-DR1「场景 / 电商开店」
- 窄屏：侧栏可收；Computer 不得挤死主对话可读性（UX-DR10；mock ≤860px 藏侧栏、≤1100px Computer 叠在对话下）
- Computer 展示清单条目或主图/文案类预览，不只聊天气泡
- 空态（2.5）仍可用；会话态可经「新任务」回空态；`sceneCode=ecommerce` 与面包屑契约不变

**Never:**
- 不调用 `streamEmptyRun` / 任何计费或空跑 API；不实现选品/Listing 真预占、落库、结算；不发/不等待 `artifact_ready`/`run_settled`（3.4/3.6）
- 不新增会话列表/历史 REST；不改 CreditLedger、SceneCapabilityPack、AgentController 契约
- 不做超范围拉回（3.5）、导出（3.7）、质量反馈/近 60 天历史页（3.8）
- 不改画廊可用性、登录/积分账本逻辑；不在浏览器下发系统提示词/tool 定义

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 进入会话态 | 空态点胶囊或发送 | 隐藏空态；显示侧栏+主对话+本地演示消息；顶栏面包屑不变；不发网络请求 | N/A |
| 尚无成果 | 会话态、未点演示打开 Computer | Computer 隐藏；工作区非 split | N/A |
| 有成果 | 点演示控件加载 fixture | split 打开；Computer 预览清单或主图/文案类内容 | 关闭 Computer → 取消 split，对话仍可读 |
| 窄屏 | 视口 ≤860px（或等价断点） | 侧栏收起/隐藏；主对话可读；有 Computer 时不遮死对话 | N/A |
| 新任务 | 点侧栏「新任务」 | 回空态；清空演示线程；关闭 Computer | N/A |

</frozen-after-approval>

## Code Map

- `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue` + `.test.ts` — 2.5 空态；本故事扩展空态↔会话态，或抽会话子组件同路由
- `lippi-ai-ebus-web/src/components/layout/AppHeader.vue` — 复用 `sceneBreadcrumb`；勿改全站顶栏契约
- `lippi-ai-ebus-web/src/styles/tokens.css` — 已有 `--sidebar-w` 等；会话壳样式对齐 `manus-chat.css`
- `sdd/.../mockups/scene-ecommerce.html` + `manus-chat.css` — `#session` / `.sidebar` / `.chat-pane` / `.computer` / `.workspace.split` 结构与断点金样
- `lippi-ai-ebus-web/src/types/business/agent.ts` + `api/business/agent/agent.ts` + `composables/useAgentEmptyRun.ts` — **本故事不接线**；留给 3.4+
- `lippi-ai-ebus-web/src/views/business/agent/AgentDryRun.vue` — 干跑页可留；勿把生产壳绑死 dry-run
- **勿改：** `AgentController` / `AgentApplicationService`；`CreditLedger`；画廊/`SceneCatalog`；`HistoryPlaceholder`（3.8）

## Tasks & Acceptance

**Execution:**
- [x] `EcommerceWorkspacePlaceholder.vue`（及必要子组件） -- 空态↔会话态：侧栏+对话+Computer split；胶囊/发送切壳+本地演示消息、零 API -- UX-DR5
- [x] 会话壳样式（页内或共享 css） -- 对齐 mock 断点与 Computer 开合；窄屏侧栏可收 -- UX-DR10
- [x] Computer 预览区 + 选品/Listing fixture -- 演示控件打开预览（非纯气泡） -- AC 预览
- [x] 侧栏 -- 「新任务」+ 当前会话一项；新任务回空态并清演示态 -- 会话导航
- [x] `EcommerceWorkspacePlaceholder.test.ts`（及子组件测） -- 覆盖矩阵：进会话、无/有成果、关 Computer、新任务、窄屏侧栏；断言无 API 调用 -- 锁壳

**Acceptance Criteria:**
- Given 工作台已从空态进入会话态，when 页面展示，then 布局为侧栏（新任务+当前会话）+ 主对话 +（演示打开成果后）右侧 Adam's Computer，且顶栏仍为「场景 / 电商开店」
- Given 演示 fixture 已打开，when Computer 更新，then 可见清单条目或主图/文案类预览，而非仅聊天气泡
- Given 窄屏会话中，when 布局响应，then 侧栏可收且主对话仍可读

## Implementation Notes

- 2026-09-26：在 `EcommerceWorkspacePlaceholder.vue` 扩展空态↔会话态；`ecommerceWorkspaceSession.css` 对齐 mock 断点；`ecommerceDemoFixtures.ts` 提供选品/Listing 演示数据；演示胶囊打开 Computer。
- 胶囊/发送切壳 + 本地消息，零生成 API；侧栏仅「新任务」+ 当前会话一项。
- 单测 12 绿（含矩阵：进会话、无/有成果、关 Computer、新任务、窄屏 CSS、无 agent API；评审补 listing 胶囊/会话发送/fixture 文案）。
- 评审补丁：局部 chatScroll ref；窄屏聊天条「新任务」；Computer scrollIntoView；恢复附件 sr-only；测试加强。

## Spec Change Log

## Review Triage Log

- `false` — 胶囊跳过「先填模板再编辑」：frozen Decisions 明确胶囊/发送直接切壳；旧 2.5 行为已被本故事意图取代。
- `low` — 去掉 GEN_SOON / aria-describedby：禁用附件失去读屏原因。日常有害且补丁小 → 并入 patch（恢复简短 sr-only）。
- `low` — 会话测未断言面包屑「场景」段：AC 要求「场景 / 电商开店」。→ patch（加强断言）。
- `defer` — 窄屏仅 CSS 源码正则：jsdom 无法诚实验 media-query 布局（verification-gap 同结论）。
- `low` — 声称矩阵全锁但缺会话内发送等测：真实缺口 → 并入 patch。
- `false` — Code Map 未列新文件：修复需改本 build spec，按规则拒绝。
- `medium` — `document.querySelector('.chat-scroll')` 全局查找：多根/误匹配会滚错节点。→ patch。
- `low` — 每条 agent 气泡都挂双预览按钮：演示路径按 Decisions 用可见控件开 Computer，堆叠可接受；不为「一次成果」加意图分支。
- `low` — `DEMO_LISTING.sku` / 未用 `.meta` 样式：纯死代码表面，用户无感。rejected。
- `low` — `fillPicksEmpty` 先写 prompt 再清：无用户可见后果。rejected。
- `low` — 非 split 时 chat-pane 仍 `border-right`：细微视觉，非功能缺陷。rejected。
- `low` — `.side-item.on` 硬编码 `#ececec`：token 漂移风险小。rejected。
- `low` — 当前会话 button 无动作：光标已 default，非假历史入口。rejected。
- `low` — 缺 Enter 发送/焦点迁移：非 AC，加键盘逻辑超最小补丁。rejected。
- `medium` — ≤860px 藏侧栏后「新任务」不可达：无法回空态。→ patch（聊天区补可达入口）。
- `medium` — ≤1100px 打开 Computer 可能在折线下：预览难发现。→ patch（scrollIntoView）。
- `maybe-false` — textarea descriptor stub 脆弱：vitest 下已绿；若真则为测基建，非产品缺陷。rejected（仅会是 low）。
- `high`（verification-gap，pre-verified）— 空态「生成上架素材」胶囊无测：可回退填模板而不进会话仍绿。→ patch。
- `high`（verification-gap，pre-verified）— `sendFromSession` 无执行覆盖。→ patch。
- `medium`（verification-gap，pre-verified）— Listing Computer 未锁 fixture 文案。→ patch。
- `medium`（verification-gap，pre-verified）— Listing 打开未断言零生成 API。→ patch。

## Design Notes

- 会话壳以 09-26 mock 为视觉金样；空态逻辑尽量原地扩展，避免新路由。
- Computer 靠内置 fixture + 演示控件可测；3.4/3.6 再换成真实 `artifact_ready`。
- 真生成发送与积分结算留给 3.4/3.6；本故事明确零 API。

## Verification

**Commands:**
- `cd lippi-ai-ebus-web && npm run lint` -- 无新增 lint 错误
- `cd lippi-ai-ebus-web && npm test -- EcommerceWorkspace` -- 会话壳相关单测绿

**Manual checks:**
- 桌面：空态→会话→演示打开/关闭 Computer→新任务回空态；窄屏：侧栏收起后对话仍可读
