# Forma

[English](README.md) · **简体中文**

https://github.com/user-attachments/assets/cc93e364-aab7-44cf-a837-154b0f841f07

**Forma** 是个人助理：把日常工作里的技能，落到 Agent 能做完的活上。一类活是**场景**，怎么干是 **Skill**；选场景、跑 Agent，在 Computer 里审成形成果。

```text
日常技能 → 场景 / Skill → Agent → Computer 可用成果
```

## 为什么需要

### 客户痛点

人日常干活靠的是自己练出来的技能：判断标准、步骤顺序、成品长什么样。这些东西在脑子里、在备忘录里，每次还是自己上手。通用 AI 可以陪着聊，却接不住这套做法，也交不出你平时会交的那份结果。

卡住的地方通常有三处。

**技能带不走。** 会干的是人，不是系统。换一台电脑、换一个人、过一周，同一件事又要从头想。

**Agent 接不住活。** 没有把日常技能写成它能执行的步骤和标准，它就只能泛泛回答，做不完你手头那件具体的事。

**交差还是人肉。** 真正要带走的是成形的交付物，不是一段对话。聊完仍要自己整理、改格式、重写。

### Forma 怎么做

Forma 把日常技能收进场景和 Skill。用户选场景、跑一轮，Agent 按这套技能做完，人在 Computer 里审稿、改、带走。

拍板仍是用户。产品负责把技能收进来、让 Agent 跑起来、把结果按这份活应有的样子交出来。电商开店、小红书种草是先落地的两套；同一套用法可以继续往里收别的日常技能。

### 适合谁

- 希望把自己日常会干的活，变成 Agent 能重复做完的人
- 一人或小团队，要把一类工作的做法固化下来、反复用
- 能判断结果，但不想每次从空白页和提示词重新来

不适合只想随便聊聊、并不打算把某项技能交给 Agent 的人。

## 目录

