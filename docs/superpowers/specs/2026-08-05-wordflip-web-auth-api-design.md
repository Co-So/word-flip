# WordFlip Web 认证 API 接入设计

## 目标

完成 `WEB-API01`：在不重写现有页面路由和其它数据模块的前提下，将 Web 的注册、登录、会话恢复、Access Token 刷新与登出接入 OpenAPI 定义的真实认证接口。

本阶段只把 `AuthRepository` 切换为 HTTP 实现。设置、词书、今日、分组、学习、测验、统计和媒体继续使用现有 Mock Repository，后续按 `WEB-API02～07` 逐模块替换。

## 契约对齐

真实认证必须严格使用 `wordflip-api/openapi.yaml`：

- `POST /auth/register`：邮箱或手机号二选一，只提交账号与至少 8 位密码。
- `POST /auth/login`：提交 `account` 与 `password`，由服务端识别邮箱或手机号。
- `POST /auth/refresh`：提交当前 `refreshToken`；成功后以新令牌对原子替换旧令牌对。
- `POST /auth/logout`：携带 Access Token，并在请求体中提交当前 `refreshToken`，只注销当前浏览器会话。

当前 Web 注册表单中的“昵称”不属于 OpenAPI 契约，真实模式下移除。`AuthSession` 使用服务端返回的 `user.id` 作为 `userId`，并以 `email`、`phone` 或 `用户 <id>` 依次生成展示标签；该标签只用于 UI 展示，不作为服务端用户资料。

## 运行时装配

增加环境变量 `VITE_DATA_SOURCE=mock|http`，默认值为 `mock`，确保现有视觉演示和测试不依赖后端。`VITE_API_BASE_URL` 指定真实 API 根地址，开发示例为 `http://127.0.0.1:8080/api/v1`。

运行时继续创建完整的 Mock Repository Bundle；当数据源为 `http` 时，仅用 `HttpAuthRepository` 替换其中的 `auth` 字段。页面只依赖 `AuthRepository`，不直接导入 Axios、令牌存储或 OpenAPI DTO。

开发服务器运行在 `http://127.0.0.1:5273`。Spring Boot dev CORS 白名单增加这个精确 Origin，保留既有 Origin，不放宽为通配符。

## 组件边界

- `TokenStore`：持久化令牌对、过期时间和服务端用户摘要；提供读取、原子替换与清空，不发起网络请求。
- `HttpClient`：创建 Axios 实例、附加 Bearer Token、协调一次并发刷新、重放一次失败请求，并把 HTTP/网络错误映射为 `AppError`。
- `HttpAuthRepository`：实现注册、登录、会话恢复和登出；只负责认证 DTO 与 `AuthSession` 的转换。
- `RepositoryFactory`：根据环境变量组装 Mock 或“真实认证 + 其余 Mock”的 Repository Bundle。
- 认证页面：只使用 `AuthRepository`；文案改为数据源无关的“账户”，账号输入支持邮箱或手机号。
- 设置页面：在独立“账户”区域提供“退出登录”，通过专用确认框调用 `AuthRepository.signOut()`，不接触 HTTP DTO 或令牌存储。

OpenAPI DTO 保持在 `data/http` 内部，不直接泄漏到 `domain` 或页面。

## 令牌生命周期

浏览器持久化 Refresh Token 是当前 Bearer Token 契约下恢复登录所必需的折中。Access Token、Refresh Token、Access Token 到期时间和用户摘要存入一个带版本号的 `localStorage` 记录；不写入日志、URL、查询参数或演示状态存储。

流程如下：

1. 注册或登录成功后，一次性写入完整令牌记录，再返回 `AuthSession`。
2. `getSession()` 没有完整记录时返回 `null`；Access Token 未过期时从记录恢复会话。
3. Access Token 已过期但存在 Refresh Token 时，先刷新并原子替换记录；刷新失败则清空记录并返回 `null`。
4. 受保护请求返回 401 时，同一时刻只允许一个刷新请求；其它请求等待该结果。刷新成功后每个原请求最多重放一次，避免无限循环。
5. 登录、注册、刷新请求本身不触发 401 自动刷新。
6. 登出优先请求服务端注销当前 Refresh Token，无论服务端成功、401 或网络不可用，都在 `finally` 中清空本地令牌，保证当前浏览器立即退出。

