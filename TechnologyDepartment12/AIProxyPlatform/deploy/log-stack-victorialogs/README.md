# Vector + VictoriaLogs 日志栈（log-stack-victorialogs）

两套 compose：**中心机**跑 VictoriaLogs，**业务机**单独跑 Vector。VMUI 用 VictoriaLogs 自带的 HTTP Basic Auth。

同目录还有一套更重的 [log-stack-doris](../log-stack-doris/)（Vector + Doris + Grafana）。

**本栈适合开发 / 演示 / 轻量自建。** 单容器 VictoriaLogs 没有副本；`down -v` 会清空日志。

## 架构

```
[业务服务器]                         [中心机]
日志文件 → Vector (remap) ──Elasticsearch bulk──► VictoriaLogs :9428
                                                       │
                                                       ▼
                                              内置 UI /select/vmui/
                                              （浏览器弹账号密码）
```

依据：

- [VictoriaLogs 接收 Vector](https://docs.victoriametrics.com/victorialogs/data-ingestion/vector/)
- [VictoriaLogs Quick Start](https://docs.victoriametrics.com/victorialogs/quickstart/)
- [VMUI（VictoriaLogs Web UI）](https://docs.victoriametrics.com/victorialogs/querying/#web-ui)
- [官方 Vector Docker demo](https://github.com/VictoriaMetrics/VictoriaLogs/tree/master/deployment/docker/victorialogs/vector)

写入走官方首选 **Elasticsearch** 协议（`/insert/elasticsearch/`）。备选是 HTTP JSON line（`/insert/jsonline`），本栈未默认启用。

查询 UI 是 VictoriaLogs 内置的 `/select/vmui/`。打开后浏览器会要 Basic Auth（默认 `admin` / `changeme`）。`/insert`、`/select`、`/health` 用同一套账号。

## 环境要求

- Docker Desktop（Linux 容器）。
- 内存远低于 Doris 栈；一般 **1 GB** 即可本地演示。compose 默认 VictoriaLogs `768m`（`GOMEMLIMIT=650MiB`）、Vector `256m`。
- 与 `log-stack-doris` 可同时跑：端口互不占用（本栈 `9428` / Vector `8687`）。

## 启动

### 1. 中心机（VictoriaLogs）

在 `deploy/log-stack-victorialogs`：

```powershell
copy .env.example .env
# 改 VL_AUTH_USERNAME / VL_AUTH_PASSWORD，生产不要用默认 changeme
docker compose --env-file .env up -d
pwsh -File .\scripts\smoke.ps1
```

VictoriaLogs 一般数秒就绪。

### 2. 业务机（仅 Vector）

把 `vector/` 目录拷到业务机。在 `vector/`：

```powershell
copy .env.example .env
# 把 VL_HOST 改成中心机 IP；JAVA_ADMIN_LOG_DIR 默认 /opt/java-admin/logs
# VL_AUTH_USERNAME / VL_AUTH_PASSWORD 必须和中心机一致
docker compose --env-file .env up -d
```

本机联调（和 VictoriaLogs 同一台 Docker）：

```powershell
cd vector
docker compose --env-file .env.example --profile demo up -d
```

`VL_HOST=host.docker.internal` 会打到本机映射出来的 `:9428`。

## 入口

| 服务              | 地址                               | 说明                                  |
| ----------------- | ---------------------------------- | ------------------------------------- |
| 内置 UI           | http://127.0.0.1:9428/select/vmui/ | 浏览器会弹 Basic Auth                 |
| VictoriaLogs HTTP | http://127.0.0.1:9428              | 写入 / LogsQL / `/health`，同样要账号 |
| Vector API        | http://业务机:8687                 | 可选，排查用                          |

UI 里可以先试：

- `*` — 全部日志
- `error` — 消息里带 error
- `level:ERROR` — 解析出的级别
- `{type="demo.log"}` — demo 演示日志
- `{type="java-admin.log"}` — java-admin 全量日志

手工查日志：

```powershell
curl -u admin:changeme http://127.0.0.1:9428/select/logsql/query -d "query=*" -d "limit=10"
curl -u admin:changeme http://127.0.0.1:9428/select/logsql/query -d "query=error" -d "limit=10"
curl -u admin:changeme http://127.0.0.1:9428/select/logsql/query -d "query=level:ERROR | fields _time, level, host, _msg" -d "limit=10"
```

## 采集什么

业务机采集两路：

- `LOG_HOST_DIR` → 容器 `/var/log/app/*.log`（demo，排除 `java-admin*.log`）
- `JAVA_ADMIN_LOG_DIR`（默认 `/opt/java-admin/logs`）→ 容器 `/var/log/java-admin/java-admin.log`

不要默认挂整个磁盘。`--profile demo` 会在 `LOG_HOST_DIR` 写演示日志（含少量 ERROR 堆栈，用于多行合并）。

解析字段：`log_time` / `collect_time` / `host` / `path` / `type` / `level` / `thread` / `position` / `message`。java-admin 另外带 `trace_id`、`user_id`、`client_ip`。

写入时：

- `_msg_field=message`
- `_time_field=timestamp`（Vector 事件时间）
- `_stream_fields=host,path,type`（低基数，符合官方 stream 建议）

## 改配置

| 文件                                  | 用途                                               |
| ------------------------------------- | -------------------------------------------------- |
| `.env` / `.env.example`               | 中心机镜像、端口、保留时长、VMUI 账号密码          |
| `vector/.env` / `vector/.env.example` | 业务机 VictoriaLogs 地址、采集目录、同一套账号密码 |
| `vector/vector.yaml`                  | 采集、解析、Elasticsearch sink                     |
| `vector/docker-compose.yml`           | 业务机编排                                         |

```powershell
# 中心机
docker compose --env-file .env up -d --force-recreate victorialogs
# 业务机
cd vector
docker compose --env-file .env up -d --force-recreate vector
```

## 停止

```powershell
# 中心机
docker compose --env-file .env down
# 业务机
cd vector
docker compose --env-file .env down
```

清中心机数据卷（日志会清空）：

```powershell
docker compose --env-file .env down -v
```

## 常见问题

1. **UI 是空的**：Vector 还没写进去，或 `VL_HOST` 指错。先 `curl -u 用户:密码 http://中心机:9428/health`，再看业务机 `docker compose logs vector`。中心机和业务机的 `VL_AUTH_*` 必须一致，否则写入 401。
2. **Vector 报 Elasticsearch healthcheck**：必须关 `healthcheck.enabled`，VictoriaLogs 没有 `/_cluster/health`。
3. **想改用 JSON line**：把 sink 换成官方 [HTTP 示例](https://docs.victoriametrics.com/victorialogs/data-ingestion/vector/#http)，URI 为 `/insert/jsonline?...`。
4. **端口冲突**：改 `.env` 里的 `VL_HTTP_PORT`。本栈默认 `9428` / Vector `8687`，避开 Doris 栈和 java-admin。
5. **时区**：中心机和 Vector 都默认 `Asia/Shanghai`。无时区的时间字段按 VictoriaLogs 进程本地时区解析。
