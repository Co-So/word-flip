# WordFlip 用户与账号设计

> 版本：v1.1  
> 日期：2026-06-30  
> 关联：[architecture.md](./architecture.md) · [requirements.md](./requirements.md) · [android-ui-spec.md](./android-ui-spec.md)

---

## 1. 账号模型

| 字段 | 说明 |
|------|------|
| `id` | 用户主键 |
| `email` | 可空，唯一，登录账号之一 |
| `phone` | 可空，唯一，E.164 格式，登录账号之一 |
| `password_hash` | BCrypt |
| `status` | active / disabled |
| `created_at` / `last_login_at` | 时间戳 |

**约束：** `email` 与 `phone` 至少一项非空。

---

## 2. 注册与登录

### 2.1 注册

- 使用 **邮箱 + 密码** 或 **手机号 + 密码** 注册（二选一必填）。
- 密码长度与复杂度校验（MVP：最少 8 位）。
- 注册成功自动登录，进入「今日」页。

### 2.2 登录

- 统一字段 `account`：自动识别邮箱或手机号 + 密码。
- 登录失败：明确提示，不泄露账号是否存在。
- MVP：密码登录；短信验证码登录为二期。

### 2.3 会话

- JWT Access Token（15 分钟）+ Refresh Token（7 天，Redis）。
- 登出：Refresh Token 失效，可选 Access 加入黑名单。
- **MVP 必须登录后使用**，无游客模式。

### 2.4 设置页

- 提供「退出登录」，清除本地 Token，回到登录页。

---

## 3. 用户数据归属

以下数据均按 `user_id` 隔离：

- 词书勾选、导入词书、分组、掌握度、复习计划
- 测验记录、学习日志、卡片图片、污渍配置、用户设置

---

## 4. 二期扩展（非 MVP）

- 绑定第二登录方式（邮箱 ↔ 手机）
- 短信验证码登录 / 找回密码
- 注销账号与数据删除

---

*业务 REQ 见 requirements.md §2（账号与登录）。*
