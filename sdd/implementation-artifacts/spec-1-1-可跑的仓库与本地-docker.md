---
title: '1.1 可跑的仓库与本地 Docker'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'NO_VCS'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-1-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 仓库目前只有规划文档与 Agent 工具链，没有可编译的 Maven 多模块、Vue 壳、APP-META compose，也没有 Pi 运行时拷贝；后续鉴权/积分/生成故事没有统一承载体。

**Approach:** 按 Architecture Spine 落地扁平仓脚手架：parent + `forma-*` + `pi-ai`/`pi-agent` + `forma-web` + `APP-META`，使 `mvn -pl forma-starter -am` 可编译，compose 能拉起 starter 与 MySQL。

**Decisions:**
- Pi 运行时：从旁路 `/Users/echo/workspace/lippi-ai-lims/backend/lippi-ai-lims-pi-{ai,agent}` 全量 vendor-copy，目录与 artifact 改名为 `pi-ai` / `pi-agent`；不依赖 LIMS 发版构件。

## Boundaries & Constraints

**Always:**
- 仓库根即 Maven parent；业务模块平铺 `forma-*`，无 `backend/` 包一层；包根 `com.xmut.ebus`
- 依赖方向符合 AD-11：`starter→interfaces→application→domain`；`application→pi-agent→pi-ai`；`interfaces→infrastructure→domain`；`*→common`；`domain`/`pi-*` 不依赖 `interfaces`
- Pi 模块目录与 artifact 名为 `pi-ai` / `pi-agent`，不依赖 LIMS 发版构件；源码来自旁路 LIMS 全量拷贝（见 Decisions）
- 栈钉死：Java 8、Spring Boot 2.7.18、Compose V2；密钥仅环境变量 / compose secrets
- `APP-META/docker-config` compose 至少拉起 starter + MySQL；前端目录 `forma-web`（Vite/Vue3/TS，非 Maven 子模块）

**Never:**
- 不搬 LIMS 的 `backend/` 布局或 `TenantContext` 多租户
- 不实现注册/JWT/积分账本/落地页视觉（属 1.2–1.7）
- 不接真实大模型密钥或生产 OSS；本地可用桩，端口形状不变
- 不把领域代码塞进 `starter`；不引入第二套主存储

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 编译成功 | 干净检出后执行 `mvn -pl forma-starter -am -DskipTests compile` | BUILD SUCCESS；各业务模块与 Pi 模块均被纳入 reactor | 依赖方向错误则编译失败，须修正 pom 后再验 |
| Compose 拉起 | `docker compose -f APP-META/docker-config/docker-compose.yml up -d`（或等价） | MySQL 健康；starter 容器启动并可连库 | 缺环境变量/端口冲突时 compose 报错可读；密钥不写死进仓 |
| 结构违规 | 误加 `backend/` 包一层或 Pi 仍叫 lims 模块名 | 验收不通过 | 重构为扁平命名后再验 |

</frozen-after-approval>

## Code Map

- 本仓根 — 仅有 `AGENTS.md`、`sdd/`、`_bmad/`、`.claude/`；**无** `pom.xml`、`lippi-*`、`APP-META`、`package.json`（绿色场）
- `sdd/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md` — AD-3/10/11/13、Structural Seed、Stack 版本表（权威）
- `sdd/implementation-artifacts/epic-1-context.md` — Epic 1 蒸馏约束
- `sdd/context/02-be.md` — 后端目录配方与 LIMS→ebus 对照（勿照搬 `backend/`）
- `sdd/context/03-fe.md` — `forma-web/src/` 约定
- `/Users/echo/workspace/lippi-ai-lims/backend/pom.xml` — Boot 2.7.18 / Java 8 / MyBatis 等版本范本（只对齐版本，不拷业务模块）
- `/Users/echo/workspace/lippi-ai-lims/backend/lippi-ai-lims-pi-ai/`、`.../lippi-ai-lims-pi-agent/` — Pi vendor-copy 源（含 `AgentSession`）
- `/Users/echo/workspace/lippi-ai-lims/APP-META/docker-config/` — compose / Dockerfile / environment 布局参考（精简为 starter+MySQL）

**Reuse：** LIMS parent 属性与 APP-META 目录形状；Pi 从旁路 LIMS 全量拷贝。

