# 工程指南

## 配置来源

本机共享基础设施由 `/home/nep/01-Own/infra/compose.yaml` 管理，连接凭据取自同目录 `.env`。应用在宿主机运行，连接基础设施映射到宿主机的端口。项目使用独立业务数据库，复用共享实例。

后端操作从 `backend/` 执行，本地配置放在该目录的 `.env`。配置项以 [.env.example](../backend/.env.example) 为清单，解析规则以 [Settings](../backend/src/tsuki/core/config.py) 为准；修改共享 infra 配置后，需同步应用实际使用的连接配置。

依赖与运行版本以各工程的依赖声明、版本文件及锁文件为准。管理端选型和独立工程边界见 [项目概览](01-project-overview.md#管理端架构)。

## 后端启动

以下命令由开发者在 `backend/` 执行。

1. 启动共享 infra 中的 PostgreSQL 与 Redis，在 PostgreSQL 中创建项目数据库 `tsuki`。
2. 执行 `uv sync --locked` 同步依赖，再执行 `cp .env.example .env` 建立本地配置，填写数据库、Redis 凭据及独立的管理员 JWT 密钥。
3. 本机使用 HTTP 时，将 `TSUKI_ADMIN_COOKIE_SECURE` 设为 `false`；生产 HTTPS 使用 `true`。
4. 按 [数据库迁移](#数据库迁移) 应用迁移。
5. 启动开发服务：

   ```bash
   uv run uvicorn tsuki.main:app --reload --host 127.0.0.1 --port 8000
   ```

本机 API 地址为 `http://127.0.0.1:8000`，交互式文档位于 `/docs`，OpenAPI 位于 `/openapi.json`。服务启动成功后，仍需通过实际业务请求确认数据库凭据和连接可用。

## 管理员维护

从 `backend/` 执行以下命令，按终端提示输入并确认密码：

```bash
uv run python -m tsuki.modules.auth.cli create-admin --username Admin
uv run python -m tsuki.modules.auth.cli reset-password --username Admin
```

密码重置会使该账号已有登录在下一次认证时失效。命令参数和退出约定以 `uv run python -m tsuki.modules.auth.cli --help` 及其子命令帮助为准；接口输入规则见 [管理员认证 API](04-api-design/01-auth-api.md)。

## 数据库迁移

在 `backend/` 执行：

```bash
uv run alembic upgrade head
```

结构变更通过迁移显式应用，应用启动不承担迁移操作。新增实体需让迁移环境加载其元数据，再生成和 Review 迁移；核对实际 SQL 后由开发者执行。表结构归档见 [数据库通用约定](03-database-design/00-conventions.md)。

## 管理端联调与部署

管理端命令从 `frontend/admin/` 执行，以该工程的 `package.json` 为准。前端环境变量清单见 [.env.example](../frontend/admin/.env.example)，本地开发配置放在 `.env.development.local`。

执行 `pnpm install --frozen-lockfile` 安装已锁定依赖；首次联调时建立 `.env.development.local` 并按 `.env.example` 填写 API 地址，然后执行 `pnpm dev`。访问管理端 `/login` 完成登录，登录后的管理区域通过 `/me` 恢复身份，并提供退出操作。

| 环境 | 访问方式 |
| --- | --- |
| 本机开发 | 管理端 `http://127.0.0.1:5173` 直接请求 API `http://127.0.0.1:8000` |
| 生产 | 管理端站点通过同源 `/api` 反向代理访问后端 |

开发时浏览器地址统一使用 `127.0.0.1`，后端 `TSUKI_CORS_ORIGINS` 填写 `["http://127.0.0.1:5173"]`。前端启用凭据发送，管理 API 写请求按 [认证接口契约](04-api-design/01-auth-api.md) 携带防护头；开发直接跨 Origin 访问，不再叠加开发代理。

联调时核对登录、刷新页面恢复身份、退出以及密码重置后重新认证，确认浏览器保存、发送及清除 Cookie。接口也可在后端 `/docs` 单独调试；界面反馈及模块职责见 [管理端架构](01-project-overview.md#管理端架构)。

生产反向代理保留 `/api/v1/admin` 路径和 Cookie。History 路由刷新时回退到 `index.html`；API 和缺失静态资源保持各自响应，不回退成页面 HTML。浏览器可见的前端配置只保存公开信息，服务密钥由后端持有。

## 接口类型更新

后端接口变化后，启动本机后端，在 `frontend/admin/` 执行 `pnpm api:types`，Review 生成差异并完成类型检查。生成文件随接口变更提交，普通开发启动和构建使用已提交类型，不依赖后端在线；生成来源与输出位置以该工程的脚本为准。

现有后端自定义错误响应未完整声明到 OpenAPI，错误类型须依据 [统一响应与全局错误码](04-api-design/00-error-codes.md)，不能直接采用生成文件中的 FastAPI 默认 422 结构。

## Git 协作

仓库托管于 GitHub，采用 `main` 与短期分支的工作流。日常改动从最新 `main` 创建分支，名称使用 `<type>/<kebab-case>`，例如 `feat/admin-auth`、`fix/login-return`。一个分支围绕一项明确改动组织，每次提交表达一个完整且可说明的变化。

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/)，使用英文类型、可选作用域和中文摘要，例如 `feat(admin): 接入管理员登录`。常用类型包括 `feat`、`fix`、`refactor`、`perf`、`docs`、`style`、`build`、`ci`、`chore`；需要说明原因或兼容性影响时补充正文。规范由开发者遵守，Git 自动生成的合并提交保留其原有格式。

日常改动通过 PR 合入 `main`，说明具体问题、改动后的行为和验证结果。CI 通过后使用 **Create a merge commit** 正常合并，保留分支提交；合并完成后删除短期分支。冲突在分支中解决后重新检查，`main` 保持可用，禁止强推和删除。该流程参考 [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)，按个人维护场景取消他人审批要求。

提交源码、迁移、公开配置示例、依赖锁文件及生成的接口类型。本机环境变量、凭据、依赖目录、构建产物、缓存和本地 Agent 资料使用 `.gitignore` 排除；后端与各前端工程分别维护自身规则，根目录规则只管理根目录的本地文件与工具状态。提交前检查暂存内容；已被 Git 跟踪的文件不会因补充忽略规则自动退出版本控制。

### GitHub 仓库设置

以下设置由开发者在公开仓库中完成；工作流文件不会自动启用仓库保护。

1. 默认分支设为 `main`；合并方式只启用 merge commit，启用合并后自动删除分支。
2. 工作流首次运行后，在 **Settings → Rules → Rulesets** 创建生效状态为 **Active**、目标为 `main` 的分支规则集。
3. 要求通过 PR 合并，审批人数设为 0；必需状态检查选择 **Backend checks** 和 **Admin checks**，来源选择 GitHub Actions。
4. 启用删除限制和阻止强推，不配置绕过角色或要求线性历史。规则细节参考 [GitHub 分支规则](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)。

## 本地检查与 CI

后端在 `backend/` 执行：

```bash
uv sync --locked
uv run --no-sync ruff check .
uv run --no-sync ruff format --check .
```

需要修正导入顺序或格式时，执行 `uv run --no-sync ruff check --fix .` 和 `uv run --no-sync ruff format .`，Review 修改后重新检查。依赖声明变化时先执行 `uv lock`，检查并提交锁文件差异，再同步环境。

管理端在 `frontend/admin/` 执行 `pnpm check`；需要格式化时先执行 `pnpm format`。检查入口和配置分别以各工程的依赖声明与工具配置为准。

CI 复用本地检查，以锁文件安装依赖；失败时先在对应工程执行上述命令复现。检查不需要启动后端、连接数据库或提供本机凭据。迁移、接口类型生成及部署由开发者按对应流程显式执行。

工作流的触发条件、运行环境和缓存设置以 [CI 配置](../.github/workflows/ci.yml) 为准。修改 CI 时核对 [pnpm 安装说明](https://github.com/pnpm/action-setup)、[uv 集成说明](https://docs.astral.sh/uv/guides/integration/github/) 与所使用版本相符；Actions 固定到已核实的完整提交 SHA，更新时同步核对版本注释。
