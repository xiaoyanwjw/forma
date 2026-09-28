# Deferred work

- source_spec: `sdd/implementation-artifacts/spec-1-1-可跑的仓库与本地-docker.md`
  summary: Pi vendor-copy 仍含 LIMS 域技能资源（certificate-ocr / walk-in-import 等），需在后续故事裁剪或隔离。
  evidence: 评审确认拷贝树内仍有 LIMS 技能与注释；本故事验收只要求可编译与 compose，未要求清洗技能集。

- source_spec: `sdd/implementation-artifacts/spec-1-1-可跑的仓库与本地-docker.md`
  summary: MySQL named volume 在改密后不会重跑入口初始化，易导致 starter 连库失败。
  evidence: compose 挂载 `mysql-data`；仅首次 init 读 MYSQL_*；改口令后旧 volume 仍保留旧认证。

- source_spec: `sdd/implementation-artifacts/spec-1-1-可跑的仓库与本地-docker.md`
  summary: Docker Hub / 国内镜像源拉取 `eclipse-temurin:8-jre` 可能 401，需本地 BASE_IMAGE 绕过。
  evidence: Implementation Notes 与 README 已记录；非代码逻辑缺陷。

- source_spec: `sdd/implementation-artifacts/spec-1-1-可跑的仓库与本地-docker.md`
  summary: compose starter↔MySQL 启动契约缺少自动化观察（Testcontainers 或 compose smoke）。
  evidence: verification-gap 层确认无 `@SpringBootTest`/Testcontainers；本故事 Verification 为手工 compose。

- source_spec: `sdd/implementation-artifacts/spec-1-2-注册与登录拿-jwt.md`
  summary: 限流信任首段 X-Forwarded-For，无可信代理时客户端可伪造 IP 绕过。
  evidence: AuthRateLimitInterceptor.resolveClientIp 直接取 XFF；近端 compose 无反代拓扑，harden 后置。

- source_spec: `sdd/implementation-artifacts/spec-1-2-注册与登录拿-jwt.md`
  summary: H2 schema-h2.sql 与 MySQL 001_ebus_user.sql 双份手维护，列变更易漂移。
  evidence: 测试与 compose init 各一份 DDL；本故事无共享 codegen。

- source_spec: `sdd/implementation-artifacts/spec-1-2-注册与登录拿-jwt.md`
  summary: OverlayModelCatalogTest 期望从 deepseek-chat 改为 deepseek-v4-flash（Pi 预存测修）。
  evidence: 非 Identity 交付；vendor Pi 测试与本故事无关。