**Do not change：** `sdd/planning-artifacts/**`、现有 BMAD 技能；勿引入 LIMS 的 report-engine / instrument-gateway / 微信等无关模块。

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml` — 建仓库根 parent（packaging=pom，Boot 2.7.18，Java 8，声明全部 Java 子模块与 dependencyManagement）— 统一版本与 reactor
- [x] `forma-common/` — 建空可编译公共模块 — 供 domain/pi/application 依赖
- [x] `forma-domain/` · `forma-application/` · `forma-infrastructure/` · `forma-interfaces/` — 按 AD-11 建模块与最小包骨架（`com.xmut.ebus`）— 后续故事承载体
- [x] `pi-ai/` · `pi-agent/` — 从旁路 LIMS 全量 vendor-copy 并改名，裁剪对 LIMS 业务模块的依赖使可编译 — 满足 AD-3
- [x] `forma-starter/` — 唯一 `@SpringBootApplication` + 可启动配置（连 compose MySQL）— compose 可拉起应用
- [x] `forma-web/` — `create-vue`（或等价）生成 Vue3/Vite/TS 壳并 lockfile — 前端承载体
- [x] `APP-META/docker-config/` · `APP-META/bootstrap/` — compose（starter+MySQL）、Dockerfile、environment 示例、最小 SQL/脚本占位 — 对齐 AD-10
- [x] `AGENTS.md` — 把 §1 Commands 换成可复制的真实命令 — 脚手架落地后可跑

**Acceptance Criteria:**
- Given 仓库尚无业务脚手架，when 落地 parent 与平铺模块及 Pi/Web/APP-META，then `mvn -pl forma-starter -am` 可编译，且依赖方向符合 AD-11
- Given APP-META 已落地，when 执行 compose up，then 至少拉起 starter 与 MySQL
- Given Pi 模块已落地，when 检查目录与 artifact，then 名为 `pi-ai`/`pi-agent` 且不依赖 LIMS 发版构件
- Given 前端壳已落地，when 查看 `forma-web`，then 为 Vite/Vue3/TS 工程且非 Maven 子模块

## Implementation Notes

- **Pi 包名：** 保留 `com.xmut.lims.pi.*`（未迁到 `com.xmut.ebus.pi`），最小改动能编译；Maven artifact / 目录已改为 `pi-ai` / `pi-agent`，依赖改为 `forma-common`（不再依赖 LIMS 发版）。
- **Pi 编译裁剪：** ebus-common 无 web 传递依赖，故 `pi-ai` 显式加了 `slf4j-api`。
- **本地无模型 Key：** 原 `PiAiAutoConfiguration` 在无厂商时 `return null`，导致 `AgentSession` NPE、starter 起不来。已改为注册 `StubModelProvider`（端口形状不变）。
- **MySQL 镜像：** `mysql:8.0.36`；connector 仍钉 `8.0.33`（与 LIMS parent 一致）；`mybatis-spring-boot` 钉 `2.3.1`。
- **Dockerfile：** 默认 `BASE_IMAGE=eclipse-temurin:8-jre`；本机 DaoCloud 镜像源对 Hub blob 返回 401，BuildKit 拉不动。验证时用已有 Java 8 镜像 tag 为 `lippi-ebus-java8:local`，并设 `STARTER_BASE_IMAGE` + `DOCKER_BUILDKIT=0`。需先 `mvn … package` 再 build（COPY 预构建 jar）。
- **Compose 路径：** SQL 卷为 `APP-META/bootstrap/sql`（相对 `docker-config` 用 `../bootstrap/sql`）。
- **验证结果（2026-09-24）：**
  - `mvn -pl forma-starter -am -DskipTests compile` → BUILD SUCCESS
  - `docker compose … config` → 含 `mysql` + `starter`
  - `docker compose … up -d` → 两服务 Up；`/actuator/health` → 200；随后 `down` 清理
- **残留风险：** Hub/镜像源不稳定时默认 `docker compose up --build` 可能失败，需本地 BASE_IMAGE 绕过；Pi 仍带 LIMS 技能资源（certificate-ocr 等），后续故事可再裁。
- **评审补丁（2026-09-24）：** README 文案「Pi 有桩」；`DB_PASSWORD` 默认 `ebus123`；忽略 `.lippi-pi/`；`build.sh` 将 jar 拷到 `APP-META/docker-config/build/app.jar` 供 Dockerfile COPY；README 用 `set -a && . .env`；starter `restart: on-failure`；补 `PiAiAutoConfiguration` 无 Key→Stub 的 `ApplicationContextRunner` 测试。

## Spec Change Log

## Review Triage Log

- false — 评审 diff 未含全部 Pi/web 文件体：属 NO_VCS 打包取舍，不构成产品缺陷。
- false — `target/` 出现在评审清单：根 `.gitignore` 已忽略 `target/`，非交付源码。
- medium → patch — `pi-agent/.lippi-pi/state.db` 未被忽略，本地 Agent 状态可能被误跟踪；应在 `.gitignore` 忽略 `.lippi-pi/`。
- low（拒修）— `.vscode/` 与 web 下 `extensions.json` 冲突：日常几乎碰不到，且修起来要改 ignore 规则复杂度。
- medium → patch — README 写「Pi 无桩」与 stub 实现矛盾，属文案错误。
- false — FE 仍是 create-vue 默认文案：Intent 只要「Vue 壳」，品牌落地页属 1.6。
- low（拒修）— Router 已注册但无 RouterView：壳可接受，后续故事再接线。
- false — 无 Vite proxy：非本故事 Intent；联调形态后续故事再定。
- false — `engines.node` 排除 Node 20：与 Spine Stack（`^22.18 || >=24.12`）一致。
- low（拒修）— lockfile 钉 npmmirror：本机习惯，不挡验收。
- low（拒修）— parent 同时继承 Boot parent 又 import BOM：常见写法，无即时伤害。
- false — DM 预钉 JWT/OSS：仅版本管理，未实现功能，不违背 Never。
- low（拒修）— `health.show-details: always`：仅本地脚手架暴露，生产 hardening 后置。
- false — compose 默认本地口令：本地可复现所需；密钥走 env 覆盖已支持。
- medium → patch — `.dockerignore` 的 `**/target` 再 `!…/jar` 在 Docker 规则下不可靠；应用 `build.sh` 把 jar 拷到非 excluded 路径再 COPY。
- defer — Pi 仍含 LIMS 技能资源（certificate-ocr 等）：vendor-copy 残留，已记 Implementation Notes，裁剪后置。
- medium → patch — `application.yml` 中 `DB_PASSWORD` 默认空，本地未 export 时无法连 compose MySQL（默认 `ebus123`）。
- defer — MySQL volume 改密后不重跑 init：通用 Docker 坑，非本故事特有缺陷。
- low → patch — starter 无 `restart`：JVM 偶发退出后服务停住；加 `restart: on-failure` 即可。
- defer — Hub/镜像源 401：已文档化 `STARTER_BASE_IMAGE` 绕过。
- medium → patch — README `export $(grep|xargs)` 遇空格/元字符会坏；改为 `set -a; . .env; set +a`。
- false — 「仅 compose up 无 jar 则失败」：README/`build.sh` 已要求先 package，且本机已验证成功路径。
- medium → patch（verification-gap）— `PiAiAutoConfiguration` 无 Key→Stub 路径无测试加载，可回归而 `mvn test` 仍绿；补 `ApplicationContextRunner` 测。
- defer（verification-gap）— compose starter↔MySQL 无自动化观察：本故事 Verification 刻意手工 compose；Testcontainers/smoke 后置。
## Design Notes

- 业务模块包一律 `com.xmut.ebus.*`。若选全量 Pi 拷贝：优先改目录/artifactId/group 坐标；包名是否从 `com.xmut.lims.pi` 迁到 `com.xmut.ebus.pi` 以实现时最小改动能编译为准，并在 Implementation Notes 记一笔。
- Compose 只保留 starter + MySQL（及必要 base 镜像层）；不要把 LIMS 的 OnlyOffice/微信等服务带进来。
- mybatis-spring-boot 钉 **2.3.1**（与 LIMS parent 一致）；MySQL 镜像钉 8.0.x 精确补丁。

## Verification

**Commands:**
- `mvn -pl forma-starter -am -DskipTests compile` -- expected: BUILD SUCCESS
- `docker compose -f APP-META/docker-config/docker-compose.yml config` -- expected: 合法且含 starter 与 mysql 服务
- `docker compose -f APP-META/docker-config/docker-compose.yml up -d` -- expected: 两服务 Up（或 healthy）；随后 `down` 清理

**Manual checks (if no CLI):**
- 根目录无 `backend/` 包一层；parent `<modules>` 含全部 Java 模块且不含 `forma-web`
