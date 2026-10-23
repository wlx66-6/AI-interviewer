# 排错笔记

按症状记的，遇到再查。命令默认在仓库根目录执行。

## 启动即退出：`Connection to localhost:5432 refused`

不是迁移脚本的问题，是后端连不到 PostgreSQL。先看容器起没起、端口有没有映射出来：

```bash
docker compose -f docker-compose.dev.yml up -d
docker ps --format '{{.Names}} {{.Ports}}'
```

`ai-interviewer-postgres` 要显示 `0.0.0.0:5432->5432/tcp`，`ai-interviewer-redis` 要显示 `0.0.0.0:6379->6379/tcp`。只显示 `5432/tcp` 说明是旧容器没有端口映射，重建即可：

```bash
docker compose -f docker-compose.dev.yml up -d --force-recreate postgres redis
```

本机 5432 / 6379 被别的项目占着就改 `.env` 里的 `POSTGRES_PORT` / `REDIS_PORT`，同时改 `docker-compose.dev.yml` 的端口映射。

## 表结构和数据对不上

schema 由 Flyway 管，`app/src/main/resources/db/migration/` 下每加一个文件就是一次迁移，执行记录在 `flyway_schema_history`。Hibernate 只做校验：

```yaml
jpa:
  hibernate:
    ddl-auto: validate  # 建表与变更交给 Flyway，这里只校验
```

`ddl-auto: update` 在早期版本用过，现在不要开——它和 Flyway 同时改表时，谁先谁后没有保证；`create` 更危险，每次重启清库。

向量表同理，Spring AI 不再自动建表：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: false
```

## 语音面试连不上（`连接已断开，请刷新页面重试`）

浏览器开发者工具 Network → WS 里看 `/ws/voice-interview/{sessionId}`，正常握手是 `101`。

- **本地**：Vite 已配 `/ws` 代理，和 `/api` 共用 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）。改完代理要重启前端。
- **Docker / Nginx**：`frontend/nginx.conf` 需要转发 `/ws/`，保留 `Upgrade`、`Connection`、带外部端口的 `Host` 头，读超时要给够。
- **握手 403**：`CORS_ALLOWED_ORIGINS` 里没有实际来源，协议、域名、端口都要完全一致。不要用允许全部来源来绕。
- **握手成功但识别不出字**：后端到 DashScope 那条连接的问题，看日志和设置页的 ASR 测试。注意设置页的 ASR 测试只验证 TCP 能否连通，通了不代表鉴权和模型可用。

页面是 HTTPS 时连接必须是 `wss://`。把连接地址写成 `ws://localhost:8080` 在远程部署下一定失败——那个 localhost 指的是访问者的电脑。

## 没声音 / 回声

ASR 和 TTS 默认也用 `AI_BAILIAN_API_KEY`。先查浏览器麦克风权限，再看后端日志里 DashScope 的连接状态。没戴耳机时容易被自己的外放回声触发，先用文字提交模式或戴耳机。

## PDF 导出中文变成方块

字体在 `app/src/main/resources/fonts/ZhuqueFangsong-Regular.ttf`，打包时随 resources 走。导出失败先看日志里的字体加载记录，再确认 iText 依赖没被 exclude。

## Windows PowerShell 里日志中文乱码

后端和 Logback 都是 UTF-8 输出，中文 Windows 控制台默认 GBK(936)，显示时就解错了。仓库里 `gradle.properties`、`logback-spring.xml`、`bootRun` 的 JVM 参数已经统一成 UTF-8，所以先查 PowerShell 这一侧——在启动 `.\gradlew.bat :app:bootRun` 的同一个终端里执行：

```powershell
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
```

想每次自动生效就写进 `$PROFILE`：`if (!(Test-Path $PROFILE)) { New-Item -Path $PROFILE -ItemType File -Force }`，再 `notepad $PROFILE` 粘贴上面四行。提示脚本被禁止执行时执行一次 `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`。

## 设置页换了模型不生效

运行时 Provider 配置写在 `~/.ai-interviewer/llm-providers.yml` 和 `~/.ai-interviewer/llm-providers.env`。设置页里点一次测试连接，或调 `POST /api/llm-provider/reload`。Docker 部署想保留配置就给这个目录挂卷。

## 简历一直显示"分析中"

异步链路是 Redis Stream：Producer 把任务丢进流，Consumer 消费并回写状态。所以按顺序查 Redis 连通、Consumer 有没有起来、后端日志有无异常。实体在消费前已被删掉时任务会被 ACK 丢弃，这是预期行为。

## IntelliJ IDEA 用 Docker 调试后端报 `mounts denied`

macOS 上的 `The path /Applications/IntelliJ IDEA.app/Contents/lib is not shared from the host`：Docker Desktop → Settings → Resources → File Sharing 里加上 `/Applications/IntelliJ IDEA.app/Contents/lib`（或整个 app）和项目目录，重启 Docker 与 IDEA。`./gradlew :app:bootRun` 和 `docker compose` 都不需要这条。
