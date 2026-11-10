# 平台时区：三层时钟

全栈共用同一套时钟语义，禁止再读 `ZoneId.systemDefault()` / 浏览器本地来解释业务墙钟。

## 三层

| 层       | 含义                                | 落地                                            |
| -------- | ----------------------------------- | ----------------------------------------------- |
| 物理时刻 | 同一个 Instant                      | `deleted_at` 毫秒、Temporal `Instant`、跨区比较 |
| 平台墙钟 | 审计字段、日历 cron、无 offset 入参 | 固定 `Asia/Shanghai`（`TimeZones.PLATFORM`）    |
| 展示时区 | 人看的数字                          | 前端 IANA 选择，只改显示                        |

## 契约

- 库内 `TIMESTAMP` ↔ Java `LocalDateTime` = 上海墙钟。
- HTTP JSON 的 `LocalDateTime` 写出 `2026-08-14T16:00:00+08:00`；入参同时收 `Z`、带 offset、无 offset（无 offset 视为上海墙钟）。
- Temporal 日历 cron：`ScheduleSpec.timeZoneName = Asia/Shanghai`。亚分钟 Interval 是时长，不绑时区。
- 用户时区不落库、不改 cron。
- 工单 `report_timezone` 与分镜构建里的投放报表业务时区，不是平台时钟。

## 不做

- 实体改 `Instant`、列改 `DATETIME` 的数据迁移。
- 按任务单独配时区。
- 把平台区改成有 DST 的 IANA（当前只支持 `Asia/Shanghai`）。
