# WordFlip Design System — MASTER

> 版本：v1.2  
> 日期：2026-07-01  
> 平台：Android · Jetpack Compose · Material 3  
> 关联：[android-ui-spec.md](../android-ui-spec.md) · [requirements.md](../requirements.md)

本文档为 WordFlip **全局视觉单一真相来源**。页面级 override 可放在 `design-system/pages/`（按需新增）。

---

## 1. 品牌方向

| 项 | 定稿 |
|----|------|
| 风格 | **Natural Sage** — 纸感暖底 + 鼠尾草绿主调 |
| 气质 | 安静、可信赖、适合长时间学习；区别于通用 SaaS 蓝 |
| 图标 | Material Symbols Outlined（24dp）；**不用 emoji 作结构导航** |
| 主题 | **Light + Dark** 同等优先级；用户可选跟随系统 / 浅色 / 深色 |

### 1.1 主色层级（推荐方案）

| 角色 | Light | Dark | 说明 |
|------|-------|------|------|
| `primary` | `#6F9038` | `#8FAF5C` | 主按钮、Tab 选中、链接强调（可点击） |
| `onPrimary` | `#FFFFFF` | `#1A2E0A` | 主按钮文字 |
| `primaryContainer` | `#B7D07A` | `#2A3320` | 品牌软绿、任务图标底、选中 chip 背景 |
| `onPrimaryContainer` | `#3D5220` | `#C5D99A` | container 上文字/图标 |
| `secondary` | `#7A9B4A` | `#A8C87A` | 次要强调 |

**说明：** `#B7D07A` 为品牌识别色，用于 **容器与轻强调**；**不**单独作为大按钮填色（对比度不足）。主 CTA 使用更深的 `primary` `#6F9038`。

**与统计绿区分：** 「已掌握」等统计语义使用 **`success` `#0B7B5C`**，与品牌 primary 色相/用途分离。

### 1.2 色彩面积原则（60 · 30 · 10）

本规范约束**单屏可视区域**内各色块的**面积占比**（非 token 数量、非色相数量）。目标：长时间学习不疲劳、品牌绿「少而准」、语义反馈不抢主界面。

#### 与 Material 3 的对应关系

60-30-10 中的「主色」指**视觉支配色**（大面积中性底），**不是** M3 的 `primary` token：

| 60-30-10 角色 | 目标占比 | M3 / 项目 Token | 典型色值（Light） |
|---------------|----------|-----------------|-------------------|
| **支配色** | **≈60%** | `background` · `surface` · `onBackground` · `onSurface` · `onSurfaceVariant` · `outline` | `#F7F6F2` · `#FFFFFF` · 文字灰阶 |
| **辅助色** | **≈30%** | `surfaceVariant` · `primaryContainer` · `secondary` · `*Container`（语义容器）· 掌握度 chip 背景 · 热力图 0–2 级 | `#EFEDE7` · `#B7D07A` · `#E0F5EE` 等 |
| **点缀色** | **≈10%** | `primary` 实色 · `success` / `error` / `warning` 实色 · 热力图 3 级 · Tab 选中 · 主 CTA | `#6F9038` · `#0B7B5C` · `#C0392B` |

> **文字色**（`on*`）计入支配色 60%，除非整行/整块使用品牌或语义背景。

#### 容差与验收

| 角色 | 目标 | 允许区间 | 验收方式 |
|------|------|----------|----------|
| 支配色 | 60% | 55% – 65% | 首屏截图按**背景填色面积**估算；新屏交付前必检 |
| 辅助色 | 30% | 25% – 35% | 卡片、chip、图标底、进度轨道等**有色块**合计 |
| 点缀色 | 10% | 8% – 12% | 主 CTA、选中态、KPI 强调、反馈条等**高饱和实色**合计 |

- 低于区间：界面可能过「素」或品牌识别弱 → 增加 `primaryContainer` 辅助块，而非扩大 `primary` 面积。
- 高于区间：界面可能过「花」或抢学习焦点 → 缩小语义实色、改用 `*Container` 背景。

#### 点缀色预算（10% 内部分配）

`primary` 与语义色**共享** 10% 点缀预算，不得各自占满 10%：

