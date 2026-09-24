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
