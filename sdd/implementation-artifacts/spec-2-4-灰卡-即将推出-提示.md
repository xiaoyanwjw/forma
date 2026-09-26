---
title: '2.4 灰卡「即将推出」提示'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: '153b304453b0bcf84014fe38c599462814dfc125'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/index.html'
  - '{project-root}/sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/styles.css'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 画廊灰卡（`COMING_SOON`）已是可点按钮，但点击无反馈；用户可能以为坏了，或误以为已进入工作台。2.3 明确把 toast 留给本故事。

**Approach:** 灰卡点击仅弹出「即将推出」类轻提示（对齐 mockup 文案与「先去电商开店」引导），不导航、不建会话、不扣分；已有 `aria-label`/未开放语义保持。

**Decisions:**
- Toast 文案与时长对齐 mockup：`「{displayName}」马上就来。你也可以先从电商开店开始。` + 链「先去电商开店」→ `scene-ecommerce`；约 4.5s 自动收起
- 无 toast 库：画廊内轻量 toast（或同目录小组件），`role="status"` + `aria-live="polite"`；不引 Element Plus
- `SceneCard` 灰卡 `@click` emit；父级展示 toast；`cursor` 可改为 pointer（可点有反馈）

</frozen-after-approval>

## Implementation Notes

- 2026-09-26：`SceneCard` 灰卡 `@click` emit `comingSoon`；`SceneGallery` 画廊内 toast（mockup 文案 +「先去电商开店」→ `scene-ecommerce`，4.5s 收起）；灰卡 `cursor: pointer`。
- 无 toast 库；卸载清 timer。测试补：toast 出现/不导航、引导链进电商。
- Review patch：隐藏态 `aria-hidden` + 链 `tabindex=-1`；连续点击先关再开以重播 live region；测补换卡文案与 4.5s 收起。

## Review Triage Log

- `false` — 灰卡 `aria-disabled=true` 与可点冲突。证据：对齐 mockup；未用原生 `disabled`，键盘/鼠标仍可触发 toast；`aria-label` 标明未开放（UX-DR11）。
- `medium` — 隐藏 toast 仍可被 AT/键盘碰到「先去电商开店」。verified：DOM 常驻 + 仅 transform。→ patch
- `false` — oneshot spec 缺 Boundaries/矩阵等。证据：step-02 oneshot 路由仅保留 Intent + Implementation Notes。
- `medium` — 无 4.5s 自动收起测试。verified：仅出现与导航断言。→ patch
- `medium` — 连续点灰卡 live region 可能不重播。verified：仅改文案、`toastVisible` 保持 true。→ patch
- `medium` — 无「另一张灰卡更新 displayName」断言。carried：并入收起测例。→ patch
- `low` — 灰卡缺 hover 动效。rejected：mockup soon 卡亦无；非日常伤害。
- `false` — Catalog 无 AVAILABLE ecommerce 时 CTA 未定义。证据：2.3 已预留 `scene-ecommerce` 路由；近端种子恒有电商。
- `false` — sprint 把 2-3 改 done。证据：本改动仅 `2-4` → `in-progress`。
- `false` — 实现中仍标 in-progress。证据：oneshot 流程 Finalize 前预期状态。
