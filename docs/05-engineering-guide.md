# 本地工程指南

## 共享基础设施

本地使用 `dev-infra/docker-compose.yml` 管理的共享 PostgreSQL、Redis、RustFS、RabbitMQ 和 Elasticsearch。本机共享目录为 `~/001-Own/01-Dev/dev-infra`。Tsuki 复用现有中间件账号，在共享 PostgreSQL 实例上单独创建一个 `tsuki` 数据库。后端数据源指向 `tsuki`，由 Flyway 在库内执行项目迁移。

宿主机运行应用时，通过 `localhost` 和共享环境映射的端口连接。容器内运行时，将应用容器接入共享网络 `dev_infra_network`，通过 Compose 服务名和容器端口连接。端口映射及网络以 `dev-infra/docker-compose.yml` 为准，宿主机端口和凭据从 `dev-infra/.env` 获取，作为本地应用配置提供。

| 服务 | 共享网络内地址 | 宿主机端口配置项 |
| --- | --- | --- |
| PostgreSQL | `postgres:5432` | `POSTGRES_PORT` |
| Redis | `redis:6379` | `REDIS_PORT` |
| RustFS S3 API | `rustfs:9000` | `RUSTFS_PORT` |
| RabbitMQ AMQP | `rabbitmq:5672` | `RABBITMQ_PORT` |
| Elasticsearch HTTP | `elasticsearch:9200` | `ES_PORT` |

## 启动后端

在 `backend/` 执行 `./mvnw -pl tsuki-app -am spring-boot:run`。主配置默认激活 `dev`，该 profile 导入 `tsuki-app/config/application-dev.yml` 中的本机配置参数。启动时查看 Flyway 连接的数据库和 `Started TsukiApplication` 日志。

本地 `dev` 启动前，除 PostgreSQL 连接参数外，还需在 `tsuki-app/config/application-dev.yml`（或环境变量）中提供非空的认证配置：

- `tsuki.auth.issuer`（`TSUKI_AUTH_ISSUER`）
- `tsuki.auth.access-secret`（`TSUKI_AUTH_ACCESS_SECRET`，Base64 解码后至少 32 字节）
- `tsuki.auth.admin-aud`（`TSUKI_AUTH_ADMIN_AUD`）
- `tsuki.auth.access-ttl`（`TSUKI_AUTH_ACCESS_TTL`，默认 `15m`，至少 `1m`）

Redis 连接参数在公共配置中默认指向 `127.0.0.1:6379`、空密码，可通过 `TSUKI_REDIS_HOST`、`TSUKI_REDIS_PORT`、`TSUKI_REDIS_PASSWORD` 覆盖（若本地共享 Redis 端口非 `6379` 或设置了密码，需在 `application-dev.yml` 中同步填写）。当前 `tsuki-auth` 使用 Redis 执行登录限流脚本。

### 生产环境配置（prod profile）

生产环境通过 `SPRING_PROFILES_ACTIVE=prod`（或 `--spring.profiles.active=prod`）激活 `prod` profile，支持通过环境变量或同目录下的 `config/application-prod.yml` 提供敏感配置：

- 数据库连接：`TSUKI_DB_URL`、`TSUKI_DB_USERNAME`、`TSUKI_DB_PASSWORD`
- 认证与 Redis：复用上述 `TSUKI_AUTH_*` 与 `TSUKI_REDIS_*` 环境变量
- 反代与监听：`prod` 固定监听 `server.address: 127.0.0.1`，启用 `server.forward-headers-strategy: native`，并将 `server.tomcat.remoteip.internal-proxies` 限定为 `"127\\.0\\.0\\.1"`，由同机 HTTPS 反向代理终止 TLS 并转发真实客户端地址。

### 初始化管理员账号

在完成后端启动与 Flyway 建表后，通过交互式终端生成 `{bcrypt}` 密码哈希并手工插入 `user_accounts`：

1. 在 `backend/` 目录下编译 `tsuki-auth`、导出依赖类路径并在真实终端运行 `PasswordHashGenerator`（依赖 `System.console()`，不支持非 TTY 控制台；密码需为 8 到 72 位可见 ASCII 字符，且不能包含空格）：

```bash
./mvnw -pl tsuki-auth -am compile dependency:build-classpath -Dmdep.outputFile="$PWD/tsuki-auth/target/auth.cp"
java -cp "tsuki-auth/target/classes:$(cat "$PWD/tsuki-auth/target/auth.cp")" com.nep.tsuki.auth.tools.PasswordHashGenerator
```