本阶段不使用 Cookie，也不解析 JWT 声明；过期时间以服务端 `expiresIn` 和客户端收到响应的时刻计算。

## 设置页退出登录

为满足 `REQ-AUTH-5` 与 `REQ-SETTINGS-6`，设置页在现有学习偏好和演示数据之外新增独立“账户”区域，避免把认证操作混入演示数据重置。退出按钮使用现有设置页视觉语言，并以危险操作颜色区分；不改造侧栏，也不新增全局账户菜单。

点击“退出登录”只打开带遮罩的确认框，不立即改变会话。确认框初始焦点落在“取消”按钮，Tab 焦点限制在两个操作按钮之间，Esc 取消并把焦点还给触发按钮。确认文案明确说明当前浏览器会退出并返回登录页，不暗示注销全部设备。

确认后只允许一次进行中的提交：禁用取消和确认按钮，并阻止 Esc 关闭。页面调用现有 `AuthRepository.signOut()`；成功后清空 TanStack Query 缓存，并使用 replace 导航到 `/login`，避免浏览器后退回到受保护页面。若 Repository 意外拒绝，确认框保持打开、恢复交互并显示不含内部细节的错误；真实 HTTP Repository 已保证远端登出失败时仍清理本地会话，因此网络不可用不阻止当前浏览器退出。

## 错误处理

统一把后端 `ErrorResponse` 和网络故障转换为现有 `AppError`：

- HTTP 400 或 `VALIDATION_ERROR` → `validation`，从 `details` 提取字符串字段错误。
- HTTP 401 → `unauthorized`，登录页显示不泄露账号是否存在的统一提示。
- HTTP 404 → `not-found`。
- HTTP 409 → `conflict`。
- 网络中断、超时和 HTTP 5xx → `unavailable`，允许页面提示稍后重试。
- 其它无法识别的响应 → `unknown`。

页面不展示令牌、响应堆栈或后端内部细节。注册冲突可显示服务端提供的“账号已存在”，登录失败继续使用统一账号或密码错误文案。

## 测试与验收

按 TDD 顺序覆盖：

1. `TokenStore` 能保存、恢复、原子轮换和清空完整记录，损坏或旧版本记录按未登录处理。
2. `HttpAuthRepository` 按 OpenAPI 发送注册、登录、刷新和登出请求，并正确映射用户摘要。
3. Access Token 过期时 `getSession()` 刷新令牌；刷新失败清理本地状态。
4. 多个受保护请求同时收到 401 时只刷新一次，每个原请求最多重放一次。
5. HTTP 状态码、后端错误体和网络错误稳定映射为 `AppError`。
6. `VITE_DATA_SOURCE=mock` 保持原演示行为，`http` 只替换认证模块。
7. 登录与注册页面使用“邮箱或手机号”契约，保留提交中状态和可访问错误提示。
8. Spring Boot CORS 测试允许 `http://127.0.0.1:5273`，且不引入通配 Origin。
9. 设置页退出登录测试覆盖确认框语义、取消后的焦点恢复、pending 防重复提交、成功清理会话并返回登录页，以及意外失败时保留确认框和错误提示。

完成后运行相关 Vitest、Web 全量测试、Lint、生产构建、认证流程 Playwright，以及相关 Spring Boot 配置/Controller 测试。真实联调必须从设置页确认退出，验证返回登录页、刷新仍保持未登录，并验证刚注销的 Refresh Token 不可再次使用。若本机 Docker、MySQL 或 Redis 不可用，真实后端联调必须明确标为未执行，不能用 Mock 测试冒充真实联调。

## 安全边界

- 不在前端保存密码、提交 FSRS rating 或计算任何学习业务状态。
- 不把 Refresh Token 注入非 API Origin，也不在错误信息中输出令牌。
- 401 刷新只发生一次；刷新接口失败后立即清理会话。
- 登出默认只注销当前浏览器会话，不隐式注销用户全部设备。
- CORS 只加入明确的本地开发 Origin，生产 Origin 由部署配置显式提供。

## 非目标

- 不在本阶段接入设置、学习计划、词书或其它业务 API。
- 不新增用户昵称/profile 接口，也不修改 OpenAPI 认证响应。
- 不实现多标签页令牌广播、跨设备会话列表、忘记密码或第三方登录。
- 不把整个 Repository Bundle 一次性切换为 HTTP。
- 不在浏览器端实现 FSRS、判题、分组、今日任务或统计计算。
