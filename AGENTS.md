<!-- bmad:context -->
<!-- Verified 2026-09-24 against greenfield (no git SHA yet). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

# AGENTS.md

给编码 Agent 的操作手册（偏短）。人读长文用 `README*`（有则）；细则按需打开，勿整目录灌入。

**产品一句话：** Adam——网页自助「选品清单 + Listing」积分工具。  
**栈：** `lippi-ai-ebus-*`（Java 8 / Spring Boot 2.7 多模块）+ `lippi-ai-ebus-web`（Vue3 / Vite）+ MySQL + 阿里云 OSS。  
**架构硬约定：** `sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md`（冲突时先对齐 Spine）。

---

## 1. Commands

```bash
# Backend
mvn -pl lippi-ai-ebus-starter -am -DskipTests compile
mvn -pl lippi-ai-ebus-starter -am test
mvn -pl lippi-ai-ebus-starter -am spring-boot:run

# Frontend
cd lippi-ai-ebus-web && npm install && npm run dev
cd lippi-ai-ebus-web && npm run lint && npm run build

# Local runtime（APP-META；密钥见 docker-config/environment/.env.example）
cp APP-META/docker-config/environment/.env.example APP-META/docker-config/.env   # 按需改密
./APP-META/bootstrap/build.sh   # 先打 jar，再 compose build
# 若 Hub/镜像源 401：export STARTER_BASE_IMAGE=<本地Java8镜像> DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0
docker compose -f APP-META/docker-config/docker-compose.yml up -d --build
docker compose -f APP-META/docker-config/docker-compose.yml down
```

优先用仓库脚本 / compose；不要猜命令。

---

## 2. Testing

- 改动处补测；提交前相关测试应绿。
- 后端：`mvn -pl lippi-ai-ebus-starter -am test`（先单模块再全 reactor）。
- 前端：`cd lippi-ai-ebus-web && npm run lint`；构建 `npm run build`。
- Compose 冒烟：`docker compose -f APP-META/docker-config/docker-compose.yml config`，再 `up -d` 确认 mysql + starter。

---

## 3. Project structure

| 路径 | 用途 |
|------|------|
| `lippi-ai-ebus-*` | Java 业务多模块（平铺；无 `backend/` 包一层） |
| `lippi-pi-ai/` · `lippi-pi-agent/` | Pi 运行时（自 LIMS 拷贝改名） |
| `lippi-ai-ebus-web/` | Vue SPA |
| `APP-META/docker-config/` · `APP-META/bootstrap/` | Compose / Dockerfile / SQL（对齐 LIMS） |
| `sdd/context/` | DDD / 编码分片（按需 1 片） |
| `sdd/planning-artifacts/` | PRD · UX · Architecture Spine |
| `AGENTS.md` | 本文件 |

**Where to start（读顺序）**

1. 本文件  
2. Spine（架构不变量）  
3. 编码分片（只开 1 个）：[`02-be.md`](sdd/context/02-be.md) / [`03-fe.md`](sdd/context/03-fe.md) / [`04-quality.md`](sdd/context/04-quality.md) · 索引 [`01-coding-style.md`](sdd/context/01-coding-style.md)  
4. 当次相关 PRD / UX  

---

## 4. Code style

- **不要**把整份风格指南贴进会话；按端打开上一节分片。
- 包根 `com.xmut.ebus`；写 `*ApplicationService`，读 `*QueryService`；Controller 薄，注入 `userId`。
- Command 继承 `BaseCommand`（userId/username），`@SuperBuilder` 创建；参数守卫 `StringUtils`/`ObjectUtils`；埋点 `LoggerUtils`（见 `02-be` §5）。
- FE：`api` 只 HTTP；类型只在 `types`；详情靠路由 id + `get*`。
- 计费 SSE：`fetch` + `ReadableStream` + JWT；不用原生 `EventSource`。
- ID：对外/业务用 UUID 字符串（`biz_id`）；库内可有 `BIGINT` 自增代理主键。范本域与目录配方见 `02-be` / `03-fe`。

---

## 5. Git / PR

- 不要把密钥、`.env` 实值、模型/OSS Key 提交进仓。
- 提交说明写清「为什么」；大改动可拆 PR。
- 合并前跑通 §1 里与本次改动相关的检查（落地后：lint / test）。
- 与 Spine / 规约冲突时：先改文档或走 course-correction，再合代码。

---

## 6. Boundaries

**Always**

- 密钥走环境变量 / `APP-META` secrets
- 积分只经 CreditLedger；结算仅在可用成果落库之后
- 模型调用只经后端 `pi-ai`；业务入口用 `AgentSession`
- 架构冲突先对齐 Spine

**Ask first**

- 升 Spring Boot 大版本 / 换 Java 主版本
- 引入支付、微信登录、小程序、第二套主存储
- 抽 `pi-*` 为跨仓共享库（相对 vendor-copy）
- 改模块依赖方向（AD-11）或所有权表（AD-6）

**Never**

- 前端直连大模型或改积分账本
- 把 LIMS 的 `backend/` 布局或 `TenantContext` 多租户原样搬进本仓
- MySQL 存图片大字段；Listing 自造第二套主图 URL 真相（应用 `mediaObjectId`）
- 仅因 SSE 流结束就扣积分
- 手改生成物目录（若日后有 codegen）或提交 `node_modules` / `target`

---

## Sources of truth

1. 代码 + 测试 + `APP-META/bootstrap` SQL（有之后）  
2. Architecture Spine  
3. `sdd/context/*`（按需 1 片）  
4. `sdd/planning-artifacts/`（PRD / UX）  

<!-- /bmad:context -->
