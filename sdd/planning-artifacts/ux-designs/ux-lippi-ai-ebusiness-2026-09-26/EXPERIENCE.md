---
title: Adam EXPERIENCE
status: final
created: 2026-09-26
updated: 2026-09-26
sources:
  - sdd/planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/prd.md
  - sdd/planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/addendum.md
---

# Foundation

- **形态：** 网页（v1）；小程序后置
- **视觉契约：** 同目录 [`DESIGN.md`](./DESIGN.md)
- **产品源：** PRD FR-13～18、UJ-0 / UJ-4 / UJ-5；近端能力仍为选品清单 + Listing
- **主角：** 小陈（一人淘宝店主）

# Inspiration & Anti-patterns

- **Inspiration：** Manus 顶栏头像进账户、居中提问门面、Computer 预览、轻量套餐层次
- **Anti：** 顶栏导航左右漂移；内部黑话文案；灰卡空壳工作台

# Information Architecture

| 表面 | 职责 | 入口 |
|---|---|---|
| **场景画廊** | 选场景 | `/` · 顶栏「场景」 |
| **电商开店 · 工作台** | 对话 + Computer | 亮卡「电商开店」 |
| **历史** | 近 60 天生成物 | 顶栏「历史」 |
| **套餐** | 积分余额与三档方案 | 顶栏「套餐」/ 升级 |
| **账户** | 个人资料 / 使用情况 / 安全 | 顶栏头像 |
| **灰场景** | 仅即将推出提示 | 点灰卡 |

顶栏契约（全站一致）：**左** Logo + 场景（或面包屑「场景 / 电商开店」）+ 历史 + 套餐；**右** 积分 + 升级 + 头像。

静态参考：[`mockups/index.html`](./mockups/index.html)、[`scene-ecommerce.html`](./mockups/scene-ecommerce.html)、[`pricing.html`](./mockups/pricing.html)、[`history.html`](./mockups/history.html)、[`profile.html`](./mockups/profile.html)。*Spines win on conflict with mocks.*

# Voice and Tone

- 冷静帮手感；短聊拉回**友好**。
- 灰卡：「即将推出」，不承诺日期。
- 场景卡文案用「主题：帮你做什么」短句，不用反问营销腔。

# Component Patterns

| 模式 | 行为 |
|---|---|
| 场景亮卡 | 整卡点击 → 工作台 |
| 场景灰卡 | toast「即将推出」；可引导去电商 |
| 工作台胶囊 | 触发送品 / 上架素材流 |
| 生成成功 | 右侧打开 Computer 展示清单或素材 |
| 超范围输入 | 短聊后友好拉回；不扣生成积分 |
| 头像 | 进入账户设置 |

# State Patterns

| 状态 | 表现 |
|---|---|
| 画廊默认 | 1 亮 + 3 灰 |
| 工作台空态 | 「我能为你做什么？」+ 胶囊 + 输入 |
| 工作台会话 | 侧栏 + 对话 +（生成后）Computer |
| 账户分区 | 个人资料 / 使用情况 / 安全 切换 |

# Interaction Primitives

- 点击优先；Enter 发送；无拖拽编排。

# Accessibility Floor

- 灰卡标明未开放；焦点环可见；账户分区可键盘切换。

# Key Flows

**F0 · 进入电商开店（UJ-0）** — 画廊 → 亮卡 → 工作台空态。  
**F1 · 灰卡（UJ-4）** — toast，不进假台。  
**F2 · 闲聊拉回（UJ-5）** — 友好拉回 + 能力按钮。  
**F3 · 选品 / Listing** — 对话反馈 + Computer 预览。  
**F4 · 打开账户** — 头像 → 个人资料；可看使用情况与安全。

# Responsive & Platform

- 桌面：画廊 2×2；工作台三栏（侧栏+对话+Computer）；账户左导航。  
- 窄屏：画廊单列；侧栏可收；账户导航改顶部分页签。  
- v1 仅网页。

# Open UX items

- [x] 工作台右侧 Computer：采用（对齐 09-24 Manus 壳）  
- [x] 账户页：顶栏头像 + 三分区  
- [ ] 灰卡插画升级（当前线描 SVG 可接受；上线前可换品牌插画）— Owner：设计；非阻塞  
