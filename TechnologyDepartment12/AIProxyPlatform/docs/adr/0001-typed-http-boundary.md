# java-admin：Controller 边界强类型（DTO 入 / VO 出）

Controller 的 HTTP 边界用强类型承载契约：`@RequestBody` 用 `com.yunpei.proxy.api.dto` 下的专用 Request/DTO；成功体 `Result` / `ObjectResult` 的 `data` 用 `com.yunpei.proxy.api.vo` 下的专用 VO。字段名对齐对外 JSON；批量结果、绑定结果、公钥等小对象也建专用类型，不手写 `Map.of` / `LinkedHashMap` 拼装。

**原因**：契约可编译、可演进；避免 Map/JsonNode 把字段错误推迟到运行时。Spring Boot 4 的 HTTP 层为 Jackson 3（`tools.jackson`），`@RequestBody` 使用 Jackson 2 的 `com.fasterxml.jackson.databind.JsonNode` 会触发 `HttpMessageConversionException`。

部分更新须区分「字段未出现」与「显式 null」时：在 DTO 对应字段的 setter 内置 `*Present` 标志（Jackson 仅在 JSON 出现该 key 时调用 setter），再映射到 Command 的 presence 语义。

**不做**：业务接口返回 `Result<Map<…>>` 或 `@RequestBody Map` / `JsonNode`（键集合本身动态、无法稳定建模时除外，如动态路由 `meta`；Service/Repository 内部聚合 Map 不在此限）。
