# Adam 静态 Demo（正式壳）

对齐上线信息架构的可点击静态站，非营销页。

## 打开

服务若仍在跑：http://127.0.0.1:8765/  

否则：

```bash
cd sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups
python3 -m http.server 8765
```

## 页面

| 页面 | 文件 | 说明 |
|---|---|---|
| 场景画廊 | `index.html` | 全宽顶栏；1 亮卡 + 3 灰卡 |
| 电商工作台 | `scene-ecommerce.html` | 沿用 09-24 Manus 对话壳（侧栏+对话+Computer）；顶栏面包屑「场景 / 电商开店」 |
| 套餐 | `pricing.html` | 三档积分表 |
| 账户 | `profile.html` | Manus 式：右上角头像进入；左栏 个人资料 / 使用情况 / 安全 |

## 设计约束（本轮修正）

- **顶栏必须全宽贴边**（`app-header`），禁止 `max-width + margin:auto` 把整条顶栏居成「悬浮胶囊」。
- 内容区可限宽；应用铬（chrome）占满视口宽。
- 契约见上级 `DESIGN.md` / `EXPERIENCE.md`。