2. 连接本地 PostgreSQL 的 `tsuki` 数据库，将工具输出的完整 `{bcrypt}...` 哈希写入管理员记录（`username` 填小写用户名，不超过 64 位，且建议符合应用层 3–32 位字母、数字和下划线规则，主键和时间戳由数据库填充）：

```sql
INSERT INTO user_accounts (username, nickname, password_hash, role, status)
VALUES ('admin', 'admin', '{bcrypt}$2a$10$替换为工具输出的完整哈希', 'ADMIN', 'ACTIVE');
```

## 启动前端

使用 Node 24 和 pnpm 12.11.2。在 `frontend/` 执行 `pnpm install`，再执行 `pnpm dev` 启动管理端。`pnpm check` 依次运行类型检查、ESLint、Prettier 格式检查和构建。`pnpm build` 生成管理端静态产物，`pnpm preview` 在本地预览构建结果。

管理端开发服务器将 `/api/v1` 转发到 `http://127.0.0.1:8080`，保留原路径。后端改用其他端口时，在 `frontend/apps/admin/.env.local` 设置 `API_PROXY_TARGET`，或为启动进程提供同名环境变量。

## 接口联调与日志

业务模块声明 `/api/v1` 下的路由，管理接口使用 `/api/v1/admin`。浏览器通过同源相对路径访问 API，开发时由前端开发代理转发。JSON 时间使用 ISO 8601 字符串，业务 ID 在 API DTO 中使用字符串，分页和计数使用数字。响应结构与公共错误见[全局错误码](04-api-design/00-error-codes.md)。

`dev` 提供 `/swagger-ui.html` 和 `/v3/api-docs`。编写和维护接口时，优先让 Springdoc 从 Spring MVC 映射、Java 类型和 Bean Validation 推导文档。只有生成的契约缺少调用方需要的语义时，才补最小范围的 Swagger 注解。长示例、业务说明和详细错误列表写进对应模块的 API 文档。

后端代码使用 Lombok 的 `@Slf4j` 记录日志。Spring Boot 通过 `application.yml` 配置 Logback 的输出和滚动策略。控制台和文件日志记录请求 ID，可通过响应头 `X-Request-ID` 定位该请求的访问记录和异常。开发日志默认位于 `backend/tsuki-app/logs/tsuki.log`，可以用 `TSUKI_LOG_FILE` 指定文件位置。

## Git 与持续集成

从已同步的 `main` 创建短分支，分支名采用 `类型/简短英文描述`，例如 `ci/github-actions`。每次提交包含一项可独立 Review 和回退的改动。提交信息采用 Conventional Commits 格式，只写一行 `type(scope): 中文摘要`，例如 `ci(actions): 添加前后端检查`。 [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow) · [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)

PR 说明改动目的和验证结果。合并前自行 Review，确认分支已与 `main` 同步，且 `backend` 和 `frontend` 两项检查均通过。使用普通 merge commit 保留分支提交历史，合并后清理短分支并同步本地 `main`。

仓库方案支持 Rulesets 时，对 `main` 要求通过 PR 合并、两项检查通过且分支与 `main` 同步。所需审批数为 `0`，禁止强推和删除 `main`。仓库方案不支持时，合并前手动核对相同条件。 [GitHub Rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)

CI 在目标为 `main` 的 PR、推送到 `main` 时运行，也支持手动触发。两个检查并行执行。 [CI 工作流](../.github/workflows/ci.yml)

| 检查 | 目录 | 本地复现命令 |
| --- | --- | --- |
| `backend` | `backend/` | `./mvnw --batch-mode --no-transfer-progress verify` |
| `frontend` | `frontend/` | 依次执行 `pnpm install --frozen-lockfile` 和 `pnpm check` |

后端 CI 构建所有 Maven 模块并运行已有测试。应用启动与数据库联调使用本地共享环境验收。

## 依赖版本与兼容性

应用依赖版本以后端 POM 和前端锁文件为准，中间件镜像和插件版本以共享 Compose 及对应 Dockerfile 为准。后端初始化时，通过 Maven 依赖解析与正常启动确认 Java、Spring Boot、MyBatis-Plus 和 Flyway 的组合。

共享搜索环境配置为 Elasticsearch 9.2.2 + IK，Spring Boot 4.1.2 管理的 Java 客户端为 9.4.8。较新的 Java 客户端连接较旧的服务端时，兼容性没有官方保证。接入搜索时，优先选择与共享服务端版本匹配的客户端组合，并通过创建索引、IK 分词和查询确认可用性。 [Boot 依赖版本](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) · [Elastic 客户端兼容策略](https://www.elastic.co/docs/reference/elasticsearch/clients/java#elasticsearch-server-compatibility-policy)
