# wordflip-android — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)

## 范围

Kotlin + Jetpack Compose MVP 客户端。UI/交互参考 v5 原型；**业务规则以 requirements v6 + API 为准**。

## 模块结构

```
app/                 # Application、主导航壳
feature-auth/
feature-today/
feature-books/
feature-groups/
feature-study/
feature-snapshot/    # 卡拍
feature-quiz/
feature-stats/
feature-settings/
core-network/        # Retrofit、Auth 拦截器
core-model/          # OpenAPI 生成 DTO
core-ui/             # 主题、Flip 卡片、Toast
core-image/          # 编辑器、CameraX
```

**依赖方向：** `feature-*` → `core-*`；`core-*` 不可依赖 `feature-*`。

## 硬性规则

- **中文注释**：Composable/KDoc/ViewModel 状态与导航逻辑使用**简体中文**（见 [coding-standards.md](../docs/wordflip/coding-standards.md)）  
- **不做**艾宾浩斯计算、测验判题、词书文件解析入库  
- **不做**手动改掌握度（移除 v5 记得/模糊按钮）  
- **主题**：Natural Sage — `primary` `#6F9038`，见 `docs/wordflip/design-system/MASTER.md`  
- **图标**：Material Symbols Outlined  
- **导航**：5 Tab + 子页面栈（REQ-NAV-1~6）；进测验必须新建 session  
- Room 仅可选缓存，**不以本地为业务真相**  

## OpenAPI

DTO 从 `../wordflip-api/openapi.yaml` 生成到 `core-model`；勿手写与契约冲突的字段名。

## 命令

```bash
./gradlew :app:assembleDebug
./gradlew test
```

API Base URL（模拟器）：`http://10.0.2.2:8080/api/v1`

## 参考

- [../docs/wordflip/android-ui-spec.md](../docs/wordflip/android-ui-spec.md)
- [../prototypes/wordflip-v5.html](../prototypes/wordflip-v5.html)（UI only）
- [../TASK.md](../TASK.md) §A、§P0-A～P4-A