- [功能](#功能)
- [快速开始](#快速开始)
- [架构](#架构)
- [领域模型](#领域模型)
- [配置](#配置)
- [开发](#开发)
- [文档](#文档)
- [现状](#现状)
- [联系](#联系)



## 功能

- 注册 / 登录（JWT）与账户设置
- 场景画廊：五类筛选（科技 / 电商 / 内容 / 体育 / 生活）；`AVAILABLE` + `COMING_SOON` 灰卡；积分全站共用
- 工作台：电商（选品 → 素材）、小红书（选题 → 笔记 / 拆解）
- Pi Agent 计费预占；SSE 用 `fetch` + JWT（不用原生 `EventSource`）
- Computer DocPreview：GitHub README 风；Forma 视图协议（`.forma-*`、`data-forma-*`）
- 场景包内 Mustache `view`；仅在可用 `view` 落库后结算
- 积分档位（FREE / PRO / PLUS）、月重置、管理员改档白名单
- Compose：MySQL + starter
- 无模型 Key 时走 Stub（接口形状不变）



## 快速开始



### 依赖


| 依赖                 | 用途                    |
| ------------------ | --------------------- |
| JDK 8 + Maven 3.8+ | 后端（`forma-starter`）   |
| Node 20+ / npm     | 前端（`forma-web`）       |
| Docker Compose     | MySQL + starter（可选一键） |


默认端口：

- `3306` MySQL
- `8080` API（starter）
- `5173` Vite 开发（web）



### 1. 环境变量

```bash
cp APP-META/docker-config/environment/.env.example APP-META/docker-config/environment/.env
# 填 JWT_SECRET（≥32 字节）、APIFY_TOKEN、DEEPSEEK_API_KEY 等。
# 本地 IDE 点运行也会读取这一份（不覆盖系统里已经有的同名变量）。
```



### 2. 后端

```bash
set -a && . APP-META/docker-config/environment/.env && set +a
mvn -pl forma-starter -am -DskipTests compile
mvn -pl forma-starter -am spring-boot:run
```



### 3. 前端

```bash
cd forma-web
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`（落地壳 `/welcome`）。

### 4. Docker（starter + MySQL）

```bash
cp APP-META/docker-config/environment/.env.example APP-META/docker-config/.env
./APP-META/bootstrap/build.sh
docker compose -f APP-META/docker-config/docker-compose.yml up -d --build
```

若 Hub / 镜像源拉 JDK 基础镜像 401：

```bash
export STARTER_BASE_IMAGE=<本地 Java8 镜像>
export DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0
docker compose -f APP-META/docker-config/docker-compose.yml up -d --build
```

清理：

```bash
docker compose -f APP-META/docker-config/docker-compose.yml down
```

**密钥**（JWT、模型）只放环境变量 / `.env`，勿提交实值。

本地 MySQL / `dev` 默认按 **北京时间** 写 `DATETIME`（`MYSQL_TZ` + `DB_SERVER_TIMEZONE`）。架构上生产仍以 UTC 为准。改 TZ 需重启 MySQL；**旧行不会自动换算**。

### 5. 冒烟路径

1. 注册 / 登录 → 打开 **场景**
2. 进入 **电商开店** 或 **小红书种草**
3. 跑一条 Skill（选品 / Listing / 选题 / 笔记）
4. 确认 Computer 出现投影文档；积分仅在成果落库后结算



## 架构


| 包                           | 路径                                                      | 角色                                     |
| --------------------------- | ------------------------------------------------------- | -------------------------------------- |
| Web                         | `forma-web/`                                            | Vue 3 + Vite SPA（非 Maven 子模块）          |
| Starter                     | `forma-starter/`                                        | Spring Boot 入口                         |
| Application                 | `forma-application/`                                    | 用例、计费编排、Computer 投影                    |
| Domain / Infra / Interfaces | `forma-domain/` · `…-infrastructure/` · `…-interfaces/` | DDD 分层                                 |
| Pi 扩展                       | `forma-pi-extension/`                                   | `core` + 电商/小红书/科技场景包；总开关 `bundle` |
| Pi 运行时                      | `pi-ai/` `pi-agent/`                                    | Agent                                  |
| 运维                          | `APP-META/`                                             | Compose、Dockerfile、bootstrap SQL       |


### 分层设计

业务模块依赖方向：**接口 → 应用 → 领域 ← 基础设施**。积分只经 **CreditLedger** 变更。Agent 工具不得写账本。可用成果 = 可投影的 Computer `view`，再 persist → settle（见 Spine AD-4 / AD-7）。

自上而下（调用方 → 工作台 → 端口 → 存储）：

![Forma 分层架构](README/assets/forma-architecture-flow.svg)


| 层        | 负责                                              | 禁止                         |
| -------- | ----------------------------------------------- | -------------------------- |
| 接口       | REST / SSE、JWT `userId`                         | 业务规则、SQL、模型密钥              |
| 应用       | 预占、AgentSession、落库、结算、Computer 投影              | 绕过 CreditLedger 改积分        |
| 领域       | Identity、CreditLedger、SceneCatalog、成果、反馈       | 框架 / HTTP                  |
| Pi 扩展    | 场景包、tool、Mustache `view`                         | 写积分账本                      |
| Pi 运行时   | Agent 循环、工具、模型 I/O                              | 产品策略、积分                    |
| 基础设施     | MyBatis、JWT、MediaStore                          | 第二套账本或第二套成果真相              |


架构不变量：  
`[ARCHITECTURE-SPINE.md](sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-26/ARCHITECTURE-SPINE.md)`

### 计费生成流程

预占 1 分 → 流式跑 Agent → 投影 Computer `view` → 成果落库 → **再**结算。SSE 结束不等于扣分。HITL（`ask_human`）挂起时不结算。

![Forma 计费生成流程](README/assets/forma-billing-flow.svg)

库表引导（**新** MySQL 卷）：`APP-META/bootstrap/sql/001_schema.sql` + `002_seed_scene.sql`。改过 schema 要重建卷（`docker compose … down -v`）。

### 目录结构

```text
forma/
├── forma-starter/          # Boot 入口
├── forma-application/      # 应用服务
├── forma-domain/
├── forma-infrastructure/
├── forma-interfaces/
├── forma-pi-extension/     # core · ecommerce · xiaohongshu · tech · bundle
├── pi-ai/pi-agent/      # Pi 运行时
├── forma-web/              # Vue SPA
├── APP-META/               # Compose · SQL · bootstrap
├── sdd/                    # PRD · UX · Spine · 编码分片
└── AGENTS.md               # 给编码 Agent 的短手册
```



## 领域模型


| 概念                   | 含义                                           |
| -------------------- | -------------------------------------------- |
| Forma                | 产品名——场景化 Skill，坯 → 成品                        |
| Scene / SceneCatalog | 画廊行（`sceneCode`、`AVAILABLE` / `COMING_SOON`） |
| SceneCapabilityPack  | 代码资源：提示词 + skill/tool 白名单                    |
| Skill                | 包内可运行剧本（如 picklist、xhs-note）                 |
| AgentSession         | 经 Pi 编排的会话                                   |
| GenerationRun        | 一次计费波次（预占 + 成果引用 + skillId）                  |
| Computer / view      | DocPreview 展示的投影文档                           |
| CreditLedger         | 余额 / 预占 / 结算 / 月重置的唯一写者                      |
| MediaObject          | 对象存储中的图；Listing 有图时用 `mediaObjectId`         |




## 配置


| 变量                                       | 使用者           | 说明                   |
| ---------------------------------------- | ------------- | -------------------- |
| `JWT_SECRET` / `JWT_EXPIRATION_MS`       | API           | HS256；密钥 ≥ 32 字节     |
| `MYSQL_*` / `DB_*`                       | API + Compose | 见 `.env.example`     |
| `MYSQL_TZ` / `DB_SERVER_TIMEZONE`        | MySQL / JDBC  | 本地默认 `Asia/Shanghai` |
| `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY` | Pi            | 空则 Stub              |
| `CREDIT_ADMIN_USER_IDS`                  | 改档            | 逗号分隔用户 UUID；空则全部 403 |
| `STARTER_BASE_IMAGE`                     | Compose       | Hub 拉失败时覆盖基础镜像       |


完整注释：`[APP-META/docker-config/environment/.env.example](APP-META/docker-config/environment/.env.example)`。

## 开发

```bash
# 后端
mvn -pl forma-starter -am -DskipTests compile
mvn -pl forma-starter -am test
# 真 MySQL IT 需 Docker；无则 skip

# 前端
cd forma-web && npm run lint && npm run build
cd forma-web && npm test -- --run
```

优先用仓库脚本 / Compose，不要猜命令。编码分片：`[sdd/context/](sdd/context/)`（只开 `02-be` / `03-fe` / `04-quality` 之一）。Agent 短手册：`[AGENTS.md](AGENTS.md)`。

## 文档


| 文档                                                                                                                                     | 用途                             |
| -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| [AGENTS.md](AGENTS.md)                                                                                                                 | 命令、边界、从哪读起                     |
| [Architecture Spine（2026-09-26）](sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-26/ARCHITECTURE-SPINE.md) | 架构不变量                          |
| [sdd/context/](sdd/context/)                                                                                                           | 后端 / 前端 / 质量编码分片               |
| [sdd/planning-artifacts/](sdd/planning-artifacts/)                                                                                     | PRD · UX · 架构                  |
| [docs/superpowers/](docs/superpowers/)                                                                                                 | 特性规格与计划（Computer、Mustache 视图等） |




## 现状

**已可用（本地 / 近端）：** 登录注册、积分、场景画廊、电商 + 小红书 + 科技速读工作台、计费 Agent 运行、Computer DocPreview（Forma 视图协议）、历史、账户设置、Compose 引导。

**延期 / 灰卡：** 装备选购对比（体育）、短视频带货、周末行程（生活=出行）等 `COMING_SOON`，待包与门禁开放。场景路线图见 `sdd/planning-artifacts/scene-product-plan-2026-10-04.md`。

## 联系

问题或反馈：[xiaoyan.wjw@gmail.com](mailto:xiaoyan.wjw@gmail.com)
