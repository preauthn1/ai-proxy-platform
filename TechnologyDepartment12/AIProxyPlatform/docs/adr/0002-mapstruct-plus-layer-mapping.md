# java-admin：层间映射用 mapstruct-plus

Request↔Command、Entity↔View、View↔VO 等字段一一对应的转换，统一用 mapstruct-plus：在源或目标类型上声明 `@AutoMapper`，经 `Converter.convert` 转换。路径参数（如 `id`）与 enrich 字段（如 `typeCode` / `roleNames`）等无法从源对象映射的，由调用方 convert 后再补全。

**原因**：减少样板拷贝与字段遗漏；生成代码可检查类型不匹配。拒绝手写「同名字段列表 → 逐 getter/setter」作为默认路径。

**注意**：

- Entity→View 目标为 **record** 时，null→`""`/`0` 等契约默认优先用 **record 紧凑构造器**规范化；勿在 record 上用 `ReverseAutoMapping.defaultValue`（会生成不可编译的 update 方法）。
- JSON 字符串↔`Map` 等类型不兼容字段（如 Task `retryPolicy`）保留手写映射 + 专用 support。
- **presence 语义**（字段是否在 JSON 中出现、`ParentIdChange` / `MetadataChange` 等）不可做朴素 AutoMapper，否则会丢失「省略 vs 显式 null」。