| 用途 | Token | 建议占屏比 | 说明 |
|------|-------|------------|------|
| 主 CTA | `primary` | 2% – 4% | 如「开始学习」底栏按钮 |
| 导航选中 | `primary` | 1% – 2% | Tab 图标 + 指示条 |
| 品牌强调 | `primary` | 1% – 2% | 标题点缀、链接、进度条填充 |
| 语义反馈 | `success` / `error` / `warning` | 合计 ≤ 4% | 答对/答错、待复习数字、Badge |

**禁止**用 `primary` 铺满顶栏、整页背景或大面积卡片底（应改用 `primaryContainer` 或 `surfaceVariant`）。

#### 辅助色用法（30% 层）

辅助色承担**分区与品牌温度**，避免 60% 层全是「白 + 灰」：

| 场景 | 推荐 Token | 避免 |
|------|------------|------|
| 任务行图标底 | `primaryContainer` | 整块 `primary` |
| 选中 FilterChip | `primaryContainer` + `onPrimaryContainer` | `primary` 填底 |
| 未选 chip / 次级块 | `surfaceVariant` | 自定义 hex |
| 统计卡片底 | `surface` 或 `successContainer` | 整块 `success` 实色 |
| 进度条轨道 | `surfaceVariant` | 透明或无轨道 |
| 进度条填充 | `primary` 或 `secondary` | 占满整条宽度以外的区域 |

#### 页面分级

| 级别 | 页面 | 60-30-10 | 备注 |
|------|------|----------|------|
| **A** | 登录、今日、词书、设置 | 严格遵循 | 支配色 ≥55%；点缀以 `primary` 为主 |
| **B** | 分组列表、分组详情、学习 | 严格遵循 | 掌握度 chip 背景计入辅助色 30% |
| **C** | 统计、测验、热力图 | 支配色仍 ≥55% | 语义点缀 + 热力图高饱和格**合计** ≤15%；超出须缩小格/缩短反馈条时长 |

C 类屏面试题反馈（1400ms）、热力图单格等**短时或小面积**高饱和可放宽至 15%，但**静态默认态**仍按 10% 设计。

#### 典型布局参考（今日 · A 类）

```
┌──────────────────────────────────┐
│ background #F7F6F2          ~55% │  ← 支配色（含露出暖底）
│  ┌──── stat ────┐×3          ~20% │  ← 辅助：surface 白卡片
│  [primaryContainer 图标底]   ~8%  │  ← 辅助：品牌软绿
│  ███ primary CTA ███         ~3%  │  ← 点缀
│  Tab 选中 primary            ~2%  │  ← 点缀
└──────────────────────────────────┘
```

#### Dark 主题

比例与 Light 相同；Dark 下 `secondary` 须为独立 token（`#A8C87A`），**不得**与 `primary` 共用同一色值，以免辅助层与点缀层合并。

| 角色 | Dark 注意 |
|------|-----------|
| 支配色 60% | `background` `#121212` + `surface` `#1E1E1E` 为主 |
| 辅助色 30% | `primaryContainer` `#2A3320`、`surfaceVariant` `#2C2C2C` |
| 点缀色 10% | `primary` `#8FAF5C` 仅 CTA / 选中 / 小强调 |

#### 实现自检清单

- [ ] 页面背景为 `background`，非 `primary` / `primaryContainer`
- [ ] 仅一个主 CTA 使用 `primary` 实色底（同屏次要操作为 `OutlinedButton` / `TextButton`）
- [ ] 任务图标、选中 chip 使用 `primaryContainer`，非 `primary`
- [ ] 统计「已掌握」数字/图标用 `success`，大卡片底用 `successContainer` 或 `surface`
- [ ] 掌握度 chip 仅在三态背景色 + 图标 + 文案，不额外叠 `primary`
- [ ] 无 Composable 内硬编码 hex（见 §8）
- [ ] C 类屏静态态截图估算：支配 ≥55%，点缀 ≤12%（或 C 类合计 ≤15%）

---

## 2. 色彩 Token — Light

### 2.1 表面与文字

| Token | Hex | 用途 |
|-------|-----|------|
| `background` | `#F7F6F2` | 纸感暖底（延续 v5） |
| `surface` | `#FFFFFF` | 卡片、列表 |
| `surfaceVariant` | `#EFEDE7` | 次级块、未选 chip |
| `outline` | `#12000000` | 分割线（8% 黑） |
| `onBackground` | `#111111` | 主文字 |
| `onSurface` | `#111111` | 卡片文字 |
| `onSurfaceVariant` | `#666666` | 次要文字 |