- source_spec: `sdd/implementation-artifacts/spec-1-2-注册与登录拿-jwt.md`
  summary: 用户名等于另一用户邮箱时 selectByUsernameOrEmail OR+LIMIT 1 匹配不确定。
  evidence: 日常极少；完整消歧需禁用户名像邮箱或改查找优先级，超出最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-1-3-积分账本-额度-预占-结算-月重置.md`
  summary: H2 schema-h2.sql 与 MySQL 002_ebus_credit.sql 双份手维护（FK/索引/精度），易漂移。
  evidence: 评审确认 H2 省略 FK/索引且用 TIMESTAMP；与 1.2 用户表同类问题，本故事无共享 codegen。

- source_spec: `sdd/implementation-artifacts/spec-1-4-登录后看见套餐与积分.md`
  summary: AuthMe「套餐与积分」链缺少挂载/导航断言。
  evidence: verification-gap 确认无 AuthMe 测；登录落地与 CreditPlan 挂载优先，页面 harness 补齐后再测互链。

- source_spec: `sdd/implementation-artifacts/spec-1-5-手工改档升级.md`
  summary: 规划文档仍写 UX-DR5「价目表行」，与 1.4 已定案的 Manus 三卡未对齐。
  evidence: 评审对照 epics/UX 与 epic-1-context；属规划 course-correction，非本故事改档因果。

- source_spec: `sdd/implementation-artifacts/spec-1-5-手工改档升级.md`
  summary: 套餐页非当前档状态条/重复额度文案/超前模板承诺/大圆角属 1.4 UI 债。
  evidence: diff 中 CreditPlan.vue 来自 baseline 前未提交的 1.4 改动；本故事不交付前端管理/价目。

- source_spec: `sdd/implementation-artifacts/spec-1-5-手工改档升级.md`
  summary: H2 审计表相对 MySQL 003 省略 FK，双份 schema 继续漂移。
  evidence: 与 1.2/1.3 已 defer 的 H2↔MySQL 双维护同类。

- source_spec: `sdd/implementation-artifacts/spec-1-6-adam-落地页-品牌-清单感.md`
  summary: 窄视口「堆叠可见」测只断言 DOM 存在，未观测布局几何。
  evidence: verification-gap 演示隐藏 `.sheet` 仍可通过查询；本仓 FE 无视觉/e2e，英雄+清单存在已由主渲染测覆盖。

- source_spec: `sdd/implementation-artifacts/spec-2-1-generationrun-与-sse-事件骨架.md`
  summary: SSE 超时或客户端断开时，若 AgentSession.prompt 仍阻塞，可能延迟/遗漏 release，需 cancel+补偿释放。
  evidence: review maybe-false；SseEmitter 有 timeout，但 Pi prompt 挂死时 onTimeout 与 release 编排未钉死；应用 AgentSession.cancel。

- source_spec: `sdd/implementation-artifacts/spec-2-1-generationrun-与-sse-事件骨架.md`
  summary: Pi TOOL_EXECUTION_UPDATE 未映射到 AD-4（无对等细粒度事件名）。
  evidence: AD-4 闭合七名无 mid-tool 进度；本故事骨架只映射 start/end。

- source_spec: `sdd/implementation-artifacts/spec-2-1-generationrun-与-sse-事件骨架.md`
  summary: `startEmptyRun` 便利方法自调用可能绕过 prepareEmptyRun 的事务代理（Controller 主路径无此问题）。
  evidence: 生产入口为 prepareEmptyRun+streamEmptyRun；startEmptyRun 主要用于单测。

- source_spec: `sdd/implementation-artifacts/spec-2-6-pi-运行时默认装配与噪音清理-重构先行.md`
  summary: 切到 InMemory 默认后，若 cwd 仍残留旧 `.lippi-pi/state.db`，无告警提示历史被弃用。
  evidence: MissingBean→InMemory 是 Decision B；加启动 warn 属过渡运维体验，非本故事最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-2-6-pi-运行时默认装配与噪音清理-重构先行.md`
  summary: `lims.pi.checkpoint.redis.enabled=true` 但无 JedisPool 时静默回落 InMemory，无 fail-fast。
  evidence: 条件注解按设计；启动失败语义需另定，超出本故事最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-2-7-mysql-sessionstore-pi_session-pi_session_entry.md`
  summary: 生产 `005_pi_session.sql` 无自动化执行（仅 H2 schema-h2）；缺 MySQL/Testcontainers 或 compose 冒烟门禁。
  evidence: verification-gap 确认仓内无 Testcontainers；IT 只跑 H2；compose/initdb 为手工检查。

- source_spec: `sdd/implementation-artifacts/spec-2-7-mysql-sessionstore-pi_session-pi_session_entry.md`
  summary: H2 TIMESTAMP/CLOB 与 MySQL DATETIME(3)/JSON 双份 DDL 行为可能漂移。
  evidence: 与既有 ebus H2/MySQL 双维护模式相同；本故事未引入共享 DDL 源。

- source_spec: `sdd/implementation-artifacts/spec-2-7-mysql-sessionstore-pi_session-pi_session_entry.md`
  summary: compose/bootstrap 文档未提示已初始化 volume 需手工跑 `005`。
  evidence: Implementation Notes 已写；改运维文档超出最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-2-8-mysql-图-checkpoint-与-resume-续跑端口.md`
  summary: 过期 pi_graph_checkpoint 行只在读路径视同缺失，不做物理删除/定时清扫。
  evidence: 规格 Always 仅要求过期视同无 CP；Redis TTL 会驱逐，MySQL 需后续运维或 purge-on-read。

