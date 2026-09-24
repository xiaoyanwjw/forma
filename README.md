# Adam（lippi-ai-ebusiness）

网页自助「选品清单 + Listing」积分工具。本仓库为 **SPA（Vue）+ Spring Boot 模块化单体**。

## 怎么跑起来

### 后端编译

```bash
mvn -pl lippi-ai-ebus-starter -am -DskipTests compile
```

本地启动（需本机 MySQL，或先起 compose 里的 mysql）：

```bash
# 建议先复制环境变量示例
cp APP-META/docker-config/environment/.env.example APP-META/docker-config/.env
set -a && . APP-META/docker-config/.env && set +a
mvn -pl lippi-ai-ebus-starter -am spring-boot:run
```

### 前端

```bash
cd lippi-ai-ebus-web
npm install
npm run dev
```

### Docker（starter + MySQL）

```bash
cp APP-META/docker-config/environment/.env.example APP-META/docker-config/.env
./APP-META/bootstrap/build.sh
docker compose -f APP-META/docker-config/docker-compose.yml up -d --build
# 清理
docker compose -f APP-META/docker-config/docker-compose.yml down
```

若 Docker Hub / 国内镜像源拉取 JDK 基础镜像失败（常见 401），可把本机任意 Java 8 镜像打成 `lippi-ebus-java8:local`，再：

```bash
export STARTER_BASE_IMAGE=lippi-ebus-java8:local
export DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0
docker compose -f APP-META/docker-config/docker-compose.yml up -d --build
```

密钥、模型 Key、OSS Key **只**放环境变量 / compose `.env`，不要提交进仓。

## 目录（扁平仓）

| 路径 | 用途 |
|------|------|
| `lippi-ai-ebus-*` | 业务 Java 模块（无 `backend/` 包一层） |
| `lippi-pi-ai` / `lippi-pi-agent` | Pi 运行时（自 LIMS vendor-copy） |
| `lippi-ai-ebus-web` | Vue3 / Vite / TS（**非** Maven 子模块） |
| `APP-META/` | compose / Dockerfile / bootstrap SQL |
| `sdd/` | 规划与实现规格 |
| `AGENTS.md` | 给编码 Agent 的短手册 |

包根：`com.xmut.ebus`。架构不变量见 `sdd/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md`。

## 说明

- Story 1.2 已落地注册/登录 JWT：`POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`GET /api/v1/me`；前端 `/register`、`/login`。
- Story 1.3 已落地积分账本：注册同事务建免费档（20）；`GET /api/v1/credits`（JWT）返回 `tier` / `available` / `balance` / `reserved` / `nextResetAt`；预占·结算·释放仅服务层（Epic 2 接线），无公开写 REST。已有 MySQL volume 需重建或执行 `APP-META/bootstrap/sql/002_ebus_credit.sql`。
- Story 1.4：登录/注册成功后默认进 `/credits`，展示当前档、本月剩余、下次重置（东八区）与三张套餐卡（Manus 式层次、无支付按钮）；`available === 0` 时常驻不足人话（可升级或等待重置）；`/me` 仍只身份，可与套餐页互跳。
- Story 1.5：管理改档 `POST /api/v1/admin/credits/change-tier`（JWT + 环境变量 `CREDIT_ADMIN_USER_IDS` 白名单；名单空则全部 403）。仅允许 FREE→PRO/PLUS 升级；改档后锚点重算、余额=新档月额度、保留 reserved；审计表 `ebus_credit_tier_change`。已有 MySQL volume 需执行 `APP-META/bootstrap/sql/003_ebus_credit_tier_change.sql`。无支付网关。
- 编码规约见 `sdd/context/`（后端 `02-be` §5：`StringUtils`/`ObjectUtils` + `LoggerUtils`；请求 `X-Trace-Id` / MDC `traceId`）。
- 本地可不配大模型密钥；无 Key 时 Pi 有桩（StubModelProvider），端口形状不变。
- 需配置 `JWT_SECRET`（≥32 字节），见 `APP-META/docker-config/environment/.env.example`。
