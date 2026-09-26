# 电商工作台默认进会话壳 + 精简顶栏

日期：2026-09-26  
范围：前端 `lippi-ai-ebus-web`（电商场景工作台与共享顶栏）  
前置：故事 3.3 已交付会话壳（侧栏 + 对话 + Computer 演示）

## 问题

从场景画廊点「电商开店」卡片进入 `/scenes/ecommerce` 时，先落在空态（「我能为你做什么？」），用户多一步才能看到对话布局。工作台顶栏仍展示「历史」「套餐」，在会话场景里显得多余。

## 目标

1. 进入 `/scenes/ecommerce`（含画廊卡片跳转）**直接显示会话壳**。
2. **移除空态页**；不再通过 `mode: empty | session` 切换整页布局。
3. 「新任务」清空当前演示线程与 Computer，**仍留在会话壳**（空白对话 + 底部输入）。
4. 工作台顶栏**隐藏「历史」「套餐」**；保留 Logo、面包屑「场景 / 电商开店」、右侧积分/升级/账户（登录态）。

## 非目标

- 不接真生成 / 空跑 / 计费 API（仍零生成请求）。
- 不改画廊路由目标（仍为 `name: 'scene-ecommerce'` → `/scenes/ecommerce`）。
- 不删右侧「升级」或积分 chip；不实现真历史列表（3.8）。
- 不新增第二套聊天路由（如 `/scenes/ecommerce/chat`）。

## 行为规格

| 场景 | 期望 |
|------|------|
| 打开 `/scenes/ecommerce` | 会话壳；对话区无气泡；Computer 关闭；无空态 DOM |
| 点画廊电商卡片 | 同上（路由不变） |
| 会话内发送 / 胶囊填模板再发 | 本地演示消息；不调 agent/generation API |
| 演示预览选品 / Listing | Computer split 打开；内容为 fixture |
| 「新任务」（侧栏或窄屏聊天条） | 清空消息、Computer、输入；侧栏标题恢复默认演示标题；仍为会话壳 |
| 工作台顶栏 | 无「历史」「套餐」链接/占位 |
| 画廊等其它页顶栏 | 「历史」「套餐」行为不变 |

## 方案（已选）

**去掉空态，页面默认即会话壳**；`AppHeader` 增加可选 prop，仅工作台关闭次要导航。

曾考虑但未采用：

- URL `?mode=session` 保留空态代码 — 空态已废弃，易成死代码。
- 新增 `/chat` 子路由 — 收益低、改动面更大。

## 组件与改动面

### `EcommerceWorkspacePlaceholder.vue`

- 删除空态模板与相关状态：`ShellMode`、`prompt`（空态）、`fillPicksEmpty` / `fillListingEmpty` / `sendFromEmpty`、居中 `.home` UI。
- 挂载即渲染现有 `.session` 壳；`messages` 初始 `[]`；`computerKind` 初始 `null`。
- `newTask`：清空消息 / Computer / `sessionPrompt`，重置 `sessionTitle`；**不**切换到已删除的空态。
- 会话内胶囊：只填入 `sessionPrompt`（不自动发送）；用户点发送走 `sendFromSession`。
- 传入 `<AppHeader :scene-breadcrumb="…" :hide-secondary-nav="true" />`。

### `AppHeader.vue`

- 新增 boolean prop（建议名 `hideSecondaryNav`，默认 `false`）。
- 为 `true` 时不渲染「历史」占位与「套餐」`RouterLink`。
- 画廊、积分、登录等页面不传该 prop，行为与现网一致。

### 不变

- `SceneGallery` / `SceneCard` 跳转目标。
- `ecommerceDemoFixtures.ts`、`ecommerceWorkspaceSession.css` 断点与 Computer 结构（除非删空态后 scoped 样式可顺手清理无用 `.home*`）。

## 测试

- `EcommerceWorkspacePlaceholder.test.ts`：删除/改写依赖空态的用例；断言挂载即 `.session`、无 `.home`、空线程；「新任务」后仍为 session 且消息空；保留 Computer 开合、零 generation API、面包屑「场景 / 电商开店」。
- `AppHeader.test.ts`：`hideSecondaryNav: true` 时文本中无「历史」「套餐」；默认仍有。

## 验收（人手）

1. 画廊点电商卡片 → 直接会话壳、空白对话。
2. 顶栏无「历史」「套餐」；有「场景 / 电商开店」。
3. 发送一句演示 → 有气泡；「新任务」→ 清空且仍在会话壳。
4. 画廊页顶栏仍能看到「历史」「套餐」。
