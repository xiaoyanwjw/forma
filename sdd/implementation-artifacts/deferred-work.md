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
