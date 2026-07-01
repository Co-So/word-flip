# wordflip-api

WordFlip REST API 的 **OpenAPI 3.0** 契约，作为 Android / Web 客户端与服务端实现的单一来源。

| 文件 | 说明 |
|------|------|
| [openapi.yaml](openapi.yaml) | 完整端点、Schema、业务规则描述 |

## 关联文档

- [../docs/wordflip/api-modules.md](../docs/wordflip/api-modules.md) — 模块划分
- [../docs/wordflip/database-design.md](../docs/wordflip/database-design.md) — 数据模型

## 代码生成（初始化后）

```bash
# Android: openapi-generator → core-model
# Server: 可选 springdoc 对照实现
```

Base URL：`/api/v1`
