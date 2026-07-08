# WordFlip Android

Kotlin + Jetpack Compose 多模块客户端（MVP 脚手架）。

## 模块结构

```
wordflip-android/
├── app/                 # Application、主导航壳
├── core-network/        # Retrofit、Auth 拦截器（占位）
├── core-model/          # OpenAPI 生成 DTO（占位）
├── core-ui/             # WordFlipTheme、通用 Compose 组件
├── core-image/          # CameraX、图片编辑器（占位）
├── feature-auth/        # 登录 / 注册
├── feature-today/       # 今日首页
├── feature-books/       # 词书
├── feature-groups/      # 分组
├── feature-study/       # 卡片学习
├── feature-quiz/        # 默写测验
├── feature-stats/       # 统计
├── feature-settings/    # 设置
└── feature-snapshot/    # 卡拍
```

## 技术栈

- Kotlin 1.9 + Jetpack Compose + Material 3
- Hilt、Navigation Compose
- Retrofit + OkHttp（`core-network`，待 API 集成）
- CameraX（`core-image`，待 P3 实现）

## 构建

需安装 Android SDK（compileSdk 34）与 JDK 17+。

```bash
cd wordflip-android
./gradlew :app:assembleDebug
./gradlew test
```

Windows PowerShell：

```powershell
cd wordflip-android
.\gradlew.bat :app:assembleDebug
```

### 真机访问本机后端（USB · adb reverse）

Debug 包 API 地址为 `http://127.0.0.1:8080/api/v1/`。手机通过 USB 连接电脑后执行：

```powershell
.\scripts\adb-reverse.ps1
.\gradlew.bat :app:installDebug
```

等价命令：`adb reverse tcp:8080 tcp:8080`（重插手机或重启 adb 后需再执行一次）。

前置条件：本机已启动 `docker compose` 与 `wordflip-server`（dev profile，端口 8080）。

### 模拟器（可选）

模拟器仍可用 `adb reverse` 配合 `127.0.0.1`；或临时将 `app/build.gradle.kts` 中 debug 的 `API_BASE_URL` 改回 `http://10.0.2.2:8080/api/v1/`。

## 当前脚手架范围

- Natural Sage 主题（`core-ui` · primary `#6F9038`）
- 登录占位 + 5 Tab 主导航（设置 / 词书 / 分组 / 统计 / 今日，默认今日）
- 各 feature 模块占位 Screen，**尚未**接入 OpenAPI / Retrofit

## 参考

- [../docs/wordflip/android-ui-spec.md](../docs/wordflip/android-ui-spec.md)
- [../docs/wordflip/design-system/MASTER.md](../docs/wordflip/design-system/MASTER.md)
- [../prototypes/wordflip-v5.html](../prototypes/wordflip-v5.html)（UI only）
- [AGENTS.md](AGENTS.md)
