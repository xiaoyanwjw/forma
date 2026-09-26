---
name: Adam
description: 场景画廊入口 + 场景内 Manus 式工作台；近端仅电商开店可用
title: Adam DESIGN
status: final
created: 2026-09-26
updated: 2026-09-26
colors:
  canvas: "#FAFAFA"
  surface: "#FFFFFF"
  ink: "#171717"
  mute: "#737373"
  mute-2: "#A3A3A3"
  line: "#E5E5E5"
  accent: "#262626"
  success: "#15803D"
typography:
  display:
    fontFamily: '"Instrument Sans", "Noto Sans SC", sans-serif'
    fontWeight: "600"
  body:
    fontFamily: '"Instrument Sans", "Noto Sans SC", sans-serif'
    fontWeight: "400"
rounded:
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  full: "9999px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "24px"
  "6": "32px"
  "7": "48px"
  "8": "64px"
components:
  scene-card:
    background: "{colors.surface}"
    border: "1px solid {colors.line}"
    rounded: "{rounded.lg}"
  scene-card-disabled:
    opacity: "0.58"
    filter: "grayscale(0.25)"
  prompt-box:
    background: "{colors.surface}"
    border: "1px solid {colors.line}"
    rounded: "{rounded.xl}"
  pill:
    background: "{colors.surface}"
    border: "1px solid {colors.line}"
    rounded: "{rounded.full}"
  app-header:
    height: "56px"
    background: "rgba(255,255,255,0.86)"
    borderBottom: "1px solid {colors.line}"
---

# Brand & Style

Adam 是「先选场景、再进工作台」的自助生意助手。近端只真正干活于**电商开店**；首页用场景大卡片讲清边界与扩展感。

视觉气质对齐 **Manus 式**：浅色画布、点阵底、大量留白、轻描边、弱阴影；应用顶栏全宽贴边。品牌名 Adam 在顶栏清晰可见。

# Colors

- `{colors.canvas}` 整页底（点阵）。
- `{colors.surface}` 卡片、输入壳、顶栏。
- `{colors.ink}` / `{colors.mute}` 主文与次文。
- `{colors.line}` 分割线。
- `{colors.accent}` 深色主按钮与头像底。
- 灰卡降饱和表达未开放；成功态用 `{colors.success}` 仅用于「可用」徽标。

# Typography

Instrument Sans + Noto Sans SC。标题字重 600；避免全大写 eyebrow。

# Layout & Spacing

- **应用顶栏必须全宽贴边**：左 Logo +（场景面包屑或）场景/历史/套餐；右积分、升级、头像。禁止整条顶栏 `max-width` 居中成悬浮胶囊。
- **场景画廊**：内容柱 ~960px；2×2 大卡片；可用卡整卡可点，无底部「开始使用」链接。
- **场景工作台**：侧栏历史 + 对话 + 右侧 Adam's Computer（生成后 split）。
- **账户页**：左设置导航 + 右内容（个人资料 / 使用情况 / 安全）。
- **套餐页**：余额卡 + 三档方案卡（无支付按钮）。

# Elevation & Depth

默认靠边框；卡片 hover 极轻阴影。

# Shapes

卡片 `{rounded.lg}`，输入壳 `{rounded.xl}`，胶囊/头像 `{rounded.full}`。

# Components

- 场景卡、胶囊、提问壳、即将推出 toast、Computer 预览窗、套餐卡、账户设置行、顶栏头像。

# Do's and Don'ts

- Do：首页先场景；顶栏导航位置跨页一致；产品文案冷静短句
- Do：工作台保留 Computer 预览（选品清单 / 上架素材）
- Don't：紫渐变、首屏统计条、顶栏悬浮胶囊、灰卡进假工作台
- Don't：内部黑话（示意、支付网关、空壳工作台、近端）出现在用户可见文案

静态参考：[`mockups/`](./mockups/)（`index` · `scene-ecommerce` · `pricing` · `history` · `profile`）。*Spines win on conflict with mocks.*