### 2.2 语义色

| Token | Hex | 用途 |
|-------|-----|------|
| `success` | `#0B7B5C` | 统计「已掌握」、测验答对强调 |
| `successContainer` | `#E0F5EE` | 成功背景 |
| `error` | `#C0392B` | 错误、测验答错 |
| `errorContainer` | `#FDECEA` | 错误背景 |
| `warning` | `#C47D00` | 警告、待复习数字 |

### 2.3 队列三态薄弱角标（辅展示）

组详情主展示为稳定性热力（§2.3b）；下列仅在 `fuzzy` / `unknown` 时作角标：

| 状态 | 背景 | 文字 | 图标 |
|------|------|------|------|
| 模糊 | `#FEF3DC` | `#8B5A00` | `HelpOutline` |
| 不认识 | `#FDECEA` | `#9B2919` | `PriorityHigh` |

> 必须 **颜色 + 文案 + 图标**，不单靠颜色（WCAG / 色盲友好）。

### 2.3b 单词稳定性热力（5 档，组详情主展示）

与 §2.5 **统计日历热力图**区分：本表映射 `MasterySnapshot.heatLevel`。

**展示原则：** 以**颜色**为主（行底浅 tint + 左侧 6dp 色条 + 右侧 5 格热力条），短文案为辅（无障碍）；勿做成纯文字 Chip。

| heatLevel | 文案 | 色条/格 | 行底 tint | 文字 |
|-----------|------|---------|-----------|------|
| 0 | 新词 | `#EFEDE7` | `#FAF9F6` | `#616161` |
| 1 | 初识 | `#D4E4BC` | `#F5F8EF` | `#3D4F1C` |
| 2 | 巩固中 | `#9BB56A` | `#EEF4E4` | `#1A2E0A` |
| 3 | 较熟 | `#6F9038` | `#E6EFDA` | `#3D4F1C` |
| 4 | 很熟 | `#0B7B5C` | `#E0F5EE` | `#0B7B5C` |

### 2.4 学习卡片

| Token | Light |
|-------|-------|
| `studyCard` | `#FFFFFF` |
| `studyCardInk` | `#1A1A2E` |
| `studyCardBackText` | `#333333` |
| `stainOpacity` | 0.12 – 0.20 |
| `elevation.studyCard` | 4dp |

### 2.5 统计日历热力图（4 级）

| 级别 | Hex |
|------|-----|
| 0 | `#EFEDE7` |
| 1 | `#D4E4BC` |
| 2 | `#9BB56A` |
| 3 | `#6F9038` |

---

## 3. 色彩 Token — Dark

### 3.1 表面与文字

| Token | Hex |
|-------|-----|
| `background` | `#121212` |
| `surface` | `#1E1E1E` |
| `surfaceVariant` | `#2C2C2C` |
| `outline` | `#33FFFFFF` |
| `onBackground` | `#E6E1E5` |
| `onSurface` | `#E6E1E5` |
| `onSurfaceVariant` | `#CAC4D0` |

### 3.2 品牌主色（Dark）

| Token | Hex |
|-------|-----|
| `primary` | `#8FAF5C` |
| `onPrimary` | `#1A2E0A` |
| `primaryContainer` | `#2A3320` |
| `onPrimaryContainer` | `#C5D99A` |

### 3.3 语义色（Dark）

| Token | Hex |
|-------|-----|
| `success` | `#6DD5A8` |
| `successContainer` | `#123528` |
| `error` | `#FFB4AB` |
| `errorContainer` | `#3D1F1C` |
| `warning` | `#FFB95A` |

### 3.4 队列薄弱角标（Dark）

| 状态 | 背景 | 文字 |
|------|------|------|
| 模糊 | `#3D2E14` | `#FFB95A` |
| 不认识 | `#3D1F1C` | `#FFB4AB` |

### 3.4b 单词稳定性热力（Dark）

| heatLevel | 文案 | 背景 |
|-----------|------|------|
| 0 | 新词 | `#3A3A3A` |
| 1 | 初识 | `#2A3320` |
| 2 | 巩固中 | `#3A4A28` |
| 3 | 较熟 | `#4A6030` |
| 4 | 很熟 | `#0B7B5C` |

### 3.5 学习卡片（Dark）

