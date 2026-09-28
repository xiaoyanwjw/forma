# Epic 4 Context: 账户资料、用量与安全

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

用户从头像进入账户页，管理个人资料、查看积分使用情况、修改密码与退出；账户能力统一挂在 `/api/v1/account/**`，资料/改密归 Identity，用量只读归 CreditLedger，禁止经账户路径写积分结算或改档。

## Stories

- Story 4.1: 账户 API 骨架与个人资料
- Story 4.2: 账户页个人资料分区 UI
- Story 4.3: 使用情况分区（只读用量）
- Story 4.4: 安全分区：改密真能力与退出

## Requirements & Constraints

- **入口与分区：** 登录态顶栏头像进账户；含个人资料 / 使用情况 / 安全；默认可落在个人资料；可从账户跳转套餐页，本页不支付。
- **个人资料：** 邮箱只读展示；显示名可编辑保存；用户可见文案无内部黑话。
- **使用情况：** 展示与 CreditLedger 一致的用量摘要与可读流水（只读）；无流水时空态说明，不白屏报错。
- **安全：** 改密为真能力（旧密码正确 + 新密码合规才更新）；错误给人话提示且密码不变；退出后本地会话/JWT 失效，受保护页需重新登录。「删除账户」若展示则禁用/即将开放，不得假装已删除。
- **写边界：** 任意 account 写接口不得具备积分结算或改档扣减能力。

## Technical Decisions

- **统一前缀：** 账户页主契约为 `/api/v1/account/**`。约定路径示例：`/api/v1/account/profile`、`/api/v1/account/password`、`/api/v1/account/credits/usage`（名称可微调，前缀锁定）。
- **职责拆分：** 写资料 / 改密 → Identity；用量明细只读查询 → CreditLedger，经 account 路由暴露。既有 `GET /api/v1/me`、`GET /api/v1/credits` 可保留兼容，但不替代 `/account` 作为账户页主契约。
- **禁止：** account 下不增加结算/改档写口；改档仍走既有 admin / CreditLedger。
- **能力归属：** FR-18 账户 = Identity + CreditLedger + `/account`。

## UX & Interaction Patterns

- **顶栏契约（全站）：** 左 Logo + 场景（或面包屑）+ 历史 + 套餐；右积分 + 升级 + 头像。顶栏全宽贴边，勿做成居中悬浮胶囊。
- **账户页布局：** 桌面左栏导航（个人资料 / 使用情况 / 安全）+ 右内容；头像进入后默认个人资料。窄屏分区导航改顶部分页签（或等价），且可键盘切换。
- **交互心智：** 对齐 Manus 式账户设置；资料行、用量摘要与流水、改密与退出入口齐全。
- **文案：** 冷静帮手感；禁止用户可见内部黑话。

## Cross-Story Dependencies

- Depends on Epic 1（JWT 登录态、Identity、CreditLedger 余额/流水）与 Epic 2（全站顶栏与头像入口已就位）。
- 4.1（`/account` 骨架与资料读写、禁止写积分）是 4.2/4.3/4.4 UI 的前置；4.3 只读聚合账本；4.4 改密走 Identity 经 account，退出清会话。
- 套餐跳转对齐既有套餐页，不在本 Epic 实现支付。
