# Epic 1 Context: 进入 Adam，看清积分与套餐

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

让一人店主能注册登录进入 Adam，立刻看清免费/Pro/Plus、本月剩余积分与下次重置；不足时知道要升级或等重置。近端用手工/管理改档联调升级（不做支付）。同时落地绿色场脚手架、JWT、CreditLedger、轻量防刷，以及落地页与套餐价目表视觉，为后续生成故事提供登录与账本承载体。

## Stories

- Story 1.1: 可跑的仓库与本地 Docker
- Story 1.2: 注册与登录拿 JWT
- Story 1.3: 积分账本（额度 / 预占 / 结算 / 月重置）
- Story 1.4: 登录后看见套餐与积分
- Story 1.5: 手工改档升级
- Story 1.6: Adam 落地页（品牌 + 清单感）
- Story 1.7: 协议声明与合规提示（AI 须人工复核）

## Requirements & Constraints

- 用户名或邮箱 + 密码注册/登录；未登录不得消耗积分生成；登录态可见套餐档与剩余积分。
- 展示：当前套餐、本月剩余积分、下次重置时间；不足时人话提示升级或等待重置。
- 额度：免费 20 / Pro 200 / Plus 600；周期到点按档重发，上月剩余清零不结转。
- 账本能力（生成接线在后续 Epic）：可用成果落库后各扣 1；不足不能开始；失败无可用结果则不扣或释放预占。
- 升级：免费 → Pro/Plus；月费待定；近端手工/管理改档且可审计（谁/何时/从→到）；无微信/支付宝。
- 合规：注册或首次生成前明示「AI 生成须人工复核后再上架」、不承诺销售效果；勾选或等效确认；静态文案即可。
- 防刷：登录 + interfaces 层频率限制；密码不明文落库。
- 近端不做：小程序、支付网关、微信 OAuth/手机验证码。

## Technical Decisions

- Vue SPA + Spring Boot 模块化单体；前端不持模型密钥、不直改积分。
- 扁平 `forma-*` + `forma-web`；Pi vendor-copy 为 `pi-ai` / `pi-agent`；包根 `com.xmut.ebus`；无 `backend/` 包一层；领域代码不进 `starter`。
- 依赖：`starter → interfaces → application → domain`；`domain`/`pi-*` 不依赖 `interfaces`。
- `APP-META` compose 至少 starter + MySQL；密钥走环境变量/secrets。
- 栈：Java 8、Spring Boot 2.7.x、MyBatis + MySQL、JWT；业务 ID 一律 UUID 字符串。
- Identity：JWT `Authorization: Bearer`；未认证不得启动计费生成。
- CreditLedger 唯一写余额：检查 → 预占 1 → 可用成果落库后结算，否则释放；禁止仅因流结束扣分；套餐档与月重置同属本域。
- 时间存 UTC、展示东八区；月重置按订阅周期锚点。REST 错误：`code` + 人话 `message`。
- 本 Epic 不实现选品/Listing SSE 与 Agent 壳全量 IA；账本与鉴权须可被 Epic 2+ 接线。

## UX & Interaction Patterns

- 品牌名 Adam；落地页品牌为英雄级信号，与清单实物感并置；明确 CTA 进登录或 Agent。
- 色：冷荧光纸色（约 `#F0F2F5`）+ 真黑（约 `#121212`）+ 店章红 `#E11D48`；禁奶油陶土/紫霓虹/薄荷绿空壳风。
- 字体：Space Grotesk（标题/数字）+ Noto Sans SC（正文）；标题字距收紧、左对齐。
- 套餐用三张并列卡（Manus 式层次；walkthrough 人改原 UX-DR5 价目行）；主按钮实心店章红、小圆角 2–4px。
- 禁用全大写 eyebrow、每段 fade-up、按钮尾「→」。
- 登录后账户/套餐入口须可读档位、剩余积分、下次重置。

## Cross-Story Dependencies

- 1.1 是承载体；1.2 先于需登录的账本查询与展示；1.3 是 1.4/1.5 前提；改档后新档额度须立即可见。
- 1.6/1.7 可与后端并行，注册路径须能挂协议确认。
- JWT + CreditLedger 被 Epic 2/3 计费生成强依赖；预占/结算在生成故事接线，本 Epic 须提供可用接口形状。
- 支付与正式月费后置；近端仅手工改档联调「用尽→升级」旅程。