| Token | Hex |
|-------|-----|
| `studyCard` | `#252525` |
| `studyCardBorder` | `#33FFFFFF`（1dp，替代重阴影） |
| `stainOpacity` | 0.18 – 0.28 |

### 3.6 热力图（Dark）

| 级别 | Hex |
|------|-----|
| 0 | `#2C2C2C` |
| 1 | `#2A3D1A` |
| 2 | `#4A6B32` |
| 3 | `#8FAF5C` |

### 3.7 浮层

| Token | Light / Dark |
|-------|--------------|
| `scrim` | `#99000000`（60%） |
| `imageEditorStage` | Light `#EFEDE7` / Dark `#0A0A0A` |
| `imageEditorBoundary` | Light `#66000000` / Dark `#66FFFFFF` |

---

## 4. 图标体系

### 4.1 原则

| 规则 | 说明 |
|------|------|
| 库 | Material Symbols **Outlined**（Compose `material-icons-extended`） |
| 尺寸 | 24dp 标准；触控 area ≥ 48dp |
| 着色 | semantic color token，禁止硬编码 |
| 选中 | Tab 可用 **Filled** + `primary` `#6F9038` |
| 禁止 | emoji 作结构导航；混用多套 icon font |

### 4.2 Launcher（Adaptive Icon）

- **Foreground：** 圆角卡片翻转造型，可含抽象 W；主点缀 `#6F9038` / `#B7D07A`
- **Background：** `#F7F6F2` 暖纸或浅 sage 渐变
- **独立**于应用内 Tab 图标，单独交付 asset

### 4.3 导航 Tab

`Settings` · `MenuBook` · `GridView` · `BarChart` · `Today`

完整功能映射见 [android-ui-spec.md §8.5](../android-ui-spec.md#85-功能映射表)。

### 4.4 与污渍

污渍为卡片内纹理，非 icon；`Palette` / `Refresh` / `Visibility` 仅作控制入口。

---

## 5. Typography

| M3 Role | 字体 | 用途 |
|---------|------|------|
| `displaySmall` | Noto Sans SC + Roboto | KPI 大数字 |
| `headlineSmall` | 同上 | 页面标题 |
| `titleMedium` | 同上 | 组名、词书名 |
| `bodyLarge` | 同上 | 正文、释义（≥16sp） |
| `labelLarge` | 同上 | 按钮、Tab |
| 音标 | Roboto Mono | 10–12sp |

---

## 6. 间距与形状

| Token | 值 |
|-------|-----|
| 网格 | 8dp |
| 页面 gutter | 16dp |
| `radius.sm` | 10dp |
| `radius.md` | 16dp |
| `radius.lg` | 20dp |
| 最小触控 | **48dp** |
| Bottom Nav 区 | 80dp + safe area |

---

## 7. 动效

| 场景 | 时长 | 说明 |
|------|------|------|
| Tab / 子页转场 | 200–300ms | Material motion |
| 卡片翻转 | ~400ms | spring |
| 打乱 | 900ms | 可 reduced-motion 降级 |
| 测验反馈 | 1400ms | 自动下一题 |
| Snackbar | 2s | |

---

## 8. Anti-Patterns

- 硬编码 hex 在 Composable 内
- 用 `#B7D07A` 作大按钮底 + 白字（对比度不足）
- emoji 作 Tab / 设置图标
- 掌握度仅用颜色、无文案
- Light 色值直接反相做 Dark

- 混用 Feather / Font Awesome / 自定义 PNG Tab 图标
- Launcher 与应用内 icon 视觉断裂

**60-30-10 相关：**

- 用 `primary` 作 Scaffold / 顶栏 / 整页背景
- 同屏多个 `primary` 实心大按钮（点缀 >12%）
- 统计卡片整块 `success` 实色底（应 `successContainer` + 小面积 `success` 文字）
- 热力图大面积使用 3 级 `#6F9038` 格（应 0–2 级占辅助层，3 级点睛）
- Dark 主题 `secondary` 与 `primary` 同色导致辅助/点缀层无法区分

---

## 9. 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-30 | v1.0 | Natural Sage 主色；Light/Dark 双主题；primary `#6F9038` + container `#B7D07A` |
| 2026-06-30 | v1.1 | 新增 §4 图标体系；Launcher 概念；Material Symbols 规范 |
| 2026-07-01 | v1.2 | 新增 §1.2 色彩面积原则（60-30-10）；页面 A/B/C 分级；§8 反模式补充 |