- source_spec: `sdd/implementation-artifacts/spec-2-8-mysql-图-checkpoint-与-resume-续跑端口.md`
  summary: 多 Pod 下 prepareToolResult 与 GraphExecutor.resume 之间 CP 可能被他实例改写（竞态未证明）。
  evidence: 同 JVM 有 activeRuns；跨实例无版本校验；若属实需 CP 版本/乐观锁，超出本故事最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-2-8b-mysql-resume-idempotency.md`
  summary: 过期 pi_resume_idempotency 行无后台扫表清扫，仅 claim 冲突时条件删除。
  evidence: 规格要求读路径视同缺失；与 2.8 CP 同策略，定时 purge 属运维后续。

- source_spec: `sdd/implementation-artifacts/spec-2-8b-mysql-resume-idempotency.md`
  summary: TTL 过期后同 confirmId 可再次 CLAIMED 并可能重跑工具副作用，客户端说明不足。
  evidence: 设计如此（过期视同无键）；HITL 客户端应换新 confirmId 或知晓窗口；改文档超出最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-2-1-scenecatalog-与画廊列表-api.md`
  summary: 生产 `008_ebus_scene.sql` 种子未进自动化（仅 H2 schema-h2 孪生），MySQL 真源可漂移而 IT 仍绿。
  evidence: verification-gap：test profile 只加载 schema-h2；仓库无 Testcontainers/compose 跑 008；与既有 bootstrap SQL 门禁缺口同类。

- source_spec: `sdd/implementation-artifacts/spec-2-1-scenecatalog-与画廊列表-api.md`
  summary: Scene 仓储暂无按 bizId/sceneCode 单条查询，Epic 3 会话绑场景时需补端口。
  evidence: 本故事 Intent 仅画廊列表；epic-2-context / AD-15 绑定落在 Epic 3。

- source_spec: `sdd/implementation-artifacts/spec-2-3-场景画廊页-1-亮-3-灰.md`
  summary: 窄屏单列布局仅靠源码 `@media` 正则断言，jsdom 无法证明运行时单列。
  evidence: verification-gap：改 media 规则或保留 `1fr` 子串仍可能绿；需浏览器级检查才能闭合。

- source_spec: `sdd/implementation-artifacts/spec-3-1-generationrun-sse-与会话必绑场景.md`
  summary: AgentDryRun / useAgentEmptyRun 无组件级单测，场景必传仅靠 api.flow 锁定。
  evidence: verification-gap：改 DryRun 去掉 sceneCode 时 agent.flow 仍绿；窄调试页，API 契约已覆盖。

- source_spec: `sdd/implementation-artifacts/spec-3-1-generationrun-sse-与会话必绑场景.md`
  summary: 空跑可复用任意 sessionId，无「会话归属当前用户」校验。
  evidence: 2.1 起即接受客户端传入 sessionId；本故事只加场景绑定，未引入归属检查。

- source_spec: `sdd/implementation-artifacts/spec-3-1-generationrun-sse-与会话必绑场景.md`
  summary: pi_session 场景绑定时仍可能 userId=null（与 MysqlSessionStore 建行一致）。
  evidence: 本人历史/归属筛选属后续故事；本故事只保证场景列写入。

- source_spec: `sdd/implementation-artifacts/spec-3-1-generationrun-sse-与会话必绑场景.md`
  summary: updateScene 影响 0 行时可能出现 run 有场景而 pi_session 行缺失（并发删行）。
  evidence: maybe-false medium；需可复现的并发删行场景才能证实。

- source_spec: `sdd/implementation-artifacts/spec-3-2-电商场景能力包按-scenecode-加载.md`
  summary: starter 与 application test/resources 双份 ecommerce pack 可能漂移，Loader 单测绿不保证生产 classpath 同步。
  evidence: 评审确认两处镜像无同步校验；近端靠手改两边，非运行时用户缺陷。

