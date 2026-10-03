---
id: SPEC-lippi-ai-ebusiness
companions:
  - glossary.md
  - ../../planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md
  - ../../planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-24/DESIGN.md
  - ../../planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-24/EXPERIENCE.md
  - ../../../AGENTS.md
sources:
  - ../../planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/prd.md
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Adam — 选品清单与 Listing 自助生成

## Why

**痛点 + 愿景：** 淘宝 / 闲鱼 / 拼多多上的一人店主，不知道卖什么、也不会做上架素材，重复劳动多。Adam 让他们在网页上自己点几下，拿到带理由的选品清单和可上架的主图+详情素材；近端用积分订阅变现，不承诺卖得出去。终局可扩到询盘/客服/物流/收款，但本 SPEC 只覆盖近端。

## Capabilities

- **CAP-1**
  - **intent:** 用户可以注册并登录，查看当前套餐与剩余积分。
  - **success:** 未登录无法完成计费生成；登录后可见套餐档与剩余积分。

- **CAP-2**
  - **intent:** 系统在「选品清单」或「Listing 套装」成功产出可用结果时各扣 1 积分；失败不扣。
  - **success:** 积分不足不能开始；失败/无可用结果释放预占；免费 20 / Pro 200 / Plus 600；月重置剩余清零不结转。

- **CAP-3**
  - **intent:** 用户可以从免费升级到 Pro 或 Plus（近端可用管理/手工改档联调）。
  - **success:** 改档后额度与展示与档位一致；微信支付/支付宝不在本 SPEC。

- **CAP-4**
  - **intent:** 用户选用品类模板后生成一份带理由的选品候选清单。
  - **success:** 一次成功约 8–12 条候选，每条至少一句可读理由；三档均可使用全部已上线模板；结果可再查看（CAP-7）。

- **CAP-5**
  - **intent:** 用户基于候选或自填商品信息生成一套上架素材（主图 + 详情文案/展示说明）并导出或复制。
  - **success:** 产出含主图（经 OSS/`mediaObjectId`）与详情文案/展示说明；国内通用风格；非包装设计；可下载图或复制文案。

- **CAP-6**
  - **intent:** 用户可对不满意结果重试，或提交简短「质量差」反馈。
  - **success:** 重试为新的计费生成（新预占）；反馈可落库备查。

- **CAP-7**
  - **intent:** 用户可查看本人近 **60 天**内的选品清单与 Listing 套装。
  - **success:** 仅本人数据；每次成功成果独立保留（重试不覆盖）；超过 60 天的记录可不展示或按策略清理。

- **CAP-8**
  - **intent:** 用户在 Agent 对话主入口完成生成，并在预览区看到清单或 Listing。
  - **success:** 计费生成走 SSE（JWT + fetch 流）；事件名符合 Spine 闭合集合；预览与对话壳对齐 EXPERIENCE/mockups。

## Constraints

- 网页自助为主；小程序后置。实现须遵守 Architecture Spine（AD-1…AD-13）与 `AGENTS.md` / `sdd/context` 编码规约。
- 前端不直连模型、不改积分；仅 CreditLedger 可变余额；结算仅在可用成果持久化之后。
- Pi 运行时为本仓 `pi-ai` / `pi-agent`（自 LIMS 拷贝）；业务模块 `forma-*`；包根 `com.xmut.ebus`。
- 主图进阿里云 OSS；库存 `mediaObjectId`；JWT 用户名/邮箱+密码；本机 Docker 经 `APP-META`。
- 须能统计单次生成模型成本；失败给人话原因；协议声明 AI 生成须人工复核后再上架；轻量防刷（登录+频率限制）。

## Non-goals

- 询盘 / 客服 / 物流 / 收款自动化
- 出海平台深度适配（Shopify / 亚马逊等）
- 人工陪跑、代运营、效果对赌为主交付
- 承诺销售额或 GMV
- v1 小程序；v1 淘宝/闲鱼/拼多多三套强适配 Listing 风格
- v1 接入微信/支付宝支付网关

## Success signal

一人店主能：登录 → 用积分生成约 10 条带理由的选品 → 再生成可导出的 Listing 套装 → 在历史中找回；积分不足时被拦住并看到升级路径（手工改档可演示）。不以「只注册不生成」为成功。

## Assumptions

- 持久化用 MyBatis；Java 8 + Spring Boot 2.7.18 对齐 LIMS 拷贝，直至单独升级决策。
- Pro/Plus **月费数字**在模型成本实测后确定；v1 升级路径可用管理/手工改档演示，不阻塞实现。

## Open Questions

<!-- none — 历史保留已定为近 60 天；月费待成本实测（见 Assumptions，非实现阻塞） -->
