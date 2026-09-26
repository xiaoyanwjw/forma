---
title: '2.5 进入电商工作台空态'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: 'e8eeb0ced9a426cdc1654f5f54f0823c1362e991'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/scene-ecommerce.html'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/manus-chat.css'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 画廊亮卡已能进 `/scenes/ecommerce`，但页内仍是占位文案：无顶栏面包屑「场景 / 电商开店」，无居中提问壳与选品/上架胶囊，用户无法从空态开始「提问形状」的工作流。

**Approach:** 把 `EcommerceWorkspacePlaceholder` 填成 mockup `#home` 空态（居中标题 + 两枚胶囊 + 提问壳），挂上 `AppHeader` 的 `sceneBreadcrumb`；页面持有电商 `sceneCode`（及可选 `bizId`）作为后续计费会话的前置，本故事不创建会话、不调生成 API。

**Decisions:**
- 路由保持 `scene-ecommerce` / `/scenes/ecommerce`；顶栏 `sceneBreadcrumb="电商开店"`（「场景」回画廊）
- 空态文案对齐 mock：`我能为你做什么？`；胶囊仅「选品清单」「生成上架素材」（不做「更多」）；提问壳 placeholder「分配一个任务或提问任何问题」
- 胶囊点击：把对应模板填入提问壳（对齐 mock），不提交、不建会话
- 发送/附件：保留布局，发送在空态禁用或无 API 副作用（生成属 Epic 3）
- 空态不渲染会话侧栏/Computer；窄屏只需主提问区不被挡（无侧栏即满足 UX-DR10）
- 场景前置：页面常量/状态持有 `sceneCode: 'ecommerce'`（可从 Catalog 解析 `bizId` 备 Epic 3）；不调新后端接口

</frozen-after-approval>

## Implementation Notes

- 2026-09-26：填充 `EcommerceWorkspacePlaceholder` 为 mock `#home` 空态；`AppHeader sceneBreadcrumb="电商开店"`；两枚胶囊（含 mock SVG）填【】纯文本模板进 textarea；发送/附件 disabled + `sr-only` 说明；`data-scene-code=ecommerce`，挂载时可选解析 Catalog `bizId`。
- 有意不做：contenteditable `.ph` 芯片编辑、会话侧栏/Computer、「更多」胶囊、共享 composable（Epic 3 直接读页内常量/`data-*` 即可）。
- 单测：面包屑、文案、胶囊填入、禁用发送、bizId、Catalog 失败仍保 sceneCode。
- Review patch：去掉 textarea `outline:none` 以恢复全局焦点环；胶囊补图标；禁用控件 `aria-describedby`；画廊注释更新。

## Review Triage Log

- `low` — 胶囊缺 mock SVG 图标。verified：text-only + gap 预留。→ patch
- `false` — 应用 `.ph` 芯片而非【】纯文本。证据：Decision 为「填模板」；mock 序列化亦为【】；芯片编辑属 Epic 3。
- `medium` — `.prompt-editor { outline: none }` 覆盖全局焦点环。verified。→ patch
- `medium` — Catalog 失败无测且 bizId 可空。verified：sceneCode 恒在；补失败测例。→ patch
- `low` — 无「侧栏/Computer 缺席」断言。rejected：空态 DOM 本无侧栏，日常不伤。
- `medium` — 禁用发送仅靠 title，AT 弱。verified。→ patch（`aria-describedby` + sr-only）
- `low` — Implementation Notes 未写 defer。→ patch
- `false` — 缺共享 composable。证据：Decision 为页内持有 sceneCode/`data-*`；Epic 3 可再抽。
- `low` — SceneGallery「reserved for 2.5」注释过时。→ patch
- `false` — sprint 仍 in-progress。证据：oneshot Finalize 前的预期中间态。
