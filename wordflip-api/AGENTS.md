# wordflip-api — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)
> 产品规则：[../docs/wordflip/requirements.md](../docs/wordflip/requirements.md) v7

## 范围

OpenAPI 3.0.3 是 Android、Server 与后续 Web 的 REST 契约单一来源。本目录只放契约、契约测试和说明，不放服务端实现。

## 文件

| 文件 | 说明 |
|------|------|
| `openapi.yaml` | `/api/v1` 全部 paths、schemas、security 与错误语义 |
| `tests/test_learning_card_contract.py` | v7 学习计划、学习卡、FSRS、媒体等关键契约断言 |

## 变更流程

1. 用户行为变化先更新 `../docs/wordflip/requirements.md`。
2. 修改 `openapi.yaml` 的 path、schema、required、description 与错误码。
3. 同步契约测试并先验证失败。
4. 业务边界变化同步 `api-modules.md`。
5. 持久化变化同步 `database-design.md` 与 migration-v2。
6. 实现 server Controller/Service。
7. 同步 Android `core-model`、`core-network` 与调用页面。
8. 执行 API、server、Android 相关验证。

禁止先改 Controller 或 Android DTO，再事后补 OpenAPI。

## v7 契约不变量

- 任一用户只有一个当前 `activePlanId`。
- `POST /learning-plans` 创建并激活计划；`PATCH /learning-plans/current` 切换或调整当前计划。
- 学习卡使用 `cardId`；`wordKey` 只保留在 `/words/{wordKey}` 查询与展示字段。
- 词书内容为已发布 learning card；来源资料通过 `sourceMaterials` 返回。
- 自定义分组请求使用 `cardIds`。
- 图片和污渍路径使用 `/learning/cards/{cardId}/...`。
- 掌握度唯一写入口是 `POST /quiz/sessions/{sessionId}/answer`。
- 答题请求必须含 `requestId`；客户端不得提交 FSRS rating 或新状态。
- 答题响应可返回 before/after FSRS snapshot，但状态由服务端计算。
- `dictation` 与 `choice` 是独立 skill。
- Study session 只上报浏览/打卡，不产生记忆更新。
- v7 无 `PUT /settings` 选多本词书，也无 `activeDictId` 全局词典设置。

## 描述与错误码要求

- 明确资源是否限定当前学习计划。
- 明确 404 是无当前计划、资源不存在还是不属于当前计划。
- 写操作说明幂等键、重复提交行为和事务边界。
- schema 中服务端必返字段进入 `required`；可空与缺省含义分别描述。
- `cardId`、`planId`、`groupId` 使用一致的整数格式；session/request ID 使用既定 UUID/string 格式。
- 任何兼容性降级都要在 description 和测试中固定。

## 校验

```powershell
python -c "import yaml; yaml.safe_load(open('openapi.yaml', encoding='utf-8'))"
python -m pytest -q tests
```

修改契约后还需运行：

```powershell
cd ..\wordflip-server
.\mvnw.cmd test

cd ..\wordflip-android
.\gradlew.bat test :app:assembleDebug
```

## 禁止

- 恢复 `PATCH /words/{wordKey}/mastery`。
- 恢复 `PUT /settings` 触发多词书增量分组。
- 恢复全局词典目录/`activeDictId` 作为当前内容真相。
- 允许客户端提交 FSRS rating、dueAt、stability 或 mastery。
- 媒体继续绑定 wordKey。
- 只改 schema 不更新 description、required、错误码与契约测试。

## 参考

- [requirements v7](../docs/wordflip/requirements.md)
- [API 模块](../docs/wordflip/api-modules.md)
- [数据库设计](../docs/wordflip/database-design.md)
- [TASK §V7-D](../TASK.md)
