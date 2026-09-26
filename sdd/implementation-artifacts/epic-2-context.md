# Epic 2 Context: 场景画廊与进入电商工作台

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

用户打开首页先见到场景大卡片画廊（1 亮 + 3 灰），点「电商开店」进入该场景工作台空态；灰卡仅「即将推出」提示且不进假台。锁定「画廊 → 工作台 → 场景内固定路径」的可扩展产品形状，并为后续加场景、全站顶栏一致、会话必绑场景打好壳。

## Stories

- Story 2.1: SceneCatalog 与画廊列表 API
- Story 2.2: 全站顶栏契约与 DESIGN token
- Story 2.3: 场景画廊页（1 亮 + 3 灰）
- Story 2.4: 灰卡「即将推出」提示
- Story 2.5: 进入电商工作台空态

## Requirements & Constraints

- 登录后首页必须是场景画廊大卡片，不是直达提问门面；近端固定四卡——电商开店可用，短视频带货 / 小红书种草 / 本地生活为即将推出。
- 亮卡进入电商工作台（提问门面 + 选品/上架胶囊）；工作台可回画廊。
- 灰卡可点，仅 toast/轻提示「即将推出」（不承诺日期），不进可操作工作台、不创建计费会话、不扣积分。
- 产品形状须可扩展：新增场景时加卡与配置能力，不得拆除画廊范式。
- 用户可见文案冷静短句，禁止内部黑话；未开放/失败给人话提示。
- 积分全站共用，不按场景拆账。

## Technical Decisions

- SceneCatalog 是场景元数据唯一写者；CatalogTemplate 只管品类，不表示产品场景。灰卡也是真库行（`COMING_SOON`）。
- 每行至少含 `biz_id`、稳定 `sceneCode`、展示名、状态、排序、可选文案；画廊列表 API 只读 Catalog，响应禁止含系统提示词或 tool 定义正文。
- 场景能力包（提示词 / skill / tool）在仓库代码资源，按 `sceneCode` 绑定；近端不因有占位包自动开放。
- 创建计费会话 / GenerationRun 必须携带 `sceneId` 或 `sceneCode`（工作台空态做场景上下文前置；真正创建落实在 Epic 3）。
- 近端不做后端对 `COMING_SOON` 的硬拒（灰卡闸靠 UX）；后置再补。
- 接口面 `/api/v1/scenes*`；种子四场景行；对外/包绑定用稳定 `sceneCode`（如 `ecommerce`）。

## UX & Interaction Patterns

- 顶栏全宽贴边：左 Logo + 场景（或面包屑「场景 / 电商开店」）+ 历史 + 套餐；右积分 + 升级 + 头像。禁止顶栏 `max-width` 居中悬浮胶囊。
- 画廊：内容柱约 960px；桌面约 2×2；亮卡整卡可点、无底部「开始使用」脚链；灰卡降饱和并标「即将推出」。窄屏改为单列。
- 工作台空态：居中「我能为你做什么？」+ 选品/上架胶囊 + 提问壳；窄屏侧栏可收，不挡主提问区。
- 视觉：DESIGN token（canvas / surface / ink / mute / line / accent / success）、圆角间距、Instrument Sans + Noto Sans SC、点阵底。
- 焦点环可见；灰卡对辅助技术标明未开放。

## Cross-Story Dependencies

- 2.1 Catalog/API 先于 2.3/2.4 画廊渲染；2.2 顶栏与 token 全站共享，画廊与工作台共用。
- 2.5 依赖亮卡路由与顶栏面包屑；会话绑场景的计费创建属 Epic 3，本 Epic 只保证空态与场景上下文前置。
- 依赖 Epic 1 登录/JWT 与顶栏积分位；头像进账户页的实现在 Epic 4，本 Epic 只保留入口位。
- 选品/Listing 生成、短聊拉回、历史属 Epic 3，不在本 Epic 交付。
