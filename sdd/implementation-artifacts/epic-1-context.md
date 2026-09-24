# Epic 1 Context: 进入 Adam，看清积分与套餐

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

让一人店主能注册登录进入 **Adam**，立刻看清免费/Pro/Plus、本月剩余积分与下次重置时间；积分不足时知道要升级或等重置；近端用手工改档完成升级联调。本 Epic 同时落地绿色场脚手架（Maven 扁平多模块 + Vue + APP-META compose）、JWT 鉴权、唯一积分账本 CreditLedger、轻量防刷，以及落地页与套餐价目表视觉，为后续选品/Listing 计费生成铺路。

## Stories

- Story 1.1: 可跑的仓库与本地 Docker
- Story 1.2: 注册与登录拿 JWT
- Story 1.3: 积分账本（额度 / 预占 / 结算 / 月重置）
- Story 1.4: 登录后看见套餐与积分
- Story 1.5: 手工改档升级
- Story 1.6: Adam 落地页（品牌 + 清单感）
- Story 1.7: 协议声明与合规提示（AI 须人工复核）

## Requirements & Constraints

- 用户可用户名或邮箱 + 密码注册登录；未登录不得消耗积分完成生成；登录态可见剩余积分与套餐档。
- 展示当前套餐（免费/Pro/Plus）、本月剩余积分、下次重置时间；积分不足时人话提示可升级或等待重置。
- 成功产出可用选品清单或 Listing 各扣 1 积分；不足不能开始；失败且无可用结果不扣分或退回。本 Epic 落地账本能力；实际生成扣分由后续 Epic 接线。
- 月额度：免费 20 / Pro 200 / Plus 600；周期到点按档重发，上月剩余清零不结转。
- 可从免费升级到 Pro/Plus；近端不接微信/支付宝，用管理/手工改档；改档可审计（谁/何时/从→到）；月费数字待定。
- 注册或首次使用前须明示：AI 生成须人工复核后再上架、不承诺销售效果；用户须勾选或等效确认；近端静态文案即可，无需法务 CMS。
- 轻量防刷：登录 + 异常频率限制（interfaces 层按用户/IP）；密码不明文落库。
- 近端不做：小程序、支付网关、询盘/客服/物流/收款自动化、出海平台、人工陪跑主交付、GMV 承诺。

## Technical Decisions

- **形态：** SPA（Vue）+ Spring Boot 模块化单体；不拆微服务。浏览器只调本系统 REST；不持有模型密钥、不直连大模型、不直接改积分。
- **仓结构：** 仓库根即 Maven parent；业务模块平铺 `lippi-ai-ebus-*`（无 `backend/` 包一层）；Pi vendor-copy 为 `lippi-pi-ai` / `lippi-pi-agent`（不依赖 LIMS 发版）；前端目录 `lippi-ai-ebus-web`；部署元数据在 `APP-META/`（compose 至少 starter + MySQL）。Java 包根 `com.xmut.ebus`。
- **依赖方向：** starter → interfaces → application → domain；infrastructure → domain；application 编排落库后结算；domain / pi-* 不依赖 interfaces。
- **栈（脚手架对齐）：** Java 8、Spring Boot 2.7.x、MyBatis + MySQL 8、JWT（jjwt）、Vue3/Vite/TS、Compose V2。密钥与 OSS/模型 Key 仅环境变量 / compose secrets。
- **认证：** Identity 唯一写用户与 JWT；API 用 `Authorization: Bearer`；除注册/登录/公开落地页外需 JWT。业务 ID 一律 UUID 字符串。
- **积分：** 仅 CreditLedger 可写余额/预占/结算/月重置/套餐档。计费路径：`检查 → 预占 1 → 可用成果落库后结算 → 否则释放`。禁止因 SSE 流结束即扣分；Agent 工具不得写积分表。时间存 UTC、展示东八区；月重置按用户订阅周期锚点。
- **领域所有权（本 Epic）：** Identity、CreditLedger 为唯一写者；跨界只走公开服务。支付后置；升级联调走 CreditLedger 手工改档。
- **错误：** REST 返回 `code` + 人话 `message`。

## UX & Interaction Patterns

- **品牌气质：** 价签/店章/商品列表感，非通用 SaaS。冷荧光纸色底（#F0F2F5）+ 真黑字（#121212）+ 店章红 #E11D48 唯一大胆色；禁用奶油陶土/紫霓虹/薄荷绿空壳风。
- **字体：** Space Grotesk（标题与数字）+ Noto Sans SC（中文正文）；标题字距收紧、左对齐。
- **落地页：** 品牌 Adam 为英雄级信号；与「选品清单」实物感并置，第一眼看到交付物；明确 CTA 进入登录或 Agent。禁用全大写 eyebrow、每段 fade-up、按钮尾「→」。
- **套餐展示：** 价目表行，不用三张孪生套餐卡。主按钮实心店章红、小圆角 2–4px。
- **IA 衔接：** 落地页进入 Agent；左侧有套餐入口（工具页完整壳与预览属后续 Epic，本 Epic 至少落地页 + 套餐/积分可见）。

## Cross-Story Dependencies

- 1.1 为全 Epic 承载体；1.2 鉴权完成后 1.3 账本与 1.4 展示才可联调。
- 1.4 依赖 1.2–1.3 的档位与余额数据；1.5 改档后须刷新用户可见额度。
- 1.6 / 1.7 可与后端并行，但注册流须落地协议确认（1.7）后方可完成注册或首次生成。
- CreditLedger 预占/结算在本 Epic 建成；选品/Listing 真正扣分由 Epic 2/3 经 GenerationRun 接线；Epic 2 起才需要 SSE 闭合事件与 Agent 壳。
