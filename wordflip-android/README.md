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

模拟器访问本机 API：`http://10.0.2.2:8080/api/v1`

## 当前脚手架范围

- Natural Sage 主题（`core-ui` · primary `#6F9038`）
- 登录占位 + 5 Tab 主导航（设置 / 词书 / 分组 / 统计 / 今日，默认今日）
- 各 feature 模块占位 Screen，**尚未**接入 OpenAPI / Retrofit

## 参考

- [../docs/wordflip/android-ui-spec.md](../docs/wordflip/android-ui-spec.md)
- [../docs/wordflip/design-system/MASTER.md](../docs/wordflip/design-system/MASTER.md)
- [../prototypes/wordflip-v5.html](../prototypes/wordflip-v5.html)（UI only）
- [AGENTS.md](AGENTS.md)