- source_spec: `sdd/implementation-artifacts/spec-3-2-电商场景能力包按-scenecode-加载.md`
  summary: Loader.load 成功未与同进程 SkillConfig.resolve(默认 skillId) 联检，注册失败时可能用人话以外的错误进 prompt。
  evidence: EbusSkillConfiguration 与 Loader 分路径扫描；补联检需注入 SkillConfig，超出本轮最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-3-2-电商场景能力包按-scenecode-加载.md`
  summary: AgentEmptyRunIntegrationTest 未覆盖装包成功注入 skillId / 缺包人话 run_failed。
  evidence: IT 仍只断言 AD-4 事件与 release；单元测已覆盖主路径，IT 加强后置。

- source_spec: `sdd/implementation-artifacts/spec-3-2-电商场景能力包按-scenecode-加载.md`
  summary: Loader 未在装包时校验各 skill promptRef 指向的 md 是否存在。
  evidence: 坏引用延后到读资源失败；骨架包现已成对齐全，加强校验后置。

- source_spec: `docs/superpowers/specs/2026-09-26-official-pi-skills-adapter-design.md`
  summary: 未将 `read_skill` 重做为官方式通用 `read` 文件工具；仍只读当前 ActiveSkill 正文。
  evidence: 设计 Non-goals；Adam 场景靠 SKILL.md + read_skill 按需拉全文。

- source_spec: `docs/superpowers/specs/2026-09-26-official-pi-skills-adapter-design.md`
  summary: 未引入 pi-mono 内置 coding tools（bash / edit / write / …）；默认 Spring 路径为代码注册 Tool（如 `read_skill`）+ `allowed-tools` 按名激活。
  evidence: 设计 Non-goals；已删 ToolLevel；无 `*.tool.json` 扫盘 on default path。

- source_spec: `docs/superpowers/specs/2026-09-26-official-pi-skills-adapter-design.md`
  summary: WRITE HITL 为全局开关 + 已注册且本轮 active 的工具批次挂起（不按 READ/WRITE level）；未改为「按工具名点名审批」协议。
  evidence: `ToolPolicyExtension` 不读 level；默认 `lims.pi.tool.write-approval.enabled=false`；后续故事可改 per-tool named approval。

- source_spec: `sdd/implementation-artifacts/spec-3-3-会话态工作台-侧栏-对话-computer.md`
  summary: 窄屏侧栏隐藏的验证仅靠 CSS 源码正则，无法在 jsdom 中诚实覆盖 media-query 布局。
  evidence: verification-gap：去掉 session CSS import 后源码测仍绿；需浏览器/冒烟才锁 UX-DR10。

- source_spec: `sdd/implementation-artifacts/spec-3-4-生成选品清单并结算-1-积分.md`
  summary: 客户端取消/SSE 断流时服务端无法中断 AgentSession.prompt，仍可能跑完并 settle。
  evidence: 评审确认与 empty-run 同构；近端无 AgentSession 取消端口；FE 已 abort 本地流并忽略迟到成果。

- source_spec: `sdd/implementation-artifacts/spec-3-4-生成选品清单并结算-1-积分.md`
  summary: mid-stream SSE 发送失败只设 aborted，不取消正在进行的模型 turn。
  evidence: 与 empty-run 同构限制；补取消需打断 prompt，超出本故事最小补丁。

- source_spec: `sdd/implementation-artifacts/spec-3-4-生成选品清单并结算-1-积分.md`
  summary: settle 失败（成果已落库）不自动 release，需运维/补偿路径处理卡住的 hold 与孤儿清单。
  evidence: 释放会导致白嫖；当前人话「联系支持」；完整补偿后置。

- source_spec: `sdd/implementation-artifacts/spec-3-4-生成选品清单并结算-1-积分.md`
  summary: modelUsage 日志仍为 token/成本占位，真实 promptTokens/totalTokens 待 TurnResult 贯通。
  evidence: NFR2 近端以占位+responseChars 满足；真实用量字段后置。

- source_spec: `docs/superpowers/specs/2026-09-26-generic-artifact-store-design.md`
  summary: 本地 compose 若仍挂载含 `ebus_picklist*` 的旧 MySQL volume，bootstrap 不会自动删表；需 `docker compose … down -v` 后重建，让 `010_ebus_artifact.sql` 在空库生效。
  evidence: 设计决策「开发库丢弃重建、无迁移脚本」；与 1.1 改密后 volume 不 re-init 同类。

- source_spec: `sdd/implementation-artifacts/spec-3-6-生成-listing-套装并结算-1-积分.md`
  summary: epic-3-context 仍写 Listing 媒体「只进阿里云 OSS」，近端已用 MinIO/MediaStore；生产换 OSS 时需同步编译上下文。
  evidence: 评审确认；本故事冻结意图为 B′ MinIO，未改生产 OSS 决策。

- source_spec: `sdd/implementation-artifacts/spec-3-6-生成-listing-套装并结算-1-积分.md`
  summary: 客户端取消/SSE 断流时服务端 Listing 仍可能跑完并 settle（与 3.4 同构）。
  evidence: FE abort 只停本地流；AgentSession.prompt 无中断端口。
