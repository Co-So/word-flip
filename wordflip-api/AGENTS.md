# wordflip-api — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)

## 范围

OpenAPI 3.0.3 契约 — Android / Web / Server 的**单一 API 来源**。本目录仅放契约与说明，**不写实现代码**。

## 文件

| 文件 | 说明 |
|------|------|
| `openapi.yaml` | 全部 paths、schemas、security、业务 description |

Base URL：`/api/v1`

## 变更流程（必须按序）

1. 修改 `openapi.yaml`（paths、schema、description、错误码）  
2. 若业务规则变化 → 更新 [../docs/wordflip/api-modules.md](../docs/wordflip/api-modules.md)  
3. 若持久化影响 → 更新 [../docs/wordflip/database-design.md](../docs/wordflip/database-design.md) + Flyway  
4. 实现 `wordflip-server` Controller/Service  
5. 重新生成 Android `core-model`  

**禁止**未改 openapi 就先改 Controller 路径或 Android DTO。

## 契约要点（勿删改语义）

- 掌握度 **仅** `POST /quiz/sessions/{sessionId}/answer`  
- **无** `PATCH /words/{wordKey}/mastery`  
- `PUT /settings` 触发增量 append；`PATCH /settings/preferences` 不 append  
- 导入：`POST /books/import/preview` → `POST /books/import`  
- `wordKey` 路径参数需 URL encode  

## 校验

```bash
# YAML 语法（需 Python PyYAML 或 openapi-cli）
python -c "import yaml; yaml.safe_load(open('openapi.yaml', encoding='utf-8'))"
```

## 参考

- [../docs/wordflip/api-modules.md](../docs/wordflip/api-modules.md)
- [../TASK.md](../TASK.md) 各阶段 `-B` 后端任务与 openapi 端点一一对应
